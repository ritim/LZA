import { getJson } from './client';

export interface SlaSummaryResponse {
  from: string;
  to: string;
  totalWorkflows: number;
  resolvedWorkflows: number;
  unresolvedWorkflows: number;
  resolvedRate: number;
  escalationRate: number;
  avgFirstResponseSeconds: number | null;
  avgResolveSeconds: number | null;
}

export interface SlaTimelineBucket {
  bucketStart: string;
  workflowsStarted: number;
  workflowsResolved: number;
  escalations: number;
}

export type SlaBucket = 'hour' | 'day';

function buildQuery(params: Record<string, string | undefined>): string {
  const usp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v != null && v !== '') usp.append(k, v);
  }
  const s = usp.toString();
  return s ? `?${s}` : '';
}

export function getSummary(from?: string, to?: string): Promise<SlaSummaryResponse> {
  return getJson<SlaSummaryResponse>(`/api/v1/sla/summary${buildQuery({ from, to })}`);
}

export function getTimeline(
  from?: string,
  to?: string,
  bucket: SlaBucket = 'hour',
): Promise<SlaTimelineBucket[]> {
  return getJson<SlaTimelineBucket[]>(
    `/api/v1/sla/timeline${buildQuery({ from, to, bucket })}`,
  );
}
