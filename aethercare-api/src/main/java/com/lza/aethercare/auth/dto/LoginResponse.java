package com.lza.aethercare.auth.dto;

import java.util.Set;

/** Login / refresh response：JWT access token + refresh token + 過期秒數 + 使用者資訊（含 tenantId）。 */
public record LoginResponse(
        String accessToken,
        long accessExpiresIn,
        String refreshToken,
        long refreshExpiresIn,
        Long userId,
        String username,
        Set<String> roles,
        Long tenantId
) {
}
