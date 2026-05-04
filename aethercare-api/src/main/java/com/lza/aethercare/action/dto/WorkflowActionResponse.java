package com.lza.aethercare.action.dto;

import com.lza.aethercare.action.enums.CareActionType;
import com.lza.aethercare.audit.dto.TimelineItem;
import com.lza.aethercare.task.enums.CareTaskStatus;
import com.lza.aethercare.workflow.enums.CareWorkflowStatus;

import java.util.List;

/**
 * Spec §6.7 回應：含 task 與 workflow 處理後狀態 + 同 transaction 寫出後的最新 timeline。
 */
public record WorkflowActionResponse(
        Long workflowId,
        Long eventId,
        Long taskId,
        CareActionType actionType,
        CareWorkflowStatus workflowStatus,
        CareTaskStatus taskStatus,
        String message,
        List<TimelineItem> timeline
) {
}
