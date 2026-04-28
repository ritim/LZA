package com.lza.aethercare.anomaly.enums;

/** 長者活動類型：sensor / app 上報的活動分類，作為 baseline / 異常偵測的維度。 */
public enum ActivityType {
    WAKE_UP,
    MOVE,
    MEAL,
    SLEEP,
    BATHROOM,
    MEDICATION
}
