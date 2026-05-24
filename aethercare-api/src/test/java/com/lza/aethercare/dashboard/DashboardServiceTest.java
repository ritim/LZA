package com.lza.aethercare.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lza.aethercare.anomaly.repository.ElderActivityEventRepository;
import com.lza.aethercare.audit.repository.CareAuditLogRepository;
import com.lza.aethercare.common.time.Clock;
import com.lza.aethercare.dashboard.dto.DashboardResponse;
import com.lza.aethercare.dashboard.service.DashboardService;
import com.lza.aethercare.event.entity.CareEvent;
import com.lza.aethercare.event.enums.CareEventType;
import com.lza.aethercare.event.enums.RiskLevel;
import com.lza.aethercare.event.repository.CareEventRepository;
import com.lza.aethercare.notification.line.entity.CaregiverLineBinding;
import com.lza.aethercare.notification.line.repository.CaregiverLineBindingRepository;
import com.lza.aethercare.task.entity.CareTask;
import com.lza.aethercare.task.enums.AssigneeType;
import com.lza.aethercare.task.enums.CareTaskStatus;
import com.lza.aethercare.task.repository.CareTaskRepository;
import com.lza.aethercare.userprofile.entity.AppUser;
import com.lza.aethercare.userprofile.entity.ElderProfile;
import com.lza.aethercare.userprofile.repository.AppUserRepository;
import com.lza.aethercare.userprofile.repository.ElderProfileRepository;
import com.lza.aethercare.workflow.entity.CareWorkflowInstance;
import com.lza.aethercare.workflow.enums.CareWorkflowStatus;
import com.lza.aethercare.workflow.repository.CareWorkflowInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * DashboardService 單元測試 — 聚焦在 ActiveEventItem.assignee 欄位的填值邏輯。
 *
 * <p>覆蓋三種狀態：
 * <ul>
 *   <li>caregiver 有 AppUser + 有 LINE binding → assignee 帶 displayName + lineDisplayName + lineBound=true</li>
 *   <li>caregiver 有 AppUser 但沒 LINE binding → assignee.lineBound=false / lineDisplayName=null</li>
 *   <li>caregiver AppUser 查不到（被刪 / id 對不上）→ displayName=null（前端顯示 #id）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final Long CAREGIVER_ID = 2001L;
    private static final Long ELDER_ID = 301L;
    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 5, 24, 10, 0, 0, 0, ZoneOffset.ofHours(8));

    @Mock CareTaskRepository taskRepo;
    @Mock CareWorkflowInstanceRepository workflowRepo;
    @Mock CareEventRepository eventRepo;
    @Mock ElderProfileRepository elderRepo;
    @Mock AppUserRepository appUserRepo;
    @Mock CaregiverLineBindingRepository lineBindingRepo;
    @Mock CareAuditLogRepository auditRepo;
    @Mock ElderActivityEventRepository activityRepo;
    @Mock Clock clock;

    DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(
                taskRepo, workflowRepo, eventRepo, elderRepo,
                appUserRepo, lineBindingRepo, auditRepo, activityRepo,
                new ObjectMapper(), clock);
        given(clock.now()).willReturn(NOW);
        // 共用：timeline / activity / resolvedToday 在所有測試都不關心
        Page<com.lza.aethercare.audit.entity.CareAuditLog> empty =
                new PageImpl<>(List.of());
        given(auditRepo.findAll(any(org.springframework.data.domain.Pageable.class)))
                .willReturn(empty);
        given(workflowRepo.countResolvedSince(any())).willReturn(0L);
        given(activityRepo.findLatestByElderIdsAndType(any(), any())).willReturn(Optional.empty());
        given(activityRepo.findLatestByElderIds(any())).willReturn(Optional.empty());
    }

    @Test
    void assignee_should_include_line_display_name_when_binding_exists() {
        givenActiveTaskAndEventAndElder();
        given(appUserRepo.findAllById(List.of(CAREGIVER_ID))).willReturn(List.of(
                AppUser.builder().id(CAREGIVER_ID).username("family01").displayName("王先生").build()));
        given(lineBindingRepo.findByCaregiverIdIn(List.of(CAREGIVER_ID))).willReturn(List.of(
                CaregiverLineBinding.builder()
                        .id(99L).tenantId(1L).caregiverId(CAREGIVER_ID)
                        .lineUserId("Uxxx").lineDisplayName("王哥的 LINE").build()));

        DashboardResponse resp = service.buildFor(CAREGIVER_ID);

        assertThat(resp.activeEvents()).hasSize(1);
        DashboardResponse.AssigneeRef a = resp.activeEvents().get(0).assignee();
        assertThat(a).isNotNull();
        assertThat(a.id()).isEqualTo(CAREGIVER_ID);
        assertThat(a.displayName()).isEqualTo("王先生");
        assertThat(a.lineDisplayName()).isEqualTo("王哥的 LINE");
        assertThat(a.lineBound()).isTrue();
    }

    @Test
    void assignee_should_flag_line_not_bound_when_binding_missing() {
        givenActiveTaskAndEventAndElder();
        given(appUserRepo.findAllById(List.of(CAREGIVER_ID))).willReturn(List.of(
                AppUser.builder().id(CAREGIVER_ID).username("family01").displayName("王先生").build()));
        given(lineBindingRepo.findByCaregiverIdIn(List.of(CAREGIVER_ID))).willReturn(List.of());

        DashboardResponse resp = service.buildFor(CAREGIVER_ID);

        DashboardResponse.AssigneeRef a = resp.activeEvents().get(0).assignee();
        assertThat(a.lineBound()).isFalse();
        assertThat(a.lineDisplayName()).isNull();
        assertThat(a.displayName()).isEqualTo("王先生");
    }

    @Test
    void assignee_should_have_null_display_name_when_app_user_missing() {
        givenActiveTaskAndEventAndElder();
        given(appUserRepo.findAllById(List.of(CAREGIVER_ID))).willReturn(List.of());
        given(lineBindingRepo.findByCaregiverIdIn(List.of(CAREGIVER_ID))).willReturn(List.of());

        DashboardResponse resp = service.buildFor(CAREGIVER_ID);

        DashboardResponse.AssigneeRef a = resp.activeEvents().get(0).assignee();
        assertThat(a.id()).isEqualTo(CAREGIVER_ID);
        assertThat(a.displayName()).isNull();
        assertThat(a.lineBound()).isFalse();
    }

    // ---------- shared fixture ----------

    private void givenActiveTaskAndEventAndElder() {
        CareTask task = CareTask.builder()
                .id(10L).workflowId(100L).eventId(200L)
                .assigneeId(CAREGIVER_ID).assigneeType(AssigneeType.FAMILY)
                .level(1).status(CareTaskStatus.PENDING)
                .deadlineAt(NOW.plusMinutes(30))
                .createdAt(NOW.minusMinutes(5)).updatedAt(NOW).version(0)
                .build();
        given(taskRepo.findActiveByAssignee(CAREGIVER_ID)).willReturn(List.of(task));
        given(workflowRepo.findAllById(List.of(100L))).willReturn(List.of(
                CareWorkflowInstance.builder().id(100L).status(CareWorkflowStatus.WAITING_RESPONSE).build()));
        given(eventRepo.findAllById(List.of(200L))).willReturn(List.of(
                CareEvent.builder().id(200L).elderId(ELDER_ID)
                        .eventType(CareEventType.MISSED_CHECK_IN).riskLevel(RiskLevel.MEDIUM)
                        .occurredAt(NOW).build()));
        given(elderRepo.findAllById(List.of(ELDER_ID))).willReturn(List.of(
                ElderProfile.builder().id(ELDER_ID).tenantId(1L)
                        .name("王美玉").age(82).mobility("LOW").build()));
    }
}
