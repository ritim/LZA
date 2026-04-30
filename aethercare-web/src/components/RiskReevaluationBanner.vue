<script setup lang="ts">
import type { RiskReevaluation } from '../api/ai';

defineProps<{
  reeval: RiskReevaluation | null;
}>();
</script>

<template>
  <div v-if="reeval" class="banner-wrap">
    <el-alert
      v-if="reeval.dangerDetected"
      type="error"
      :closable="false"
      show-icon
      class="banner banner-danger"
    >
      <template #title>
        <span class="title">⚠ 偵測到危險徵象</span>
      </template>
      <div class="body">
        <div v-if="reeval.recommendedAction" class="line">
          建議動作：<b>{{ reeval.recommendedAction }}</b>
        </div>
        <div class="line">{{ reeval.message }}</div>
        <div class="line risk">re-eval Risk Level：{{ reeval.riskLevel }}</div>
      </div>
    </el-alert>

    <el-alert
      v-else
      type="success"
      :closable="false"
      show-icon
      class="banner banner-safe"
    >
      <template #title>目前未偵測到立即危險徵象</template>
      <div class="body">
        <div v-if="reeval.message" class="line">{{ reeval.message }}</div>
        <div class="line risk">re-eval Risk Level：{{ reeval.riskLevel }}</div>
      </div>
    </el-alert>
  </div>
</template>

<style scoped>
.banner-wrap {
  margin-bottom: 16px;
}

.title {
  font-weight: 700;
}

.body {
  margin-top: 4px;
  line-height: 1.6;
}

.line {
  font-size: 14px;
}

.risk {
  font-size: 12px;
  color: #606266;
  margin-top: 4px;
}
</style>
