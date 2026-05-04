package com.lza.aethercare.event.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lza.aethercare.common.error.BusinessException;
import com.lza.aethercare.common.error.ErrorCode;
import com.lza.aethercare.common.util.PiiMasker;
import com.lza.aethercare.event.dto.CareEventDetailResponse;
import com.lza.aethercare.event.dto.CareEventResponse;
import com.lza.aethercare.event.dto.CreateCareEventRequest;
import com.lza.aethercare.event.entity.CareEvent;
import com.lza.aethercare.event.service.CareEventService;
import com.lza.aethercare.event.service.CareEventService.CareEventResult;
import com.lza.aethercare.workflow.repository.CareWorkflowInstanceRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 照護事件 REST Controller：接收事件並同步啟動照護流程，並提供 spec §6.2 詳情查詢。
 */
@RestController
@RequestMapping("/api/v1/care-events")
@RequiredArgsConstructor
@Slf4j
public class CareEventController {

    private final CareEventService careEventService;
    private final CareWorkflowInstanceRepository workflowRepo;
    private final ObjectMapper objectMapper;

    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {};

    /**
     * 建立照護事件並啟動對應 workflow（同步）。
     */
    @PostMapping
    public ResponseEntity<CareEventResponse> createCareEvent(
            @Valid @RequestBody CreateCareEventRequest req) {
        log.info("收到照護事件請求 elderId={} eventType={}",
                PiiMasker.maskId(req.getElderId()), req.getEventType());
        CareEventResult result = careEventService.createAndStartWorkflow(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result));
    }

    /**
     * Spec §6.2：查詢事件詳情，含 sensorSummary 解析。
     */
    @GetMapping("/{eventId}")
    public ResponseEntity<CareEventDetailResponse> getCareEvent(@PathVariable Long eventId) {
        log.info("查詢照護事件 eventId={}", eventId);
        CareEvent event = careEventService.findById(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "care event " + eventId));

        Long workflowId = workflowRepo.findFirstByEventIdOrderByIdDesc(eventId)
                .map(w -> w.getId())
                .orElse(null);

        Map<String, Object> metadata = parseMetadata(event.getMetadata());
        CareEventDetailResponse.SensorSummary sensorSummary = buildSensorSummary(metadata, event);
        String location = metadata.get("location") instanceof String s ? s : null;

        return ResponseEntity.ok(new CareEventDetailResponse(
                event.getId(),
                event.getElderId(),
                workflowId,
                event.getEventType(),
                event.getRiskLevel(),
                event.getStatus(),
                location,
                event.getOccurredAt(),
                event.getCreatedAt(),
                sensorSummary,
                metadata
        ));
    }

    private Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Map<String, Object> map = objectMapper.readValue(json, METADATA_TYPE);
            return map == null ? Map.of() : new HashMap<>(map);
        } catch (IOException e) {
            log.warn("metadata 解析失敗 raw={}", json, e);
            return Map.of();
        }
    }

    private CareEventDetailResponse.SensorSummary buildSensorSummary(
            Map<String, Object> metadata, CareEvent event) {
        Integer noMovementSeconds = asInteger(metadata.get("noMovementSeconds"));
        Double fallConfidence = asDouble(metadata.get("fallConfidence"));
        if (fallConfidence == null) {
            fallConfidence = asDouble(metadata.get("confidence"));
        }
        String source = event.getSource() == null ? null : event.getSource().name();
        if (noMovementSeconds == null && fallConfidence == null && source == null) {
            return null;
        }
        return new CareEventDetailResponse.SensorSummary(noMovementSeconds, fallConfidence, source);
    }

    private static Integer asInteger(Object v) {
        if (v instanceof Number n) return n.intValue();
        return null;
    }

    private static Double asDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        return null;
    }

    private static CareEventResponse toResponse(CareEventResult result) {
        return new CareEventResponse(
                result.event().getId(),
                result.event().getElderId(),
                result.event().getEventType(),
                result.event().getRiskLevel(),
                result.event().getStatus(),
                result.workflow().getId(),
                result.event().getOccurredAt()
        );
    }
}
