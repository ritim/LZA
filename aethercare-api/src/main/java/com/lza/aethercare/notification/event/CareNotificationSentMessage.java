package com.lza.aethercare.notification.event;

import com.lza.aethercare.userprofile.enums.NotificationChannel;

import java.time.OffsetDateTime;

/** Kafka payload：care.notification.sent。 */
public record CareNotificationSentMessage(
        Long taskId,
        Long workflowId,
        NotificationChannel channel,
        Long assigneeId,
        OffsetDateTime sentAt
) {
}
