package com.lza.aethercare.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Logout：撤銷單一 refresh token 的請求 DTO。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogoutRequest {

    @NotBlank
    private String refreshToken;
}
