<script setup lang="ts">
import { computed } from 'vue';
import type { CareWorkflowStatus } from '../api/types';

const props = defineProps<{ status: CareWorkflowStatus }>();

type TagType = 'primary' | 'success' | 'warning' | 'danger' | 'info';

const tagType = computed<TagType>(() => {
  switch (props.status) {
    case 'NEW':
      return 'info';
    case 'ACTIVE':
    case 'WAITING_RESPONSE':
      return 'primary';
    case 'ACKNOWLEDGED':
      return 'warning';
    case 'ESCALATED':
      return 'danger';
    case 'RESOLVED':
      return 'success';
    case 'UNRESOLVED':
      return 'danger';
    default:
      return 'info';
  }
});
</script>

<template>
  <el-tag :type="tagType" size="large" effect="dark">
    {{ status }}
  </el-tag>
</template>
