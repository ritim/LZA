package com.lza.aethercare.ai.llm;

import com.lza.aethercare.ai.enums.KnowledgeEventType;
import com.lza.aethercare.ai.knowledge.CareKnowledge;
import com.lza.aethercare.ai.knowledge.CareKnowledgeBase;
import com.lza.aethercare.event.entity.CareEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 靜態 LLM provider：直接從 {@link CareKnowledgeBase} 取得知識。
 *
 * <p>啟用條件：{@code aethercare.ai.provider=static}（預設啟用）。MVP 不需要真 LLM 即可運作。
 */
@Component
@ConditionalOnProperty(name = "aethercare.ai.provider", havingValue = "static", matchIfMissing = true)
public class StaticLlmProvider implements LlmProvider {

    @Override
    public Optional<CareKnowledge> generate(CareEvent event, KnowledgeEventType type, CareKnowledgeBase knowledgeBase) {
        return knowledgeBase.lookup(type);
    }
}
