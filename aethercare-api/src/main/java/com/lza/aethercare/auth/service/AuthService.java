package com.lza.aethercare.auth.service;

import com.lza.aethercare.auth.dto.LoginRequest;
import com.lza.aethercare.auth.dto.LoginResponse;
import com.lza.aethercare.auth.service.RefreshTokenService.IssuedToken;
import com.lza.aethercare.auth.service.RefreshTokenService.RotationResult;
import com.lza.aethercare.common.security.AppUserDetails;
import com.lza.aethercare.common.security.JwtService;
import com.lza.aethercare.common.time.Clock;
import com.lza.aethercare.common.util.PiiMasker;
import com.lza.aethercare.userprofile.entity.AppUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Duration;

/** Auth service：以 AuthenticationManager 驗證帳密 + 簽發 JWT access + refresh token。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock;

    /** 帳密登入：簽 access token + refresh token，並把 UA / IP 存進 refresh_token 供稽核。 */
    public LoginResponse login(LoginRequest req, String userAgent, String ipAddress) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));
        AppUserDetails user = (AppUserDetails) auth.getPrincipal();
        String accessToken = jwtService.generate(user);
        IssuedToken refresh = refreshTokenService.issueFor(user.getId(), userAgent, ipAddress);
        log.info("使用者登入成功 username={} roles={} tenantId={}",
                PiiMasker.maskUsername(user.getUsername()), user.getRoles(), user.getTenantId());
        return buildResponse(user.getId(), user.getUsername(), user.getRoles(),
                user.getTenantId(), accessToken, refresh);
    }

    /** Refresh：驗證 refresh token → rotate → 簽新 access；若已 revoked 觸發 reuse detection。 */
    public LoginResponse refresh(String rawRefreshToken, String userAgent, String ipAddress) {
        RotationResult result = refreshTokenService.rotate(rawRefreshToken, userAgent, ipAddress);
        AppUser user = result.user();
        AppUserDetails details = AppUserDetails.fromToken(
                user.getId(), user.getUsername(), user.getRoles(), user.getTenantId());
        String accessToken = jwtService.generate(details);
        log.info("refresh token 輪轉成功 userId={}", PiiMasker.maskId(user.getId()));
        return buildResponse(user.getId(), user.getUsername(), user.getRoles(),
                user.getTenantId(), accessToken, result.newToken());
    }

    /** Logout：撤銷單一 refresh token；找不到視為 idempotent 成功。 */
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    private LoginResponse buildResponse(Long userId, String username, java.util.Set<String> roles,
                                        Long tenantId, String accessToken, IssuedToken refresh) {
        long refreshExpiresIn = Math.max(0,
                Duration.between(clock.now(), refresh.expiresAt()).toSeconds());
        return new LoginResponse(
                accessToken,
                jwtService.getExpirySeconds(),
                refresh.rawToken(),
                refreshExpiresIn,
                userId,
                username,
                roles,
                tenantId);
    }
}
