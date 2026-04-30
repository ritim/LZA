// AI Guidance API：對應 aethercare-api v1.0-rc2 /api/v1/ai/care-guidance 與 assessment-answers。
import { httpClient } from './client';

export type AssessmentQuestionType = 'YES_NO_UNKNOWN' | 'SINGLE_CHOICE' | 'TEXT';

export type SuggestedActionType =
  | 'CALL_EMERGENCY'
  | 'ESCALATE'
  | 'CONFIRM_SAFE'
  | 'CALL_ELDER'
  | 'CALL_SECOND_CONTACT'
  | 'REQUEST_HELP'
  | 'MARK_UNABLE_TO_CONFIRM'
  | 'ADD_NOTE';

export type SuggestedActionPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface AssessmentQuestion {
  id: string;
  question: string;
  type: AssessmentQuestionType;
  options?: string[];
  dangerAnswer?: string[];
}

export interface SuggestedAction {
  type: SuggestedActionType;
  label: string;
  priority: SuggestedActionPriority;
  confirmationRequired: boolean;
}

export interface CareGuidance {
  summary: string;
  guidance: string[];
  questions: AssessmentQuestion[];
  suggestedActions: SuggestedAction[];
  dangerSigns: string[];
  disclaimer: string;
  generatedAt: string;
}

export interface AssessmentAnswerItem {
  questionId: string;
  question: string;
  answer: string;
}

export interface AssessmentAnswerSubmit {
  eventId: number;
  taskId?: number | null;
  answers: AssessmentAnswerItem[];
}

export interface RiskReevaluation {
  riskLevel: string;
  dangerDetected: boolean;
  recommendedAction: string | null;
  message: string;
}

export interface AssessmentAnswerResult {
  workflowId: number;
  taskId: number | null;
  riskReevaluation: RiskReevaluation;
  saved: boolean;
}

export async function getCareGuidance(
  eventId: number,
  workflowId: number,
): Promise<CareGuidance> {
  const { data } = await httpClient.get<CareGuidance>('/api/v1/ai/care-guidance', {
    params: { eventId, workflowId },
  });
  return data;
}

export async function submitAssessmentAnswers(
  workflowId: number,
  body: AssessmentAnswerSubmit,
): Promise<AssessmentAnswerResult> {
  const { data } = await httpClient.post<AssessmentAnswerResult>(
    `/api/v1/workflows/${workflowId}/assessment-answers`,
    body,
  );
  return data;
}
