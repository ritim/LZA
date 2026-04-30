package com.lza.aethercare.ai.knowledge;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.lza.aethercare.ai.dto.AssessmentQuestionDto;
import com.lza.aethercare.ai.dto.SuggestedActionDto;

import java.util.List;

/**
 * 對映 {@code care-knowledge/*.json} 結構：知識庫單筆條目，
 * 由 {@link CareKnowledgeBase} 啟動時透過 Jackson 反序列化載入。
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} 讓 JSON 多餘欄位（例如未來新增的
 * eventType / riskLevel meta）不會導致載入失敗。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CareKnowledge(
        String summary,
        List<String> guidance,
        List<AssessmentQuestionDto> questions,
        List<String> dangerSigns,
        List<SuggestedActionDto> suggestedActions,
        String disclaimer
) {
}
