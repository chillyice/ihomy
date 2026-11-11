// 全局快捷键:只在有精细指针(鼠标 / 触控板)的设备上生效,触屏一条都不响应。
//
// 只注册两个动作(见 AGENTS 前端规范 / Android 第三批 P1-C):
// - Ctrl/⌘+K:开关命令面板(输入框里也生效,它是命令组合键不是字符输入);
// - /:聚焦本页搜索框(输入框/可编辑区里直接放行,斜杠照常输入)。
// 裸 Esc、方向键、空格、字母、F* 一律不进全局监听——它们已被照片查看器 / 阅读器 /
// 思维导图 / 户型图 / 各类游戏占用,统一由「当前打开的那层」自己处理。
import { ref } from 'vue'
import { useDevice } from '@/composables/useDevice'

const EDITABLE_SELECTOR = 'input, textarea, select, [contenteditable="true"], [contenteditable=""], [data-hotkeys-ignore]'

/** 目标是否落在输入控件/可编辑区里(纯判断,便于单测) */
export function isEditableTarget(el) {
  if (!el || typeof el.closest !== 'function') return false
  return !!el.closest(EDITABLE_SELECTOR)
}

/**
 * 把键盘事件归类成全局动作(纯函数,便于单测)。
 * 返回 'palette' | 'search' | null,不读写 DOM / store。
 */
export function hotkeyAction(e) {
  if (!e) return null
  // 输入法组字中(中文拼音候选窗)一律不抢
  if (e.isComposing || e.keyCode === 229) return null
  if (e.altKey) return null
  if ((e.ctrlKey || e.metaKey) && !e.shiftKey && (e.key === 'k' || e.key === 'K')) return 'palette'
  if (e.key === '/' && !e.ctrlKey && !e.metaKey && !e.shiftKey) return 'search'
  return null
}

/** 有 EP 弹窗/确认框打开时不抢焦点(避免与「确认删除」等操作打架) */
export function isBlockingOverlayOpen() {
  if (typeof document === 'undefined') return false
  return Array.from(document.querySelectorAll('.el-overlay')).some(
    (el) => el.offsetParent !== null && !!el.querySelector('.el-overlay-dialog, .el-message-box'),
  )
}

/** 本页第一个可见的搜索框:优先主内容区,避免命中侧栏/弹层里的输入 */
export function findSearchInput() {
  if (typeof document === 'undefined') return null
  const scopes = ['main', '.gc-main', '.mobile-layout'].map((sel) => document.querySelector(sel)).filter(Boolean)
  for (const scope of scopes) {
    for (const el of scope.querySelectorAll('[data-search-input]')) {
      if (el.offsetParent !== null) return el
    }
  }
  return null
}

/** `/` 与命令面板「聚焦本页搜索」共用这一份查找;找不到就安静返回 false */
export function focusPageSearch() {
  const el = findSearchInput()
  if (!el) return false
  // data-search-input 可能落在 el-input 的内层 input 上,也可能落在包装层:两种都兜住
  const target = typeof el.matches === 'function' && el.matches('input, textarea') ? el : el.querySelector?.('input, textarea')
  if (!target) return false
  target.focus?.()
  target.select?.()
  return true
}

// ---- 模块级单例:一次会话只绑一次 document 监听,所有调用方共享同一个面板状态 ----
const paletteOpen = ref(false)
let bound = false

const { hasPointer } = useDevice()

function onKeydown(e) {
  // 指针热插拔要即时生效:这里判断,不解绑
  if (!hasPointer.value) return
  const action = hotkeyAction(e)
  if (!action) return
  if (isBlockingOverlayOpen()) return
  if (action === 'palette') {
    // 命令组合键无条件吞掉浏览器默认行为(Ctrl/⌘+K 常被浏览器/输入法占用)
    e.preventDefault()
    paletteOpen.value = !paletteOpen.value
    return
  }
  // `/`:正在输入就让斜杠照常落进输入框
  if (isEditableTarget(e.target)) return
  if (focusPageSearch()) e.preventDefault()
}

// 模块级开关:深链 / App.vue 的 ?search=1 兜底直接调用,不必先取 useHotkeys 实例。
// 触屏没有键盘快捷键,但仍可由此显式打开面板(快捷方式「搜索」就是这条路)。
export function openPalette() {
  paletteOpen.value = true
}

export function closePalette() {
  paletteOpen.value = false
}

export function togglePalette() {
  paletteOpen.value = !paletteOpen.value
}

export function useHotkeys() {
  if (!bound && typeof document !== 'undefined') {
    document.addEventListener('keydown', onKeydown)
    bound = true
  }
  return { paletteOpen, openPalette, closePalette, togglePalette }
}
