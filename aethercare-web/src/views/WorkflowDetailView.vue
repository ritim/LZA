<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { useRouter } from 'vue-router';
import axios from 'axios';
import AppHeader from '../components/AppHeader.vue';
import TaskCard from '../components/TaskCard.vue';
import AuditTimeline from '../components/AuditTimeline.vue';
import WorkflowStatusBadge from '../components/WorkflowStatusBadge.vue';
import AiGuidanceCard from '../components/AiGuidanceCard.vue';
import AssessmentQuestionCard from '../components/AssessmentQuestionCard.vue';
import SuggestedActionBar from '../components/SuggestedActionBar.vue';
import RiskReevaluationBanner from '../components/RiskReevaluationBanner.vue';
import { getAuditLogs, getWorkflow, submitAction } from '../api/care';
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
  AuditLogResponse,
  CareActionType,
  CareTaskSummary,
  CareWorkflowStatus,
  WorkflowResponse,
} from '../api/types';

const props = defineProps<{ id: string }>();
const router = useRouter();

const workflow = ref<WorkflowResponse | null>(null);
const auditLogs = ref<AuditLogResponse[]>([]);
const loading = ref<boolean>(false);
const lastUpdated = ref<number | null>(null);
let pollTimer: ReturnType<typeof setInterval> | null = null;

// AI guidance state
const guidance = ref<CareGuidance | null>(null);
const guidanceLoading = ref<boolean>(false);
const assessmentSubmitting = ref<boolean>(false);
const assessmentAnswered = ref<boolean>(false);
const reeval = ref<RiskReevaluation | null>(null);

const workflowId = computed<number>(() => Number(props.id));

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

const sortedTasks = computed(() => {
  if (!workflow.value) return [];
  return [...workflow.value.tasks].sort((a, b) => a.level - b.level || a.taskId - b.taskId);
});

// 取「目前 active task」：優先 PENDING/ACKNOWLEDGED，其次 currentLevel 對應的 task。
const activeTask = computed<CareTaskSummary | null>(() => {
  if (!workflow.value) return null;
  const tasks = sortedTasks.value;
  const live = tasks.find((t) => t.status === 'PENDING' || t.status === 'ACKNOWLEDGED');
  if (live) return live;
  const byLevel = tasks.find((t) => t.level === workflow.value!.currentLevel);
  return byLevel ?? null;
});

const startedAtText = computed<string>(() => {
  if (!workflow.value?.startedAt) return '—';
  return new Date(workflow.value.startedAt).toLocaleString('zh-TW');
});

const completedAtText = computed<string>(() => {
  if (!workflow.value?.completedAt) return '—';
  return new Date(workflow.value.completedAt).toLocaleString('zh-TW');
});

const lastUpdatedText = computed<string>(() => {
  if (!lastUpdated.value) return '—';
  return new Date(lastUpdated.value).toLocaleTimeString('zh-TW');
});

async function refresh(showSpinner = false) {
  if (Number.isNaN(workflowId.value)) return;
  if (showSpinner) loading.value = true;
  try {
    const [wf, logs] = await Promise.all([
      getWorkflow(workflowId.value),
      getAuditLogs(workflowId.value),
    ]);
    workflow.value = wf;
    auditLogs.value = logs;
    lastUpdated.value = Date.now();
  } catch (err) {
    if (axios.isAxiosError(err) && err.response?.status === 404) {
      ElMessage.error(`找不到 workflow #${workflowId.value}`);
      stopPolling();
    } else if (showSpinner) {
      // 僅在使用者主動觸發時跳錯誤訊息，避免 polling 噴一堆
      ElMessage.error('載入 workflow 失敗');
    }
  } finally {
    if (showSpinner) loading.value = false;
  }
}

