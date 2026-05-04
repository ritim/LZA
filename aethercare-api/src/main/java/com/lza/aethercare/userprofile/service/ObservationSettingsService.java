package com.lza.aethercare.userprofile.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lza.aethercare.common.error.BusinessException;
import com.lza.aethercare.common.error.ErrorCode;
import com.lza.aethercare.tenant.context.TenantContext;
import com.lza.aethercare.userprofile.dto.ObservationSettingsResponse;
import com.lza.aethercare.userprofile.dto.UpdateObservationSettingsRequest;
import com.lza.aethercare.userprofile.entity.RecipientObservationSettings;
import com.lza.aethercare.userprofile.repository.RecipientObservationSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalTime;

/**
 * Spec § Master §0 / Gap C：observation settings get / upsert service。
 *
 * <p>GET 缺資料時 return MVP 預設值（不寫 DB），讓前端在 UI 還沒推 settings 表單時
 * 仍能取到合理 fallback；PUT 採 upsert 寫入。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ObservationSettingsService {

    private static final int DEFAULT_GRACE_MINUTES = 60;
    private static final int DEFAULT_DAYTIME_INACTIVE_MIN = 180;
    private static final int DEFAULT_NIGHT_INACTIVE_MIN = 480;

    private final RecipientObservationSettingsRepository repo;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ObservationSettingsResponse get(Long careRecipientId) {
        return repo.findByCareRecipientId(careRecipientId)
                .map(this::toResponse)
                .orElseGet(() -> defaultsFor(careRecipientId));
    }

    @Transactional
    public ObservationSettingsResponse upsert(Long careRecipientId, UpdateObservationSettingsRequest req) {
        validateEscalationJson(req.getEscalationPolicyJson());

        RecipientObservationSettings entity = repo.findByCareRecipientId(careRecipientId)
                .orElseGet(() -> RecipientObservationSettings.builder()
                        .tenantId(TenantContext.getOrDefault())
                        .careRecipientId(careRecipientId)
                        .checkinGraceMinutes(DEFAULT_GRACE_MINUTES)
                        .maxInactiveMinutesDaytime(DEFAULT_DAYTIME_INACTIVE_MIN)
                        .maxInactiveMinutesNight(DEFAULT_NIGHT_INACTIVE_MIN)
                        .passiveMonitoringEnabled(true)
                        .build());

        // partial update：null 代表不變動，但 expectedCheckinTime 例外（null 是 valid value，代表「停用」）
        if (req.getCheckinGraceMinutes() != null) entity.setCheckinGraceMinutes(req.getCheckinGraceMinutes());
        if (req.getMaxInactiveMinutesDaytime() != null) entity.setMaxInactiveMinutesDaytime(req.getMaxInactiveMinutesDaytime());
        if (req.getMaxInactiveMinutesNight() != null) entity.setMaxInactiveMinutesNight(req.getMaxInactiveMinutesNight());
        if (req.getPassiveMonitoringEnabled() != null) entity.setPassiveMonitoringEnabled(req.getPassiveMonitoringEnabled());
        if (req.getEscalationPolicyJson() != null) entity.setEscalationPolicyJson(req.getEscalationPolicyJson());
        // expectedCheckinTime：永遠以 request 為準（含 null），讓 PUT 可以「清掉」設定
        entity.setExpectedCheckinTime(req.getExpectedCheckinTime());

        RecipientObservationSettings saved = repo.save(entity);
        log.info("ObservationSettings upsert recipientId={} id={}", careRecipientId, saved.getId());
        return toResponse(saved);
    }

    private void validateEscalationJson(String json) {
        if (json == null || json.isBlank()) return;
        try {
            objectMapper.readTree(json);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "escalationPolicyJson 不是有效 JSON：" + e.getMessage());
        }
    }

    private ObservationSettingsResponse toResponse(RecipientObservationSettings e) {
        return new ObservationSettingsResponse(
                e.getCareRecipientId(),
                e.getExpectedCheckinTime(),
                e.getCheckinGraceMinutes(),
                e.getMaxInactiveMinutesDaytime(),
                e.getMaxInactiveMinutesNight(),
                Boolean.TRUE.equals(e.getPassiveMonitoringEnabled()),
                e.getEscalationPolicyJson()
        );
    }

    private ObservationSettingsResponse defaultsFor(Long careRecipientId) {
        return new ObservationSettingsResponse(
                careRecipientId,
                LocalTime.of(9, 0),
                DEFAULT_GRACE_MINUTES,
                DEFAULT_DAYTIME_INACTIVE_MIN,
                DEFAULT_NIGHT_INACTIVE_MIN,
                true,
                null
        );
    }
}
