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

export default defineConfig(({ mode }) => {
  // App 构建(--mode app,Copacitor 壳)不挂 PWA:壳内不需要 Service Worker,
  // 且旧 SW 预缓存的 index.html 会顶掉升级后新 APK 的壳(踩坑速查 §7.2 同类问题)
  const isApp = mode === 'app'
  return {
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
      ...(isApp
        ? []
        : [
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
                // 麻将/扑克牌面图集 2.3MB:只在牌类游戏页按需加载,不进预缓存
                // (预缓存会让每个访客首装多下 2.3MB,workbox 默认也会因 >2MiB 直接构建失败)
                globIgnores: ['ruffle/**', 'emulatorjs/**', 'assets/tiles-cards-atlas-*.png'],
                runtimeCaching: [
                  {
                    // 缓存 GET 接口(离线浏览/弱网);排除敏感接口:保险箱、认证、运维、个人资料
                    // 这些响应含明文密码/凭证/权限,不能落 Cache Storage(登出也无法保证清除)
                    urlPattern: ({ url }) =>
                      url.pathname.startsWith('/api/') && !/^\/api\/(vault|auth|ops|profile)(\/|$)/.test(url.pathname),
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
          ]),
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    build: {
      // 浏览器目标:与 Vite 5 的 modules 下限保持一致(家里仍有 iOS < 16.4 的 iPad),
      // 不接受 Vite 8 默认的 Chrome 111 / Safari 16.4;改动前先在最老 iPad 上实测。
      target: ['chrome87', 'edge88', 'firefox78', 'safari14'],
      cssTarget: 'safari14',
      // Vite 8 换 Rolldown 内核:rollupOptions → rolldownOptions,
      // 对象式 output.manualChunks 不再支持,改分组写法(name + test)。
      // 分组走 codeSplitting(advancedChunks 已标 deprecated,等价且构建不再告警);
      // 关掉依赖递归捕获,否则 simple-mind-map 会把 xmind/导出等按需依赖并进同一 chunk。
      // preserveEntrySignatures(Vite 应用构建默认 false) + strictExecutionOrder 是 Rolldown
      // 对「关闭依赖递归捕获」的官方配套:按源码顺序包一层初始化,避免 app 内部 chunk 与入口
      // 形成循环引用(实测缺 strictExecutionOrder 时 Breadcrumb 块在运行时抛 `_ is not a function`)。
      rolldownOptions: {
        output: {
          strictExecutionOrder: true,
          codeSplitting: {
            includeDependenciesRecursively: false,
            groups: [
              { name: 'element-plus', test: /node_modules[\\/]element-plus[\\/]/ },
              { name: 'gsap', test: /node_modules[\\/]gsap[\\/]/ },
              { name: 'vue-i18n', test: /node_modules[\\/]vue-i18n[\\/]/ },
              { name: 'epubjs', test: /node_modules[\\/]epubjs[\\/]/ },
              { name: 'pdfjs', test: /node_modules[\\/]pdfjs-dist[\\/]/ },
              { name: 'simple-mind-map', test: /node_modules[\\/]simple-mind-map[\\/]/ },
              { name: 'three', test: /node_modules[\\/]three[\\/]/ },
            ],
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
  }
})
