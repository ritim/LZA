package com.lza.aethercare.anomaly.service;

import com.lza.aethercare.anomaly.service.NoActivityDetector.NoActivityHit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * No-activity scanner 排程：每分鐘掃一次，命中即委派 detector.triggerFor 走獨立 transaction。
 */
@Component
@ConditionalOnProperty(
        name = "aethercare.scheduler.no-activity.enabled",
        havingValue = "true",
        matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class NoActivityScheduler {

    private final NoActivityDetector detector;

    @Scheduled(fixedDelayString = "${aethercare.scheduler.no-activity.fixed-delay:60000}")
    public void scan() {
        List<NoActivityHit> hits;
        try {
            hits = detector.findCandidates();
        } catch (Exception e) {
            log.warn("no-activity scan 失敗 reason={}", e.getMessage());
            return;
        }
        if (hits.isEmpty()) return;
        log.debug("no-activity scan: {} candidates", hits.size());
        for (NoActivityHit hit : hits) {
            try {
                detector.triggerFor(hit);
            } catch (Exception e) {
                log.warn("no-activity trigger 失敗 recipientId={} reason={}",
                        hit.careRecipientId(), e.getMessage());
            }
        }
    }
}
