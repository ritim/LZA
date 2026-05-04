import { getJson, putJson } from './client';
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
