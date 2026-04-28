package com.lza.aethercare.common.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lza.aethercare.common.event.PublishToKafka;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Kafka 發送 listener：在 transaction commit 後才送出，避免 rollback 時訊息已外洩。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaPublishingListener {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /** 主要路徑：在 transaction commit 後送 Kafka。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommit(PublishToKafka ev) {
        send(ev);
    }

    /** Fallback：若無 transaction 上下文（例如 scanner 直接呼叫），直接送出並 warning。 */
    @EventListener
    public void onNoTx(PublishToKafka ev) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }
        log.warn("無 transaction 上下文直接發送 Kafka，topic={}, key={}", ev.topic(), ev.key());
        send(ev);
    }

    private void send(PublishToKafka ev) {
        try {
            String value = objectMapper.writeValueAsString(ev.payload());
            kafkaTemplate.send(ev.topic(), ev.key(), value);
        } catch (JsonProcessingException e) {
            log.warn("Kafka payload 序列化失敗 topic={} key={}", ev.topic(), ev.key(), e);
        } catch (RuntimeException e) {
            log.warn("Kafka 發送失敗 topic={} key={}", ev.topic(), ev.key(), e);
        }
    }
}
