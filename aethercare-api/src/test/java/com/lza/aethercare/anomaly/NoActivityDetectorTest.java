package com.lza.aethercare.anomaly;

import com.lza.aethercare.anomaly.entity.ElderActivityEvent;
import com.lza.aethercare.anomaly.enums.ActivityType;
import com.lza.aethercare.anomaly.repository.ElderActivityEventRepository;
import com.lza.aethercare.anomaly.service.NoActivityDetector;
import com.lza.aethercare.anomaly.service.NoActivityDetector.NoActivityHit;
import com.lza.aethercare.common.time.Clock;
import com.lza.aethercare.event.dto.CreateCareEventRequest;
import com.lza.aethercare.event.entity.CareEvent;
import com.lza.aethercare.event.enums.CareEventSource;
import com.lza.aethercare.event.enums.CareEventType;
import com.lza.aethercare.event.repository.CareEventRepository;
import com.lza.aethercare.event.service.CareEventService;
import com.lza.aethercare.event.service.CareEventService.CareEventResult;
import com.lza.aethercare.userprofile.entity.ElderProfile;
import com.lza.aethercare.userprofile.entity.RecipientObservationSettings;
import com.lza.aethercare.userprofile.repository.ElderProfileRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * Spec § Master §6 Flow D：NO_ACTIVITY / POSSIBLE_FALL 偵測單元測試。
 *
 * <p>固定 Asia/Taipei 時區，daytime threshold = 180 min、night threshold = 480 min。
 */
@ExtendWith(MockitoExtension.class)
class NoActivityDetectorTest {

    private static final ZoneOffset TPE = ZoneOffset.ofHours(8);
    private static final Long RECIPIENT_ID = 301L;

    @Mock RecipientObservationSettingsRepository settingsRepo;
    @Mock ElderActivityEventRepository activityRepo;
    @Mock CareEventRepository careEventRepo;
    @Mock ElderProfileRepository profileRepo;
    @Mock CareEventService careEventService;
    @Mock Clock clock;

    NoActivityDetector detector;

    @BeforeEach
    void setUp() {
        detector = new NoActivityDetector(settingsRepo, activityRepo, careEventRepo, profileRepo,
                careEventService, clock);
    }

    private RecipientObservationSettings settings(int daytime, int night) {
        return RecipientObservationSettings.builder()
                .id(1L).tenantId(1L).careRecipientId(RECIPIENT_ID)
                .expectedCheckinTime(LocalTime.of(9, 0))
                .checkinGraceMinutes(60)
                .maxInactiveMinutesDaytime(daytime)
                .maxInactiveMinutesNight(night)
                .passiveMonitoringEnabled(true)
                .build();
    }

    private OffsetDateTime tpe(int hour, int minute) {
        return OffsetDateTime.of(2026, 4, 27, hour, minute, 0, 0, TPE);
    }

    private ElderActivityEvent activityAt(OffsetDateTime when) {
        return ElderActivityEvent.builder()
                .id(99L).elderId(RECIPIENT_ID)
                .activityType(ActivityType.MOVE)
                .occurredAt(when)
                .build();
    }

    private ElderProfile profile(String mobility) {
        return ElderProfile.builder().id(RECIPIENT_ID).mobility(mobility).build();
    }

    /** Daytime 16:00 掃描，最後活動 12:00（差 240 min > 180 daytime threshold），mobility=NORMAL → NO_ACTIVITY。 */
    @Test
    void daytime_no_activity_normal_mobility_creates_no_activity_hit() {
        given(clock.now()).willReturn(tpe(16, 0));
        given(settingsRepo.findAllByPassiveMonitoringEnabledTrueAndExpectedCheckinTimeNotNull())
                .willReturn(List.of(settings(180, 480)));
        given(activityRepo.findFirstByElderIdOrderByOccurredAtDesc(RECIPIENT_ID))
                .willReturn(Optional.of(activityAt(tpe(12, 0))));
        given(profileRepo.findById(RECIPIENT_ID)).willReturn(Optional.of(profile("NORMAL")));
        given(careEventRepo.existsByElderIdAndEventTypeAndOccurredAtAfter(
                eq(RECIPIENT_ID), eq(CareEventType.NO_ACTIVITY), any())).willReturn(false);

        List<NoActivityHit> hits = detector.findCandidates();

        assertThat(hits).hasSize(1);
        NoActivityHit h = hits.get(0);
        assertThat(h.eventType()).isEqualTo(CareEventType.NO_ACTIVITY);
        assertThat(h.thresholdMinutes()).isEqualTo(180);
        assertThat(h.minutesSinceLastActivity()).isEqualTo(240);
        assertThat(h.daytime()).isTrue();
    }

