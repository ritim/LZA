<script setup lang="ts">
import { ElMessageBox } from 'element-plus';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';

const auth = useAuthStore();
const router = useRouter();

async function onLogout() {
  try {
    await ElMessageBox.confirm('確定要登出嗎？', '登出', {
      confirmButtonText: '登出',
      cancelButtonText: '取消',
      type: 'warning',
    });
  } catch {
    return;
  }
  await auth.logout();
  router.push('/login');
}
</script>

<template>
  <header class="app-header">
    <div class="brand">
      <el-icon size="20"><HomeFilled /></el-icon>
      <span class="title">AetherCare 照護 Dashboard</span>
    </div>
    <div class="user-block" v-if="auth.isAuthenticated">
      <el-tag type="info" size="default">{{ auth.displayName }}</el-tag>
      <el-button type="primary" link @click="onLogout">
        <el-icon><SwitchButton /></el-icon>
        <span style="margin-left: 4px">登出</span>
      </el-button>
    </div>
  </header>
</template>

<style scoped>
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  background: #ffffff;
  border-bottom: 1px solid #ebeef5;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: #303133;
}

.title {
  font-size: 16px;
}

.user-block {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
