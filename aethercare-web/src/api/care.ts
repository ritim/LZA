import { getJson, postJson } from './client';
import type {
  AuditLogResponse,
  CareActionResponse,
  CareActionType,
  CareEventDetailResponse,
  CareEventResponse,
  CreateCareActionRequest,
  CreateCareEventRequest,
  WorkflowActionRequest,
  WorkflowActionResponse,
  WorkflowResponse,
  WorkflowTimelineResponse,
} from './types';

export function createCareEvent(req: CreateCareEventRequest): Promise<CareEventResponse> {
  return postJson<CareEventResponse, CreateCareEventRequest>('/api/v1/care-events', req);
}

export function getCareEvent(eventId: number): Promise<CareEventDetailResponse> {
  return getJson<CareEventDetailResponse>(`/api/v1/care-events/${eventId}`);
}

export function getWorkflow(workflowId: number): Promise<WorkflowResponse> {
  return getJson<WorkflowResponse>(`/api/v1/workflows/${workflowId}`);
}

export function getAuditLogs(workflowId: number): Promise<AuditLogResponse[]> {
  return getJson<AuditLogResponse[]>(`/api/v1/workflows/${workflowId}/audit-logs`);
}

export function getWorkflowTimeline(workflowId: number): Promise<WorkflowTimelineResponse> {
  return getJson<WorkflowTimelineResponse>(`/api/v1/workflows/${workflowId}/timeline`);
}

/** 既有 task-level 動作（向後相容）。 */
export function submitAction(
  taskId: number,
  actionType: CareActionType,
  note?: string,
): Promise<CareActionResponse> {
  const body: CreateCareActionRequest = { actionType, note };
  return postJson<CareActionResponse, CreateCareActionRequest>(
    `/api/v1/care-tasks/${taskId}/actions`,
    body,
  );
}

/** Spec §6.7 workflow-level 動作：回傳含最新 timeline 的擴充 payload。 */
export function submitWorkflowAction(
  workflowId: number,
  taskId: number,
  actionType: CareActionType,
  note?: string,
  eventId?: number,
): Promise<WorkflowActionResponse> {
  const body: WorkflowActionRequest = { taskId, actionType, note, eventId };
  return postJson<WorkflowActionResponse, WorkflowActionRequest>(
    `/api/v1/workflows/${workflowId}/actions`,
    body,
  );
}
