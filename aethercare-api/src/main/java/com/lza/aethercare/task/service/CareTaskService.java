package com.lza.aethercare.task.service;

import com.lza.aethercare.audit.enums.CareAuditAction;
import com.lza.aethercare.audit.service.CareAuditService;
import com.lza.aethercare.common.event.PublishToKafka;
import com.lza.aethercare.common.time.Clock;
import com.lza.aethercare.task.entity.CareTask;
import com.lza.aethercare.task.enums.AssigneeType;
import com.lza.aethercare.task.enums.CareTaskStatus;
import com.lza.aethercare.task.event.CareTaskCreatedMessage;
import com.lza.aethercare.task.repository.CareTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** 任務 service：建立任務並透過 conditional update 處理狀態變更。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CareTaskService {

    private final CareTaskRepository repo;
    private final CareAuditService auditService;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    @Value("${aethercare.kafka.topics.task-created}")
    private String taskCreatedTopic;

    @Transactional
    public CareTask createTask(Long workflowId, Long eventId, Long assigneeId,
                                AssigneeType assigneeType, int level, OffsetDateTime deadlineAt) {
        OffsetDateTime now = clock.now();
        CareTask task = CareTask.builder()
                .workflowId(workflowId)
                .eventId(eventId)
                .assigneeId(assigneeId)
                .assigneeType(assigneeType)
                .level(level)
                .status(CareTaskStatus.PENDING)
                .deadlineAt(deadlineAt)
                .createdAt(now)
                .updatedAt(now)
                .build();
        CareTask saved = repo.save(task);

        auditService.log(workflowId, eventId, null, CareAuditAction.TASK_CREATED,
                "level=" + level + " assignee=" + assigneeId + " deadline=" + deadlineAt);

        publisher.publishEvent(new PublishToKafka(
                taskCreatedTopic,
                String.valueOf(workflowId),
                new CareTaskCreatedMessage(saved.getId(), workflowId, eventId,
                        assigneeId, assigneeType, level, deadlineAt)));
        return saved;
    }

    @Transactional
    public boolean completeIfActive(Long taskId) {
        return repo.completeIfActive(taskId, clock.now()) == 1;
    }

    @Transactional
    public boolean acknowledgeIfPending(Long taskId) {
        return repo.acknowledgeIfPending(taskId, clock.now()) == 1;
    }

    @Transactional
    public boolean markTimeoutIfPending(Long taskId) {
        return repo.markTimeoutIfPending(taskId, clock.now()) == 1;
    }

    @Transactional(readOnly = true)
    public Optional<CareTask> findById(Long id) {
        return repo.findById(id);
    }

    @Transactional(readOnly = true)
    public List<CareTask> findByWorkflow(Long workflowId) {
        return repo.findByWorkflowIdOrderByLevelAscIdAsc(workflowId);
    }

    @Transactional(readOnly = true)
    public List<CareTask> findExpiredPending(OffsetDateTime now) {
        return repo.findExpiredPendingTasks(now);
    }
}
