package com.lza.aethercare.task;

import com.lza.aethercare.audit.enums.CareAuditAction;
import com.lza.aethercare.audit.service.CareAuditService;
import com.lza.aethercare.task.entity.CareTask;
import com.lza.aethercare.task.enums.AssigneeType;
import com.lza.aethercare.task.enums.CareTaskStatus;
import com.lza.aethercare.task.service.CareTaskService;
import com.lza.aethercare.task.service.CareTaskTimeoutHandler;
import com.lza.aethercare.workflow.service.CareWorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * CareTaskTimeoutHandler 單元測試：驗證 markTimeoutIfPending 回 false 時不重複處理，以及成功時正確 audit 與 escalate。
 */
@ExtendWith(MockitoExtension.class)
class CareTaskTimeoutHandlerTest {

    @Mock
    CareTaskService taskService;
    @Mock
    CareWorkflowService workflowService;
    @Mock
    CareAuditService auditService;

    @InjectMocks
    CareTaskTimeoutHandler handler;

    private final OffsetDateTime now = OffsetDateTime.of(2026, 4, 27, 12, 0, 0, 0, ZoneOffset.ofHours(8));

    private CareTask buildTask() {
        return CareTask.builder()
                .id(1L)
                .workflowId(100L)
                .eventId(200L)
                .assigneeId(2001L)
                .assigneeType(AssigneeType.FAMILY)
                .level(1)
                .status(CareTaskStatus.PENDING)
                .deadlineAt(now.minusMinutes(5))
                .createdAt(now.minusHours(1))
                .updatedAt(now)
                .version(0)
                .build();
    }

    /** 驗證 markTimeoutIfPending 回 false 時，escalate 不被呼叫（避免重複處理）。 */
    @Test
    void should_skip_when_markTimeoutIfPending_returns_false() {
        CareTask task = buildTask();
        given(taskService.findById(1L)).willReturn(Optional.of(task));
        given(taskService.markTimeoutIfPending(1L)).willReturn(false);

        handler.handleTimeout(1L);

        then(workflowService).should(never()).escalate(any(), any());
        then(auditService).should(never()).log(any(), any(), any(), any(), anyString());
    }

    /** 驗證 markTimeoutIfPending 回 true 時，audit 寫入後正確呼叫 escalate。 */
    @Test
    void should_audit_and_escalate_when_markTimeout_succeeds() {
        CareTask task = buildTask();
        given(taskService.findById(1L)).willReturn(Optional.of(task));
        given(taskService.markTimeoutIfPending(1L)).willReturn(true);

        handler.handleTimeout(1L);

        then(auditService).should().log(
                eq(task.getWorkflowId()),
                eq(task.getEventId()),
                isNull(),
                eq(CareAuditAction.TASK_TIMEOUT),
                anyString());
        then(workflowService).should().escalate(eq(task), isNull());
    }
}
