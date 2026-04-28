package com.lza.aethercare.anomaly.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lza.aethercare.anomaly.entity.ElderActivityEvent;
import com.lza.aethercare.anomaly.repository.ElderActivityEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Multi-signal fusion：對指定窗口內某 elder 的 activity events，依 metadata.signalSource
 * 加權投票，計算 fusion confidence。AnomalyDetector 在 z-score 異常通過時呼叫此服務，
 * confidence 不足則 suppress trigger，降 false positive。
 *
 * <p>Confidence 公式：{@code min(1.0, totalWeight / 10 + (sourceCount - 1) * 0.2)}，
 * 多源（≥2 source）會額外 +0.2 分以反映「不同 sensor 互相佐證」價值。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MultiSignalFusionService {

    /** 預設 source（metadata.signalSource 缺值時 fallback）。 */
    public static final String UNKNOWN_SOURCE = "UNKNOWN";

    private static final TypeReference<Map<String, Object>> METADATA_TYPE =
            new TypeReference<>() {};

    private final ElderActivityEventRepository eventRepo;
    private final ObjectMapper objectMapper;

    @Value("${aethercare.anomaly.fusion.min-confidence:0.5}")
    private double minConfidence;

    @Value("#{${aethercare.anomaly.fusion.weights:{'WEARABLE':2.0,'IOT_SENSOR':1.5,'MOBILE_APP':1.0}}}")
    private Map<String, Double> sourceWeights;

    /**
     * 評估窗口內各訊號來源的 fusion confidence。
     *
     * @param elderId elder ID
     * @param windowStart 窗口起點（含）
     * @param windowEnd 窗口終點（不含）
     * @return FusionResult，含 confidence、source 種類數、各 source count、是否達 threshold
     */
    public FusionResult evaluate(Long elderId, OffsetDateTime windowStart, OffsetDateTime windowEnd) {
        List<ElderActivityEvent> events = eventRepo.findByElderIdAndOccurredAtBetween(
                elderId, windowStart, windowEnd);
        if (events.isEmpty()) {
            return new FusionResult(0.0, 0, Map.of(), false);
        }
        Map<String, Long> sourceCounts = new LinkedHashMap<>();
        for (ElderActivityEvent e : events) {
            String src = extractSource(e);
            sourceCounts.merge(src, 1L, Long::sum);
        }
        double totalWeight = sourceCounts.entrySet().stream()
                .mapToDouble(en -> sourceWeights.getOrDefault(en.getKey(), 1.0) * en.getValue())
                .sum();
        int sourceCount = sourceCounts.size();
        double confidence = Math.min(1.0,
                (totalWeight / 10.0) + Math.max(0, sourceCount - 1) * 0.2);
        boolean confident = confidence >= minConfidence;
        log.debug("fusion elderId={} sources={} totalWeight={} confidence={} confident={}",
                elderId, sourceCounts, totalWeight, confidence, confident);
        return new FusionResult(confidence, sourceCount, sourceCounts, confident);
    }

    private String extractSource(ElderActivityEvent event) {
        String meta = event.getMetadata();
        if (meta == null || meta.isBlank()) {
            return UNKNOWN_SOURCE;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(meta, METADATA_TYPE);
            Object src = map.get("signalSource");
            if (src instanceof String s && !s.isBlank()) {
                return s;
            }
        } catch (Exception e) {
            log.debug("無法解析 metadata 取得 signalSource eventId={}", event.getId(), e);
        }
        return UNKNOWN_SOURCE;
    }

    /** Fusion 評估結果。 */
    public record FusionResult(double confidence,
                               int sourceCount,
                               Map<String, Long> sourceCounts,
                               boolean confident) {
        public Map<String, Long> sourceCountsCopy() {
            return new HashMap<>(sourceCounts);
        }
    }
}
