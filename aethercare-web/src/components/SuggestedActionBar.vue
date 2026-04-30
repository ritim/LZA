<script setup lang="ts">
import { computed } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type {
  SuggestedAction,
  SuggestedActionPriority,
  SuggestedActionType,
} from '../api/ai';

const props = defineProps<{
  actions: SuggestedAction[];
  disabled: boolean;
}>();

const emit = defineEmits<{
  (e: 'select', actionType: SuggestedActionType): void;
}>();

const priorityRank: Record<SuggestedActionPriority, number> = {
  CRITICAL: 0,
  HIGH: 1,
  MEDIUM: 2,
  LOW: 3,
};

// CALL_EMERGENCY 永遠最左，其餘依 priority 排序。
const sortedActions = computed<SuggestedAction[]>(() => {
  return [...props.actions].sort((a, b) => {
    if (a.type === 'CALL_EMERGENCY' && b.type !== 'CALL_EMERGENCY') return -1;
    if (b.type === 'CALL_EMERGENCY' && a.type !== 'CALL_EMERGENCY') return 1;
    return (priorityRank[a.priority] ?? 99) - (priorityRank[b.priority] ?? 99);
  });
});

type ButtonType = 'primary' | 'success' | 'warning' | 'danger' | 'info';

function buttonType(action: SuggestedAction): ButtonType {
  if (action.type === 'CALL_EMERGENCY') return 'danger';
  switch (action.priority) {
    case 'CRITICAL':
      return 'danger';
    case 'HIGH':
      return 'warning';
    case 'MEDIUM':
      return 'warning';
    case 'LOW':
    default:
      return 'info';
  }
}

function buttonClass(action: SuggestedAction): string {
  if (action.type === 'CALL_EMERGENCY') return 'btn-emergency';
  return `btn-${action.priority.toLowerCase()}`;
}

async function onClick(action: SuggestedAction) {
  // CALL_EMERGENCY：MVP 不撥打，顯示模擬 dialog；同時發 NEED_HELP 給 audit。
  if (action.type === 'CALL_EMERGENCY') {
    try {
      await ElMessageBox.confirm(
        '請立即撥打 119 並告知長者狀況。系統將同步紀錄一筆求助事件。',
        '⚠ 緊急求助',
        {
          confirmButtonText: '已撥打 / 紀錄',
          cancelButtonText: '取消',
          type: 'error',
        },
      );
    } catch {
      return;
    }
    emit('select', 'CALL_EMERGENCY');
    return;
  }

  // 其他需確認的動作
  if (action.confirmationRequired) {
    try {
      await ElMessageBox.confirm(
        `確定要執行「${action.label}」嗎？`,
        '確認動作',
        {
          confirmButtonText: '確定',
          cancelButtonText: '取消',
          type: 'warning',
        },
      );
    } catch {
      return;
    }
  }

  // 未實作的 action type → MVP 顯示 info toast
  const unhandled: SuggestedActionType[] = [
    'CALL_ELDER',
    'CALL_SECOND_CONTACT',
    'REQUEST_HELP',
    'MARK_UNABLE_TO_CONFIRM',
    'ADD_NOTE',
  ];
  if (unhandled.includes(action.type)) {
    ElMessage.info(`動作「${action.label}」暫未串接（MVP）`);
    return;
  }

  emit('select', action.type);
}
</script>

<template>
  <div v-if="sortedActions.length > 0" class="action-bar">
    <el-button
      v-for="action in sortedActions"
      :key="action.type"
      :type="buttonType(action)"
      :class="buttonClass(action)"
      size="large"
      :disabled="disabled"
      @click="onClick(action)"
    >
      {{ action.label }}
    </el-button>
  </div>
</template>

<style scoped>
.action-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px;
  background: #ffffff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  margin-bottom: 16px;
}

.btn-emergency {
  font-size: 16px;
  font-weight: 700;
}

.btn-critical {
  font-weight: 600;
}

.btn-high {
  font-weight: 600;
}

@media (max-width: 768px) {
  .action-bar {
    position: sticky;
    bottom: 0;
    z-index: 10;
    box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.08);
  }
}
</style>
