// axios 实例封装:统一附加 JWT、解包业务响应码、令牌过期预检续期、401 自动续期重放
import axios from 'axios'
import { useUserStore } from '@/stores/user'
import { isTokenExpired } from '@/utils/jwt'
import { ElMessage } from 'element-plus'
import router from '@/router'
import i18n from '@/i18n'
import { rewriteAssetUrls } from '@/utils/assetUrl'

// 基址:Web/开发走同源相对路径;Capacitor 壳内由 .env.app 注入服务端绝对地址
// (壳内页面 origin 是 https://localhost,相对路径会打到壳自己)
const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  // 壳内页面 origin 是 https://localhost,接口在 https://ihomy.top 属跨源:
  // 登录/刷新靠响应的 Set-Cookie 下发 ihomy_at(私有照片/电子书/视频按它鉴权),
  // 跨源 XHR 默认不接收 Set-Cookie,不显式允许携带凭证会导致文件接口全部 403。
  // Web 端是同源请求,该开关无副作用。
  withCredentials: true,
  timeout: 15000,
})

// 刷新令牌的共享 Promise:续期的并发请求共享同一次刷新(令牌每次使用即轮换,
// 重复提交会互相判失效),全部等待刷新完成后各自携新 token 重放
let refreshPromise = null

// 共享一次续期;成功后再取 userStore.token 即为新令牌
const renewToken = (userStore) => {
  if (!refreshPromise) {
    refreshPromise = userStore.refresh().finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

// 请求拦截:登录态下自动附加 Bearer token;令牌已过期就先续期再发
request.interceptors.request.use(async (config) => {
  const userStore = useUserStore()
  // 认证类端点自身(登录/续期/登出)不预检,否则续期会递归触发自己;
  // 刷新令牌也过期说明会话是真的没了,按未登录发出去即可(不惊动登录页)
  const isAuthCall = /(^|\/)auth\//.test(config.url || '')
  if (
    !isAuthCall &&
    userStore.token &&
    userStore.refreshToken &&
    isTokenExpired(userStore.token) &&
    !isTokenExpired(userStore.refreshToken, 0)
  ) {
    // 过期令牌对公开接口表现为"未登录"且照常返回 200,不会触发下面的 401 续期 →
    // 家庭天气位置、日出日落等会静默退回游客口径(按 IP 定位),故这里先就地续期
    try {
      await renewToken(userStore)
    } catch (e) {
      // 续期失败:按现状发出去,由响应侧原有流程收尾
    }
  }
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  return config
})

// 独立产品页(meta.standalone:/wallpaper、/kada、/bit-pouch)不套 ihomy 外壳、也没有登录页可跳 ——
// 续期失败就地保持未登录,不要把整页顶到 /login(壁纸场景尤其糟:墙上会变成 ihomy 登录页)
const redirectToLogin = (query) => {
  if (router.currentRoute.value.meta?.standalone) return
  router.push(query ? { name: 'Login', query } : '/login')
}

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== undefined && res.code !== 0) {
      // 5xx 级系统异常附上 tid,用户报障时可直接拿它到运维"详细日志"页检索
      const tid = response.headers?.['x-trace-id']
      ElMessage.error((res.code >= 500 && tid ? `[tid:${tid}] ` : '') + (res.message || i18n.global.t('common.requestFailed')))
      // 业务层 401(如 refresh token 失效):登出但仅写操作跳登录页
      if (res.code === 401) {
        const userStore = useUserStore()
        userStore.logout()
        if (response.config.method !== 'get') redirectToLogin()
      }
      return Promise.reject(res)
    }
    // 成功时直接返回 data 字段,调用方无需再取 res.data
    // 壳内把 /files/... 补成服务端绝对地址(Web 端此行是恒等返回)
    if (res.data !== undefined) return rewriteAssetUrls(res.data)
    return res
  },
  async (error) => {
    const status = error.response?.status
    // HTTP 401:已登录尝试续期(仅重放一次,防死循环),未登录直接跳登录页(仅写操作)
    if (status === 401 && error.config && !error.config._retried) {
      const userStore = useUserStore()
      if (!userStore.token) {
        // 未登录用户触发写操作 401 → 跳登录页带回调
        if (error.config.method !== 'get') redirectToLogin({ redirect: router.currentRoute.value.fullPath })
        return Promise.reject(error)
      }
      try {
        await renewToken(userStore)
        error.config._retried = true
        error.config.headers.Authorization = `Bearer ${userStore.token}`
        return request(error.config)
      } catch (e) {
        // 续期失败(刷新令牌也过期):登出,写操作跳登录页
        userStore.logout()
        if (error.config.method !== 'get') redirectToLogin()
        return Promise.reject(e)
      }
    }
    const tid = error.response?.headers?.['x-trace-id']
    ElMessage.error((tid ? `[tid:${tid}] ` : '') + (error.response?.data?.message || error.message || i18n.global.t('common.networkError')))
    return Promise.reject(error)
  },
)

export default request
