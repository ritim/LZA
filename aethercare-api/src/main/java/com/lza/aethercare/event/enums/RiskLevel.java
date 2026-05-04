package com.lza.aethercare.event.enums;

/**
 * 風險等級：對應 SLA 嚴重度。CRITICAL 用於 spec §5 立即升級事件
 * （目前 decision 規則尚未產出 CRITICAL，但欄位必須存在以便前端 spec 對齊）。
 */
public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
