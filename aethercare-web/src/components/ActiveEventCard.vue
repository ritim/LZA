<script setup lang="ts">
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import SlaCountdownBar from './SlaCountdownBar.vue';
import type { DashboardActiveEventItem } from '../api/types';

const props = defineProps<{ event: DashboardActiveEventItem }>();
const router = useRouter();

const isHighRisk = computed(() =>
  props.event.riskLevel === 'HIGH' || props.event.riskLevel === 'CRITICAL',
);

const assigneeText = computed<string>(() => {
  const a = props.event.assignee;
  if (!a) return '';
  return a.displayName || `#${a.id}`;
});

const eventTypeText = computed<string>(() => {
  switch (props.event.type) {
    case 'FALL_DETECTED':
      return '疑似跌倒';
    case 'NO_ACTIVITY':
      return '長時間無活動';
    case 'SOS':
      return 'SOS 求救';
    case 'DAILY_REMINDER':
      return '每日提醒';
    default:
      return props.event.type;
  }
});

function open() {
  router.push({
    name: 'event-detail',
    params: { eventId: String(props.event.id) },
  });
}

function openElder() {
  router.push({
    name: 'care-profile',
    params: { elderId: String(props.event.elder.id) },
  });
}
</script>

<template>
  <el-card
    class="active-card"
    :class="{ 'high-risk': isHighRisk }"
    shadow="hover"
    @click="open"
  >
    <div class="head">
      <span class="badge">
        <span v-if="event.riskLevel === 'CRITICAL'">🚨</span>
        <span v-else-if="event.riskLevel === 'HIGH'">🔴</span>
        <span v-else-if="event.riskLevel === 'MEDIUM'">🟡</span>
        <span v-else>🟢</span>
        {{ event.riskLevel }}
      </span>
      <el-tag type="danger" v-if="isHighRisk" size="small">高風險事件</el-tag>
    </div>
    <h3 class="title">{{ eventTypeText }}</h3>
    <div class="meta">
      <span>被照顧者：</span>
      <a class="elder-link" @click.stop="openElder">
        <template v-if="event.elder.name">
          {{ event.elder.name }}<span v-if="event.elder.age">（{{ event.elder.age }}歲）</span>
        </template>
        <template v-else>#{{ event.elder.id }}</template>
      </a>
    </div>
    <div class="meta" v-if="event.location">地點：{{ event.location }}</div>
    <div class="meta">狀態：{{ event.status }}</div>
    <div class="meta assignee" v-if="event.assignee">
      <span>派發給：{{ assigneeText }}</span>
      <el-tag
        v-if="event.assignee.lineBound"
        size="small"
        type="success"
        effect="plain"
        class="line-chip"
      >
        📱 LINE：{{ event.assignee.lineDisplayName || event.assignee.displayName || '已綁定' }}
      </el-tag>
      <el-tag v-else size="small" type="info" effect="plain" class="line-chip">
        ⚠️ 未綁 LINE
      </el-tag>
    </div>
    <SlaCountdownBar v-if="event.sla" :deadline-at="event.sla.deadlineAt" :status="event.status" />
    <div class="cta">
      <el-button type="primary" :size="isHighRisk ? 'large' : 'default'" @click.stop="open">
        立即處理
      </el-button>
    </div>
  </el-card>
</template>

<style scoped>
.active-card {
  cursor: pointer;
  margin-bottom: 12px;
}

.active-card.high-risk {
  border-left: 4px solid #f56c6c;
}

.head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.badge {
  font-size: 12px;
  color: #606266;
}

.title {
  margin: 4px 0 8px;
  font-size: 18px;
  color: #303133;
}

.meta {
  color: #606266;
  font-size: 13px;
  margin-bottom: 4px;
}

.assignee {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.line-chip {
  font-weight: 500;
}

.elder-link {
  color: #409eff;
  cursor: pointer;
  text-decoration: underline;
}

.elder-link:hover {
  color: #66b1ff;
}

.cta {
  margin-top: 12px;
  text-align: right;
}
</style>
