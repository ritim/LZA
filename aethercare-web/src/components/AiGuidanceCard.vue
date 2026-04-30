<script setup lang="ts">
import { computed } from 'vue';
import type { CareGuidance } from '../api/ai';

const props = defineProps<{
  guidance: CareGuidance | null;
  loading: boolean;
}>();

const generatedAtText = computed<string>(() => {
  if (!props.guidance?.generatedAt) return '';
  return new Date(props.guidance.generatedAt).toLocaleString('zh-TW');
});
</script>

<template>
  <el-card shadow="never" class="ai-card">
    <template #header>
      <div class="card-header">
        <span class="title">AI 照護建議</span>
        <span v-if="generatedAtText" class="time">產生於 {{ generatedAtText }}</span>
      </div>
    </template>

    <el-skeleton v-if="loading && !guidance" :rows="4" animated />

    <template v-else-if="guidance">
      <el-alert
        v-if="guidance.summary"
        :title="guidance.summary"
        type="info"
        :closable="false"
        show-icon
        class="summary"
      />

      <div v-if="guidance.guidance.length > 0" class="section">
        <div class="section-title">建議步驟</div>
        <ol class="guidance-list">
          <li v-for="(step, i) in guidance.guidance" :key="i">{{ step }}</li>
        </ol>
      </div>

      <el-alert
        v-if="guidance.dangerSigns.length > 0"
        type="error"
        :closable="false"
        show-icon
        class="danger"
      >
        <template #title>危險徵象（若出現任一項請立即升級）</template>
        <ul class="danger-list">
          <li v-for="(sign, i) in guidance.dangerSigns" :key="i">{{ sign }}</li>
        </ul>
      </el-alert>

      <div v-if="guidance.disclaimer" class="disclaimer">
        {{ guidance.disclaimer }}
      </div>
    </template>

    <el-empty v-else description="尚無 AI 建議" />
  </el-card>
</template>

<style scoped>
.ai-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.title {
  font-weight: 600;
  color: #303133;
}

.time {
  font-size: 12px;
  color: #909399;
}

.summary {
  margin-bottom: 12px;
}

.section {
  margin-bottom: 12px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 6px;
}

.guidance-list {
  margin: 0;
  padding-left: 20px;
  color: #303133;
  line-height: 1.7;
}

.guidance-list li {
  margin-bottom: 4px;
}

.danger {
  margin-bottom: 12px;
}

.danger-list {
  margin: 6px 0 0;
  padding-left: 20px;
  line-height: 1.6;
}

.disclaimer {
  margin-top: 12px;
  padding-top: 8px;
  border-top: 1px dashed #ebeef5;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}
</style>
