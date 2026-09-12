// 应用入口:装配 Pinia(状态)与 Router(路由),引入全局样式与国际化
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import i18n from './i18n'
import 'element-plus/theme-chalk/dark/css-vars.css'
import './styles/main.css'
// 命令式 API(ElMessage/ElMessageBox/ElNotification/ElLoading)不经过模板按需加载,
// 样式必须显式引入,否则弹窗/吐司以无样式裸 DOM 渲染到文档流末尾(不可见)
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/notification/style/css'
import 'element-plus/es/components/loading/style/css'
import { initTheme } from './theme'

// 天气图标字体仅在天气面板/光照测试台用,异步加载不阻塞首屏
import('qweather-icons/font/qweather-icons.css')

initTheme()

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(i18n)
app.mount('#app')
