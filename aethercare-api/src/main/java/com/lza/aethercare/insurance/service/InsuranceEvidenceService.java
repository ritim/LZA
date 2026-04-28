package com.lza.aethercare.insurance.service;

import com.lza.aethercare.action.entity.CareAction;
import com.lza.aethercare.audit.entity.CareAuditLog;
import com.lza.aethercare.audit.enums.CareAuditAction;
import com.lza.aethercare.audit.service.CareAuditService;
import com.lza.aethercare.common.error.BusinessException;
import com.lza.aethercare.common.error.ErrorCode;
import com.lza.aethercare.event.entity.CareEvent;
import com.lza.aethercare.insurance.dto.InsuranceEvidenceResponse;
import com.lza.aethercare.task.entity.CareTask;
import com.lza.aethercare.workflow.entity.CareWorkflowInstance;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Insurance evidence 服務：依 elderId + 區間撈 events / workflows / tasks /
 * actions / audit log，組裝成 {@link InsuranceEvidenceResponse}。
 *
 * <p>每次 query 會寫一筆 {@code INSURANCE_QUERY} audit log（workflowId=null）
 * 以建立可稽核的查詢軌跡。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InsuranceEvidenceService {

    @PersistenceContext
    private EntityManager em;

    private final CareAuditService auditService;

    @Transactional(readOnly = true)
    public InsuranceEvidenceResponse query(Long elderId, OffsetDateTime from,
                                           OffsetDateTime to, Long actorId) {
        validate(elderId, from, to);

        @SuppressWarnings("unchecked")
        List<CareEvent> events = em.createQuery("""
                SELECT e FROM CareEvent e
                 WHERE e.elderId = :elderId
                   AND e.occurredAt >= :from AND e.occurredAt < :to
                 ORDER BY e.occurredAt ASC
                """)
                .setParameter("elderId", elderId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();

        @SuppressWarnings("unchecked")
        List<CareWorkflowInstance> workflows = em.createQuery("""
                SELECT w FROM CareWorkflowInstance w
                 WHERE w.elderId = :elderId
                   AND w.startedAt >= :from AND w.startedAt < :to
                 ORDER BY w.startedAt ASC
                """)
                .setParameter("elderId", elderId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();

        Map<Long, Long> taskCountByWorkflow = new HashMap<>();
        Map<Long, Long> actionCountByWorkflow = new HashMap<>();
        Map<Long, List<String>> auditChainByWorkflow = new HashMap<>();

        if (!workflows.isEmpty()) {
            List<Long> workflowIds = workflows.stream().map(CareWorkflowInstance::getId).toList();

            @SuppressWarnings("unchecked")
            List<Object[]> taskCounts = em.createQuery("""
                    SELECT t.workflowId, COUNT(t) FROM CareTask t
                     WHERE t.workflowId IN :ids
                     GROUP BY t.workflowId
                    """)
                    .setParameter("ids", workflowIds)
                    .getResultList();
            for (Object[] row : taskCounts) {
                taskCountByWorkflow.put((Long) row[0], (Long) row[1]);
            }

            @SuppressWarnings("unchecked")
            List<Object[]> actionCounts = em.createQuery("""
                    SELECT a.workflowId, COUNT(a) FROM CareAction a
                     WHERE a.workflowId IN :ids
                     GROUP BY a.workflowId
                    """)
                    .setParameter("ids", workflowIds)
                    .getResultList();
            for (Object[] row : actionCounts) {
                actionCountByWorkflow.put((Long) row[0], (Long) row[1]);
            }

            @SuppressWarnings("unchecked")
            List<CareAuditLog> auditRows = em.createQuery("""
                    SELECT a FROM CareAuditLog a
                     WHERE a.workflowId IN :ids
                     ORDER BY a.workflowId ASC, a.createdAt ASC
                    """)
                    .setParameter("ids", workflowIds)
                    .getResultList();
            for (CareAuditLog row : auditRows) {
                auditChainByWorkflow
                        .computeIfAbsent(row.getWorkflowId(), k -> new ArrayList<>())
                        .add(row.getAction().name());
            }
        }

        List<InsuranceEvidenceResponse.EventEvidence> eventEvidence = events.stream()
                .map(e -> new InsuranceEvidenceResponse.EventEvidence(
                        e.getId(),
                        e.getEventType().name(),
                        e.getRiskLevel() == null ? null : e.getRiskLevel().name(),
                        e.getOccurredAt()))
                .toList();

        List<InsuranceEvidenceResponse.WorkflowEvidence> workflowEvidence = workflows.stream()
                .map(w -> new InsuranceEvidenceResponse.WorkflowEvidence(
                        w.getId(),
                        w.getStatus().name(),
                        w.getCurrentLevel(),
                        w.getStartedAt(),
                        w.getCompletedAt(),
                        Math.toIntExact(taskCountByWorkflow.getOrDefault(w.getId(), 0L)),
                        Math.toIntExact(actionCountByWorkflow.getOrDefault(w.getId(), 0L)),
                        auditChainByWorkflow.getOrDefault(w.getId(), List.of())))
                .toList();

        // 寫 audit log：workflowId=null（系統級查詢，已由 0013 migration 改 nullable）
        auditService.log(null, null, actorId, CareAuditAction.INSURANCE_QUERY,
                String.format("evidence query elderId=%d from=%s to=%s events=%d workflows=%d",
                        elderId, from, to, events.size(), workflows.size()));

        return new InsuranceEvidenceResponse(
                elderId, from, to,
                events.size(), workflows.size(),
                eventEvidence, workflowEvidence);
    }

    private static void validate(Long elderId, OffsetDateTime from, OffsetDateTime to) {
        if (elderId == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "elderId 不可為 null");
        }
        if (from == null || to == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "from / to 不可為 null");
        }
        if (!from.isBefore(to)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "from 必須早於 to");
        }
    }

    /** 顯式宣告 unused import 不會被 IDE 移除（CareTask / CareAction 用於 JPQL entity ref）。 */
    @SuppressWarnings("unused")
    private static final Class<?>[] JPQL_ENTITY_REFS = { CareTask.class, CareAction.class };
}
