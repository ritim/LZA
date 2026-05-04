package com.lza.aethercare.aichat.dto;

import com.lza.aethercare.aichat.enums.ChatRole;
import com.lza.aethercare.aichat.enums.ChatSource;

import java.time.OffsetDateTime;
import java.util.List;

/** Spec § AI_Care_Chat §4：GET /api/v1/workflows/{workflowId}/ai-messages 回應。 */
public record AiChatHistoryResponse(
        Long workflowId,
        List<AiChatMessageItem> items
) {

    public record AiChatMessageItem(
            Long id,
            ChatRole role,
            ChatSource source,
            String message,
            String structuredJson,
            OffsetDateTime createdAt
    ) {
    }
}
