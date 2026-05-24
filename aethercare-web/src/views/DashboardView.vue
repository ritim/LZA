<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { useRouter } from 'vue-router';
import axios from 'axios';
import AppHeader from '../components/AppHeader.vue';
import ActiveEventCard from '../components/ActiveEventCard.vue';
import LineBindingCard from '../components/LineBindingCard.vue';
import { getDashboard } from '../api/caregiver';
import { createCareEvent } from '../api/care';
import type {
  ApiErrorBody,
  CreateCareEventRequest,
  DashboardResponse,
} from '../api/types';

const TPE_FORMAT: Intl.DateTimeFormatOptions = {
  hour12: false,
  timeZone: 'Asia/Taipei',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
};

function formatTime(iso: string | null | undefined): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('zh-TW', TPE_FORMAT);
}

function relativeMinutes(iso: string | null | undefined): string {
  if (!iso) return '';
  const diffSec = Math.round((Date.now() - new Date(iso).getTime()) / 1000);
  if (diffSec < 0) return `${Math.round(-diffSec / 60)} 分後`;
  if (diffSec < 60) return '剛剛';
  if (diffSec < 3600) return `${Math.round(diffSec / 60)} 分前`;
  if (diffSec < 86400) return `${Math.round(diffSec / 3600)} 小時前`;
  return `${Math.round(diffSec / 86400)} 天前`;
}

const router = useRouter();
const dashboard = ref<DashboardResponse | null>(null);
const loading = ref<boolean>(false);
const submittingDemo = ref<boolean>(false);
let pollTimer: ReturnType<typeof setInterval> | null = null;

const summary = computed(() => dashboard.value?.summary);
const nextEscalationCountdown = computed(() => {
  const iso = summary.value?.nextEscalationDeadline;
  if (!iso) return null;
  const remainSec = Math.round((new Date(iso).getTime() - Date.now()) / 1000);
  return { iso, remainSec };
});

async function refresh(showSpinner = false) {
  if (showSpinner) loading.value = true;
  try {
    dashboard.value = await getDashboard();
  } catch (err) {
    if (showSpinner) {
      ElMessage.error('載入 dashboard 失敗');
    }
  } finally {
    if (showSpinner) loading.value = false;
  }
}

function startPolling() {
  stopPolling();
  pollTimer = setInterval(() => refresh(false), 5000);
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
}

async function triggerFallEvent() {
  submittingDemo.value = true;
  try {
    const req: CreateCareEventRequest = {
      elderId: 1001,
      source: 'MOBILE_APP',
      eventType: 'FALL_DETECTED',
      occurredAt: new Date().toISOString(),
      metadata: { confidence: 0.92, location: '客廳' },
    };
    const resp = await createCareEvent(req);
    ElMessage.success(`已建立事件 #${resp.eventId}`);
    router.push({ name: 'event-detail', params: { eventId: String(resp.eventId) } });
  } catch (err) {
    let msg = '建立事件失敗';
    if (axios.isAxiosError(err)) {
      const body = err.response?.data as ApiErrorBody | undefined;
      msg = body?.message ?? body?.error ?? `建立事件失敗（HTTP ${err.response?.status ?? '?'}）`;
    }
    ElMessage.error(msg);
  } finally {
    submittingDemo.value = false;
  }
}

onMounted(async () => {
  await refresh(true);
  startPolling();
});

onBeforeUnmount(() => stopPolling());
</script>

