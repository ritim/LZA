package com.lza.aethercare.common.event;

/** 本機 Spring application event：請求 listener 在 commit 後發送 Kafka 訊息。 */
public record PublishToKafka(String topic, String key, Object payload) {
}
