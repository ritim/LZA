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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Spec § Master §6 Flow C：multi-caregiver escalation routing 端到端。
 *
 * <p>劇本：
 * <ol>
 *   <li>family01 建 SOS for elder=1001 → task level 1 派給 caregiver=2 (family01)</li>
 *   <li>把 task.deadline_at 改成過去，模擬 SLA 已過期</li>
 *   <li>等 timeout scanner（fixed-delay=200ms in AbstractIntegrationTest）抓到</li>
 *   <li>驗：原 task 變 TIMEOUT；新 level 2 task assignee=3 (family02)；
 *       audit 鏈含 TASK_TIMEOUT + ESCALATION_TRIGGERED</li>
 * </ol>
 *
 * <p>不關 timeout scanner（用 AbstractIntegrationTest 預設的 200ms），其他 scanner 全關以
 * 避免雜訊干擾 assertion。
 */
@TestPropertySource(properties = {
        "aethercare.scheduler.missed-checkin.enabled=false",
        "aethercare.scheduler.no-activity.enabled=false",
        "aethercare.anomaly.scheduler.enabled=false"
})
class MultiCaregiverEscalationEndToEndIT extends AbstractIntegrationTest {

    private static final long ELDER_ID = 1001L;
    private static final long CAREGIVER_LEVEL_1 = 2L;  // family01 (DemoUserSeeder)
    private static final long CAREGIVER_LEVEL_2 = 3L;  // family02

    @Autowired TestRestTemplate restTemplate;
    @Autowired JdbcTemplate jdbcTemplate;

    private String token;

    @BeforeEach
    void setUp() {
        Map<String, Object> body = Map.of("username", "family01", "password", "family123");
        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/v1/auth/login", body, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        token = (String) resp.getBody().get("accessToken");

        // 確保 elder=1001 escalation 鏈 OK：level 1=2 / level 2=3，SLA 拉短以加速 timeout
        jdbcTemplate.update("""
                UPDATE care_contact_escalation SET sla_seconds = 5
                 WHERE elder_id = ? AND level IN (1, 2)
                """, ELDER_ID);
    }

    @Test
    void sla_timeout_should_escalate_task_to_second_caregiver() {
        Map<String, Object> bag = createSosAndGetWorkflow();
        Number workflowId = (Number) bag.get("workflowId");
        Number taskId = (Number) bag.get("taskId");

        // sanity：level 1 task 派給 caregiver=2
        Long initialAssignee = jdbcTemplate.queryForObject(
                "SELECT assignee_id FROM care_task WHERE id = ?", Long.class, taskId.longValue());
        assertThat(initialAssignee).isEqualTo(CAREGIVER_LEVEL_1);

        // 把 deadline_at 設成過去，讓 timeout scanner 立刻抓到
        jdbcTemplate.update(
                "UPDATE care_task SET deadline_at = ? WHERE id = ?",
                OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(60),
                taskId.longValue());

        // 等 scanner 升級
        await().atMost(15, SECONDS).pollInterval(300, MILLISECONDS).untilAsserted(() -> {
            String level1Status = jdbcTemplate.queryForObject(
                    "SELECT status FROM care_task WHERE id = ?", String.class, taskId.longValue());
            assertThat(level1Status).isEqualTo("TIMEOUT");

            List<Map<String, Object>> level2Rows = jdbcTemplate.queryForList("""
                    SELECT id, assignee_id, level, status FROM care_task
                     WHERE workflow_id = ? AND level = 2
                    """, workflowId.longValue());
            assertThat(level2Rows).hasSize(1);
            assertThat(((Number) level2Rows.get(0).get("assignee_id")).longValue())
                    .isEqualTo(CAREGIVER_LEVEL_2);
            assertThat((String) level2Rows.get(0).get("status")).isEqualTo("PENDING");
        });

        // audit 鏈包含升級與第二次 NOTIFICATION_SENT
        ResponseEntity<List> audits = restTemplate.exchange(
                "/api/v1/workflows/" + workflowId + "/audit-logs",
                HttpMethod.GET, new HttpEntity<>(jwtHeaders()), List.class);
        assertThat(audits.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<String> actions = ((List<Map<String, Object>>) audits.getBody()).stream()
                .map(o -> (String) o.get("action")).toList();
        assertThat(actions).contains(
                "WORKFLOW_STARTED",
                "TASK_CREATED",
                "TASK_TIMEOUT",
                "TASK_ESCALATED",   // scanner 升級用此 audit；CareActionService NEED_HELP 才用 ESCALATION_TRIGGERED
                "NOTIFICATION_SENT");
        long taskCreated = actions.stream().filter("TASK_CREATED"::equals).count();
        long notified = actions.stream().filter("NOTIFICATION_SENT"::equals).count();
        // level 1 + level 2 各一次
        assertThat(taskCreated).isGreaterThanOrEqualTo(2);
        assertThat(notified).isGreaterThanOrEqualTo(2);
    }

    /** 升級到 level 2 後若 family02 也沒回應，第二次 timeout 應把 workflow 標為 UNRESOLVED（沒有 level 3）。 */
    @Test
    void second_level_timeout_with_no_level_3_should_mark_workflow_unresolved() {
        Map<String, Object> bag = createSosAndGetWorkflow();
        Number workflowId = (Number) bag.get("workflowId");
        Number taskId = (Number) bag.get("taskId");

        // level 1 過期
        jdbcTemplate.update("UPDATE care_task SET deadline_at = ? WHERE id = ?",
                OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(60), taskId.longValue());

        // 等 level 2 task 出現
        await().atMost(15, SECONDS).pollInterval(300, MILLISECONDS).untilAsserted(() -> {
            Integer level2Count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM care_task WHERE workflow_id = ? AND level = 2",
                    Integer.class, workflowId.longValue());
            assertThat(level2Count).isEqualTo(1);
        });

        // level 2 也讓它過期
        jdbcTemplate.update("""
                UPDATE care_task SET deadline_at = ?
                 WHERE workflow_id = ? AND level = 2
                """, OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(60), workflowId.longValue());

        // 等 workflow 狀態變 UNRESOLVED
        await().atMost(15, SECONDS).pollInterval(300, MILLISECONDS).untilAsserted(() -> {
            String wfStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM care_workflow_instance WHERE id = ?",
                    String.class, workflowId.longValue());
            assertThat(wfStatus).isEqualTo("UNRESOLVED");
        });
    }

    // ---------- helpers ----------

    private Map<String, Object> createSosAndGetWorkflow() {
        Map<String, Object> req = Map.of(
                "elderId", ELDER_ID,
                "source", "MOBILE_APP",
                "eventType", "SOS",
                "occurredAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
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
        Map<String, Object> level1 = tasks.stream()
                .filter(t -> ((Number) t.get("level")).intValue() == 1)
                .findFirst().orElseThrow();
        return Map.of("workflowId", workflowId, "taskId", level1.get("taskId"));
    }

    private HttpHeaders jwtHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}
