package com.lza.aethercare.action.controller;

import com.lza.aethercare.action.dto.CreateCareActionRequest;
import com.lza.aethercare.action.dto.WorkflowActionRequest;
import com.lza.aethercare.action.dto.WorkflowActionResponse;
import com.lza.aethercare.action.entity.CareAction;
import com.lza.aethercare.action.service.CareActionService;
import com.lza.aethercare.audit.service.TimelineService;
import com.lza.aethercare.common.error.BusinessException;
import com.lza.aethercare.common.error.ErrorCode;
import com.lza.aethercare.common.security.AppUserDetails;
import com.lza.aethercare.common.util.PiiMasker;
import com.lza.aethercare.task.entity.CareTask;
import com.lza.aethercare.task.service.CareTaskService;
import com.lza.aethercare.workflow.entity.CareWorkflowInstance;
import com.lza.aethercare.workflow.service.CareWorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spec §6.7 POST /api/workflows/{id}/actions：workflow-level 動作入口。
 *
 * <p>本 controller 為 spec 對齊外殼，內部 delegate 到既有 {@link CareActionService}
 * （task-level）。同時保留 {@code POST /api/v1/care-tasks/{taskId}/actions} 向後相容。
 *
 * <p>受 USER role 保護（見 SecurityConfig {@code /api/v1/workflows/**}）。
 */
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
@Slf4j
public class WorkflowActionController {

    private final CareActionService careActionService;
    private final CareTaskService careTaskService;
    private final CareWorkflowService careWorkflowService;
    private final TimelineService timelineService;

    @PostMapping("/{workflowId}/actions")
    public ResponseEntity<WorkflowActionResponse> handle(
            @PathVariable Long workflowId,
            @AuthenticationPrincipal AppUserDetails currentUser,
            @Valid @RequestBody WorkflowActionRequest req) {
        Long actorId = currentUser != null ? currentUser.getId() : null;
        log.info("收到 workflow 動作 workflowId={} taskId={} actionType={} actorId={}",
                workflowId, req.getTaskId(), req.getActionType(), PiiMasker.maskId(actorId));

        // 防呆：task 必須屬於這個 workflow，避免跨流程動作
        CareTask task = careTaskService.findById(req.getTaskId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "task=" + req.getTaskId()));
        if (!task.getWorkflowId().equals(workflowId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "task " + req.getTaskId() + " 不屬於 workflow " + workflowId);
        }

        CreateCareActionRequest delegate = new CreateCareActionRequest();
        delegate.setActionType(req.getActionType());
        delegate.setNote(req.getNote());

        CareAction action = careActionService.handle(req.getTaskId(), actorId, delegate);

        // 重新 fetch 取最新狀態（handle 已 commit）
        CareTask refreshedTask = careTaskService.findById(req.getTaskId()).orElse(task);
        CareWorkflowInstance workflow = careWorkflowService.findById(workflowId);

        return ResponseEntity.status(HttpStatus.CREATED).body(new WorkflowActionResponse(
                workflowId,
                task.getEventId(),
                req.getTaskId(),
                action.getActionType(),
                workflow.getStatus(),
                refreshedTask.getStatus(),
                "事件已更新",
                timelineService.buildForWorkflow(workflowId).items()
        ));
    }
}
