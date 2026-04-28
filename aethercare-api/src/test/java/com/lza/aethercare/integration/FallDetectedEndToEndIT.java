package com.lza.aethercare.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * FALL_DETECTED 端到端整合測試：
 * 對應系統設計 §18.1 demo 場景與 §18.2 衝突場景 #3。
 * <p>
 * Step 1  POST /api/v1/care-events 建立事件 → 201, 取得 workflowId
 * Step 2  GET /api/v1/workflows/{id} → WAITING_RESPONSE, level=1 task
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

    /** 把 demo seed 的 SLA 壓縮到 2/5 秒，讓 timeout scanner 在 IT 內可觀測。 */
    @BeforeEach
    void compressSla() {
        jdbcTemplate.update("UPDATE care_contact_escalation SET sla_seconds = 2 WHERE level = 1");
        jdbcTemplate.update("UPDATE care_contact_escalation SET sla_seconds = 5 WHERE level = 2");
    }

    @Test
    void should_complete_fall_detected_workflow_with_full_audit_chain() {
        // Step 1: 建立事件
        Map<String, Object> req = Map.of(
                "elderId", 1001,
                "source", "MOBILE_APP",
                "eventType", "FALL_DETECTED",
                "occurredAt", "2026-04-27T12:00:00+08:00",
                "metadata", Map.of("confidence", 0.92, "location", "living_room"));
        ResponseEntity<Map> created = restTemplate.postForEntity("/api/v1/care-events", req, Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        Number workflowId = (Number) created.getBody().get("workflowId");
        assertThat(workflowId).isNotNull();

        // Step 2: 初始狀態 WAITING_RESPONSE / level=1 task
        ResponseEntity<Map> initial = restTemplate.getForEntity(
                "/api/v1/workflows/" + workflowId, Map.class);
        assertThat(initial.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(initial.getBody().get("status")).isEqualTo("WAITING_RESPONSE");
        assertThat(((Number) initial.getBody().get("currentLevel")).intValue()).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> initialTasks = (List<Map<String, Object>>) initial.getBody().get("tasks");
        assertThat(initialTasks).hasSize(1);
        assertThat(((Number) initialTasks.get(0).get("level")).intValue()).isEqualTo(1);
        assertThat(((Number) initialTasks.get(0).get("assigneeId")).longValue()).isEqualTo(2001L);

        // Step 3: 等到 timeout scanner 升級到 level=2
        await().atMost(15, SECONDS).pollInterval(500, MILLISECONDS).untilAsserted(() -> {
            ResponseEntity<Map> wf = restTemplate.getForEntity(
                    "/api/v1/workflows/" + workflowId, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tasks = (List<Map<String, Object>>) wf.getBody().get("tasks");
            assertThat(tasks).anySatisfy(t ->
                    assertThat(((Number) t.get("level")).intValue()).isEqualTo(2));
        });

        // 取 level=2 taskId
        ResponseEntity<Map> wf2 = restTemplate.getForEntity(
                "/api/v1/workflows/" + workflowId, Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) wf2.getBody().get("tasks");
        Number level2TaskId = tasks.stream()
                .filter(t -> ((Number) t.get("level")).intValue() == 2)
                .map(t -> (Number) t.get("taskId"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("找不到 level=2 task"));

        // Step 4: level2 contact CONFIRM_SAFE
        Map<String, Object> actionReq = Map.of(
                "actorId", 2002,
                "actionType", "CONFIRM_SAFE",
                "note", "已電話確認，長者安全");
        ResponseEntity<Map> actionResp = restTemplate.postForEntity(
                "/api/v1/care-tasks/" + level2TaskId + "/actions", actionReq, Map.class);
        assertThat(actionResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Step 5: workflow 應為 RESOLVED
        await().atMost(5, SECONDS).pollInterval(200, MILLISECONDS).untilAsserted(() -> {
            ResponseEntity<Map> wf3 = restTemplate.getForEntity(
                    "/api/v1/workflows/" + workflowId, Map.class);
            assertThat(wf3.getBody().get("status")).isEqualTo("RESOLVED");
            assertThat(wf3.getBody().get("completedAt")).isNotNull();
        });

        // Step 6: audit timeline 包含完整責任鏈
        ResponseEntity<List> audits = restTemplate.getForEntity(
                "/api/v1/workflows/" + workflowId + "/audit-logs", List.class);
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
        ResponseEntity<Map> created = restTemplate.postForEntity("/api/v1/care-events", req, Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Number workflowId = (Number) created.getBody().get("workflowId");

        ResponseEntity<Map> wf = restTemplate.getForEntity(
                "/api/v1/workflows/" + workflowId, Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) wf.getBody().get("tasks");
        Number taskId = (Number) tasks.get(0).get("taskId");

        Map<String, Object> action = Map.of(
                "actorId", 2001,
                "actionType", "CONFIRM_SAFE");

        ResponseEntity<Map> first = restTemplate.postForEntity(
                "/api/v1/care-tasks/" + taskId + "/actions", action, Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> second = restTemplate.postForEntity(
                "/api/v1/care-tasks/" + taskId + "/actions", action, Map.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
