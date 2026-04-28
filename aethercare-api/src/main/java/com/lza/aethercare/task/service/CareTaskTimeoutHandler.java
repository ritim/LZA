package com.lza.aethercare.task.service;

import com.lza.aethercare.audit.enums.CareAuditAction;
import com.lza.aethercare.audit.service.CareAuditService;
import com.lza.aethercare.task.entity.CareTask;
import com.lza.aethercare.workflow.service.CareWorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 單獨 bean 持有 @Transactional method，讓 scanner 透過代理呼叫以啟用 transaction。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CareTaskTimeoutHandler {

    private final CareTaskService taskService;
    private final CareWorkflowService workflowService;
    private final CareAuditService auditService;

    @Transactional
    public void handleTimeout(Long taskId) {
        CareTask task = taskService.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("task disappeared id=" + taskId));
        if (!taskService.markTimeoutIfPending(taskId)) {
            log.debug("taskId={} 已被其他 worker 處理，跳過", taskId);
            return;
        }
        auditService.log(task.getWorkflowId(), task.getEventId(), null,
                CareAuditAction.TASK_TIMEOUT, "task=" + taskId + " 已超時");
        workflowService.escalate(task, null);
    }
}
