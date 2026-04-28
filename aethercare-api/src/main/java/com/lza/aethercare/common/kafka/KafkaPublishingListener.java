package com.lza.aethercare.common.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lza.aethercare.common.event.PublishToKafka;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Kafka 發送 listener：在 transaction commit 後才送出，避免 rollback 時訊息已外洩。
 *  fallbackExecution=true：無 transaction 上下文時（例如 scanner 直接呼叫）仍會執行。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaPublishingListener {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void publish(PublishToKafka ev) {
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
