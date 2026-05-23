package com.lza.aethercare.aichat.rules;

import com.lza.aethercare.ai.dto.AssessmentQuestionDto;
import com.lza.aethercare.ai.dto.SuggestedActionDto;
import com.lza.aethercare.ai.knowledge.CareKnowledge;
import com.lza.aethercare.common.time.Clock;
import com.lza.aethercare.event.enums.RiskLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Spec § AI_Care_Chat §6：deterministic MVP rules engine。
 *
 * <p>規則優先序（先到先勝）：
 * <ol>
 *   <li>Danger signs → CALL_EMERGENCY 為首要</li>
 *   <li>SLA &lt; 2 分鐘 → 提醒緊急性 + ESCALATE</li>
 *   <li>Caregiver 表達電話沒人接 / 既有 CALL_NO_ANSWER 紀錄 → ESCALATE + REQUEST_ON_SITE_CHECK
 *       （workflow 必須保持 open）</li>
 *   <li>Caregiver 表達被照顧者安全 → 建議 CONFIRM_SAFE + 提醒會留審計</li>
 *   <li>否則回 static guidance 為基底的中性回覆</li>
 * </ol>
 *
 * <p>所有 reply 文字由 2-3 句模板池 deterministic 輪替（依 workflow.id ^ message.hashCode()），
 * 避免每次都是同一段制式字串。模板內 {@code %s} 會插入被照顧者顯示名稱（缺值回退「長者」）。
 */
@Component
@RequiredArgsConstructor
public class AiCareChatRulesEngine {

    private static final List<String> DANGER_KEYWORDS = List.of(
            "不省人事", "沒呼吸", "沒有呼吸", "呼吸困難", "胸痛", "胸悶", "出血", "流血",
            "頭撞", "撞到頭", "無法移動", "不能動", "中風", "口齒不清", "意識不清", "嘔吐",
            "抽搐", "失去意識", "昏迷", "unconscious", "not breathing", "chest pain",
            "bleeding", "head impact", "cannot move");

    private static final List<String> NO_ANSWER_KEYWORDS = List.of(
            "沒接", "沒人接", "電話未接", "聯絡不到", "找不到人", "no answer", "not answering");

    private static final List<String> SAFE_KEYWORDS = List.of(
            "都很好", "已經安全", "沒事了", "確認安全", "人安全", "確定安全",
            "is safe", "doing fine", "everything is fine");

    private static final long SLA_NEAR_EXPIRY_SECONDS = 120;

    private static final String DEFAULT_NAME = "長者";

    private static final List<String> DANGER_POOL = List.of(
            "您描述的徵兆已經超出居家觀察可以處理的範圍 — 請立刻撥 119，並同步通知第二聯絡人。"
                    + "救援抵達前，避免移動 %s，留意呼吸與意識變化。",
            "從您的敘述判斷，這不是再等等可以接受的時刻。馬上撥 119，請其他家人或鄰居就近過去。"
                    + "在電話那頭時，幫忙確認 %s 的呼吸是否規律。",
            "這些訊號需要立即處置：119 先打，第二聯絡人同步聯絡。在專業人員到場前先不要移動 %s，"
                    + "記錄意識與呼吸的變化。");

    private static final List<String> NO_ANSWER_POOL = List.of(
            "%s 暫時聯絡不上，這不能當成沒事。建議轉給下一位聯絡人，或請住附近的人去看一眼。"
                    + "若有摔倒、意識不清的疑慮，119 不要猶豫。",
            "電話沒人接不等於安全。下一步可以通知下一位、或請鄰居順道看 %s 一下；"
                    + "任何不確定的徵兆都是直接撥 119 的合理理由。",
            "目前還沒接到 %s — 先別假設一切都好。可以把責任傳給下一位、或請就近的人現場確認；"
                    + "有外傷或意識異常的疑慮就直接 119。");

    /** SAFE pool：每句必含「責任時間軸」（測試契約）。 */
    private static final List<String> SAFE_POOL = List.of(
            "了解，您現場確認 %s 安全了。可以按「確認安全」收尾，這筆紀錄會留在責任時間軸；"
                    + "之後若有新訊號系統仍會繼續追蹤。",
            "謝謝您的確認。按下「確認安全」事件就會收尾，紀錄會留在責任時間軸供回查；"
                    + "後續若 %s 再有新訊號還是會通知您。",
            "好，%s 沒事是這次最重要的結果。請按「確認安全」正式收尾，"
                    + "這次的確認會留在責任時間軸。");

    /** SLA pool：每句必含「自動升級」（測試契約）。 %d 為剩餘秒數。 */
    private static final List<String> SLA_POOL = List.of(
            "提醒：%s 這筆任務剩約 %d 秒就會自動升級。仍在處理就先按「通知下一位」交棒；"
                    + "若已確認安全請按「確認安全」收尾。",
            "再 %2$d 秒沒有動作，%1$s 這筆任務會自動升級到下一位。"
                    + "可以「通知下一位」接力，或「確認安全」結尾。",
            "倒數約 %2$d 秒到 %1$s 任務自動升級。「通知下一位」交棒、或「確認安全」收尾。");

