package com.lza.aethercare.userprofile.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * Spec § Master §7：PUT /api/v1/care-recipients/{id}/observation-settings request。
 *
 * <p>所有欄位皆可選；缺漏者沿用既有值（partial update）。{@code expectedCheckinTime} 設成 null
 * 代表「不設定每日 check-in」。grace / inactive minutes 範圍卡 1 分鐘 ~ 24 小時。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateObservationSettingsRequest {

    private LocalTime expectedCheckinTime;

    @Min(0) @Max(720)
    private Integer checkinGraceMinutes;

    @Min(0) @Max(1440)
    private Integer maxInactiveMinutesDaytime;

    @Min(0) @Max(1440)
    private Integer maxInactiveMinutesNight;

    private Boolean passiveMonitoringEnabled;

    /** 自由 JSON string，service 層會做基本格式驗證。 */
    private String escalationPolicyJson;
}
