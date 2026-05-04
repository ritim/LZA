package com.lza.aethercare.recipient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lza.aethercare.anomaly.dto.CreateActivityRequest;
import com.lza.aethercare.anomaly.entity.ElderActivityEvent;
import com.lza.aethercare.anomaly.enums.ActivityType;
import com.lza.aethercare.anomaly.repository.ElderActivityEventRepository;
import com.lza.aethercare.anomaly.service.ActivityIngestionService;
import com.lza.aethercare.common.error.GlobalExceptionHandler;
import com.lza.aethercare.common.time.Clock;
import com.lza.aethercare.event.dto.CreateCareEventRequest;
import com.lza.aethercare.event.entity.CareEvent;
import com.lza.aethercare.event.enums.CareEventSource;
import com.lza.aethercare.event.enums.CareEventType;
import com.lza.aethercare.event.enums.RiskLevel;
import com.lza.aethercare.event.repository.CareEventRepository;
import com.lza.aethercare.event.service.CareEventService;
import com.lza.aethercare.event.service.CareEventService.CareEventResult;
import com.lza.aethercare.recipient.controller.RecipientSelfController;
import com.lza.aethercare.workflow.entity.CareWorkflowInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec § Master §7：被照顧者 self-service endpoint 整合測試（standalone MockMvc）。 */
@ExtendWith(MockitoExtension.class)
class RecipientSelfControllerTest {

    private static final OffsetDateTime FIXED_NOW =
            OffsetDateTime.of(2026, 4, 27, 10, 30, 0, 0, ZoneOffset.UTC);
    private static final Long RECIPIENT_ID = 301L;

    @Mock ActivityIngestionService activityIngestionService;
    @Mock CareEventService careEventService;
    @Mock ElderActivityEventRepository activityRepo;
    @Mock CareEventRepository careEventRepo;
    @Mock Clock clock;

