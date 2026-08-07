// 应用入口:装配 Pinia(状态)与 Router(路由),引入全局样式
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import 'vant/lib/index.css'
import './styles/main.css'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
