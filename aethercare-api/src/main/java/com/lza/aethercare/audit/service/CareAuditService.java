package com.lza.aethercare.audit.service;

import com.lza.aethercare.audit.entity.CareAuditLog;
import com.lza.aethercare.audit.enums.CareAuditAction;
import com.lza.aethercare.audit.event.CareAuditCreatedMessage;
import com.lza.aethercare.audit.repository.CareAuditLogRepository;
import com.lza.aethercare.common.event.PublishToKafka;
import com.lza.aethercare.common.time.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Audit log service：以獨立 transaction 寫入並發送 Kafka，失敗不 rethrow。 */
@Service
@Slf4j
public class CareAuditService {

    private final CareAuditLogRepository repo;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final String auditCreatedTopic;

    private Counter writeFailureCounter;

    public CareAuditService(CareAuditLogRepository repo,
                            ApplicationEventPublisher publisher,
                            Clock clock,
                            MeterRegistry meterRegistry,
                            @Value("${aethercare.kafka.topics.audit-created}") String auditCreatedTopic) {
        this.repo = repo;
        this.publisher = publisher;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.auditCreatedTopic = auditCreatedTopic;
    }

    @PostConstruct
    public void init() {
        writeFailureCounter = Counter.builder("aethercare.audit.write.failures").register(meterRegistry);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<CareAuditLog> log(Long workflowId, Long eventId, Long actorId,
                                      CareAuditAction action, String message) {
        try {
            CareAuditLog logEntry = CareAuditLog.builder()
                    .workflowId(workflowId)
                    .eventId(eventId)
                    .actorId(actorId)
                    .action(action)
                    .message(message)
                    .createdAt(clock.now())
                    .build();
            CareAuditLog saved = repo.save(logEntry);
            publisher.publishEvent(new PublishToKafka(
                    auditCreatedTopic,
                    String.valueOf(workflowId),
                    new CareAuditCreatedMessage(
                            saved.getId(), workflowId, eventId, action, message, saved.getCreatedAt())));
            return Optional.of(saved);
        } catch (Exception e) {
            writeFailureCounter.increment();
            log.warn("audit log 寫入失敗 workflowId={} action={} reason={}", workflowId, action, e.getMessage());
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true)
    public List<CareAuditLog> timeline(Long workflowId) {
        return repo.findByWorkflowIdOrderByCreatedAtAsc(workflowId);
    }
}
