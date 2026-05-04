package com.lza.aethercare.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lza.aethercare.ai.enums.KnowledgeEventType;
import com.lza.aethercare.ai.knowledge.CareKnowledge;
import com.lza.aethercare.ai.knowledge.CareKnowledgeBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec § Master §10 Required Tests：「Static guidance returns valid structured JSON for
 * NO_RESPONSE and NO_ACTIVITY」與 § Gap G「靜態知識庫 5 份檔案」。
 *
 * <p>本測試對所有 spec 規定的 knowledge type 驗證：
 * <ul>
 *   <li>JSON 載入成功，summary / guidance / questions / suggestedActions / dangerSigns / disclaimer 不為 null/空</li>
 *   <li>spec § §8「不可說 definitely safe / 不可關 workflow」核心：disclaimer 應提到「不能取代醫療診斷」之類字樣</li>
 * </ul>
 */
class StaticGuidanceShapeTest {

    private static CareKnowledgeBase kb;

    @BeforeAll
    static void loadKnowledge() throws IOException {
        kb = new CareKnowledgeBase(new ObjectMapper());
        kb.loadAll();
    }

    @Test
    void no_response_returns_valid_structured_guidance() {
        Optional<CareKnowledge> ck = kb.lookup(KnowledgeEventType.NO_RESPONSE);
        assertThat(ck).isPresent();
        assertHasFullShape(ck.get());
    }

    @Test
    void no_activity_returns_valid_structured_guidance() {
        Optional<CareKnowledge> ck = kb.lookup(KnowledgeEventType.NO_ACTIVITY);
        assertThat(ck).isPresent();
        assertHasFullShape(ck.get());
    }

    /** spec § Gap G + § Master §8：核心 6 種 knowledge type 全部要齊全。 */
    @ParameterizedTest
    @EnumSource(value = KnowledgeEventType.class,
            names = {"FALL", "POSSIBLE_FALL", "NO_ACTIVITY", "MISSED_CHECK_IN",
                    "NO_RESPONSE", "FEELING_UNWELL"})
    void all_spec_required_types_have_complete_guidance(KnowledgeEventType type) {
        Optional<CareKnowledge> ck = kb.lookup(type);
        assertThat(ck).as("knowledge for %s", type).isPresent();
        assertHasFullShape(ck.get());
    }

    private void assertHasFullShape(CareKnowledge ck) {
        assertThat(ck.summary()).isNotBlank();
        assertThat(ck.guidance()).isNotEmpty();
        assertThat(ck.questions()).isNotEmpty();
        assertThat(ck.suggestedActions()).isNotEmpty();
        assertThat(ck.dangerSigns()).isNotEmpty();
        assertThat(ck.disclaimer())
                .as("disclaimer 必須提到不取代醫療診斷（spec §8 AI 邊界）")
                .containsAnyOf("不能取代醫療診斷", "不取代醫療診斷", "醫療緊急");
    }
}
