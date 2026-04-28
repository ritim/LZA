package com.lza.aethercare.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Jackson 設定：註冊 JavaTime 模組，禁用 timestamp 寫法。 */
@Configuration
public class JacksonConfig {

    /** 對所有 ObjectMapper 套用：JavaTimeModule + 不寫日期 timestamp。 */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer objectMapperCustomizer() {
        return builder -> builder
                .modulesToInstall(new JavaTimeModule())
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /** 提供獨立 ObjectMapper bean 給 Kafka publish 用。 */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
