package com.lza.aethercare.notification.line.controller;

import com.lza.aethercare.common.security.AppUserDetails;
import com.lza.aethercare.common.security.CurrentUser;
import com.lza.aethercare.notification.line.entity.CaregiverLineBinding;
import com.lza.aethercare.notification.line.service.LineBindingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Spec § Master §0：caregiver 端 LINE 綁定 endpoints。
 *
 * <p>SecurityConfig 已將 {@code /api/v1/caregiver/**} 限 USER role；caregiver_id
 * 從 {@link AppUserDetails#getId()} 取，避免 IDOR。
 */
@RestController
@RequestMapping("/api/v1/caregiver/line-binding")
@RequiredArgsConstructor
@Slf4j
public class CaregiverLineBindingController {

    private final LineBindingService service;

    /** 產生綁定碼。前端拿到後顯示給 caregiver，caregiver 再透過 LINE 私訊 OA。 */
    @PostMapping("/start")
    public ResponseEntity<StartBindingResponse> start(@CurrentUser AppUserDetails user) {
        LineBindingService.StartResult r = service.startBinding(user.getId(), user.getTenantId());
        log.info("LineBinding start caregiverId={} expiresAt={}", user.getId(), r.expiresAt());
        return ResponseEntity.ok(new StartBindingResponse(
                r.code(), r.expiresAt(), r.ttlMinutes()));
    }

    /** 查當前綁定狀態，前端輪詢用。 */
    @GetMapping
    public ResponseEntity<BindingStatusResponse> status(@CurrentUser AppUserDetails user) {
        Optional<CaregiverLineBinding> b = service.findBinding(user.getId());
        if (b.isEmpty()) {
            return ResponseEntity.ok(new BindingStatusResponse(false, null, null, null));
        }
        CaregiverLineBinding v = b.get();
        return ResponseEntity.ok(new BindingStatusResponse(
                true, v.getLineUserId(), v.getLineDisplayName(), v.getBoundAt()));
    }

    /** 解綁；之後若 caregiver 重新綁需要再走 start flow。 */
    @DeleteMapping
    public ResponseEntity<Void> unbind(@CurrentUser AppUserDetails user) {
        boolean removed = service.unbind(user.getId());
        log.info("LineBinding unbind caregiverId={} removed={}", user.getId(), removed);
        return removed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    public record StartBindingResponse(String code, OffsetDateTime expiresAt, int ttlMinutes) {}

    public record BindingStatusResponse(
            boolean bound, String lineUserId, String lineDisplayName, OffsetDateTime boundAt) {}
}
