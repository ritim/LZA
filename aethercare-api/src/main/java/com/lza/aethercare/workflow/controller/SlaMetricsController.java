package com.lza.aethercare.workflow.controller;

import com.lza.aethercare.workflow.dto.SlaSummaryResponse;
import com.lza.aethercare.workflow.dto.SlaTimelineBucket;
import com.lza.aethercare.workflow.service.SlaMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * SLA dashboard REST controller：提供 workflow 解決率、升級率、平均回應時間
 * 的彙總與時序資料給前端 dashboard 與保險證明使用。
 *
 * <p>受 USER role 保護（見 SecurityConfig）。預設 from = now-7d、to = now，
 * timeline bucket 預設 hour。
 */
@RestController
@RequestMapping("/api/v1/sla")
@RequiredArgsConstructor
@Slf4j
public class SlaMetricsController {

    private final SlaMetricsService slaMetricsService;

    @GetMapping("/summary")
    public ResponseEntity<SlaSummaryResponse> summary(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        OffsetDateTime[] window = defaultWindow(from, to);
        log.info("查詢 SLA summary from={} to={}", window[0], window[1]);
        return ResponseEntity.ok(slaMetricsService.summary(window[0], window[1]));
    }

    @GetMapping("/timeline")
    public ResponseEntity<List<SlaTimelineBucket>> timeline(
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(value = "bucket", required = false, defaultValue = "hour") String bucket) {
        OffsetDateTime[] window = defaultWindow(from, to);
        log.info("查詢 SLA timeline from={} to={} bucket={}", window[0], window[1], bucket);
        return ResponseEntity.ok(slaMetricsService.timeline(window[0], window[1], bucket));
    }

    /** 若 caller 沒帶 from / to，預設 [now-7d, now]，避免空查詢吃光 PG planner 時間。 */
    private static OffsetDateTime[] defaultWindow(OffsetDateTime from, OffsetDateTime to) {
        OffsetDateTime resolvedTo = to != null ? to : OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime resolvedFrom = from != null ? from : resolvedTo.minusDays(7);
        return new OffsetDateTime[]{resolvedFrom, resolvedTo};
    }
}
