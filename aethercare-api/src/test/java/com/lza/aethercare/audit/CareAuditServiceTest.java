package com.lza.aethercare.audit;

import com.lza.aethercare.audit.entity.CareAuditLog;
import com.lza.aethercare.audit.enums.CareAuditAction;
import com.lza.aethercare.audit.repository.CareAuditLogRepository;
import com.lza.aethercare.audit.service.CareAuditService;
import com.lza.aethercare.common.event.PublishToKafka;
import com.lza.aethercare.common.time.Clock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * CareAuditService 單元測試：驗證 log 成功時發送 Kafka 事件，失敗時遞增計數器且不 rethrow。
 */
@ExtendWith(MockitoExtension.class)
class CareAuditServiceTest {

    @Mock
    CareAuditLogRepository repo;
    @Mock
    ApplicationEventPublisher publisher;
    @Mock
    Clock clock;

    SimpleMeterRegistry meterRegistry;
    CareAuditService service;

    private final OffsetDateTime now = OffsetDateTime.of(2026, 4, 27, 12, 0, 0, 0, ZoneOffset.ofHours(8));

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new CareAuditService(repo, publisher, clock, meterRegistry, "care.audit.created");
        // 手動觸發 @PostConstruct
        service.init();
        given(clock.now()).willReturn(now);
    }

    /** 驗證 save 成功時，回傳 Optional 包含 log，且 publishEvent 被呼叫。 */
    @Test
    void should_record_audit_and_publish_when_save_succeeds() {
        given(repo.save(any())).willAnswer(inv -> {
            CareAuditLog l = inv.getArgument(0);
            l.setId(99L);
            return l;
        });

        Optional<CareAuditLog> result = service.log(1L, 2L, 3L,
                CareAuditAction.EVENT_CREATED, "測試訊息");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(99L);
        then(publisher).should().publishEvent(any(PublishToKafka.class));
    }

    /** 驗證 save 拋出例外時，回傳 empty，計數器遞增，且不 publish。 */
    @Test
    void should_increment_counter_and_return_empty_when_save_fails() {
        given(repo.save(any())).willThrow(new RuntimeException("DB down"));

        Optional<CareAuditLog> result = service.log(1L, 2L, 3L,
                CareAuditAction.EVENT_CREATED, "測試訊息");

        assertThat(result).isEmpty();
        then(publisher).should(never()).publishEvent(any(PublishToKafka.class));
        double count = meterRegistry.counter("aethercare.audit.write.failures").count();
        assertThat(count).isEqualTo(1.0);
    }
}
