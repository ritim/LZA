package com.lza.aethercare.notification;

import com.lza.aethercare.audit.enums.CareAuditAction;
import com.lza.aethercare.audit.service.CareAuditService;
import com.lza.aethercare.common.error.BusinessException;
import com.lza.aethercare.common.error.ErrorCode;
import com.lza.aethercare.common.time.Clock;
import com.lza.aethercare.event.repository.CareEventRepository;
import com.lza.aethercare.notification.line.LineMessagingClient;
import com.lza.aethercare.notification.line.repository.CaregiverLineBindingRepository;
import com.lza.aethercare.notification.service.NotificationService;
import com.lza.aethercare.notification.service.StubNotificationGateway;
import com.lza.aethercare.task.entity.CareTask;
import com.lza.aethercare.task.enums.AssigneeType;
import com.lza.aethercare.task.enums.CareTaskStatus;
import com.lza.aethercare.userprofile.entity.CareContactEscalation;
import com.lza.aethercare.userprofile.enums.NotificationChannel;
import com.lza.aethercare.event.entity.CareEvent;
import com.lza.aethercare.event.enums.CareEventType;
import com.lza.aethercare.event.enums.RiskLevel;
import com.lza.aethercare.notification.line.entity.CaregiverLineBinding;
import com.lza.aethercare.userprofile.entity.ElderProfile;
import com.lza.aethercare.userprofile.repository.ElderProfileRepository;
import com.lza.aethercare.userprofile.service.EscalationContactService;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
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
    @Mock
    ObjectProvider<LineMessagingClient> lineClientProvider;
    @Mock
    CaregiverLineBindingRepository lineBindingRepo;
    @Mock
    CareEventRepository careEventRepo;
    @Mock
    ElderProfileRepository elderProfileRepo;

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

    /** 長者有 phone → LINE Flex bubble 應同時帶「打給長輩」(tel:) 與「我收到了」(postback) 按鈕。 */
    @Test
    @SuppressWarnings("unchecked")
    void flex_bubble_should_include_call_elder_button_when_elder_has_phone() {
        CareTask task = buildTask(1);
        CareContactEscalation contact = CareContactEscalation.builder()
                .id(1L).elderId(elderId).contactUserId(2001L).level(1)
                .channel(NotificationChannel.LINE).slaSeconds(30).build();
        given(contactService.findContact(elderId, 1)).willReturn(Optional.of(contact));
        given(gateway.send(any(), any(), anyString(), anyString())).willReturn(true);

        // LINE push 鏈：client / binding / event / elder 都存在
        LineMessagingClient lineClient = org.mockito.Mockito.mock(LineMessagingClient.class);
        given(lineClientProvider.getIfAvailable()).willReturn(lineClient);
        given(lineBindingRepo.findByCaregiverId(2001L)).willReturn(Optional.of(
                CaregiverLineBinding.builder()
                        .id(7L).tenantId(1L).caregiverId(2001L).lineUserId("Uxxx").build()));
        given(careEventRepo.findById(200L)).willReturn(Optional.of(
                CareEvent.builder().id(200L).elderId(elderId)
                        .eventType(CareEventType.MISSED_CHECK_IN).riskLevel(RiskLevel.MEDIUM)
                        .occurredAt(now).build()));
        given(elderProfileRepo.findById(elderId)).willReturn(Optional.of(
                ElderProfile.builder().id(elderId).tenantId(1L).name("王美玉")
                        .age(82).mobility("LOW").phone("+886912345678").build()));

        service.notify(task, elderId);

        ArgumentCaptor<Map<String, Object>> bubbleCap =
                ArgumentCaptor.forClass(Map.class);
        then(lineClient).should().pushFlex(eq("Uxxx"), anyString(), bubbleCap.capture());

        Map<String, Object> footer = (Map<String, Object>) bubbleCap.getValue().get("footer");
        List<Map<String, Object>> buttons =
                (List<Map<String, Object>>) footer.get("contents");
        assertButtonsContainCallAndAck(buttons);
    }

    /** 長者沒 phone（null）→ Flex bubble 只有「我收到了」按鈕。 */
    @Test
    @SuppressWarnings("unchecked")
    void flex_bubble_should_skip_call_button_when_elder_phone_missing() {
        CareTask task = buildTask(1);
        CareContactEscalation contact = CareContactEscalation.builder()
                .id(1L).elderId(elderId).contactUserId(2001L).level(1)
                .channel(NotificationChannel.LINE).slaSeconds(30).build();
        given(contactService.findContact(elderId, 1)).willReturn(Optional.of(contact));
        given(gateway.send(any(), any(), anyString(), anyString())).willReturn(true);

        LineMessagingClient lineClient = org.mockito.Mockito.mock(LineMessagingClient.class);
        given(lineClientProvider.getIfAvailable()).willReturn(lineClient);
        given(lineBindingRepo.findByCaregiverId(2001L)).willReturn(Optional.of(
                CaregiverLineBinding.builder()
                        .id(7L).tenantId(1L).caregiverId(2001L).lineUserId("Uxxx").build()));
        given(careEventRepo.findById(200L)).willReturn(Optional.of(
                CareEvent.builder().id(200L).elderId(elderId)
                        .eventType(CareEventType.MISSED_CHECK_IN).riskLevel(RiskLevel.MEDIUM)
                        .occurredAt(now).build()));
        given(elderProfileRepo.findById(elderId)).willReturn(Optional.of(
                ElderProfile.builder().id(elderId).tenantId(1L).name("王美玉")
                        .age(82).mobility("LOW").phone(null).build()));

        service.notify(task, elderId);

        ArgumentCaptor<Map<String, Object>> bubbleCap =
                ArgumentCaptor.forClass(Map.class);
        then(lineClient).should().pushFlex(eq("Uxxx"), anyString(), bubbleCap.capture());

        Map<String, Object> footer = (Map<String, Object>) bubbleCap.getValue().get("footer");
        List<Map<String, Object>> buttons =
                (List<Map<String, Object>>) footer.get("contents");
        org.assertj.core.api.Assertions.assertThat(buttons).hasSize(1);
        Map<String, Object> action0 = (Map<String, Object>) buttons.get(0).get("action");
        org.assertj.core.api.Assertions.assertThat(action0.get("type")).isEqualTo("postback");
    }

    @SuppressWarnings("unchecked")
    private static void assertButtonsContainCallAndAck(List<Map<String, Object>> buttons) {
        org.assertj.core.api.Assertions.assertThat(buttons).hasSize(2);

        Map<String, Object> callAction = (Map<String, Object>) buttons.get(0).get("action");
        org.assertj.core.api.Assertions.assertThat(callAction.get("type")).isEqualTo("uri");
        org.assertj.core.api.Assertions.assertThat((String) callAction.get("uri"))
                .startsWith("tel:+886");
        org.assertj.core.api.Assertions.assertThat((String) callAction.get("label"))
                .contains("打給").contains("王美玉");

        Map<String, Object> ackAction = (Map<String, Object>) buttons.get(1).get("action");
        org.assertj.core.api.Assertions.assertThat(ackAction.get("type")).isEqualTo("postback");
        org.assertj.core.api.Assertions.assertThat((String) ackAction.get("data"))
                .startsWith("ack:");
    }
}
