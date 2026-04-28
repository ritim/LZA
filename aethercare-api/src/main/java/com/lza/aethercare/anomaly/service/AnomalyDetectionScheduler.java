package com.lza.aethercare.anomaly.service;

import com.lza.aethercare.anomaly.repository.ElderActivityEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/** Anomaly scan 排程：每 fixed-delay 掃描所有有上報資料的長者並執行 detector。 */
@Component
@ConditionalOnProperty(name = "aethercare.anomaly.scheduler.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class AnomalyDetectionScheduler {

    private final ElderActivityEventRepository eventRepo;
    private final AnomalyDetector detector;

    @Scheduled(fixedDelayString = "${aethercare.anomaly.scheduler.fixed-delay:300000}")
    public void scan() {
        List<Long> elderIds = eventRepo.findDistinctElderIds();
        log.debug("Anomaly scan: {} elders", elderIds.size());
        for (Long elderId : elderIds) {
            try {
                detector.detectForElder(elderId);
            } catch (Exception e) {
                log.warn("anomaly scan 失敗 elderId={}", elderId, e);
            }
        }
    }
}
