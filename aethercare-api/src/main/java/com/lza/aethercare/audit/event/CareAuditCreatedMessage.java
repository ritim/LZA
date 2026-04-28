package com.lza.aethercare.audit.event;

import com.lza.aethercare.audit.enums.CareAuditAction;

import java.time.OffsetDateTime;

/** Kafka payload：care.audit.created。 */
public record CareAuditCreatedMessage(
        Long auditId,
        Long workflowId,
        Long eventId,
        CareAuditAction action,
        String message,
        OffsetDateTime createdAt
) {
}
