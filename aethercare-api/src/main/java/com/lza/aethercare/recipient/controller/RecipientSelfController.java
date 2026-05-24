package com.lza.aethercare.recipient.controller;

import com.lza.aethercare.anomaly.dto.CreateActivityRequest;
import com.lza.aethercare.anomaly.entity.ElderActivityEvent;
import com.lza.aethercare.anomaly.enums.ActivityType;
import com.lza.aethercare.anomaly.repository.ElderActivityEventRepository;
import com.lza.aethercare.anomaly.service.ActivityIngestionService;
import com.lza.aethercare.common.error.BusinessException;
import com.lza.aethercare.common.error.ErrorCode;
import com.lza.aethercare.common.time.Clock;
import com.lza.aethercare.event.dto.CreateCareEventRequest;
import com.lza.aethercare.event.entity.CareEvent;
import com.lza.aethercare.event.enums.CareEventSource;
import com.lza.aethercare.event.enums.CareEventStatus;
import com.lza.aethercare.event.enums.CareEventType;
import com.lza.aethercare.event.repository.CareEventRepository;
import com.lza.aethercare.event.service.CareEventService;
import com.lza.aethercare.event.service.CareEventService.CareEventResult;
import com.lza.aethercare.recipient.dto.RecipientCheckInRequest;
import com.lza.aethercare.recipient.service.RecipientNotificationService;
import com.lza.aethercare.recipient.dto.RecipientCheckInResponse;
import com.lza.aethercare.recipient.dto.RecipientEventResponse;
import com.lza.aethercare.recipient.dto.RecipientSosRequest;
import com.lza.aethercare.recipient.dto.RecipientStatusReportRequest;
import com.lza.aethercare.recipient.dto.RecipientTodayResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spec § Master §7 / Roadmap Phase 1：被照顧者自助 endpoint。
 *
 * <p>Recipient ID 解析優先序：{@code X-Care-Recipient-Id} header → request body 內的
 * {@code careRecipientId} → query param（today endpoint）。任一缺失視為 400。
 *
 * <p>SecurityConfig 已將 {@code /api/v1/recipient/**} permitAll，因此這裡不依賴 JWT；
 * production 必須換成真實 recipient 認證（OTP 或 device-bound token）。
 */
@RestController
@RequestMapping("/api/v1/recipient")
@RequiredArgsConstructor
@Slf4j
public class RecipientSelfController {

    private static final String RECIPIENT_HEADER = "X-Care-Recipient-Id";

    private final ActivityIngestionService activityIngestionService;
    private final CareEventService careEventService;
    private final ElderActivityEventRepository activityRepo;
    private final CareEventRepository careEventRepo;
    private final RecipientNotificationService notificationService;
    private final Clock clock;

    /** 我今天還好：寫 CHECK_IN activity，不啟動 workflow（spec §0 Required Backend Behavior）。 */
    @PostMapping("/check-ins")
    public ResponseEntity<RecipientCheckInResponse> checkIn(
            @RequestHeader(value = RECIPIENT_HEADER, required = false) Long headerId,
            @Valid @RequestBody(required = false) RecipientCheckInRequest body) {
        Long recipientId = resolveRecipientId(headerId, body == null ? null : body.getCareRecipientId());
        OffsetDateTime occurredAt = body != null && body.getOccurredAt() != null ? body.getOccurredAt() : clock.now();

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("triggeredBy", "RECIPIENT_BUTTON");
        if (body != null && body.getNote() != null) {
            metadata.put("note", body.getNote());
        }

        CreateActivityRequest req = CreateActivityRequest.builder()
                .activityType(ActivityType.CHECK_IN)
                .occurredAt(occurredAt)
                .durationSeconds(0)
                .metadata(metadata)
                .build();
        ElderActivityEvent saved = activityIngestionService.ingest(recipientId, req);
        log.info("RecipientCheckIn recipientId={} activityId={}", recipientId, saved.getId());

        // Step 2：簽到成功 → 推播家屬。stub gateway 不會阻塞 / 不會 throw；不啟動 workflow。
        notificationService.notifyCheckInReceived(recipientId, saved.getOccurredAt());

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new RecipientCheckInResponse(saved.getId(), recipientId, saved.getOccurredAt()));
    }

    /** 我需要幫忙：建 SOS（HIGH risk）並啟動 workflow + level-1 task + audit + mock notification。 */
    @PostMapping("/sos")
    public ResponseEntity<RecipientEventResponse> sos(
            @RequestHeader(value = RECIPIENT_HEADER, required = false) Long headerId,
            @Valid @RequestBody(required = false) RecipientSosRequest body) {
        Long recipientId = resolveRecipientId(headerId, body == null ? null : body.getCareRecipientId());

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("triggeredBy", "RECIPIENT_SOS_BUTTON");
        if (body != null) {
            if (body.getNote() != null) metadata.put("note", body.getNote());
            if (body.getLocation() != null) metadata.put("location", body.getLocation());
        }

        CareEventResult result = careEventService.createAndStartWorkflow(buildCareEventRequest(
                recipientId, CareEventType.SOS,
                body == null ? null : body.getOccurredAt(), metadata));
        log.info("RecipientSos recipientId={} eventId={} workflowId={}",
                recipientId, result.event().getId(), result.workflow().getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(toEventResponse(result));
    }

    /** 身體不舒服：建 FEELING_UNWELL（MEDIUM）；UI quick-questions 結果寫進 metadata.dangerSignals。 */
    @PostMapping("/status-reports")
    public ResponseEntity<RecipientEventResponse> statusReport(
            @RequestHeader(value = RECIPIENT_HEADER, required = false) Long headerId,
            @Valid @RequestBody(required = false) RecipientStatusReportRequest body) {
        Long recipientId = resolveRecipientId(headerId, body == null ? null : body.getCareRecipientId());

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("triggeredBy", "RECIPIENT_FEELING_UNWELL");
        if (body != null) {
            if (body.getSymptom() != null) metadata.put("symptom", body.getSymptom());
            if (body.getDangerSignals() != null && !body.getDangerSignals().isEmpty()) {
                metadata.put("dangerSignals", new HashMap<>(body.getDangerSignals()));
            }
        }

        CareEventResult result = careEventService.createAndStartWorkflow(buildCareEventRequest(
                recipientId, CareEventType.FEELING_UNWELL,
                body == null ? null : body.getOccurredAt(), metadata));
        log.info("RecipientStatusReport recipientId={} eventId={} workflowId={}",
                recipientId, result.event().getId(), result.workflow().getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(toEventResponse(result));
    }

    /** GET /today：被照顧者首頁摘要。 */
    @GetMapping("/today")
    public ResponseEntity<RecipientTodayResponse> today(
            @RequestHeader(value = RECIPIENT_HEADER, required = false) Long headerId,
            @RequestParam(value = "careRecipientId", required = false) Long queryId) {
        Long recipientId = resolveRecipientId(headerId, queryId);
        OffsetDateTime now = clock.now();
        OffsetDateTime startOfDay = now.toLocalDate().atStartOfDay().atOffset(now.getOffset());

        List<ElderActivityEvent> todayEvents =
                activityRepo.findByElderIdAndOccurredAtBetween(recipientId, startOfDay, now.plusSeconds(1));
        OffsetDateTime latestCheckIn = todayEvents.stream()
                .filter(e -> e.getActivityType() == ActivityType.CHECK_IN)
                .map(ElderActivityEvent::getOccurredAt)
                .max(OffsetDateTime::compareTo)
                .orElse(null);

        long openEvents = careEventRepo.findByElderIdOrderByOccurredAtDesc(recipientId).stream()
                .filter(this::isOpen)
                .count();

        return ResponseEntity.ok(new RecipientTodayResponse(
                recipientId, latestCheckIn, latestCheckIn != null, openEvents, now));
    }

    private CreateCareEventRequest buildCareEventRequest(
            Long recipientId, CareEventType type, OffsetDateTime occurredAt, Map<String, Object> metadata) {
        CreateCareEventRequest req = new CreateCareEventRequest();
        req.setElderId(recipientId);
        req.setSource(CareEventSource.MOBILE_APP);
        req.setEventType(type);
        req.setOccurredAt(occurredAt != null ? occurredAt : clock.now());
        req.setMetadata(metadata);
        return req;
    }

    private boolean isOpen(CareEvent e) {
        // RECEIVED / PROCESSED 視為仍在處理；DISCARDED 才是真正結案。
        // 注：CareEventStatus 沒有獨立 RESOLVED — 結案是由 workflow 端反映；展示用此估算即可。
        return e.getStatus() != CareEventStatus.DISCARDED;
    }

    private RecipientEventResponse toEventResponse(CareEventResult result) {
        return new RecipientEventResponse(
                result.event().getId(),
                result.workflow().getId(),
                result.event().getElderId(),
                result.event().getEventType(),
                result.event().getRiskLevel(),
                result.event().getOccurredAt());
    }

    private Long resolveRecipientId(Long headerId, Long bodyId) {
        Long resolved = headerId != null ? headerId : bodyId;
        if (resolved == null || resolved <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "缺 careRecipientId（請帶 X-Care-Recipient-Id header 或 body.careRecipientId）");
        }
        return resolved;
    }
}