    private static final List<String> DEFAULT_KNOWLEDGE_POOL = List.of(
            "%s 若有新進展請隨時補充，或按下方建議行動。",
            "%s 任何不確定的訊息都歡迎告訴我，我會根據情況提醒下一步。",
            "%s 您可以先依下方建議行動；有新狀況再回到這邊。");

    private static final List<String> DEFAULT_GENERIC_POOL = List.of(
            "收到您的訊息。請依下方建議行動 — 任何不確定都不應視為安全。",
            "收到。可以依下方建議的動作試試看，過程中有任何狀況再告訴我。",
            "收到您的更新。下方有目前可以考慮的動作；不確定的情況請當作不安全處理。");

    private static final List<String> FIRST_KNOWLEDGE_POOL = List.of(
            "關於 %s — %s 請依下方建議與時間軸協助處置；任何不確定都不應視為安全。",
            "事件已建立。%2$s 接下來請依建議動作協助 %1$s，並留意下方提醒。",
            "%2$s（追蹤對象：%1$s）任何不確定的訊號都不要當作安全；下方有可採取的動作。");

    private static final List<String> FIRST_FALLBACK_POOL = List.of(
            "此事件已建立。請依下方建議與時間軸協助處置 %s；任何不確定都不應視為安全。",
            "%s 的事件已建立。請從下方建議動作開始，並隨時補充新進展。",
            "事件已開啟並追蹤中 — %s 任何不確定都不應視為安全，請參考下方提醒。");

    private final Clock clock;

    public AiCareChatReply evaluate(AiCareChatContext ctx) {
        String msg = ctx.message() == null ? "" : ctx.message();
        String lower = msg.toLowerCase();

        if (matchesAny(lower, DANGER_KEYWORDS)) {
            return dangerReply(ctx);
        }
        if (slaNearExpiry(ctx)) {
            return slaUrgencyReply(ctx);
        }
        if (matchesAny(lower, NO_ANSWER_KEYWORDS) || ctx.priorActionTypes().contains("CALL_NO_ANSWER")) {
            return noAnswerReply(ctx);
        }
        if (matchesAny(lower, SAFE_KEYWORDS)) {
            return safeDeclarationReply(ctx);
        }
        return defaultReply(ctx);
    }

    /** 給開啟事件時的首訊息：純 static guidance summary，不對 caregiver 訊息回應。 */
    public AiCareChatReply firstMessage(AiCareChatContext ctx) {
        String name = name(ctx);
        String summary = ctx.knowledge().map(CareKnowledge::summary).orElse(null);
        String reply;
        if (summary != null && !summary.isBlank()) {
            reply = String.format(pick(FIRST_KNOWLEDGE_POOL, ctx), name, summary);
        } else {
            reply = String.format(pick(FIRST_FALLBACK_POOL, ctx), name);
        }
        List<AssessmentQuestionDto> questions = ctx.knowledge()
                .map(CareKnowledge::questions)
                .orElseGet(List::of);
        List<SuggestedActionDto> actions = ctx.knowledge()
                .map(CareKnowledge::suggestedActions)
                .orElseGet(List::of);
        List<String> dangerSigns = ctx.knowledge()
                .map(CareKnowledge::dangerSigns)
                .orElseGet(List::of);
        return new AiCareChatReply(reply, questions, actions, dangerSigns, AiCareChatReply.DEFAULT_DISCLAIMER);
    }

    private AiCareChatReply dangerReply(AiCareChatContext ctx) {
        String reply = String.format(pick(DANGER_POOL, ctx), name(ctx));
        List<SuggestedActionDto> actions = List.of(
                new SuggestedActionDto("CALL_EMERGENCY", "立即撥打119", "HIGH", true),
                new SuggestedActionDto("ESCALATE", "通知下一位照顧者", "HIGH", true),
                new SuggestedActionDto("REQUEST_ON_SITE_CHECK", "請人到場協助", "HIGH", true));
        List<String> danger = ctx.knowledge().map(CareKnowledge::dangerSigns).orElseGet(List::of);
        return new AiCareChatReply(reply, List.of(), actions, danger, AiCareChatReply.DEFAULT_DISCLAIMER);
    }

