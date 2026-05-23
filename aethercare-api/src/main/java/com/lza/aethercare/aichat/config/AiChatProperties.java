package com.lza.aethercare.aichat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Spec § AI_Care_Chat：AI Care Chat reply 文字生成設定。
 *
 * <p>{@code provider} 切換實作：
 * <ul>
 *   <li>{@code none}（預設） — rules engine 模板池產 reply（無外部依賴）</li>
 *   <li>{@code anthropic} — 呼叫 Anthropic Claude API，需 {@code apiKey}</li>
 *   <li>{@code ollama} — 呼叫本機 Ollama，需 {@code ollamaBaseUrl} / {@code ollamaModel}</li>
 * </ul>
 * Rules engine 仍負責所有結構化欄位（suggestedActions / dangerSigns / SLA 規則），
 * LLM 僅覆寫 reply 字串以貼合情境。
 */
@ConfigurationProperties(prefix = "aethercare.ai.chat")
public record AiChatProperties(
        String provider,
        String model,
        int maxTokens,
        long timeoutMs,
        String apiKey,
        String ollamaBaseUrl,
        String ollamaModel,
        int historyLimit
) {
}
