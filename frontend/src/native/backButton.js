// Android 返回键:弹层打开 → 先关弹层;有历史 → 返回上一页;根页面 → 二次确认退出。
// 只在原生壳内由 native/index.js 懒加载。
import { App } from '@capacitor/app'
import { ElMessage } from 'element-plus'
import router from '@/router'
import i18n from '@/i18n'

/** 两次返回键的间隔(毫秒),与常见国产 App 体验一致 */
const EXIT_WINDOW = 1500
let lastBackAt = 0

/** 当前可见的最上层 Element Plus 遮罩(弹窗/抽屉/消息框) */
function visibleOverlay() {
  const nodes = document.querySelectorAll('.el-overlay')
  for (const el of nodes) {
    if (el.offsetWidth || el.offsetHeight || el.getClientRects().length) return el
  }
  return null
}

/** 关掉最上层弹层;返回是否真的关了一层 */
function closeTopOverlay() {
  const overlay = visibleOverlay()
  if (!overlay) return false
  // Element Plus 弹窗/抽屉/消息框默认支持 ESC 关闭,先按 ESC 让组件走自己的关闭流程
  const target = overlay.firstElementChild || overlay
  target.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', keyCode: 27, bubbles: true }))
  if (visibleOverlay()) {
    // 个别弹层关掉了 ESC 关闭(close-on-press-escape=false),兜底点右上角关闭按钮
    const btn = overlay.querySelector('.el-dialog__headerbtn, .el-drawer__close-btn, .el-message-box__headerbtn')
    btn?.click()
  }
  return true
}

export async function initBackButton() {
  await App.addListener('backButton', ({ canGoBack }) => {
    if (closeTopOverlay()) return
    if (canGoBack) {
      router.back()
      return
    }
    const now = Date.now()
    if (now - lastBackAt <= EXIT_WINDOW) {
      lastBackAt = 0
      App.exitApp()
      return
    }
    lastBackAt = now
    ElMessage({
      message: i18n.global.t('common.pressAgainToExit'),
      type: 'info',
      duration: 1500,
    })
  })
}
