package com.lza.aethercare.aichat.rules;

import com.lza.aethercare.aichat.enums.ChatSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 當 {@code aethercare.ai.chat.provider=none}（或未設定）時啟用，
 * 直接使用 rules engine 模板池的 reply 字串。
 */
@Component
@ConditionalOnProperty(prefix = "aethercare.ai.chat", name = "provider",
        havingValue = "none", matchIfMissing = true)
public class TemplateLlmReplyGenerator implements LlmReplyGenerator {

    @Override
    public Outcome generate(AiCareChatContext ctx, AiCareChatReply baseReply, List<ChatTurn> history) {
        return new Outcome(baseReply.reply(), ChatSource.RULE_ENGINE);
    }
}
