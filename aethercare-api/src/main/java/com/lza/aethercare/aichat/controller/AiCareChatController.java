package com.lza.aethercare.aichat.controller;

import com.lza.aethercare.aichat.dto.AiCareChatRequest;
import com.lza.aethercare.aichat.dto.AiCareChatResponse;
import com.lza.aethercare.aichat.dto.AiChatHistoryResponse;
import com.lza.aethercare.aichat.service.AiCareChatService;
import com.lza.aethercare.common.security.AppUserDetails;
import com.lza.aethercare.common.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spec § AI_Care_Chat §4：AI Care Chat REST controller。
 *
 * <p>受 USER role 保護（SecurityConfig 涵蓋 {@code /api/v1/ai/**} 與 {@code /api/v1/workflows/**}）。
 * Caregiver 身份從 SecurityContext 取（{@link CurrentUser}）。
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class AiCareChatController {

    private final AiCareChatService service;

    /** Spec § AI_Care_Chat §4：送 caregiver 訊息並取結構化 AI 回覆。 */
    @PostMapping("/api/v1/ai/care-chat")
    public ResponseEntity<AiCareChatResponse> sendMessage(
            @CurrentUser AppUserDetails user,
            @Valid @RequestBody AiCareChatRequest req) {
        Long actorId = user != null ? user.getId() : null;
        log.info("AI care-chat caregiver={} eventId={} workflowId={}",
                actorId, req.getCareEventId(), req.getWorkflowId());
        return ResponseEntity.ok(service.sendMessage(actorId, req));
    }

    /** Spec § AI_Care_Chat §4 / §3：載入 workflow chat 歷史；首次開啟自動產生開場 ASSISTANT 訊息。 */
    @GetMapping("/api/v1/workflows/{workflowId}/ai-messages")
    public ResponseEntity<AiChatHistoryResponse> history(
            @CurrentUser AppUserDetails user,
            @PathVariable Long workflowId) {
        Long actorId = user != null ? user.getId() : null;
        log.info("AI care-chat history workflowId={} caregiver={}", workflowId, actorId);
        return ResponseEntity.ok(service.getHistory(workflowId, actorId));
    }
}
