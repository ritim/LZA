package com.lza.aethercare.anomaly.enums;

/**
 * 長者活動類型：sensor / app 上報的活動分類，作為 baseline / 異常偵測的維度。
 *
 * <p>注意：JPA 用 default {@code @Enumerated(ORDINAL)}，新增條目務必只能 append 在最末（CHECK_IN
 * 在尾），避免改動既有 ordinal 而與歷史資料錯位。
 *
 * <p>{@code CHECK_IN}：被照顧者主動 heartbeat，{@code spec §0 Required Backend Behavior} 規定
 * 只寫 activity log、不啟動 workflow。BaselineCalculator 會將其一併跑 baseline，
 * 樣本不足時自動跳過。
 */
public enum ActivityType {
    WAKE_UP,
    MOVE,
    MEAL,
    SLEEP,
    BATHROOM,
    MEDICATION,
    CHECK_IN
}
