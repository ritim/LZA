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
        String summary = ctx.knowledge()
                .map(CareKnowledge::summary)
                .orElse("此事件已建立。請依下方建議與時間軸協助處置；任何不確定都不應視為安全。");
        List<AssessmentQuestionDto> questions = ctx.knowledge()
                .map(CareKnowledge::questions)
                .orElseGet(List::of);
        List<SuggestedActionDto> actions = ctx.knowledge()
                .map(CareKnowledge::suggestedActions)
                .orElseGet(List::of);
        List<String> dangerSigns = ctx.knowledge()
                .map(CareKnowledge::dangerSigns)
                .orElseGet(List::of);
        return new AiCareChatReply(summary, questions, actions, dangerSigns, AiCareChatReply.DEFAULT_DISCLAIMER);
    }

    private AiCareChatReply dangerReply(AiCareChatContext ctx) {
        String reply = "您描述的徵兆屬於緊急狀況。請立即撥打 119 並回到通話現場；同時通知第二聯絡人。"
                + "在救援抵達前，避免移動長者，並隨時觀察呼吸與意識。";
        List<SuggestedActionDto> actions = List.of(
                new SuggestedActionDto("CALL_EMERGENCY", "立即撥打119", "HIGH", true),
                new SuggestedActionDto("ESCALATE", "通知下一位照顧者", "HIGH", true),
                new SuggestedActionDto("REQUEST_ON_SITE_CHECK", "請人到場協助", "HIGH", true));
        List<String> danger = ctx.knowledge().map(CareKnowledge::dangerSigns).orElseGet(List::of);
        return new AiCareChatReply(reply, List.of(), actions, danger, AiCareChatReply.DEFAULT_DISCLAIMER);
    }

    private AiCareChatReply noAnswerReply(AiCareChatContext ctx) {
        String reply = "電話未接時請不要視為安全。建議通知第二順位照顧者，或請鄰近的人到場確認。"
                + "如果有意識不清、外傷、跌倒等任何疑慮，請立即撥打 119。";
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
        String reply = "了解，若您已親自確認長者安全，可按「確認安全」收尾。此確認會記錄到責任時間軸，"
                + "之後若有新訊號，系統仍會持續追蹤。";
        List<SuggestedActionDto> actions = List.of(
                new SuggestedActionDto("CONFIRM_SAFE", "確認安全並關閉事件", "HIGH", true),
                new SuggestedActionDto("ADD_NOTE", "新增備註", "LOW", false));
        return new AiCareChatReply(reply, List.of(), actions, List.of(), AiCareChatReply.DEFAULT_DISCLAIMER);
    }

    private AiCareChatReply slaUrgencyReply(AiCareChatContext ctx) {
        long secs = remainingSeconds(ctx);
        String reply = String.format("提醒：目前任務剩約 %d 秒就會自動升級。"
                + "若您仍在處理，可先按「通知下一位」交棒；若已確認安全，請按「確認安全」收尾。",
                Math.max(secs, 0));
        List<SuggestedActionDto> actions = List.of(
                new SuggestedActionDto("ESCALATE", "通知下一位照顧者", "HIGH", true),
                new SuggestedActionDto("CONFIRM_SAFE", "確認安全", "HIGH", true));
        return new AiCareChatReply(reply, List.of(), actions, List.of(), AiCareChatReply.DEFAULT_DISCLAIMER);
    }

    private AiCareChatReply defaultReply(AiCareChatContext ctx) {
        String reply = ctx.knowledge()
                .map(k -> k.summary() + " 若有新進展請隨時補充，或按下方建議的動作。")
                .orElse("已收到您的訊息。請依下方建議行動；任何不確定的情況都不應視為安全。");
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
        return remainingSeconds(ctx) >= 0 && remainingSeconds(ctx) <= SLA_NEAR_EXPIRY_SECONDS;
    }

    private long remainingSeconds(AiCareChatContext ctx) {
        return ctx.currentTask()
                .map(t -> t.getDeadlineAt())
                .map(d -> Duration.between(clock.now(), d).getSeconds())
                .orElse(Long.MAX_VALUE);
    }
}
