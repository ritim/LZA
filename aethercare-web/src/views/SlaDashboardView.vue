<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import axios from 'axios';
import AppHeader from '../components/AppHeader.vue';
import {
  getSummary,
  getTimeline,
  type SlaBucket,
  type SlaSummaryResponse,
  type SlaTimelineBucket,
} from '../api/sla';
import type { ApiErrorBody } from '../api/types';

const summary = ref<SlaSummaryResponse | null>(null);
const timeline = ref<SlaTimelineBucket[]>([]);
const loading = ref(false);
const bucket = ref<SlaBucket>('hour');

// 預設窗口：過去 7 天，UTC ISO（後端只接 ISO-8601 OffsetDateTime）
function defaultRange(): [Date, Date] {
  const to = new Date();
  const from = new Date(to.getTime() - 7 * 24 * 60 * 60 * 1000);
  return [from, to];
}

const range = ref<[Date, Date]>(defaultRange());

const fromIso = computed(() => range.value[0]?.toISOString());
const toIso = computed(() => range.value[1]?.toISOString());

function formatRate(v: number): string {
  return `${(v * 100).toFixed(1)}%`;
}

function formatSeconds(v: number | null): string {
  if (v == null) return '—';
  if (v < 60) return `${v.toFixed(1)} 秒`;
  if (v < 3600) return `${(v / 60).toFixed(1)} 分`;
  return `${(v / 3600).toFixed(2)} 小時`;
}

function formatBucket(s: string): string {
  // ISO -> 'YYYY-MM-DD HH:00' (hour) / 'YYYY-MM-DD' (day)
  const d = new Date(s);
  if (bucket.value === 'day') return d.toISOString().slice(0, 10);
  return `${d.toISOString().slice(0, 10)} ${String(d.getUTCHours()).padStart(2, '0')}:00`;
}

async function reload() {
  loading.value = true;
  try {
    const [s, t] = await Promise.all([
      getSummary(fromIso.value, toIso.value),
      getTimeline(fromIso.value, toIso.value, bucket.value),
    ]);
    summary.value = s;
    timeline.value = t;
  } catch (err) {
    let msg = '載入 SLA 資料失敗';
    if (axios.isAxiosError(err)) {
      const body = err.response?.data as ApiErrorBody | undefined;
      msg = body?.message ?? body?.error ?? `載入失敗（HTTP ${err.response?.status ?? '?'}）`;
    }
    ElMessage.error(msg);
  } finally {
    loading.value = false;
  }
}

onMounted(reload);
watch(bucket, reload);
</script>

<template>
  <div class="sla">
    <AppHeader />
    <main class="content">
      <el-card shadow="never" class="filters">
        <div class="filters-row">
          <el-date-picker
            v-model="range"
            type="datetimerange"
            range-separator="→"
            start-placeholder="開始時間"
            end-placeholder="結束時間"
            value-format="YYYY-MM-DDTHH:mm:ssZ"
          />
          <el-radio-group v-model="bucket">
            <el-radio-button label="hour">時 (hour)</el-radio-button>
            <el-radio-button label="day">日 (day)</el-radio-button>
          </el-radio-group>
          <el-button type="primary" :loading="loading" @click="reload">套用</el-button>
        </div>
      </el-card>

      <div class="stat-grid" v-loading="loading">
        <el-card shadow="never">
          <el-statistic title="總 workflow" :value="summary?.totalWorkflows ?? 0" />
        </el-card>
        <el-card shadow="never">
          <el-statistic title="已解決 / 解決率"
            :value="summary?.resolvedWorkflows ?? 0">
            <template #suffix>
              <span class="suffix"> / {{ formatRate(summary?.resolvedRate ?? 0) }}</span>
            </template>
          </el-statistic>
        </el-card>
        <el-card shadow="never">
          <el-statistic title="升級率" :value="formatRate(summary?.escalationRate ?? 0)" />
        </el-card>
        <el-card shadow="never">
          <el-statistic title="平均解決時間" :value="formatSeconds(summary?.avgResolveSeconds ?? null)" />
        </el-card>
      </div>

      <el-card shadow="never">
        <template #header>
          <span style="font-weight: 600">Timeline（bucket = {{ bucket }}）</span>
        </template>
        <el-table :data="timeline" stripe size="small" empty-text="此區間內無資料">
          <el-table-column prop="bucketStart" label="Bucket" min-width="160">
            <template #default="{ row }">{{ formatBucket(row.bucketStart) }}</template>
          </el-table-column>
          <el-table-column prop="workflowsStarted" label="啟動" width="120" align="right" />
          <el-table-column prop="workflowsResolved" label="解決" width="120" align="right" />
          <el-table-column prop="escalations" label="升級" width="120" align="right" />
        </el-table>
      </el-card>
    </main>
  </div>
</template>

<style scoped>
.sla {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
.content {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 1080px;
  margin: 0 auto;
  width: 100%;
  box-sizing: border-box;
}
.filters-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: center;
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.suffix {
  font-size: 14px;
  color: #909399;
  margin-left: 4px;
}
@media (max-width: 720px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
