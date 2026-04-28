// 對應 aethercare-api DTO + enums。enum 用 union string literal 維持 JSON wire 相容。

export type CareEventSource = 'MOBILE_APP' | 'WEARABLE' | 'IOT_SENSOR' | 'EDGE_GATEWAY';
export type CareEventType = 'FALL_DETECTED' | 'NO_ACTIVITY' | 'DAILY_REMINDER' | 'SOS';
export type CareEventStatus = 'RECEIVED' | 'PROCESSED' | 'DISCARDED';
export type RiskLevel = 'HIGH' | 'MEDIUM' | 'LOW';

export type CareWorkflowType = 'FALL_RESPONSE' | 'INACTIVITY_CHECK' | 'REMINDER';
export type CareWorkflowStatus =
  | 'NEW'
  | 'ACTIVE'
  | 'WAITING_RESPONSE'
  | 'ACKNOWLEDGED'
  | 'ESCALATED'
  | 'RESOLVED'
  | 'UNRESOLVED';

export type CareTaskStatus = 'PENDING' | 'ACKNOWLEDGED' | 'COMPLETED' | 'TIMEOUT' | 'CANCELLED';
export type AssigneeType = 'FAMILY' | 'CAREGIVER' | 'CALL_CENTER';

export type CareActionType = 'CONFIRM_SAFE' | 'NEED_HELP' | 'ACKNOWLEDGE';

export type CareAuditAction =
  | 'EVENT_CREATED'
  | 'WORKFLOW_STARTED'
  | 'TASK_CREATED'
  | 'NOTIFICATION_SENT'
  | 'TASK_ACKNOWLEDGED'
  | 'TASK_COMPLETED'
  | 'TASK_TIMEOUT'
  | 'TASK_ESCALATED'
  | 'WORKFLOW_RESOLVED'
  | 'WORKFLOW_UNRESOLVED'
  | 'STATE_CONFLICT_SKIPPED'
  | 'ESCALATION_TRIGGERED';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  accessExpiresIn: number;
  refreshToken: string;
  refreshExpiresIn: number;
  userId: number;
  username: string;
  roles: string[];
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface LogoutRequest {
  refreshToken: string;
}

export interface CreateCareEventRequest {
  elderId: number;
  source: CareEventSource;
  eventType: CareEventType;
  occurredAt: string;
  metadata?: Record<string, unknown>;
}

export interface CareEventResponse {
  eventId: number;
  elderId: number;
  eventType: CareEventType;
  riskLevel: RiskLevel;
  status: CareEventStatus;
  workflowId: number;
  occurredAt: string;
}

export interface CareTaskSummary {
  taskId: number;
  level: number;
  assigneeId: number | null;
  assigneeType: AssigneeType;
  status: CareTaskStatus;
  deadlineAt: string | null;
  acknowledgedAt: string | null;
  completedAt: string | null;
}

export interface WorkflowResponse {
  workflowId: number;
  eventId: number;
  elderId: number;
  workflowType: CareWorkflowType;
  riskLevel: RiskLevel;
  status: CareWorkflowStatus;
  currentLevel: number;
  startedAt: string;
  completedAt: string | null;
  tasks: CareTaskSummary[];
}

export interface CreateCareActionRequest {
  actionType: CareActionType;
  note?: string;
}

export interface CareActionResponse {
  actionId: number;
  taskId: number;
  workflowId: number;
  actorId: number;
  actionType: CareActionType;
  createdAt: string;
}

export interface AuditLogResponse {
  auditId: number;
  action: CareAuditAction;
  message: string | null;
  actorId: number | null;
  createdAt: string;
}

export interface ApiErrorBody {
  message?: string;
  error?: string;
  status?: number;
  [key: string]: unknown;
}
