package com.lza.aethercare.userprofile.dto;

import com.lza.aethercare.event.enums.CareEventStatus;
import com.lza.aethercare.event.enums.CareEventType;
import com.lza.aethercare.event.enums.RiskLevel;

import java.time.OffsetDateTime;

/** Spec §3.4：GET /api/elders/{id}/events 回應 item，僅暴露 caregiver 需要的欄位。 */
public record ElderEventItem(
        Long eventId,
        CareEventType eventType,
        RiskLevel riskLevel,
        CareEventStatus status,
        OffsetDateTime occurredAt
) {
}
