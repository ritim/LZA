package com.lza.aethercare.ai.enums;

/**
 * AI care-knowledge 事件類型：對應 {@code src/main/resources/care-knowledge/*.json} 檔名。
 *
 * <p>故意與 {@link com.lza.aethercare.event.enums.CareEventType} 分離，因為 knowledge 的
 * 命名語義（FALL / STROKE / WANDERING）與 sensor 上報事件（FALL_DETECTED / SOS）不同；
 * 兩者轉換由 {@code EventTypeMapper} 負責。
 */
public enum KnowledgeEventType {
    FALL,
    POSSIBLE_FALL,
    NO_ACTIVITY,
    MISSED_CHECK_IN,
    NO_RESPONSE,
    FEELING_UNWELL,
    STROKE,
    WANDERING,
    BREATHING_ISSUE,
    OTHER
}
