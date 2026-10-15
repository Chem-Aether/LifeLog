import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
  ],
  optimizeDeps: {
    exclude: ['maplibre-gl'],
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  server: {
    host: '0.0.0.0',   // 监听所有网卡
    port: 5173,         // 端口
    strictPort: false,  // 端口被占用时自动切换
    // 如果前端要调后端 API，加代理：
    proxy: {
      '/api': 'http://localhost:8765',
    },
  },
})