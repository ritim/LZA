package com.lza.aethercare.action.service;

import com.lza.aethercare.action.dto.CreateCareActionRequest;
import com.lza.aethercare.action.entity.CareAction;
import com.lza.aethercare.action.event.CareActionReceivedMessage;
import com.lza.aethercare.action.repository.CareActionRepository;
import com.lza.aethercare.audit.enums.CareAuditAction;
import com.lza.aethercare.audit.service.CareAuditService;
import com.lza.aethercare.common.error.BusinessException;
import com.lza.aethercare.common.error.ErrorCode;
import com.lza.aethercare.common.event.PublishToKafka;
import com.lza.aethercare.common.time.Clock;
import com.lza.aethercare.task.entity.CareTask;
import com.lza.aethercare.task.service.CareTaskService;
import com.lza.aethercare.workflow.service.CareWorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Action service：依 actionType 路由 task / workflow 狀態變更。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CareActionService {

    private final CareActionRepository actionRepo;
    private final CareTaskService taskService;
    private final CareWorkflowService workflowService;
    private final CareAuditService auditService;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    @Value("${aethercare.kafka.topics.action-received}")
    private String actionReceivedTopic;

    @Transactional
    public CareAction handle(Long taskId, CreateCareActionRequest req) {
        CareTask task = taskService.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "task=" + taskId));

        switch (req.getActionType()) {
            case CONFIRM_SAFE -> {
                if (!taskService.completeIfActive(taskId)) {
                    throw new BusinessException(ErrorCode.TASK_ALREADY_FINALIZED, "task=" + taskId + " 已被處理");
                }
                CareAction action = persistAction(task, req);
                auditService.log(task.getWorkflowId(), task.getEventId(), req.getActorId(),
                        CareAuditAction.TASK_COMPLETED, "CONFIRM_SAFE: " + safeNote(req));
                workflowService.resolve(task.getWorkflowId(), req.getActorId());
                publishKafka(action, req);
                return action;
            }
            case NEED_HELP -> {
                if (!taskService.completeIfActive(taskId)) {
                    throw new BusinessException(ErrorCode.TASK_ALREADY_FINALIZED, "task=" + taskId + " 已被處理");
                }
                CareAction action = persistAction(task, req);
                auditService.log(task.getWorkflowId(), task.getEventId(), req.getActorId(),
                        CareAuditAction.TASK_COMPLETED, "NEED_HELP: " + safeNote(req));
                auditService.log(task.getWorkflowId(), task.getEventId(), req.getActorId(),
                        CareAuditAction.ESCALATION_TRIGGERED, "user requested escalation");
                workflowService.escalate(task, req.getActorId());
                publishKafka(action, req);
                return action;
            }
            case ACKNOWLEDGE -> {
                if (!taskService.acknowledgeIfPending(taskId)) {
                    throw new BusinessException(ErrorCode.TASK_ALREADY_FINALIZED, "task=" + taskId + " 已被處理");
                }
                CareAction action = persistAction(task, req);
                auditService.log(task.getWorkflowId(), task.getEventId(), req.getActorId(),
                        CareAuditAction.TASK_ACKNOWLEDGED, "ACKNOWLEDGE: " + safeNote(req));
                publishKafka(action, req);
                return action;
            }
            default -> throw new BusinessException(ErrorCode.INVALID_REQUEST, "未知 actionType");
        }
    }

    private String safeNote(CreateCareActionRequest req) {
        return req.getNote() == null ? "" : req.getNote();
    }

    private CareAction persistAction(CareTask task, CreateCareActionRequest req) {
        CareAction action = CareAction.builder()
                .workflowId(task.getWorkflowId())
                .taskId(task.getId())
                .actorId(req.getActorId())
                .actionType(req.getActionType())
                .note(req.getNote())
                .createdAt(clock.now())
                .build();
        return actionRepo.save(action);
    }

    private void publishKafka(CareAction action, CreateCareActionRequest req) {
        publisher.publishEvent(new PublishToKafka(
                actionReceivedTopic,
                String.valueOf(action.getWorkflowId()),
                new CareActionReceivedMessage(action.getId(), action.getWorkflowId(),
                        action.getTaskId(), req.getActorId(), req.getActionType(), action.getCreatedAt())));
    }

    @Transactional(readOnly = true)
    public List<CareAction> findByWorkflow(Long workflowId) {
        return actionRepo.findByWorkflowIdOrderByCreatedAtAsc(workflowId);
    }
}
