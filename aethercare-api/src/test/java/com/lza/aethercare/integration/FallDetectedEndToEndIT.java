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

import java.util.List;
import java.util.Map;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * FALL_DETECTED 端到端整合測試（帶 Spring Security JWT）。
 * 對應系統設計 §18.1 demo 場景與 §18.2 衝突場景 #3。
 * <p>
 * Step 1  POST /api/v1/auth/login → JWT
 * Step 2  POST /api/v1/care-events → 201, 取得 workflowId
 * Step 3  Awaitility 等到 timeout scanner 升級出 level=2 task
 * Step 4  POST /api/v1/care-tasks/{level2}/actions CONFIRM_SAFE → 201
 * Step 5  workflow → RESOLVED, completedAt 不為 null
 * Step 6  audit timeline 包含完整責任鏈動作
 */
class FallDetectedEndToEndIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Tokens family01;
    private Tokens family02;
    private String tokenFamily01;
    private String tokenFamily02;

    /** 把 demo seed 的 SLA 壓縮到 2/5 秒，並先登入兩個 demo user 拿 JWT。 */
    @BeforeEach
    void setUp() {
        jdbcTemplate.update("UPDATE care_contact_escalation SET sla_seconds = 2 WHERE level = 1");
        jdbcTemplate.update("UPDATE care_contact_escalation SET sla_seconds = 5 WHERE level = 2");
        family01 = login("family01", "family123");
        family02 = login("family02", "family123");
        tokenFamily01 = family01.access();
        tokenFamily02 = family02.access();
    }

    private Tokens login(String username, String password) {
        Map<String, Object> body = Map.of("username", username, "password", password);
        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/v1/auth/login", body, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return new Tokens(
                (String) resp.getBody().get("accessToken"),
                (String) resp.getBody().get("refreshToken"));
    }

    /** access + refresh token 對。 */
    private record Tokens(String access, String refresh) {}

    private HttpHeaders authHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private <T> ResponseEntity<T> postJson(String path, Object body, String token, Class<T> respType) {
        return restTemplate.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body, authHeaders(token)), respType);
    }

    private <T> ResponseEntity<T> getJson(String path, String token, Class<T> respType) {
        return restTemplate.exchange(path, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), respType);
    }

    @Test
    void should_complete_fall_detected_workflow_with_full_audit_chain() {
        // Step 2: 建立事件（用 family01 token）
        Map<String, Object> req = Map.of(
                "elderId", 1001,
                "source", "MOBILE_APP",
                "eventType", "FALL_DETECTED",
                "occurredAt", "2026-04-27T12:00:00+08:00",
                "metadata", Map.of("confidence", 0.92, "location", "living_room"));
        ResponseEntity<Map> created = postJson("/api/v1/care-events", req, tokenFamily01, Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        Number workflowId = (Number) created.getBody().get("workflowId");
        assertThat(workflowId).isNotNull();

        // Step 2b: 初始狀態 WAITING_RESPONSE / level=1
        ResponseEntity<Map> initial = getJson("/api/v1/workflows/" + workflowId, tokenFamily01, Map.class);
        assertThat(initial.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(initial.getBody().get("status")).isEqualTo("WAITING_RESPONSE");
        assertThat(((Number) initial.getBody().get("currentLevel")).intValue()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> initialTasks = (List<Map<String, Object>>) initial.getBody().get("tasks");
        assertThat(initialTasks).hasSize(1);
        assertThat(((Number) initialTasks.get(0).get("level")).intValue()).isEqualTo(1);

        // Step 3: 等到 timeout scanner 升級到 level=2
        await().atMost(15, SECONDS).pollInterval(500, MILLISECONDS).untilAsserted(() -> {
            ResponseEntity<Map> wf = getJson("/api/v1/workflows/" + workflowId, tokenFamily01, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tasks = (List<Map<String, Object>>) wf.getBody().get("tasks");
            assertThat(tasks).anySatisfy(t ->
                    assertThat(((Number) t.get("level")).intValue()).isEqualTo(2));
        });

        // 取 level=2 taskId
        ResponseEntity<Map> wf2 = getJson("/api/v1/workflows/" + workflowId, tokenFamily01, Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) wf2.getBody().get("tasks");
        Number level2TaskId = tasks.stream()
                .filter(t -> ((Number) t.get("level")).intValue() == 2)
                .map(t -> (Number) t.get("taskId"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("找不到 level=2 task"));

        // Step 4: level2 contact (family02) CONFIRM_SAFE
        Map<String, Object> actionReq = Map.of(
                "actionType", "CONFIRM_SAFE",
                "note", "已電話確認，長者安全");
        ResponseEntity<Map> actionResp = postJson(
                "/api/v1/care-tasks/" + level2TaskId + "/actions", actionReq, tokenFamily02, Map.class);
        assertThat(actionResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Step 5: workflow 應為 RESOLVED
        await().atMost(5, SECONDS).pollInterval(200, MILLISECONDS).untilAsserted(() -> {
            ResponseEntity<Map> wf3 = getJson("/api/v1/workflows/" + workflowId, tokenFamily01, Map.class);
            assertThat(wf3.getBody().get("status")).isEqualTo("RESOLVED");
            assertThat(wf3.getBody().get("completedAt")).isNotNull();
        });

        // Step 6: audit timeline 包含完整責任鏈
        ResponseEntity<List> audits = getJson(
                "/api/v1/workflows/" + workflowId + "/audit-logs", tokenFamily01, List.class);
        assertThat(audits.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<String> actions = ((List<Map<String, Object>>) audits.getBody()).stream()
                .map(o -> (String) o.get("action"))
                .toList();
        assertThat(actions).contains(
                "EVENT_CREATED",
                "WORKFLOW_STARTED",
                "TASK_CREATED",
                "NOTIFICATION_SENT",
                "TASK_TIMEOUT",
                "TASK_ESCALATED",
                "TASK_COMPLETED",
                "WORKFLOW_RESOLVED");
    }

    @Test
    void should_return_409_when_action_double_submitted() {
        // 對應系統設計 §18.2 #3：對同一任務重複 CONFIRM_SAFE，第二次應 409。
        Map<String, Object> req = Map.of(
                "elderId", 1001,
                "source", "MOBILE_APP",
                "eventType", "FALL_DETECTED",
                "occurredAt", "2026-04-27T12:30:00+08:00");
        ResponseEntity<Map> created = postJson("/api/v1/care-events", req, tokenFamily01, Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Number workflowId = (Number) created.getBody().get("workflowId");

        ResponseEntity<Map> wf = getJson("/api/v1/workflows/" + workflowId, tokenFamily01, Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) wf.getBody().get("tasks");
        Number taskId = (Number) tasks.get(0).get("taskId");

        Map<String, Object> action = Map.of("actionType", "CONFIRM_SAFE");

        ResponseEntity<Map> first = postJson(
                "/api/v1/care-tasks/" + taskId + "/actions", action, tokenFamily01, Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> second = postJson(
                "/api/v1/care-tasks/" + taskId + "/actions", action, tokenFamily01, Map.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void should_return_401_when_no_token() {
        // 沒帶 token 應該被 SecurityFilterChain 攔截為 401。
        Map<String, Object> req = Map.of(
                "elderId", 1001,
                "source", "MOBILE_APP",
                "eventType", "FALL_DETECTED",
                "occurredAt", "2026-04-27T13:00:00+08:00");
        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/v1/care-events", req, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void should_rotate_refresh_token_and_issue_new_access_token() {
        // 用 family01 的 refresh token 換新組
        Map<String, Object> body = Map.of("refreshToken", family01.refresh());
        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/v1/auth/refresh", body, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        String newAccess = (String) resp.getBody().get("accessToken");
        String newRefresh = (String) resp.getBody().get("refreshToken");
        assertThat(newAccess).isNotBlank().isNotEqualTo(family01.access());
        assertThat(newRefresh).isNotBlank().isNotEqualTo(family01.refresh());

        // 用新 access 應能 call 受保護資源
        ResponseEntity<List> audits = getJson(
                "/api/v1/workflows/0/audit-logs", newAccess, List.class);
        // workflowId=0 不存在但通過 auth → 應為 200 with empty list 或 4xx，不應 401/403
        assertThat(audits.getStatusCode().value()).isNotIn(401, 403);
    }

    @Test
    void should_detect_refresh_token_reuse_and_revoke_all_sessions() {
        // step 1: rotate family02 → 拿新 token
        Map<String, Object> body1 = Map.of("refreshToken", family02.refresh());
        ResponseEntity<Map> resp1 = restTemplate.postForEntity("/api/v1/auth/refresh", body1, Map.class);
        assertThat(resp1.getStatusCode()).isEqualTo(HttpStatus.OK);
        String refresh2 = (String) resp1.getBody().get("refreshToken");

        // step 2: reuse 舊 refresh token（已 revoked）→ 應 401，且觸發 reuse detection
        ResponseEntity<Map> resp2 = restTemplate.postForEntity("/api/v1/auth/refresh", body1, Map.class);
        assertThat(resp2.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // step 3: refresh2（也應因 reuse detection 一併被撤）→ 401
        Map<String, Object> body3 = Map.of("refreshToken", refresh2);
        ResponseEntity<Map> resp3 = restTemplate.postForEntity("/api/v1/auth/refresh", body3, Map.class);
        assertThat(resp3.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
