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
  // 预缓存首页的静态依赖闭包。其它路由的哈希资源在访问时缓存。
  const shellFiles = new Set()
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
            {
              name: 'ihomy-home-precache',
              apply: 'build',
              enforce: 'post',
              generateBundle(_, bundle) {
                shellFiles.clear()
                const collect = (file) => {
                  if (shellFiles.has(file)) return
                  const chunk = bundle[file]
                  if (chunk?.type !== 'chunk') return
                  shellFiles.add(file)
                  for (const style of chunk.viteMetadata?.importedCss || []) shellFiles.add(style)
                  for (const dep of chunk.imports) collect(dep)
                }
                for (const chunk of Object.values(bundle)) {
                  if (chunk.type !== 'chunk') continue
                  const source = chunk.facadeModuleId?.replaceAll('\\', '/') || ''
                  const homeView =
                    /\/(HomeRoute|Home|ElderHome|WarmLayout|MobileLayout|WindowShell|WindowHome|WindowDirectory|WindowMusicPlayer|AppSidebar|SunLightLayer|ChatFloat|CallOverlay|MorningOverlay|ScreensaverOverlay|ChangePasswordDialog)\.vue$/.test(
                      source,
                    )
                  if (chunk.isEntry || homeView || source.endsWith('/i18n/en.js')) collect(chunk.fileName)
                }
              },
            },
            VitePWA({
              registerType: 'autoUpdate',
              includeAssets: ['apple-touch-window.png'],
              manifest: {
                name: 'ihomy',
                short_name: '家庭',
                description: '家庭成员共用内容平台',
                theme_color: '#F1E7D6',
                background_color: '#F1E7D6',
                display: 'standalone',
                start_url: '/',
                icons: [
                  { src: 'pwa-window-192.png', sizes: '192x192', type: 'image/png' },
                  { src: 'pwa-window-512.png', sizes: '512x512', type: 'image/png' },
                  { src: 'pwa-window-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
                ],
              },
              workbox: {
                globPatterns: [
                  'index.html',
                  'registerSW.js',
                  'pwa-*.png',
                  'assets/*.{js,css}',
                  'assets/{sitting-room,walnut}-*.webp',
                  'assets/qweather-icons-*.woff2',
                  'demo/*.svg',
                ],
                manifestTransforms: [
                  (entries) => ({
                    manifest: entries.filter(
                      ({ url }) =>
                        !url.startsWith('assets/') ||
                        shellFiles.has(url) ||
                        /^assets\/qweather-icons-.*\.(css|woff2)$/.test(url) ||
                        /^assets\/(?:sitting-room|walnut)-.*\.webp$/.test(url),
                    ),
                    warnings: [],
                  }),
                ],
                // iframe 里的静态小游戏是独立的页面导航,不能被 SPA 的离线兜底顶成 /index.html(会显示成首页)。
                // /api/ 与 /files/ 同理:安装包下载走 /api/public/product/download → 302 到 /files/<page>/<包名>,
                // 一旦被兜底成 index.html,前端路由找不到这个路径就跳回首页——用户看到的是「点下载却打开了 ihomy」。
                navigateFallbackDenylist: [/^\/mini-games\//, /^\/api\//, /^\/files\//],
                runtimeCaching: [
                  {
                    urlPattern: ({ url, sameOrigin }) =>
                      sameOrigin && /^\/assets\/[^/]+\.(js|css|woff2?|ttf|svg|png|jpe?g|webp|wasm)$/.test(url.pathname),
                    handler: 'CacheFirst',
                    options: {
                      cacheName: 'ihomy-page-assets',
                      expiration: { maxEntries: 320, maxAgeSeconds: 60 * 60 * 24 * 30 },
                      cacheableResponse: { statuses: [200] },
                    },
                  },
                  {
                    // 缓存 GET 接口(离线浏览/弱网);排除敏感接口:保险箱、认证、运维、个人资料
                    // 这些响应含明文密码/凭证/权限,不能落 Cache Storage(登出也无法保证清除)
                    // 安装包下载接口也排除:8MB 级的二进制不配进 api-cache(且它本来就不该被导航缓存命中)
                    urlPattern: ({ url }) =>
                      url.pathname.startsWith('/api/') &&
                      !/^\/api\/(vault|auth|ops|profile|ai\/assistant|chat)(\/|$)/.test(url.pathname) &&
                      !url.pathname.startsWith('/api/public/product/download'),
                    handler: 'NetworkFirst',
                    options: {
                      cacheName: 'api-cache',
                      // 同一设备切换家庭后，离线读取只能命中当前会话。缓存键不保留凭证明文。
                      plugins: [
                        {
                          cacheKeyWillBeUsed: async ({ request }) => {
                            const url = new URL(request.url)
                            const authorization = request.headers.get('Authorization')
                            let scope = 'guest'
                            if (authorization) {
                              const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(authorization))
                              scope = Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, '0')).join('')
                            }
                            url.searchParams.set('__ihomy_session', scope)
                            return url.href
                          },
                        },
                      ],
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
              // 图片查看器也用 throttle，显式跟组件库打包，避免入口反向依赖首个使用 el-image 的工具页。
              { name: 'element-plus', test: /node_modules[\\/](?:element-plus[\\/]|lodash-es[\\/]throttle\.js$)/ },
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
        '/api/ws/': { target: 'ws://localhost:8080', ws: true },
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
        '/api/ws/': { target: 'ws://localhost:8080', ws: true },
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
