package com.lza.aethercare.event.dto;

import com.lza.aethercare.event.enums.CareEventSource;
import com.lza.aethercare.event.enums.CareEventStatus;
import com.lza.aethercare.event.enums.CareEventType;
import com.lza.aethercare.event.enums.RiskLevel;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Spec §6.2 GET /api/care-events/{id} 詳情。
 *
 * <p>{@code sensorSummary} 由 metadata JSON 解析常見鍵（noMovementSeconds / fallConfidence /
 * source）；無對應鍵時保留為 null。
 */
public record CareEventDetailResponse(
        Long id,
        Long elderId,
        Long workflowId,
        CareEventType type,
        RiskLevel riskLevel,
        CareEventStatus status,
        String location,
        OffsetDateTime detectedAt,
        OffsetDateTime createdAt,
        SensorSummary sensorSummary,
        Map<String, Object> metadata
) {
    public record SensorSummary(
            Integer noMovementSeconds,
            Double fallConfidence,
            String source
    ) {
    }
}
