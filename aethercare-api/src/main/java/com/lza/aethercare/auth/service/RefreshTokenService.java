package com.lza.aethercare.auth.service;

import com.lza.aethercare.auth.entity.RefreshToken;
import com.lza.aethercare.auth.repository.RefreshTokenRepository;
import com.lza.aethercare.common.error.BusinessException;
import com.lza.aethercare.common.error.ErrorCode;
import com.lza.aethercare.common.time.Clock;
import com.lza.aethercare.common.util.PiiMasker;
import com.lza.aethercare.userprofile.entity.AppUser;
import com.lza.aethercare.userprofile.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Refresh token 簽發 / 輪轉 / 撤銷服務。
 * <p>
 * 流程：
 * <ul>
 *   <li>{@code issueFor}：產 32 bytes random → Base64URL 為 raw token；SHA-256 hash 後存 DB；
 *       raw token 只回給 caller，DB 不留 plaintext。</li>
 *   <li>{@code rotate}：每次刷新都簽新 token + revoke 舊的並串接 replaced_by_id；
 *       若收到已 revoked token → reuse detection，撤銷該 user 所有 active token。</li>
 *   <li>{@code revoke}：logout 路徑，hash 後找到並標 revoked，找不到視為 idempotent 成功。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private static final int TOKEN_BYTE_LENGTH = 32;

    private final RefreshTokenRepository refreshTokenRepo;
    private final AppUserRepository appUserRepo;
    private final RefreshTokenSecurityHandler securityHandler;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${aethercare.security.jwt.refresh.expiry-days:30}")
    private int refreshExpiryDays;

    /** 簽發新 refresh token；raw 只回給 caller，DB 存 SHA-256 hash。 */
    @Transactional
    public IssuedToken issueFor(Long userId, String userAgent, String ipAddress) {
        String raw = generateRawToken();
        String hash = sha256Hex(raw);
        OffsetDateTime now = clock.now();
        OffsetDateTime expiresAt = now.plusDays(refreshExpiryDays);

        RefreshToken entity = RefreshToken.builder()
                .userId(userId)
                .tokenHash(hash)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .revoked(false)
                .userAgent(truncate(userAgent, 500))
                .ipAddress(truncate(ipAddress, 50))
                .build();
        refreshTokenRepo.save(entity);

        return new IssuedToken(raw, expiresAt);
    }

    /**
     * 輪轉：驗證 raw token → 簽新 token → 標老 token revoked + replaced_by_id 指向新 token。
     * <p>
     * 若收到已 revoked token，視為 reuse → 撤銷該 user 所有 active token 並拋 401。
     */
    @Transactional
    public RotationResult rotate(String rawRefreshToken, String userAgent, String ipAddress) {
        String hash = sha256Hex(rawRefreshToken);
        Optional<RefreshToken> opt = refreshTokenRepo.findByTokenHash(hash);
        if (opt.isEmpty()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "refresh token not found");
        }
        RefreshToken existing = opt.get();
        OffsetDateTime now = clock.now();

        if (existing.isRevoked()) {
            // Reuse detection：整 user 所有 session 立即撤銷
            // 必須走 REQUIRES_NEW 獨立 transaction，否則下面的 throw 會 rollback 撤銷動作
            int revoked = securityHandler.revokeAllForUser(existing.getUserId(), now);
            log.warn("refresh token reuse detected userId={} revokedCount={}",
                    PiiMasker.maskId(existing.getUserId()), revoked);
            throw new BusinessException(ErrorCode.UNAUTHORIZED,
                    "refresh token reuse detected, all sessions revoked");
        }

        if (existing.getExpiresAt().isBefore(now)) {
            refreshTokenRepo.revokeIfActive(existing.getId(), now, null);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "refresh token expired");
        }

        // 簽新 token
        IssuedToken next = issueFor(existing.getUserId(), userAgent, ipAddress);

        // 取剛簽出 token 的 id 來串 replaced_by_id
        Long newId = refreshTokenRepo.findByTokenHash(sha256Hex(next.rawToken()))
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "newly issued refresh token missing"))
                .getId();
        int updated = refreshTokenRepo.revokeIfActive(existing.getId(), now, newId);
        if (updated == 0) {
            // 已被別的併發 rotate 動到，視為 reuse；同樣走獨立 transaction 撤銷全 session
            securityHandler.revokeAllForUser(existing.getUserId(), now);
            throw new BusinessException(ErrorCode.UNAUTHORIZED,
                    "refresh token reuse detected, all sessions revoked");
        }

        AppUser user = appUserRepo.findById(existing.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "user not found"));
        return new RotationResult(user, next);
    }

    /** logout 路徑：找不到視為 already revoked，靜默成功。 */
    @Transactional
    public void revoke(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        String hash = sha256Hex(rawRefreshToken);
        refreshTokenRepo.findByTokenHash(hash).ifPresent(token ->
                refreshTokenRepo.revokeIfActive(token.getId(), clock.now(), null));
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 → 64 字元 hex（lowercase）。 */
    static String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** 對外回傳的新簽 token：raw + expiresAt。 */
    public record IssuedToken(String rawToken, OffsetDateTime expiresAt) {}

    /** 輪轉結果：user + 新 token。 */
    public record RotationResult(AppUser user, IssuedToken newToken) {}
}
