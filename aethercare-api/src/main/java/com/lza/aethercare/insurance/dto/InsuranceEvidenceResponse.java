package com.lza.aethercare.insurance.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 保險公司「照護證據」查詢回應：包裝 elder 在指定區間內的事件 / workflow /
 * 任務 / action / audit 鏈摘要。
 *
 * <p>不含原始 sensor metadata（隱私），只回傳 type / risk / 時間戳 / 計數，
 * 詳細權衡請見 runbook §5。
 */
public record InsuranceEvidenceResponse(
        Long elderId,
        OffsetDateTime from,
        OffsetDateTime to,
        int totalEvents,
        int totalWorkflows,
        List<EventEvidence> events,
        List<WorkflowEvidence> workflows
) {

    public record EventEvidence(
            Long eventId,
            String eventType,
            String riskLevel,
            OffsetDateTime occurredAt
    ) {
    }

    public record WorkflowEvidence(
            Long workflowId,
            String status,
            int currentLevel,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt,
            int taskCount,
            int actionCount,
            List<String> auditChain
    ) {
    }
}
