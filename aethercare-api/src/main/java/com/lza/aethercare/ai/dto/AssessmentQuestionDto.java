package com.lza.aethercare.ai.dto;

import java.util.List;

/** AI 提供給 caregiver 的評估問題 DTO（對應 spec §5.5 questions）。 */
public record AssessmentQuestionDto(
        String id,
        String question,
        String type,
        List<String> options,
        List<String> dangerAnswer
) {
}
