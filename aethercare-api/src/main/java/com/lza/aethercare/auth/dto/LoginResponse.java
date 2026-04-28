package com.lza.aethercare.auth.dto;

import java.util.Set;

/** Login response：JWT token + 過期秒數 + 使用者資訊。 */
public record LoginResponse(
        String token,
        long expiresInSeconds,
        Long userId,
        String username,
        Set<String> roles
) {
}
