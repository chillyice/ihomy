// axios 实例封装:统一附加 JWT、解包业务响应码、401 自动续期重放
import axios from 'axios'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

// 请求拦截:登录态下自动附加 Bearer token
request.interceptors.request.use((config) => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  return config
})

// 刷新令牌的并发互斥标记,避免多个 401 同时触发重复刷新
let refreshing = false

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== undefined && res.code !== 0) {
      ElMessage.error(res.message || '请求失败')
      // 业务层 401(如 refresh 失效):直接登出回登录页
      if (res.code === 401) {
        const userStore = useUserStore()
        userStore.logout()
        router.push('/login')
      }
      return Promise.reject(res)
    }
    // 成功时直接返回 data 字段,调用方无需再取 res.data
    return res.data !== undefined ? res.data : res
  },
  async (error) => {
    const status = error.response?.status
    // HTTP 401 且当前没有其他刷新在进行:静默续期后重放原请求
    if (status === 401 && !refreshing) {
      refreshing = true
      const userStore = useUserStore()
      try {
        await userStore.refresh()
        refreshing = false
        error.config.headers.Authorization = `Bearer ${userStore.token}`
        return request(error.config)
      } catch (e) {
        // 刷新失败(如 refresh token 过期):清空登录态跳登录页
        refreshing = false
        userStore.logout()
        router.push('/login')
        return Promise.reject(e)
      }
    }
    ElMessage.error(error.response?.data?.message || error.message || '网络错误')
    return Promise.reject(error)
  }
)

export default request
