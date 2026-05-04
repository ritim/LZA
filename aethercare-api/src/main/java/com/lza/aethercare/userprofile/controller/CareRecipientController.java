package com.lza.aethercare.userprofile.controller;

import com.lza.aethercare.userprofile.dto.CareRecipientContactsResponse;
import com.lza.aethercare.userprofile.dto.ElderContactsResponse;
import com.lza.aethercare.userprofile.dto.ElderEventItem;
import com.lza.aethercare.userprofile.dto.ElderProfileResponse;
import com.lza.aethercare.userprofile.dto.ObservationSettingsResponse;
import com.lza.aethercare.userprofile.dto.UpdateObservationSettingsRequest;
import com.lza.aethercare.userprofile.service.ElderProfileService;
import com.lza.aethercare.userprofile.service.ObservationSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Spec § Master §0 / §7 canonical care-recipient resource。
 *
 * <p>v1.0-rc1 的 {@code ElderController}（{@code /api/v1/elders/*}）已於 spec rename 後退役，
 * 全部讀取路徑改由本 controller 提供。Sensor / admin 用的 activity 上報 endpoint
 * 仍掛在 {@link com.lza.aethercare.anomaly.controller.ElderActivityController}（待後續 rename）。
 *
 * <p>受 USER role 保護（{@code /api/v1/care-**} matcher，見 SecurityConfig）。
 */
@RestController
@RequestMapping("/api/v1/care-recipients")
@RequiredArgsConstructor
@Slf4j
public class CareRecipientController {

    private final ElderProfileService service;
    private final ObservationSettingsService observationSettingsService;

    /** Spec §7：GET /api/v1/care-recipients/{careRecipientId}。 */
    @GetMapping("/{careRecipientId}")
    public ResponseEntity<ElderProfileResponse> getRecipient(@PathVariable Long careRecipientId) {
        log.info("查詢 care recipient profile careRecipientId={}", careRecipientId);
        return ResponseEntity.ok(service.getProfile(careRecipientId));
    }

    /** Spec §7：GET /api/v1/care-recipients/{careRecipientId}/contacts。 */
    @GetMapping("/{careRecipientId}/contacts")
    public ResponseEntity<CareRecipientContactsResponse> getContacts(@PathVariable Long careRecipientId) {
        log.info("查詢 care recipient contacts careRecipientId={}", careRecipientId);
        ElderContactsResponse legacy = service.getContacts(careRecipientId);
        return ResponseEntity.ok(new CareRecipientContactsResponse(careRecipientId, legacy.contacts()));
    }

    /** spec §3.4 等同：近期事件清單。Field-neutral DTO 直接重用。 */
    @GetMapping("/{careRecipientId}/events")
    public ResponseEntity<List<ElderEventItem>> getEvents(
            @PathVariable Long careRecipientId,
            @RequestParam(defaultValue = "20") int limit) {
        log.info("查詢 care recipient recent events careRecipientId={} limit={}", careRecipientId, limit);
        return ResponseEntity.ok(service.getRecentEvents(careRecipientId, limit));
    }

    /** Spec §7 / Gap C：GET observation settings；無資料時 service 層回 MVP 預設。 */
    @GetMapping("/{careRecipientId}/observation-settings")
    public ResponseEntity<ObservationSettingsResponse> getObservationSettings(
            @PathVariable Long careRecipientId) {
        log.info("查詢 observation settings careRecipientId={}", careRecipientId);
        return ResponseEntity.ok(observationSettingsService.get(careRecipientId));
    }

    /** Spec §7 / Gap C：PUT observation settings（upsert，partial update）。 */
    @PutMapping("/{careRecipientId}/observation-settings")
    public ResponseEntity<ObservationSettingsResponse> putObservationSettings(
            @PathVariable Long careRecipientId,
            @Valid @RequestBody UpdateObservationSettingsRequest body) {
        log.info("更新 observation settings careRecipientId={}", careRecipientId);
        return ResponseEntity.ok(observationSettingsService.upsert(careRecipientId, body));
    }
}
