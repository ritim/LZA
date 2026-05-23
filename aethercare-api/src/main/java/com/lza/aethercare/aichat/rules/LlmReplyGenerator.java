package com.lza.aethercare.aichat.rules;

import com.lza.aethercare.aichat.enums.ChatSource;

import java.util.List;

/**
 * Spec § AI_Care_Chat：AI Care Chat reply 文字生成策略。
 *
 * <p>Rules engine 已產出結構化決策（reply、questions、suggestedActions、dangerSigns、disclaimer）。
 * 此介面負責把 {@link AiCareChatReply#reply()} 替換為更貼合脈絡的自然語言；
 * questions / suggestedActions / dangerSigns / disclaimer 完全不動，由 rules engine 保證安全護欄。
 *
 * <p>實作必須在無法產生內容（外部依賴失敗、未設定金鑰、空回覆等）時回傳 {@code baseReply.reply()}，
 * 保證 chat 永遠有回應（spec §3「Silence is a signal. 無回應不等於安全」）。
 */
public interface LlmReplyGenerator {

    /** 對話歷史的簡化單元，避免 generator 依賴 JPA entity。role 為 {@code "USER"} 或 {@code "ASSISTANT"}。 */
    record ChatTurn(String role, String message) {}

    /**
     * Generator 的輸出。當 LLM 成功生成時 {@code source = LLM}；當實作走的是 deterministic 模板
     * 或外部依賴失敗回退到 baseReply 時，{@code source = RULE_ENGINE}。Service 用這個欄位寫 audit。
     */
    record Outcome(String reply, ChatSource source) {}

    /**
     * @param ctx       rules engine 的 context（含 event / workflow / task / recipientName 等）
     * @param baseReply rules engine 產出的結構化回覆；當 generator 無法產生內容時必須回傳 baseReply.reply()
     * @param history   先前對話訊息（依時序），caller 已截斷為合理長度
     */
    Outcome generate(AiCareChatContext ctx, AiCareChatReply baseReply, List<ChatTurn> history);
}