    private AiCareChatReply noAnswerReply(AiCareChatContext ctx) {
        String reply = String.format(pick(NO_ANSWER_POOL, ctx), name(ctx));
        List<SuggestedActionDto> actions = new ArrayList<>(List.of(
                new SuggestedActionDto("ESCALATE", "通知下一位照顧者", "HIGH", true),
                new SuggestedActionDto("REQUEST_ON_SITE_CHECK", "請人到場確認", "HIGH", true),
                new SuggestedActionDto("CALL_NO_ANSWER", "記錄電話未接", "MEDIUM", true)));
        // Spec § AI_Care_Chat §6 Rule: HIGH/CRITICAL 才補 CALL_EMERGENCY
        if (ctx.event() != null && (ctx.event().getRiskLevel() == RiskLevel.HIGH
                || ctx.event().getRiskLevel() == RiskLevel.CRITICAL)) {
            actions.add(new SuggestedActionDto("CALL_EMERGENCY", "撥打119", "HIGH", true));
        }
        List<AssessmentQuestionDto> questions = List.of(
                new AssessmentQuestionDto("q_last_activity", "最後一次活動或 check-in 是什麼時候？",
                        "TEXT", List.of(), List.of()),
                new AssessmentQuestionDto("q_on_site", "是否有人可以到場確認？",
                        "YES_NO_UNKNOWN", List.of("是", "否", "不確定"), List.of("否", "不確定")));
        return new AiCareChatReply(reply, questions, actions, List.of(), AiCareChatReply.DEFAULT_DISCLAIMER);
    }

    private AiCareChatReply safeDeclarationReply(AiCareChatContext ctx) {
        String reply = String.format(pick(SAFE_POOL, ctx), name(ctx));
        List<SuggestedActionDto> actions = List.of(
                new SuggestedActionDto("CONFIRM_SAFE", "確認安全並關閉事件", "HIGH", true),
                new SuggestedActionDto("ADD_NOTE", "新增備註", "LOW", false));
        return new AiCareChatReply(reply, List.of(), actions, List.of(), AiCareChatReply.DEFAULT_DISCLAIMER);
    }

    private AiCareChatReply slaUrgencyReply(AiCareChatContext ctx) {
        long secs = Math.max(remainingSeconds(ctx), 0);
        String reply = String.format(pick(SLA_POOL, ctx), name(ctx), secs);
        List<SuggestedActionDto> actions = List.of(
                new SuggestedActionDto("ESCALATE", "通知下一位照顧者", "HIGH", true),
                new SuggestedActionDto("CONFIRM_SAFE", "確認安全", "HIGH", true));
        return new AiCareChatReply(reply, List.of(), actions, List.of(), AiCareChatReply.DEFAULT_DISCLAIMER);
    }

    private AiCareChatReply defaultReply(AiCareChatContext ctx) {
        String summary = ctx.knowledge().map(CareKnowledge::summary).orElse(null);
        String reply = (summary != null && !summary.isBlank())
                ? String.format(pick(DEFAULT_KNOWLEDGE_POOL, ctx), summary)
                : pick(DEFAULT_GENERIC_POOL, ctx);
        List<AssessmentQuestionDto> questions = ctx.knowledge()
                .map(CareKnowledge::questions)
                .orElseGet(List::of);
        List<SuggestedActionDto> actions = ctx.knowledge()
                .map(CareKnowledge::suggestedActions)
                .orElseGet(List::of);
        return new AiCareChatReply(reply, questions, actions, List.of(), AiCareChatReply.DEFAULT_DISCLAIMER);
    }

    private boolean matchesAny(String lower, List<String> keywords) {
        if (lower == null || lower.isBlank()) return false;
        for (String kw : keywords) {
            if (lower.contains(kw.toLowerCase())) return true;
        }
        return false;
    }

    private boolean slaNearExpiry(AiCareChatContext ctx) {
        long secs = remainingSeconds(ctx);
        return secs >= 0 && secs <= SLA_NEAR_EXPIRY_SECONDS;
    }

    private long remainingSeconds(AiCareChatContext ctx) {
        return ctx.currentTask()
                .map(t -> t.getDeadlineAt())
                .map(d -> Duration.between(clock.now(), d).getSeconds())
                .orElse(Long.MAX_VALUE);
    }

    private String name(AiCareChatContext ctx) {
        return ctx.recipientName()
                .filter(n -> n != null && !n.isBlank())
                .orElse(DEFAULT_NAME);
    }

    /**
     * Deterministic 句池選擇：以 workflow.id 與 message hash 混合當 seed，
     * 確保「同一個 workflow + 同樣訊息」永遠回相同句（避免測試 flaky），
     * 但不同訊息會輪到不同句，移除每次都是同一段制式字串的感覺。
     */
    private static <T> T pick(List<T> pool, AiCareChatContext ctx) {
        long seed = ctx.workflow() == null || ctx.workflow().getId() == null
                ? 0L
                : ctx.workflow().getId();
        if (ctx.message() != null) {
            seed = seed * 31L + ctx.message().hashCode();
        }
        int idx = Math.floorMod(seed, pool.size());
        return pool.get(idx);
    }
}
