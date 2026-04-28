<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { useRouter } from 'vue-router';
import axios from 'axios';
import AppHeader from '../components/AppHeader.vue';
import TaskCard from '../components/TaskCard.vue';
import AuditTimeline from '../components/AuditTimeline.vue';
import WorkflowStatusBadge from '../components/WorkflowStatusBadge.vue';
import { getAuditLogs, getWorkflow, submitAction } from '../api/care';
import type {
  ApiErrorBody,
  AuditLogResponse,
  CareActionType,
  WorkflowResponse,
} from '../api/types';

const props = defineProps<{ id: string }>();
const router = useRouter();

const workflow = ref<WorkflowResponse | null>(null);
const auditLogs = ref<AuditLogResponse[]>([]);
const loading = ref<boolean>(false);
const lastUpdated = ref<number | null>(null);
let pollTimer: ReturnType<typeof setInterval> | null = null;

const workflowId = computed<number>(() => Number(props.id));

const sortedTasks = computed(() => {
  if (!workflow.value) return [];
  return [...workflow.value.tasks].sort((a, b) => a.level - b.level || a.taskId - b.taskId);
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

function backToDashboard() {
  router.push('/dashboard');
}

onMounted(async () => {
  await refresh(true);
  startPolling();
});

onBeforeUnmount(() => stopPolling());

watch(
  () => props.id,
  async () => {
    await refresh(true);
    startPolling();
  },
);
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
          <el-descriptions-item label="Elder ID">{{ workflow.elderId }}</el-descriptions-item>
          <el-descriptions-item label="Tasks">{{ workflow.tasks.length }}</el-descriptions-item>
          <el-descriptions-item label="Started At">{{ startedAtText }}</el-descriptions-item>
          <el-descriptions-item label="Completed At">{{ completedAtText }}</el-descriptions-item>
        </el-descriptions>
        <el-empty v-else-if="!loading" description="尚未載入 workflow" />
      </el-card>

      <el-row :gutter="16" class="grid">
        <el-col :xs="24" :md="14">
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
</style>
