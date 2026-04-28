package com.lza.aethercare.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** JWT 簽發 / 解析（HS256）。Token 內含 userId(sub) / username / roles / iat / exp。 */
@Service
@Slf4j
public class JwtService {

    private final String secret;
    private final long expirySeconds;
    private SecretKey signingKey;

    public JwtService(
            @Value("${aethercare.security.jwt.secret}") String secret,
            @Value("${aethercare.security.jwt.expiry-seconds:3600}") long expirySeconds) {
        this.secret = secret;
        this.expirySeconds = expirySeconds;
    }

    @PostConstruct
    void init() {
        // secret 必須是 base64 編碼，至少 32 bytes（256 bits）以符合 HS256 要求
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /** 簽發 token：sub=userId，並帶 username / roles / tenantId / exp。 */
    public String generate(AppUserDetails user) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(expirySeconds);
        return Jwts.builder()
                // jti 確保同 user 同秒簽出的 token 也不同（避免 access token rotation 後相同）
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("roles", user.getRoles())
                .claim("tenantId", user.getTenantId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /** 解析 + 驗證簽章 / 過期。失敗回 Optional.empty。 */
    public Optional<DecodedJwt> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long userId = Long.parseLong(claims.getSubject());
            String username = claims.get("username", String.class);
            Set<String> roles = extractRoles(claims.get("roles"));
            Long tenantId = extractTenantId(claims.get("tenantId"));
            Instant exp = claims.getExpiration().toInstant();
            return Optional.of(new DecodedJwt(userId, username, roles, tenantId, exp));
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT parse failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public long getExpirySeconds() {
        return expirySeconds;
    }

    @SuppressWarnings("unchecked")
    private Set<String> extractRoles(Object raw) {
        if (raw instanceof Collection<?> coll) {
            Set<String> out = new HashSet<>();
            for (Object o : coll) {
                out.add(String.valueOf(o));
            }
            return out;
        }
        return Set.of();
    }

    /** tenantId claim 解析；舊 token 缺少時回 default tenant id=1（向後相容）。 */
    private Long extractTenantId(Object raw) {
        if (raw == null) {
            return 1L;
        }
        if (raw instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return 1L;
        }
    }

    /** 解析後的 token 內容。 */
    public record DecodedJwt(Long userId, String username, Set<String> roles, Long tenantId, Instant expiresAt) {
        public List<String> rolesAsList() {
            return List.copyOf(roles);
        }
    }
}
