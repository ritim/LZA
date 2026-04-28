package com.lza.aethercare.common.time;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** 預設時鐘實作：統一回傳 UTC 時間，與 entity @PrePersist 及 DB TIMESTAMPTZ 一致。 */
@Component
public class SystemClock implements Clock {

    @Override
    public OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
