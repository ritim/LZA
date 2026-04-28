package com.lza.aethercare.common.outbox;

import com.lza.aethercare.common.time.Clock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;

/** OutboxCleanupScheduler 單元測試：批次刪除、cutoff 計算、counter 累加。 */
@ExtendWith(MockitoExtension.class)
class OutboxCleanupSchedulerTest {

    @Mock
    OutboxMessageRepository outboxRepo;
    @Mock
    Clock clock;

    SimpleMeterRegistry meterRegistry;
    OutboxCleanupScheduler scheduler;

    private final OffsetDateTime now = OffsetDateTime.of(2026, 4, 28, 12, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        scheduler = new OutboxCleanupScheduler(outboxRepo, clock, meterRegistry);
        ReflectionTestUtils.setField(scheduler, "retentionDays", 7);
        ReflectionTestUtils.setField(scheduler, "batchSize", 1000);
        ReflectionTestUtils.setField(scheduler, "maxBatchesPerRun", 50);
        scheduler.init();
        lenient().when(clock.now()).thenReturn(now);
    }

    /** 驗證單批刪光（< batchSize）後即停止，counter 累加實際刪除數。 */
    @Test
    void should_stop_when_batch_returns_less_than_batchsize() {
        OffsetDateTime expectedCutoff = now.minusDays(7);
        given(outboxRepo.deletePublishedBefore(eq(expectedCutoff), anyInt())).willReturn(123);

        scheduler.cleanup();

        then(outboxRepo).should().deletePublishedBefore(eq(expectedCutoff), eq(1000));
        assertThat(meterRegistry.counter("aethercare.outbox.cleanup.rows-deleted").count()).isEqualTo(123.0);
    }

    /** 驗證連續批量刪除：第一次回滿 batchSize 會繼續跑，直到回少於 batchSize 或達 max-batches。 */
    @Test
    void should_loop_until_batch_smaller_than_batchsize() {
        given(outboxRepo.deletePublishedBefore(any(), eq(1000)))
                .willReturn(1000)   // 第一批滿
                .willReturn(1000)   // 第二批滿
                .willReturn(50);    // 第三批不滿，停止

        scheduler.cleanup();

        then(outboxRepo).should(org.mockito.Mockito.times(3)).deletePublishedBefore(any(), eq(1000));
        assertThat(meterRegistry.counter("aethercare.outbox.cleanup.rows-deleted").count()).isEqualTo(2050.0);
    }
}
