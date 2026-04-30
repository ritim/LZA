<script setup lang="ts">
import { computed, ref } from 'vue';
import type { AssessmentQuestion, AssessmentAnswerItem } from '../api/ai';

const props = defineProps<{
  questions: AssessmentQuestion[];
  submitting: boolean;
}>();

const emit = defineEmits<{
  (e: 'submit', answers: AssessmentAnswerItem[]): void;
}>();

// questionId → answer
const answers = ref<Record<string, string>>({});

const allAnswered = computed<boolean>(() => {
  if (props.questions.length === 0) return false;
  return props.questions.every((q) => {
    const a = answers.value[q.id];
    return typeof a === 'string' && a.trim() !== '';
  });
});

function isDangerous(q: AssessmentQuestion, answer: string | undefined): boolean {
  if (!answer || !q.dangerAnswer) return false;
  return q.dangerAnswer.includes(answer);
}

function setAnswer(qid: string, value: string) {
  answers.value[qid] = value;
}

function onSubmit() {
  if (!allAnswered.value) return;
  const payload: AssessmentAnswerItem[] = props.questions.map((q) => ({
    questionId: q.id,
    question: q.question,
    answer: answers.value[q.id] ?? '',
  }));
  emit('submit', payload);
}

function ynuButtonType(value: string, current: string | undefined): 'primary' | 'warning' | 'default' {
  if (current !== value) return 'default';
  if (value === '是') return 'primary';
  if (value === '否') return 'default';
  return 'warning';
}
</script>

<template>
  <el-card shadow="never" class="assess-card">
    <template #header>
      <span class="header-title">情境評估問題</span>
      <span class="header-hint">請依目前狀況回答下列問題</span>
    </template>

    <el-empty v-if="questions.length === 0" description="無評估問題" />

    <div v-else class="q-list">
      <div
        v-for="q in questions"
        :key="q.id"
        class="q-item"
        :class="{ 'q-danger': isDangerous(q, answers[q.id]) }"
      >
        <div class="q-text">
          <span class="q-mark" v-if="answers[q.id]">✓</span>
          {{ q.question }}
        </div>

        <div v-if="q.type === 'YES_NO_UNKNOWN'" class="q-options">
          <el-button
            size="large"
            :type="ynuButtonType('是', answers[q.id])"
            @click="setAnswer(q.id, '是')"
          >是</el-button>
          <el-button
            size="large"
            :type="ynuButtonType('否', answers[q.id])"
            @click="setAnswer(q.id, '否')"
          >否</el-button>
          <el-button
            size="large"
            :type="ynuButtonType('不確定', answers[q.id])"
            @click="setAnswer(q.id, '不確定')"
          >不確定</el-button>
        </div>

        <div v-else-if="q.type === 'SINGLE_CHOICE'" class="q-options">
          <el-button
            v-for="opt in q.options ?? []"
            :key="opt"
            size="large"
            :type="answers[q.id] === opt ? 'primary' : 'default'"
            @click="setAnswer(q.id, opt)"
          >{{ opt }}</el-button>
        </div>

        <div v-else-if="q.type === 'TEXT'" class="q-options">
          <el-input
            :model-value="answers[q.id] ?? ''"
            type="textarea"
            :rows="2"
            placeholder="請輸入..."
            @update:model-value="(v: string) => setAnswer(q.id, v)"
          />
        </div>

        <div v-if="answers[q.id]" class="q-answer">
          已回答：<b>{{ answers[q.id] }}</b>
          <span v-if="isDangerous(q, answers[q.id])" class="danger-tag">⚠ 危險徵象</span>
        </div>
      </div>

      <div class="submit-row">
        <el-button
          type="danger"
          size="large"
          :loading="submitting"
          :disabled="!allAnswered"
          @click="onSubmit"
        >
          提交評估
        </el-button>
        <span v-if="!allAnswered" class="hint">請完成所有題目後再提交</span>
      </div>
    </div>
  </el-card>
</template>

<style scoped>
.assess-card {
  margin-bottom: 16px;
}

.header-title {
  font-weight: 600;
  color: #303133;
  margin-right: 8px;
}

.header-hint {
  font-size: 12px;
  color: #909399;
}

.q-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.q-item {
  padding: 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fafbfc;
}

.q-item.q-danger {
  border-color: #f56c6c;
  background: #fef0f0;
}

.q-text {
  font-size: 16px;
  color: #303133;
  margin-bottom: 8px;
  line-height: 1.5;
}

.q-mark {
  color: #67c23a;
  font-weight: 700;
  margin-right: 4px;
}

.q-options {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.q-answer {
  margin-top: 8px;
  font-size: 13px;
  color: #606266;
}

.danger-tag {
  margin-left: 8px;
  color: #f56c6c;
  font-weight: 600;
}

.submit-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
}

.hint {
  font-size: 12px;
  color: #909399;
}
</style>
