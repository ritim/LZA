// Spec § AI_Care_Chat §4：AI Care Chat client。
import { getJson, postJson } from './client';

export type AiChatRole = 'USER' | 'ASSISTANT' | 'SYSTEM';
export type AiChatSource = 'CAREGIVER_INPUT' | 'STATIC_GUIDANCE' | 'RULE_ENGINE' | 'SYSTEM';

export interface AiChatQuestion {
  id: string;
  question: string;
  type: string;
  options?: string[];
  dangerAnswer?: string[];
}

export interface AiChatSuggestedAction {
  type: string;
  label: string;
  priority: string;
  confirmationRequired: boolean;
}

export interface AiCareChatRequest {
  careEventId: number;
  workflowId: number;
  taskId?: number | null;
  message: string;
}

export interface AiCareChatResponse {
  messageId: number;
  workflowId: number;
  careEventId: number;
  reply: string;
  questions: AiChatQuestion[];
  suggestedActions: AiChatSuggestedAction[];
  dangerSigns: string[];
  disclaimer: string;
  generatedAt: string;
}

export interface AiChatHistoryItem {
  id: number;
  role: AiChatRole;
  source: AiChatSource;
  message: string;
  structuredJson: string | null;
  createdAt: string;
}

export interface AiChatHistoryResponse {
  workflowId: number;
  items: AiChatHistoryItem[];
}

export function postAiCareChat(req: AiCareChatRequest): Promise<AiCareChatResponse> {
  return postJson<AiCareChatResponse, AiCareChatRequest>('/api/v1/ai/care-chat', req);
}

export function getAiChatHistory(workflowId: number): Promise<AiChatHistoryResponse> {
  return getJson<AiChatHistoryResponse>(`/api/v1/workflows/${workflowId}/ai-messages`);
}
