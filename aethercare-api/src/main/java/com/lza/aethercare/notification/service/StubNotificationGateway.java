package com.lza.aethercare.notification.service;

import com.lza.aethercare.common.util.PiiMasker;
import com.lza.aethercare.userprofile.enums.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Stub notification gateway：MVP 階段僅 log，永遠成功。 */
@Component
@Slf4j
public class StubNotificationGateway {

    public boolean send(NotificationChannel channel, Long assigneeId, String subject, String body) {
        log.info("[STUB-NOTIFY] channel={} assignee={} subject={} body={}",
                channel, PiiMasker.maskId(assigneeId), subject, body);
        return true;
    }
}
