package com.lza.aethercare.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lza.aethercare.ai.dto.CareGuidanceResponse;
import com.lza.aethercare.ai.knowledge.CareKnowledgeBase;
import com.lza.aethercare.ai.llm.StaticLlmProvider;
import com.lza.aethercare.ai.service.AiCareGuidanceService;
import com.lza.aethercare.ai.service.EventTypeMapper;
import com.lza.aethercare.common.error.BusinessException;
import com.lza.aethercare.common.time.Clock;
import com.lza.aethercare.event.entity.CareEvent;
import com.lza.aethercare.event.enums.CareEventStatus;
import com.lza.aethercare.event.enums.CareEventSource;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * AiCareGuidanceService 單元測試：驗證 FALL_DETECTED 對映 fall.json 並回 guidance；
 * event 不存在時拋 NOT_FOUND。
 */
class AiCareGuidanceServiceTest {

    private CareEventService careEventService;
    private CareWorkflowService workflowService;
    private CareKnowledgeBase knowledgeBase;
    private Clock clock;
    private AiCareGuidanceService service;

    private final OffsetDateTime now = OffsetDateTime.of(2026, 4, 27, 12, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUp() throws IOException {
        careEventService = mock(CareEventService.class);
        workflowService = mock(CareWorkflowService.class);
        clock = mock(Clock.class);
        knowledgeBase = new CareKnowledgeBase(new ObjectMapper());
        knowledgeBase.loadAll();

        service = new AiCareGuidanceService(
                careEventService,
                workflowService,
                knowledgeBase,
                new EventTypeMapper(),
                new StaticLlmProvider(),
                clock);

        given(clock.now()).willReturn(now);
    }

    /** FALL_DETECTED event 應拿到 fall.json 的 guidance + questions。 */
    @Test
    void should_return_fall_guidance_for_fall_detected_event() {
        CareEvent event = CareEvent.builder()
                .id(10L)
                .elderId(1001L)
                .tenantId(1L)
                .source(CareEventSource.IOT_SENSOR)
                .eventType(CareEventType.FALL_DETECTED)
                .riskLevel(RiskLevel.HIGH)
                .status(CareEventStatus.RECEIVED)
                .occurredAt(now.minusMinutes(2))
                .build();
        given(careEventService.findById(10L)).willReturn(Optional.of(event));
        given(workflowService.findById(100L)).willReturn(mock(CareWorkflowInstance.class));

        CareGuidanceResponse resp = service.generate(10L, 100L);

        assertThat(resp.summary()).contains("跌倒");
        assertThat(resp.questions()).isNotEmpty();
        assertThat(resp.dangerSigns()).isNotEmpty();
        assertThat(resp.suggestedActions()).anyMatch(a -> "CALL_EMERGENCY".equals(a.type()));
        assertThat(resp.generatedAt()).isEqualTo(now);
    }

    /** event 不存在時拋 BusinessException(NOT_FOUND)。 */
    @Test
    void should_throw_when_event_not_found() {
        given(careEventService.findById(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(999L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("event=999");
    }
}
