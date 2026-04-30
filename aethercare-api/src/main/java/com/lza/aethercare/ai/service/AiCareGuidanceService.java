package com.lza.aethercare.ai.service;

import com.lza.aethercare.ai.dto.CareGuidanceResponse;
import com.lza.aethercare.ai.enums.KnowledgeEventType;
import com.lza.aethercare.ai.knowledge.CareKnowledge;
import com.lza.aethercare.ai.knowledge.CareKnowledgeBase;
import com.lza.aethercare.ai.llm.LlmProvider;
import com.lza.aethercare.common.error.BusinessException;
import com.lza.aethercare.common.error.ErrorCode;
import com.lza.aethercare.common.time.Clock;
import com.lza.aethercare.event.entity.CareEvent;
import com.lza.aethercare.event.service.CareEventService;
import com.lza.aethercare.workflow.service.CareWorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 照護指引 service：撈 event + workflow（套 tenant 隔離）→ map 到 knowledge type
 * → 透過 {@link LlmProvider} 取得 {@link CareKnowledge} → 包成 response。
 *
 * <p>不直接觸 DB，所有 entity 撈取都透過既有 service，靠 v1.0-rc1 的 tenant filter 隔離。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiCareGuidanceService {

    private final CareEventService careEventService;
    private final CareWorkflowService workflowService;
    private final CareKnowledgeBase knowledgeBase;
    private final EventTypeMapper mapper;
    private final LlmProvider llmProvider;
    private final Clock clock;

    @Transactional(readOnly = true)
    public CareGuidanceResponse generate(Long eventId, Long workflowId) {
        CareEvent event = careEventService.findById(eventId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "event=" + eventId));
        // tenant 隔離由 CareWorkflowService.findById 內建偽 NOT_FOUND
        workflowService.findById(workflowId);

        KnowledgeEventType ktype = mapper.from(event.getEventType());
        CareKnowledge ck = llmProvider.generate(event, ktype, knowledgeBase)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "AI guidance unavailable for " + event.getEventType()));

        return new CareGuidanceResponse(
                ck.summary(),
                ck.guidance(),
                ck.questions(),
                ck.suggestedActions(),
                ck.dangerSigns(),
                ck.disclaimer(),
                clock.now()
        );
    }
}
