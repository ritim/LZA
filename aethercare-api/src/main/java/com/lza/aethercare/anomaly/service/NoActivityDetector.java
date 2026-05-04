package com.lza.aethercare.anomaly.service;

import com.lza.aethercare.anomaly.entity.ElderActivityEvent;
import com.lza.aethercare.anomaly.repository.ElderActivityEventRepository;
import com.lza.aethercare.common.time.Clock;
import com.lza.aethercare.event.dto.CreateCareEventRequest;
import com.lza.aethercare.event.enums.CareEventSource;
import com.lza.aethercare.event.enums.CareEventType;
import com.lza.aethercare.event.repository.CareEventRepository;
import com.lza.aethercare.event.service.CareEventService;
import com.lza.aethercare.userprofile.entity.ElderProfile;
import com.lza.aethercare.userprofile.entity.RecipientObservationSettings;
import com.lza.aethercare.userprofile.repository.ElderProfileRepository;
import com.lza.aethercare.userprofile.repository.RecipientObservationSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Spec § Master §0 + §6 Flow D：NO_ACTIVITY / POSSIBLE_FALL 偵測。
 *
 * <p>規則：
 * <ol>
 *   <li>對每位開啟 passive monitoring 的 recipient：</li>
 *   <li>查 last activity；空（從未上報）跳過避免新註冊就誤觸發。</li>
 *   <li>依當下時段（Asia/Taipei）選擇 daytime / night 門檻：</li>
 *   <ul>
 *       <li>daytime: 06:00-21:59，比較 max_inactive_minutes_daytime</li>
 *       <li>night : 22:00-05:59，比較 max_inactive_minutes_night</li>
 *   </ul>
 *   <li>距離 last activity 已超過門檻 → 命中。</li>
 *   <li>升級規則（spec §6 Flow D：HIGH risk when low mobility or fall history）：</li>
 *   <ul>
 *       <li>profile.mobility = "LOW" → POSSIBLE_FALL（HIGH，FALL_RESPONSE workflow）</li>
 *       <li>否則 → NO_ACTIVITY（MEDIUM，INACTIVITY_CHECK workflow）</li>
 *   </ul>
 *   <li>去重：今日已建過同型別事件則不重建。</li>
 * </ol>
 *
 * <p>fall history 進一步升級規則 post-MVP；目前 MVP 只看 mobility 欄位。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NoActivityDetector {

    private static final ZoneId DEMO_ZONE = ZoneId.of("Asia/Taipei");

    /** 06:00-21:59 視為日間。 */
    private static final int DAYTIME_START_HOUR = 6;
    private static final int DAYTIME_END_HOUR_EXCLUSIVE = 22;

    private final RecipientObservationSettingsRepository settingsRepo;
    private final ElderActivityEventRepository activityRepo;
    private final CareEventRepository careEventRepo;
    private final ElderProfileRepository profileRepo;
    private final CareEventService careEventService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<NoActivityHit> findCandidates() {
        OffsetDateTime now = clock.now();
        int hourLocal = now.atZoneSameInstant(DEMO_ZONE).getHour();
        boolean daytime = hourLocal >= DAYTIME_START_HOUR && hourLocal < DAYTIME_END_HOUR_EXCLUSIVE;
        LocalDate today = now.atZoneSameInstant(DEMO_ZONE).toLocalDate();
        OffsetDateTime startOfDay = today.atStartOfDay(DEMO_ZONE).toOffsetDateTime();

        List<NoActivityHit> hits = new ArrayList<>();
        for (RecipientObservationSettings s : settingsRepo
                .findAllByPassiveMonitoringEnabledTrueAndExpectedCheckinTimeNotNull()) {

            int thresholdMinutes = daytime
                    ? safeMinutes(s.getMaxInactiveMinutesDaytime())
                    : safeMinutes(s.getMaxInactiveMinutesNight());
            if (thresholdMinutes <= 0) continue;

            Optional<ElderActivityEvent> last = activityRepo.findFirstByElderIdOrderByOccurredAtDesc(s.getCareRecipientId());
            if (last.isEmpty()) continue; // 從未上報的 recipient 不誤觸

            long minutesSince = ChronoUnit.MINUTES.between(last.get().getOccurredAt(), now);
            if (minutesSince < thresholdMinutes) continue;

            CareEventType eventType = chooseEventType(s.getCareRecipientId());
            boolean alreadyTriggered = careEventRepo.existsByElderIdAndEventTypeAndOccurredAtAfter(
                    s.getCareRecipientId(), eventType, startOfDay);
            if (alreadyTriggered) continue;

            hits.add(new NoActivityHit(
                    s.getCareRecipientId(), eventType, last.get().getOccurredAt(),
                    thresholdMinutes, minutesSince, daytime, now));
        }
        return hits;
    }

    @Transactional
    public void triggerFor(NoActivityHit hit) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("triggeredBy", "NO_ACTIVITY_SCANNER");
        metadata.put("lastActivityAt", hit.lastActivityAt().toString());
        metadata.put("thresholdMinutes", hit.thresholdMinutes());
        metadata.put("minutesSinceLastActivity", hit.minutesSinceLastActivity());
        metadata.put("daytime", hit.daytime());
        metadata.put("scanAt", hit.scanAt().toString());

        CreateCareEventRequest req = CreateCareEventRequest.builder()
                .elderId(hit.careRecipientId())
                .source(CareEventSource.ANOMALY_DETECTOR)
                .eventType(hit.eventType())
                .occurredAt(hit.scanAt())
                .metadata(metadata)
                .build();

        careEventService.createAndStartWorkflow(req);
        log.info("NoActivityDetector triggered recipientId={} type={} sinceMin={} thresholdMin={}",
                hit.careRecipientId(), hit.eventType(), hit.minutesSinceLastActivity(), hit.thresholdMinutes());
    }

    private CareEventType chooseEventType(Long recipientId) {
        // mobility=LOW → POSSIBLE_FALL；缺 profile / 其他 mobility 級別 → NO_ACTIVITY
        return profileRepo.findById(recipientId)
                .map(ElderProfile::getMobility)
                .filter(m -> "LOW".equalsIgnoreCase(m))
                .map(m -> CareEventType.POSSIBLE_FALL)
                .orElse(CareEventType.NO_ACTIVITY);
    }

    private int safeMinutes(Integer v) {
        return v == null ? 0 : v;
    }

    /** 偵測命中結果：scheduler 用來餵 triggerFor。 */
    public record NoActivityHit(
            Long careRecipientId,
            CareEventType eventType,
            OffsetDateTime lastActivityAt,
            int thresholdMinutes,
            long minutesSinceLastActivity,
            boolean daytime,
            OffsetDateTime scanAt) {
    }
}
