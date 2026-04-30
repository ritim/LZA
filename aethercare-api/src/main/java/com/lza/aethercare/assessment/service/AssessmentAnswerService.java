package com.lza.aethercare.assessment.service;

import com.lza.aethercare.ai.dto.AssessmentQuestionDto;
import com.lza.aethercare.ai.knowledge.CareKnowledge;
import com.lza.aethercare.ai.knowledge.CareKnowledgeBase;
import com.lza.aethercare.ai.service.EventTypeMapper;
import com.lza.aethercare.assessment.dto.AnswerItem;
import com.lza.aethercare.assessment.dto.AssessmentAnswerRequest;
import com.lza.aethercare.assessment.dto.AssessmentAnswerResponse;
import com.lza.aethercare.assessment.dto.RiskReevaluation;
import com.lza.aethercare.assessment.entity.CareAssessmentAnswer;
import com.lza.aethercare.assessment.repository.CareAssessmentAnswerRepository;
import com.lza.aethercare.audit.enums.CareAuditAction;
import com.lza.aethercare.audit.service.CareAuditService;
import com.lza.aethercare.common.error.BusinessException;
import com.lza.aethercare.common.error.ErrorCode;
import com.lza.aethercare.common.time.Clock;
import com.lza.aethercare.event.entity.CareEvent;
import com.lza.aethercare.event.service.CareEventService;
import com.lza.aethercare.tenant.context.TenantContext;
import com.lza.aethercare.workflow.service.CareWorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Assessment 提交服務：保存每筆 answer、套 dangerAnswer 規則偵測、寫 audit log，
 * 並回傳 risk reevaluation。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssessmentAnswerService {

    private final CareAssessmentAnswerRepository repo;
    private final CareKnowledgeBase knowledgeBase;
    private final EventTypeMapper mapper;
    private final CareEventService careEventService;
    private final CareWorkflowService workflowService;
    private final CareAuditService auditService;
    private final Clock clock;

    @Transactional
    public AssessmentAnswerResponse submit(Long workflowId, Long actorId, AssessmentAnswerRequest req) {
        CareEvent event = careEventService.findById(req.getEventId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "event=" + req.getEventId()));
        // tenant 隔離由 CareWorkflowService.findById 內建偽 NOT_FOUND
        workflowService.findById(workflowId);

        Optional<CareKnowledge> ck = knowledgeBase.lookup(mapper.from(event.getEventType()));
        Map<String, Set<String>> dangerMap = buildDangerMap(ck);

        boolean anyDanger = false;
        for (AnswerItem item : req.getAnswers()) {
            boolean danger = dangerMap.getOrDefault(item.getQuestionId(), Set.of())
                    .contains(item.getAnswer());
            if (danger) {
                anyDanger = true;
            }
            CareAssessmentAnswer entity = CareAssessmentAnswer.builder()
                    .tenantId(TenantContext.getOrDefault())
                    .workflowId(workflowId)
                    .eventId(req.getEventId())
                    .taskId(req.getTaskId())
                    .caregiverId(actorId)
                    .questionId(item.getQuestionId())
                    .question(item.getQuestion())
                    .answer(item.getAnswer())
                    .dangerDetected(danger)
                    .createdAt(clock.now())
                    .build();
            repo.save(entity);
        }

        auditService.log(workflowId, req.getEventId(), actorId,
                CareAuditAction.ASSESSMENT_RECORDED,
                "已記錄 " + req.getAnswers().size() + " 筆評估答案 dangerDetected=" + anyDanger);

        String riskLevel = event.getRiskLevel() != null ? event.getRiskLevel().name() : "UNKNOWN";
        RiskReevaluation reeval = anyDanger
                ? new RiskReevaluation(riskLevel, true, "CALL_EMERGENCY",
                        "偵測到 danger answer；建議提高警覺並考慮聯絡緊急服務。")
                : new RiskReevaluation(riskLevel, false, null,
                        "目前未偵測到立即危險徵象，請依指引繼續確認。");

        return new AssessmentAnswerResponse(workflowId, req.getTaskId(), reeval, true);
    }

    /** 由 knowledge.questions 構造 questionId → dangerAnswer set 的查詢 map。 */
    private Map<String, Set<String>> buildDangerMap(Optional<CareKnowledge> ck) {
        if (ck.isEmpty()) {
            return Map.of();
        }
        List<AssessmentQuestionDto> questions = ck.get().questions();
        if (questions == null || questions.isEmpty()) {
            return Map.of();
        }
        Map<String, Set<String>> result = new HashMap<>();
        for (AssessmentQuestionDto q : questions) {
            List<String> danger = q.dangerAnswer();
            result.put(q.id(), danger == null ? Set.of() : new HashSet<>(danger));
        }
        return result;
    }
}
