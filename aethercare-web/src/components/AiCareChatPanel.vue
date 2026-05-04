<script setup lang="ts">
// Spec § AI_Care_Chat §7：caregiver event detail 內嵌的 AI 對話 panel。
// 顯示 chat 歷史、輸入框、quick chips 與 suggested actions。
// suggested actions 是按鈕，但**不**直接改 workflow state；
// emit `actionSuggested` 給父層 EventDetailView 走 workflow action confirmation flow。
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import axios from 'axios';
import {
  getAiChatHistory,
  postAiCareChat,
  type AiChatHistoryItem,
  type AiChatSuggestedAction,
} from '../api/aiChat';
import type { ApiErrorBody } from '../api/types';

const props = defineProps<{
  careEventId: number;
  workflowId: number;
  taskId?: number | null;
}>();

const emit = defineEmits<{
  (e: 'actionSuggested', action: AiChatSuggestedAction): void;
}>();

const history = ref<AiChatHistoryItem[]>([]);
const suggestedActions = ref<AiChatSuggestedAction[]>([]);
const loading = ref<boolean>(false);
const sending = ref<boolean>(false);
const input = ref<string>('');
const scrollEl = ref<HTMLDivElement | null>(null);

// Spec § AI_Care_Chat §7 quick chips
const QUICK_CHIPS = ['電話未接', '已聯絡到', '有人可到場', '狀況不明', '有危險徵兆'];

const lastAssistantDisclaimer = computed<string>(() => {
  const lastAssistant = [...history.value].reverse().find((m) => m.role === 'ASSISTANT');
  if (!lastAssistant) return '';
  try {
    const data = lastAssistant.structuredJson ? JSON.parse(lastAssistant.structuredJson) : {};
    return typeof data.disclaimer === 'string' ? data.disclaimer : '';
  } catch {
    return '';
  }
});

async function loadHistory() {
  loading.value = true;
  try {
    const resp = await getAiChatHistory(props.workflowId);
    history.value = resp.items;
    syncSuggestedActions();
    await nextTick();
    scrollToBottom();
  } catch (err) {
    ElMessage.error(`載入 AI Chat 歷史失敗：${describeError(err)}`);
  } finally {
    loading.value = false;
  }
}

function syncSuggestedActions() {
  const lastAssistant = [...history.value].reverse().find((m) => m.role === 'ASSISTANT');
  if (!lastAssistant?.structuredJson) {
    suggestedActions.value = [];
    return;
  }
  try {
    const data = JSON.parse(lastAssistant.structuredJson);
    suggestedActions.value = Array.isArray(data.suggestedActions) ? data.suggestedActions : [];
  } catch {
    suggestedActions.value = [];
  }
}

function describeError(err: unknown): string {
  if (axios.isAxiosError(err)) {
    const body = err.response?.data as ApiErrorBody | undefined;
    return body?.message ?? body?.error ?? `HTTP ${err.response?.status ?? '?'}`;
  }
  return err instanceof Error ? err.message : String(err);
}

function scrollToBottom() {
  if (scrollEl.value) scrollEl.value.scrollTop = scrollEl.value.scrollHeight;
}

async function sendMessage(text?: string) {
  const message = (text ?? input.value).trim();
  if (!message || sending.value) return;
  sending.value = true;
  try {
    // Optimistic：先把 USER 訊息塞進 history（伺服器會回 messageId 但 history 重抓最完整）
    history.value.push({
      id: -Date.now(),
      role: 'USER',
      source: 'CAREGIVER_INPUT',
      message,
      structuredJson: null,
      createdAt: new Date().toISOString(),
    });
    input.value = '';
    await nextTick();
    scrollToBottom();

    const resp = await postAiCareChat({
      careEventId: props.careEventId,
      workflowId: props.workflowId,
      taskId: props.taskId ?? null,
      message,
    });
    history.value.push({
      id: resp.messageId,
      role: 'ASSISTANT',
      source: 'RULE_ENGINE',
      message: resp.reply,
      structuredJson: JSON.stringify({
        questions: resp.questions,
        suggestedActions: resp.suggestedActions,
        dangerSigns: resp.dangerSigns,
        disclaimer: resp.disclaimer,
      }),
      createdAt: resp.generatedAt,
    });
    suggestedActions.value = resp.suggestedActions;
    await nextTick();
    scrollToBottom();
  } catch (err) {
    ElMessage.error(`AI 回覆失敗：${describeError(err)}`);
    // 回退 optimistic message
    history.value = history.value.filter((m) => m.id > 0);
  } finally {
    sending.value = false;
  }
}

