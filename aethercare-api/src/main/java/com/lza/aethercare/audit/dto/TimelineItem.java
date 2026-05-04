package com.lza.aethercare.audit.dto;

import com.lza.aethercare.audit.enums.CareAuditAction;

import java.time.OffsetDateTime;

/**
 * Spec §6.8 timeline item：對外結構化呈現，{@code level} 由 action 推導
 * （INFO / WARNING / CRITICAL）。{@code actorName} 為 user displayName 或 "System"。
 */
public record TimelineItem(
        Long id,
        OffsetDateTime time,
        CareAuditAction type,
        String actorName,
        String message,
        String level
) {
}
