package com.lza.aethercare.recipient.dto;

import com.lza.aethercare.event.enums.CareEventType;
import com.lza.aethercare.event.enums.RiskLevel;

import java.time.OffsetDateTime;

/**
 * 被照顧者主動觸發的 SOS / FEELING_UNWELL 回應：給前端確認 workflow 已啟動。
 */
public record RecipientEventResponse(
        Long eventId,
        Long workflowId,
        Long careRecipientId,
        CareEventType eventType,
        RiskLevel riskLevel,
        OffsetDateTime occurredAt
) {
}
