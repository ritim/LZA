<script setup lang="ts">
import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import { useRouter } from 'vue-router';
import axios from 'axios';
import AppHeader from '../components/AppHeader.vue';
import { createCareEvent } from '../api/care';
import type { ApiErrorBody, CreateCareEventRequest } from '../api/types';

const router = useRouter();
const submitting = ref(false);

async function triggerFallEvent() {
  submitting.value = true;
  try {
    const req: CreateCareEventRequest = {
      elderId: 1001,
      source: 'MOBILE_APP',
      eventType: 'FALL_DETECTED',
      occurredAt: new Date().toISOString(),
      metadata: { confidence: 0.92, location: 'living_room' },
    };
    const resp = await createCareEvent(req);
    ElMessage.success(`已建立事件 #${resp.eventId}，跳轉至 workflow #${resp.workflowId}`);
    router.push({ name: 'workflow-detail', params: { id: String(resp.workflowId) } });
  } catch (err) {
    let msg = '建立事件失敗';
    if (axios.isAxiosError(err)) {
      const body = err.response?.data as ApiErrorBody | undefined;
      msg = body?.message ?? body?.error ?? `建立事件失敗（HTTP ${err.response?.status ?? '?'}）`;
    }
    ElMessage.error(msg);
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div class="dashboard">
    <AppHeader />
    <main class="content">
      <el-card shadow="never" class="hero">
        <h2 class="hero-title">Demo 控制台</h2>
        <p class="hero-desc">
          點下方按鈕將以 <code>elderId=1001 / source=MOBILE_APP</code> 建立一筆
          <strong>FALL_DETECTED</strong> 事件，後端會立即啟動 workflow 並建立第一層任務。
        </p>
        <el-button type="primary" size="large" :loading="submitting" @click="triggerFallEvent">
          建立 FALL_DETECTED 事件
        </el-button>
        <p class="hero-note">建立後會自動跳到對應 workflow 詳情頁，並每 2 秒輪詢狀態。</p>
      </el-card>

      <el-card shadow="never" class="flow-card">
        <template #header>
          <span style="font-weight: 600">Demo Flow 概要</span>
        </template>
        <el-steps :active="4" finish-status="success" simple>
          <el-step title="家屬建立事件" />
          <el-step title="L1 Task → 通知家屬" />
          <el-step title="30 秒未確認 → TIMEOUT 升級" />
          <el-step title="L2 / L3 接手或解除" />
        </el-steps>
        <ul class="bullets">
          <li>每個 task 預設 30 秒 deadline，過期 scheduler 自動標記 TIMEOUT 並升級。</li>
          <li>家屬可選 <code>CONFIRM_SAFE</code>（標記安全並結案）、<code>NEED_HELP</code>（升級）、<code>ACKNOWLEDGE</code>（已知悉，繼續觀察）。</li>
          <li>右側 audit timeline 顯示完整責任鏈，每個動作都會寫入 <code>care_audit_log</code>。</li>
        </ul>
      </el-card>
    </main>
  </div>
</template>

<style scoped>
.dashboard {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.content {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-width: 960px;
  margin: 0 auto;
  width: 100%;
  box-sizing: border-box;
}

.hero {
  text-align: center;
}

.hero-title {
  margin: 0 0 8px;
  font-size: 22px;
  color: #303133;
}

.hero-desc {
  color: #606266;
  margin: 0 0 16px;
}

.hero-note {
  margin: 12px 0 0;
  color: #909399;
  font-size: 13px;
}

.flow-card .bullets {
  margin: 16px 0 0;
  padding-left: 20px;
  color: #606266;
  line-height: 1.8;
}

code {
  background: #f4f4f5;
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 13px;
}
</style>
