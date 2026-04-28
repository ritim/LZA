package com.lza.aethercare.anomaly.service;

import com.lza.aethercare.anomaly.entity.ElderActivityBaseline;
import com.lza.aethercare.anomaly.enums.ActivityType;
import com.lza.aethercare.anomaly.repository.ElderActivityBaselineRepository;
import com.lza.aethercare.anomaly.repository.ElderActivityEventRepository;
import com.lza.aethercare.common.time.Clock;
import com.lza.aethercare.event.dto.CreateCareEventRequest;
import com.lza.aethercare.event.enums.CareEventSource;
import com.lza.aethercare.event.enums.CareEventType;
import com.lza.aethercare.event.service.CareEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 異常偵測器：對指定長者比較過去一小時實際活動 count vs baseline mean/stddev，
 * 若 |z-score| 超過 threshold 即建立 ACTIVITY_ANOMALY 照護事件並啟動 workflow。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnomalyDetector {

    private final ElderActivityEventRepository eventRepo;
    private final ElderActivityBaselineRepository baselineRepo;
    private final CareEventService careEventService;
    private final MultiSignalFusionService fusionService;
    private final Clock clock;

    @Value("${aethercare.anomaly.z-score-threshold:3.0}")
    private double zScoreThreshold;

    @Transactional
    public List<DetectedAnomaly> detectForElder(Long elderId) {
        List<DetectedAnomaly> hits = new ArrayList<>();
        OffsetDateTime now = clock.now();
        int currentHour = now.atZoneSameInstant(ZoneOffset.UTC).getHour();
        OffsetDateTime windowStart = now.minusHours(1);

        for (ActivityType type : ActivityType.values()) {
            Optional<ElderActivityBaseline> baselineOpt =
                    baselineRepo.findByElderIdAndActivityTypeAndHourOfDay(elderId, type, currentHour);
            if (baselineOpt.isEmpty()) {
                continue;
            }
            ElderActivityBaseline b = baselineOpt.get();
            long actual = eventRepo.countInWindow(elderId, type.name(), windowStart, now);
            double zScore = (actual - b.getExpectedCountMean()) / b.getExpectedCountStddev();
            if (Math.abs(zScore) >= zScoreThreshold) {
                // Multi-signal fusion 二次確認：confidence 不足則 suppress 觸發，降 false positive
                MultiSignalFusionService.FusionResult fusion = fusionService.evaluate(elderId, windowStart, now);
                if (!fusion.confident()) {
                    log.info("anomaly z-score 過({}) 但 fusion confidence={} sources={} < threshold，suppress trigger",
                            zScore, fusion.confidence(), fusion.sourceCount());
                    continue;
                }
                DetectedAnomaly anomaly = new DetectedAnomaly(elderId, type, actual,
                        b.getExpectedCountMean(), b.getExpectedCountStddev(), zScore, now);
                triggerCareEvent(anomaly, fusion);
                hits.add(anomaly);
            }
        }
        return hits;
    }

    private void triggerCareEvent(DetectedAnomaly a, MultiSignalFusionService.FusionResult fusion) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("activityType", a.activityType().name());
        metadata.put("actualCount", a.actualCount());
        metadata.put("expectedMean", a.expectedMean());
        metadata.put("expectedStddev", a.expectedStddev());
        metadata.put("zScore", a.zScore());
        metadata.put("fusionConfidence", fusion.confidence());
        metadata.put("fusionSourceCount", fusion.sourceCount());

        CreateCareEventRequest req = CreateCareEventRequest.builder()
                .elderId(a.elderId())
                .source(CareEventSource.ANOMALY_DETECTOR)
                .eventType(CareEventType.ACTIVITY_ANOMALY)
                .occurredAt(a.detectedAt())
                .metadata(metadata)
                .build();
        careEventService.createAndStartWorkflow(req);
        log.info("anomaly detected elderId={} type={} z={}", a.elderId(), a.activityType(), a.zScore());
    }

    /** 偵測到的異常結果。 */
    public record DetectedAnomaly(Long elderId,
                                   ActivityType activityType,
                                   long actualCount,
                                   double expectedMean,
                                   double expectedStddev,
                                   double zScore,
                                   OffsetDateTime detectedAt) {
    }
}
