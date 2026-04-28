package com.lza.aethercare.anomaly.service;

import com.lza.aethercare.anomaly.entity.ElderActivityBaseline;
import com.lza.aethercare.anomaly.enums.ActivityType;
import com.lza.aethercare.anomaly.repository.ElderActivityBaselineRepository;
import com.lza.aethercare.anomaly.repository.ElderActivityEventRepository;
import com.lza.aethercare.common.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Baseline 計算器：自過去 N 天的 activity event 中聚合 (elder, activity, hour-of-day) 的 mean / stddev，
 * upsert 至 elder_activity_baseline，作為 z-score 異常偵測的期望分布。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BaselineCalculator {

    /** 避免 stddev=0 導致 z-score 爆炸的下限。 */
    private static final double STDDEV_FLOOR = 0.5;

    private final ElderActivityEventRepository eventRepo;
    private final ElderActivityBaselineRepository baselineRepo;
    private final Clock clock;

    @Value("${aethercare.anomaly.baseline.lookback-days:14}")
    private int lookbackDays;

    @Value("${aethercare.anomaly.baseline.min-samples:3}")
    private int minSamples;

    @Transactional
    public int recalculateForElder(Long elderId) {
        OffsetDateTime from = clock.now().minusDays(lookbackDays);
        int upserted = 0;
        for (ActivityType type : ActivityType.values()) {
            List<Object[]> rows = eventRepo.aggregateHourlyCountsSince(elderId, type.name(), from);
            // group by hour_of_day → list of per-day counts
            Map<Integer, List<Long>> hourCounts = new HashMap<>();
            for (Object[] r : rows) {
                int hour = ((Number) r[0]).intValue();
                long cnt = ((Number) r[2]).longValue();
                hourCounts.computeIfAbsent(hour, k -> new ArrayList<>()).add(cnt);
            }
            for (Map.Entry<Integer, List<Long>> e : hourCounts.entrySet()) {
                if (e.getValue().size() < minSamples) {
                    continue;
                }
                double mean = e.getValue().stream().mapToLong(Long::longValue).average().orElse(0);
                double variance = e.getValue().stream()
                        .mapToDouble(c -> Math.pow(c - mean, 2)).average().orElse(0);
                double stddev = Math.max(Math.sqrt(variance), STDDEV_FLOOR);
                upsertBaseline(elderId, type, e.getKey(), mean, stddev, e.getValue().size());
                upserted++;
            }
        }
        log.info("BaselineCalculator: elderId={} upserted {} baselines", elderId, upserted);
        return upserted;
    }

    @Transactional
    public int recalculateAll() {
        int total = 0;
        for (Long elderId : eventRepo.findDistinctElderIds()) {
            total += recalculateForElder(elderId);
        }
        return total;
    }

    private void upsertBaseline(Long elderId, ActivityType type, int hour,
                                double mean, double stddev, int samples) {
        baselineRepo.findByElderIdAndActivityTypeAndHourOfDay(elderId, type, hour)
                .ifPresentOrElse(
                        existing -> {
                            existing.setExpectedCountMean(mean);
                            existing.setExpectedCountStddev(stddev);
                            existing.setSampleCount(samples);
                            existing.setUpdatedAt(clock.now());
                            baselineRepo.save(existing);
                        },
                        () -> baselineRepo.save(ElderActivityBaseline.builder()
                                .elderId(elderId)
                                .activityType(type)
                                .hourOfDay(hour)
                                .expectedCountMean(mean)
                                .expectedCountStddev(stddev)
                                .sampleCount(samples)
                                .updatedAt(clock.now())
                                .build())
                );
    }
}
