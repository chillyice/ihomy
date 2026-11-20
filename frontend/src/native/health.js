// 健康数据原生桥(仅 Android 壳):Health Connect 授权、立即同步、每日自动同步。
// 只做「把本机健康数据读出来交给服务端」;指纹/聚合/可见性全在后端,与 iOS 快捷指令同一套口径。
import { registerPlugin } from '@capacitor/core'
import { watch } from 'vue'
import { healthApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { isAndroidApp } from '@/utils/platform'

const IhomyHealth = registerPlugin('HealthData')

/** 授权分组:与后端 HealthMetric.group、原生 HealthConnectReader.GROUPS 一致 */
export const HEALTH_GROUPS = ['SLEEP', 'ACTIVITY', 'VITALS', 'BODY']

/** 本机是否具备健康数据同步能力(只有 Android 壳有 Health Connect) */
export function healthSyncSupported() {
  return isAndroidApp()
}

/** 自动同步令牌名(服务端令牌列表里可见,方便吊销) */
const AUTO_TOKEN_NAME = '手机自动同步'
/** 自动同步令牌的本地 id(关闭时先吊销服务端那一条) */
const AUTO_TOKEN_ID_KEY = 'ihomy:health:autoTokenId'

function rememberAutoToken(id) {
  try {
    if (id) localStorage.setItem(AUTO_TOKEN_ID_KEY, String(id))
  } catch (e) {
    // 存储失败只影响「关闭时顺手吊销」,不影响同步本身
  }
}

function takeAutoTokenId() {
  try {
    const id = localStorage.getItem(AUTO_TOKEN_ID_KEY)
    localStorage.removeItem(AUTO_TOKEN_ID_KEY)
    return id ? Number(id) : null
  } catch (e) {
    return null
  }
}

/** 可用性 + 分组授权状态 + 上次同步结果(不可用/未授权都按空状态返回,不抛错) */
export async function healthNativeStatus() {
  if (!healthSyncSupported()) return { supported: false, available: false, groups: {}, granted: [], last: null }
  const avail = await IhomyHealth.isAvailable().catch(() => ({ available: false }))
  if (!avail || !avail.available) {
    return {
      supported: true,
      available: false,
      status: avail && avail.status,
      // 华为链状态(NO_HMS/NO_HEALTH_APP/NO_APPID/OK),华为机不可用时用于细分提示
      huaweiStatus: (avail && avail.huaweiStatus) || '',
      groups: {},
      granted: [],
      last: null,
    }
  }
  const [status, last] = await Promise.all([
    IhomyHealth.groupStatus({ groups: HEALTH_GROUPS }).catch(() => null),
    IhomyHealth.lastSync().catch(() => null),
  ])
  return {
    supported: true,
    available: true,
    provider: (status && status.provider) || '',
    groups: (status && status.groups) || {},
    granted: (status && status.granted) || [],
    last: last || null,
  }
}

/** 拉起系统授权弹窗;返回实际授予的组与未授予的组 */
export async function requestHealthPermissions(groups = HEALTH_GROUPS) {
  try {
    return await IhomyHealth.requestPermissions({ groups })
  } catch (e) {
    throw new Error(e && e.message ? e.message : 'permission_failed', { cause: e })
  }
}

/** 拿一个尽量新鲜的登录令牌:先 ping 一次让拦截器顺手续期(过期时),再交给原生 */
async function currentToken() {
  const userStore = useUserStore()
  if (!userStore.isLoggedIn) throw new Error('guest')
  await healthApi.ping().catch(() => {})
  return userStore.token
}

/**
 * 立即同步。首次没有同步记录时回填 90 天,之后只补上次成功点之后的窗口(原生侧处理)。
 * 返回 {accepted, duplicated, rejected, skipped, requests, groups}。
 */
export async function syncHealthNow({ days = 90 } = {}) {
  const token = await currentToken()
  const res = await IhomyHealth.sync({ token, days, groups: HEALTH_GROUPS })
  return res || {}
}

/**
 * 自动同步开关。开启时由页面生成一把 hk_ 长期令牌交给原生(后台没有 WebView,不能靠登录态),
 * 关闭时撤掉原生日程并吊销刚生成的那把令牌。
 */
export async function setHealthAutoSync(enabled) {
  if (!enabled) {
    await IhomyHealth.clearAutoSync().catch(() => {})
    const id = takeAutoTokenId()
    if (id) await healthApi.revokeToken(id).catch(() => {})
    return { auto: false }
  }
  const created = await healthApi.createToken({ name: AUTO_TOKEN_NAME })
  if (!created || !created.token) throw new Error('token_create_failed')
  rememberAutoToken(created.id)
  const status = await IhomyHealth.groupStatus({ groups: HEALTH_GROUPS }).catch(() => null)
  const groups = (status && status.granted) || []
  const res = await IhomyHealth.setAutoSync({ enabled: true, token: created.token, groups })
  return { auto: !!(res && res.auto) }
}

/** 原生桥安装:登出时清掉自动同步令牌与排期(避免换人登录后继续往上一个账号传数据) */
export async function installHealthBridge() {
  if (!healthSyncSupported()) return
  const userStore = useUserStore()
  watch(
    () => userStore.isLoggedIn,
    (on) => {
      if (on) return
      IhomyHealth.clearAutoSync().catch(() => {})
      takeAutoTokenId()
    },
  )
}
