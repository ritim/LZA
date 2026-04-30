package com.lza.aethercare.ai.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lza.aethercare.ai.enums.KnowledgeEventType;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * 啟動時將 5 個靜態 care-knowledge JSON 載入記憶體 Map，提供 O(1) lookup。
 *
 * <p>JSON 內容請見 {@code src/main/resources/care-knowledge/*.json}；
 * 若任一檔案缺失或解析失敗，啟動會直接 fail-fast。
 */
@Component
@Slf4j
public class CareKnowledgeBase {

    private final Map<KnowledgeEventType, CareKnowledge> knowledge = new EnumMap<>(KnowledgeEventType.class);
    private final ObjectMapper objectMapper;

    public CareKnowledgeBase(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void loadAll() throws IOException {
        load(KnowledgeEventType.FALL, "care-knowledge/fall.json");
        load(KnowledgeEventType.NO_ACTIVITY, "care-knowledge/no_activity.json");
        load(KnowledgeEventType.STROKE, "care-knowledge/stroke.json");
        load(KnowledgeEventType.WANDERING, "care-knowledge/wandering.json");
        load(KnowledgeEventType.BREATHING_ISSUE, "care-knowledge/breathing_issue.json");
        log.info("CareKnowledgeBase loaded {} entries", knowledge.size());
    }

    private void load(KnowledgeEventType type, String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            knowledge.put(type, objectMapper.readValue(in, CareKnowledge.class));
        }
    }

    /** 依 type 找對應 knowledge；找不到（例如 OTHER）回 empty。 */
    public Optional<CareKnowledge> lookup(KnowledgeEventType type) {
        return Optional.ofNullable(knowledge.get(type));
    }
}
