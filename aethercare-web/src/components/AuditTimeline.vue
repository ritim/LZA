<script setup lang="ts">
import { computed } from 'vue';
import type { AuditLogResponse, CareAuditAction } from '../api/types';

const props = defineProps<{ logs: AuditLogResponse[] }>();

type TimelineColor = 'primary' | 'success' | 'warning' | 'danger' | 'info';

const colorMap: Partial<Record<CareAuditAction, TimelineColor>> = {
  EVENT_CREATED: 'primary',
  WORKFLOW_STARTED: 'primary',
  TASK_CREATED: 'info',
  NOTIFICATION_SENT: 'info',
  TASK_ACKNOWLEDGED: 'warning',
  TASK_COMPLETED: 'success',
  TASK_TIMEOUT: 'danger',
  TASK_ESCALATED: 'danger',
  WORKFLOW_RESOLVED: 'success',
  WORKFLOW_UNRESOLVED: 'danger',
  STATE_CONFLICT_SKIPPED: 'info',
  ESCALATION_TRIGGERED: 'warning',
  INSURANCE_QUERY: 'info',
  ASSESSMENT_RECORDED: 'info',
};

function maskActor(id: number | null): string {
  if (id == null) return '系統';
  const s = String(id);
  if (s.length <= 2) return `***${s.slice(-1)}`;
  return `***${s.slice(-2)}`;
}

function formatTime(ts: string): string {
  return new Date(ts).toLocaleString('zh-TW');
}

const sorted = computed(() => [...props.logs].sort((a, b) => a.createdAt.localeCompare(b.createdAt)));
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <span style="font-weight: 600">Audit Timeline</span>
      <span style="margin-left: 8px; color: #909399">共 {{ sorted.length }} 筆</span>
    </template>
    <el-empty v-if="sorted.length === 0" description="尚無稽核紀錄" />
    <el-timeline v-else>
      <el-timeline-item
        v-for="log in sorted"
        :key="log.auditId"
        :timestamp="formatTime(log.createdAt)"
        :type="colorMap[log.action] ?? 'info'"
        placement="top"
      >
        <div class="audit-row">
          <el-tag size="small" :type="colorMap[log.action] ?? 'info'">{{ log.action }}</el-tag>
          <span class="actor">actor: {{ maskActor(log.actorId) }}</span>
        </div>
        <div v-if="log.message" class="message">{{ log.message }}</div>
      </el-timeline-item>
    </el-timeline>
  </el-card>
</template>

<style scoped>
.audit-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.actor {
  font-size: 12px;
  color: #909399;
}

.message {
  color: #606266;
  font-size: 13px;
  white-space: pre-wrap;
}
</style>
