<script setup lang="ts">
// Spec § Master §0：caregiver 端 LINE 綁定卡片。
// 未綁定：「綁定 LINE」按鈕 → 開 dialog 顯示綁定碼 + 步驟說明。
// dialog 開啟期間每 3 秒輪詢狀態；綁定完成自動關閉 dialog。
// 已綁定：顯示綁定資訊 + 解綁按鈕。
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import axios from 'axios';
import {
  getLineBindingStatus,
  startLineBinding,
  unbindLine,
  type LineBindingStatus,
  type StartLineBindingResponse,
} from '../api/caregiver';
import type { ApiErrorBody } from '../api/types';

const status = ref<LineBindingStatus | null>(null);
const loading = ref<boolean>(false);
const issuing = ref<boolean>(false);
const dialogOpen = ref<boolean>(false);
const issued = ref<StartLineBindingResponse | null>(null);
const remainingSec = ref<number>(0);

let pollTimer: ReturnType<typeof setInterval> | null = null;
let countdownTimer: ReturnType<typeof setInterval> | null = null;

const bound = computed<boolean>(() => status.value?.bound === true);

async function refresh(showSpinner = false) {
  if (showSpinner) loading.value = true;
  try {
    status.value = await getLineBindingStatus();
  } catch {
    if (showSpinner) ElMessage.error('載入 LINE 綁定狀態失敗');
  } finally {
    if (showSpinner) loading.value = false;
  }
}

async function onIssue() {
  issuing.value = true;
  try {
    issued.value = await startLineBinding();
    dialogOpen.value = true;
    remainingSec.value = computeRemaining(issued.value.expiresAt);
    startPolling();
    startCountdown();
  } catch (err) {
    let msg = '產生綁定碼失敗';
    if (axios.isAxiosError(err)) {
      const body = err.response?.data as ApiErrorBody | undefined;
      msg = body?.message ?? body?.error ?? msg;
    }
    ElMessage.error(msg);
  } finally {
    issuing.value = false;
  }
}

async function onUnbind() {
  try {
    await ElMessageBox.confirm(
      '解綁後將不再透過 LINE 收到通知，要繼續嗎？',
      '解綁 LINE',
      { confirmButtonText: '解綁', cancelButtonText: '取消', type: 'warning' },
    );
  } catch {
    return;
  }
  try {
    await unbindLine();
    ElMessage.success('已解綁');
    await refresh(false);
  } catch {
    ElMessage.error('解綁失敗');
  }
}

function computeRemaining(iso: string): number {
  return Math.max(0, Math.round((new Date(iso).getTime() - Date.now()) / 1000));
}

function startCountdown() {
  stopCountdown();
  countdownTimer = setInterval(() => {
    if (!issued.value) return stopCountdown();
    remainingSec.value = computeRemaining(issued.value.expiresAt);
    if (remainingSec.value <= 0) stopCountdown();
  }, 1000);
}

function stopCountdown() {
  if (countdownTimer) {
    clearInterval(countdownTimer);
    countdownTimer = null;
  }
}

function startPolling() {
  stopPolling();
  pollTimer = setInterval(async () => {
    await refresh(false);
    if (bound.value) {
      ElMessage.success('LINE 綁定成功！');
      closeDialog();
    }
  }, 3000);
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
}

function closeDialog() {
  dialogOpen.value = false;
  issued.value = null;
  stopPolling();
  stopCountdown();
}

function formatBoundAt(iso: string | null): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('zh-TW', {
    hour12: false,
    timeZone: 'Asia/Taipei',
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  });
}

async function copyCode() {
  if (!issued.value) return;
  try {
    await navigator.clipboard.writeText(issued.value.code);
    ElMessage.success('已複製');
  } catch {
    ElMessage.warning('瀏覽器禁用剪貼簿，請手動複製');
  }
}

