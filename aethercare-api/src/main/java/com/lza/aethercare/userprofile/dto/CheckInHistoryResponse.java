package com.lza.aethercare.userprofile.dto;

import java.time.LocalTime;
import java.util.List;

/**
 * Spec § Master §0：被照顧者最近 N 天 check-in 歷史（日曆 view 用）。
 *
 * <p>{@code items} 依日期升冪排列，包含起始日到今日（Asia/Taipei）。
 */
public record CheckInHistoryResponse(
        Long careRecipientId,
        LocalTime expectedCheckInTime,
        int graceMinutes,
        int days,
        List<CheckInDayItem> items
) {
}
