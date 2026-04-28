package com.lza.aethercare.event.dto;

import com.lza.aethercare.event.enums.CareEventStatus;
import com.lza.aethercare.event.enums.CareEventType;
import com.lza.aethercare.event.enums.RiskLevel;

import java.time.OffsetDateTime;

/** 照護事件 response：包含對應 workflow id 與風險評估結果。 */
public record CareEventResponse(
        Long eventId,
        Long elderId,
        CareEventType eventType,
        RiskLevel riskLevel,
        CareEventStatus status,
        Long workflowId,
        OffsetDateTime occurredAt
) {
}
