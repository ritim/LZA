package com.lza.aethercare.ai.llm;

import com.lza.aethercare.ai.enums.KnowledgeEventType;
import com.lza.aethercare.ai.knowledge.CareKnowledge;
import com.lza.aethercare.ai.knowledge.CareKnowledgeBase;
import com.lza.aethercare.event.entity.CareEvent;

import java.util.Optional;

/**
 * AI provider interface：保留未來換真 LLM（OpenAI / Claude）的擴充點。
 *
 * <p>MVP 由 {@link StaticLlmProvider} 直接從 {@link CareKnowledgeBase} 取靜態內容；
 * production impl 可透過 event metadata + 病史 prompt 真正請求 LLM 生成 override。
 */
public interface LlmProvider {

    /**
     * 為指定 event 與 knowledge type 生成 / 取出 care knowledge。
     *
     * @param event 觸發 AI 指引的照護事件
     * @param type  經 EventTypeMapper 對映後的 knowledge type
     * @param knowledgeBase 靜態知識庫（fallback 來源）
     * @return 對應 knowledge；型別不支援時回 empty
     */
    Optional<CareKnowledge> generate(CareEvent event, KnowledgeEventType type, CareKnowledgeBase knowledgeBase);
}
