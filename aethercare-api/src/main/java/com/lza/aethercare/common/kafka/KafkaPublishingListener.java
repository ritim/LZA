package com.lza.aethercare.common.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lza.aethercare.common.event.PublishToKafka;
import com.lza.aethercare.common.outbox.OutboxMessage;
import com.lza.aethercare.common.outbox.OutboxMessageRepository;
import com.lza.aethercare.common.outbox.OutboxMessageStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Kafka 發送 listener：BEFORE_COMMIT 寫入 outbox，與業務同 transaction commit/rollback；
 *  實際 publish 由 OutboxPublisher scheduler 異步處理（含 retry / DEAD_LETTER）。
 *  fallbackExecution=true：無 transaction 上下文時（例如 scanner 直接呼叫）仍會執行。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaPublishingListener {

    private final OutboxMessageRepository outboxRepo;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
    public void enqueue(PublishToKafka ev) {
        try {
            String payload = objectMapper.writeValueAsString(ev.payload());
            OutboxMessage msg = OutboxMessage.builder()
                    .targetTopic(ev.topic())
                    .messageKey(ev.key())
                    .payload(payload)
                    .status(OutboxMessageStatus.PENDING)
                    .attemptCount(0)
                    .nextAttemptAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                    .build();
            outboxRepo.save(msg);
        } catch (Exception e) {
            // 寫 outbox 失敗會 propagate 讓主 transaction rollback（保證一致性）
            log.error("無法寫入 outbox topic={} key={}", ev.topic(), ev.key(), e);
            throw new RuntimeException("Outbox write failed", e);
        }
    }
}
