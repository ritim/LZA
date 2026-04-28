package com.lza.aethercare.anomaly.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lza.aethercare.anomaly.dto.CreateActivityRequest;
import com.lza.aethercare.anomaly.entity.ElderActivityEvent;
import com.lza.aethercare.anomaly.repository.ElderActivityEventRepository;
import com.lza.aethercare.common.error.BusinessException;
import com.lza.aethercare.common.error.ErrorCode;
import com.lza.aethercare.common.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 活動事件 ingestion service：將上報活動寫入 elder_activity_event。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityIngestionService {

    private final ElderActivityEventRepository repo;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public ElderActivityEvent ingest(Long elderId, CreateActivityRequest req) {
        String metadataJson;
        try {
            metadataJson = req.getMetadata() == null ? null : objectMapper.writeValueAsString(req.getMetadata());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "metadata 序列化失敗：" + e.getMessage());
        }
        ElderActivityEvent event = ElderActivityEvent.builder()
                .elderId(elderId)
                .activityType(req.getActivityType())
                .occurredAt(req.getOccurredAt() != null ? req.getOccurredAt() : clock.now())
                .durationSeconds(req.getDurationSeconds())
                .metadata(metadataJson)
                .createdAt(clock.now())
                .build();
        ElderActivityEvent saved = repo.save(event);
        log.debug("ActivityIngestion: elderId={} type={} id={}", elderId, req.getActivityType(), saved.getId());
        return saved;
    }
}
