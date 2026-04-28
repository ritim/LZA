package com.lza.aethercare.anomaly.dto;

import com.lza.aethercare.anomaly.enums.ActivityType;

import java.time.OffsetDateTime;

/** 活動事件 response。 */
public record ActivityResponse(
        Long id,
        Long elderId,
        ActivityType activityType,
        OffsetDateTime occurredAt,
        Integer durationSeconds,
        OffsetDateTime createdAt
) {
}
