package com.lza.aethercare.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JwtService 單元測試：簽發 / 解析 / 過期 / 無效簽章 / jti uniqueness。
 * <p>不啟 Spring context，手動 new + 呼叫 init() 觸發 signingKey 初始化。
 */
class JwtServiceTest {

    // base64(32+ bytes)；HS256 至少 256-bit key
    private static final String SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdW5pdC10ZXN0LWxvbmctZW5vdWdoLXBsZWFzZQ==";
    private static final long EXPIRY_1H = 3600L;

    private JwtService service;

    @BeforeEach
    void setUp() {
        service = new JwtService(SECRET, EXPIRY_1H);
        service.init();
    }

    private AppUserDetails sampleUser() {
        return AppUserDetails.fromToken(42L, "alice", Set.of("USER", "ADMIN"));
    }

    @Test
    void should_roundtrip_generate_then_parse() {
        String token = service.generate(sampleUser());
        Optional<JwtService.DecodedJwt> decoded = service.parse(token);

        assertThat(decoded).isPresent();
        assertThat(decoded.get().userId()).isEqualTo(42L);
        assertThat(decoded.get().username()).isEqualTo("alice");
        assertThat(decoded.get().roles()).containsExactlyInAnyOrder("USER", "ADMIN");
    }

    @Test
    void should_return_empty_when_signature_invalid() {
        String token = service.generate(sampleUser());
        // 改最後一個字元 → 簽章驗證失敗
        String tampered = token.substring(0, token.length() - 1)
                + (token.endsWith("A") ? "B" : "A");

        Optional<JwtService.DecodedJwt> decoded = service.parse(tampered);
        assertThat(decoded).isEmpty();
    }

    @Test
    void should_return_empty_when_token_malformed() {
        assertThat(service.parse("not-a-jwt")).isEmpty();
        assertThat(service.parse("")).isEmpty();
        assertThat(service.parse("aaa.bbb.ccc")).isEmpty();
    }

    @Test
    void should_return_empty_when_token_expired() {
        // 用負 expiry 製造過期 token
        JwtService expiredService = new JwtService(SECRET, -10);
        expiredService.init();
        String expiredToken = expiredService.generate(sampleUser());

        // 用正常 service parse 也應失敗（簽章相同 secret，但 exp 已過）
        Optional<JwtService.DecodedJwt> decoded = service.parse(expiredToken);
        assertThat(decoded).isEmpty();
    }

    @Test
    void should_generate_unique_token_per_call_via_jti() {
        // 即使同 user 同秒簽兩次，因 jti=UUID 確保 token 不同
        String token1 = service.generate(sampleUser());
        String token2 = service.generate(sampleUser());
        assertThat(token1).isNotEqualTo(token2);
    }

    @Test
    void should_expose_expiry_seconds() {
        assertThat(service.getExpirySeconds()).isEqualTo(EXPIRY_1H);
    }
}
