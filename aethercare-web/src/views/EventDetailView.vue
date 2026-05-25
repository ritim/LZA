<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { useRouter } from 'vue-router';
import axios from 'axios';
import AppHeader from '../components/AppHeader.vue';
import AiGuidanceCard from '../components/AiGuidanceCard.vue';
import AssessmentQuestionCard from '../components/AssessmentQuestionCard.vue';
import RiskReevaluationBanner from '../components/RiskReevaluationBanner.vue';
import SuggestedActionBar from '../components/SuggestedActionBar.vue';
import SlaCountdownBar from '../components/SlaCountdownBar.vue';
import ActionConfirmationModal from '../components/ActionConfirmationModal.vue';
import AiCareChatPanel from '../components/AiCareChatPanel.vue';
import type { AiChatSuggestedAction } from '../api/aiChat';
import {
  getCareEvent,
  getWorkflow,
  getWorkflowTimeline,
  submitWorkflowAction,
} from '../api/care';
import {
  getCareGuidance,
  submitAssessmentAnswers,
  type AssessmentAnswerItem,
  type CareGuidance,
  type RiskReevaluation,
  type SuggestedActionType,
} from '../api/ai';
import type {
  ApiErrorBody,
  CareActionType,
  CareEventDetailResponse,
  CareTaskSummary,
  CareWorkflowStatus,
  TimelineItem,
  WorkflowResponse,
} from '../api/types';

const props = defineProps<{ eventId: string }>();
const router = useRouter();

const event = ref<CareEventDetailResponse | null>(null);
const workflow = ref<WorkflowResponse | null>(null);
const timeline = ref<TimelineItem[]>([]);
const loading = ref<boolean>(false);
let pollTimer: ReturnType<typeof setInterval> | null = null;

const guidance = ref<CareGuidance | null>(null);
const guidanceLoading = ref<boolean>(false);
const assessmentSubmitting = ref<boolean>(false);
const assessmentAnswered = ref<boolean>(false);
const reeval = ref<RiskReevaluation | null>(null);

const confirmOpen = ref<boolean>(false);
const pendingAction = ref<CareActionType | null>(null);
const submitting = ref<boolean>(false);

const eventIdNum = computed<number>(() => Number(props.eventId));

const ACTIVE_STATUSES: CareWorkflowStatus[] = [
  'NEW',
  'ACTIVE',
  'WAITING_RESPONSE',
  'ACKNOWLEDGED',
  'ESCALATED',
];

const isActive = computed<boolean>(() => {
  if (!workflow.value) return false;
  return ACTIVE_STATUSES.includes(workflow.value.status);
});

const sortedTasks = computed<CareTaskSummary[]>(() => {
  if (!workflow.value) return [];
  return [...workflow.value.tasks].sort((a, b) => a.level - b.level || a.taskId - b.taskId);
});

const activeTask = computed<CareTaskSummary | null>(() => {
  if (!workflow.value) return null;
  const tasks = sortedTasks.value;
  return (
    tasks.find((t) => t.status === 'PENDING' || t.status === 'ACKNOWLEDGED') ??
    tasks.find((t) => t.level === workflow.value!.currentLevel) ??
    null
  );
});

const eventTypeText = computed<string>(() => {
  if (!event.value) return '';
  switch (event.value.type) {
    case 'FALL_DETECTED':
      return '疑似跌倒';
    case 'NO_ACTIVITY':
      return '長時間無活動';
    case 'SOS':
      return 'SOS 求救';
    case 'DAILY_REMINDER':
      return '每日提醒';
    default:
      return event.value.type;
  }
});

const detectedAtText = computed<string>(() => {
  if (!event.value?.detectedAt) return '—';
  return new Date(event.value.detectedAt).toLocaleString('zh-TW');
});

const sensorText = computed<string | null>(() => {
  const s = event.value?.sensorSummary;
  if (!s) return null;
  const parts: string[] = [];
  if (s.fallConfidence != null) parts.push(`fall confidence ${(s.fallConfidence * 100).toFixed(0)}%`);
  if (s.noMovementSeconds != null) parts.push(`無活動 ${s.noMovementSeconds}s`);
  if (s.source) parts.push(`source ${s.source}`);
  return parts.join(' / ') || null;
});

async function refresh(showSpinner = false) {
  if (Number.isNaN(eventIdNum.value)) return;
  if (showSpinner) loading.value = true;
  try {
    const ev = await getCareEvent(eventIdNum.value);
    event.value = ev;
    if (ev.workflowId) {
      const [wf, tl] = await Promise.all([
        getWorkflow(ev.workflowId),
        getWorkflowTimeline(ev.workflowId),
      ]);
      workflow.value = wf;
      timeline.value = tl.items;
    }
  } catch (err) {
    if (axios.isAxiosError(err) && err.response?.status === 404) {
      ElMessage.error(`找不到 event #${eventIdNum.value}`);
      stopPolling();
    } else if (showSpinner) {
      ElMessage.error('載入事件失敗');
    }
  } finally {
    if (showSpinner) loading.value = false;
  }
}

