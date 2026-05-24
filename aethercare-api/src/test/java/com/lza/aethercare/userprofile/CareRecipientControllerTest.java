package com.lza.aethercare.userprofile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lza.aethercare.userprofile.controller.CareRecipientController;
import com.lza.aethercare.userprofile.dto.ElderContactResponse;
import com.lza.aethercare.userprofile.dto.ElderContactsResponse;
import com.lza.aethercare.userprofile.dto.ElderEventItem;
import com.lza.aethercare.userprofile.dto.ElderProfileResponse;
import com.lza.aethercare.userprofile.dto.ObservationSettingsResponse;
import com.lza.aethercare.userprofile.dto.UpdateObservationSettingsRequest;
import com.lza.aethercare.userprofile.service.CheckInHistoryService;
import com.lza.aethercare.userprofile.service.ElderProfileService;
import com.lza.aethercare.userprofile.service.ObservationSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CareRecipientController（spec § Master §7 canonical /api/v1/care-recipients/*）整合測試。 */
@ExtendWith(MockitoExtension.class)
class CareRecipientControllerTest {

    private static final Long RECIPIENT_ID = 301L;

    @Mock ElderProfileService elderProfileService;
    @Mock ObservationSettingsService observationSettingsService;
    @Mock CheckInHistoryService checkInHistoryService;

    MockMvc mvc;
    final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() {
        CareRecipientController controller = new CareRecipientController(
                elderProfileService, observationSettingsService, checkInHistoryService);
        // 預設 standalone setup 不註冊 JavaTimeModule，LocalTime 會被序列化成 [9,0] array；
        // 自帶 ObjectMapper 才能拿到 ISO "09:00:00"。
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
                .build();
    }

    @Test
    void get_recipient_proxies_to_elder_profile_service() throws Exception {
        given(elderProfileService.getProfile(RECIPIENT_ID))
                .willReturn(new ElderProfileResponse(RECIPIENT_ID, "王美玉", 82, "FEMALE", "LOW",
                        List.of("diabetes"), List.of(), "台北市", "曾跌倒"));

        mvc.perform(get("/api/v1/care-recipients/{id}", RECIPIENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(RECIPIENT_ID))
                .andExpect(jsonPath("$.name").value("王美玉"));
    }

    /** Contacts wire 欄位 rename：legacy elderId → canonical careRecipientId。 */
    @Test
    void get_contacts_renames_field_to_care_recipient_id() throws Exception {
        ElderContactResponse contact = new ElderContactResponse(
                1L, "王先生", "兒子", "+886912345678", 1);
        given(elderProfileService.getContacts(RECIPIENT_ID))
                .willReturn(new ElderContactsResponse(RECIPIENT_ID, List.of(contact)));

        mvc.perform(get("/api/v1/care-recipients/{id}/contacts", RECIPIENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.careRecipientId").value(RECIPIENT_ID))
                .andExpect(jsonPath("$.elderId").doesNotExist())
                .andExpect(jsonPath("$.contacts[0].name").value("王先生"));
    }

    @Test
    void get_events_proxies_to_recent_events() throws Exception {
        given(elderProfileService.getRecentEvents(RECIPIENT_ID, 20)).willReturn(List.of());

        mvc.perform(get("/api/v1/care-recipients/{id}/events", RECIPIENT_ID))
                .andExpect(status().isOk());

        then(elderProfileService).should().getRecentEvents(RECIPIENT_ID, 20);
    }

    @Test
    void get_observation_settings_returns_response() throws Exception {
        given(observationSettingsService.get(RECIPIENT_ID))
                .willReturn(new ObservationSettingsResponse(
                        RECIPIENT_ID, LocalTime.of(9, 0), 60, 180, 480, true, null));

        mvc.perform(get("/api/v1/care-recipients/{id}/observation-settings", RECIPIENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.careRecipientId").value(RECIPIENT_ID))
                .andExpect(jsonPath("$.expectedCheckinTime").value("09:00:00"))
                .andExpect(jsonPath("$.checkinGraceMinutes").value(60))
                .andExpect(jsonPath("$.passiveMonitoringEnabled").value(true));
    }

    /** PUT 將 request 轉給 service.upsert，服務層回傳的新值反映到 response。 */
    @Test
    void put_observation_settings_calls_upsert() throws Exception {
        given(observationSettingsService.upsert(eq(RECIPIENT_ID), any()))
                .willReturn(new ObservationSettingsResponse(
                        RECIPIENT_ID, LocalTime.of(8, 30), 30, 120, 360, false, null));

        String body = """
                {"expectedCheckinTime":"08:30:00","checkinGraceMinutes":30,
                 "maxInactiveMinutesDaytime":120,"maxInactiveMinutesNight":360,
                 "passiveMonitoringEnabled":false}
                """;

        mvc.perform(put("/api/v1/care-recipients/{id}/observation-settings", RECIPIENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expectedCheckinTime").value("08:30:00"))
                .andExpect(jsonPath("$.checkinGraceMinutes").value(30))
                .andExpect(jsonPath("$.passiveMonitoringEnabled").value(false));

        ArgumentCaptor<UpdateObservationSettingsRequest> captor =
                ArgumentCaptor.forClass(UpdateObservationSettingsRequest.class);
        then(observationSettingsService).should().upsert(eq(RECIPIENT_ID), captor.capture());
        UpdateObservationSettingsRequest sent = captor.getValue();
        assertThat(sent.getExpectedCheckinTime()).isEqualTo(LocalTime.of(8, 30));
        assertThat(sent.getCheckinGraceMinutes()).isEqualTo(30);
        assertThat(sent.getPassiveMonitoringEnabled()).isFalse();
    }
}
