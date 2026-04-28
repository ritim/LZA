package com.lza.aethercare.workflow;

import com.lza.aethercare.audit.enums.CareAuditAction;
import com.lza.aethercare.audit.service.CareAuditService;
import com.lza.aethercare.common.time.Clock;
import com.lza.aethercare.decision.service.DecisionService;
import com.lza.aethercare.event.enums.RiskLevel;
import com.lza.aethercare.notification.service.NotificationService;
import com.lza.aethercare.task.entity.CareTask;
import com.lza.aethercare.task.enums.AssigneeType;
import com.lza.aethercare.task.enums.CareTaskStatus;
import com.lza.aethercare.task.service.CareTaskService;
import com.lza.aethercare.userprofile.service.EscalationContactService;
import com.lza.aethercare.workflow.entity.CareWorkflowInstance;
import com.lza.aethercare.workflow.enums.CareWorkflowStatus;
import com.lza.aethercare.workflow.enums.CareWorkflowType;
import com.lza.aethercare.workflow.repository.CareWorkflowInstanceRepository;
import com.lza.aethercare.workflow.service.CareWorkflowService;
import com.lza.aethercare.workflow.service.WorkflowLockService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * CareWorkflowService 單元測試：驗證 escalate 無下一層聯絡人時標記 UNRESOLVED，以及鎖取得失敗時提前返回。
 */
@ExtendWith(MockitoExtension.class)
class CareWorkflowServiceTest {

    @Mock
    CareWorkflowInstanceRepository workflowRepo;
    @Mock
    CareTaskService taskService;
    @Mock
    NotificationService notificationService;
    @Mock
    EscalationContactService contactService;
    @Mock
    CareAuditService auditService;
    @Mock
    WorkflowLockService lockService;
    @Mock
    DecisionService decisionService;
    @Mock
    ApplicationEventPublisher publisher;
    @Mock
    Clock clock;

    @InjectMocks
    CareWorkflowService service;

    private final OffsetDateTime now = OffsetDateTime.of(2026, 4, 27, 12, 0, 0, 0, ZoneOffset.ofHours(8));

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "workflowStartedTopic", "care.workflow.started");
        lenient().when(clock.now()).thenReturn(now);
    }

    /** 驗證 escalate 找不到下一層聯絡人時，會呼叫 markTerminalIfIn 標記 UNRESOLVED，並記錄 audit。 */
    @Test
    void should_markUnresolved_when_escalate_finds_no_next_contact() {
        // given: level=2 的超時任務
        CareTask timedOutTask = CareTask.builder()
                .id(10L)
                .workflowId(100L)
                .eventId(200L)
                .assigneeId(2001L)
                .assigneeType(AssigneeType.FAMILY)
                .level(2)
                .status(CareTaskStatus.TIMEOUT)
                .deadlineAt(now.minusMinutes(5))
                .createdAt(now.minusHours(1))
                .updatedAt(now)
                .version(1)
                .build();

        CareWorkflowInstance wf = CareWorkflowInstance.builder()
                .id(100L)
                .eventId(200L)
                .elderId(1001L)
                .workflowType(CareWorkflowType.FALL_RESPONSE)
                .riskLevel(RiskLevel.HIGH)
                .status(CareWorkflowStatus.WAITING_RESPONSE)
                .currentLevel(2)
                .version(0)
                .build();

        given(lockService.acquire(100L)).willReturn(Optional.of("token-100"));
        given(workflowRepo.findById(100L)).willReturn(Optional.of(wf));
        // level 3 不存在
        given(contactService.findContact(1001L, 3)).willReturn(Optional.empty());
        // markUnresolved 內部呼叫 markTerminalIfIn -> 更新 1 筆，再查 wf
        given(workflowRepo.markTerminalIfIn(eq(100L), eq(CareWorkflowStatus.UNRESOLVED.name()),
                anyList(), any())).willReturn(1);

        // when
        service.escalate(timedOutTask, null);

        // then: markTerminalIfIn 被呼叫，狀態設為 UNRESOLVED
        then(workflowRepo).should().markTerminalIfIn(eq(100L),
                eq(CareWorkflowStatus.UNRESOLVED.name()), anyList(), any());
        // audit WORKFLOW_UNRESOLVED 被記錄
        then(auditService).should().log(eq(100L), any(), isNull(),
                eq(CareAuditAction.WORKFLOW_UNRESOLVED), anyString());
        // 不應建立新 task（注意 level 為 int，要用 anyInt() 避免 unboxing NPE）
        then(taskService).should(never()).createTask(anyLong(), anyLong(), anyLong(),
                any(), anyInt(), any());
    }

    /** 驗證 lockService.acquire 回 false 時，escalate 提前返回，不呼叫 contactService。 */
    @Test
    void should_skip_escalate_when_lock_acquire_fails() {
        CareTask timedOutTask = CareTask.builder()
                .id(11L)
                .workflowId(101L)
                .eventId(201L)
                .assigneeId(2001L)
                .assigneeType(AssigneeType.FAMILY)
                .level(1)
                .status(CareTaskStatus.TIMEOUT)
                .deadlineAt(now.minusMinutes(3))
                .createdAt(now.minusHours(1))
                .updatedAt(now)
                .version(1)
                .build();

        given(lockService.acquire(101L)).willReturn(Optional.empty());

        // when
        service.escalate(timedOutTask, null);

        // then: 鎖取不到，不應碰 contactService
        verifyNoInteractions(contactService);
        then(workflowRepo).should(never()).findById(anyLong());
    }
}
