// 网络状态提示:断网/恢复时给一条反馈,避免用户以为页面卡死(不做离线数据逻辑)
import { Network } from '@capacitor/network'
import { ElMessage } from 'element-plus'
import i18n from '@/i18n'

export async function initNetworkStatus() {
  let online = null
  const handle = (status) => {
    if (online === null) {
      online = status.connected
      return
    }
    if (online === status.connected) return
    online = status.connected
    ElMessage({
      message: i18n.global.t(status.connected ? 'common.networkBack' : 'common.networkOffline'),
      type: status.connected ? 'success' : 'warning',
      duration: 2000,
    })
  }
  try {
    const status = await Network.getStatus()
    online = status.connected
    await Network.addListener('networkStatusChange', handle)
  } catch (e) {
    console.warn('[native] 网络状态监听失败', e)
  }
}
