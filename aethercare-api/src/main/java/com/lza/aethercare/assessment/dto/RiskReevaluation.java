package com.lza.aethercare.assessment.dto;

/**
 * 風險重新評估結果：根據答案的 dangerDetected 推算下一步建議。
 *
 * <p>{@code recommendedAction} 與 {@link com.lza.aethercare.ai.dto.SuggestedActionDto#type()}
 * 字串對齊（例如 CALL_EMERGENCY）。前端依此 highlight 對應按鈕。
 */
public record RiskReevaluation(
        String riskLevel,
        boolean dangerDetected,
        String recommendedAction,
        String message
) {
}
