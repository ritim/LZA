package com.lza.aethercare.common.time;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/** 預設時鐘實作：直接回傳系統時間。 */
@Component
public class SystemClock implements Clock {

    @Override
    public OffsetDateTime now() {
        return OffsetDateTime.now();
    }
}