    MockMvc mvc;
    ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        RecipientSelfController controller = new RecipientSelfController(
                activityIngestionService, careEventService, activityRepo, careEventRepo, clock);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        // missing-id → 400 路徑不打 clock，故用 lenient 避免 strict mockito 報未使用 stub
        lenient().when(clock.now()).thenReturn(FIXED_NOW);
    }

    private ElderActivityEvent savedActivity() {
        return ElderActivityEvent.builder()
                .id(11L).elderId(RECIPIENT_ID)
                .activityType(ActivityType.CHECK_IN)
                .occurredAt(FIXED_NOW)
                .build();
    }

    private CareEventResult sosResult() {
        return new CareEventResult(
                CareEvent.builder().id(555L).elderId(RECIPIENT_ID)
                        .eventType(CareEventType.SOS).riskLevel(RiskLevel.HIGH)
                        .occurredAt(FIXED_NOW).build(),
                CareWorkflowInstance.builder().id(777L).build());
    }

    /** check-ins 寫 ActivityIngestionService 並回 201 + activityLogId。 */
    @Test
    void check_in_with_header_writes_activity_log() throws Exception {
        given(activityIngestionService.ingest(eq(RECIPIENT_ID), any(CreateActivityRequest.class)))
                .willReturn(savedActivity());

        mvc.perform(post("/api/v1/recipient/check-ins")
                        .header("X-Care-Recipient-Id", RECIPIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activityLogId").value(11))
                .andExpect(jsonPath("$.careRecipientId").value(RECIPIENT_ID));

        ArgumentCaptor<CreateActivityRequest> captor = ArgumentCaptor.forClass(CreateActivityRequest.class);
        then(activityIngestionService).should().ingest(eq(RECIPIENT_ID), captor.capture());
        assertThat(captor.getValue().getActivityType()).isEqualTo(ActivityType.CHECK_IN);
        assertThat(captor.getValue().getMetadata()).containsEntry("triggeredBy", "RECIPIENT_BUTTON");
    }

    /** body 內帶 careRecipientId 也可（demo 友善），不必 header。 */
    @Test
    void check_in_falls_back_to_body_recipient_id() throws Exception {
        given(activityIngestionService.ingest(eq(RECIPIENT_ID), any())).willReturn(savedActivity());

        mvc.perform(post("/api/v1/recipient/check-ins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"careRecipientId\":" + RECIPIENT_ID + "}"))
                .andExpect(status().isCreated());

        then(activityIngestionService).should().ingest(eq(RECIPIENT_ID), any());
    }

    /** 缺 recipient id → 400 INVALID_REQUEST，不打下游。 */
    @Test
    void check_in_without_recipient_id_returns_400() throws Exception {
        mvc.perform(post("/api/v1/recipient/check-ins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        then(activityIngestionService).shouldHaveNoInteractions();
    }

    /** SOS 觸發 CareEventService 完整 pipeline，metadata 帶 triggeredBy。 */
    @Test
    void sos_creates_high_event_via_care_event_service() throws Exception {
        given(careEventService.createAndStartWorkflow(any())).willReturn(sosResult());

        mvc.perform(post("/api/v1/recipient/sos")
                        .header("X-Care-Recipient-Id", RECIPIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"頭很暈\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value(555))
                .andExpect(jsonPath("$.workflowId").value(777))
                .andExpect(jsonPath("$.eventType").value("SOS"))
                .andExpect(jsonPath("$.riskLevel").value("HIGH"));

        ArgumentCaptor<CreateCareEventRequest> captor = ArgumentCaptor.forClass(CreateCareEventRequest.class);
        then(careEventService).should().createAndStartWorkflow(captor.capture());
        CreateCareEventRequest req = captor.getValue();
        assertThat(req.getElderId()).isEqualTo(RECIPIENT_ID);
        assertThat(req.getEventType()).isEqualTo(CareEventType.SOS);
        assertThat(req.getSource()).isEqualTo(CareEventSource.MOBILE_APP);
        assertThat(req.getMetadata())
                .containsEntry("triggeredBy", "RECIPIENT_SOS_BUTTON")
                .containsEntry("note", "頭很暈");
    }

    /** Status report 建立 FEELING_UNWELL，dangerSignals 寫進 metadata。 */
    @Test
    void status_report_creates_feeling_unwell_event() throws Exception {
        CareEventResult result = new CareEventResult(
                CareEvent.builder().id(888L).elderId(RECIPIENT_ID)
                        .eventType(CareEventType.FEELING_UNWELL).riskLevel(RiskLevel.MEDIUM)
                        .occurredAt(FIXED_NOW).build(),
                CareWorkflowInstance.builder().id(999L).build());
        given(careEventService.createAndStartWorkflow(any())).willReturn(result);

        mvc.perform(post("/api/v1/recipient/status-reports")
                        .header("X-Care-Recipient-Id", RECIPIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"symptom":"胸悶","dangerSignals":{"hasChestPain":true}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventType").value("FEELING_UNWELL"))
                .andExpect(jsonPath("$.riskLevel").value("MEDIUM"));

        ArgumentCaptor<CreateCareEventRequest> captor = ArgumentCaptor.forClass(CreateCareEventRequest.class);
        then(careEventService).should().createAndStartWorkflow(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(CareEventType.FEELING_UNWELL);
        assertThat(captor.getValue().getMetadata())
                .containsEntry("symptom", "胸悶")
                .containsKey("dangerSignals");
    }

    /** GET /today 回今日有 check-in 的摘要。 */
    @Test
    void today_returns_summary_with_check_in_and_open_event_count() throws Exception {
        ElderActivityEvent ci = ElderActivityEvent.builder()
                .id(1L).elderId(RECIPIENT_ID)
                .activityType(ActivityType.CHECK_IN)
                .occurredAt(FIXED_NOW.minusHours(2))
                .build();
        given(activityRepo.findByElderIdAndOccurredAtBetween(eq(RECIPIENT_ID), any(), any()))
                .willReturn(List.of(ci));
        given(careEventRepo.findByElderIdOrderByOccurredAtDesc(RECIPIENT_ID))
                .willReturn(List.of()); // 無 open events

        mvc.perform(get("/api/v1/recipient/today")
                        .header("X-Care-Recipient-Id", RECIPIENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.careRecipientId").value(RECIPIENT_ID))
                .andExpect(jsonPath("$.checkedInToday").value(true))
                .andExpect(jsonPath("$.openEventsCount").value(0));
    }

    /** GET /today 缺 id → 400。 */
    @Test
    void today_without_id_returns_400() throws Exception {
        mvc.perform(get("/api/v1/recipient/today"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        then(activityRepo).shouldHaveNoInteractions();
    }
}
