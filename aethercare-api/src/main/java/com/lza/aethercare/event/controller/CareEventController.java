package com.lza.aethercare.event.controller;

import com.lza.aethercare.event.dto.CareEventResponse;
import com.lza.aethercare.event.dto.CreateCareEventRequest;
import com.lza.aethercare.event.service.CareEventService;
import com.lza.aethercare.event.service.CareEventService.CareEventResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 照護事件 REST Controller：接收事件並同步啟動照護流程。
 */
@RestController
@RequestMapping("/api/v1/care-events")
@RequiredArgsConstructor
@Slf4j
public class CareEventController {

    private final CareEventService careEventService;

    /**
     * 建立照護事件並啟動對應 workflow（同步）。
     *
     * @param req 建立事件請求
     * @return 201 Created，含事件與 workflow 識別資訊
     */
    @PostMapping
    public ResponseEntity<CareEventResponse> createCareEvent(
            @Valid @RequestBody CreateCareEventRequest req) {
        log.info("收到照護事件請求 elderId={} eventType={}", req.getElderId(), req.getEventType());
        CareEventResult result = careEventService.createAndStartWorkflow(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(result));
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
