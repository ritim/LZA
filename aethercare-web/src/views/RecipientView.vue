<script setup lang="ts">
import { computed, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import axios from 'axios';
import { useRoute } from 'vue-router';
import { postCheckIn, postSos, postStatusReport } from '../api/recipient';
import type { ApiErrorBody } from '../api/types';

const route = useRoute();

const recipientId = computed<number>(() => {
  const raw = route.query.id;
  const parsed = typeof raw === 'string' ? Number.parseInt(raw, 10) : NaN;
  return Number.isFinite(parsed) && parsed > 0 ? parsed : 1;
});

const submitting = ref<null | 'CHECK_IN' | 'SOS' | 'FEELING_UNWELL' | 'CALL_FAMILY'>(null);
const lastAction = ref<{ kind: string; at: string; message: string } | null>(null);

const familyTel = computed<string>(() => {
  const raw = route.query.familyTel;
  return typeof raw === 'string' && raw ? raw : 'tel:0911000111';
});

function describeError(err: unknown): string {
  if (axios.isAxiosError(err)) {
    const body = err.response?.data as ApiErrorBody | undefined;
    return body?.message ?? body?.error ?? `HTTP ${err.response?.status ?? '?'}`;
  }
  return err instanceof Error ? err.message : String(err);
}

function nowDisplay(): string {
  return new Date().toLocaleTimeString('zh-TW', { hour12: false, timeZone: 'Asia/Taipei' });
}

function recordResult(kind: string, message: string) {
  lastAction.value = { kind, at: nowDisplay(), message };
}

async function onCheckIn() {
  submitting.value = 'CHECK_IN';
  try {
    const resp = await postCheckIn(recipientId.value);
    recordResult('我今天還好', `已通知家人您今天平安（活動 #${resp.activityLogId}）。`);
    ElMessage.success('已通知家人您今天平安。');
  } catch (err) {
    // CHECK_IN 失敗不應該讓使用者焦慮，給溫和提示。
    recordResult('我今天還好', `紀錄暫時送不出，請改用「打給家人」：${describeError(err)}`);
    ElMessage.warning(`暫時無法通知，請改用「打給家人」：${describeError(err)}`);
  } finally {
    submitting.value = null;
  }
}

async function onSos() {
  const confirmed = await ElMessageBox.confirm(
    '系統會立即通知您的家人協助。確認需要幫忙嗎？',
    '我需要幫忙',
    { confirmButtonText: '是，立即通知家人', cancelButtonText: '取消', type: 'warning' },
  ).catch(() => false);
  if (!confirmed) return;

  submitting.value = 'SOS';
  try {
    const resp = await postSos(recipientId.value);
    recordResult('我需要幫忙', `家人已收到通知（事件 #${resp.eventId}），請保持手機暢通。`);
    ElMessage.success(`已通知家人，事件編號 ${resp.eventId}`);
  } catch (err) {
    recordResult('我需要幫忙', `自動通知失敗，請立即「打給家人」：${describeError(err)}`);
    ElMessage.error(`自動通知失敗，請改撥電話：${describeError(err)}`);
  } finally {
    submitting.value = null;
  }
}

async function onFeelingUnwell() {
  const symptom = await ElMessageBox.prompt('請簡短描述哪裡不舒服（例如：頭痛、胸悶、想吐）', '身體不舒服', {
    confirmButtonText: '通知家人',
    cancelButtonText: '取消',
    inputPlaceholder: '可留空或簡單描述',
    inputValidator: () => true,
  }).catch(() => null);
  if (!symptom) return;

  submitting.value = 'FEELING_UNWELL';
  try {
    const resp = await postStatusReport(recipientId.value, {
      symptom: symptom.value || '未具體描述',
    });
    recordResult('身體不舒服', `家人已收到您的不適回報（事件 #${resp.eventId}）。`);
    ElMessage.success('家人已收到您的不適回報。');
  } catch (err) {
    recordResult('身體不舒服', `自動通知失敗，請立即「打給家人」：${describeError(err)}`);
    ElMessage.error(`自動通知失敗，請改撥電話：${describeError(err)}`);
  } finally {
    submitting.value = null;
  }
}

function onCallFamily() {
  recordResult('打給家人', '撥號中…請拿起電話。');
  window.location.href = familyTel.value;
}
</script>

<template>
  <div class="recipient-page">
    <header class="page-header">
      <h1>您好，請選擇您現在要做的事</h1>
      <p class="hint">按下大按鈕後，家人會立即收到通知。</p>
    </header>

    <main class="actions">
      <button
        class="action-btn ok"
        :disabled="submitting !== null"
        :aria-busy="submitting === 'CHECK_IN'"
        @click="onCheckIn"
      >
        <span class="emoji" aria-hidden="true">🙂</span>
        <span class="label">我今天還好</span>
        <span class="sub">告訴家人您平安</span>
      </button>

      <button
        class="action-btn help"
        :disabled="submitting !== null"
        :aria-busy="submitting === 'SOS'"
        @click="onSos"
      >
        <span class="emoji" aria-hidden="true">🆘</span>
        <span class="label">我需要幫忙</span>
        <span class="sub">立即通知家人</span>
      </button>

      <button
        class="action-btn unwell"
        :disabled="submitting !== null"
        :aria-busy="submitting === 'FEELING_UNWELL'"
        @click="onFeelingUnwell"
      >
        <span class="emoji" aria-hidden="true">🤒</span>
        <span class="label">身體不舒服</span>
        <span class="sub">告訴家人哪裡不適</span>
      </button>

      <a class="action-btn call" :href="familyTel" @click="onCallFamily">
        <span class="emoji" aria-hidden="true">📞</span>
        <span class="label">打給家人</span>
        <span class="sub">直接撥電話</span>
      </a>
    </main>

    <section v-if="lastAction" class="last-action" role="status" aria-live="polite">
      <div class="kind">{{ lastAction.kind }}</div>
      <div class="message">{{ lastAction.message }}</div>
      <div class="ts">{{ lastAction.at }}</div>
    </section>
  </div>
</template>

<style scoped>
.recipient-page {
  min-height: 100vh;
  padding: 24px 20px 40px;
  background: #fff8e7;
  color: #1f2d3d;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  max-width: 720px;
  margin: 0 auto;
}

.page-header {
  text-align: center;
  margin-bottom: 24px;
}

.page-header h1 {
  font-size: 28px;
  margin: 0 0 8px;
  font-weight: 700;
}

.page-header .hint {
  font-size: 18px;
  color: #4b5b6b;
  margin: 0;
}

.actions {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
}

.action-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  text-decoration: none;
  font-family: inherit;
  border: 4px solid transparent;
  border-radius: 20px;
  padding: 28px 16px;
  min-height: 140px;
  cursor: pointer;
  transition: transform 0.05s ease, box-shadow 0.1s ease, opacity 0.1s ease;
  box-shadow: 0 4px 0 rgba(0, 0, 0, 0.08);
  color: #1f2d3d;
}

