package com.lza.aethercare.common.time;

import java.time.OffsetDateTime;

/** 時鐘抽象：方便測試替換固定時間。 */
public interface Clock {

    /** 取得目前時間（OffsetDateTime）。 */
    OffsetDateTime now();
}