function onChip(chip: string) {
  sendMessage(chip);
}

function onSuggestedAction(action: AiChatSuggestedAction) {
  // Spec § AI_Care_Chat §7：suggested action 必須 emit 給父層走 workflow action confirmation。
  emit('actionSuggested', action);
}

watch(
  () => [props.careEventId, props.workflowId],
  () => loadHistory(),
);

onMounted(loadHistory);
</script>

<template>
  <el-card shadow="never" class="chat-card">
    <template #header>
      <div class="header">
        <span class="title">AI Care Chat</span>
        <span class="hint">spec § AI_Care_Chat — AI 引導行動，workflow 控制狀態</span>
      </div>
    </template>

    <div ref="scrollEl" class="chat-history">
      <el-empty v-if="!loading && history.length === 0" description="尚無對話" />
      <div v-for="item in history" :key="item.id" :class="['msg', `msg-${item.role.toLowerCase()}`]">
        <div class="bubble">
          <span class="role-label">{{
            item.role === 'USER' ? '我' : item.role === 'ASSISTANT' ? 'AI' : '系統'
          }}</span>
          <span class="text">{{ item.message }}</span>
        </div>
      </div>
      <div v-if="sending" class="msg msg-assistant">
        <div class="bubble pending">AI 思考中…</div>
      </div>
    </div>

    <div v-if="suggestedActions.length" class="suggested-actions">
      <div class="section-label">建議行動（按鈕會走 workflow 確認流程）</div>
      <el-button
        v-for="action in suggestedActions"
        :key="action.type + action.label"
        size="small"
        :type="action.priority === 'HIGH' ? 'danger' : action.priority === 'MEDIUM' ? 'warning' : 'default'"
        @click="onSuggestedAction(action)"
      >
        {{ action.label }}
      </el-button>
    </div>

    <div class="quick-chips">
      <el-button
        v-for="chip in QUICK_CHIPS"
        :key="chip"
        size="small"
        plain
        :disabled="sending"
        @click="onChip(chip)"
      >
        {{ chip }}
      </el-button>
    </div>

    <div class="input-row">
      <el-input
        v-model="input"
        placeholder="描述目前狀況或詢問下一步"
        :disabled="sending"
        @keyup.enter="sendMessage()"
      />
      <el-button type="primary" :loading="sending" @click="sendMessage()">送出</el-button>
    </div>

    <div v-if="lastAssistantDisclaimer" class="disclaimer">{{ lastAssistantDisclaimer }}</div>
  </el-card>
</template>

<style scoped>
.chat-card { margin-top: 12px; }
.header { display: flex; justify-content: space-between; align-items: baseline; }
.header .title { font-weight: 600; }
.header .hint { font-size: 12px; color: #909399; }

.chat-history {
  max-height: 320px;
  overflow-y: auto;
  padding: 8px 4px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: #f9fafb;
  border-radius: 6px;
}

.msg { display: flex; }
.msg-user { justify-content: flex-end; }
.msg-assistant, .msg-system { justify-content: flex-start; }
.bubble {
  max-width: 80%;
  padding: 8px 12px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid #e6e9ef;
  display: flex;
  gap: 6px;
  align-items: baseline;
}
.msg-user .bubble { background: #ecf5ff; border-color: #b3d8ff; }
.msg-system .bubble { background: #fef0f0; border-color: #fbc4c4; }
.bubble.pending { color: #909399; font-style: italic; }
.role-label { font-size: 11px; color: #909399; }
.text { white-space: pre-wrap; word-break: break-word; }

.suggested-actions { margin-top: 12px; display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
.suggested-actions .section-label { width: 100%; font-size: 12px; color: #606266; margin-bottom: 4px; }

.quick-chips { margin-top: 12px; display: flex; flex-wrap: wrap; gap: 6px; }

.input-row { margin-top: 12px; display: flex; gap: 8px; }

.disclaimer { margin-top: 8px; font-size: 12px; color: #909399; font-style: italic; }
</style>
