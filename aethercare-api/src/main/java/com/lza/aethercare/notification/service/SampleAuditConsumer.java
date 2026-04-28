package com.lza.aethercare.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Sample Kafka consumer：驗證 audit topic bus 通暢。 */
@Component
@Slf4j
public class SampleAuditConsumer {

    @KafkaListener(topics = "${aethercare.kafka.topics.audit-created}", groupId = "aethercare-api-sample")
    public void onAuditCreated(String message) {
        log.info("[SAMPLE-CONSUMER] audit message received: {}", message);
    }
}
