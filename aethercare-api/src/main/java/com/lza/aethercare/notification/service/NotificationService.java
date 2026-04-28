package com.lza.aethercare.notification.service;

import com.lza.aethercare.audit.enums.CareAuditAction;
import com.lza.aethercare.audit.service.CareAuditService;
import com.lza.aethercare.common.error.BusinessException;
import com.lza.aethercare.common.error.ErrorCode;
import com.lza.aethercare.common.event.PublishToKafka;
import com.lza.aethercare.common.time.Clock;
import com.lza.aethercare.notification.event.CareNotificationSentMessage;
import com.lza.aethercare.task.entity.CareTask;
import com.lza.aethercare.userprofile.entity.CareContactEscalation;
import com.lza.aethercare.userprofile.service.EscalationContactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 通知 service：呼叫 stub gateway、寫 audit、發 Kafka。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final StubNotificationGateway gateway;
    private final CareAuditService auditService;
    private final EscalationContactService contactService;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    @Value("${aethercare.kafka.topics.notification-sent}")
    private String notificationSentTopic;

    @Transactional
    public void notify(CareTask task, Long elderId) {
        CareContactEscalation contact = contactService.findContact(elderId, task.getLevel())
                .orElseThrow(() -> new BusinessException(ErrorCode.ESCALATION_NOT_AVAILABLE,
                        "找不到 level=" + task.getLevel() + " 聯絡人"));
        String subject = "AetherCare Alert (level " + task.getLevel() + ")";
        String body = "task=" + task.getId() + " workflow=" + task.getWorkflowId();
        gateway.send(contact.getChannel(), task.getAssigneeId(), subject, body);

        auditService.log(task.getWorkflowId(), task.getEventId(), null,
                CareAuditAction.NOTIFICATION_SENT,
                "channel=" + contact.getChannel() + " assignee=" + task.getAssigneeId());

        publisher.publishEvent(new PublishToKafka(
                notificationSentTopic,
                String.valueOf(task.getWorkflowId()),
                new CareNotificationSentMessage(
                        task.getId(), task.getWorkflowId(),
                        contact.getChannel(), task.getAssigneeId(),
                        clock.now())));
    }
}