.action-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.action-btn:active:not(:disabled) {
  transform: translateY(2px);
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.08);
}

.action-btn .emoji {
  font-size: 56px;
  line-height: 1;
  margin-bottom: 6px;
}

.action-btn .label {
  font-size: 30px;
  font-weight: 800;
  letter-spacing: 1px;
}

.action-btn .sub {
  font-size: 18px;
  margin-top: 6px;
  color: #4b5b6b;
}

.action-btn.ok {
  background: #d6f5d6;
  border-color: #4caf50;
}
.action-btn.help {
  background: #ffe1e1;
  border-color: #d32f2f;
  color: #b71c1c;
}
.action-btn.help .sub {
  color: #b71c1c;
}
.action-btn.unwell {
  background: #fff4cc;
  border-color: #f5a623;
}
.action-btn.call {
  background: #d6e9ff;
  border-color: #1976d2;
}

.last-action {
  margin-top: 28px;
  padding: 16px 18px;
  background: #ffffff;
  border-radius: 12px;
  border: 2px solid #e6e9ef;
  font-size: 18px;
}

.last-action .kind {
  font-weight: 700;
  font-size: 20px;
  margin-bottom: 4px;
}

.last-action .message {
  color: #1f2d3d;
}

.last-action .ts {
  color: #8693a3;
  font-size: 14px;
  margin-top: 6px;
  text-align: right;
}

@media (min-width: 480px) {
  .actions {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