<template>
  <div class="page">
    <AppHeader />
    <main class="content">
      <el-row :gutter="16">
        <el-col :xs="24" :md="8">
          <el-card shadow="never">
            <div class="stat-row">
              <div class="stat" style="color: #67c23a">
                <div class="num">{{ dashboard?.summary.normalCount ?? 0 }}</div>
                <div class="label">正常</div>
              </div>
              <div class="stat" style="color: #e6a23c">
                <div class="num">{{ dashboard?.summary.attentionCount ?? 0 }}</div>
                <div class="label">關注</div>
              </div>
              <div class="stat" style="color: #f56c6c">
                <div class="num">{{ dashboard?.summary.alertCount ?? 0 }}</div>
                <div class="label">警報</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :xs="24" :md="16">
          <el-card shadow="never">
            <template #header>
              <span style="font-weight: 600">Demo 控制台</span>
            </template>
            <p style="margin: 0 0 8px; color: #606266; line-height: 1.6">
              建立一筆 <strong>FALL_DETECTED</strong> 事件（elder=1001），系統會啟動 workflow，
              dashboard 會自動每 5 秒輪詢顯示。
            </p>
            <el-button type="primary" :loading="submittingDemo" @click="triggerFallEvent">
              建立 FALL_DETECTED 事件
            </el-button>
            <el-button @click="router.push('/sla')">SLA Dashboard</el-button>
            <el-button :loading="loading" @click="refresh(true)">重新整理</el-button>
          </el-card>
        </el-col>
      </el-row>

      <LineBindingCard />

      <!-- Spec § Gap H：MVP dashboard 必要 metrics 卡片 -->
      <el-card shadow="never" class="metrics-card">
        <template #header>
          <span style="font-weight: 600">今日狀態（spec § Gap H）</span>
        </template>
        <div class="metrics-grid">
          <div class="metric">
            <div class="metric-num">{{ summary?.activeEventsCount ?? 0 }}</div>
            <div class="metric-label">進行中事件</div>
          </div>
          <div class="metric">
            <div class="metric-num">{{ summary?.waitingResponseCount ?? 0 }}</div>
            <div class="metric-label">等待回應</div>
          </div>
          <div class="metric" :class="{ danger: (summary?.expiredTaskCount ?? 0) > 0 }">
            <div class="metric-num">{{ summary?.expiredTaskCount ?? 0 }}</div>
            <div class="metric-label">過期任務</div>
          </div>
          <div class="metric">
            <div class="metric-num">{{ summary?.resolvedTodayCount ?? 0 }}</div>
            <div class="metric-label">今日已結案</div>
          </div>
          <div class="metric metric-time">
            <div class="metric-time-value">{{ formatTime(summary?.latestCheckInAt) }}</div>
            <div class="metric-time-rel">{{ relativeMinutes(summary?.latestCheckInAt) }}</div>
            <div class="metric-label">最近 check-in</div>
          </div>
          <div class="metric metric-time">
            <div class="metric-time-value">{{ formatTime(summary?.latestActivityAt) }}</div>
            <div class="metric-time-rel">{{ relativeMinutes(summary?.latestActivityAt) }}</div>
            <div class="metric-label">最近活動</div>
          </div>
          <div
            class="metric metric-time"
            :class="{ danger: (nextEscalationCountdown?.remainSec ?? 1) <= 0 }"
          >
            <div class="metric-time-value">{{ formatTime(summary?.nextEscalationDeadline) }}</div>
            <div class="metric-time-rel">
              <template v-if="nextEscalationCountdown && nextEscalationCountdown.remainSec > 0">
                {{ Math.round(nextEscalationCountdown.remainSec / 60) }} 分鐘後到期
              </template>
              <template v-else-if="nextEscalationCountdown">
                已逾期
              </template>
              <template v-else>—</template>
            </div>
            <div class="metric-label">下個 SLA 截止</div>
          </div>
        </div>
      </el-card>

      <el-row :gutter="16" class="grid">
        <el-col :xs="24" :md="14">
          <h3 class="section-title">
            進行中事件
            <span class="count">（{{ dashboard?.activeEvents.length ?? 0 }}）</span>
          </h3>
          <el-empty
            v-if="!dashboard || dashboard.activeEvents.length === 0"
            description="目前沒有 active 事件"
          />
          <ActiveEventCard
            v-for="ev in dashboard?.activeEvents ?? []"
            :key="ev.id"
            :event="ev"
          />
        </el-col>
        <el-col :xs="24" :md="10">
          <h3 class="section-title">近期時序</h3>
          <el-card shadow="never">
            <el-empty
              v-if="!dashboard || dashboard.recentTimeline.length === 0"
              description="尚無時序紀錄"
            />
            <el-timeline v-else>
              <el-timeline-item
                v-for="(item, idx) in dashboard.recentTimeline"
                :key="idx"
                :timestamp="new Date(item.time).toLocaleString('zh-TW')"
                placement="top"
              >
                {{ item.message }}
              </el-timeline-item>
            </el-timeline>
          </el-card>
        </el-col>
      </el-row>
    </main>
  </div>
</template>

<style scoped>
.page { min-height: 100vh; display: flex; flex-direction: column; }
.content { padding: 24px; max-width: 1200px; margin: 0 auto; width: 100%; box-sizing: border-box; display: flex; flex-direction: column; gap: 16px; }
.stat-row { display: flex; justify-content: space-around; }
.stat { text-align: center; }
.num { font-size: 32px; font-weight: 700; line-height: 1; }
.label { font-size: 12px; color: #909399; margin-top: 4px; }
.section-title { margin: 0 0 12px; font-size: 16px; color: #303133; }
.section-title .count { color: #909399; font-size: 13px; font-weight: normal; }
.grid { margin-top: 8px; }

.metrics-card { margin-top: 8px; }
.metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 16px;
}
.metric {
  text-align: center;
  padding: 8px 4px;
  border-radius: 8px;
  background: #f5f7fa;
}
.metric.danger { background: #fef0f0; color: #b71c1c; }
.metric-num { font-size: 28px; font-weight: 700; line-height: 1.1; }
.metric-label { font-size: 12px; color: #606266; margin-top: 4px; }
.metric-time .metric-time-value { font-size: 16px; font-weight: 600; line-height: 1.2; }
.metric-time .metric-time-rel { font-size: 12px; color: #909399; margin-top: 2px; }
.metric-time.danger .metric-time-rel { color: #b71c1c; font-weight: 600; }
</style>
