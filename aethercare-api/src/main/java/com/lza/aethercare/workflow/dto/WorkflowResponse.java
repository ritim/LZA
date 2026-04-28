package com.lza.aethercare.workflow.dto;

import com.lza.aethercare.event.enums.RiskLevel;
import com.lza.aethercare.workflow.enums.CareWorkflowStatus;
import com.lza.aethercare.workflow.enums.CareWorkflowType;

import java.time.OffsetDateTime;
import java.util.List;

/** Workflow 完整 response：含狀態機與所有層級任務摘要。 */
public record WorkflowResponse(
        Long workflowId,
        Long eventId,
        Long elderId,
        CareWorkflowType workflowType,
        RiskLevel riskLevel,
        CareWorkflowStatus status,
        int currentLevel,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        List<CareTaskSummary> tasks
) {
}
