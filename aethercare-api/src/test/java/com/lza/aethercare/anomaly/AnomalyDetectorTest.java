package com.lza.aethercare.anomaly;

import com.lza.aethercare.anomaly.entity.ElderActivityBaseline;
import com.lza.aethercare.anomaly.enums.ActivityType;
import com.lza.aethercare.anomaly.repository.ElderActivityBaselineRepository;
import com.lza.aethercare.anomaly.repository.ElderActivityEventRepository;
import com.lza.aethercare.anomaly.service.AnomalyDetector;
import com.lza.aethercare.anomaly.service.AnomalyDetector.DetectedAnomaly;
import com.lza.aethercare.common.time.Clock;
import com.lza.aethercare.event.dto.CreateCareEventRequest;
import com.lza.aethercare.event.enums.CareEventSource;
import com.lza.aethercare.event.enums.CareEventType;
import com.lza.aethercare.event.service.CareEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/** AnomalyDetector 單元測試：z-score 觸發 / 未觸發行為。 */
@ExtendWith(MockitoExtension.class)
class AnomalyDetectorTest {

    @Mock
    ElderActivityEventRepository eventRepo;
    @Mock
    ElderActivityBaselineRepository baselineRepo;
    @Mock
    CareEventService careEventService;
    @Mock
    Clock clock;

    AnomalyDetector detector;

    private final OffsetDateTime now = OffsetDateTime.of(2026, 4, 27, 9, 30, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        detector = new AnomalyDetector(eventRepo, baselineRepo, careEventService, clock);
        ReflectionTestUtils.setField(detector, "zScoreThreshold", 3.0);
        given(clock.now()).willReturn(now);
        // 預設沒 baseline
        given(baselineRepo.findByElderIdAndActivityTypeAndHourOfDay(any(), any(), anyInt()))
                .willReturn(Optional.empty());
    }

    private ElderActivityBaseline baseline(ActivityType type, double mean, double stddev) {
        return ElderActivityBaseline.builder()
                .id(1L).elderId(1001L).activityType(type).hourOfDay(9)
                .expectedCountMean(mean).expectedCountStddev(stddev).sampleCount(5).updatedAt(now)
                .build();
    }

    /** 驗證 |z-score| 超過 threshold 時觸發 ACTIVITY_ANOMALY workflow。 */
    @Test
    void should_trigger_care_event_when_z_score_exceeds_threshold() {
        // baseline mean=5, stddev=0.5 → actual=0 → z = (0-5)/0.5 = -10
        given(baselineRepo.findByElderIdAndActivityTypeAndHourOfDay(1001L, ActivityType.MOVE, 9))
                .willReturn(Optional.of(baseline(ActivityType.MOVE, 5.0, 0.5)));
        given(eventRepo.countInWindow(eq(1001L), eq(ActivityType.MOVE.name()), any(), any()))
                .willReturn(0L);

        List<DetectedAnomaly> hits = detector.detectForElder(1001L);

        assertThat(hits).hasSize(1);
        DetectedAnomaly a = hits.get(0);
        assertThat(a.activityType()).isEqualTo(ActivityType.MOVE);
        assertThat(a.actualCount()).isZero();
        assertThat(a.zScore()).isEqualTo(-10.0);

        ArgumentCaptor<CreateCareEventRequest> cap = ArgumentCaptor.forClass(CreateCareEventRequest.class);
        then(careEventService).should().createAndStartWorkflow(cap.capture());
        CreateCareEventRequest req = cap.getValue();
        assertThat(req.getElderId()).isEqualTo(1001L);
        assertThat(req.getEventType()).isEqualTo(CareEventType.ACTIVITY_ANOMALY);
        assertThat(req.getSource()).isEqualTo(CareEventSource.ANOMALY_DETECTOR);
        assertThat(req.getOccurredAt()).isEqualTo(now);
        assertThat(req.getMetadata()).containsEntry("activityType", "MOVE")
                .containsEntry("actualCount", 0L)
                .containsEntry("zScore", -10.0);
    }

    /** 驗證 |z-score| 在 threshold 以下時不觸發。 */
    @Test
    void should_not_trigger_when_z_score_within_threshold() {
        // baseline mean=5, stddev=2 → actual=4 → z = -0.5
        given(baselineRepo.findByElderIdAndActivityTypeAndHourOfDay(1001L, ActivityType.MOVE, 9))
                .willReturn(Optional.of(baseline(ActivityType.MOVE, 5.0, 2.0)));
        given(eventRepo.countInWindow(eq(1001L), eq(ActivityType.MOVE.name()), any(), any()))
                .willReturn(4L);

        List<DetectedAnomaly> hits = detector.detectForElder(1001L);

        assertThat(hits).isEmpty();
        then(careEventService).should(never()).createAndStartWorkflow(any());
    }
}
