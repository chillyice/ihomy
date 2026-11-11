// 后台消息提醒(仅 Android 壳):登录且开关打开时启动原生前台服务,
// 登出/关开关时停掉。服务自己每 30 秒对一次未读(见 live/LiveService.java),
// 这里只负责生命周期同步,不让 Web 侧关心实现。
import { App } from '@capacitor/app'
import { registerPlugin } from '@capacitor/core'
import { watch } from 'vue'
import { isAndroidApp } from '@/utils/platform'
import { useUserStore } from '@/stores/user'
import { getUnreadNotifyEnabled, setUnreadNotifyEnabled } from './widgets'

const IhomyWidgets = registerPlugin('IhomyWidgets')

let installed = false

/** 按「已登录 + 开关」决定服务该开还是该关;网络/插件失败只记日志,下次进前台再补 */
export async function syncLiveService() {
  try {
    const on = useUserStore().isLoggedIn && (await getUnreadNotifyEnabled())
    if (on) await IhomyWidgets.startLive()
    else await IhomyWidgets.stopLive()
  } catch (e) {
    console.warn('[live] 后台消息提醒同步失败', e)
  }
}

/** 设置页开关入口:先落原生偏好,再同步服务状态 */
export async function setBackgroundLiveEnabled(enabled) {
  await setUnreadNotifyEnabled(!!enabled)
  await syncLiveService()
}

export function installLiveService() {
  if (installed || !isAndroidApp()) return
  installed = true
  const userStore = useUserStore()
  // 登录态恢复/退出、切家庭都会走这里;immediate 覆盖冷启动已登录的情况
  watch(
    () => userStore.isLoggedIn,
    () => syncLiveService(),
    { immediate: true },
  )
  // 回到前台补一次:系统在极端省电模式下可能已经回收了服务
  App.addListener('appStateChange', ({ isActive }) => {
    if (isActive) syncLiveService()
  }).catch(() => {})
}
