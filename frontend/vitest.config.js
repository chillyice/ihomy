import { defineConfig } from 'vitest/config'
import { fileURLToPath, URL } from 'node:url'

// 单元测试配置:只跑纯逻辑(utils),不加载 VitePWA/AutoImport 等构建插件
export default defineConfig({
  test: {
    environment: 'node',
    include: ['src/**/*.spec.js'],
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
})
