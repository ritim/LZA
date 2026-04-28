package com.lza.aethercare.common.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Kafka topics 宣告：6 個 MVP topic，partitions=3、replication-factor=1。 */
@Configuration
public class KafkaTopicConfig {

    private static final int PARTITIONS = 3;
    private static final short REPLICATION_FACTOR = 1;

    @Value("${aethercare.kafka.topics.event-created}")
    private String eventCreatedTopic;

    @Value("${aethercare.kafka.topics.workflow-started}")
    private String workflowStartedTopic;

    @Value("${aethercare.kafka.topics.task-created}")
    private String taskCreatedTopic;

    @Value("${aethercare.kafka.topics.notification-sent}")
    private String notificationSentTopic;

    @Value("${aethercare.kafka.topics.action-received}")
    private String actionReceivedTopic;

    @Value("${aethercare.kafka.topics.audit-created}")
    private String auditCreatedTopic;

    @Bean
    public NewTopic eventCreatedTopic() {
        return TopicBuilder.name(eventCreatedTopic).partitions(PARTITIONS).replicas(REPLICATION_FACTOR).build();
    }

    @Bean
    public NewTopic workflowStartedTopic() {
        return TopicBuilder.name(workflowStartedTopic).partitions(PARTITIONS).replicas(REPLICATION_FACTOR).build();
    }

    @Bean
    public NewTopic taskCreatedTopic() {
        return TopicBuilder.name(taskCreatedTopic).partitions(PARTITIONS).replicas(REPLICATION_FACTOR).build();
    }

    @Bean
    public NewTopic notificationSentTopic() {
        return TopicBuilder.name(notificationSentTopic).partitions(PARTITIONS).replicas(REPLICATION_FACTOR).build();
    }

    @Bean
    public NewTopic actionReceivedTopic() {
        return TopicBuilder.name(actionReceivedTopic).partitions(PARTITIONS).replicas(REPLICATION_FACTOR).build();
    }

    @Bean
    public NewTopic auditCreatedTopic() {
        return TopicBuilder.name(auditCreatedTopic).partitions(PARTITIONS).replicas(REPLICATION_FACTOR).build();
    }
}
