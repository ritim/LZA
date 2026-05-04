<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { useRouter } from 'vue-router';
import axios from 'axios';
import AppHeader from '../components/AppHeader.vue';
import {
  getElder,
  getElderContacts,
  getElderEvents,
  getObservationSettings,
  putObservationSettings,
  type ObservationSettings,
  type UpdateObservationSettingsRequest,
} from '../api/caregiver';
import type {
  ApiErrorBody,
  ElderContactsResponse,
  ElderEventItem,
  ElderProfileResponse,
} from '../api/types';

const props = defineProps<{ elderId: string }>();
const router = useRouter();

const profile = ref<ElderProfileResponse | null>(null);
const contacts = ref<ElderContactsResponse | null>(null);
const events = ref<ElderEventItem[]>([]);
const settings = ref<ObservationSettings | null>(null);
const loading = ref<boolean>(false);
const editing = ref<boolean>(false);
const saving = ref<boolean>(false);

const form = reactive<{
  expectedCheckinTime: string | null;
  checkinGraceMinutes: number;
  maxInactiveMinutesDaytime: number;
  maxInactiveMinutesNight: number;
  passiveMonitoringEnabled: boolean;
}>({
  expectedCheckinTime: null,
  checkinGraceMinutes: 60,
  maxInactiveMinutesDaytime: 180,
  maxInactiveMinutesNight: 480,
  passiveMonitoringEnabled: true,
});

async function load() {
  loading.value = true;
  try {
    const id = Number(props.elderId);
    const [p, c, e, s] = await Promise.all([
      getElder(id),
      getElderContacts(id),
      getElderEvents(id, 20),
      getObservationSettings(id),
    ]);
    profile.value = p;
    contacts.value = c;
    events.value = e;
    settings.value = s;
  } catch {
    ElMessage.error('載入被照顧者資料失敗');
  } finally {
    loading.value = false;
  }
}

function openEdit() {
  if (!settings.value) return;
  form.expectedCheckinTime = settings.value.expectedCheckinTime;
  form.checkinGraceMinutes = settings.value.checkinGraceMinutes;
  form.maxInactiveMinutesDaytime = settings.value.maxInactiveMinutesDaytime;
  form.maxInactiveMinutesNight = settings.value.maxInactiveMinutesNight;
  form.passiveMonitoringEnabled = settings.value.passiveMonitoringEnabled;
  editing.value = true;
}

async function saveSettings() {
  saving.value = true;
  try {
    const id = Number(props.elderId);
    const body: UpdateObservationSettingsRequest = {
      // null 透過 PUT 可以「清空」每日 check-in 設定
      expectedCheckinTime: form.expectedCheckinTime || null,
      checkinGraceMinutes: form.checkinGraceMinutes,
      maxInactiveMinutesDaytime: form.maxInactiveMinutesDaytime,
      maxInactiveMinutesNight: form.maxInactiveMinutesNight,
      passiveMonitoringEnabled: form.passiveMonitoringEnabled,
    };
    settings.value = await putObservationSettings(id, body);
    editing.value = false;
    ElMessage.success('觀察設定已更新');
  } catch (err) {
    let msg = '更新觀察設定失敗';
    if (axios.isAxiosError(err)) {
      const detail = err.response?.data as ApiErrorBody | undefined;
      msg = detail?.message ?? detail?.error ?? `更新失敗（HTTP ${err.response?.status ?? '?'}）`;
    }
    ElMessage.error(msg);
  } finally {
    saving.value = false;
  }
}

function checkinSummary(s: ObservationSettings): string {
  if (!s.expectedCheckinTime) return '未設定每日 check-in';
  return `${s.expectedCheckinTime.substring(0, 5)} ＋ ${s.checkinGraceMinutes} 分鐘 grace`;
}

function maskPhone(p: string): string {
  // 只顯示後 4 碼，符合 spec §15 隱私規則
  if (!p || p.length <= 4) return p;
  return `${'*'.repeat(p.length - 4)}${p.slice(-4)}`;
}

function eventTypeText(t: string): string {
  switch (t) {
    case 'FALL_DETECTED':
      return '疑似跌倒';
    case 'NO_ACTIVITY':
      return '長時間無活動';
    case 'SOS':
      return 'SOS 求救';
    case 'DAILY_REMINDER':
      return '每日提醒';
    default:
      return t;
  }
}

onMounted(load);
</script>

