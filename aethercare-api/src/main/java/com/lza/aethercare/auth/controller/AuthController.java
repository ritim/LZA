package com.lza.aethercare.auth.controller;

import com.lza.aethercare.auth.dto.LoginRequest;
import com.lza.aethercare.auth.dto.LoginResponse;
import com.lza.aethercare.auth.dto.LogoutRequest;
import com.lza.aethercare.auth.dto.RefreshRequest;
import com.lza.aethercare.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Auth REST：暴露 /login /refresh /logout，由 AuthService 處理 access + refresh token 流程。 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req,
                                               HttpServletRequest httpReq) {
        return ResponseEntity.ok(authService.login(req, httpReq.getHeader("User-Agent"), httpReq.getRemoteAddr()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest req,
                                                 HttpServletRequest httpReq) {
        return ResponseEntity.ok(
                authService.refresh(req.getRefreshToken(), httpReq.getHeader("User-Agent"), httpReq.getRemoteAddr()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest req) {
        authService.logout(req.getRefreshToken());
        return ResponseEntity.noContent().build();
    }
}
