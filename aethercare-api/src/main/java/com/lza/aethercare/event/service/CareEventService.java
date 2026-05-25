package com.lza.aethercare.event.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lza.aethercare.audit.enums.CareAuditAction;
import com.lza.aethercare.audit.service.CareAuditService;
import com.lza.aethercare.common.error.BusinessException;
import com.lza.aethercare.common.error.ErrorCode;
import com.lza.aethercare.common.event.PublishToKafka;
import com.lza.aethercare.common.time.Clock;
import com.lza.aethercare.decision.service.DecisionService;
import com.lza.aethercare.event.dto.CreateCareEventRequest;
import com.lza.aethercare.event.entity.CareEvent;
import com.lza.aethercare.event.enums.CareEventStatus;
import com.lza.aethercare.event.enums.CareEventType;
import com.lza.aethercare.event.enums.RiskLevel;
import com.lza.aethercare.event.event.CareEventCreatedMessage;
import com.lza.aethercare.event.repository.CareEventRepository;
import com.lza.aethercare.tenant.context.TenantContext;
import com.lza.aethercare.workflow.entity.CareWorkflowInstance;
import com.lza.aethercare.workflow.repository.CareWorkflowInstanceRepository;
import com.lza.aethercare.workflow.service.CareWorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/** Event service：建立事件並啟動 workflow（單一 transaction）。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CareEventService {

    private final CareEventRepository repo;
    private final CareWorkflowInstanceRepository workflowRepo;
    private final DecisionService decisionService;
    private final CareWorkflowService workflowService;
    private final CareAuditService auditService;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    @Value("${aethercare.kafka.topics.event-created}")
    private String eventCreatedTopic;

    /** metadata JSON 序列化後上限（8 KiB），防止過大 payload 壓垮 DB / Kafka。 */
    private static final int METADATA_MAX_BYTES = 8 * 1024;

    /**
     * 長輩端 self-service 防呆：同 elder 同 type 在 dedupe 窗內視為手抖連按，
     * 回傳既有 event 而非新建。涵蓋緊急族（SOS / FALL）以及不適族（FEELING_UNWELL）—
     * 這些是按錯成本最高的。被動偵測類（MISSED_CHECK_IN / NO_ACTIVITY）由 scanner
     * 自帶去重，不在此 list。
     */
    private static final Set<CareEventType> DEDUPE_TYPES = EnumSet.of(
            CareEventType.SOS,
            CareEventType.FALL_DETECTED,
            CareEventType.POSSIBLE_FALL,
            CareEventType.FEELING_UNWELL);

    private static final Duration DEDUPE_WINDOW = Duration.ofSeconds(3);

    @Transactional
    public CareEventResult createAndStartWorkflow(CreateCareEventRequest req) {
        OffsetDateTime now = clock.now();

        // 短時窗 dedupe：手抖 / 緊急按鈕連按 → 回傳既有 event，不新建第二筆事件 / 第二封 LINE。
        if (DEDUPE_TYPES.contains(req.getEventType())) {
            Optional<CareEvent> recent = repo.findFirstByElderIdAndEventTypeAndCreatedAtAfterOrderByCreatedAtDesc(
                    req.getElderId(), req.getEventType(), now.minus(DEDUPE_WINDOW));
            if (recent.isPresent()) {
                CareEvent existing = recent.get();
                CareWorkflowInstance existingWf = workflowRepo
                        .findFirstByEventIdOrderByIdDesc(existing.getId())
                        .orElse(null);
                log.info("Event dedupe 命中 elderId={} type={} 既有 eventId={} 距今={}秒，回傳既有",
                        req.getElderId(), req.getEventType(), existing.getId(),
                        Duration.between(existing.getCreatedAt(), now).getSeconds());
                return new CareEventResult(existing, existingWf);
            }
        }

        RiskLevel riskLevel = decisionService.classify(req.getEventType());
        String metadataJson;
        try {
            metadataJson = req.getMetadata() == null ? null : objectMapper.writeValueAsString(req.getMetadata());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "metadata 序列化失敗：" + e.getMessage());
        }
        if (metadataJson != null && metadataJson.length() > METADATA_MAX_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "metadata 過大：" + metadataJson.length() + " bytes，上限 " + METADATA_MAX_BYTES);
        }

        CareEvent event = CareEvent.builder()
                .tenantId(TenantContext.getOrDefault())
                .elderId(req.getElderId())
                .source(req.getSource())
                .eventType(req.getEventType())
                .riskLevel(riskLevel)
                .status(CareEventStatus.RECEIVED)
                .metadata(metadataJson)
                .occurredAt(req.getOccurredAt())
                .createdAt(now)
                .updatedAt(now)
                .build();
        event = repo.save(event);

        publisher.publishEvent(new PublishToKafka(
                eventCreatedTopic,
                String.valueOf(event.getId()),
                new CareEventCreatedMessage(event.getId(), event.getElderId(),
                        event.getSource(), event.getEventType(), riskLevel,
                        event.getOccurredAt(), req.getMetadata())));

        // EVENT_CREATED audit 由 workflowService.start() 內部以 wf.id 寫入，
        // 避免 care_audit_log.workflow_id NOT NULL 違反。
        CareWorkflowInstance workflow = workflowService.start(event);

        event.setStatus(CareEventStatus.PROCESSED);
        event.setUpdatedAt(clock.now());
        repo.save(event);

        return new CareEventResult(event, workflow);
    }

    public record CareEventResult(CareEvent event, CareWorkflowInstance workflow) {
    }

    @Transactional(readOnly = true)
    public Optional<CareEvent> findById(Long id) {
        return repo.findById(id);
    }
}
