package com.lza.aethercare.anomaly;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lza.aethercare.anomaly.dto.CreateActivityRequest;
import com.lza.aethercare.anomaly.entity.ElderActivityEvent;
import com.lza.aethercare.anomaly.enums.ActivityType;
import com.lza.aethercare.anomaly.repository.ElderActivityEventRepository;
import com.lza.aethercare.anomaly.service.ActivityIngestionService;
import com.lza.aethercare.common.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;

/** ActivityIngestionService 單元測試：正常 ingest 與 null occurredAt fallback。 */
@ExtendWith(MockitoExtension.class)
class ActivityIngestionServiceTest {

    @Mock
    ElderActivityEventRepository repo;
    @Mock
    Clock clock;

    private final OffsetDateTime now = OffsetDateTime.of(2026, 4, 27, 12, 0, 0, 0, ZoneOffset.UTC);

    private ActivityIngestionService newService() {
        lenient().when(clock.now()).thenReturn(now);
        given(repo.save(any(ElderActivityEvent.class))).willAnswer(inv -> inv.getArgument(0));
        return new ActivityIngestionService(repo, new ObjectMapper(), clock);
    }

    /** 驗證正常 ingest：欄位寫入正確，metadata 序列化為 JSON 字串。 */
    @Test
    void should_ingest_with_provided_occurredAt_and_serialize_metadata() {
        ActivityIngestionService service = newService();
        OffsetDateTime occurred = now.minusMinutes(10);
        CreateActivityRequest req = CreateActivityRequest.builder()
                .activityType(ActivityType.MOVE)
                .occurredAt(occurred)
                .durationSeconds(60)
                .metadata(Map.of("location", "kitchen"))
                .build();

        ElderActivityEvent saved = service.ingest(1001L, req);

        ArgumentCaptor<ElderActivityEvent> cap = ArgumentCaptor.forClass(ElderActivityEvent.class);
        then(repo).should().save(cap.capture());
        ElderActivityEvent persisted = cap.getValue();
        assertThat(persisted.getElderId()).isEqualTo(1001L);
        assertThat(persisted.getActivityType()).isEqualTo(ActivityType.MOVE);
        assertThat(persisted.getOccurredAt()).isEqualTo(occurred);
        assertThat(persisted.getDurationSeconds()).isEqualTo(60);
        assertThat(persisted.getMetadata()).contains("\"location\"").contains("\"kitchen\"");
        assertThat(persisted.getCreatedAt()).isEqualTo(now);
        assertThat(saved).isSameAs(persisted);
    }

    /** 驗證 null occurredAt 時用 clock.now() 補。 */
    @Test
    void should_use_clock_now_when_occurredAt_is_null() {
        ActivityIngestionService service = newService();
        CreateActivityRequest req = CreateActivityRequest.builder()
                .activityType(ActivityType.SLEEP)
                .build();

        service.ingest(2002L, req);

        ArgumentCaptor<ElderActivityEvent> cap = ArgumentCaptor.forClass(ElderActivityEvent.class);
        then(repo).should().save(cap.capture());
        assertThat(cap.getValue().getOccurredAt()).isEqualTo(now);
        assertThat(cap.getValue().getMetadata()).isNull();
    }
}
