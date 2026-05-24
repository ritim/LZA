package com.lza.aethercare.dashboard.dto;

import com.lza.aethercare.event.enums.CareEventType;
import com.lza.aethercare.event.enums.RiskLevel;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Spec §6.1 Caregiver dashboard 回應。
 *
 * <p>{@code summary}：依 active workflow 風險聚合計數；
 * {@code activeEvents}：當前 caregiver 名下未結案任務對應的事件；
 * {@code recentTimeline}：最新 N 筆系統事件供首頁掃描。
 */
public record DashboardResponse(
        Summary summary,
        List<ActiveEventItem> activeEvents,
        List<TimelineItem> recentTimeline
) {

    /**
     * Spec § Gap H：MVP dashboard summary fields。
     *
     * <p>{@code normal/attention/alert} 為既有 v1 risk-bucket counts；新增欄位給前端做今日
     * SLA / check-in 一覽。資料 scope：caregiver 名下未結案事件對應的 recipient 集合。
     *
     * @param activeEventsCount      caregiver 名下未結案事件數
     * @param waitingResponseCount   其中狀態為 WAITING_RESPONSE 的 workflow 數
     * @param expiredTaskCount       SLA 已過期 task 數
     * @param resolvedTodayCount     今日已 RESOLVED workflow 總數（demo 全域，post-MVP 改 per-caregiver）
     * @param latestCheckInAt        caregiver 名下 recipient 集合的最新 CHECK_IN 時間，無則 null
     * @param latestActivityAt       同集合的最新任意活動時間，無則 null
     * @param nextEscalationDeadline 下一個將到期的 pending task deadline，無則 null
     */
    public record Summary(
            int normalCount,
            int attentionCount,
            int alertCount,
            int activeEventsCount,
            int waitingResponseCount,
            int expiredTaskCount,
            long resolvedTodayCount,
            OffsetDateTime latestCheckInAt,
            OffsetDateTime latestActivityAt,
            OffsetDateTime nextEscalationDeadline
    ) {
    }

    public record ElderRef(Long id, String name, Integer age) {
    }

    /**
     * 任務派發對象 + LINE 綁定狀態，供 dashboard 卡片顯示「目前由 XXX 處理（已綁 LINE）」。
     *
     * @param id               assignee 的 AppUser.id
     * @param displayName      AppUser.display_name；查不到時 null（前端 fallback 顯示 #id）
     * @param lineDisplayName  CaregiverLineBinding.line_display_name；未綁定為 null
     * @param lineBound        綁定旗標 — 前端用來決定是否畫綠色「LINE 已綁」chip
     */
    public record AssigneeRef(
            Long id,
            String displayName,
            String lineDisplayName,
            boolean lineBound
    ) {
    }

    public record SlaInfo(
            OffsetDateTime deadlineAt,
            long remainingSeconds,
            boolean expired
    ) {
    }

    public record ActiveEventItem(
            Long id,
            Long workflowId,
            ElderRef elder,
            AssigneeRef assignee,
            CareEventType type,
            RiskLevel riskLevel,
            String status,
            String location,
            OffsetDateTime detectedAt,
            SlaInfo sla
    ) {
    }

    public record TimelineItem(
            OffsetDateTime time,
            String message
    ) {
    }
}
