// 应用入口:装配 Pinia(状态)与 Router(路由),引入全局样式与国际化
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import i18n, { ensureLocale } from './i18n'
import 'element-plus/theme-chalk/dark/css-vars.css'
import './styles/main.css'
// 命令式 API(ElMessage/ElMessageBox/ElNotification/ElLoading)不经过模板按需加载,
// 样式必须显式引入,否则弹窗/吐司以无样式裸 DOM 渲染到文档流末尾(不可见)
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/notification/style/css'
import 'element-plus/es/components/loading/style/css'
import { initTheme } from './theme'
import { a11yClick } from './utils/a11y'
import { isNativeApp } from './utils/platform'
import { setBootTokens } from './stores/user'

// 天气图标字体仅在天气面板/光照测试台用,异步加载不阻塞首屏
import('qweather-icons/font/qweather-icons.css')

initTheme()

// 壳内令牌在 Keystore,读取是异步的:必须在挂载前完成,否则首帧会以游客态渲染
// (首页会先拉一批游客数据、再突变登录态)。Web 端直接跳过。
async function loadBootTokens() {
  const { readTokens } = await import('./native/secureStore')
  const tokens = await readTokens()
  if (tokens) setBootTokens(tokens)
}

async function preloadNativeSession() {
  if (!isNativeApp()) return
  try {
    // 原生桥异常绝不能让启动卡住:最多等 1.5s,读不到就按未登录启动
    const timeout = new Promise((resolve) => setTimeout(resolve, 1500))
    await Promise.race([loadBootTokens(), timeout])
  } catch (e) {
    console.warn('[native] 启动读取登录态失败', e)
  }
}

async function boot() {
  // 预读失败也必须继续启动(否则整页空白,比游客态更糟)
  await preloadNativeSession()
  const app = createApp(App)
  app.use(createPinia())
  app.use(router)
  app.use(i18n)
  app.directive('a11y-click', a11yClick)

  // 英文文案是懒加载 chunk:首屏语言为英文时先补齐再挂载,避免闪一下中文/键名
  // (同样加超时兜底:chunk 取不下来也要能挂载,回落中文文案)
  const mount = () => app.mount('#app')
  if (i18n.global.locale.value === 'en') {
    const timeout = new Promise((resolve) => setTimeout(resolve, 1500))
    Promise.race([ensureLocale('en').catch(() => {}), timeout]).then(mount)
  } else {
    mount()
  }
}

boot()
