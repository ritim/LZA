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
 * Spec § Master §0 / §7 canonical：被照顧者活動上報 + sensor / admin baseline / anomaly tooling。
 *
 * <p>原 {@code ElderActivityController}（{@code /api/v1/elders/*}）已退役，本 controller
 * 提供 spec canonical {@code /api/v1/care-recipients/{careRecipientId}/...} 路徑。
 *
 * <p>三條 endpoint：
 * <ul>
 *   <li>{@code POST .../activities} — sensor / app 上報活動</li>
 *   <li>{@code POST .../baseline/recalculate} — admin 重算 baseline</li>
 *   <li>{@code POST .../anomaly/detect} — admin 立即觸發異常偵測（避免等 scheduler）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/care-recipients")
@RequiredArgsConstructor
@Slf4j
public class CareRecipientActivityController {

    private final ActivityIngestionService ingestionService;
    private final BaselineCalculator baselineCalculator;
    private final AnomalyDetector anomalyDetector;

    @PostMapping("/{careRecipientId}/activities")
    public ResponseEntity<ActivityResponse> ingest(
            @PathVariable Long careRecipientId,
            @Valid @RequestBody CreateActivityRequest req) {
        ElderActivityEvent saved = ingestionService.ingest(careRecipientId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @PostMapping("/{careRecipientId}/baseline/recalculate")
    public ResponseEntity<Map<String, Object>> recalculate(@PathVariable Long careRecipientId) {
        int upserted = baselineCalculator.recalculateForElder(careRecipientId);
        return ResponseEntity.ok(Map.of("careRecipientId", careRecipientId, "upsertedBaselines", upserted));
    }

    @PostMapping("/{careRecipientId}/anomaly/detect")
    public ResponseEntity<List<DetectedAnomaly>> detectNow(@PathVariable Long careRecipientId) {
        return ResponseEntity.ok(anomalyDetector.detectForElder(careRecipientId));
    }

    private static ActivityResponse toResponse(ElderActivityEvent e) {
        return new ActivityResponse(e.getId(), e.getElderId(), e.getActivityType(),
                e.getOccurredAt(), e.getDurationSeconds(), e.getCreatedAt());
    }
}
