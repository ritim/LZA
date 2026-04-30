package com.lza.aethercare.ai.controller;

import com.lza.aethercare.ai.dto.CareGuidanceResponse;
import com.lza.aethercare.ai.service.AiCareGuidanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 照護指引 REST controller：caregiver 接到 task 後查詢結構化指引與評估問題。
 *
 * <p>受 USER role 保護（見 SecurityConfig）。
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Slf4j
public class AiCareGuidanceController {

    private final AiCareGuidanceService service;

    @GetMapping("/care-guidance")
    public ResponseEntity<CareGuidanceResponse> guidance(
            @RequestParam Long eventId,
            @RequestParam Long workflowId) {
        log.info("查詢 AI 指引 eventId={} workflowId={}", eventId, workflowId);
        return ResponseEntity.ok(service.generate(eventId, workflowId));
    }
}
