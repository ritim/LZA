<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import type { CareActionType, CareTaskSummary, CareTaskStatus } from '../api/types';

const props = defineProps<{ task: CareTaskSummary }>();
const emit = defineEmits<{
  (e: 'action', taskId: number, actionType: CareActionType): void;
}>();

type TagType = 'primary' | 'success' | 'warning' | 'danger' | 'info';

const now = ref<number>(Date.now());
let timer: ReturnType<typeof setInterval> | null = null;

onMounted(() => {
  timer = setInterval(() => {
    now.value = Date.now();
  }, 1000);
});

onBeforeUnmount(() => {
  if (timer) clearInterval(timer);
});

const isTerminal = computed<boolean>(() => {
  const terminal: CareTaskStatus[] = ['COMPLETED', 'TIMEOUT', 'CANCELLED'];
  return terminal.includes(props.task.status);
});

const remainingText = computed<string>(() => {
  if (!props.task.deadlineAt) return '無 deadline';
  const deadline = new Date(props.task.deadlineAt).getTime();
  const diffSec = Math.floor((deadline - now.value) / 1000);
  if (isTerminal.value) {
    if (props.task.status === 'COMPLETED') return '已完成';
    if (props.task.status === 'TIMEOUT') return '已超時';
    return '已取消';
  }
  if (diffSec <= 0) return '已超時';
  const m = Math.floor(diffSec / 60);
  const s = diffSec % 60;
  return m > 0 ? `${m} 分 ${s} 秒` : `${s} 秒`;
});

const remainingClass = computed<string>(() => {
  if (isTerminal.value) return 'remain-terminal';
  if (!props.task.deadlineAt) return 'remain-normal';
  const deadline = new Date(props.task.deadlineAt).getTime();
  const diffSec = Math.floor((deadline - now.value) / 1000);
  if (diffSec <= 0) return 'remain-overdue';
  if (diffSec <= 10) return 'remain-warn';
  return 'remain-normal';
});

const statusTagType = computed<TagType>(() => {
  switch (props.task.status) {
    case 'PENDING':
      return 'primary';
    case 'ACKNOWLEDGED':
      return 'warning';
    case 'COMPLETED':
      return 'success';
    case 'TIMEOUT':
      return 'danger';
    case 'CANCELLED':
      return 'info';
    default:
      return 'info';
  }
});

const formattedDeadline = computed<string>(() =>
  props.task.deadlineAt ? new Date(props.task.deadlineAt).toLocaleString('zh-TW') : '—',
);

function trigger(actionType: CareActionType) {
  emit('action', props.task.taskId, actionType);
}
</script>

<template>
  <el-card shadow="hover" class="task-card">
    <template #header>
      <div class="card-header">
        <div class="left">
          <el-tag size="small" type="info">L{{ task.level }}</el-tag>
          <el-tag size="small">{{ task.assigneeType }}</el-tag>
          <span class="task-id">Task #{{ task.taskId }}</span>
        </div>
        <el-tag :type="statusTagType" effect="dark" size="default">
          {{ task.status }}
        </el-tag>
      </div>
    </template>

    <div class="meta">
      <div class="meta-row">
        <span class="meta-label">指派 ID</span>
        <span class="meta-value">{{ task.assigneeId ?? '—' }}</span>
      </div>
      <div class="meta-row">
        <span class="meta-label">Deadline</span>
        <span class="meta-value">{{ formattedDeadline }}</span>
      </div>
      <div class="meta-row">
        <span class="meta-label">剩餘</span>
        <span class="meta-value" :class="remainingClass">{{ remainingText }}</span>
      </div>
    </div>

    <div class="actions">
      <el-button
        type="success"
        :disabled="isTerminal"
        @click="trigger('CONFIRM_SAFE')"
      >
        CONFIRM_SAFE
      </el-button>
      <el-button
        type="danger"
        :disabled="isTerminal"
        @click="trigger('NEED_HELP')"
      >
        NEED_HELP
      </el-button>
      <el-button :disabled="isTerminal" @click="trigger('ACKNOWLEDGE')">
        ACKNOWLEDGE
      </el-button>
    </div>
  </el-card>
</template>

<style scoped>
.task-card {
  margin-bottom: 12px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.task-id {
  font-weight: 600;
  color: #606266;
}

.meta {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 8px 16px;
  margin-bottom: 12px;
}

.meta-row {
  display: flex;
  flex-direction: column;
}

.meta-label {
  font-size: 12px;
  color: #909399;
}

.meta-value {
  font-size: 14px;
  color: #303133;
  font-variant-numeric: tabular-nums;
}

.remain-normal {
  color: #303133;
}

.remain-warn {
  color: #e6a23c;
  font-weight: 600;
}

.remain-overdue {
  color: #f56c6c;
  font-weight: 600;
}

.remain-terminal {
  color: #909399;
}

.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
