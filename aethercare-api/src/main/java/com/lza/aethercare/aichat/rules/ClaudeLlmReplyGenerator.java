package com.lza.aethercare.aichat.rules;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.lza.aethercare.aichat.config.AiChatProperties;
import com.lza.aethercare.aichat.enums.ChatSource;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Spec § AI_Care_Chat：用 Anthropic Claude API 生成 reply 文字。
 *
 * <p>Rules engine 仍負責 suggestedActions / dangerSigns / SLA 規則。LLM 只接收這些決策作為 prompt 輸入，
 * 並在 system prompt 明確禁止改寫安全護欄（不能宣稱絕對安全、不能改 SLA、不能取代醫療診斷）。
 *
 * <p>當外部依賴不可用（API 失敗、空回覆、缺金鑰）時，回傳 {@code baseReply.reply()}。
 * Spec §3 要求「無回應不等於安全」，所以 chat 永遠不能因 LLM 故障而靜默。
 */
@Component
@ConditionalOnProperty(prefix = "aethercare.ai.chat", name = "provider", havingValue = "anthropic")
@RequiredArgsConstructor
@Slf4j
public class ClaudeLlmReplyGenerator implements LlmReplyGenerator {

    private final AiChatProperties props;
    private AnthropicClient client;

    @PostConstruct
    void init() {
        if (props.apiKey() == null || props.apiKey().isBlank()) {
            log.warn("AI Care Chat 已啟用，但 ANTHROPIC_API_KEY 為空，reply 將沿用 rules engine 模板。");
            return;
        }
        this.client = AnthropicOkHttpClient.builder()
                .apiKey(props.apiKey())
                .build();
        log.info("ClaudeLlmReplyGenerator 已啟用，model={} maxTokens={}", props.model(), props.maxTokens());
    }

    @Override
    public Outcome generate(AiCareChatContext ctx, AiCareChatReply baseReply, List<ChatTurn> history) {
        if (client == null) {
            return new Outcome(baseReply.reply(), ChatSource.RULE_ENGINE);
        }
        try {
            String userPrompt = ChatPromptBuilder.buildUserPrompt(ctx, baseReply, history);
            MessageCreateParams params = MessageCreateParams.builder()
                    .model(props.model())
                    .maxTokens(props.maxTokens())
                    .system(ChatPromptBuilder.SYSTEM_PROMPT)
                    .addUserMessage(userPrompt)
                    .build();
            Message resp = client.messages().create(params);
            String text = resp.content().stream()
                    .flatMap(b -> b.text().stream())
                    .map(TextBlock::text)
                    .collect(Collectors.joining(""))
                    .trim();
            if (text.isBlank()) {
                log.warn("Claude reply 空字串；沿用 rules engine 模板。");
                return new Outcome(baseReply.reply(), ChatSource.RULE_ENGINE);
            }
            return new Outcome(text, ChatSource.LLM);
        } catch (RuntimeException e) {
            log.warn("Claude reply 失敗（{}）；沿用 rules engine 模板。", e.toString());
            return new Outcome(baseReply.reply(), ChatSource.RULE_ENGINE);
        }
    }

}
