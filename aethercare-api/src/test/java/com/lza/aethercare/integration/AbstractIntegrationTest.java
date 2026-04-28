package com.lza.aethercare.integration;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 共用 Testcontainers base：啟動 Postgres / Redis / Kafka 並動態注入連線屬性。
 * <p>
 * 子類別只需 extends 即可獲得完整 Spring context（含 Liquibase 套用 schema + seed）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("aethercare")
            .withUsername("aethercare")
            .withPassword("aethercare")
            .withReuse(true);

    @Container
    static final RedisContainer REDIS = new RedisContainer(
            DockerImageName.parse("redis:7-alpine"))
            .withReuse(true);

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.7.1"))
            .withReuse(true);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry reg) {
        reg.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        reg.add("spring.datasource.username", POSTGRES::getUsername);
        reg.add("spring.datasource.password", POSTGRES::getPassword);
        reg.add("spring.data.redis.host", REDIS::getHost);
        reg.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        reg.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        reg.add("aethercare.scheduler.timeout-scan-fixed-delay", () -> "200");
    }
}
