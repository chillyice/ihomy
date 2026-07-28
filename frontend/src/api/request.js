import axios from 'axios'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

request.interceptors.request.use((config) => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  return config
})

let refreshing = false

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== undefined && res.code !== 0) {
      ElMessage.error(res.message || '请求失败')
      if (res.code === 401) {
        const userStore = useUserStore()
        userStore.logout()
        router.push('/login')
      }
      return Promise.reject(res)
    }
    return res.data !== undefined ? res.data : res
  },
  async (error) => {
    const status = error.response?.status
    if (status === 401 && !refreshing) {
      refreshing = true
      const userStore = useUserStore()
      try {
        await userStore.refresh()
        refreshing = false
        error.config.headers.Authorization = `Bearer ${userStore.token}`
        return request(error.config)
      } catch (e) {
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
