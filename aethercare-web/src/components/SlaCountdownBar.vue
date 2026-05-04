<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

/**
 * Spec §7.2 SlaCountdownBar：每秒倒數，依剩餘秒數呈現四種狀態（normal / warning / urgent / expired）。
 * 過期時呼叫 onExpired 一次。
 */
const props = defineProps<{
  deadlineAt: string;
  status?: string;
  onExpired?: () => void;
}>();

const now = ref<number>(Date.now());
let timer: ReturnType<typeof setInterval> | null = null;
let expiredFired = false;

const remainingSeconds = computed<number>(() => {
  const deadline = new Date(props.deadlineAt).getTime();
  return Math.max(0, Math.floor((deadline - now.value) / 1000));
});

const phase = computed<'normal' | 'warning' | 'urgent' | 'expired'>(() => {
  const r = remainingSeconds.value;
  if (r <= 0) return 'expired';
  if (r <= 10) return 'urgent';
  if (r <= 30) return 'warning';
  return 'normal';
});

const mmss = computed<string>(() => {
  const total = remainingSeconds.value;
  const m = Math.floor(total / 60).toString().padStart(2, '0');
  const s = (total % 60).toString().padStart(2, '0');
  return `${m}:${s}`;
});

const totalForBar = 60; // 視覺基準：以 60s 滿格倒推百分比
const percent = computed<number>(() => {
  return Math.min(100, Math.max(0, (remainingSeconds.value / totalForBar) * 100));
});

function tick() {
  now.value = Date.now();
  if (remainingSeconds.value <= 0 && !expiredFired) {
    expiredFired = true;
    props.onExpired?.();
  }
}

onMounted(() => {
  tick();
  timer = setInterval(tick, 1000);
});

onBeforeUnmount(() => {
  if (timer) clearInterval(timer);
});
</script>

<template>
  <div class="sla" :class="`sla-${phase}`">
    <div class="row">
      <span class="label">SLA 倒數</span>
      <span class="time">{{ phase === 'expired' ? '已逾時' : mmss }}</span>
    </div>
    <div class="bar">
      <div class="fill" :style="{ width: `${percent}%` }"></div>
    </div>
    <div class="hint">
      <span v-if="phase === 'normal'">於期限內</span>
      <span v-else-if="phase === 'warning'">⚠️ 接近期限，請儘速處理</span>
      <span v-else-if="phase === 'urgent'">🔴 即將逾時，立即處理</span>
      <span v-else>⏱ 已逾時，系統將自動升級</span>
    </div>
  </div>
</template>

<style scoped>
.sla {
  border-radius: 6px;
  padding: 12px 16px;
  background: #f5f7fa;
  border-left: 4px solid #909399;
}

.sla-normal {
  background: #f0f9ff;
  border-left-color: #409eff;
}
.sla-warning {
  background: #fdf6ec;
  border-left-color: #e6a23c;
}
.sla-urgent {
  background: #fef0f0;
  border-left-color: #f56c6c;
  animation: pulse 1s infinite alternate;
}
.sla-expired {
  background: #f4f4f5;
  border-left-color: #909399;
}

@keyframes pulse {
  from { box-shadow: 0 0 0 0 rgba(245, 108, 108, 0.4); }
  to { box-shadow: 0 0 0 6px rgba(245, 108, 108, 0); }
}

.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.label {
  color: #606266;
  font-size: 13px;
}

.time {
  font-family: 'SF Mono', Menlo, Consolas, monospace;
  font-size: 22px;
  font-weight: 600;
  color: #303133;
}

.sla-urgent .time, .sla-expired .time {
  color: #f56c6c;
}

.bar {
  height: 6px;
  border-radius: 3px;
  background: #e4e7ed;
  overflow: hidden;
  margin-bottom: 6px;
}

.fill {
  height: 100%;
  background: currentColor;
  transition: width 0.5s linear;
}

.sla-normal .fill { background: #409eff; }
.sla-warning .fill { background: #e6a23c; }
.sla-urgent .fill { background: #f56c6c; }
.sla-expired .fill { background: #c0c4cc; }

.hint {
  font-size: 12px;
  color: #909399;
}
</style>
