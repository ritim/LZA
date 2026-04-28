package com.lza.aethercare.notification;

import com.lza.aethercare.audit.enums.CareAuditAction;
import com.lza.aethercare.audit.service.CareAuditService;
import com.lza.aethercare.common.error.BusinessException;
import com.lza.aethercare.common.error.ErrorCode;
import com.lza.aethercare.common.time.Clock;
import com.lza.aethercare.notification.service.NotificationService;
import com.lza.aethercare.notification.service.StubNotificationGateway;
import com.lza.aethercare.task.entity.CareTask;
import com.lza.aethercare.task.enums.AssigneeType;
import com.lza.aethercare.task.enums.CareTaskStatus;
import com.lza.aethercare.userprofile.entity.CareContactEscalation;
import com.lza.aethercare.userprofile.enums.NotificationChannel;
import com.lza.aethercare.userprofile.service.EscalationContactService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;

/**
 * NotificationService 單元測試：驗證找不到聯絡人時拋出例外，以及聯絡人存在時正確通知與 audit。
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    StubNotificationGateway gateway;
    @Mock
    CareAuditService auditService;
    @Mock
    EscalationContactService contactService;
    @Mock
    ApplicationEventPublisher publisher;
    @Mock
    Clock clock;

    @InjectMocks
    NotificationService service;

    private final OffsetDateTime now = OffsetDateTime.of(2026, 4, 27, 12, 0, 0, 0, ZoneOffset.ofHours(8));
    private final Long elderId = 1001L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "notificationSentTopic", "care.notification.sent");
        lenient().when(clock.now()).thenReturn(now);
    }

    private CareTask buildTask(int level) {
        return CareTask.builder()
                .id(10L)
                .workflowId(100L)
                .eventId(200L)
                .assigneeId(2001L)
                .assigneeType(AssigneeType.FAMILY)
                .level(level)
                .status(CareTaskStatus.PENDING)
                .deadlineAt(now.plusMinutes(30))
                .createdAt(now.minusMinutes(5))
                .updatedAt(now)
                .version(0)
                .build();
    }

    /** 驗證找不到聯絡人時，拋出 ESCALATION_NOT_AVAILABLE 例外。 */
    @Test
    void should_throw_ESCALATION_NOT_AVAILABLE_when_no_contact() {
        given(contactService.findContact(elderId, 1)).willReturn(Optional.empty());
        CareTask task = buildTask(1);

        assertThatThrownBy(() -> service.notify(task, elderId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.ESCALATION_NOT_AVAILABLE);
    }

    /** 驗證聯絡人存在時，gateway 被呼叫，且 NOTIFICATION_SENT audit 被記錄。 */
    @Test
    void should_invoke_gateway_and_audit_when_contact_present() {
        CareTask task = buildTask(1);
        CareContactEscalation contact = CareContactEscalation.builder()
                .id(1L)
                .elderId(elderId)
                .contactUserId(2001L)
                .level(1)
                .channel(NotificationChannel.LINE)
                .slaSeconds(30)
                .build();

        given(contactService.findContact(elderId, 1)).willReturn(Optional.of(contact));
        given(gateway.send(any(), any(), anyString(), anyString())).willReturn(true);

        service.notify(task, elderId);

        then(gateway).should().send(
                eq(NotificationChannel.LINE),
                eq(task.getAssigneeId()),
                anyString(),
                anyString());
        then(auditService).should().log(
                eq(task.getWorkflowId()),
                eq(task.getEventId()),
                isNull(),
                eq(CareAuditAction.NOTIFICATION_SENT),
                anyString());
    }
}
