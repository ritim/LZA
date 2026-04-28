package com.lza.aethercare.event.event;

import com.lza.aethercare.event.enums.CareEventSource;
import com.lza.aethercare.event.enums.CareEventType;
import com.lza.aethercare.event.enums.RiskLevel;

import java.time.OffsetDateTime;
import java.util.Map;

/** Kafka payload：care.event.created。 */
public record CareEventCreatedMessage(
        Long eventId,
        Long elderId,
        CareEventSource source,
        CareEventType eventType,
        RiskLevel riskLevel,
        OffsetDateTime occurredAt,
        Map<String, Object> metadata
) {
}
