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
import com.lza.aethercare.event.enums.RiskLevel;
import com.lza.aethercare.event.event.CareEventCreatedMessage;
import com.lza.aethercare.event.repository.CareEventRepository;
import com.lza.aethercare.workflow.entity.CareWorkflowInstance;
import com.lza.aethercare.workflow.service.CareWorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

/** Event service：建立事件並啟動 workflow（單一 transaction）。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CareEventService {

    private final CareEventRepository repo;
    private final DecisionService decisionService;
    private final CareWorkflowService workflowService;
    private final CareAuditService auditService;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    @Value("${aethercare.kafka.topics.event-created}")
    private String eventCreatedTopic;

    @Transactional
    public CareEventResult createAndStartWorkflow(CreateCareEventRequest req) {
        OffsetDateTime now = clock.now();
        RiskLevel riskLevel = decisionService.classify(req.getEventType());
        String metadataJson;
        try {
            metadataJson = req.getMetadata() == null ? null : objectMapper.writeValueAsString(req.getMetadata());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "metadata 序列化失敗：" + e.getMessage());
        }

        CareEvent event = CareEvent.builder()
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
