package com.lza.aethercare.anomaly.dto;

import com.lza.aethercare.anomaly.enums.ActivityType;

import java.time.OffsetDateTime;

/** Baseline response（保留簡單 DTO；目前 controller 用 Map 即可，留作未來擴充用）。 */
public record BaselineResponse(
        Long id,
        Long elderId,
        ActivityType activityType,
        int hourOfDay,
        double expectedCountMean,
        double expectedCountStddev,
        int sampleCount,
        OffsetDateTime updatedAt
) {
}
