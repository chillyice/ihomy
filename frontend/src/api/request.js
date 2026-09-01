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

// 刷新令牌的共享 Promise:access token 过期时并发的多个 401 共享同一次刷新,
// 全部等待刷新完成后各自携新 token 重放,避免刷新期间的请求被误报"登录已过期"
let refreshPromise = null

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== undefined && res.code !== 0) {
      ElMessage.error(res.message || '请求失败')
      // 业务层 401(如 refresh token 失效):登出但仅写操作跳登录页
      if (res.code === 401) {
        const userStore = useUserStore()
        userStore.logout()
        if (response.config.method !== 'get') router.push('/login')
      }
      return Promise.reject(res)
    }
    // 成功时直接返回 data 字段,调用方无需再取 res.data
    return res.data !== undefined ? res.data : res
  },
  async (error) => {
    const status = error.response?.status
    // HTTP 401:已登录尝试刷新续期(仅重放一次,防死循环),未登录直接跳登录页(仅写操作)
    if (status === 401 && error.config && !error.config._retried) {
      const userStore = useUserStore()
      if (!userStore.token) {
        // 未登录用户触发写操作 401 → 跳登录页带回调
        if (error.config.method !== 'get') router.push({ name: 'Login', query: { redirect: router.currentRoute.value.fullPath } })
        return Promise.reject(error)
      }
      if (!refreshPromise) {
        refreshPromise = userStore.refresh()
          .catch((e) => {
            userStore.logout()
            throw e
          })
          .finally(() => { refreshPromise = null })
      }
      try {
        await refreshPromise
        error.config._retried = true
        error.config.headers.Authorization = `Bearer ${userStore.token}`
        return request(error.config)
      } catch (e) {
        // 刷新失败(refresh token 过期):已登出,写操作跳登录页
        if (error.config.method !== 'get') router.push('/login')
        return Promise.reject(e)
      }
    }
    ElMessage.error(error.response?.data?.message || error.message || '网络错误')
    return Promise.reject(error)
  }
)

export default request
