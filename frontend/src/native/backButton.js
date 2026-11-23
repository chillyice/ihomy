// Android 返回键依次处理弹层、页内状态和上级路由，在首页把任务切到后台。
// 只在原生壳内由 native/index.js 懒加载。
import { App } from '@capacitor/app'
import router from '@/router'
import { useUserStore } from '@/stores/user'
import { parentPathOf } from '@/utils/navParent'
import { APP_BACK_EVENT } from '@/composables/useAppBack'

/** 当前可见的最上层 Element Plus 遮罩(弹窗/抽屉/消息框) */
function visibleOverlay() {
  const nodes = document.querySelectorAll('.el-overlay')
  let top = null
  let topZ = -Infinity
  for (const el of nodes) {
    if (!el.getClientRects().length) continue
    const z = Number.parseInt(getComputedStyle(el).zIndex, 10) || 0
    if (z >= topZ) {
      top = el
      topZ = z
    }
  }
  return top
}

/** 关掉最上层弹层;返回是否真的关了一层 */
function closeTopOverlay() {
  const overlay = visibleOverlay()
  if (!overlay) return false
  const btn = overlay.querySelector('.el-dialog__headerbtn, .el-drawer__close-btn, .el-message-box__headerbtn')
  if (btn) btn.click()
  else (overlay.firstElementChild || overlay).dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', bubbles: true }))
  return true
}

export async function initBackButton() {
  let navigating = false
  await App.addListener('backButton', async () => {
    if (closeTopOverlay() || navigating) return
    const route = router.currentRoute.value
    // 这两类面板压的是同 URL 的临时历史项，弹出后由面板自己的监听器收起。
    const state = window.history.state
    if (state?.windowDirectory || (route.path === '/album' && state?.ihomy === 'album-panel')) {
      return router.back()
    }
    if (!window.dispatchEvent(new Event(APP_BACK_EVENT, { cancelable: true }))) return
    const root = useUserStore().isPureOps ? '/ops' : '/home'
    const parent = parentPathOf(route.fullPath, router.getRoutes(), root)
    navigating = true
    try {
      if (parent) await router.replace(parent)
      else await App.minimizeApp()
    } finally {
      navigating = false
    }
  })
}
