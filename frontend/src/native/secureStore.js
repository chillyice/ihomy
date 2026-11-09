// 令牌安全存储(仅原生壳):token / refreshToken 落 Android Keystore(EncryptedSharedPreferences),
// localStorage 只放非敏感的 userInfo。插件按需 import,进不了首屏包。
import { isNativeApp } from '@/utils/platform'

const TOKEN_KEY = 'token'
const REFRESH_KEY = 'refreshToken'

// 插件代理对象本身带 then(方法未实现时抛错),绝不能作为 promise 的 resolution value 返回:
// Promise 会把它当 thenable 采纳,直接以 "SecureStorage.then() is not implemented" 失败。
// 所以只在回调里给模块级变量赋值,函数返回 undefined。
let storeApi = null
let storeLoading = null

function loadStore() {
  if (!storeLoading) {
    storeLoading = import('@aparajita/capacitor-secure-storage')
      .then((mod) => {
        storeApi = mod.SecureStorage
      })
      .catch((e) => {
        console.warn('[native] 安全存储不可用,退回本地缓存', e)
      })
  }
  return storeLoading
}

/**
 * 启动时读取令牌。优先 Keystore,失败再退回 localStorage
 * (旧版本或 Keystore 异常时至少不让用户白丢登录态)。
 */
export async function readTokens() {
  if (!isNativeApp()) return null
  try {
    await loadStore()
    if (storeApi) {
      const token = await storeApi.get(TOKEN_KEY)
      if (token) {
        const refreshToken = await storeApi.get(REFRESH_KEY)
        return { token: String(token), refreshToken: refreshToken ? String(refreshToken) : '' }
      }
    }
  } catch (e) {
    console.warn('[native] 安全存储读取失败,回退本地缓存', e)
  }
  const token = localStorage.getItem(TOKEN_KEY)
  if (!token) return null
  return { token, refreshToken: localStorage.getItem(REFRESH_KEY) || '' }
}

/** 写入/轮换令牌;成功后清掉 localStorage 里的旧值 */
export async function writeTokens(token, refreshToken) {
  if (!isNativeApp()) return
  try {
    await loadStore()
    if (storeApi) {
      if (token) await storeApi.set(TOKEN_KEY, token)
      else await storeApi.remove(TOKEN_KEY)
      if (refreshToken) await storeApi.set(REFRESH_KEY, refreshToken)
      else await storeApi.remove(REFRESH_KEY)
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(REFRESH_KEY)
      return
    }
  } catch (e) {
    console.warn('[native] 安全存储写入失败,退回本地缓存', e)
  }
  // Keystore 不可用(极老设备/系统异常)时退回 localStorage,保证能继续用
  localStorage.setItem(TOKEN_KEY, token || '')
  localStorage.setItem(REFRESH_KEY, refreshToken || '')
}

/** 登出清空两处(Keystore + 残留 localStorage) */
export async function clearTokens() {
  if (!isNativeApp()) return
  try {
    await loadStore()
    if (storeApi) {
      // 两个 remove 各自独立执行:串行 try 包裹时第一个抛错会吞掉第二个,
      // 实测登出后 refreshToken 残留在 Keystore;allSettled + 回读校验保底清干净
      const results = await Promise.allSettled([storeApi.remove(TOKEN_KEY), storeApi.remove(REFRESH_KEY)])
      for (const r of results) {
        if (r.status === 'rejected') console.warn('[native] 安全存储清理项失败', r.reason)
      }
      const leftovers = await Promise.allSettled([storeApi.get(TOKEN_KEY), storeApi.get(REFRESH_KEY)])
      const stillThere = leftovers.some((r) => r.status === 'fulfilled' && r.value)
      if (stillThere) {
        await Promise.allSettled([storeApi.remove(TOKEN_KEY), storeApi.remove(REFRESH_KEY)])
      }
    }
  } catch (e) {
    console.warn('[native] 安全存储清理失败', e)
  }
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_KEY)
}
