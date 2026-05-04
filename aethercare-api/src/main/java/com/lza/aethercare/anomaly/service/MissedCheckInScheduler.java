package com.lza.aethercare.anomaly.service;

import com.lza.aethercare.anomaly.service.MissedCheckInDetector.DetectionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Missed check-in scanner 排程：每分鐘掃一次，命中即委派 detector.triggerFor 走獨立 transaction。
 *
 * <p>由 {@code aethercare.scheduler.missed-checkin.enabled} 控制，預設啟用；
 * IT / spring boot test 跑 schema-only 場景可關掉避免 noise。
 */
@Component
@ConditionalOnProperty(
        name = "aethercare.scheduler.missed-checkin.enabled",
        havingValue = "true",
        matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class MissedCheckInScheduler {

    private final MissedCheckInDetector detector;

    @Scheduled(fixedDelayString = "${aethercare.scheduler.missed-checkin.fixed-delay:60000}")
    public void scan() {
        List<DetectionResult> hits;
        try {
            hits = detector.findCandidates();
        } catch (Exception e) {
            log.warn("missed check-in scan 失敗 reason={}", e.getMessage());
            return;
        }
        if (hits.isEmpty()) return;
        log.debug("missed check-in scan: {} candidates", hits.size());
        for (DetectionResult hit : hits) {
            try {
                detector.triggerFor(hit);
            } catch (Exception e) {
                log.warn("missed check-in trigger 失敗 recipientId={} reason={}",
                        hit.careRecipientId(), e.getMessage());
            }
        }
    }
}
