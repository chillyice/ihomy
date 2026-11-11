// 应用锁(仅原生壳):离开 App 超过 5 分钟再回来,先过一道系统指纹/人脸。
//
// 口径:
// - pausedAt 只放内存,不落 localStorage;进程被系统杀掉后重开不做二次校验(冷启动仍可直接进)。
// - 本机没有可用生物识别、或指纹被系统移除时自动关掉开关,绝不把用户永久挡在门外。
// - 取消/失败一律回登录页(还有密码可走),不留在当前页面。
import { App } from '@capacitor/app'
import { isNativeApp } from '@/utils/platform'
import { useUserStore } from '@/stores/user'
import router from '@/router'

/** 开关(默认关,由用户在「设置 → 手机客户端」里打开) */
const ON_KEY = 'ihomy:app:lock:on'
/** 切后台多久后回前台需要再次验证 */
export const APP_LOCK_AFTER_MS = 5 * 60 * 1000

let pausedAt = 0
let locking = false
let installed = false

export function appLockEnabled() {
  try {
    return localStorage.getItem(ON_KEY) === '1'
  } catch (e) {
    return false
  }
}

export function setAppLockEnabled(on) {
  try {
    if (on) localStorage.setItem(ON_KEY, '1')
    else localStorage.removeItem(ON_KEY)
  } catch (e) {
    console.warn('[appLock] 保存开关失败', e)
  }
}

/** App 启动时安装一次;只在回前台时判断,不在页面里散落钩子 */
export function installAppLock() {
  if (installed || !isNativeApp()) return
  installed = true
  App.addListener('appStateChange', ({ isActive }) => {
    if (!isActive) {
      pausedAt = Date.now()
      return
    }
    const gap = pausedAt ? Date.now() - pausedAt : 0
    pausedAt = 0
    if (gap > APP_LOCK_AFTER_MS) verifyAndUnlock()
  }).catch(() => {})
}

async function verifyAndUnlock() {
  if (locking || !appLockEnabled()) return
  const userStore = useUserStore()
  if (!userStore.isLoggedIn) return
  // 独立产品页(壁纸/咔哒等)没有登录页可回,不套应用锁
  if (router.currentRoute.value.meta?.standalone) return

  locking = true
  try {
    const bio = await import('./biometric')
    const status = await bio.biometricStatus()
    if (!status || !status.available) {
      // 系统里指纹被删/硬件不可用:自动关闭,避免永久锁死
      setAppLockEnabled(false)
      return
    }
    const result = await bio.verifyAppLock()
    if (result.ok) return
    if (result.reason === 'busy') return // 保险箱自己的系统框正在前台,交给它处理
    if (['no_enrollment', 'no_hardware', 'unsupported', 'hw_unavailable', 'security_update'].includes(result.reason)) {
      setAppLockEnabled(false)
      return
    }
    // 用户取消/识别失败:退出到登录页,不把内容留在前台
    userStore.logout()
    router.replace('/login')
  } catch (e) {
    console.warn('[appLock] 校验失败', e)
  } finally {
    locking = false
  }
}
