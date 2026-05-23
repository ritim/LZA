package com.lza.aethercare.aichat.rules;

import com.lza.aethercare.aichat.config.AiChatProperties;
import com.lza.aethercare.aichat.enums.ChatSource;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Spec § AI_Care_Chat：用本機 Ollama 服務生成 reply 文字。
 *
 * <p>呼叫 {@code POST {ollamaBaseUrl}/api/chat}，沿用 {@link ChatPromptBuilder} 的 system / user prompt。
 * 與 Anthropic 路徑共用安全護欄與 fallback 邏輯：失敗或空回覆時回傳 {@code baseReply.reply()}，
 * source 標為 {@code RULE_ENGINE}。
 */
@Component
@ConditionalOnProperty(prefix = "aethercare.ai.chat", name = "provider", havingValue = "ollama")
@RequiredArgsConstructor
@Slf4j
public class OllamaLlmReplyGenerator implements LlmReplyGenerator {

    private final AiChatProperties props;
    private RestClient restClient;

    @PostConstruct
    void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Math.min(props.timeoutMs(), Integer.MAX_VALUE));
        factory.setReadTimeout((int) Math.min(props.timeoutMs(), Integer.MAX_VALUE));
        this.restClient = RestClient.builder()
                .baseUrl(props.ollamaBaseUrl())
                .requestFactory(factory)
                .build();
        log.info("OllamaLlmReplyGenerator 已啟用，baseUrl={} model={} timeoutMs={}",
                props.ollamaBaseUrl(), props.ollamaModel(), props.timeoutMs());
    }

    @Override
    public Outcome generate(AiCareChatContext ctx, AiCareChatReply baseReply, List<ChatTurn> history) {
        try {
            String userPrompt = ChatPromptBuilder.buildUserPrompt(ctx, baseReply, history);
            Map<String, Object> body = Map.of(
                    "model", props.ollamaModel(),
                    "messages", List.of(
                            Map.of("role", "system", "content", ChatPromptBuilder.SYSTEM_PROMPT),
                            Map.of("role", "user", "content", userPrompt)),
                    "stream", false,
                    "options", Map.of(
                            "num_predict", props.maxTokens(),
                            "temperature", 0.6));

            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restClient.post()
                    .uri("/api/chat")
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            String text = extractText(resp);
            if (text == null || text.isBlank()) {
                log.warn("Ollama reply 空字串；沿用 rules engine 模板。");
                return new Outcome(baseReply.reply(), ChatSource.RULE_ENGINE);
            }
            return new Outcome(text.trim(), ChatSource.LLM);
        } catch (RuntimeException e) {
            log.warn("Ollama reply 失敗（{}）；沿用 rules engine 模板。", e.toString());
            return new Outcome(baseReply.reply(), ChatSource.RULE_ENGINE);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> resp) {
        if (resp == null) return null;
        Object msg = resp.get("message");
        if (msg instanceof Map<?, ?> m) {
            Object content = m.get("content");
            if (content instanceof String s) return s;
        }
        return null;
    }
}
