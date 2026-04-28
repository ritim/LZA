package com.lza.aethercare.workflow.dto;

import com.lza.aethercare.task.enums.AssigneeType;
import com.lza.aethercare.task.enums.CareTaskStatus;

import java.time.OffsetDateTime;

/** Workflow 巢狀任務摘要：供 GET workflow 端點呈現各 level 任務狀態。 */
public record CareTaskSummary(
        Long taskId,
        int level,
        Long assigneeId,
        AssigneeType assigneeType,
        CareTaskStatus status,
        OffsetDateTime deadlineAt,
        OffsetDateTime acknowledgedAt,
        OffsetDateTime completedAt
) {
}
