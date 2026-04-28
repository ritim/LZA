import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

// 後端走 vite proxy（同源），ngrok / cloudflared 暴露 5173 即可整套對外，
// 不用再開第二個 tunnel 給 8080。
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    strictPort: true,
    // 允許任何外部 host 透過 tunnel 連進 dev server
    allowedHosts: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/actuator': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
