package com.lza.aethercare.anomaly;

import com.lza.aethercare.anomaly.entity.ElderActivityBaseline;
import com.lza.aethercare.anomaly.enums.ActivityType;
import com.lza.aethercare.anomaly.repository.ElderActivityBaselineRepository;
import com.lza.aethercare.anomaly.repository.ElderActivityEventRepository;
import com.lza.aethercare.anomaly.service.BaselineCalculator;
import com.lza.aethercare.common.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/** BaselineCalculator 單元測試：mean/stddev 計算與 min-samples gate。 */
@ExtendWith(MockitoExtension.class)
class BaselineCalculatorTest {

    @Mock
    ElderActivityEventRepository eventRepo;
    @Mock
    ElderActivityBaselineRepository baselineRepo;
    @Mock
    Clock clock;

    BaselineCalculator calculator;

    private final OffsetDateTime now = OffsetDateTime.of(2026, 4, 27, 12, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        calculator = new BaselineCalculator(eventRepo, baselineRepo, clock);
        ReflectionTestUtils.setField(calculator, "lookbackDays", 14);
        ReflectionTestUtils.setField(calculator, "minSamples", 3);
        given(clock.now()).willReturn(now);
        // 預設所有 type 沒有資料
        given(eventRepo.aggregateHourlyCountsSince(any(), anyString(), any())).willReturn(List.of());
    }

    private static Object[] row(int hour, LocalDate day, long count) {
        return new Object[]{hour, Date.valueOf(day), count};
    }

    /** 驗證：3 個 sample (counts=2,4,6, hour=9) 計算 mean=4 stddev=sqrt(8/3)≈1.633，並 insert baseline。 */
    @Test
    void should_compute_mean_stddev_and_insert_when_samples_meet_threshold() {
        List<Object[]> rows = List.of(
                row(9, LocalDate.of(2026, 4, 20), 2L),
                row(9, LocalDate.of(2026, 4, 21), 4L),
                row(9, LocalDate.of(2026, 4, 22), 6L)
        );
        given(eventRepo.aggregateHourlyCountsSince(eq(1001L), eq(ActivityType.MOVE.name()), any()))
                .willReturn(rows);
        given(baselineRepo.findByElderIdAndActivityTypeAndHourOfDay(1001L, ActivityType.MOVE, 9))
                .willReturn(Optional.empty());

        int upserted = calculator.recalculateForElder(1001L);

        assertThat(upserted).isEqualTo(1);
        ArgumentCaptor<ElderActivityBaseline> cap = ArgumentCaptor.forClass(ElderActivityBaseline.class);
        then(baselineRepo).should().save(cap.capture());
        ElderActivityBaseline saved = cap.getValue();
        assertThat(saved.getElderId()).isEqualTo(1001L);
        assertThat(saved.getActivityType()).isEqualTo(ActivityType.MOVE);
        assertThat(saved.getHourOfDay()).isEqualTo(9);
        assertThat(saved.getExpectedCountMean()).isEqualTo(4.0);
        assertThat(saved.getExpectedCountStddev()).isCloseTo(Math.sqrt(8.0 / 3.0), org.assertj.core.data.Offset.offset(1e-9));
        assertThat(saved.getSampleCount()).isEqualTo(3);
        assertThat(saved.getUpdatedAt()).isEqualTo(now);
    }

    /** 驗證：sample 數 < min-samples (=3) 時不寫入 baseline。 */
    @Test
    void should_skip_when_samples_below_min_threshold() {
        List<Object[]> rows = List.of(
                row(9, LocalDate.of(2026, 4, 20), 5L),
                row(9, LocalDate.of(2026, 4, 21), 5L)
        );
        given(eventRepo.aggregateHourlyCountsSince(eq(2002L), eq(ActivityType.MEAL.name()), any()))
                .willReturn(rows);

        int upserted = calculator.recalculateForElder(2002L);

        assertThat(upserted).isZero();
        then(baselineRepo).should(never()).save(any());
        then(baselineRepo).should(never())
                .findByElderIdAndActivityTypeAndHourOfDay(any(), any(), anyInt());
    }
}
