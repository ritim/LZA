package com.lza.aethercare.aichat.enums;

/**
 * Spec § AI_Care_Chat §8：訊息來源。
 *
 * <ul>
 *   <li>{@link #CAREGIVER_INPUT}：照顧者輸入的訊息</li>
 *   <li>{@link #STATIC_GUIDANCE}：以 static knowledge JSON 為基礎產生的 ASSISTANT 回應</li>
 *   <li>{@link #RULE_ENGINE}：由 deterministic rules 額外產生的 ASSISTANT 回應</li>
 *   <li>{@link #LLM}：由外部 LLM（如 Anthropic Claude API）生成的 reply 字串；結構化欄位仍由 rules engine 決定</li>
 *   <li>{@link #SYSTEM}：系統訊息（如 chat 開啟提示）</li>
 * </ul>
 */
public enum ChatSource {
    CAREGIVER_INPUT,
    STATIC_GUIDANCE,
    RULE_ENGINE,
    LLM,
    SYSTEM
}
