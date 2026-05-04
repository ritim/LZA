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
 * Spec § Master §0 + §6 Flow C：MISSED_CHECK_IN scanner 端到端 IT。
 *
 * <p>Step 1  清理 elder=1001 今日 CHECK_IN 與既有 MISSED_CHECK_IN 事件（避免歷史污染）
 * <p>Step 2  將 observation_settings 設為 expectedCheckinTime=00:00:00 + grace=0，
 *           讓當下時間必過門檻
 * <p>Step 3  Awaitility 等 scanner 觸發 → DB 出現 MISSED_CHECK_IN MEDIUM 事件
 * <p>Step 4  驗 audit chain 含 EVENT_CREATED / WORKFLOW_STARTED / TASK_CREATED / NOTIFICATION_SENT
 *
 * <p>{@code aethercare.scheduler.missed-checkin.fixed-delay=500} 把預設 60s 壓到 0.5s 加速；
 * 同時關閉 no-activity scanner 避免兩條 scanner 平行觸發干擾 assertion。
 */
@TestPropertySource(properties = {
        "aethercare.scheduler.missed-checkin.fixed-delay=500",
        "aethercare.scheduler.no-activity.enabled=false",
        "aethercare.anomaly.scheduler.enabled=false"
})
class MissedCheckInScannerEndToEndIT extends AbstractIntegrationTest {

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

        // 清今日 CHECK_IN，避免抑制 scanner。care_event / workflow / audit 不清理：
        // FK 鏈太深，且 scanner 內建「今日已有 MISSED_CHECK_IN 就不重複」去重，
        // 兩種起始狀態（已有 / 沒有）assertion 都成立。
        OffsetDateTime startOfDayUtc = OffsetDateTime.now(ZoneOffset.UTC).withHour(0).withMinute(0)
                .withSecond(0).withNano(0).minusDays(1);
        jdbcTemplate.update(
                "DELETE FROM elder_activity_event WHERE elder_id = ? AND activity_type = 'CHECK_IN' AND occurred_at >= ?",
                ELDER_ID, startOfDayUtc);

        // 強制把 expectedCheckinTime 設成 00:00、grace=0，當下時間必過門檻
        // 用 UPSERT 確保即使 seed 還沒 apply / 被前測清掉，row 都會存在
        jdbcTemplate.update("""
                INSERT INTO recipient_observation_settings
                  (tenant_id, care_recipient_id, expected_checkin_time, checkin_grace_minutes,
                   max_inactive_minutes_daytime, max_inactive_minutes_night,
                   passive_monitoring_enabled, created_at, updated_at)
                VALUES (1, ?, '00:00:00', 0, 180, 480, TRUE, NOW(), NOW())
                ON CONFLICT (care_recipient_id) DO UPDATE
                   SET expected_checkin_time = EXCLUDED.expected_checkin_time,
                       checkin_grace_minutes = EXCLUDED.checkin_grace_minutes,
                       passive_monitoring_enabled = EXCLUDED.passive_monitoring_enabled,
                       updated_at = NOW()
                """, ELDER_ID);
    }

    @Test
    void scanner_creates_missed_check_in_event_with_full_workflow_chain() {
        // Step 3：等 scanner 觸發 MISSED_CHECK_IN 事件
        await().atMost(30, SECONDS).pollInterval(500, MILLISECONDS).untilAsserted(() -> {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM care_event WHERE elder_id = ? AND event_type = 'MISSED_CHECK_IN'",
                    Long.class, ELDER_ID);
            assertThat(count).isGreaterThanOrEqualTo(1);
        });

        // 取最新 event id
        Long eventId = jdbcTemplate.queryForObject("""
                SELECT id FROM care_event
                 WHERE elder_id = ? AND event_type = 'MISSED_CHECK_IN'
                 ORDER BY id DESC LIMIT 1
                """, Long.class, ELDER_ID);
        assertThat(eventId).isNotNull();

        // Risk = MEDIUM（spec §5）
        String risk = jdbcTemplate.queryForObject(
                "SELECT risk_level FROM care_event WHERE id = ?", String.class, eventId);
        assertThat(risk).isEqualTo("MEDIUM");

        // 對應的 workflow 應已啟動
        Long workflowId = jdbcTemplate.queryForObject("""
                SELECT id FROM care_workflow_instance
                 WHERE event_id = ?
                 ORDER BY id DESC LIMIT 1
                """, Long.class, eventId);
        assertThat(workflowId).isNotNull();

        // Step 4：透過 API 取 audit timeline，驗完整責任鏈
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

    /** 寫一筆 CHECK_IN 後，scanner 不該再為今天建第二筆 MISSED_CHECK_IN。 */
    @Test
    void check_in_within_today_suppresses_further_missed_check_in_creation() {
        // 先讓 scanner 至少跑過一次（與上一個 test 同邏輯）
        await().atMost(30, SECONDS).pollInterval(500, MILLISECONDS).untilAsserted(() -> {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM care_event WHERE elder_id = ? AND event_type = 'MISSED_CHECK_IN'",
                    Long.class, ELDER_ID);
            assertThat(count).isGreaterThanOrEqualTo(1);
        });
        Long firstCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM care_event WHERE elder_id = ? AND event_type = 'MISSED_CHECK_IN'",
                Long.class, ELDER_ID);

        // 寫一筆 CHECK_IN（spec：CHECK_IN 抑制 MISSED_CHECK_IN，但已建的不會撤）
        jdbcTemplate.update("""
                INSERT INTO elder_activity_event
                  (tenant_id, elder_id, activity_type, occurred_at, created_at)
                VALUES (1, ?, 'CHECK_IN', NOW(), NOW())
                """, ELDER_ID);

        // 等待數個 scanner 週期，count 不該再增加（去重 + CHECK_IN 抑制）
        try {
            Thread.sleep(2_500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Long secondCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM care_event WHERE elder_id = ? AND event_type = 'MISSED_CHECK_IN'",
                Long.class, ELDER_ID);
        assertThat(secondCount).isEqualTo(firstCount);
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }
}
