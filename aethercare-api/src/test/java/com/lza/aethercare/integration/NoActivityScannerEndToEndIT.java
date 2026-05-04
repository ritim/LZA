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
 * Spec § Master §0 + §6 Flow D：NO_ACTIVITY / POSSIBLE_FALL scanner 端到端 IT。
 *
 * <p>elder_profile id=1001 mobility=LOW（seed 0019），scanner 命中時應建 POSSIBLE_FALL（HIGH risk
 * + FALL_RESPONSE workflow），非 NO_ACTIVITY。
 *
 * <p>Step 1  把 max_inactive_minutes_daytime/night 都壓到 1 分鐘
 * <p>Step 2  寫一筆 5 分鐘前的 last activity（MOVE）
 * <p>Step 3  Awaitility 等 scanner 觸發 → DB 出現 POSSIBLE_FALL 事件
 * <p>Step 4  驗對應 workflow + audit chain
 *
 * <p>關掉 missed-checkin / anomaly scanner，避免兩條 scanner 平行觸發干擾 assertion。
 */
@TestPropertySource(properties = {
        "aethercare.scheduler.no-activity.fixed-delay=500",
        "aethercare.scheduler.missed-checkin.enabled=false",
        "aethercare.anomaly.scheduler.enabled=false"
})
class NoActivityScannerEndToEndIT extends AbstractIntegrationTest {

    private static final long ELDER_ID = 1001L;

    @Autowired TestRestTemplate restTemplate;
    @Autowired JdbcTemplate jdbcTemplate;

    private String tokenFamily01;

    @BeforeEach
    void setUp() {
        Map<String, Object> body = Map.of("username", "family01", "password", "family123");
        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/v1/auth/login", body, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        tokenFamily01 = (String) resp.getBody().get("accessToken");

        // 把無活動門檻壓到 1 分鐘，scanner 必命中（5 分鐘前才有活動）
        jdbcTemplate.update("""
                UPDATE recipient_observation_settings
                   SET max_inactive_minutes_daytime = 1,
                       max_inactive_minutes_night = 1,
                       passive_monitoring_enabled = TRUE,
                       expected_checkin_time = '09:00:00',
                       updated_at = NOW()
                 WHERE care_recipient_id = ?
                """, ELDER_ID);

        // 清今日活動，再插一筆 5 分鐘前的 last activity（避免 seed 或前測殘留干擾）
        OffsetDateTime startOfDayUtc = OffsetDateTime.now(ZoneOffset.UTC).withHour(0).withMinute(0)
                .withSecond(0).withNano(0).minusDays(1);
        jdbcTemplate.update(
                "DELETE FROM elder_activity_event WHERE elder_id = ? AND occurred_at >= ?",
                ELDER_ID, startOfDayUtc);
        jdbcTemplate.update("""
                INSERT INTO elder_activity_event
                  (tenant_id, elder_id, activity_type, occurred_at, created_at)
                VALUES (1, ?, 'MOVE', NOW() - INTERVAL '5 minutes', NOW())
                """, ELDER_ID);
    }

    @Test
    void scanner_creates_possible_fall_event_for_low_mobility_recipient() {
        // Step 3：等 scanner 觸發 POSSIBLE_FALL（mobility=LOW 升級規則）
        await().atMost(30, SECONDS).pollInterval(500, MILLISECONDS).untilAsserted(() -> {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM care_event WHERE elder_id = ? AND event_type = 'POSSIBLE_FALL'",
                    Long.class, ELDER_ID);
            assertThat(count).isGreaterThanOrEqualTo(1);
        });

        // 取最新 event id
        Long eventId = jdbcTemplate.queryForObject("""
                SELECT id FROM care_event
                 WHERE elder_id = ? AND event_type = 'POSSIBLE_FALL'
                 ORDER BY id DESC LIMIT 1
                """, Long.class, ELDER_ID);

        // POSSIBLE_FALL → HIGH（spec §5）
        String risk = jdbcTemplate.queryForObject(
                "SELECT risk_level FROM care_event WHERE id = ?", String.class, eventId);
        assertThat(risk).isEqualTo("HIGH");

        // 對應 workflow 應啟動
        Long workflowId = jdbcTemplate.queryForObject("""
                SELECT id FROM care_workflow_instance
                 WHERE event_id = ?
                 ORDER BY id DESC LIMIT 1
                """, Long.class, eventId);
        assertThat(workflowId).isNotNull();

        // Step 4：API audit chain 驗證
        ResponseEntity<List> audits = restTemplate.exchange(
                "/api/v1/workflows/" + workflowId + "/audit-logs",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(tokenFamily01)),
                List.class);
        assertThat(audits.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<String> actions = ((List<Map<String, Object>>) audits.getBody()).stream()
                .map(o -> (String) o.get("action"))
                .toList();
        assertThat(actions).contains(
                "EVENT_CREATED",
                "WORKFLOW_STARTED",
                "TASK_CREATED",
                "NOTIFICATION_SENT");
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}
