package com.lza.aethercare.recipient.dto;

import java.time.OffsetDateTime;

/**
 * 被照顧者 check-in 回應：spec §0 規定 CHECK_IN 不啟動 workflow，回應只回 activity log id 與時間。
 */
public record RecipientCheckInResponse(
        Long activityLogId,
        Long careRecipientId,
        OffsetDateTime occurredAt
) {
}
