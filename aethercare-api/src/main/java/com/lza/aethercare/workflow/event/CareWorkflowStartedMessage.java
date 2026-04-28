package com.lza.aethercare.workflow.event;

import com.lza.aethercare.event.enums.RiskLevel;
import com.lza.aethercare.workflow.enums.CareWorkflowType;

import java.time.OffsetDateTime;

/** Kafka payload：care.workflow.started。 */
public record CareWorkflowStartedMessage(
        Long workflowId,
        Long eventId,
        Long elderId,
        CareWorkflowType workflowType,
        RiskLevel riskLevel,
        OffsetDateTime startedAt
) {
}
