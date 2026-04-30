package com.lza.aethercare.assessment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lza.aethercare.ai.knowledge.CareKnowledgeBase;
import com.lza.aethercare.ai.service.EventTypeMapper;
import com.lza.aethercare.assessment.dto.AnswerItem;
import com.lza.aethercare.assessment.dto.AssessmentAnswerRequest;
import com.lza.aethercare.assessment.dto.AssessmentAnswerResponse;
import com.lza.aethercare.assessment.entity.CareAssessmentAnswer;
import com.lza.aethercare.assessment.repository.CareAssessmentAnswerRepository;
import com.lza.aethercare.assessment.service.AssessmentAnswerService;
import com.lza.aethercare.audit.enums.CareAuditAction;
import com.lza.aethercare.audit.service.CareAuditService;
import com.lza.aethercare.common.time.Clock;
import com.lza.aethercare.event.entity.CareEvent;
import com.lza.aethercare.event.enums.CareEventSource;
import com.lza.aethercare.event.enums.CareEventStatus;
import com.lza.aethercare.event.enums.CareEventType;
import com.lza.aethercare.event.enums.RiskLevel;
import com.lza.aethercare.event.service.CareEventService;
import com.lza.aethercare.workflow.entity.CareWorkflowInstance;
import com.lza.aethercare.workflow.service.CareWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

/**
 * AssessmentAnswerService 單元測試：danger answer 觸發 dangerDetected=true，
 * 安全 answer 不觸發。
 */
class AssessmentAnswerServiceTest {

    private CareAssessmentAnswerRepository repo;
    private CareKnowledgeBase knowledgeBase;
    private CareEventService careEventService;
    private CareWorkflowService workflowService;
    private CareAuditService auditService;
    private Clock clock;
    private AssessmentAnswerService service;

    private final OffsetDateTime now = OffsetDateTime.of(2026, 4, 27, 12, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUp() throws IOException {
        repo = mock(CareAssessmentAnswerRepository.class);
        careEventService = mock(CareEventService.class);
        workflowService = mock(CareWorkflowService.class);
        auditService = mock(CareAuditService.class);
        clock = mock(Clock.class);
        knowledgeBase = new CareKnowledgeBase(new ObjectMapper());
        knowledgeBase.loadAll();

        service = new AssessmentAnswerService(
                repo,
                knowledgeBase,
                new EventTypeMapper(),
                careEventService,
                workflowService,
                auditService,
                clock);

        given(clock.now()).willReturn(now);
        given(repo.save(any(CareAssessmentAnswer.class))).willAnswer(inv -> {
            CareAssessmentAnswer e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });
    }

    /** q_consciousness 答「否」屬於 fall.json dangerAnswer，應該 dangerDetected=true 並記 audit。 */
    @Test
    void should_detect_danger_when_answer_matches_dangerAnswer() {
        CareEvent event = mockEvent(CareEventType.FALL_DETECTED, RiskLevel.HIGH);
        given(careEventService.findById(50L)).willReturn(Optional.of(event));
        given(workflowService.findById(500L)).willReturn(mock(CareWorkflowInstance.class));

        AssessmentAnswerRequest req = AssessmentAnswerRequest.builder()
                .eventId(50L)
                .taskId(5000L)
                .answers(List.of(AnswerItem.builder()
                        .questionId("q_consciousness")
                        .question("長者目前是否意識清楚並能正常對話？")
                        .answer("否")
                        .build()))
                .build();

        AssessmentAnswerResponse resp = service.submit(500L, 99L, req);

        assertThat(resp.saved()).isTrue();
        assertThat(resp.riskReevaluation().dangerDetected()).isTrue();
        assertThat(resp.riskReevaluation().recommendedAction()).isEqualTo("CALL_EMERGENCY");
        then(auditService).should().log(eq(500L), eq(50L), eq(99L),
                eq(CareAuditAction.ASSESSMENT_RECORDED), any());
        then(repo).should().save(any(CareAssessmentAnswer.class));
    }

    /** 全部答案皆為安全選項時 dangerDetected=false 且 recommendedAction=null。 */
    @Test
    void should_not_detect_danger_when_no_dangerAnswer_match() {
        CareEvent event = mockEvent(CareEventType.FALL_DETECTED, RiskLevel.HIGH);
        given(careEventService.findById(51L)).willReturn(Optional.of(event));
        given(workflowService.findById(501L)).willReturn(mock(CareWorkflowInstance.class));

        AssessmentAnswerRequest req = AssessmentAnswerRequest.builder()
                .eventId(51L)
                .answers(List.of(AnswerItem.builder()
                        .questionId("q_consciousness")
                        .question("長者目前是否意識清楚並能正常對話？")
                        .answer("是")
                        .build()))
                .build();

        AssessmentAnswerResponse resp = service.submit(501L, 99L, req);

        assertThat(resp.riskReevaluation().dangerDetected()).isFalse();
        assertThat(resp.riskReevaluation().recommendedAction()).isNull();
        assertThat(resp.riskReevaluation().riskLevel()).isEqualTo("HIGH");
    }

    private CareEvent mockEvent(CareEventType type, RiskLevel risk) {
        return CareEvent.builder()
                .id(1L)
                .tenantId(1L)
                .elderId(1001L)
                .source(CareEventSource.IOT_SENSOR)
                .eventType(type)
                .riskLevel(risk)
                .status(CareEventStatus.RECEIVED)
                .occurredAt(now.minusMinutes(3))
                .build();
    }
}

