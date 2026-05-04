package com.lza.aethercare.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec § AI_Care_Chat §10：AI Care Chat 端到端 IT。
 *
 * <p>覆蓋 acceptance：
 * <ul>
 *   <li>POST 儲存 caregiver + assistant 訊息</li>
 *   <li>CALL_NO_ANSWER 訊息回 ESCALATE + REQUEST_ON_SITE_CHECK</li>
 *   <li>Chat 不改 workflow status</li>
 *   <li>危險詞訊息 CALL_EMERGENCY 為首要</li>
 *   <li>GET history 依時間升冪</li>
 * </ul>
 */
@TestPropertySource(properties = {
        "aethercare.scheduler.timeout-scan-fixed-delay=600000",
        "aethercare.scheduler.missed-checkin.enabled=false",
        "aethercare.scheduler.no-activity.enabled=false",
        "aethercare.anomaly.scheduler.enabled=false"
})
class AiCareChatEndToEndIT extends AbstractIntegrationTest {

    private static final long ELDER_ID = 1001L;

    @Autowired TestRestTemplate restTemplate;
    @Autowired JdbcTemplate jdbcTemplate;

    private String token;

    @BeforeEach
    void setUp() {
        Map<String, Object> body = Map.of("username", "family01", "password", "family123");
        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/v1/auth/login", body, Map.class);
        token = (String) resp.getBody().get("accessToken");
        jdbcTemplate.update("UPDATE care_contact_escalation SET sla_seconds = 600 WHERE level = 1");
    }

    @Test
    void chat_saves_user_and_assistant_messages_and_does_not_change_workflow() {
        EventCtx ctx = createSosEvent("2026-04-27T18:00:00+08:00");

        Map<String, Object> chatReq = Map.of(
                "careEventId", ctx.eventId(),
                "workflowId", ctx.workflowId(),
                "taskId", ctx.taskId(),
                "message", "電話打了三次都沒人接");

        ResponseEntity<Map> chat = restTemplate.exchange(
                "/api/v1/ai/care-chat", HttpMethod.POST,
                new HttpEntity<>(chatReq, jwt()), Map.class);
        assertThat(chat.getStatusCode()).isEqualTo(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> actions = (List<Map<String, Object>>) chat.getBody().get("suggestedActions");
        List<String> types = actions.stream().map(a -> (String) a.get("type")).toList();
        assertThat(types).contains("ESCALATE", "REQUEST_ON_SITE_CHECK");

        // workflow status 不變（HIGH SOS 但 chat 不改 state）
        ResponseEntity<Map> wf = restTemplate.exchange(
                "/api/v1/workflows/" + ctx.workflowId(), HttpMethod.GET,
                new HttpEntity<>(jwt()), Map.class);
        assertThat(wf.getBody().get("status")).isEqualTo("WAITING_RESPONSE");

        // DB：USER + ASSISTANT 都各寫至少 1 筆（first message + this exchange = 至少 3 筆 assistant 含開場）
        Long userCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_chat_messages WHERE workflow_id = ? AND role = 'USER'",
                Long.class, ctx.workflowId());
        Long assistantCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_chat_messages WHERE workflow_id = ? AND role = 'ASSISTANT'",
                Long.class, ctx.workflowId());
        assertThat(userCount).isGreaterThanOrEqualTo(1);
        assertThat(assistantCount).isGreaterThanOrEqualTo(2); // 開場 + 此次回覆
    }

    @Test
    void danger_sign_message_returns_call_emergency_first() {
        EventCtx ctx = createSosEvent("2026-04-27T18:30:00+08:00");

        Map<String, Object> chatReq = Map.of(
                "careEventId", ctx.eventId(),
                "workflowId", ctx.workflowId(),
                "taskId", ctx.taskId(),
                "message", "長者意識不清，沒呼吸");

        ResponseEntity<Map> chat = restTemplate.exchange(
                "/api/v1/ai/care-chat", HttpMethod.POST,
                new HttpEntity<>(chatReq, jwt()), Map.class);
        assertThat(chat.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> actions = (List<Map<String, Object>>) chat.getBody().get("suggestedActions");
        assertThat(actions.get(0).get("type")).isEqualTo("CALL_EMERGENCY");
    }

    @Test
    void history_endpoint_returns_messages_in_order() {
        EventCtx ctx = createSosEvent("2026-04-27T19:00:00+08:00");

        // 連續送兩則訊息
        sendChat(ctx, "第一則");
        sendChat(ctx, "第二則");

        ResponseEntity<Map> hist = restTemplate.exchange(
                "/api/v1/workflows/" + ctx.workflowId() + "/ai-messages",
                HttpMethod.GET, new HttpEntity<>(jwt()), Map.class);
        assertThat(hist.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) hist.getBody().get("items");
        assertThat(items).isNotEmpty();
        // 升冪：createdAt 應遞增
        for (int i = 1; i < items.size(); i++) {
            String prev = (String) items.get(i - 1).get("createdAt");
            String curr = (String) items.get(i).get("createdAt");
            assertThat(curr).isGreaterThanOrEqualTo(prev);
        }
        // audit log 應含 AI_CHAT_STARTED + AI_CHAT_MESSAGE_CREATED
        @SuppressWarnings("unchecked")
        List<String> auditActions = jdbcTemplate.queryForList(
                "SELECT action FROM care_audit_log WHERE workflow_id = ?",
                String.class, ctx.workflowId());
        assertThat(auditActions).contains("AI_CHAT_STARTED", "AI_CHAT_MESSAGE_CREATED");
    }

    private void sendChat(EventCtx ctx, String message) {
        Map<String, Object> chatReq = Map.of(
                "careEventId", ctx.eventId(),
                "workflowId", ctx.workflowId(),
                "taskId", ctx.taskId(),
                "message", message);
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/ai/care-chat", HttpMethod.POST,
                new HttpEntity<>(chatReq, jwt()), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private EventCtx createSosEvent(String occurredAt) {
        Map<String, Object> req = Map.of(
                "elderId", ELDER_ID,
                "source", "MOBILE_APP",
                "eventType", "SOS",
                "occurredAt", occurredAt);
        ResponseEntity<Map> created = restTemplate.exchange(
                "/api/v1/care-events", HttpMethod.POST,
                new HttpEntity<>(req, jwt()), Map.class);
        Long eventId = ((Number) created.getBody().get("eventId")).longValue();
        Long workflowId = ((Number) created.getBody().get("workflowId")).longValue();
        Long taskId = jdbcTemplate.queryForObject(
                "SELECT id FROM care_task WHERE workflow_id = ? ORDER BY id ASC LIMIT 1",
                Long.class, workflowId);
        return new EventCtx(eventId, workflowId, taskId);
    }

    private HttpHeaders jwt() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private record EventCtx(Long eventId, Long workflowId, Long taskId) {}
}
