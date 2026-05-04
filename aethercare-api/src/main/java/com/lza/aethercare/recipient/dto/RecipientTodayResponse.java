package com.lza.aethercare.recipient.dto;

import java.time.OffsetDateTime;

/**
 * Spec § Master §7：GET /api/v1/recipient/today 摘要，給被照顧者端首頁顯示。
 *
 * <p>只暴露最低限度資訊：今日是否已 check-in、目前是否有未結案事件，避免 PII 過度暴露給
 * 不安裝 caregiver 端的設備（被照顧者裝置常被多人共用）。
 */
public record RecipientTodayResponse(
        Long careRecipientId,
        OffsetDateTime latestCheckInAt,
        boolean checkedInToday,
        long openEventsCount,
        OffsetDateTime serverTime
) {
}
