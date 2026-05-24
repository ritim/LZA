package com.lza.aethercare.notification.line;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * LINE Messaging API 設定。
 *
 * <p>{@code enabled=true} 且 {@code channelAccessToken} 非空時，{@link LineMessagingClient}
 * 才會被建立並啟用 push。{@code testUserIds} 是 dev 階段廣播對象；production 應改成從
 * caregiver 綁定資料（webhook follow event 收 userId）讀取。
 */
@ConfigurationProperties(prefix = "aethercare.line")
public record LineProperties(
        boolean enabled,
        String channelSecret,
        String channelAccessToken,
        List<String> testUserIds
) {
}
