package com.lza.aethercare.aichat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lza.aethercare.action.repository.CareActionRepository;
import com.lza.aethercare.ai.knowledge.CareKnowledge;
import com.lza.aethercare.ai.knowledge.CareKnowledgeBase;
import com.lza.aethercare.ai.service.EventTypeMapper;
import com.lza.aethercare.aichat.dto.AiCareChatRequest;
import com.lza.aethercare.aichat.dto.AiCareChatResponse;
import com.lza.aethercare.aichat.dto.AiChatHistoryResponse;
import com.lza.aethercare.aichat.dto.AiChatHistoryResponse.AiChatMessageItem;
import com.lza.aethercare.aichat.entity.AiChatMessage;
import com.lza.aethercare.aichat.enums.ChatRole;
import com.lza.aethercare.aichat.enums.ChatSource;
import com.lza.aethercare.aichat.repository.AiChatMessageRepository;
import com.lza.aethercare.aichat.rules.AiCareChatContext;
import com.lza.aethercare.aichat.rules.AiCareChatReply;
import com.lza.aethercare.aichat.rules.AiCareChatRulesEngine;
import com.lza.aethercare.audit.enums.CareAuditAction;
import com.lza.aethercare.audit.service.CareAuditService;
import com.lza.aethercare.common.error.BusinessException;
import com.lza.aethercare.common.error.ErrorCode;
import com.lza.aethercare.common.time.Clock;
import com.lza.aethercare.event.entity.CareEvent;
import com.lza.aethercare.event.service.CareEventService;
import com.lza.aethercare.tenant.context.TenantContext;
import com.lza.aethercare.task.entity.CareTask;
import com.lza.aethercare.task.repository.CareTaskRepository;
import com.lza.aethercare.workflow.entity.CareWorkflowInstance;
import com.lza.aethercare.workflow.service.CareWorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Spec § AI_Care_Chat：AI Care Chat 對話 service。
 *
 * <p>核心流程（{@link #sendMessage}）：
 * <ol>
 *   <li>驗事件 / workflow 存在</li>
 *   <li>確保 workflow 有「首訊息」（沒有則先建一個 STATIC_GUIDANCE assistant message + 首啟 audit）</li>
 *   <li>儲存 caregiver 輸入為 USER message</li>
 *   <li>跑 deterministic rules engine 產生結構化回覆</li>
 *   <li>儲存 ASSISTANT message（含 structured_json）</li>
 *   <li>寫 audit：AI_CHAT_MESSAGE_CREATED + AI_CHAT_SUGGESTED_ACTIONS</li>
 *   <li>回 response（{@link AiCareChatResponse}）</li>
 * </ol>
 *
 * <p>Spec § AI_Care_Chat §9：本 service 永遠不直接改 workflow / task state；
 * suggested actions 只是按鈕建議，前端 click 仍要走 workflow action API + caregiver 確認。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiCareChatService {

    private final AiChatMessageRepository chatRepo;
    private final CareEventService careEventService;
    private final CareWorkflowService workflowService;
    private final CareTaskRepository taskRepo;
    private final CareActionRepository actionRepo;
    private final CareKnowledgeBase knowledgeBase;
    private final EventTypeMapper eventTypeMapper;
    private final AiCareChatRulesEngine rules;
    private final CareAuditService auditService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public AiCareChatResponse sendMessage(Long actorUserId, AiCareChatRequest req) {
        CareEvent event = careEventService.findById(req.getCareEventId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "event=" + req.getCareEventId()));
        CareWorkflowInstance workflow = workflowService.findById(req.getWorkflowId());
        if (!workflow.getEventId().equals(event.getId())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "workflowId 與 careEventId 不對應");
        }

        ensureFirstMessage(actorUserId, event, workflow);

        // 1) caregiver 訊息入庫
        AiChatMessage userMsg = persistMessage(workflow, event, req.getTaskId(), actorUserId,
                ChatRole.USER, ChatSource.CAREGIVER_INPUT, req.getMessage(), null);

        // 2) rules engine 產回覆
        AiCareChatContext ctx = buildContext(event, workflow, req.getTaskId(), req.getMessage());
        AiCareChatReply reply = rules.evaluate(ctx);

        // 3) ASSISTANT 訊息入庫，structured_json 存 questions / suggestedActions / dangerSigns
        String structuredJson = serializeStructured(reply);
        AiChatMessage assistantMsg = persistMessage(workflow, event, req.getTaskId(), null,
                ChatRole.ASSISTANT, ChatSource.RULE_ENGINE, reply.reply(), structuredJson);

        // 4) audit
        auditService.log(workflow.getId(), event.getId(), actorUserId,
                CareAuditAction.AI_CHAT_MESSAGE_CREATED,
                "userMsg=" + userMsg.getId() + " assistantMsg=" + assistantMsg.getId());
        if (!reply.suggestedActions().isEmpty()) {
            auditService.log(workflow.getId(), event.getId(), actorUserId,
                    CareAuditAction.AI_CHAT_SUGGESTED_ACTIONS,
                    "actions=" + reply.suggestedActions().stream().map(a -> a.type()).toList());
        }

        return new AiCareChatResponse(
                assistantMsg.getId(),
                workflow.getId(),
                event.getId(),
                reply.reply(),
                reply.questions(),
                reply.suggestedActions(),
                reply.dangerSigns(),
                reply.disclaimer(),
                assistantMsg.getCreatedAt());
    }

    /**
     * Spec § AI_Care_Chat §3：caregiver 開啟事件時自動產生 first ASSISTANT message。
     * 因此 GET history 的 happy-path 需要寫資料，不能 readOnly。
     */
    @Transactional
    public AiChatHistoryResponse getHistory(Long workflowId, Long actorUserId) {
        CareWorkflowInstance workflow = workflowService.findById(workflowId);
        if (!chatRepo.existsByWorkflowId(workflowId)) {
            CareEvent event = careEventService.findById(workflow.getEventId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                            "event=" + workflow.getEventId()));
            ensureFirstMessage(actorUserId, event, workflow);
        }
        List<AiChatMessageItem> items = chatRepo.findByWorkflowIdOrderByCreatedAtAsc(workflowId).stream()
                .map(m -> new AiChatMessageItem(
                        m.getId(), m.getRole(), m.getSource(), m.getMessage(),
                        m.getStructuredJson(), m.getCreatedAt()))
                .toList();
        return new AiChatHistoryResponse(workflowId, items);
    }

    private void ensureFirstMessage(Long actorUserId, CareEvent event, CareWorkflowInstance workflow) {
        if (chatRepo.existsByWorkflowId(workflow.getId())) return;

        AiCareChatContext ctx = buildContext(event, workflow, null, null);
        AiCareChatReply firstReply = rules.firstMessage(ctx);

        AiChatMessage first = persistMessage(workflow, event, null, null,
                ChatRole.ASSISTANT, ChatSource.STATIC_GUIDANCE, firstReply.reply(),
                serializeStructured(firstReply));
        auditService.log(workflow.getId(), event.getId(), actorUserId,
                CareAuditAction.AI_CHAT_STARTED, "firstMessageId=" + first.getId());
    }

    private AiCareChatContext buildContext(CareEvent event, CareWorkflowInstance workflow,
                                           Long taskId, String message) {
        Optional<CareTask> task = taskId == null ? Optional.empty() : taskRepo.findById(taskId);
        Optional<CareKnowledge> knowledge = knowledgeBase.lookup(eventTypeMapper.from(event.getEventType()));
        List<String> priorActions = actionRepo.findByWorkflowIdOrderByCreatedAtAsc(workflow.getId()).stream()
                .map(a -> a.getActionType() == null ? "" : a.getActionType().name())
                .toList();
        return new AiCareChatContext(event, workflow, task, knowledge, priorActions, message);
    }

    private AiChatMessage persistMessage(CareWorkflowInstance workflow, CareEvent event, Long taskId,
                                         Long actorUserId, ChatRole role, ChatSource source,
                                         String text, String structuredJson) {
        AiChatMessage m = AiChatMessage.builder()
                .tenantId(TenantContext.getOrDefault())
                .workflowId(workflow.getId())
                .careEventId(event.getId())
                .taskId(taskId)
                .actorUserId(actorUserId)
                .role(role)
                .source(source)
                .message(text)
                .structuredJson(structuredJson)
                .createdAt(clock.now())
                .build();
        return chatRepo.save(m);
    }

    private String serializeStructured(AiCareChatReply reply) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("questions", reply.questions());
        payload.put("suggestedActions", reply.suggestedActions());
        payload.put("dangerSigns", reply.dangerSigns());
        payload.put("disclaimer", reply.disclaimer());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("serialize chat structured payload 失敗：{}", e.getMessage());
            return "{}";
        }
    }
}
