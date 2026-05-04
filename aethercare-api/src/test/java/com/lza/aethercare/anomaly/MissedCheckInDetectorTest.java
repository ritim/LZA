package com.lza.aethercare.anomaly;

import com.lza.aethercare.anomaly.entity.ElderActivityEvent;
import com.lza.aethercare.anomaly.enums.ActivityType;
import com.lza.aethercare.anomaly.repository.ElderActivityEventRepository;
import com.lza.aethercare.anomaly.service.MissedCheckInDetector;
import com.lza.aethercare.anomaly.service.MissedCheckInDetector.DetectionResult;
import com.lza.aethercare.common.time.Clock;
import com.lza.aethercare.event.dto.CreateCareEventRequest;
import com.lza.aethercare.event.entity.CareEvent;
import com.lza.aethercare.event.enums.CareEventSource;
import com.lza.aethercare.event.enums.CareEventType;
import com.lza.aethercare.event.repository.CareEventRepository;
import com.lza.aethercare.event.service.CareEventService;
import com.lza.aethercare.event.service.CareEventService.CareEventResult;
import com.lza.aethercare.userprofile.entity.RecipientObservationSettings;
import com.lza.aethercare.userprofile.repository.RecipientObservationSettingsRepository;
import com.lza.aethercare.workflow.entity.CareWorkflowInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * Spec § Master §0 Required Backend Behavior + Flow C：
 * MISSED_CHECK_IN 偵測單元測試。
 *
 * <p>固定時區 Asia/Taipei (UTC+8) 計算 expectedCheckinTime + grace；
 * now = 10:30 Asia/Taipei (= 02:30 UTC) 對應 09:00 + 60min grace 已過。
 */
@ExtendWith(MockitoExtension.class)
class MissedCheckInDetectorTest {

    private static final ZoneOffset TPE = ZoneOffset.ofHours(8);
    private static final Long RECIPIENT_ID = 301L;

    @Mock RecipientObservationSettingsRepository settingsRepo;
    @Mock ElderActivityEventRepository activityRepo;
    @Mock CareEventRepository careEventRepo;
    @Mock CareEventService careEventService;
    @Mock Clock clock;

    MissedCheckInDetector detector;

    @BeforeEach
    void setUp() {
        detector = new MissedCheckInDetector(settingsRepo, activityRepo, careEventRepo, careEventService, clock);
    }

    private RecipientObservationSettings settings(LocalTime expected, int graceMinutes) {
        return RecipientObservationSettings.builder()
                .id(1L)
                .tenantId(1L)
                .careRecipientId(RECIPIENT_ID)
                .expectedCheckinTime(expected)
                .checkinGraceMinutes(graceMinutes)
                .maxInactiveMinutesDaytime(180)
                .maxInactiveMinutesNight(480)
                .passiveMonitoringEnabled(true)
                .build();
    }

    private OffsetDateTime tpe(int hour, int minute) {
        return OffsetDateTime.of(2026, 4, 27, hour, minute, 0, 0, TPE);
    }

    private ElderActivityEvent checkInAt(int hour, int minute) {
        return ElderActivityEvent.builder()
                .id(99L)
                .elderId(RECIPIENT_ID)
                .activityType(ActivityType.CHECK_IN)
                .occurredAt(tpe(hour, minute))
                .build();
    }

    /** 09:00 expected + 60 grace，10:30 掃描，今日無 CHECK_IN、無既有 MISSED_CHECK_IN → 命中。 */
    @Test
    void should_detect_when_past_grace_with_no_checkin() {
        given(clock.now()).willReturn(tpe(10, 30));
        given(settingsRepo.findAllByPassiveMonitoringEnabledTrueAndExpectedCheckinTimeNotNull())
                .willReturn(List.of(settings(LocalTime.of(9, 0), 60)));
        given(activityRepo.findByElderIdAndOccurredAtBetween(eq(RECIPIENT_ID), any(), any()))
                .willReturn(List.of()); // 今日完全沒上報
        given(careEventRepo.existsByElderIdAndEventTypeAndOccurredAtAfter(
                eq(RECIPIENT_ID), eq(CareEventType.MISSED_CHECK_IN), any()))
                .willReturn(false);

        List<DetectionResult> hits = detector.findCandidates();

        assertThat(hits).hasSize(1);
        DetectionResult r = hits.get(0);
        assertThat(r.careRecipientId()).isEqualTo(RECIPIENT_ID);
        assertThat(r.expectedAt()).isEqualTo(tpe(9, 0));
        assertThat(r.graceDeadlineAt()).isEqualTo(tpe(10, 0));
    }

