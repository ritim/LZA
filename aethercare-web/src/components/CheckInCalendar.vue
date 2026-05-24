<script setup lang="ts">
// Spec § Master §0：被照顧者每日打卡日曆。
// 7 欄 grid，依 Asia/Taipei 日界線顯示過去 N 天 check-in 狀態。
import { computed, onMounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import {
  getCheckInHistory,
  type CheckInDayItem,
  type CheckInHistoryResponse,
} from '../api/caregiver';

const props = withDefaults(
  defineProps<{
    careRecipientId: number;
    days?: number;
  }>(),
  { days: 30 },
);

const data = ref<CheckInHistoryResponse | null>(null);
const loading = ref<boolean>(false);

const dayHeaders = ['日', '一', '二', '三', '四', '五', '六'];

const leadingBlanks = computed<number>(() => {
  const first = data.value?.items?.[0]?.date;
  if (!first) return 0;
  // YYYY-MM-DD 用本地時區解析夠用（只取 dayOfWeek，不關時刻）
  return new Date(`${first}T00:00:00`).getDay();
});

const stats = computed(() => {
  const items = data.value?.items ?? [];
  return {
    checkedIn: items.filter((i) => i.status === 'CHECKED_IN').length,
    missed: items.filter((i) => i.status === 'MISSED').length,
    pending: items.filter((i) => i.status === 'PENDING').length,
  };
});

async function load() {
  loading.value = true;
  try {
    data.value = await getCheckInHistory(props.careRecipientId, props.days);
  } catch {
    ElMessage.error('載入打卡歷史失敗');
  } finally {
    loading.value = false;
  }
}

function dayNumber(dateStr: string): string {
  return String(Number(dateStr.slice(-2)));
}

function timeLabel(at: string | null): string {
  if (!at) return '';
  return new Date(at).toLocaleTimeString('zh-TW', { hour: '2-digit', minute: '2-digit' });
}

function cellTitle(item: CheckInDayItem): string {
  const status =
    item.status === 'CHECKED_IN' ? '已簽到'
    : item.status === 'MISSED' ? '未簽到'
    : '今日待簽';
  const time = timeLabel(item.checkedInAt);
  return `${item.date}：${status}${time ? ' ' + time : ''}`;
}

watch(() => [props.careRecipientId, props.days], load);
onMounted(load);

defineExpose({ refresh: load });
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="header">
        <span class="title">每日打卡日曆</span>
        <span v-if="data" class="hint">
          過去 {{ data.days }} 天 ｜
          <span class="legend-ok">● {{ stats.checkedIn }} 已簽</span>
          <span class="legend-miss">● {{ stats.missed }} 未簽</span>
          <span class="legend-pending">● {{ stats.pending }} 待簽</span>
          <template v-if="data.expectedCheckInTime">
            　預期 {{ data.expectedCheckInTime.substring(0, 5) }}＋{{ data.graceMinutes }}m
          </template>
        </span>
      </div>
    </template>

    <el-empty v-if="!loading && !data" description="無資料" />
    <div v-else-if="data" class="cal">
      <div v-for="h in dayHeaders" :key="h" class="day-header">{{ h }}</div>
      <div v-for="b in leadingBlanks" :key="'blank-' + b" class="cell blank"></div>
      <div
        v-for="item in data.items"
        :key="item.date"
        :class="['cell', `cell-${item.status.toLowerCase()}`]"
        :title="cellTitle(item)"
      >
        <span class="d">{{ dayNumber(item.date) }}</span>
        <span v-if="item.checkedInAt" class="t">{{ timeLabel(item.checkedInAt) }}</span>
      </div>
    </div>
  </el-card>
</template>

<style scoped>
.header { display: flex; justify-content: space-between; align-items: baseline; flex-wrap: wrap; gap: 8px; }
.title { font-weight: 600; }
.hint { font-size: 12px; color: #606266; }
.legend-ok { color: #67c23a; }
.legend-miss { color: #f56c6c; }
.legend-pending { color: #e6a23c; }

.cal {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 6px;
  margin-top: 8px;
}

.day-header { text-align: center; font-size: 12px; color: #909399; padding: 4px 0; }

.cell {
  aspect-ratio: 1 / 1;
  min-height: 56px;
  border-radius: 6px;
  border: 1px solid #e6e9ef;
  background: #f9fafb;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  font-size: 12px;
}

.cell.blank { border: none; background: transparent; }
.cell-checked_in { background: #f0f9eb; border-color: #67c23a; color: #67c23a; }
.cell-missed { background: #fef0f0; border-color: #f56c6c; color: #f56c6c; }
.cell-pending { background: #fdf6ec; border-color: #e6a23c; color: #e6a23c; }

.cell .d { font-weight: 600; font-size: 16px; line-height: 1; }
.cell .t { font-size: 10px; opacity: 0.85; }
</style>
