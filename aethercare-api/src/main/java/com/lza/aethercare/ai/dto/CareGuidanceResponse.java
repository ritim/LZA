package com.lza.aethercare.ai.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** AI 照護指引 response（對應 spec §5.5 AICareGuidance）。 */
public record CareGuidanceResponse(
        String summary,
        List<String> guidance,
        List<AssessmentQuestionDto> questions,
        List<SuggestedActionDto> suggestedActions,
        List<String> dangerSigns,
        String disclaimer,
        OffsetDateTime generatedAt
) {
}
