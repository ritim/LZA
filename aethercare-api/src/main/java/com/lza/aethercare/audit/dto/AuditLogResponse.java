package com.lza.aethercare.audit.dto;

import com.lza.aethercare.audit.enums.CareAuditAction;

import java.time.OffsetDateTime;

/** Audit log response：對應 GET /api/v1/workflows/{id}/audit-logs。 */
public record AuditLogResponse(
        Long auditId,
        CareAuditAction action,
        String message,
        Long actorId,
        OffsetDateTime createdAt
) {
}