async function loadGuidance() {
  if (!event.value || !workflow.value || !isActive.value || guidance.value) return;
  guidanceLoading.value = true;
  try {
    guidance.value = await getCareGuidance(event.value.id, workflow.value.workflowId);
  } catch (err) {
    let msg = '載入 AI 建議失敗';
    if (axios.isAxiosError(err)) {
      const body = err.response?.data as ApiErrorBody | undefined;
      msg = body?.message ?? body?.error ?? msg;
    }
    ElMessage.error(msg);
  } finally {
    guidanceLoading.value = false;
  }
}

function startPolling() {
  stopPolling();
  pollTimer = setInterval(() => refresh(false), 2000);
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
}

function openElderProfile() {
  if (!event.value) return;
  router.push({ name: 'care-profile', params: { elderId: String(event.value.elderId) } });
}

async function onAssessmentSubmit(answers: AssessmentAnswerItem[]) {
  if (!workflow.value) return;
  assessmentSubmitting.value = true;
  try {
    const result = await submitAssessmentAnswers(workflow.value.workflowId, {
      eventId: workflow.value.eventId,
      taskId: activeTask.value?.taskId ?? null,
      answers,
    });
    reeval.value = result.riskReevaluation;
    assessmentAnswered.value = true;
    if (result.riskReevaluation.dangerDetected) {
      ElMessage.warning('已偵測到危險徵象，請依建議動作處置');
    } else {
      ElMessage.success('評估已提交');
    }
    await refresh(false);
  } catch (err) {
    let msg = '提交評估失敗';
    if (axios.isAxiosError(err)) {
      const body = err.response?.data as ApiErrorBody | undefined;
      msg = body?.message ?? body?.error ?? msg;
    }
    ElMessage.error(msg);
  } finally {
    assessmentSubmitting.value = false;
  }
}

// Spec § AI_Care_Chat §7：chat panel 內按鈕 emit 給這裡，走既有 confirmation flow。
function onChatActionSuggested(action: AiChatSuggestedAction) {
  onSuggestedAction(action.type as SuggestedActionType);
}

function onSuggestedAction(t: SuggestedActionType) {
  if (!activeTask.value) {
    ElMessage.info('目前無可作用的任務');
    return;
  }
  // Spec §6.7：8 種 action 都可直接送出，後端會收斂為內部語意但保留原始 type 在 audit + kafka
  pendingAction.value = t as CareActionType;
  confirmOpen.value = true;
}

async function onConfirmAction() {
  if (!workflow.value || !activeTask.value || !pendingAction.value) return;
  submitting.value = true;
  try {
    const resp = await submitWorkflowAction(
      workflow.value.workflowId,
      activeTask.value.taskId,
      pendingAction.value,
      undefined,
      workflow.value.eventId,
    );
    timeline.value = resp.timeline;
    ElMessage.success(resp.message || '已送出');
    confirmOpen.value = false;
    await refresh(false);
  } catch (err) {
    let msg = '動作失敗';
    if (axios.isAxiosError(err)) {
      const body = err.response?.data as ApiErrorBody | undefined;
      msg = body?.message ?? body?.error ?? msg;
    }
    ElMessage.error(msg);
  } finally {
    submitting.value = false;
  }
}

function backToDashboard() {
  router.push('/dashboard');
}

const timelineColor: Record<string, string> = {
  INFO: 'primary',
  WARNING: 'warning',
  CRITICAL: 'danger',
};

onMounted(async () => {
  await refresh(true);
  await loadGuidance();
  startPolling();
});

onBeforeUnmount(() => stopPolling());

watch(
  () => props.eventId,
  async () => {
    guidance.value = null;
    reeval.value = null;
    assessmentAnswered.value = false;
    await refresh(true);
    await loadGuidance();
    startPolling();
  },
);

watch(isActive, async (active) => {
  if (active && !guidance.value) {
    await loadGuidance();
  }
});
</script>

