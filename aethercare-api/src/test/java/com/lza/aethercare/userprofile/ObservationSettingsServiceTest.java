package com.lza.aethercare.userprofile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lza.aethercare.common.error.BusinessException;
import com.lza.aethercare.userprofile.dto.ObservationSettingsResponse;
import com.lza.aethercare.userprofile.dto.UpdateObservationSettingsRequest;
import com.lza.aethercare.userprofile.entity.RecipientObservationSettings;
import com.lza.aethercare.userprofile.repository.RecipientObservationSettingsRepository;
import com.lza.aethercare.userprofile.service.ObservationSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/** Spec § Gap C：observation settings service 行為單元測試。 */
@ExtendWith(MockitoExtension.class)
class ObservationSettingsServiceTest {

    private static final Long RECIPIENT_ID = 301L;

    @Mock RecipientObservationSettingsRepository repo;
    final ObjectMapper json = new ObjectMapper();

    ObservationSettingsService service;

    @BeforeEach
    void setUp() {
        service = new ObservationSettingsService(repo, json);
    }

    /** 缺資料時回 MVP 預設，不寫 DB。 */
    @Test
    void get_returns_defaults_when_missing() {
        given(repo.findByCareRecipientId(RECIPIENT_ID)).willReturn(Optional.empty());

        ObservationSettingsResponse resp = service.get(RECIPIENT_ID);

        assertThat(resp.careRecipientId()).isEqualTo(RECIPIENT_ID);
        assertThat(resp.expectedCheckinTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(resp.checkinGraceMinutes()).isEqualTo(60);
        assertThat(resp.maxInactiveMinutesDaytime()).isEqualTo(180);
        assertThat(resp.maxInactiveMinutesNight()).isEqualTo(480);
        assertThat(resp.passiveMonitoringEnabled()).isTrue();
        then(repo).should().findByCareRecipientId(RECIPIENT_ID);
        then(repo).shouldHaveNoMoreInteractions();
    }

    /** 有資料時回 DB 內容。 */
    @Test
    void get_returns_persisted_values() {
        RecipientObservationSettings entity = RecipientObservationSettings.builder()
                .id(1L).tenantId(1L).careRecipientId(RECIPIENT_ID)
                .expectedCheckinTime(LocalTime.of(8, 30))
                .checkinGraceMinutes(30)
                .maxInactiveMinutesDaytime(150)
                .maxInactiveMinutesNight(420)
                .passiveMonitoringEnabled(false)
                .build();
        given(repo.findByCareRecipientId(RECIPIENT_ID)).willReturn(Optional.of(entity));

        ObservationSettingsResponse resp = service.get(RECIPIENT_ID);

        assertThat(resp.expectedCheckinTime()).isEqualTo(LocalTime.of(8, 30));
        assertThat(resp.checkinGraceMinutes()).isEqualTo(30);
        assertThat(resp.passiveMonitoringEnabled()).isFalse();
    }

    /** 沒有 entity → upsert 建新的並 save。 */
    @Test
    void upsert_creates_when_absent() {
        given(repo.findByCareRecipientId(RECIPIENT_ID)).willReturn(Optional.empty());
        given(repo.save(any(RecipientObservationSettings.class)))
                .willAnswer(inv -> inv.getArgument(0));

        UpdateObservationSettingsRequest req = UpdateObservationSettingsRequest.builder()
                .expectedCheckinTime(LocalTime.of(7, 45))
                .checkinGraceMinutes(45)
                .build();
        ObservationSettingsResponse resp = service.upsert(RECIPIENT_ID, req);

        assertThat(resp.expectedCheckinTime()).isEqualTo(LocalTime.of(7, 45));
        assertThat(resp.checkinGraceMinutes()).isEqualTo(45);
        // 缺漏欄位用 service 預設
        assertThat(resp.maxInactiveMinutesDaytime()).isEqualTo(180);
        assertThat(resp.maxInactiveMinutesNight()).isEqualTo(480);
        assertThat(resp.passiveMonitoringEnabled()).isTrue();
    }

    /** 已存在 entity → partial update：null 欄位不變，但 expectedCheckinTime null 例外（可清掉）。 */
    @Test
    void upsert_partial_update_keeps_unspecified_fields_but_can_clear_expected_checkin_time() {
        RecipientObservationSettings existing = RecipientObservationSettings.builder()
                .id(2L).tenantId(1L).careRecipientId(RECIPIENT_ID)
                .expectedCheckinTime(LocalTime.of(9, 0))
                .checkinGraceMinutes(60)
                .maxInactiveMinutesDaytime(180)
                .maxInactiveMinutesNight(480)
                .passiveMonitoringEnabled(true)
                .build();
        given(repo.findByCareRecipientId(RECIPIENT_ID)).willReturn(Optional.of(existing));
        given(repo.save(any(RecipientObservationSettings.class))).willAnswer(inv -> inv.getArgument(0));

        UpdateObservationSettingsRequest req = UpdateObservationSettingsRequest.builder()
                .expectedCheckinTime(null) // 明確清空
                .checkinGraceMinutes(15) // 變更
                // daytime / night / passive 留 null → 不動
                .build();

        ObservationSettingsResponse resp = service.upsert(RECIPIENT_ID, req);

        assertThat(resp.expectedCheckinTime()).isNull(); // 被 PUT 清掉
        assertThat(resp.checkinGraceMinutes()).isEqualTo(15);
        assertThat(resp.maxInactiveMinutesDaytime()).isEqualTo(180);
        assertThat(resp.maxInactiveMinutesNight()).isEqualTo(480);
        assertThat(resp.passiveMonitoringEnabled()).isTrue();
    }

    @Test
    void upsert_rejects_invalid_escalation_json() {
        UpdateObservationSettingsRequest req = UpdateObservationSettingsRequest.builder()
                .escalationPolicyJson("{not valid json")
                .build();

        assertThatThrownBy(() -> service.upsert(RECIPIENT_ID, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("escalationPolicyJson");

        // 驗證在 repo lookup 之前發生，避免無效 PUT 仍命中 DB
        then(repo).shouldHaveNoInteractions();
    }

    @Test
    void upsert_persists_valid_escalation_json() {
        given(repo.findByCareRecipientId(RECIPIENT_ID)).willReturn(Optional.empty());
        given(repo.save(any())).willAnswer(inv -> inv.getArgument(0));

        UpdateObservationSettingsRequest req = UpdateObservationSettingsRequest.builder()
                .escalationPolicyJson("{\"levels\":[{\"contactId\":1,\"timeoutSeconds\":60}]}")
                .build();

        service.upsert(RECIPIENT_ID, req);

        ArgumentCaptor<RecipientObservationSettings> captor =
                ArgumentCaptor.forClass(RecipientObservationSettings.class);
        then(repo).should().save(captor.capture());
        assertThat(captor.getValue().getEscalationPolicyJson()).contains("levels");
    }
}
