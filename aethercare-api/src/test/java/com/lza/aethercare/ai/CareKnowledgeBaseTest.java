package com.lza.aethercare.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lza.aethercare.ai.enums.KnowledgeEventType;
import com.lza.aethercare.ai.knowledge.CareKnowledge;
import com.lza.aethercare.ai.knowledge.CareKnowledgeBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CareKnowledgeBase 單元測試：驗證 5 個 JSON 都能載入，且 OTHER 沒有對應 entry。
 */
class CareKnowledgeBaseTest {

    private CareKnowledgeBase knowledgeBase;

    @BeforeEach
    void setUp() throws IOException {
        knowledgeBase = new CareKnowledgeBase(new ObjectMapper());
        knowledgeBase.loadAll();
    }

    /** 5 個 JSON 都應該成功載入並能 lookup 取得；FALL 必含 dangerSigns 與 questions。 */
    @Test
    void should_load_all_known_event_types() {
        Optional<CareKnowledge> fall = knowledgeBase.lookup(KnowledgeEventType.FALL);
        assertThat(fall).isPresent();
        assertThat(fall.get().questions()).isNotEmpty();
        assertThat(fall.get().dangerSigns()).isNotEmpty();
        assertThat(fall.get().disclaimer()).isNotBlank();

        assertThat(knowledgeBase.lookup(KnowledgeEventType.NO_ACTIVITY)).isPresent();
        assertThat(knowledgeBase.lookup(KnowledgeEventType.STROKE)).isPresent();
        assertThat(knowledgeBase.lookup(KnowledgeEventType.WANDERING)).isPresent();
        assertThat(knowledgeBase.lookup(KnowledgeEventType.BREATHING_ISSUE)).isPresent();
    }

    /** OTHER 為 catch-all enum，沒有對應 JSON，應該回 empty。 */
    @Test
    void should_return_empty_for_other() {
        assertThat(knowledgeBase.lookup(KnowledgeEventType.OTHER)).isEmpty();
    }
}
