// 桌面卡片令牌下发(仅原生壳):登录/切家庭时把 WIDGET 令牌交给原生,登出时清掉。
// 令牌 30 天不轮换,卡片 WorkManager 凭它只读拉日程;这里是令牌的唯一来源。
import { registerPlugin } from '@capacitor/core'
import request from '@/api/request'
import { useUserStore } from '@/stores/user'

const IhomyWidgets = registerPlugin('IhomyWidgets')

/** 按当前登录态同步令牌:未登录清掉,已登录签发新令牌(家庭快照随令牌走) */
export async function syncWidgetToken() {
  const userStore = useUserStore()
  if (!userStore.isLoggedIn) {
    await IhomyWidgets.clearWidgetToken().catch(() => {})
    return 'cleared'
  }
  const res = await request.post('/auth/widget-token')
  const token = res && res.refreshToken
  if (!token) throw new Error('no widget token in response')
  await IhomyWidgets.setWidgetToken({ token })
  return 'pushed'
}

/** 原生桥安装:登录态/家庭变化时防抖同步一次(与 chat.js 的换代重建同思路) */
export async function installWidgetBridge() {
  const { watch } = await import('vue')
  const userStore = useUserStore()
  let timer = null
  const run = () => {
    clearTimeout(timer)
    timer = setTimeout(() => {
      syncWidgetToken().catch((e) => console.warn('[widgets] 令牌同步失败', e))
    }, 1500)
  }
  watch(() => userStore.isLoggedIn, run)
  watch(() => userStore.userInfo && userStore.userInfo.familyId, run)
  // 启动先同步一次(覆盖「App 打开期间令牌过期/家庭切换」)
  run()
}
