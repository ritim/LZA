package com.lza.aethercare.workflow.controller;

import com.lza.aethercare.task.entity.CareTask;
import com.lza.aethercare.task.service.CareTaskService;
import com.lza.aethercare.workflow.dto.CareTaskSummary;
import com.lza.aethercare.workflow.dto.WorkflowResponse;
import com.lza.aethercare.workflow.entity.CareWorkflowInstance;
import com.lza.aethercare.workflow.service.CareWorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 照護流程 REST Controller：查詢 workflow 完整狀態與任務清單。
 */
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
@Slf4j
public class WorkflowController {

    private final CareWorkflowService careWorkflowService;
    private final CareTaskService careTaskService;

    /**
     * 查詢指定 workflow 的完整狀態、層級與所有任務摘要。
     *
     * @param workflowId workflow 識別碼
     * @return 200 OK，含 workflow 狀態機與巢狀任務清單
     */
    @GetMapping("/{workflowId}")
    public ResponseEntity<WorkflowResponse> getWorkflow(@PathVariable Long workflowId) {
        log.info("查詢 workflow workflowId={}", workflowId);
        CareWorkflowInstance workflow = careWorkflowService.findById(workflowId);
        List<CareTask> tasks = careTaskService.findByWorkflow(workflowId);
        return ResponseEntity.ok(toResponse(workflow, tasks));
    }

    private static WorkflowResponse toResponse(CareWorkflowInstance wf, List<CareTask> tasks) {
        List<CareTaskSummary> taskSummaries = tasks.stream()
                .map(t -> new CareTaskSummary(
                        t.getId(),
                        t.getLevel(),
                        t.getAssigneeId(),
                        t.getAssigneeType(),
                        t.getStatus(),
                        t.getDeadlineAt(),
                        t.getAcknowledgedAt(),
                        t.getCompletedAt()))
                .toList();

        return new WorkflowResponse(
                wf.getId(),
                wf.getEventId(),
                wf.getElderId(),
                wf.getWorkflowType(),
                wf.getRiskLevel(),
                wf.getStatus(),
                wf.getCurrentLevel(),
                wf.getStartedAt(),
                wf.getCompletedAt(),
                taskSummaries
        );
    }
}
