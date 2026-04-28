<script setup lang="ts">
import { onMounted } from 'vue';
import { useAuthStore } from './stores/auth';

const auth = useAuthStore();
onMounted(() => auth.initFromStorage());
</script>

<template>
  <div id="app-root">
    <RouterView />
  </div>
</template>

<style>
html,
body,
#app {
  height: 100%;
  margin: 0;
  padding: 0;
  font-family:
    -apple-system,
    BlinkMacSystemFont,
    'Segoe UI',
    'PingFang TC',
    'Microsoft JhengHei',
    Roboto,
    sans-serif;
  background: #f5f7fa;
  color: #1f2d3d;
}

#app-root {
  min-height: 100vh;
}
</style>
