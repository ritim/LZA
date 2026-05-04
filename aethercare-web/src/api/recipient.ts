// Spec § Master §7：被照顧者自助 endpoint client。
// 對應 backend /api/v1/recipient/*。X-Care-Recipient-Id header 由 caller 帶。
import { httpClient } from './client';
import type { CareEventType, RiskLevel } from './types';

const RECIPIENT_HEADER = 'X-Care-Recipient-Id';

function headers(recipientId: number): Record<string, string> {
  return { [RECIPIENT_HEADER]: String(recipientId) };
}

export interface RecipientCheckInResponse {
  activityLogId: number;
  careRecipientId: number;
  occurredAt: string;
}

export interface RecipientEventResponse {
  eventId: number;
  workflowId: number;
  careRecipientId: number;
  eventType: CareEventType;
  riskLevel: RiskLevel;
  occurredAt: string;
}

export interface RecipientTodayResponse {
  careRecipientId: number;
  latestCheckInAt: string | null;
  checkedInToday: boolean;
  openEventsCount: number;
  serverTime: string;
}

export async function postCheckIn(recipientId: number, note?: string): Promise<RecipientCheckInResponse> {
  const resp = await httpClient.post<RecipientCheckInResponse>(
    '/api/v1/recipient/check-ins',
    { careRecipientId: recipientId, note },
    { headers: headers(recipientId) },
  );
  return resp.data;
}

export async function postSos(
  recipientId: number,
  options: { note?: string; location?: string } = {},
): Promise<RecipientEventResponse> {
  const resp = await httpClient.post<RecipientEventResponse>(
    '/api/v1/recipient/sos',
    { careRecipientId: recipientId, ...options },
    { headers: headers(recipientId) },
  );
  return resp.data;
}

export async function postStatusReport(
  recipientId: number,
  payload: { symptom?: string; dangerSignals?: Record<string, unknown> },
): Promise<RecipientEventResponse> {
  const resp = await httpClient.post<RecipientEventResponse>(
    '/api/v1/recipient/status-reports',
    { careRecipientId: recipientId, ...payload },
    { headers: headers(recipientId) },
  );
  return resp.data;
}

export async function getToday(recipientId: number): Promise<RecipientTodayResponse> {
  const resp = await httpClient.get<RecipientTodayResponse>(
    `/api/v1/recipient/today?careRecipientId=${recipientId}`,
    { headers: headers(recipientId) },
  );
  return resp.data;
}
