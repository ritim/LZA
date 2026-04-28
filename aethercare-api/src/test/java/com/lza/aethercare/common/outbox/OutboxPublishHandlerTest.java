package com.lza.aethercare.common.outbox;

import com.lza.aethercare.common.time.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * OutboxPublishHandler 單元測試：驗證成功時標記 PUBLISHED、失敗時排程重試、達上限時標記 DEAD_LETTER。
 */
@ExtendWith(MockitoExtension.class)
class OutboxPublishHandlerTest {

    @Mock
    OutboxMessageRepository outboxRepo;
    @Mock
    KafkaTemplate<String, String> kafkaTemplate;
    @Mock
    Clock clock;

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private OutboxPublishHandler handler;

    private final int maxAttempts = 10;
    private final long backoffBaseSeconds = 5L;
    private final OffsetDateTime now = OffsetDateTime.of(2026, 4, 27, 12, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        handler = new OutboxPublishHandler(outboxRepo, kafkaTemplate, clock, meterRegistry);
        ReflectionTestUtils.setField(handler, "maxAttempts", maxAttempts);
        ReflectionTestUtils.setField(handler, "backoffBaseSeconds", backoffBaseSeconds);
        handler.init();
    }

    private OutboxMessage buildPending(int attemptCount) {
        return OutboxMessage.builder()
                .id(42L)
                .targetTopic("care.event.created")
                .messageKey("k-1")
                .payload("{\"a\":1}")
                .status(OutboxMessageStatus.PENDING)
                .attemptCount(attemptCount)
                .nextAttemptAt(now)
                .createdAt(now)
                .version(0)
                .build();
    }

    /** kafka send 成功時：呼叫 markPublished(id, now)，不會 schedule retry / dead letter。 */
    @Test
    void should_mark_published_when_send_succeeds() {
        OutboxMessage msg = buildPending(0);
        given(outboxRepo.findById(42L)).willReturn(Optional.of(msg));
        given(clock.now()).willReturn(now);
        given(kafkaTemplate.send(eq("care.event.created"), eq("k-1"), eq("{\"a\":1}")))
                .willReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));

        handler.publishOne(42L);

        then(outboxRepo).should().markPublished(42L, now);
        then(outboxRepo).should(never()).markRetryScheduled(any(), any(), anyString());
        then(outboxRepo).should(never()).markDeadLetter(any(), anyString());
        assertThat(meterRegistry.counter("aethercare.outbox.publish.failures").count()).isEqualTo(0.0);
        assertThat(meterRegistry.counter("aethercare.outbox.deadletter").count()).isEqualTo(0.0);
    }

    /** kafka send 失敗（attempt 未達上限）時：呼叫 markRetryScheduled，next_attempt_at = now + 5s（指數 backoff first attempt）。 */
    @Test
    void should_increment_attempt_and_schedule_retry_when_send_fails() {
        OutboxMessage msg = buildPending(0);
        given(outboxRepo.findById(42L)).willReturn(Optional.of(msg));
        given(clock.now()).willReturn(now);
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        given(kafkaTemplate.send(eq("care.event.created"), eq("k-1"), eq("{\"a\":1}"))).willReturn(failed);

        handler.publishOne(42L);

        // first retry：delay = backoffBaseSeconds * 2^0 = 5s
        OffsetDateTime expectedNext = now.plusSeconds(5L);
        then(outboxRepo).should().markRetryScheduled(eq(42L), eq(expectedNext), contains("broker down"));
        then(outboxRepo).should(never()).markPublished(any(), any());
        then(outboxRepo).should(never()).markDeadLetter(any(), anyString());
        assertThat(meterRegistry.counter("aethercare.outbox.publish.failures").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("aethercare.outbox.deadletter").count()).isEqualTo(0.0);
    }

    /** attemptCount = maxAttempts - 1 時再次失敗：標記 DEAD_LETTER，deadLetterCounter +1。 */
    @Test
    void should_mark_dead_letter_when_attempts_reach_max() {
        OutboxMessage msg = buildPending(maxAttempts - 1);
        given(outboxRepo.findById(42L)).willReturn(Optional.of(msg));
        // clock 在 dead letter 路徑不會被讀（不需 stub），避免 UnnecessaryStubbing
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("still down"));
        given(kafkaTemplate.send(eq("care.event.created"), eq("k-1"), eq("{\"a\":1}"))).willReturn(failed);

        handler.publishOne(42L);

        then(outboxRepo).should().markDeadLetter(eq(42L), contains("still down"));
        then(outboxRepo).should(never()).markRetryScheduled(any(), any(), anyString());
        then(outboxRepo).should(never()).markPublished(any(), any());
        assertThat(meterRegistry.counter("aethercare.outbox.publish.failures").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("aethercare.outbox.deadletter").count()).isEqualTo(1.0);
    }
}
