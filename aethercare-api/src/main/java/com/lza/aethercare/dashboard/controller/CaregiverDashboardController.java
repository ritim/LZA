package com.lza.aethercare.dashboard.controller;

import com.lza.aethercare.common.security.AppUserDetails;
import com.lza.aethercare.common.security.CurrentUser;
import com.lza.aethercare.dashboard.dto.DashboardResponse;
import com.lza.aethercare.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Caregiver 入口儀表板：spec §3.1 / §6.1。
 *
 * <p>由 SecurityConfig {@code /api/v1/caregiver/**} 規則限定 USER role。
 * Caregiver 身份從 SecurityContext 拿（{@link CurrentUser}），spec 提的 X-Caregiver-Id
 * header 在 JWT 取代下不再使用。
 */
@RestController
@RequestMapping("/api/v1/caregiver")
@RequiredArgsConstructor
@Slf4j
public class CaregiverDashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> dashboard(@CurrentUser AppUserDetails user) {
        Long caregiverId = user != null ? user.getId() : null;
        log.info("查詢 caregiver dashboard caregiverId={}", caregiverId);
        return ResponseEntity.ok(dashboardService.buildFor(caregiverId));
    }
}
