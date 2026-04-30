package com.lza.aethercare.assessment.controller;

import com.lza.aethercare.assessment.dto.AssessmentAnswerRequest;
import com.lza.aethercare.assessment.dto.AssessmentAnswerResponse;
import com.lza.aethercare.assessment.service.AssessmentAnswerService;
import com.lza.aethercare.common.security.AppUserDetails;
import com.lza.aethercare.common.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Caregiver 提交評估答案 controller：受 USER role 保護（透過 SecurityConfig
 * {@code /api/v1/workflows/**} 規則 cover）。
 */
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
@Slf4j
public class AssessmentAnswerController {

    private final AssessmentAnswerService service;

    @PostMapping("/{workflowId}/assessment-answers")
    public ResponseEntity<AssessmentAnswerResponse> submit(
            @PathVariable Long workflowId,
            @CurrentUser AppUserDetails user,
            @Valid @RequestBody AssessmentAnswerRequest req) {
        Long actorId = user != null ? user.getId() : null;
        log.info("提交評估答案 workflowId={} actor={} count={}", workflowId, actorId,
                req.getAnswers() == null ? 0 : req.getAnswers().size());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.submit(workflowId, actorId, req));
    }
}