<template>
  <div class="page">
    <AppHeader />
    <main class="content">
      <div class="toolbar">
        <el-button @click="router.push('/dashboard')">回 Dashboard</el-button>
        <el-button :loading="loading" @click="load">重新整理</el-button>
      </div>

      <el-card v-if="profile" shadow="never">
        <template #header>
          <span style="font-weight: 600">被照顧者資料</span>
        </template>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="姓名">{{ profile.name }}</el-descriptions-item>
          <el-descriptions-item label="年齡">{{ profile.age }} 歲</el-descriptions-item>
          <el-descriptions-item label="性別">{{ profile.gender || '—' }}</el-descriptions-item>
          <el-descriptions-item label="活動能力">{{ profile.mobility }}</el-descriptions-item>
          <el-descriptions-item label="慢性病" :span="2">
            <el-tag
              v-for="d in profile.chronicDiseases"
              :key="d"
              size="small"
              style="margin-right: 4px"
            >
              {{ d }}
            </el-tag>
            <span v-if="profile.chronicDiseases.length === 0">—</span>
          </el-descriptions-item>
          <el-descriptions-item label="過敏" :span="2">
            <el-tag
              v-for="a in profile.allergies"
              :key="a"
              size="small"
              type="warning"
              style="margin-right: 4px"
            >
              {{ a }}
            </el-tag>
            <span v-if="profile.allergies.length === 0">—</span>
          </el-descriptions-item>
          <el-descriptions-item label="地址" :span="2">{{ profile.address || '—' }}</el-descriptions-item>
          <el-descriptions-item label="緊急備註" :span="2">
            {{ profile.emergencyNotes || '—' }}
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- Spec § Master §7 / Gap C：觀察設定 (passive monitoring 門檻) -->
      <el-card v-if="settings" shadow="never">
        <template #header>
          <div class="settings-header">
            <span style="font-weight: 600">觀察設定</span>
            <el-button size="small" type="primary" plain @click="openEdit">編輯</el-button>
          </div>
        </template>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="被動監測">
            <el-tag v-if="settings.passiveMonitoringEnabled" type="success" size="small">啟用</el-tag>
            <el-tag v-else type="info" size="small">停用</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="每日 check-in">{{ checkinSummary(settings) }}</el-descriptions-item>
          <el-descriptions-item label="日間無活動門檻">{{ settings.maxInactiveMinutesDaytime }} 分鐘</el-descriptions-item>
          <el-descriptions-item label="夜間無活動門檻">{{ settings.maxInactiveMinutesNight }} 分鐘</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-dialog v-model="editing" title="編輯觀察設定" width="480px">
        <el-form :model="form" label-position="top" size="small">
          <el-form-item label="被動監測（passive monitoring）">
            <el-switch v-model="form.passiveMonitoringEnabled" />
            <span class="hint">關閉後，scanner 不會為此被照顧者建立 missed check-in / no-activity 事件。</span>
          </el-form-item>
          <el-form-item label="預期 check-in 時間（HH:mm:ss，空白＝停用每日 check-in）">
            <el-input v-model="form.expectedCheckinTime" placeholder="09:00:00" clearable />
          </el-form-item>
          <el-form-item label="Grace 分鐘（0–720）">
            <el-input-number v-model="form.checkinGraceMinutes" :min="0" :max="720" :step="5" />
          </el-form-item>
          <el-form-item label="日間無活動上限（分鐘，0–1440）">
            <el-input-number v-model="form.maxInactiveMinutesDaytime" :min="0" :max="1440" :step="10" />
          </el-form-item>
          <el-form-item label="夜間無活動上限（分鐘，0–1440）">
            <el-input-number v-model="form.maxInactiveMinutesNight" :min="0" :max="1440" :step="10" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="editing = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="saveSettings">儲存</el-button>
        </template>
      </el-dialog>

      <el-row :gutter="16" class="grid">
        <el-col :xs="24" :md="12">
          <el-card shadow="never">
            <template #header>
              <span style="font-weight: 600">緊急聯絡人</span>
            </template>
            <el-empty v-if="!contacts || contacts.contacts.length === 0" description="尚未設定" />
            <el-table v-else :data="contacts.contacts" size="small">
              <el-table-column prop="priorityLevel" label="順位" width="60" />
              <el-table-column prop="name" label="姓名" />
              <el-table-column prop="relationship" label="關係" />
              <el-table-column label="電話">
                <template #default="{ row }">{{ maskPhone(row.phone) }}</template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
        <el-col :xs="24" :md="12">
          <el-card shadow="never">
            <template #header>
              <span style="font-weight: 600">近期事件</span>
            </template>
            <el-empty v-if="events.length === 0" description="無事件紀錄" />
            <el-table v-else :data="events" size="small">
              <el-table-column label="類型">
                <template #default="{ row }">{{ eventTypeText(row.eventType) }}</template>
              </el-table-column>
              <el-table-column label="風險" width="90">
                <template #default="{ row }">
                  <el-tag
                    :type="row.riskLevel === 'HIGH' ? 'danger' : row.riskLevel === 'MEDIUM' ? 'warning' : 'info'"
                    size="small"
                  >
                    {{ row.riskLevel }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="時間">
                <template #default="{ row }">
                  {{ new Date(row.occurredAt).toLocaleString('zh-TW') }}
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
      </el-row>
    </main>
  </div>
</template>

<style scoped>
.page { min-height: 100vh; display: flex; flex-direction: column; }
.content { padding: 24px; max-width: 1200px; margin: 0 auto; width: 100%; box-sizing: border-box; display: flex; flex-direction: column; gap: 16px; }
.toolbar { display: flex; gap: 8px; }
.grid { margin-top: 4px; }
.settings-header { display: flex; justify-content: space-between; align-items: center; }
.hint { display: block; font-size: 12px; color: #909399; margin-top: 4px; }
</style>
