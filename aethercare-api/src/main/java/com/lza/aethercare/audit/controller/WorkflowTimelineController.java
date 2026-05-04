package com.lza.aethercare.audit.controller;

import com.lza.aethercare.audit.dto.WorkflowTimelineResponse;
import com.lza.aethercare.audit.service.TimelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spec §6.8 GET /api/workflows/{id}/timeline：對外結構化時序，與既有
 * {@code /audit-logs} 同一資料來源但格式對齊 spec。
 */
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
@Slf4j
public class WorkflowTimelineController {

    private final TimelineService timelineService;

    @GetMapping("/{workflowId}/timeline")
    public ResponseEntity<WorkflowTimelineResponse> getTimeline(@PathVariable Long workflowId) {
        log.info("查詢 workflow timeline workflowId={}", workflowId);
        return ResponseEntity.ok(timelineService.buildForWorkflow(workflowId));
    }
}
