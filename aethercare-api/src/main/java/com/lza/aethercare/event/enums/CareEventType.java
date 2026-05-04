package com.lza.aethercare.event.enums;

/**
 * 照護事件類型：對應前端 / sensor 上報的事件種類。
 *
 * <p>spec § Master Spec §5 列出 MVP 必要事件型別。新增條目務必同步：
 * <ul>
 *   <li>{@link com.lza.aethercare.decision.service.RuleBasedRiskClassifier} 的 risk / workflow Map</li>
 *   <li>{@link com.lza.aethercare.ai.service.EventTypeMapper} 的 switch（exhaustive）</li>
 *   <li>aethercare-web {@code src/api/types.ts} 的 {@code CareEventType} union</li>
 * </ul>
 */
public enum CareEventType {
    FALL_DETECTED,
    POSSIBLE_FALL,
    NO_ACTIVITY,
    DAILY_REMINDER,
    SOS,
    ACTIVITY_ANOMALY,
    MISSED_CHECK_IN,
    NO_RESPONSE,
    FEELING_UNWELL
}
