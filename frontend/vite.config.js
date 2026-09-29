import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { VitePWA } from 'vite-plugin-pwa'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath, URL } from 'node:url'
import { readFileSync } from 'node:fs'

// 版本号唯一事实来源:仓库根 VERSION 文件(规则见 AGENTS.md「版本号规则」)
// 构建时注入 __APP_VERSION__ 供页脚与运维页展示;读取失败回落 'unknown',不阻塞构建
const appVersion = (() => {
  try {
    return readFileSync(fileURLToPath(new URL('../VERSION', import.meta.url)), 'utf8').trim()
  } catch {
    return 'unknown'
  }
})()

export default defineConfig({
  define: {
    __APP_VERSION__: JSON.stringify(appVersion),
  },
  plugins: [
    vue(),
    AutoImport({
      imports: ['vue', 'vue-router', 'pinia'],
      resolvers: [ElementPlusResolver()],
    }),
    Components({
      resolvers: [ElementPlusResolver()],
    }),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.svg', 'apple-touch-icon.png'],
      manifest: {
        name: 'ihomy',
        short_name: '家庭',
        description: '家庭成员共用内容平台',
        theme_color: '#1F3A5F',
        background_color: '#ffffff',
        display: 'standalone',
        start_url: '/',
        icons: [
          { src: 'pwa-192x192.png', sizes: '192x192', type: 'image/png' },
          { src: 'pwa-512x512.png', sizes: '512x512', type: 'image/png' },
          { src: 'pwa-512x512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,ico,png,svg,woff2}'],
        globIgnores: ['ruffle/**', 'emulatorjs/**'],
        runtimeCaching: [
          {
            // 缓存 GET 接口(离线浏览/弱网);排除敏感接口:保险箱、认证、运维、个人资料
            // 这些响应含明文密码/凭证/权限,不能落 Cache Storage(登出也无法保证清除)
            urlPattern: ({ url }) =>
              url.pathname.startsWith('/api/') &&
              !/^\/api\/(vault|auth|ops|profile)(\/|$)/.test(url.pathname),
            handler: 'NetworkFirst',
            options: {
              cacheName: 'api-cache',
              expiration: { maxEntries: 100, maxAgeSeconds: 60 * 60 * 24 },
              cacheableResponse: { statuses: [0, 200] },
            },
          },
        ],
      },
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'element-plus': ['element-plus'],
          'gsap': ['gsap'],
          'vue-i18n': ['vue-i18n'],
          'epubjs': ['epubjs'],
          'pdfjs': ['pdfjs-dist'],
          'simple-mind-map': ['simple-mind-map'],
          'three': ['three'],
        },
      },
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // 上传文件:开发模式后端 context-path=/api,files 映射在 /api/files 下,故代理需 rewrite(生产由 nginx /files/ 直接托管磁盘)
      '/files': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => '/api' + path,
      },
    },
  },
  // vite preview 同样代理后端(本地验证生产构建用)
  preview: {
    port: 4173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/files': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => '/api' + path,
      },
    },
  },
})