onMounted(() => refresh(true));
onBeforeUnmount(() => {
  stopPolling();
  stopCountdown();
});
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="header">
        <span class="title">LINE 通知綁定</span>
        <el-tag v-if="bound" type="success" size="small">已綁定</el-tag>
        <el-tag v-else type="info" size="small">未綁定</el-tag>
      </div>
    </template>

    <div v-if="loading" class="loading">載入中…</div>

    <template v-else-if="bound && status">
      <p class="info-line">
        綁定 LINE userId：
        <code>{{ status.lineUserId?.substring(0, 4) }}***{{ status.lineUserId?.slice(-3) }}</code>
      </p>
      <p class="info-line" v-if="status.lineDisplayName">
        顯示名稱：{{ status.lineDisplayName }}
      </p>
      <p class="info-line">綁定時間：{{ formatBoundAt(status.boundAt) }}</p>
      <el-button type="danger" plain size="small" @click="onUnbind">解綁</el-button>
    </template>

    <template v-else>
      <p class="hint">
        綁定後，家中長輩按下「我今天還好」或觸發 SOS 時，您會在 LINE 收到即時通知。
      </p>
      <el-button type="primary" :loading="issuing" @click="onIssue">
        產生綁定碼
      </el-button>
    </template>

    <el-dialog
      v-model="dialogOpen"
      title="綁定 LINE"
      width="440px"
      :close-on-click-modal="false"
      @close="closeDialog"
    >
      <div v-if="issued" class="dialog-body">
        <div class="code-row">
          <span class="code-text">{{ issued.code }}</span>
          <el-button size="small" plain @click="copyCode">複製</el-button>
        </div>
        <div class="countdown" :class="{ expired: remainingSec <= 0 }">
          <template v-if="remainingSec > 0">
            剩餘 {{ Math.floor(remainingSec / 60) }} 分 {{ remainingSec % 60 }} 秒
          </template>
          <template v-else>
            已過期，請按「產生綁定碼」重發
          </template>
        </div>
        <ol class="steps">
          <li>用手機 LINE 加入 AetherCare 官方帳號為好友（QR code 由管理員提供）。</li>
          <li>在 OA 對話框內<strong>傳送上方的綁定碼</strong>。</li>
          <li>OA 回覆「綁定成功」後，此視窗會自動關閉並更新狀態。</li>
        </ol>
        <div class="status-hint">
          <span class="dot pulse"></span> 等待 LINE 回傳綁定中…（每 3 秒檢查一次）
        </div>
      </div>
      <template #footer>
        <el-button @click="closeDialog">關閉</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<style scoped>
.header { display: flex; justify-content: space-between; align-items: center; }
.title { font-weight: 600; }
.loading { color: #909399; font-size: 12px; }
.hint { color: #606266; line-height: 1.6; margin: 0 0 12px; }
.info-line { margin: 4px 0; color: #303133; font-size: 13px; }
.info-line code { background: #f0f2f5; padding: 1px 6px; border-radius: 3px; }

.dialog-body { display: flex; flex-direction: column; gap: 12px; }
.code-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 16px;
  background: #f0f9eb;
  border: 2px dashed #67c23a;
  border-radius: 8px;
}
.code-text {
  font-family: 'SF Mono', Monaco, monospace;
  font-size: 28px;
  letter-spacing: 4px;
  font-weight: 700;
  color: #67c23a;
}
.countdown { text-align: center; color: #606266; font-size: 13px; }
.countdown.expired { color: #f56c6c; font-weight: 600; }
.steps { color: #606266; line-height: 1.7; padding-left: 20px; margin: 0; }
.steps strong { color: #303133; }
.status-hint { color: #909399; font-size: 12px; display: flex; align-items: center; gap: 6px; }
.dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; background: #409eff; }
.dot.pulse { animation: pulse 1.4s ease-in-out infinite; }
@keyframes pulse {
  0%, 100% { opacity: 0.3; }
  50% { opacity: 1; }
}
</style>
