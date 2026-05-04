package com.lza.aethercare.aichat.dto;

import com.lza.aethercare.ai.dto.AssessmentQuestionDto;
import com.lza.aethercare.ai.dto.SuggestedActionDto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Spec § AI_Care_Chat §4：POST /api/v1/ai/care-chat 回應。
 *
 * <p>{@code suggestedActions} 是「按鈕清單」；前端 click 時應呼叫 workflow action API
 * （需 caregiver 確認）才會真正改 state，spec § AI_Care_Chat §9 明示。
 */
public record AiCareChatResponse(
        Long messageId,
        Long workflowId,
        Long careEventId,
        String reply,
        List<AssessmentQuestionDto> questions,
        List<SuggestedActionDto> suggestedActions,
        List<String> dangerSigns,
        String disclaimer,
        OffsetDateTime generatedAt
) {
}
