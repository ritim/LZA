package com.lza.aethercare.aichat;

import com.lza.aethercare.ai.dto.SuggestedActionDto;
import com.lza.aethercare.ai.knowledge.CareKnowledge;
import com.lza.aethercare.aichat.rules.AiCareChatContext;
import com.lza.aethercare.aichat.rules.AiCareChatReply;
import com.lza.aethercare.aichat.rules.AiCareChatRulesEngine;
import com.lza.aethercare.common.time.Clock;
import com.lza.aethercare.event.entity.CareEvent;
import com.lza.aethercare.event.enums.CareEventType;
import com.lza.aethercare.event.enums.RiskLevel;
import com.lza.aethercare.task.entity.CareTask;
import com.lza.aethercare.workflow.entity.CareWorkflowInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/** Spec § AI_Care_Chat §6：rules engine 行為單元測試（純 Mockito）。 */
@ExtendWith(MockitoExtension.class)
class AiCareChatRulesEngineTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 5, 2, 10, 0, 0, 0, ZoneOffset.UTC);

    @Mock Clock clock;
    AiCareChatRulesEngine engine;

    @BeforeEach
    void setUp() {
        engine = new AiCareChatRulesEngine(clock);
    }

    private CareEvent event(CareEventType type, RiskLevel risk) {
        return CareEvent.builder().id(1L).elderId(1001L).eventType(type).riskLevel(risk).build();
    }

    private CareWorkflowInstance workflow() {
        return CareWorkflowInstance.builder().id(101L).eventId(1L).build();
    }

    private CareTask task(OffsetDateTime deadline) {
        return CareTask.builder().id(201L).workflowId(101L).deadlineAt(deadline).build();
    }

    private AiCareChatContext context(String message, RiskLevel risk,
                                       OffsetDateTime deadline, List<String> priorActions) {
        return new AiCareChatContext(
                event(CareEventType.MISSED_CHECK_IN, risk),
                workflow(),
                deadline == null ? Optional.empty() : Optional.of(task(deadline)),
                Optional.empty(),
                priorActions,
                message);
    }

    /** Spec § AI_Care_Chat §6：危險詞觸發 CALL_EMERGENCY 為首要動作。 */
    @Test
    void danger_signs_trigger_call_emergency_first() {
        org.mockito.Mockito.lenient().when(clock.now()).thenReturn(NOW);
        AiCareChatContext ctx = context("長者意識不清，沒呼吸", RiskLevel.HIGH, null, List.of());

        AiCareChatReply reply = engine.evaluate(ctx);

        assertThat(reply.suggestedActions()).isNotEmpty();
        assertThat(reply.suggestedActions().get(0).type()).isEqualTo("CALL_EMERGENCY");
        assertThat(reply.disclaimer()).contains("不能取代醫療診斷");
    }

    /** Spec § AI_Care_Chat §6：CALL_NO_ANSWER 訊息回 ESCALATE + REQUEST_ON_SITE_CHECK。 */
    @Test
    void call_no_answer_message_returns_escalate_and_on_site_check() {
        org.mockito.Mockito.lenient().when(clock.now()).thenReturn(NOW);
        AiCareChatContext ctx = context("打了三次都沒人接", RiskLevel.MEDIUM, null, List.of());

        AiCareChatReply reply = engine.evaluate(ctx);

        List<String> types = reply.suggestedActions().stream().map(SuggestedActionDto::type).toList();
        assertThat(types).contains("ESCALATE", "REQUEST_ON_SITE_CHECK");
        assertThat(types).doesNotContain("CALL_EMERGENCY"); // MEDIUM risk 不補 CALL_EMERGENCY
    }

    /** Spec § AI_Care_Chat §6：HIGH risk 的 CALL_NO_ANSWER 才補 CALL_EMERGENCY。 */
    @Test
    void high_risk_call_no_answer_includes_call_emergency() {
        org.mockito.Mockito.lenient().when(clock.now()).thenReturn(NOW);
        AiCareChatContext ctx = context("沒人接", RiskLevel.HIGH, null, List.of());

        AiCareChatReply reply = engine.evaluate(ctx);

        List<String> types = reply.suggestedActions().stream().map(SuggestedActionDto::type).toList();
        assertThat(types).contains("ESCALATE", "REQUEST_ON_SITE_CHECK", "CALL_EMERGENCY");
    }

    /** 既有 prior action CALL_NO_ANSWER 也應該觸發同樣的回覆。 */
    @Test
    void prior_call_no_answer_action_triggers_same_rule() {
        org.mockito.Mockito.lenient().when(clock.now()).thenReturn(NOW);
        AiCareChatContext ctx = context("接下來怎麼辦？", RiskLevel.MEDIUM, null,
                List.of("CALL_NO_ANSWER"));

        AiCareChatReply reply = engine.evaluate(ctx);

        List<String> types = reply.suggestedActions().stream().map(SuggestedActionDto::type).toList();
        assertThat(types).contains("ESCALATE", "REQUEST_ON_SITE_CHECK");
    }

    /** Spec § AI_Care_Chat §6：caregiver 確認安全 → 建議 CONFIRM_SAFE，但提醒會留審計。 */
    @Test
    void safe_declaration_suggests_confirm_safe_but_does_not_close_workflow() {
        org.mockito.Mockito.lenient().when(clock.now()).thenReturn(NOW);
        AiCareChatContext ctx = context("人很安全沒事了", RiskLevel.MEDIUM, null, List.of());

        AiCareChatReply reply = engine.evaluate(ctx);

        List<String> types = reply.suggestedActions().stream().map(SuggestedActionDto::type).toList();
        assertThat(types).contains("CONFIRM_SAFE");
        assertThat(reply.reply()).contains("責任時間軸"); // 提醒留審計
    }

    /** Spec § AI_Care_Chat §6：SLA 剩 2 分鐘內提醒緊急性 + ESCALATE。 */
    @Test
    void sla_near_expiry_triggers_urgency_message() {
        org.mockito.Mockito.lenient().when(clock.now()).thenReturn(NOW);
        AiCareChatContext ctx = context("還在等家人回電", RiskLevel.MEDIUM,
                NOW.plusSeconds(60), List.of());

        AiCareChatReply reply = engine.evaluate(ctx);

        assertThat(reply.reply()).contains("自動升級");
        assertThat(reply.suggestedActions().stream().map(SuggestedActionDto::type).toList())
                .contains("ESCALATE");
    }

    /** firstMessage：以 static knowledge 為基底回開場語，不評估 caregiver message。 */
    @Test
    void first_message_uses_static_knowledge_summary() {
        CareKnowledge mockKnowledge = new CareKnowledge(
                "事件摘要", List.of("guidance"), List.of(),
                List.of("danger"), List.of(), "disclaimer");
        AiCareChatContext ctx = new AiCareChatContext(
                event(CareEventType.MISSED_CHECK_IN, RiskLevel.MEDIUM),
                workflow(), Optional.empty(),
                Optional.of(mockKnowledge),
                List.of(), null);

        AiCareChatReply reply = engine.firstMessage(ctx);

        // 模板會包含 summary（前後加上人性化開場/收尾），故用 contains 而非 equals。
        assertThat(reply.reply()).contains("事件摘要");
        assertThat(reply.dangerSigns()).containsExactly("danger");
    }

    /** 人性化模板：recipientName 有值時應被插入 reply。 */
    @Test
    void recipient_name_is_interpolated_into_reply() {
        org.mockito.Mockito.lenient().when(clock.now()).thenReturn(NOW);
        AiCareChatContext ctx = new AiCareChatContext(
                event(CareEventType.MISSED_CHECK_IN, RiskLevel.MEDIUM),
                workflow(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                "打了三次都沒人接",
                Optional.of("王美玉"));

        AiCareChatReply reply = engine.evaluate(ctx);

        assertThat(reply.reply()).contains("王美玉");
    }

    /** 句池輪替穩定性：相同 context 兩次 evaluate 必回相同 reply（deterministic）。 */
    @Test
    void same_context_produces_same_reply_for_deterministic_rotation() {
        org.mockito.Mockito.lenient().when(clock.now()).thenReturn(NOW);
        AiCareChatContext ctx = context("人很安全沒事了", RiskLevel.MEDIUM, null, List.of());

        AiCareChatReply r1 = engine.evaluate(ctx);
        AiCareChatReply r2 = engine.evaluate(ctx);

        assertThat(r1.reply()).isEqualTo(r2.reply());
    }
}
