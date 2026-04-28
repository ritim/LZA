package com.lza.aethercare.workflow.service;

import com.lza.aethercare.audit.enums.CareAuditAction;
import com.lza.aethercare.audit.service.CareAuditService;
import com.lza.aethercare.common.error.BusinessException;
import com.lza.aethercare.common.error.ErrorCode;
import com.lza.aethercare.common.event.PublishToKafka;
import com.lza.aethercare.common.time.Clock;
import com.lza.aethercare.decision.service.DecisionService;
import com.lza.aethercare.event.entity.CareEvent;
import com.lza.aethercare.notification.service.NotificationService;
import com.lza.aethercare.task.entity.CareTask;
import com.lza.aethercare.task.enums.AssigneeType;
import com.lza.aethercare.task.service.CareTaskService;
import com.lza.aethercare.userprofile.entity.CareContactEscalation;
import com.lza.aethercare.userprofile.service.EscalationContactService;
import com.lza.aethercare.workflow.entity.CareWorkflowInstance;
import com.lza.aethercare.workflow.enums.CareWorkflowStatus;
import com.lza.aethercare.workflow.enums.CareWorkflowType;
import com.lza.aethercare.workflow.event.CareWorkflowStartedMessage;
import com.lza.aethercare.workflow.repository.CareWorkflowInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** Workflow service：start / resolve / escalate / markUnresolved，全程透過 conditional update。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CareWorkflowService {

    private final CareWorkflowInstanceRepository workflowRepo;
    private final CareTaskService taskService;
    private final NotificationService notificationService;
    private final EscalationContactService contactService;
    private final CareAuditService auditService;
    private final WorkflowLockService lockService;
    private final DecisionService decisionService;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    @Value("${aethercare.kafka.topics.workflow-started}")
    private String workflowStartedTopic;

    @Transactional
    public CareWorkflowInstance start(CareEvent event) {
        OffsetDateTime now = clock.now();
        CareWorkflowType type = decisionService.resolveWorkflowType(event.getEventType());

        CareWorkflowInstance wf = CareWorkflowInstance.builder()
                .eventId(event.getId())
                .elderId(event.getElderId())
                .workflowType(type)
                .riskLevel(event.getRiskLevel())
                .status(CareWorkflowStatus.ACTIVE)
                .currentLevel(1)
                .startedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .version(0)
                .build();
        wf = workflowRepo.save(wf);

        // 第一筆 audit：事件已被接收（綁 workflow id 以便 timeline 查詢）
        auditService.log(wf.getId(), event.getId(), null, CareAuditAction.EVENT_CREATED,
                "事件已接收：type=" + event.getEventType() + " risk=" + event.getRiskLevel());

        auditService.log(wf.getId(), event.getId(), null, CareAuditAction.WORKFLOW_STARTED,
                "workflowType=" + type + " riskLevel=" + event.getRiskLevel());

        // level 1 task
        CareContactEscalation contact = contactService.findContact(event.getElderId(), 1)
                .orElseThrow(() -> new BusinessException(ErrorCode.ESCALATION_NOT_AVAILABLE,
                        "elder=" + event.getElderId() + " 沒有 level=1 聯絡人"));
        OffsetDateTime deadline = now.plusSeconds(contact.getSlaSeconds());
        CareTask task = taskService.createTask(wf.getId(), event.getId(),
                contact.getContactUserId(), AssigneeType.FAMILY, 1, deadline);

        // workflow 進入 WAITING_RESPONSE
        int updated = workflowRepo.advanceLevel(wf.getId(), 1,
                CareWorkflowStatus.WAITING_RESPONSE.name(),
                List.of(CareWorkflowStatus.ACTIVE.name()), clock.now());
        if (updated != 1) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "workflow 無法進入 WAITING_RESPONSE");
        }

        notificationService.notify(task, event.getElderId());
        lockService.cacheElderLatestStatus(event.getElderId(), CareWorkflowStatus.WAITING_RESPONSE);

        publisher.publishEvent(new PublishToKafka(
                workflowStartedTopic,
                String.valueOf(wf.getId()),
                new CareWorkflowStartedMessage(wf.getId(), event.getId(), event.getElderId(),
                        type, event.getRiskLevel(), now)));

        return workflowRepo.findById(wf.getId()).orElseThrow();
    }

    @Transactional
    public void resolve(Long workflowId, Long actorId) {
        if (!lockService.acquire(workflowId)) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "workflow 已在處理中");
        }
        try {
            int updated = workflowRepo.markTerminalIfIn(workflowId,
                    CareWorkflowStatus.RESOLVED.name(),
                    List.of(CareWorkflowStatus.WAITING_RESPONSE.name(),
                            CareWorkflowStatus.ACKNOWLEDGED.name(),
                            CareWorkflowStatus.ESCALATED.name(),
                            CareWorkflowStatus.ACTIVE.name()),
                    clock.now());
            if (updated == 0) {
                auditService.log(workflowId, null, actorId, CareAuditAction.STATE_CONFLICT_SKIPPED,
                        "RESOLVED skipped: workflow 狀態已變");
                return;
            }
            CareWorkflowInstance wf = workflowRepo.findById(workflowId).orElseThrow();
            auditService.log(workflowId, wf.getEventId(), actorId,
                    CareAuditAction.WORKFLOW_RESOLVED, "已 resolved");
            lockService.cacheElderLatestStatus(wf.getElderId(), CareWorkflowStatus.RESOLVED);
        } finally {
            lockService.release(workflowId);
        }
    }

    @Transactional
    public void markUnresolved(Long workflowId, Long actorId, String reason) {
        int updated = workflowRepo.markTerminalIfIn(workflowId,
                CareWorkflowStatus.UNRESOLVED.name(),
                List.of(CareWorkflowStatus.WAITING_RESPONSE.name(),
                        CareWorkflowStatus.ACKNOWLEDGED.name(),
                        CareWorkflowStatus.ESCALATED.name(),
                        CareWorkflowStatus.ACTIVE.name()),
                clock.now());
        if (updated == 0) {
            auditService.log(workflowId, null, actorId, CareAuditAction.STATE_CONFLICT_SKIPPED,
                    "UNRESOLVED skipped: workflow 狀態已變");
            return;
        }
        CareWorkflowInstance wf = workflowRepo.findById(workflowId).orElseThrow();
        auditService.log(workflowId, wf.getEventId(), actorId,
                CareAuditAction.WORKFLOW_UNRESOLVED, reason);
        lockService.cacheElderLatestStatus(wf.getElderId(), CareWorkflowStatus.UNRESOLVED);
    }

    @Transactional
    public void escalate(CareTask timedOutTask, Long actorId) {
        Long workflowId = timedOutTask.getWorkflowId();
        if (!lockService.acquire(workflowId)) {
            log.warn("workflow={} 已在處理中，跳過 escalation", workflowId);
            return;
        }
        try {
            CareWorkflowInstance wf = workflowRepo.findById(workflowId).orElseThrow();
            int nextLevel = timedOutTask.getLevel() + 1;
            Optional<CareContactEscalation> nextContact = contactService.findContact(wf.getElderId(), nextLevel);
            if (nextContact.isEmpty()) {
                markUnresolved(workflowId, actorId, "已無 level=" + nextLevel + " 聯絡人");
                return;
            }

            int updated = workflowRepo.advanceLevel(workflowId, nextLevel,
                    CareWorkflowStatus.ESCALATED.name(),
                    List.of(CareWorkflowStatus.WAITING_RESPONSE.name(),
                            CareWorkflowStatus.ACKNOWLEDGED.name()),
                    clock.now());
            if (updated == 0) {
                auditService.log(workflowId, wf.getEventId(), actorId,
                        CareAuditAction.STATE_CONFLICT_SKIPPED,
                        "escalation 取消：狀態已變");
                return;
            }

            OffsetDateTime now = clock.now();
            OffsetDateTime deadline = now.plusSeconds(nextContact.get().getSlaSeconds());
            CareTask nextTask = taskService.createTask(workflowId, wf.getEventId(),
                    nextContact.get().getContactUserId(), AssigneeType.FAMILY, nextLevel, deadline);

            // 進回 WAITING_RESPONSE
            workflowRepo.advanceLevel(workflowId, nextLevel,
                    CareWorkflowStatus.WAITING_RESPONSE.name(),
                    List.of(CareWorkflowStatus.ESCALATED.name()),
                    clock.now());

            auditService.log(workflowId, wf.getEventId(), actorId,
                    CareAuditAction.TASK_ESCALATED,
                    "升級到 level=" + nextLevel + " 聯絡人=" + nextContact.get().getContactUserId());

            notificationService.notify(nextTask, wf.getElderId());
            lockService.cacheElderLatestStatus(wf.getElderId(), CareWorkflowStatus.WAITING_RESPONSE);
        } finally {
            lockService.release(workflowId);
        }
    }

    @Transactional(readOnly = true)
    public CareWorkflowInstance findById(Long id) {
        return workflowRepo.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "workflow=" + id));
    }
}
