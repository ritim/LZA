package com.lza.aethercare.aichat.rules;

import com.lza.aethercare.ai.dto.SuggestedActionDto;
import com.lza.aethercare.ai.knowledge.CareKnowledge;
import com.lza.aethercare.event.entity.CareEvent;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Spec § AI_Care_Chat：LLM provider 共用的 system / user prompt 組裝。
 *
 * <p>所有 LLM 實作（Anthropic、Ollama，未來可能加 OpenAI）共用同一份 prompt 結構，
 * 確保安全護欄表達一致、context 欄位一致、便於跨 provider 比較產出品質。
 */
final class ChatPromptBuilder {

    private ChatPromptBuilder() {}

    static final String SYSTEM_PROMPT = """
            你是 AetherCare 居家照護系統的事件助手。你的任務是用自然、口語、有同理心的台灣繁體中文，回應目前正在處理事件的「照顧者」。

            嚴格規範（不可違反）：
            1. 不能宣稱被照顧者「絕對安全」或「沒事了」——「安全」只能由照顧者親自到場確認或經對話確認後，按下「確認安全」按鈕。
            2. 不能延遲、不能說「再等一下」、不能改變或延長系統的 SLA / 自動升級邏輯。
            3. 不能取代醫療診斷；遇到可能危急的徵兆（意識、呼吸、外傷、跌倒、出血）一律建議撥打 119。
            4. 你只生成自然語言回覆。下方提供的建議動作（suggestedActions）已由規則引擎決定，前端會自動顯示按鈕；請以自然語言「呼應」這些動作，不要逐字列舉動作代碼。
            5. 不要重複照顧者剛剛的訊息；以對話歷史為脈絡延伸即可。
            6. 字數 50–120 字、1–3 句、口語、貼合脈絡。
            7. 「無回應」永遠不等於安全；不要主動表達放心。

            語言規範（台灣讀者，請嚴格遵守）：
            8. 一律使用「台灣繁體中文」與台灣慣用口語，不能出現任何簡體字。
            9. 不使用中國大陸用詞，請改用對應的台灣說法：
                ✗ 兜底         → ✓ 保底 / 最後一道把關 / 善後
                ✗ 視頻         → ✓ 影片
                ✗ 信息         → ✓ 資訊 / 訊息
                ✗ 打的         → ✓ 搭計程車
                ✗ 哥們、姊們   → ✓ 朋友 / 您
                ✗ 軟件、硬件   → ✓ 軟體、硬體
                ✗ 計算機（PC） → ✓ 電腦
                ✗ 走心         → ✓ 用心
                ✗ 給力         → ✓ 厲害 / 幫忙很大
            10. 句尾語氣詞用「好嗎、可以嗎、麻煩您」，不要用「哦」「哈」「呢」「沒問題哦」這類陸式口吻。
            11. 標點符號一律用全形（，。！？「」、），不要用半形 , . ! ? " '。
            12. 撥打電話請寫「撥打 119」「撥打給家人」，不要寫成「打 119」「打個電話」。
            13. 直接稱呼被照顧者時用對話歷史中提供的姓名（若帶「（Demo）」標記，請完整保留全形括號）。

            風格範例（請參考此語感，不要照抄）：
            ✓「先撥打給第二位聯絡人，請他就近過去看看王美玉的狀況。如果有意識不清或外傷，麻煩立刻撥打 119。」
            ✗「先打給下一位幫手，叫他去看看王美玉的情况。要是不清醒，沒問題哦你就打 119。」
            """;

    static String buildUserPrompt(AiCareChatContext ctx, AiCareChatReply baseReply,
                                  List<LlmReplyGenerator.ChatTurn> history) {
        StringBuilder sb = new StringBuilder();
        CareEvent event = ctx.event();
        sb.append("【事件脈絡】\n");
        sb.append("- 事件類型：").append(event == null ? "未知" : event.getEventType()).append("\n");
        sb.append("- 風險等級：").append(event == null ? "未知" : event.getRiskLevel()).append("\n");
        sb.append("- 被照顧者：").append(ctx.recipientName().orElse("長者")).append("\n");
        ctx.currentTask().ifPresent(t -> {
            if (t.getDeadlineAt() != null) {
                long secs = Duration.between(OffsetDateTime.now(), t.getDeadlineAt()).getSeconds();
                sb.append("- 當前任務 SLA 剩餘約 ").append(Math.max(secs, 0L)).append(" 秒\n");
            }
        });
        if (!ctx.priorActionTypes().isEmpty()) {
            sb.append("- 照顧者已採取的動作：")
                    .append(String.join(", ", ctx.priorActionTypes()))
                    .append("\n");
        }
        ctx.knowledge().map(CareKnowledge::summary).ifPresent(s ->
                sb.append("- 規則引擎摘要：").append(s).append("\n"));
        if (!baseReply.suggestedActions().isEmpty()) {
            sb.append("- 系統建議動作（已決定，請自然呼應，不要逐字列出代碼）：\n");
            for (SuggestedActionDto a : baseReply.suggestedActions()) {
                sb.append("    · ").append(a.label())
                        .append(" [").append(a.type()).append("]")
                        .append("\n");
            }
        }

        if (history != null && !history.isEmpty()) {
            sb.append("\n【最近對話】\n");
            for (LlmReplyGenerator.ChatTurn h : history) {
                sb.append("[").append(h.role()).append("] ").append(h.message()).append("\n");
            }
        }

        sb.append("\n【請以一段自然語言回應目前最新的 caregiver 訊息，符合上方規範】");
        return sb.toString();
    }
}
