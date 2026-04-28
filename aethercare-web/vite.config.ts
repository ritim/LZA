import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

// 後端 CORS 已加入 5173；前端直接 axios 呼叫 http://localhost:8080，不走 proxy。
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    strictPort: true,
  },
});
