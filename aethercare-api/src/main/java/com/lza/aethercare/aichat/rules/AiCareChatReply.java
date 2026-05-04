package com.lza.aethercare.aichat.rules;

import com.lza.aethercare.ai.dto.AssessmentQuestionDto;
import com.lza.aethercare.ai.dto.SuggestedActionDto;

import java.util.List;

/**
 * Spec § AI_Care_Chat §6：rules engine 結構化輸出。
 * 純資料載體，由 service 包成 entity / response。
 */
public record AiCareChatReply(
        String reply,
        List<AssessmentQuestionDto> questions,
        List<SuggestedActionDto> suggestedActions,
        List<String> dangerSigns,
        String disclaimer
) {

    /** Spec § AI_Care_Chat §1：強制 disclaimer，避免被當醫療診斷。 */
    public static final String DEFAULT_DISCLAIMER =
            "此建議不能取代醫療診斷。若有危急狀況，請立即聯絡緊急服務。";
}
