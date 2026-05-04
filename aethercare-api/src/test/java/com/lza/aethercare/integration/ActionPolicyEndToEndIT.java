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
 * Spec § Master §4 / §6 Flow C / § Gap D 「保留 workflow open」action 行為驗證。
 *
 * <p>覆蓋兩條 acceptance：
 * <ul>
 *   <li>{@code CALL_NO_ANSWER}（電話未接）寫 care_action + audit 但不結案、不升級</li>
 *   <li>{@code MARK_UNABLE_TO_CONFIRM}（無法確認）同樣保留 workflow open；spec § Gap D 修正前
 *       v1.0-rc1 將其映射到 NEED_HELP（升級），與 spec 衝突。</li>
 * </ul>
 *
 * <p>兩條共用同一份 @TestPropertySource，故合併成單一 IT class 以減少 Spring context 數，
 * 避免 multi-context 下 connection pool 競爭造成 flaky。
 */
@TestPropertySource(properties = {
        "aethercare.scheduler.timeout-scan-fixed-delay=600000",
        "aethercare.scheduler.missed-checkin.enabled=false",
        "aethercare.scheduler.no-activity.enabled=false",
        "aethercare.anomaly.scheduler.enabled=false"
})
class ActionPolicyEndToEndIT extends AbstractIntegrationTest {

    private static final long ELDER_ID = 1001L;

    @Autowired TestRestTemplate restTemplate;
    @Autowired JdbcTemplate jdbcTemplate;

    private String token;

    @BeforeEach
    void setUp() {
        Map<String, Object> body = Map.of("username", "family01", "password", "family123");
        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/v1/auth/login", body, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        token = (String) resp.getBody().get("accessToken");
        // 拉長 SLA 防 race
        jdbcTemplate.update("UPDATE care_contact_escalation SET sla_seconds = 600 WHERE level = 1");
    }

    @Test
    void call_no_answer_records_action_but_keeps_workflow_open() {
        Map<String, Object> result = createSosAndGetWorkflow("2026-04-27T16:00:00+08:00");
        Number workflowId = (Number) result.get("workflowId");
        Number taskId = (Number) result.get("taskId");

        Map<String, Object> action = Map.of(
                "actionType", "CALL_NO_ANSWER",
                "note", "已撥三次都沒接");
        ResponseEntity<Map> actionResp = restTemplate.exchange(
                "/api/v1/care-tasks/" + taskId + "/actions", HttpMethod.POST,
                new HttpEntity<>(action, jwtHeaders()), Map.class);
        assertThat(actionResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertWorkflowStillWaitingResponseAtLevel1(workflowId);

        Long actionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM care_action WHERE workflow_id = ? AND action_type = 'CALL_NO_ANSWER'",
                Long.class, workflowId.longValue());
        assertThat(actionCount).isGreaterThanOrEqualTo(1);

        assertNoEscalationOrResolutionInAudit(workflowId);
    }

    @Test
    void mark_unable_to_confirm_records_action_but_does_not_escalate() {
        Map<String, Object> result = createSosAndGetWorkflow("2026-04-27T17:00:00+08:00");
        Number workflowId = (Number) result.get("workflowId");
        Number taskId = (Number) result.get("taskId");

        Map<String, Object> action = Map.of(
                "actionType", "MARK_UNABLE_TO_CONFIRM",
                "note", "嘗試多次仍無法確認");
        ResponseEntity<Map> actionResp = restTemplate.exchange(
                "/api/v1/care-tasks/" + taskId + "/actions", HttpMethod.POST,
                new HttpEntity<>(action, jwtHeaders()), Map.class);
        assertThat(actionResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertWorkflowStillWaitingResponseAtLevel1(workflowId);
        assertNoEscalationOrResolutionInAudit(workflowId);
    }

    private Map<String, Object> createSosAndGetWorkflow(String occurredAt) {
        Map<String, Object> req = Map.of(
                "elderId", ELDER_ID,
                "source", "MOBILE_APP",
                "eventType", "SOS",
                "occurredAt", occurredAt);
        ResponseEntity<Map> created = restTemplate.exchange(
                "/api/v1/care-events", HttpMethod.POST,
                new HttpEntity<>(req, jwtHeaders()), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Number workflowId = (Number) created.getBody().get("workflowId");

        ResponseEntity<Map> wf = restTemplate.exchange(
                "/api/v1/workflows/" + workflowId, HttpMethod.GET,
                new HttpEntity<>(jwtHeaders()), Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) wf.getBody().get("tasks");
        return Map.of("workflowId", workflowId, "taskId", tasks.get(0).get("taskId"));
    }

    private void assertWorkflowStillWaitingResponseAtLevel1(Number workflowId) {
        ResponseEntity<Map> after = restTemplate.exchange(
                "/api/v1/workflows/" + workflowId, HttpMethod.GET,
                new HttpEntity<>(jwtHeaders()), Map.class);
        assertThat(after.getBody().get("status")).isEqualTo("WAITING_RESPONSE");
        assertThat(after.getBody().get("completedAt")).isNull();
        assertThat(((Number) after.getBody().get("currentLevel")).intValue()).isEqualTo(1);
    }

    private void assertNoEscalationOrResolutionInAudit(Number workflowId) {
        ResponseEntity<List> audits = restTemplate.exchange(
                "/api/v1/workflows/" + workflowId + "/audit-logs", HttpMethod.GET,
                new HttpEntity<>(jwtHeaders()), List.class);
        @SuppressWarnings("unchecked")
        List<String> actions = ((List<Map<String, Object>>) audits.getBody()).stream()
                .map(o -> (String) o.get("action"))
                .toList();
        assertThat(actions).doesNotContain("WORKFLOW_RESOLVED", "TASK_ESCALATED");
    }

    private HttpHeaders jwtHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}
