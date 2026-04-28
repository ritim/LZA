package com.lza.aethercare.common.outbox;

import com.lza.aethercare.common.time.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/** Outbox publish handler：對單一 outbox 訊息執行 send、根據結果標記 PUBLISHED / 重試 / DEAD_LETTER。
 *  獨立 bean 以確保 @Transactional 經由 Spring proxy 生效。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublishHandler {

    private final OutboxMessageRepository outboxRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    @Value("${aethercare.outbox.max-attempts:10}")
    private int maxAttempts;

    @Value("${aethercare.outbox.backoff-base-seconds:5}")
    private long backoffBaseSeconds;

    private Counter publishFailureCounter;
    private Counter deadLetterCounter;

    @PostConstruct
    public void init() {
        publishFailureCounter = Counter.builder("aethercare.outbox.publish.failures").register(meterRegistry);
        deadLetterCounter = Counter.builder("aethercare.outbox.deadletter").register(meterRegistry);
    }

    @Transactional
    public void publishOne(Long id) {
        OutboxMessage msg = outboxRepo.findById(id).orElse(null);
        if (msg == null || msg.getStatus() != OutboxMessageStatus.PENDING) return;

        try {
            kafkaTemplate.send(msg.getTargetTopic(), msg.getMessageKey(), msg.getPayload()).get();
            int updated = outboxRepo.markPublished(id, clock.now());
            if (updated == 0) log.debug("Outbox id={} 已被其他 worker 標記，跳過", id);
        } catch (Exception e) {
            publishFailureCounter.increment();
            int nextAttempts = msg.getAttemptCount() + 1;
            String errMsg = e.getClass().getSimpleName() + ": " + e.getMessage();
            if (nextAttempts >= maxAttempts) {
                deadLetterCounter.increment();
                outboxRepo.markDeadLetter(id, errMsg);
                log.error("Outbox id={} 達 max attempts={}，標記 DEAD_LETTER", id, maxAttempts);
            } else {
                long delaySeconds = backoffBaseSeconds * (long) Math.pow(2, nextAttempts - 1);
                OffsetDateTime nextAttemptAt = clock.now().plusSeconds(delaySeconds);
                outboxRepo.markRetryScheduled(id, nextAttemptAt, errMsg);
                log.warn("Outbox id={} 失敗 attempt={}/{}, 將於 {} 後重試 ({}s)", id, nextAttempts, maxAttempts,
                        nextAttemptAt, delaySeconds);
            }
        }
    }
}
