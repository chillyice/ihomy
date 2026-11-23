import { onBeforeUnmount, onMounted } from 'vue'

// 页内阅读器、游戏或首页子页签可先消费 Android 返回，再由壳处理路由和桌面。
export const APP_BACK_EVENT = 'ihomy:app-back'

export function useAppBack(handleBack) {
  const listener = (event) => {
    if (!event.defaultPrevented && handleBack()) event.preventDefault()
  }
  onMounted(() => window.addEventListener(APP_BACK_EVENT, listener))
  onBeforeUnmount(() => window.removeEventListener(APP_BACK_EVENT, listener))
}