    /** 09:30 掃描（grace 還沒到）→ 不命中。 */
    @Test
    void should_skip_when_within_grace_window() {
        given(clock.now()).willReturn(tpe(9, 30));
        given(settingsRepo.findAllByPassiveMonitoringEnabledTrueAndExpectedCheckinTimeNotNull())
                .willReturn(List.of(settings(LocalTime.of(9, 0), 60)));

        List<DetectionResult> hits = detector.findCandidates();

        assertThat(hits).isEmpty();
        // 還沒到 grace，scanner 不該打 activity / event repo 浪費 IO
        then(activityRepo).should(never()).findByElderIdAndOccurredAtBetween(any(), any(), any());
        then(careEventRepo).should(never())
                .existsByElderIdAndEventTypeAndOccurredAtAfter(any(), any(), any());
    }

    /** 今日已有 CHECK_IN → 不命中（spec：CHECK_IN 抑制 MISSED_CHECK_IN）。 */
    @Test
    void should_skip_when_recipient_already_checked_in_today() {
        given(clock.now()).willReturn(tpe(10, 30));
        given(settingsRepo.findAllByPassiveMonitoringEnabledTrueAndExpectedCheckinTimeNotNull())
                .willReturn(List.of(settings(LocalTime.of(9, 0), 60)));
        given(activityRepo.findByElderIdAndOccurredAtBetween(eq(RECIPIENT_ID), any(), any()))
                .willReturn(List.of(checkInAt(8, 45)));

        List<DetectionResult> hits = detector.findCandidates();

        assertThat(hits).isEmpty();
        // CHECK_IN 命中後不必再查 care event 去重，省一次 query
        then(careEventRepo).should(never())
                .existsByElderIdAndEventTypeAndOccurredAtAfter(any(), any(), any());
    }

    /** 今日已有 MISSED_CHECK_IN 事件 → 不再重建（去重）。 */
    @Test
    void should_skip_when_already_triggered_today() {
        given(clock.now()).willReturn(tpe(10, 30));
        given(settingsRepo.findAllByPassiveMonitoringEnabledTrueAndExpectedCheckinTimeNotNull())
                .willReturn(List.of(settings(LocalTime.of(9, 0), 60)));
        given(activityRepo.findByElderIdAndOccurredAtBetween(eq(RECIPIENT_ID), any(), any()))
                .willReturn(List.of());
        given(careEventRepo.existsByElderIdAndEventTypeAndOccurredAtAfter(
                eq(RECIPIENT_ID), eq(CareEventType.MISSED_CHECK_IN), any()))
                .willReturn(true);

        List<DetectionResult> hits = detector.findCandidates();

        assertThat(hits).isEmpty();
    }

    /** 沒有有效 settings（passive 停用 / 無 expected）→ repo 直接回空，detector 不命中。 */
    @Test
    void should_return_empty_when_no_eligible_settings() {
        given(clock.now()).willReturn(tpe(10, 30));
        given(settingsRepo.findAllByPassiveMonitoringEnabledTrueAndExpectedCheckinTimeNotNull())
                .willReturn(List.of());

        List<DetectionResult> hits = detector.findCandidates();

        assertThat(hits).isEmpty();
        then(activityRepo).shouldHaveNoInteractions();
        then(careEventRepo).shouldHaveNoInteractions();
    }

    /** triggerFor 透過 CareEventService 完整 pipeline 建立 MISSED_CHECK_IN 事件，metadata 帶 scanner 證據。 */
    @Test
    void trigger_builds_missed_checkin_event_with_metadata() {
        OffsetDateTime expected = tpe(9, 0);
        OffsetDateTime deadline = tpe(10, 0);
        OffsetDateTime scan = tpe(10, 30);
        DetectionResult hit = new DetectionResult(RECIPIENT_ID, expected, deadline, scan);

        given(careEventService.createAndStartWorkflow(any()))
                .willReturn(new CareEventResult(
                        CareEvent.builder().id(555L).elderId(RECIPIENT_ID)
                                .eventType(CareEventType.MISSED_CHECK_IN).build(),
                        CareWorkflowInstance.builder().id(777L).build()));

        detector.triggerFor(hit);

        ArgumentCaptor<CreateCareEventRequest> captor = ArgumentCaptor.forClass(CreateCareEventRequest.class);
        then(careEventService).should().createAndStartWorkflow(captor.capture());
        CreateCareEventRequest req = captor.getValue();

        assertThat(req.getElderId()).isEqualTo(RECIPIENT_ID);
        assertThat(req.getEventType()).isEqualTo(CareEventType.MISSED_CHECK_IN);
        assertThat(req.getSource()).isEqualTo(CareEventSource.ANOMALY_DETECTOR);
        assertThat(req.getOccurredAt()).isEqualTo(scan);
        assertThat(req.getMetadata())
                .containsEntry("triggeredBy", "MISSED_CHECKIN_SCANNER")
                .containsEntry("expectedCheckinAt", expected.toString())
                .containsEntry("graceDeadlineAt", deadline.toString())
                .containsEntry("scanAt", scan.toString());
    }
}
