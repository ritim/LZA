package com.lza.aethercare.workflow.dto;

import java.time.OffsetDateTime;

/**
 * SLA 總覽：對指定時間區間內的 workflow 狀態做彙總，供 dashboard / 保險證明使用。
 *
 * <p>Resolved rate = resolved / total；Escalation rate = 區間內任一次出現
 * TASK_ESCALATED 的 workflow 數 / total。
 *
 * <p>{@code avgFirstResponseSeconds}：task 從 created_at 到
 * {@code coalesce(acknowledged_at, completed_at)} 的平均秒數，
 * {@code avgResolveSeconds}：workflow 從 started_at 到 completed_at 的平均秒數，
 * 兩者皆可能為 {@code null}（區間內無資料）。
 */
public record SlaSummaryResponse(
        OffsetDateTime from,
        OffsetDateTime to,
        long totalWorkflows,
        long resolvedWorkflows,
        long unresolvedWorkflows,
        double resolvedRate,
        double escalationRate,
        Double avgFirstResponseSeconds,
        Double avgResolveSeconds
) {
}
