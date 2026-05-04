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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec § Master §10 Required Tests + § Frontend Behavior：被照顧者自助 endpoint 端到端驗測。
 *
 * <p>覆蓋 acceptance：
 * <ul>
 *   <li>Check-in 不啟動 workflow，只寫 activity log</li>
 *   <li>SOS 建 HIGH event + workflow + level-1 task + audit + mock notification</li>
 *   <li>Status report 建 FEELING_UNWELL MEDIUM event</li>
 *   <li>GET /today 反映 check-in 與 open events</li>
 *   <li>Recipient endpoint 走 X-Care-Recipient-Id mock auth，不需 JWT</li>
 * </ul>
 *
 * <p>關掉 missed-checkin / no-activity / anomaly scanner 防止背景 scanner 干擾 assertion。
 */
@TestPropertySource(properties = {
        "aethercare.scheduler.missed-checkin.enabled=false",
        "aethercare.scheduler.no-activity.enabled=false",
        "aethercare.anomaly.scheduler.enabled=false"
})
class RecipientSelfEndToEndIT extends AbstractIntegrationTest {

    private static final long ELDER_ID = 1001L;

    @Autowired TestRestTemplate restTemplate;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // 清今日 CHECK_IN，避免測試之間的 activity log 污染 today summary
        OffsetDateTime windowStart = OffsetDateTime.now(ZoneOffset.UTC).withHour(0).withMinute(0)
                .withSecond(0).withNano(0).minusDays(1);
        jdbcTemplate.update(
                "DELETE FROM elder_activity_event WHERE elder_id = ? AND activity_type = 'CHECK_IN' AND occurred_at >= ?",
                ELDER_ID, windowStart);
    }

    private HttpHeaders recipientHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-Care-Recipient-Id", String.valueOf(ELDER_ID));
        return h;
    }

    private <T> ResponseEntity<T> recipientPost(String path, Object body, Class<T> respType) {
        return restTemplate.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body, recipientHeaders()), respType);
    }

    private <T> ResponseEntity<T> recipientGet(String path, Class<T> respType) {
        return restTemplate.exchange(path, HttpMethod.GET,
                new HttpEntity<>(recipientHeaders()), respType);
    }

    @Test
    void check_in_writes_activity_log_without_workflow() {
        Long workflowsBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM care_workflow_instance", Long.class);

        ResponseEntity<Map> resp = recipientPost(
                "/api/v1/recipient/check-ins", Map.of(), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull();
        assertThat(((Number) resp.getBody().get("activityLogId")).longValue()).isPositive();
        assertThat(((Number) resp.getBody().get("careRecipientId")).longValue()).isEqualTo(ELDER_ID);

        // 確認沒新的 workflow 被建立
        Long workflowsAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM care_workflow_instance", Long.class);
        assertThat(workflowsAfter).isEqualTo(workflowsBefore);

        // 確認 activity log 落在 elder_activity_event
        Long checkInCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM elder_activity_event WHERE elder_id = ? AND activity_type = 'CHECK_IN'",
                Long.class, ELDER_ID);
        assertThat(checkInCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    void sos_creates_high_event_with_full_workflow_chain() {
        ResponseEntity<Map> resp = recipientPost(
                "/api/v1/recipient/sos",
                Map.of("note", "頭很暈想吐"),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("eventType")).isEqualTo("SOS");
        assertThat(resp.getBody().get("riskLevel")).isEqualTo("HIGH");
        Long eventId = ((Number) resp.getBody().get("eventId")).longValue();
        Long workflowId = ((Number) resp.getBody().get("workflowId")).longValue();
        assertThat(eventId).isPositive();
        assertThat(workflowId).isPositive();

        // metadata 應記錄 triggeredBy + note
        String metadata = jdbcTemplate.queryForObject(
                "SELECT metadata FROM care_event WHERE id = ?", String.class, eventId);
        assertThat(metadata).contains("RECIPIENT_SOS_BUTTON").contains("頭很暈想吐");

        // 應該至少有一個 PENDING task（mock notification 已寫，audit chain 由現有 service 寫）
        Long pendingTasks = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM care_task WHERE workflow_id = ? AND status = 'PENDING'",
                Long.class, workflowId);
        assertThat(pendingTasks).isGreaterThanOrEqualTo(1);

        // MVP notification 是 mock service（無 notification_record 表），通知事件以 audit
        // log 的 NOTIFICATION_SENT 為 source of truth；下方 audit 斷言會檢查。

        // audit chain：EVENT_CREATED + WORKFLOW_STARTED + TASK_CREATED + NOTIFICATION_SENT
        @SuppressWarnings("unchecked")
        List<String> actions = jdbcTemplate.queryForList(
                "SELECT action FROM care_audit_log WHERE workflow_id = ?", String.class, workflowId);
        assertThat(actions).contains(
                "EVENT_CREATED", "WORKFLOW_STARTED", "TASK_CREATED", "NOTIFICATION_SENT");
    }

    @Test
    void status_report_creates_feeling_unwell_event_with_symptom() {
        ResponseEntity<Map> resp = recipientPost(
                "/api/v1/recipient/status-reports",
                Map.of("symptom", "胸悶呼吸困難",
                        "dangerSignals", Map.of("hasChestPain", true)),
                Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("eventType")).isEqualTo("FEELING_UNWELL");
        assertThat(resp.getBody().get("riskLevel")).isEqualTo("MEDIUM");

        Long eventId = ((Number) resp.getBody().get("eventId")).longValue();
        String metadata = jdbcTemplate.queryForObject(
                "SELECT metadata FROM care_event WHERE id = ?", String.class, eventId);
        assertThat(metadata)
                .contains("RECIPIENT_FEELING_UNWELL")
                .contains("胸悶呼吸困難")
                .contains("hasChestPain");
    }

    @Test
    void today_summary_reflects_check_in() {
        // 還沒 check-in 時 → checkedInToday=false
        ResponseEntity<Map> before = recipientGet("/api/v1/recipient/today", Map.class);
        assertThat(before.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Boolean) before.getBody().get("checkedInToday")).isFalse();
        assertThat(before.getBody().get("latestCheckInAt")).isNull();

        // check-in 後 → checkedInToday=true 且 latestCheckInAt 不為 null
        recipientPost("/api/v1/recipient/check-ins", Map.of(), Map.class);
        ResponseEntity<Map> after = recipientGet("/api/v1/recipient/today", Map.class);
        assertThat((Boolean) after.getBody().get("checkedInToday")).isTrue();
        assertThat(after.getBody().get("latestCheckInAt")).isNotNull();
    }

    @Test
    void recipient_endpoint_does_not_require_jwt() {
        // SecurityConfig 對 /api/v1/recipient/** 是 permitAll；不帶 Bearer token 也應 200/201
        HttpHeaders noJwt = new HttpHeaders();
        noJwt.setContentType(MediaType.APPLICATION_JSON);
        noJwt.set("X-Care-Recipient-Id", String.valueOf(ELDER_ID));
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/recipient/check-ins", HttpMethod.POST,
                new HttpEntity<>(Map.of(), noJwt), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void recipient_endpoint_returns_400_when_id_missing() {
        HttpHeaders noId = new HttpHeaders();
        noId.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/api/v1/recipient/check-ins", HttpMethod.POST,
                new HttpEntity<>(Map.of(), noId), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