<template>
  <div class="page">
    <AppHeader />
    <main class="content">
      <div class="toolbar">
        <el-button @click="backToDashboard">回 Dashboard</el-button>
        <el-button :loading="loading" @click="refresh(true)">立即重新整理</el-button>
      </div>

      <el-card v-if="event" shadow="never" :class="{ alert: event.riskLevel === 'HIGH' }">
        <div class="header-row">
          <span class="head-icon">{{ event.riskLevel === 'HIGH' ? '🚨' : '🟡' }}</span>
          <h2 class="head-title">{{ eventTypeText }}</h2>
          <el-tag :type="event.riskLevel === 'HIGH' ? 'danger' : 'warning'" size="large">
            {{ event.riskLevel }} 風險
          </el-tag>
        </div>
        <el-descriptions :column="2" border size="small" class="head-meta">
          <el-descriptions-item label="被照顧者">
            <a class="elder-link" @click="openElderProfile">
              #{{ event.elderId }} · 查看完整檔案 →
            </a>
          </el-descriptions-item>
          <el-descriptions-item label="地點">{{ event.location || '—' }}</el-descriptions-item>
          <el-descriptions-item label="發生時間">{{ detectedAtText }}</el-descriptions-item>
          <el-descriptions-item label="目前狀態">{{ workflow?.status || event.status }}</el-descriptions-item>
          <el-descriptions-item v-if="sensorText" label="感測摘要" :span="2">{{ sensorText }}</el-descriptions-item>
        </el-descriptions>
        <SlaCountdownBar
          v-if="activeTask?.deadlineAt"
          class="head-sla"
          :deadline-at="activeTask.deadlineAt"
          :status="activeTask.status"
        />
      </el-card>

      <el-row :gutter="16" class="grid">
        <el-col :xs="24" :md="14">
          <template v-if="workflow && isActive">
            <h3 class="section-title">AI 照護輔助</h3>
            <AiGuidanceCard :guidance="guidance" :loading="guidanceLoading" />
            <RiskReevaluationBanner :reeval="reeval" />
            <AssessmentQuestionCard
              v-if="guidance && !assessmentAnswered"
              :questions="guidance.questions"
              :submitting="assessmentSubmitting"
              @submit="onAssessmentSubmit"
            />
            <SuggestedActionBar
              v-if="guidance && activeTask"
              :actions="guidance.suggestedActions"
              :disabled="false"
              @select="onSuggestedAction"
            />
            <AiCareChatPanel
              v-if="event && workflow"
              :care-event-id="event.id"
              :workflow-id="workflow.workflowId"
              :task-id="activeTask?.taskId ?? null"
              @action-suggested="onChatActionSuggested"
            />
          </template>
          <template v-else-if="workflow && !isActive">
            <el-alert
              type="info"
              :closable="false"
              show-icon
              title="事件已結束"
              description="此事件對應 workflow 已進入終態。"
              class="ended"
            />
          </template>
        </el-col>

        <el-col :xs="24" :md="10">
          <h3 class="section-title">責任鏈時序</h3>
          <el-card shadow="never">
            <el-empty v-if="timeline.length === 0" description="尚無時序紀錄" />
            <el-timeline v-else>
              <el-timeline-item
                v-for="item in timeline"
                :key="item.id"
                :timestamp="new Date(item.time).toLocaleString('zh-TW')"
                :type="(timelineColor[item.level] || 'info') as any"
                placement="top"
              >
                <div class="row">
                  <el-tag size="small" :type="(timelineColor[item.level] || 'info') as any">
                    {{ item.type }}
                  </el-tag>
                  <span class="actor">{{ item.actorName }}</span>
                </div>
                <div v-if="item.message" class="msg">{{ item.message }}</div>
              </el-timeline-item>
            </el-timeline>
          </el-card>
        </el-col>
      </el-row>

      <ActionConfirmationModal
        v-model="confirmOpen"
        :action-type="pendingAction"
        :loading="submitting"
        @confirm="onConfirmAction"
        @cancel="confirmOpen = false"
      />
    </main>
  </div>
</template>

<style scoped>
.page { min-height: 100vh; display: flex; flex-direction: column; }
.content { padding: 24px; max-width: 1200px; margin: 0 auto; width: 100%; box-sizing: border-box; display: flex; flex-direction: column; gap: 16px; }
.toolbar { display: flex; gap: 8px; }
.alert { border-left: 4px solid #f56c6c; }
.header-row { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.head-icon { font-size: 24px; }
.head-title { margin: 0; font-size: 22px; color: #303133; }
.head-meta { margin-bottom: 12px; }
.head-sla { margin-top: 8px; }
.section-title { margin: 0 0 12px; font-size: 16px; color: #303133; }
.row { display: flex; align-items: center; gap: 8px; }
.actor { color: #909399; font-size: 12px; }
.msg { color: #606266; font-size: 13px; margin-top: 4px; white-space: pre-wrap; }
.ended { margin-bottom: 16px; }
.grid { margin-top: 4px; }
.elder-link { color: #409eff; cursor: pointer; text-decoration: underline; }
.elder-link:hover { color: #66b1ff; }
</style>
