// 對應 aethercare-api DTO + enums。enum 用 union string literal 維持 JSON wire 相容。

export type CareEventSource = 'MOBILE_APP' | 'WEARABLE' | 'IOT_SENSOR' | 'EDGE_GATEWAY';
export type CareEventType =
  | 'FALL_DETECTED'
  | 'POSSIBLE_FALL'
  | 'NO_ACTIVITY'
  | 'DAILY_REMINDER'
  | 'SOS'
  | 'ACTIVITY_ANOMALY'
  | 'MISSED_CHECK_IN'
  | 'NO_RESPONSE'
  | 'FEELING_UNWELL';
export type CareEventStatus = 'RECEIVED' | 'PROCESSED' | 'DISCARDED';
export type RiskLevel = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

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

export type CareActionType =
  | 'CONFIRM_SAFE'
  | 'NEED_HELP'
  | 'ACKNOWLEDGE'
  | 'CALL_EMERGENCY'
  | 'ESCALATE'
  | 'CALL_ELDER'
  | 'CALL_SECOND_CONTACT'
  | 'REQUEST_HELP'
  | 'MARK_UNABLE_TO_CONFIRM'
  | 'ADD_NOTE'
  | 'CALL_NO_ANSWER';

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
  | 'ESCALATION_TRIGGERED'
  | 'INSURANCE_QUERY'
  | 'ASSESSMENT_RECORDED';

export type TimelineLevel = 'INFO' | 'WARNING' | 'CRITICAL';

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

export interface SensorSummary {
  noMovementSeconds: number | null;
  fallConfidence: number | null;
  source: string | null;
}

// Spec §6.2 GET /api/care-events/{id} 詳情
export interface CareEventDetailResponse {
  id: number;
  elderId: number;
  workflowId: number | null;
  type: CareEventType;
  riskLevel: RiskLevel;
  status: CareEventStatus;
  location: string | null;
  detectedAt: string;
  createdAt: string;
  sensorSummary: SensorSummary | null;
  metadata: Record<string, unknown>;
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

// Spec §6.8 timeline item（structured）
export interface TimelineItem {
  id: number;
  time: string;
  type: CareAuditAction;
  actorName: string;
  message: string | null;
  level: TimelineLevel;
}

export interface WorkflowTimelineResponse {
  workflowId: number;
  items: TimelineItem[];
}

// Spec §6.7 workflow-level action 請求 / 回應
export interface WorkflowActionRequest {
  eventId?: number;
  taskId: number;
  actionType: CareActionType;
  note?: string;
}

export interface WorkflowActionResponse {
  workflowId: number;
  eventId: number;
  taskId: number;
  actionType: CareActionType;
  workflowStatus: CareWorkflowStatus;
  taskStatus: CareTaskStatus;
  message: string;
  timeline: TimelineItem[];
}

// Spec §6.1 caregiver dashboard + Gap H 補強欄位
export interface DashboardSummary {
  normalCount: number;
  attentionCount: number;
  alertCount: number;
  activeEventsCount: number;
  waitingResponseCount: number;
  expiredTaskCount: number;
  resolvedTodayCount: number;
  latestCheckInAt: string | null;
  latestActivityAt: string | null;
  nextEscalationDeadline: string | null;
}

export interface DashboardSlaInfo {
  deadlineAt: string;
  remainingSeconds: number;
  expired: boolean;
}

export interface DashboardElderRef {
  id: number;
  name: string | null;
  age: number | null;
}

export interface DashboardAssigneeRef {
  id: number;
  displayName: string | null;
  lineDisplayName: string | null;
  lineBound: boolean;
}

export interface DashboardActiveEventItem {
  id: number;
  workflowId: number;
  elder: DashboardElderRef;
  assignee: DashboardAssigneeRef | null;
  type: CareEventType;
  riskLevel: RiskLevel;
  status: string;
  location: string | null;
  detectedAt: string;
  sla: DashboardSlaInfo | null;
}

export interface DashboardTimelineItem {
  time: string;
  message: string;
}

export interface DashboardResponse {
  summary: DashboardSummary;
  activeEvents: DashboardActiveEventItem[];
  recentTimeline: DashboardTimelineItem[];
}

// Spec §3.4 / §6.3 / §6.9 elder
export interface ElderProfileResponse {
  id: number;
  name: string;
  age: number;
  gender: string | null;
  mobility: string;
  chronicDiseases: string[];
  allergies: string[];
  address: string | null;
  emergencyNotes: string | null;
}

export interface ElderContactItem {
  id: number;
  name: string;
  relationship: string;
  phone: string;
  priorityLevel: number;
}

// Spec § Master §0：canonical /api/v1/care-recipients/*/contacts wire 欄位用 careRecipientId。
export interface ElderContactsResponse {
  careRecipientId: number;
  contacts: ElderContactItem[];
}

export interface ElderEventItem {
  eventId: number;
  eventType: CareEventType;
  riskLevel: RiskLevel;
  status: CareEventStatus;
  occurredAt: string;
}

export interface ApiErrorBody {
  message?: string;
  error?: string;
  status?: number;
  [key: string]: unknown;
}
