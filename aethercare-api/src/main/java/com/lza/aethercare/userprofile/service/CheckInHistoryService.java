package com.lza.aethercare.userprofile.service;

import com.lza.aethercare.anomaly.entity.ElderActivityEvent;
import com.lza.aethercare.anomaly.enums.ActivityType;
import com.lza.aethercare.anomaly.repository.ElderActivityEventRepository;
import com.lza.aethercare.common.error.BusinessException;
import com.lza.aethercare.common.error.ErrorCode;
import com.lza.aethercare.userprofile.dto.CheckInDayItem;
import com.lza.aethercare.userprofile.dto.CheckInHistoryResponse;
import com.lza.aethercare.userprofile.entity.RecipientObservationSettings;
import com.lza.aethercare.userprofile.repository.ElderProfileRepository;
import com.lza.aethercare.userprofile.repository.RecipientObservationSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spec § Master §0：被照顧者最近 N 天 check-in 歷史，給 caregiver dashboard 的月曆視圖。
 *
 * <p>日界線以 Asia/Taipei 為準。
 * <ul>
 *   <li>任何一日有 CHECK_IN activity → CHECKED_IN（取最早一筆時間）</li>
 *   <li>過去日無 CHECK_IN → MISSED</li>
 *   <li>今日未到 expected + grace → PENDING</li>
 *   <li>今日已過 expected + grace 但仍無 CHECK_IN → MISSED</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CheckInHistoryService {

    /** Spec § Master §0：未設定 observation settings 時的 MVP 預設。 */
    private static final LocalTime DEFAULT_EXPECTED = LocalTime.of(9, 0);
    private static final int DEFAULT_GRACE_MINUTES = 60;
    private static final ZoneId TZ_TAIPEI = ZoneId.of("Asia/Taipei");

    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 90;

    private final ElderProfileRepository profileRepo;
    private final RecipientObservationSettingsRepository settingsRepo;
    private final ElderActivityEventRepository activityRepo;

    @Transactional(readOnly = true)
    public CheckInHistoryResponse getHistory(Long careRecipientId, int days) {
        if (!profileRepo.existsById(careRecipientId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "recipient " + careRecipientId);
        }
        int boundedDays = Math.min(MAX_DAYS, Math.max(MIN_DAYS, days));

        RecipientObservationSettings settings = settingsRepo.findByCareRecipientId(careRecipientId)
                .orElse(null);
        LocalTime expectedTime = settings != null && settings.getExpectedCheckinTime() != null
                ? settings.getExpectedCheckinTime() : DEFAULT_EXPECTED;
        int graceMinutes = settings != null && settings.getCheckinGraceMinutes() != null
                ? settings.getCheckinGraceMinutes() : DEFAULT_GRACE_MINUTES;

        OffsetDateTime now = OffsetDateTime.now();
        LocalDate today = now.atZoneSameInstant(TZ_TAIPEI).toLocalDate();
        LocalDate startDate = today.minusDays(boundedDays - 1L);

        OffsetDateTime fromInstant = startDate.atStartOfDay(TZ_TAIPEI).toOffsetDateTime();
        OffsetDateTime toInstant = today.plusDays(1).atStartOfDay(TZ_TAIPEI).toOffsetDateTime();

        List<ElderActivityEvent> events = activityRepo.findByElderIdAndTypeAndOccurredAtBetween(
                careRecipientId, ActivityType.CHECK_IN.name(), fromInstant, toInstant);

        Map<LocalDate, OffsetDateTime> firstCheckinPerDay = new HashMap<>();
        for (ElderActivityEvent e : events) {
            LocalDate d = e.getOccurredAt().atZoneSameInstant(TZ_TAIPEI).toLocalDate();
            OffsetDateTime cur = firstCheckinPerDay.get(d);
            if (cur == null || e.getOccurredAt().isBefore(cur)) {
                firstCheckinPerDay.put(d, e.getOccurredAt());
            }
        }

        OffsetDateTime todayExpectedCutoff = today.atTime(expectedTime)
                .plusMinutes(graceMinutes)
                .atZone(TZ_TAIPEI)
                .toOffsetDateTime();

        List<CheckInDayItem> items = new ArrayList<>(boundedDays);
        for (int i = 0; i < boundedDays; i++) {
            LocalDate d = startDate.plusDays(i);
            OffsetDateTime checked = firstCheckinPerDay.get(d);
            String status;
            if (checked != null) {
                status = "CHECKED_IN";
            } else if (d.isBefore(today)) {
                status = "MISSED";
            } else {
                status = now.isBefore(todayExpectedCutoff) ? "PENDING" : "MISSED";
            }
            items.add(new CheckInDayItem(d, checked, status));
        }

        return new CheckInHistoryResponse(careRecipientId, expectedTime, graceMinutes,
                boundedDays, items);
    }
}
