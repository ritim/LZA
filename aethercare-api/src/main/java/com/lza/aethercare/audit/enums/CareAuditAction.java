package com.lza.aethercare.audit.enums;

/** Audit log 動作類型：對應系統設計 §15 + plan §3.7 額外旗標。 */
public enum CareAuditAction {
    EVENT_CREATED,
    WORKFLOW_STARTED,
    TASK_CREATED,
    NOTIFICATION_SENT,
    TASK_ACKNOWLEDGED,
    TASK_COMPLETED,
    TASK_TIMEOUT,
    TASK_ESCALATED,
    WORKFLOW_RESOLVED,
    WORKFLOW_UNRESOLVED,
    STATE_CONFLICT_SKIPPED,
    ESCALATION_TRIGGERED,
    INSURANCE_QUERY,
    ASSESSMENT_RECORDED,
    // Spec § AI_Care_Chat §9：AI Care Chat audit；不會自動改變 workflow state，
    // 純粹記錄 AI 與 caregiver 對話的責任鏈。
    AI_CHAT_STARTED,
    AI_CHAT_MESSAGE_CREATED,
    AI_CHAT_SUGGESTED_ACTIONS
}
