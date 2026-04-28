import { getJson, postJson } from './client';
import type {
  AuditLogResponse,
  CareActionResponse,
  CareActionType,
  CareEventResponse,
  CreateCareActionRequest,
  CreateCareEventRequest,
  WorkflowResponse,
} from './types';

export function createCareEvent(req: CreateCareEventRequest): Promise<CareEventResponse> {
  return postJson<CareEventResponse, CreateCareEventRequest>('/api/v1/care-events', req);
}

export function getWorkflow(workflowId: number): Promise<WorkflowResponse> {
  return getJson<WorkflowResponse>(`/api/v1/workflows/${workflowId}`);
}

export function getAuditLogs(workflowId: number): Promise<AuditLogResponse[]> {
  return getJson<AuditLogResponse[]>(`/api/v1/workflows/${workflowId}/audit-logs`);
}

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
