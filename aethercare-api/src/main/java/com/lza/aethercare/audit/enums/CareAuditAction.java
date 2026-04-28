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
    ESCALATION_TRIGGERED
}
