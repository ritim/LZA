<script setup lang="ts">
import { computed } from 'vue';
import type { CareActionType } from '../api/types';

/**
 * Spec §7.5：依 action 類型顯示不同確認文案；CALL_EMERGENCY 顯示 spec 要求的撥打 119 提示。
 */
const props = defineProps<{
  modelValue: boolean;
  actionType: CareActionType | null;
  loading?: boolean;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void;
  (e: 'confirm'): void;
  (e: 'cancel'): void;
}>();

const dialogModel = computed<boolean>({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
});

interface Copy {
  title: string;
  message: string;
  confirmText: string;
  type: 'primary' | 'danger' | 'warning';
}

const FALLBACK: Copy = {
  title: '請確認',
  message: '請確認此操作。',
  confirmText: '確認',
  type: 'warning',
};

const COPY: Record<CareActionType, Copy> = {
  CONFIRM_SAFE: {
    title: '請確認長者安全',
    message: '你是否已親自確認長者安全？此操作會記錄在照護責任鏈中。',
    confirmText: '確認安全',
    type: 'primary',
  },
  NEED_HELP: {
    title: '請確認需要升級協助',
    message: '系統將通知下一順位照顧者並升級任務，此操作會記錄在照護責任鏈中。',
    confirmText: '確認升級',
    type: 'danger',
  },
  ACKNOWLEDGE: {
    title: '請確認知悉事件',
    message: '此操作只表示已知悉，不會結案，仍需後續處置。',
    confirmText: '確認知悉',
    type: 'warning',
  },
  CALL_EMERGENCY: {
    title: '撥打 119',
    message:
      '請立即撥打 119，並告知：長者姓名、地址、疑似跌倒、是否清醒、是否有頭部撞擊。完成後此操作會記錄為已升級。',
    confirmText: '已撥打，記錄事件',
    type: 'danger',
  },
  ESCALATE: {
    title: '通知下一順位聯絡人',
    message: '系統將通知下一順位照顧者，此操作會記錄在照護責任鏈中。',
    confirmText: '確認通知',
    type: 'danger',
  },
  CALL_ELDER: {
    title: '撥打長者電話',
    message: '此操作會記錄在照護責任鏈，但不會改變 workflow 狀態。',
    confirmText: '記錄已聯絡',
    type: 'warning',
  },
  CALL_SECOND_CONTACT: {
    title: '聯絡第二聯絡人',
    message: '系統將通知第二順位聯絡人，此操作會記錄在照護責任鏈中。',
    confirmText: '確認聯絡',
    type: 'danger',
  },
  REQUEST_HELP: {
    title: '請求協助',
    message: '系統將升級任務並通知下一層級照顧者。',
    confirmText: '確認請求',
    type: 'danger',
  },
  MARK_UNABLE_TO_CONFIRM: {
    title: '無法確認被照顧者狀態',
    message: '系統將升級任務，此操作會記錄在照護責任鏈中。',
    confirmText: '確認升級',
    type: 'danger',
  },
  ADD_NOTE: {
    title: '加入備註',
    message: '此操作只新增一筆 audit note，不會改變狀態。',
    confirmText: '記錄備註',
    type: 'warning',
  },
  CALL_NO_ANSWER: {
    title: '記錄電話未接',
    message:
      '此動作會記錄電話未接，但**不會結案**也不會升級事件。建議再試其他方式聯絡，或請第二聯絡人到場確認（spec § Gap D）。',
    confirmText: '記錄電話未接',
    type: 'warning',
  },
};

const copy = computed<Copy>(() => {
  const t = props.actionType;
  if (!t) return FALLBACK;
  return COPY[t] ?? FALLBACK;
});
</script>

<template>
  <el-dialog v-model="dialogModel" :title="copy.title" width="460" append-to-body>
    <p style="margin: 0; line-height: 1.6; white-space: pre-wrap">{{ copy.message }}</p>
    <template #footer>
      <span>
        <el-button @click="emit('cancel')">返回</el-button>
        <el-button :type="copy.type" :loading="loading" @click="emit('confirm')">
          {{ copy.confirmText }}
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>
