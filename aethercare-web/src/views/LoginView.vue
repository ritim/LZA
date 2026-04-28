<script setup lang="ts">
import { reactive, ref } from 'vue';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { useRoute, useRouter } from 'vue-router';
import axios from 'axios';
import { useAuthStore } from '../stores/auth';
import type { ApiErrorBody } from '../api/types';

const router = useRouter();
const route = useRoute();
const auth = useAuthStore();

const formRef = ref<FormInstance | null>(null);
const submitting = ref(false);

const form = reactive({
  username: '',
  password: '',
});

const rules: FormRules = {
  username: [{ required: true, message: '請輸入帳號', trigger: 'blur' }],
  password: [{ required: true, message: '請輸入密碼', trigger: 'blur' }],
};

async function onSubmit() {
  if (!formRef.value) return;
  const ok = await formRef.value.validate().catch(() => false);
  if (!ok) return;

  submitting.value = true;
  try {
    await auth.login(form.username, form.password);
    ElMessage.success(`歡迎，${auth.displayName}`);
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard';
    router.push(redirect);
  } catch (err) {
    let msg = '登入失敗';
    if (axios.isAxiosError(err)) {
      const body = err.response?.data as ApiErrorBody | undefined;
      msg = body?.message ?? body?.error ?? `登入失敗（HTTP ${err.response?.status ?? '?'}）`;
    }
    ElMessage.error(msg);
  } finally {
    submitting.value = false;
  }
}

function fill(account: 'family01' | 'admin') {
  if (account === 'family01') {
    form.username = 'family01';
    form.password = 'family123';
  } else {
    form.username = 'admin';
    form.password = 'admin123';
  }
}
</script>

<template>
  <div class="login-page">
    <el-card class="login-card" shadow="always">
      <template #header>
        <div class="header">
          <el-icon size="22"><HomeFilled /></el-icon>
          <span class="title">AetherCare 照護 Dashboard</span>
        </div>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="onSubmit"
      >
        <el-form-item label="帳號" prop="username">
          <el-input v-model="form.username" autocomplete="username" placeholder="username" />
        </el-form-item>

        <el-form-item label="密碼" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            autocomplete="current-password"
            show-password
            placeholder="password"
            @keyup.enter="onSubmit"
          />
        </el-form-item>

        <el-button type="primary" :loading="submitting" style="width: 100%" @click="onSubmit">
          登入
        </el-button>
      </el-form>

      <el-divider>Demo 帳號</el-divider>

      <div class="demo-block">
        <el-button size="small" @click="fill('family01')">family01 / family123</el-button>
        <el-button size="small" @click="fill('admin')">admin / admin123</el-button>
      </div>

      <div class="hint">
        後端 (aethercare-api) 必須先啟動於 <code>http://localhost:8080</code>。
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #e0eafc, #cfdef3);
}

.login-card {
  width: 380px;
}

.header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.title {
  font-size: 16px;
}

.demo-block {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: center;
}

.hint {
  margin-top: 16px;
  font-size: 12px;
  color: #909399;
  text-align: center;
}

code {
  background: #f4f4f5;
  padding: 1px 4px;
  border-radius: 3px;
}
</style>