    /** 同情境但 mobility=LOW → 升級為 POSSIBLE_FALL（spec §6 Flow D）。 */
    @Test
    void daytime_no_activity_low_mobility_escalates_to_possible_fall() {
        given(clock.now()).willReturn(tpe(16, 0));
        given(settingsRepo.findAllByPassiveMonitoringEnabledTrueAndExpectedCheckinTimeNotNull())
                .willReturn(List.of(settings(180, 480)));
        given(activityRepo.findFirstByElderIdOrderByOccurredAtDesc(RECIPIENT_ID))
                .willReturn(Optional.of(activityAt(tpe(12, 0))));
        given(profileRepo.findById(RECIPIENT_ID)).willReturn(Optional.of(profile("LOW")));
        given(careEventRepo.existsByElderIdAndEventTypeAndOccurredAtAfter(
                eq(RECIPIENT_ID), eq(CareEventType.POSSIBLE_FALL), any())).willReturn(false);

        List<NoActivityHit> hits = detector.findCandidates();

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).eventType()).isEqualTo(CareEventType.POSSIBLE_FALL);
    }

    /** Nighttime 03:00 掃描，最後活動前一天 23:00（差 240 min < 480 night threshold）→ 不命中。 */
    @Test
    void nighttime_within_threshold_does_not_trigger() {
        OffsetDateTime night = tpe(3, 0);
        given(clock.now()).willReturn(night);
        given(settingsRepo.findAllByPassiveMonitoringEnabledTrueAndExpectedCheckinTimeNotNull())
                .willReturn(List.of(settings(180, 480)));
        // 前一天 23:00
        OffsetDateTime lastActivity = OffsetDateTime.of(2026, 4, 26, 23, 0, 0, 0, TPE);
        given(activityRepo.findFirstByElderIdOrderByOccurredAtDesc(RECIPIENT_ID))
                .willReturn(Optional.of(activityAt(lastActivity)));

        List<NoActivityHit> hits = detector.findCandidates();

        assertThat(hits).isEmpty();
    }

    /** Recipient 從未上報 activity → detector 不誤觸（避免新註冊立刻爆 event）。 */
    @Test
    void no_history_recipient_is_skipped() {
        given(clock.now()).willReturn(tpe(16, 0));
        given(settingsRepo.findAllByPassiveMonitoringEnabledTrueAndExpectedCheckinTimeNotNull())
                .willReturn(List.of(settings(180, 480)));
        given(activityRepo.findFirstByElderIdOrderByOccurredAtDesc(RECIPIENT_ID))
                .willReturn(Optional.empty());

        List<NoActivityHit> hits = detector.findCandidates();

        assertThat(hits).isEmpty();
        then(careEventRepo).should(never())
                .existsByElderIdAndEventTypeAndOccurredAtAfter(any(), any(), any());
    }

    /** 今日已有同型別事件 → 去重不重建。 */
    @Test
    void already_triggered_today_is_deduped() {
        given(clock.now()).willReturn(tpe(16, 0));
        given(settingsRepo.findAllByPassiveMonitoringEnabledTrueAndExpectedCheckinTimeNotNull())
                .willReturn(List.of(settings(180, 480)));
        given(activityRepo.findFirstByElderIdOrderByOccurredAtDesc(RECIPIENT_ID))
                .willReturn(Optional.of(activityAt(tpe(12, 0))));
        given(profileRepo.findById(RECIPIENT_ID)).willReturn(Optional.of(profile("NORMAL")));
        given(careEventRepo.existsByElderIdAndEventTypeAndOccurredAtAfter(
                eq(RECIPIENT_ID), eq(CareEventType.NO_ACTIVITY), any())).willReturn(true);

        assertThat(detector.findCandidates()).isEmpty();
    }

    /** triggerFor 透過 CareEventService 完整 pipeline 建立事件，metadata 帶 scanner 證據。 */
    @Test
    void trigger_builds_no_activity_event_with_metadata() {
        OffsetDateTime last = tpe(12, 0);
        OffsetDateTime scan = tpe(16, 0);
        NoActivityHit hit = new NoActivityHit(RECIPIENT_ID, CareEventType.POSSIBLE_FALL,
                last, 180, 240L, true, scan);

        given(careEventService.createAndStartWorkflow(any()))
                .willReturn(new CareEventResult(
                        CareEvent.builder().id(901L).elderId(RECIPIENT_ID)
                                .eventType(CareEventType.POSSIBLE_FALL).build(),
                        CareWorkflowInstance.builder().id(902L).build()));

        detector.triggerFor(hit);

        ArgumentCaptor<CreateCareEventRequest> captor = ArgumentCaptor.forClass(CreateCareEventRequest.class);
        then(careEventService).should().createAndStartWorkflow(captor.capture());
        CreateCareEventRequest req = captor.getValue();

        assertThat(req.getElderId()).isEqualTo(RECIPIENT_ID);
        assertThat(req.getEventType()).isEqualTo(CareEventType.POSSIBLE_FALL);
        assertThat(req.getSource()).isEqualTo(CareEventSource.ANOMALY_DETECTOR);
        assertThat(req.getOccurredAt()).isEqualTo(scan);
        assertThat(req.getMetadata())
                .containsEntry("triggeredBy", "NO_ACTIVITY_SCANNER")
                .containsEntry("lastActivityAt", last.toString())
                .containsEntry("thresholdMinutes", 180)
                .containsEntry("minutesSinceLastActivity", 240L)
                .containsEntry("daytime", true)
                .containsEntry("scanAt", scan.toString());
    }
}
