package com.lza.aethercare.anomaly.controller;

import com.lza.aethercare.anomaly.dto.ActivityResponse;
import com.lza.aethercare.anomaly.dto.CreateActivityRequest;
import com.lza.aethercare.anomaly.entity.ElderActivityEvent;
import com.lza.aethercare.anomaly.service.ActivityIngestionService;
import com.lza.aethercare.anomaly.service.AnomalyDetector;
import com.lza.aethercare.anomaly.service.AnomalyDetector.DetectedAnomaly;
import com.lza.aethercare.anomaly.service.BaselineCalculator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 長者活動 / 異常偵測 REST Controller：上報活動、重算 baseline、立即觸發異常偵測。
 */
@RestController
@RequestMapping("/api/v1/elders")
@RequiredArgsConstructor
@Slf4j
public class ElderActivityController {

    private final ActivityIngestionService ingestionService;
    private final BaselineCalculator baselineCalculator;
    private final AnomalyDetector anomalyDetector;

    @PostMapping("/{elderId}/activities")
    public ResponseEntity<ActivityResponse> ingest(
            @PathVariable Long elderId,
            @Valid @RequestBody CreateActivityRequest req) {
        ElderActivityEvent saved = ingestionService.ingest(elderId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @PostMapping("/{elderId}/baseline/recalculate")
    public ResponseEntity<Map<String, Object>> recalculate(@PathVariable Long elderId) {
        int upserted = baselineCalculator.recalculateForElder(elderId);
        return ResponseEntity.ok(Map.of("elderId", elderId, "upsertedBaselines", upserted));
    }

    @PostMapping("/{elderId}/anomaly/detect")
    public ResponseEntity<List<DetectedAnomaly>> detectNow(@PathVariable Long elderId) {
        return ResponseEntity.ok(anomalyDetector.detectForElder(elderId));
    }

    private static ActivityResponse toResponse(ElderActivityEvent e) {
        return new ActivityResponse(e.getId(), e.getElderId(), e.getActivityType(),
                e.getOccurredAt(), e.getDurationSeconds(), e.getCreatedAt());
    }
}
