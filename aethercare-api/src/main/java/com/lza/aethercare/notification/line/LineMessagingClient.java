package com.lza.aethercare.notification.line;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Spec § Master §0：LINE Messaging API push client。
 *
 * <p>呼叫 {@code POST https://api.line.me/v2/bot/message/push}，每次傳純文字訊息。
 * 失敗只 log warn，不 throw — 通知是補強層，不可阻塞主流程
 * （spec §0「無回應 ≠ 安全」反向：通知派送失敗也不該影響 check-in 寫入或 workflow）。
 */
@Component
@ConditionalOnProperty(prefix = "aethercare.line", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class LineMessagingClient {

    private static final String PUSH_URL = "https://api.line.me/v2/bot/message/push";
    private static final String REPLY_URL = "https://api.line.me/v2/bot/message/reply";
    private static final String PROFILE_URL = "https://api.line.me/v2/bot/profile/";

    private final LineProperties props;
    private RestClient http;

    @PostConstruct
    void init() {
        if (props.channelAccessToken() == null || props.channelAccessToken().isBlank()) {
            log.warn("LINE channel-access-token 為空，LineMessagingClient 不會發送 push。");
            return;
        }
        this.http = RestClient.builder()
                .defaultHeader("Authorization", "Bearer " + props.channelAccessToken())
                .defaultHeader("Content-Type", "application/json")
                .build();
        log.info("LineMessagingClient 已啟用，testUserIds={}",
                props.testUserIds() == null ? 0 : props.testUserIds().size());
    }

    public void pushText(String userId, String text) {
        if (http == null) return;
        if (userId == null || userId.isBlank()) return;
        try {
            Map<String, Object> body = Map.of(
                    "to", userId,
                    "messages", List.of(Map.of("type", "text", "text", text)));
            http.post()
                    .uri(PUSH_URL)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("LINE push 成功 user={}", maskUserId(userId));
        } catch (RuntimeException e) {
            log.warn("LINE push 失敗 user={} err={}", maskUserId(userId), e.toString());
        }
    }

    /** Push Flex Message bubble；{@code bubble} 為 Flex JSON 結構（Map）。 */
    public void pushFlex(String userId, String altText, Map<String, Object> bubble) {
        if (http == null || userId == null || userId.isBlank() || bubble == null) return;
        try {
            Map<String, Object> message = Map.of(
                    "type", "flex",
                    "altText", altText,
                    "contents", bubble);
            Map<String, Object> body = Map.of(
                    "to", userId,
                    "messages", List.of(message));
            http.post().uri(PUSH_URL).body(body).retrieve().toBodilessEntity();
            log.info("LINE flex push 成功 user={}", maskUserId(userId));
        } catch (RuntimeException e) {
            log.warn("LINE flex push 失敗 user={} err={}", maskUserId(userId), e.toString());
        }
    }

    /**
     * 用 webhook event 帶來的 {@code replyToken} 回覆使用者。
     * Reply token 一次性、30 秒內有效；過期或重複使用會 400。
     */
    public void replyText(String replyToken, String text) {
        if (http == null) return;
        if (replyToken == null || replyToken.isBlank()) return;
        try {
            Map<String, Object> body = Map.of(
                    "replyToken", replyToken,
                    "messages", List.of(Map.of("type", "text", "text", text)));
            http.post()
                    .uri(REPLY_URL)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("LINE reply 成功 replyToken={}", maskToken(replyToken));
        } catch (RuntimeException e) {
            log.warn("LINE reply 失敗 replyToken={} err={}", maskToken(replyToken), e.toString());
        }
    }

    /** 取 LINE 使用者顯示名稱；失敗回 empty（不阻塞主流程）。 */
    public Optional<String> fetchDisplayName(String userId) {
        if (http == null || userId == null || userId.isBlank()) return Optional.empty();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = http.get()
                    .uri(PROFILE_URL + userId)
                    .retrieve()
                    .body(Map.class);
            Object dn = resp == null ? null : resp.get("displayName");
            return dn instanceof String s && !s.isBlank() ? Optional.of(s) : Optional.empty();
        } catch (RuntimeException e) {
            log.warn("LINE fetch profile 失敗 user={} err={}", maskUserId(userId), e.toString());
            return Optional.empty();
        }
    }

    private static String maskUserId(String id) {
        if (id == null || id.length() < 8) return "***";
        return id.substring(0, 4) + "***" + id.substring(id.length() - 3);
    }

    private static String maskToken(String t) {
        if (t == null || t.length() < 8) return "***";
        return t.substring(0, 4) + "***";
    }
}
