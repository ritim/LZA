package com.lza.aethercare.action.event;

import com.lza.aethercare.action.enums.CareActionType;

import java.time.OffsetDateTime;

/** Kafka payload：care.action.received。 */
public record CareActionReceivedMessage(
        Long actionId,
        Long workflowId,
        Long taskId,
        Long actorId,
        CareActionType actionType,
        OffsetDateTime createdAt
) {
}
