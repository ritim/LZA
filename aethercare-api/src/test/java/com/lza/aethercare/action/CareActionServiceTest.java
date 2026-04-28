package com.lza.aethercare.action;

import com.lza.aethercare.action.dto.CreateCareActionRequest;
import com.lza.aethercare.action.entity.CareAction;
import com.lza.aethercare.action.enums.CareActionType;
import com.lza.aethercare.action.repository.CareActionRepository;
import com.lza.aethercare.action.service.CareActionService;
import com.lza.aethercare.audit.service.CareAuditService;
import com.lza.aethercare.common.error.BusinessException;
import com.lza.aethercare.common.error.ErrorCode;
import com.lza.aethercare.common.time.Clock;
import com.lza.aethercare.task.entity.CareTask;
import com.lza.aethercare.task.enums.AssigneeType;
import com.lza.aethercare.task.enums.CareTaskStatus;
import com.lza.aethercare.task.service.CareTaskService;
import com.lza.aethercare.workflow.service.CareWorkflowService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;

/**
 * CareActionService 單元測試：驗證 CONFIRM_SAFE 成功路徑與任務已完成時拋出例外的行為。
 */
@ExtendWith(MockitoExtension.class)
class CareActionServiceTest {

    @Mock
    CareActionRepository actionRepo;
    @Mock
    CareTaskService taskService;
    @Mock
    CareWorkflowService workflowService;
    @Mock
    CareAuditService auditService;
    @Mock
    ApplicationEventPublisher publisher;
    @Mock
    Clock clock;

    @InjectMocks
    CareActionService service;

    private final OffsetDateTime now = OffsetDateTime.of(2026, 4, 27, 12, 0, 0, 0, ZoneOffset.ofHours(8));

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "actionReceivedTopic", "care.action.received");
        lenient().when(clock.now()).thenReturn(now);
    }

    private CareTask buildTask() {
        return CareTask.builder()
                .id(1L)
                .workflowId(100L)
                .eventId(200L)
                .assigneeId(2001L)
                .assigneeType(AssigneeType.FAMILY)
                .level(1)
                .status(CareTaskStatus.PENDING)
                .deadlineAt(now.plusMinutes(30))
                .createdAt(now.minusMinutes(10))
                .updatedAt(now)
                .version(0)
                .build();
    }

    private CreateCareActionRequest confirmSafeReq() {
        return CreateCareActionRequest.builder()
                .actorId(2001L)
                .actionType(CareActionType.CONFIRM_SAFE)
                .note("平安確認")
                .build();
    }

    /** 驗證 CONFIRM_SAFE 成功時，workflow.resolve 被正確呼叫。 */
    @Test
    void should_resolve_workflow_when_CONFIRM_SAFE_succeeds() {
        CareTask task = buildTask();
        given(taskService.findById(1L)).willReturn(Optional.of(task));
        given(taskService.completeIfActive(1L)).willReturn(true);
        given(actionRepo.save(any())).willAnswer(inv -> {
            CareAction a = inv.getArgument(0);
            a.setId(99L);
            return a;
        });

        service.handle(1L, confirmSafeReq());

        then(workflowService).should().resolve(eq(task.getWorkflowId()), eq(2001L));
    }

    /** 驗證 completeIfActive 回 false 時，拋出 TASK_ALREADY_FINALIZED 例外。 */
    @Test
    void should_throw_TASK_ALREADY_FINALIZED_when_completeIfActive_returns_false() {
        CareTask task = buildTask();
        given(taskService.findById(1L)).willReturn(Optional.of(task));
        given(taskService.completeIfActive(1L)).willReturn(false);

        assertThatThrownBy(() -> service.handle(1L, confirmSafeReq()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.TASK_ALREADY_FINALIZED);
    }
}
