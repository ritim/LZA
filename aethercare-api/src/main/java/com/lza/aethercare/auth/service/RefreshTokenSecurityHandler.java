package com.lza.aethercare.auth.service;

import com.lza.aethercare.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Refresh token 安全處置 handler：reuse detection 必須在獨立 transaction 內
 * commit，不能被 RefreshTokenService.rotate 拋出的 BusinessException 觸發
 * outer transaction rollback 而失效。
 *
 * <p>拆獨立 bean 避免 self-invocation 繞過 Spring proxy 讓 REQUIRES_NEW 失效
 * （與 timeout scanner 同模式）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenSecurityHandler {

    private final RefreshTokenRepository refreshTokenRepo;

    /** 撤銷指定 user 所有 active refresh token；REQUIRES_NEW 確保獨立 commit。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeAllForUser(Long userId, OffsetDateTime now) {
        int revoked = refreshTokenRepo.revokeAllForUser(userId, now);
        log.warn("已撤銷 user 全部 refresh token: count={}", revoked);
        return revoked;
    }
}