async function loadGuidance() {
  if (!workflow.value) return;
  if (!isActive.value) return;
  if (guidance.value) return; // 已載入過就不重抓
  guidanceLoading.value = true;
  try {
    guidance.value = await getCareGuidance(workflow.value.eventId, workflow.value.workflowId);
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
  pollTimer = setInterval(() => {
    refresh(false);
  }, 2000);
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
}

async function onTaskAction(taskId: number, actionType: CareActionType) {
  try {
    await submitAction(taskId, actionType);
    ElMessage.success(`已送出 ${actionType}`);
    await refresh(false);
  } catch (err) {
    let msg = '動作失敗';
    if (axios.isAxiosError(err)) {
      const body = err.response?.data as ApiErrorBody | undefined;
      msg = body?.message ?? body?.error ?? `動作失敗（HTTP ${err.response?.status ?? '?'}）`;
    }
    ElMessage.error(msg);
  }
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

async function onSuggestedAction(actionType: SuggestedActionType) {
  const task = activeTask.value;
  if (!task) {
    ElMessage.info('目前無可作用的任務');
    return;
  }
  if (actionType === 'CONFIRM_SAFE') {
    await onTaskAction(task.taskId, 'CONFIRM_SAFE');
  } else if (actionType === 'ESCALATE') {
    await onTaskAction(task.taskId, 'NEED_HELP');
  } else if (actionType === 'CALL_EMERGENCY') {
    // MVP：不真撥打，但同步紀錄一筆 NEED_HELP 給 audit
    await onTaskAction(task.taskId, 'NEED_HELP');
  }
}

function backToDashboard() {
  router.push('/dashboard');
}

onMounted(async () => {
  await refresh(true);
  await loadGuidance();
  startPolling();
});

onBeforeUnmount(() => stopPolling());

watch(
  () => props.id,
  async () => {
    // 切換到不同 workflow，要重置 AI state
    guidance.value = null;
    reeval.value = null;
    assessmentAnswered.value = false;
    await refresh(true);
    await loadGuidance();
    startPolling();
  },
);

// workflow 從非 active 變 active 時嘗試補載 guidance
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
        <el-button @click="backToDashboard">
          <el-icon><ArrowLeft /></el-icon>
          <span style="margin-left: 4px">回 Dashboard</span>
        </el-button>
        <span class="poll-hint">自動每 2 秒輪詢 · 最後更新 {{ lastUpdatedText }}</span>
        <el-button :loading="loading" @click="refresh(true)">
          <el-icon><Refresh /></el-icon>
          <span style="margin-left: 4px">立即重新整理</span>
        </el-button>
      </div>

      <el-card shadow="never" class="overview" v-loading="loading && !workflow">
        <template #header>
          <div class="overview-header">
            <span style="font-weight: 600">Workflow #{{ workflowId }}</span>
            <WorkflowStatusBadge v-if="workflow" :status="workflow.status" />
          </div>
        </template>
        <el-descriptions v-if="workflow" :column="3" border size="small">
          <el-descriptions-item label="Workflow Type">
            {{ workflow.workflowType }}
          </el-descriptions-item>
          <el-descriptions-item label="Risk Level">
            <el-tag :type="workflow.riskLevel === 'HIGH' ? 'danger' : workflow.riskLevel === 'MEDIUM' ? 'warning' : 'info'">
              {{ workflow.riskLevel }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="Current Level">
            L{{ workflow.currentLevel }}
          </el-descriptions-item>
          <el-descriptions-item label="Event ID">{{ workflow.eventId }}</el-descriptions-item>
          <el-descriptions-item label="被照顧者 ID">{{ workflow.elderId }}</el-descriptions-item>
          <el-descriptions-item label="Tasks">{{ workflow.tasks.length }}</el-descriptions-item>
          <el-descriptions-item label="Started At">{{ startedAtText }}</el-descriptions-item>
          <el-descriptions-item label="Completed At">{{ completedAtText }}</el-descriptions-item>
        </el-descriptions>
        <el-empty v-else-if="!loading" description="尚未載入 workflow" />
      </el-card>

      <el-row :gutter="16" class="grid">
        <el-col :xs="24" :md="14">
          <!-- AI Guidance Section：只在 workflow active 時顯示 -->
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
          </template>
          <template v-else-if="workflow && !isActive">
            <el-alert
              type="info"
              :closable="false"
              show-icon
              title="事件已結束"
              description="此 workflow 已進入終態，AI 輔助功能不再啟用"
              class="ended"
            />
          </template>

          <h3 class="section-title">任務列表</h3>
          <el-empty v-if="sortedTasks.length === 0" description="尚無任務" />
          <TaskCard
            v-for="task in sortedTasks"
            :key="task.taskId"
            :task="task"
            @action="onTaskAction"
          />
        </el-col>
        <el-col :xs="24" :md="10">
          <h3 class="section-title">責任鏈時序</h3>
          <AuditTimeline :logs="auditLogs" />
        </el-col>
      </el-row>
    </main>
  </div>
</template>

<style scoped>
.page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.content {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
  box-sizing: border-box;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.poll-hint {
  color: #909399;
  font-size: 13px;
}

.overview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.section-title {
  margin: 0 0 12px;
  font-size: 16px;
  color: #303133;
}

.grid {
  margin-top: 4px;
}

.ended {
  margin-bottom: 16px;
}
</style>
