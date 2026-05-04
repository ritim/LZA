package com.lza.aethercare.anomaly.service;

import com.lza.aethercare.anomaly.entity.ElderActivityEvent;
import com.lza.aethercare.anomaly.enums.ActivityType;
import com.lza.aethercare.anomaly.repository.ElderActivityEventRepository;
import com.lza.aethercare.common.time.Clock;
import com.lza.aethercare.event.dto.CreateCareEventRequest;
import com.lza.aethercare.event.enums.CareEventSource;
import com.lza.aethercare.event.enums.CareEventType;
import com.lza.aethercare.event.repository.CareEventRepository;
import com.lza.aethercare.event.service.CareEventService;
import com.lza.aethercare.userprofile.entity.RecipientObservationSettings;
import com.lza.aethercare.userprofile.repository.RecipientObservationSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spec § Master §0 / §6 Flow C：MISSED_CHECK_IN 偵測。
 *
 * <p>觸發條件（每位開啟 passive monitoring 且設了 expectedCheckinTime 的長者）：
 * <ol>
 *   <li>當前時間 ≥ 今日預期 check-in 時間 + grace minutes</li>
 *   <li>今日 00:00 ~ 當下 沒有任何 CHECK_IN activity log</li>
 *   <li>今日尚未存在 MISSED_CHECK_IN 事件（去重）</li>
 * </ol>
 *
 * <p>命中即透過 {@link CareEventService} 建立 MEDIUM care event 並啟動 INACTIVITY_CHECK
 * workflow + level-1 task + audit + mock notification（呼叫 spec §0 完整 pipeline）。
 *
 * <p>時區：expectedCheckinTime 是 wall-clock LocalTime，依 spec § Master §0 規定使用
 * Asia/Taipei 解讀；MVP 不支援 per-household timezone 覆蓋。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MissedCheckInDetector {

    /** Spec § Master §0：demo 時區固定 Asia/Taipei；household-level timezone 是 post-MVP。 */
    private static final ZoneId DEMO_ZONE = ZoneId.of("Asia/Taipei");

    private final RecipientObservationSettingsRepository settingsRepo;
    private final ElderActivityEventRepository activityRepo;
    private final CareEventRepository careEventRepo;
    private final CareEventService careEventService;
    private final Clock clock;

    /**
     * 對所有有效 settings 跑一次掃描；每位 recipient 在自己 transaction 內處理，
     * 單筆失敗不影響其他 recipient（呼叫端負責 try/catch per recipient）。
     */
    @Transactional(readOnly = true)
    public List<DetectionResult> findCandidates() {
        OffsetDateTime now = clock.now();
        LocalDate today = now.atZoneSameInstant(DEMO_ZONE).toLocalDate();

        List<DetectionResult> hits = new ArrayList<>();
        for (RecipientObservationSettings s : settingsRepo
                .findAllByPassiveMonitoringEnabledTrueAndExpectedCheckinTimeNotNull()) {

            OffsetDateTime expectedAt = today.atTime(s.getExpectedCheckinTime())
                    .atZone(DEMO_ZONE).toOffsetDateTime();
            int grace = s.getCheckinGraceMinutes() == null ? 0 : s.getCheckinGraceMinutes();
            OffsetDateTime deadline = expectedAt.plusMinutes(grace);

            if (now.isBefore(deadline)) {
                continue; // 還沒過 grace
            }

            OffsetDateTime startOfDay = today.atStartOfDay(DEMO_ZONE).toOffsetDateTime();
            // 今日有 CHECK_IN → 視為已回報，跳過
            boolean hasCheckIn = activityRepo
                    .findByElderIdAndOccurredAtBetween(s.getCareRecipientId(), startOfDay, now)
                    .stream()
                    .anyMatch(e -> e.getActivityType() == ActivityType.CHECK_IN);
            if (hasCheckIn) continue;

            // 今日已建過 MISSED_CHECK_IN → 去重
            boolean alreadyTriggered = careEventRepo.existsByElderIdAndEventTypeAndOccurredAtAfter(
                    s.getCareRecipientId(), CareEventType.MISSED_CHECK_IN, startOfDay);
            if (alreadyTriggered) continue;

            hits.add(new DetectionResult(s.getCareRecipientId(), expectedAt, deadline, now));
        }
        return hits;
    }

    /** 為單一 recipient 真的建立事件；獨立 transaction，scheduler 為每筆呼叫一次。 */
    @Transactional
    public void triggerFor(DetectionResult hit) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("triggeredBy", "MISSED_CHECKIN_SCANNER");
        metadata.put("expectedCheckinAt", hit.expectedAt().toString());
        metadata.put("graceDeadlineAt", hit.graceDeadlineAt().toString());
        metadata.put("scanAt", hit.scanAt().toString());

        CreateCareEventRequest req = CreateCareEventRequest.builder()
                .elderId(hit.careRecipientId())
                .source(CareEventSource.ANOMALY_DETECTOR)
                .eventType(CareEventType.MISSED_CHECK_IN)
                .occurredAt(hit.scanAt())
                .metadata(metadata)
                .build();

        careEventService.createAndStartWorkflow(req);
        log.info("MissedCheckInDetector triggered recipientId={} expected={} now={}",
                hit.careRecipientId(), hit.expectedAt(), hit.scanAt());
    }

    /** 偵測命中結果：scheduler 用來餵 triggerFor。 */
    public record DetectionResult(
            Long careRecipientId,
            OffsetDateTime expectedAt,
            OffsetDateTime graceDeadlineAt,
            OffsetDateTime scanAt) {
    }
}
