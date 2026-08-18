// 应用入口:装配 Pinia(状态)与 Router(路由),引入全局样式与国际化
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import i18n from './i18n'
import './styles/main.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import { initTheme } from './theme'

// 天气图标字体仅在天气面板/光照测试台用,异步加载不阻塞首屏
import('qweather-icons/font/qweather-icons.css')

initTheme()

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(i18n)
app.mount('#app')
