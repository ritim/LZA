package com.lza.aethercare.action.service;

import com.lza.aethercare.action.dto.CreateCareActionRequest;
import com.lza.aethercare.action.entity.CareAction;
import com.lza.aethercare.action.enums.CareActionType;
import com.lza.aethercare.action.event.CareActionReceivedMessage;
import com.lza.aethercare.action.repository.CareActionRepository;
import com.lza.aethercare.audit.enums.CareAuditAction;
import com.lza.aethercare.audit.service.CareAuditService;
import com.lza.aethercare.common.error.BusinessException;
import com.lza.aethercare.common.error.ErrorCode;
import com.lza.aethercare.common.event.PublishToKafka;
import com.lza.aethercare.common.time.Clock;
import com.lza.aethercare.tenant.context.TenantContext;
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

/**
 * Action service：依 actionType 路由 task / workflow 狀態變更。
 *
 * <p>Spec §6.7 要求 8 種 action type，本 service 收斂為 3 種內部語意（CONFIRM_SAFE / NEED_HELP /
 * ACKNOWLEDGE），但 audit message + kafka payload 保留 caller 送出的原始 type，方便事後追溯。
 */
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
    public CareAction handle(Long taskId, Long actorId, CreateCareActionRequest req) {
        CareTask task = taskService.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "task=" + taskId));

        CareActionType incoming = req.getActionType();
        CareActionType internal = mapToInternal(incoming);

        switch (internal) {
            case CONFIRM_SAFE -> {
                if (!taskService.completeIfActive(taskId)) {
                    throw new BusinessException(ErrorCode.TASK_ALREADY_FINALIZED, "task=" + taskId + " 已被處理");
                }
                CareAction action = persistAction(task, actorId, incoming, req.getNote());
                auditService.log(task.getWorkflowId(), task.getEventId(), actorId,
                        CareAuditAction.TASK_COMPLETED, incoming.name() + ": " + safeNote(req));
                workflowService.resolve(task.getWorkflowId(), actorId);
                publishKafka(action, actorId, req);
                return action;
            }
            case NEED_HELP -> {
                if (!taskService.completeIfActive(taskId)) {
                    throw new BusinessException(ErrorCode.TASK_ALREADY_FINALIZED, "task=" + taskId + " 已被處理");
                }
                CareAction action = persistAction(task, actorId, incoming, req.getNote());
                auditService.log(task.getWorkflowId(), task.getEventId(), actorId,
                        CareAuditAction.TASK_COMPLETED, incoming.name() + ": " + safeNote(req));
                auditService.log(task.getWorkflowId(), task.getEventId(), actorId,
                        CareAuditAction.ESCALATION_TRIGGERED,
                        "user requested escalation via " + incoming.name());
                workflowService.escalate(task, actorId);
                publishKafka(action, actorId, req);
                return action;
            }
            case ACKNOWLEDGE -> {
                if (!taskService.acknowledgeIfPending(taskId)) {
                    throw new BusinessException(ErrorCode.TASK_ALREADY_FINALIZED, "task=" + taskId + " 已被處理");
                }
                CareAction action = persistAction(task, actorId, incoming, req.getNote());
                auditService.log(task.getWorkflowId(), task.getEventId(), actorId,
                        CareAuditAction.TASK_ACKNOWLEDGED, incoming.name() + ": " + safeNote(req));
                publishKafka(action, actorId, req);
                return action;
            }
            default -> throw new BusinessException(ErrorCode.INVALID_REQUEST, "未知 actionType");
        }
    }

    /** Spec §6.7：對外接受 8 種 action，收斂為 3 種內部語意。 */
    private static CareActionType mapToInternal(CareActionType type) {
        return switch (type) {
            case CONFIRM_SAFE -> CareActionType.CONFIRM_SAFE;
            case NEED_HELP, CALL_EMERGENCY, ESCALATE, CALL_SECOND_CONTACT,
                    REQUEST_HELP -> CareActionType.NEED_HELP;
            // Spec § Gap D：MARK_UNABLE_TO_CONFIRM 屬於「保留 workflow open」家族，
            // 不可直接升級或結案；改 caregiver 透過 ESCALATE / REQUEST_HELP 主動推進。
            case ACKNOWLEDGE, CALL_ELDER, ADD_NOTE, CALL_NO_ANSWER,
                    MARK_UNABLE_TO_CONFIRM -> CareActionType.ACKNOWLEDGE;
        };
    }

    private String safeNote(CreateCareActionRequest req) {
        return req.getNote() == null ? "" : req.getNote();
    }

    private CareAction persistAction(CareTask task, Long actorId, CareActionType actionType, String note) {
        CareAction action = CareAction.builder()
                .tenantId(TenantContext.getOrDefault())
                .workflowId(task.getWorkflowId())
                .taskId(task.getId())
                .actorId(actorId)
                .actionType(actionType)
                .note(note)
                .createdAt(clock.now())
                .build();
        return actionRepo.save(action);
    }

    private void publishKafka(CareAction action, Long actorId, CreateCareActionRequest req) {
        publisher.publishEvent(new PublishToKafka(
                actionReceivedTopic,
                String.valueOf(action.getWorkflowId()),
                new CareActionReceivedMessage(action.getId(), action.getWorkflowId(),
                        action.getTaskId(), actorId, req.getActionType(), action.getCreatedAt())));
    }

    @Transactional(readOnly = true)
    public List<CareAction> findByWorkflow(Long workflowId) {
        return actionRepo.findByWorkflowIdOrderByCreatedAtAsc(workflowId);
    }
}
