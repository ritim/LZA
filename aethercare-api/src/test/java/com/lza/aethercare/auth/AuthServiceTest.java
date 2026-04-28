package com.lza.aethercare.auth;

import com.lza.aethercare.auth.dto.LoginRequest;
import com.lza.aethercare.auth.dto.LoginResponse;
import com.lza.aethercare.auth.service.AuthService;
import com.lza.aethercare.auth.service.RefreshTokenService;
import com.lza.aethercare.auth.service.RefreshTokenService.IssuedToken;
import com.lza.aethercare.auth.service.RefreshTokenService.RotationResult;
import com.lza.aethercare.common.security.AppUserDetails;
import com.lza.aethercare.common.security.JwtService;
import com.lza.aethercare.common.time.Clock;
import com.lza.aethercare.userprofile.entity.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;

/** AuthService 單元測試：login / refresh / logout 三條路徑。 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    AuthenticationManager authenticationManager;
    @Mock
    JwtService jwtService;
    @Mock
    RefreshTokenService refreshTokenService;
    @Mock
    Clock clock;
    @Mock
    Authentication authentication;

    @InjectMocks
    AuthService service;

    private final OffsetDateTime now = OffsetDateTime.of(2026, 4, 28, 12, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        lenient().when(clock.now()).thenReturn(now);
    }

    private AppUserDetails sampleUser() {
        return AppUserDetails.fromToken(7L, "alice", Set.of("USER"));
    }

    @Test
    void login_should_return_access_and_refresh_token() {
        LoginRequest req = LoginRequest.builder().username("alice").password("pwd").build();
        AppUserDetails user = sampleUser();
        IssuedToken refresh = new IssuedToken("raw-refresh-1", now.plusDays(30));

        given(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .willReturn(authentication);
        given(authentication.getPrincipal()).willReturn(user);
        given(jwtService.generate(user)).willReturn("access.jwt.token");
        given(jwtService.getExpirySeconds()).willReturn(3600L);
        given(refreshTokenService.issueFor(7L, "ua", "1.2.3.4")).willReturn(refresh);

        LoginResponse resp = service.login(req, "ua", "1.2.3.4");

        assertThat(resp.accessToken()).isEqualTo("access.jwt.token");
        assertThat(resp.refreshToken()).isEqualTo("raw-refresh-1");
        assertThat(resp.accessExpiresIn()).isEqualTo(3600L);
        assertThat(resp.refreshExpiresIn()).isPositive();
        assertThat(resp.userId()).isEqualTo(7L);
        assertThat(resp.username()).isEqualTo("alice");
        assertThat(resp.roles()).containsExactly("USER");
    }

    @Test
    void login_should_propagate_authentication_exception_when_credentials_invalid() {
        LoginRequest req = LoginRequest.builder().username("alice").password("bad").build();
        given(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .willThrow(new BadCredentialsException("bad creds"));

        assertThatThrownBy(() -> service.login(req, null, null))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("bad creds");
    }

    @Test
    void refresh_should_rotate_then_issue_new_access_token() {
        AppUser user = AppUser.builder()
                .id(7L)
                .username("alice")
                .passwordHash("hash")
                .enabled(true)
                .roles(new HashSet<>(Set.of("USER")))
                .build();
        IssuedToken newRefresh = new IssuedToken("raw-refresh-2", now.plusDays(30));
        RotationResult rotation = new RotationResult(user, newRefresh);

        given(refreshTokenService.rotate("raw-refresh-1", "ua", "ip")).willReturn(rotation);
        given(jwtService.generate(any(AppUserDetails.class))).willReturn("new.access.jwt");
        given(jwtService.getExpirySeconds()).willReturn(3600L);

        LoginResponse resp = service.refresh("raw-refresh-1", "ua", "ip");

        assertThat(resp.accessToken()).isEqualTo("new.access.jwt");
        assertThat(resp.refreshToken()).isEqualTo("raw-refresh-2");
        assertThat(resp.userId()).isEqualTo(7L);
        then(refreshTokenService).should().rotate(eq("raw-refresh-1"), eq("ua"), eq("ip"));
    }

    @Test
    void logout_should_invoke_refresh_token_service_revoke() {
        service.logout("raw-refresh-1");
        then(refreshTokenService).should().revoke("raw-refresh-1");
    }
}
