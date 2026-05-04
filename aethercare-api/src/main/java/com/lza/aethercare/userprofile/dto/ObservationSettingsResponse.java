package com.lza.aethercare.userprofile.dto;

import java.time.LocalTime;

/**
 * Spec § Master §7：GET /api/v1/care-recipients/{id}/observation-settings 回應。
 *
 * <p>{@code expectedCheckinTime} 為 wall-clock time（無時區，依 household timezone 解讀）。
 */
public record ObservationSettingsResponse(
        Long careRecipientId,
        LocalTime expectedCheckinTime,
        int checkinGraceMinutes,
        int maxInactiveMinutesDaytime,
        int maxInactiveMinutesNight,
        boolean passiveMonitoringEnabled,
        String escalationPolicyJson
) {
}
