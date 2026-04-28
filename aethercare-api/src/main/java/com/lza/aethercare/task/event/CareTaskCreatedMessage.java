package com.lza.aethercare.task.event;

import com.lza.aethercare.task.enums.AssigneeType;

import java.time.OffsetDateTime;

/** Kafka payload：care.task.created。 */
public record CareTaskCreatedMessage(
        Long taskId,
        Long workflowId,
        Long eventId,
        Long assigneeId,
        AssigneeType assigneeType,
        Integer level,
        OffsetDateTime deadlineAt
) {
}
