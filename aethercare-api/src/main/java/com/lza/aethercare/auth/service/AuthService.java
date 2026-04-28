package com.lza.aethercare.auth.service;

import com.lza.aethercare.auth.dto.LoginRequest;
import com.lza.aethercare.auth.dto.LoginResponse;
import com.lza.aethercare.common.security.AppUserDetails;
import com.lza.aethercare.common.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/** Auth service：以 AuthenticationManager 驗證帳密並簽發 JWT。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public LoginResponse login(LoginRequest req) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));
        AppUserDetails user = (AppUserDetails) auth.getPrincipal();
        String token = jwtService.generate(user);
        log.info("使用者登入成功 username={} roles={}", user.getUsername(), user.getRoles());
        return new LoginResponse(
                token,
                jwtService.getExpirySeconds(),
                user.getId(),
                user.getUsername(),
                user.getRoles());
    }
}
