package com.lza.aethercare.auth;

import com.lza.aethercare.auth.entity.RefreshToken;
import com.lza.aethercare.auth.repository.RefreshTokenRepository;
import com.lza.aethercare.auth.service.RefreshTokenSecurityHandler;
import com.lza.aethercare.auth.service.RefreshTokenService;
import com.lza.aethercare.auth.service.RefreshTokenService.IssuedToken;
import com.lza.aethercare.auth.service.RefreshTokenService.RotationResult;
import com.lza.aethercare.common.error.BusinessException;
import com.lza.aethercare.common.error.ErrorCode;
import com.lza.aethercare.common.time.Clock;
import com.lza.aethercare.userprofile.entity.AppUser;
import com.lza.aethercare.userprofile.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

/**
 * RefreshTokenService 單元測試：
 * <ol>
 *   <li>issueFor 回 raw token，DB 存 SHA-256 hash（兩者不等）</li>
 *   <li>rotate happy path：簽新 + 標舊 revoked + replaced_by_id 串接</li>
 *   <li>rotate reuse detection：碰到已 revoked → revokeAllForUser + 401</li>
 *   <li>rotate expired → revoke 自己 + 401</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    RefreshTokenRepository refreshTokenRepo;
    @Mock
    AppUserRepository appUserRepo;
    @Mock
    RefreshTokenSecurityHandler securityHandler;
    @Mock
    Clock clock;

    RefreshTokenService service;

    private final OffsetDateTime now = OffsetDateTime.of(2026, 4, 28, 12, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(refreshTokenRepo, appUserRepo, securityHandler, clock);
        ReflectionTestUtils.setField(service, "refreshExpiryDays", 30);
        lenient().when(clock.now()).thenReturn(now);
    }

    @Test
    void issueFor_should_return_raw_token_not_equal_to_stored_hash() {
        IssuedToken issued = service.issueFor(42L, "junit-ua", "127.0.0.1");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        then(refreshTokenRepo).should().save(captor.capture());

        RefreshToken saved = captor.getValue();
        assertThat(issued.rawToken()).isNotBlank();
        assertThat(saved.getTokenHash()).isNotBlank();
        assertThat(saved.getTokenHash()).isNotEqualTo(issued.rawToken());
        // hash 應該是 raw 的 SHA-256 hex
        assertThat(saved.getTokenHash()).isEqualTo(sha256Hex(issued.rawToken()));
        assertThat(saved.getTokenHash()).hasSize(64);
        assertThat(saved.getUserId()).isEqualTo(42L);
        assertThat(saved.getIssuedAt()).isEqualTo(now);
        assertThat(saved.getExpiresAt()).isEqualTo(now.plusDays(30));
        assertThat(saved.isRevoked()).isFalse();
        assertThat(saved.getUserAgent()).isEqualTo("junit-ua");
        assertThat(saved.getIpAddress()).isEqualTo("127.0.0.1");
    }

    @Test
    void rotate_happy_path_should_issue_new_and_revoke_old_with_replacement_chain() {
        String oldRaw = "old-raw-token";
        String oldHash = sha256Hex(oldRaw);
        RefreshToken oldToken = RefreshToken.builder()
                .id(10L)
                .userId(99L)
                .tokenHash(oldHash)
                .issuedAt(now.minusHours(1))
                .expiresAt(now.plusDays(29))
                .revoked(false)
                .build();
        AppUser user = AppUser.builder().id(99L).username("alice").roles(Set.of("USER")).build();

        given(refreshTokenRepo.findByTokenHash(oldHash)).willReturn(Optional.of(oldToken));
        // 第二次 findByTokenHash 撈剛簽出的新 token
        given(refreshTokenRepo.findByTokenHash(org.mockito.ArgumentMatchers.argThat(h -> !h.equals(oldHash))))
                .willAnswer(inv -> {
                    String h = inv.getArgument(0);
                    RefreshToken newOne = RefreshToken.builder()
                            .id(11L).userId(99L).tokenHash(h)
                            .issuedAt(now).expiresAt(now.plusDays(30)).revoked(false)
                            .build();
                    return Optional.of(newOne);
                });
        given(refreshTokenRepo.revokeIfActive(eq(10L), eq(now), eq(11L))).willReturn(1);
        given(appUserRepo.findById(99L)).willReturn(Optional.of(user));

        RotationResult result = service.rotate(oldRaw, "ua", "1.2.3.4");

        assertThat(result.user().getId()).isEqualTo(99L);
        assertThat(result.newToken().rawToken()).isNotEqualTo(oldRaw);
        then(refreshTokenRepo).should().revokeIfActive(eq(10L), eq(now), eq(11L));
        then(securityHandler).should(never()).revokeAllForUser(anyLong(), any());
    }

    @Test
    void rotate_revoked_token_should_trigger_reuse_detection() {
        String reusedRaw = "reused-raw";
        String reusedHash = sha256Hex(reusedRaw);
        RefreshToken revokedToken = RefreshToken.builder()
                .id(20L)
                .userId(7L)
                .tokenHash(reusedHash)
                .issuedAt(now.minusHours(2))
                .expiresAt(now.plusDays(29))
                .revoked(true)
                .revokedAt(now.minusHours(1))
                .build();

        given(refreshTokenRepo.findByTokenHash(reusedHash)).willReturn(Optional.of(revokedToken));
        given(securityHandler.revokeAllForUser(7L, now)).willReturn(3);

        assertThatThrownBy(() -> service.rotate(reusedRaw, "ua", "ip"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(be.getDetail()).contains("reuse detected");
                });
        then(securityHandler).should().revokeAllForUser(7L, now);
    }

    @Test
    void rotate_expired_token_should_revoke_and_throw_unauthorized() {
        String expiredRaw = "expired-raw";
        String expiredHash = sha256Hex(expiredRaw);
        RefreshToken expired = RefreshToken.builder()
                .id(30L)
                .userId(8L)
                .tokenHash(expiredHash)
                .issuedAt(now.minusDays(40))
                .expiresAt(now.minusDays(1))
                .revoked(false)
                .build();

        given(refreshTokenRepo.findByTokenHash(expiredHash)).willReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate(expiredRaw, "ua", "ip"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                    assertThat(be.getDetail()).contains("expired");
                });
        then(refreshTokenRepo).should().revokeIfActive(eq(30L), eq(now), eq(null));
        then(refreshTokenRepo).should(never()).save(any());
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
