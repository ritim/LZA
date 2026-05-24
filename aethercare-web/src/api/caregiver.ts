import { deleteJson, getJson, postJson, putJson } from './client';
import type {
  DashboardResponse,
  ElderContactsResponse,
  ElderEventItem,
  ElderProfileResponse,
} from './types';

export function getDashboard(): Promise<DashboardResponse> {
  return getJson<DashboardResponse>('/api/v1/caregiver/dashboard');
}

// Spec § Master §0：canonical resource 為 /api/v1/care-recipients/*。
// 函數名仍叫 getElder* 以避免大規模 rename caller，內部已改打 canonical endpoint。
export function getElder(careRecipientId: number): Promise<ElderProfileResponse> {
  return getJson<ElderProfileResponse>(`/api/v1/care-recipients/${careRecipientId}`);
}

export function getElderContacts(careRecipientId: number): Promise<ElderContactsResponse> {
  return getJson<ElderContactsResponse>(`/api/v1/care-recipients/${careRecipientId}/contacts`);
}

export function getElderEvents(careRecipientId: number, limit = 20): Promise<ElderEventItem[]> {
  return getJson<ElderEventItem[]>(
    `/api/v1/care-recipients/${careRecipientId}/events?limit=${limit}`,
  );
}

// Spec § Master §7 / Gap C：observation settings GET / PUT。
// 走 canonical /api/v1/care-recipients/* namespace。
export interface ObservationSettings {
  careRecipientId: number;
  expectedCheckinTime: string | null;
  checkinGraceMinutes: number;
  maxInactiveMinutesDaytime: number;
  maxInactiveMinutesNight: number;
  passiveMonitoringEnabled: boolean;
  escalationPolicyJson: string | null;
}

export interface UpdateObservationSettingsRequest {
  expectedCheckinTime?: string | null;
  checkinGraceMinutes?: number;
  maxInactiveMinutesDaytime?: number;
  maxInactiveMinutesNight?: number;
  passiveMonitoringEnabled?: boolean;
  escalationPolicyJson?: string | null;
}

export function getObservationSettings(careRecipientId: number): Promise<ObservationSettings> {
  return getJson<ObservationSettings>(
    `/api/v1/care-recipients/${careRecipientId}/observation-settings`,
  );
}

export function putObservationSettings(
  careRecipientId: number,
  body: UpdateObservationSettingsRequest,
): Promise<ObservationSettings> {
  return putJson<ObservationSettings, UpdateObservationSettingsRequest>(
    `/api/v1/care-recipients/${careRecipientId}/observation-settings`,
    body,
  );
}

// Spec § Master §0：被照顧者最近 N 天 check-in 歷史（月曆視圖用）。
export type CheckInStatus = 'CHECKED_IN' | 'MISSED' | 'PENDING';

export interface CheckInDayItem {
  date: string;            // YYYY-MM-DD (Asia/Taipei)
  checkedInAt: string | null;
  status: CheckInStatus;
}

export interface CheckInHistoryResponse {
  careRecipientId: number;
  expectedCheckInTime: string | null;
  graceMinutes: number;
  days: number;
  items: CheckInDayItem[];
}

export function getCheckInHistory(
  careRecipientId: number,
  days = 30,
): Promise<CheckInHistoryResponse> {
  return getJson<CheckInHistoryResponse>(
    `/api/v1/care-recipients/${careRecipientId}/check-ins?days=${days}`,
  );
}

// Spec § Master §0：caregiver ↔ LINE 綁定。
export interface LineBindingStatus {
  bound: boolean;
  lineUserId: string | null;
  lineDisplayName: string | null;
  boundAt: string | null;
}

export interface StartLineBindingResponse {
  code: string;
  expiresAt: string;
  ttlMinutes: number;
}

export function getLineBindingStatus(): Promise<LineBindingStatus> {
  return getJson<LineBindingStatus>('/api/v1/caregiver/line-binding');
}

export function startLineBinding(): Promise<StartLineBindingResponse> {
  return postJson<StartLineBindingResponse, Record<string, never>>(
    '/api/v1/caregiver/line-binding/start',
    {},
  );
}

export function unbindLine(): Promise<void> {
  return deleteJson<void>('/api/v1/caregiver/line-binding');
}
