import { ref, onMounted, onUnmounted } from 'vue'

// 可拖动+可调整大小的组合式函数,位置/大小持久化到 localStorage
// 用法:const { pos, size, reset } = useDragResize({ x, y, w, h, storageKey, minY, anchorRight, marginLeft })
// storageKey 为空则不持久化;minY 限制最高位置(默认 0);anchorRight=true 时 pos.x 为离右边缘距离(拖动方向取反)
// marginLeft 限制左侧边界(默认 0,沉浸式首页导航栏宽 220 时传 220)
// zIndex:面板层级,拖拽/resize 时自动置顶(但不超过光影层 65),由模块级计数器管理
const PANEL_BASE_Z = 20
const PANEL_TOP_MAX = 60  // 低于光影层 bright-spot(65)
let zCounter = PANEL_BASE_Z

export function useDragResize(initial) {
  const key = initial.storageKey
  const minY = initial.minY ?? 0
  const marginLeft = initial.marginLeft ?? 0
  const anchorRight = initial.anchorRight ?? false
  const load = () => {
    if (!key) return null
    try {
      const raw = localStorage.getItem(key)
      if (!raw) return null
      const obj = JSON.parse(raw)
      // 旧版 right 面板 x 为负数,取绝对值迁移到新 anchorRight 语义
      return { x: Math.abs(obj.x), y: obj.y, w: obj.w, h: obj.h }
    } catch (e) { return null }
  }
  const saved = load()
  const pos = ref({ x: saved?.x ?? initial.x ?? 0, y: saved?.y ?? initial.y ?? 0 })
  const size = ref({ w: saved?.w ?? initial.w ?? 320, h: saved?.h ?? initial.h ?? 200 })
  const zIndex = ref(PANEL_BASE_Z)
  const dragging = ref(false)
  const resizing = ref(false)
  let startX = 0, startY = 0, startPos = { x: 0, y: 0 }, startSize = { w: 0, h: 0 }

  // 置顶:拖拽或 resize 时调用,提升 z-index(不超过光影层)
  const bringToFront = () => {
    zCounter = Math.min(zCounter + 1, PANEL_TOP_MAX)
    zIndex.value = zCounter
  }

  const save = () => {
    if (!key) return
    try {
      localStorage.setItem(key, JSON.stringify({ x: pos.value.x, y: pos.value.y, w: size.value.w, h: size.value.h }))
    } catch (e) {}
  }

  const onDragStart = (e) => {
    if (e.target.classList.contains('resize-handle')) return
    dragging.value = true
    bringToFront()
    startX = e.clientX
    startY = e.clientY
    startPos = { ...pos.value }
    e.preventDefault()
  }
  const onResizeStart = (e) => {
    resizing.value = true
    bringToFront()
    startX = e.clientX
    startY = e.clientY
    startPos = { ...pos.value }
    startSize = { ...size.value }
    e.preventDefault()
    e.stopPropagation()
  }
  const onMouseMove = (e) => {
    const vw = window.innerWidth
    const vh = window.innerHeight
    if (dragging.value) {
      const w = size.value.w
      const h = size.value.h
      const dx = e.clientX - startX
      const dy = e.clientY - startY
      let nx
      if (anchorRight) {
        // right 模式:pos.x 是离右边缘距离,鼠标向左(dx<0)→ nx 增大(面板向左)
        // 左边界限制:面板左边 = vw - pos.x - w ≥ marginLeft → pos.x ≤ vw - marginLeft - w
        const maxX = Math.max(0, vw - marginLeft - w)
        nx = Math.max(0, Math.min(startPos.x - dx, maxX))
      } else {
        // left 模式:左边界 ≥ marginLeft,右边界 ≤ vw(右边不出屏)
        nx = Math.max(marginLeft, Math.min(startPos.x + dx, Math.max(marginLeft, vw - w)))
      }
      // 顶边不低于 minY,底边不超出 vh
      const ny = Math.max(minY, Math.min(startPos.y + dy, Math.max(minY, vh - h)))
      pos.value = { x: nx, y: ny }
    }
    if (resizing.value) {
      const dx = e.clientX - startX
      const dy = e.clientY - startY
      if (anchorRight) {
        // right 模式 + 左下角把手:右边缘固定(pos.x 不变),鼠标向左(dx<0)→ 宽度增大(左边缘左移)
        // 左边缘 = vw - pos.x - startSize.w,新左边缘 = 左边缘 + dx,新宽度 = startSize.w - dx
        // 宽度范围:最小 200,最大 = vw - marginLeft - startPos.x(左边缘不越过 marginLeft)
        const maxW = Math.max(200, vw - marginLeft - startPos.x)
        const newW = Math.min(Math.max(200, startSize.w - dx), maxW)
        pos.value = { x: startPos.x, y: startPos.y }
        size.value = { w: newW, h: Math.min(Math.max(120, startSize.h + dy), Math.max(120, vh - startPos.y)) }
      } else {
        // left 模式:左边固定,鼠标向右(dx>0)→ 宽度增大,右边右移,但不超过 vw
        size.value = {
          w: Math.min(Math.max(200, startSize.w + dx), Math.max(200, vw - startPos.x)),
          h: Math.min(Math.max(120, startSize.h + dy), Math.max(120, vh - startPos.y)),
        }
      }
    }
  }
  const onMouseUp = () => {
    if (dragging.value || resizing.value) save()
    dragging.value = false
    resizing.value = false
  }
  const reset = () => {
    pos.value = { x: initial.x ?? 0, y: initial.y ?? 0 }
    size.value = { w: initial.w ?? 320, h: initial.h ?? 200 }
    if (key) { try { localStorage.removeItem(key) } catch (e) {} }
  }
  onMounted(() => {
    window.addEventListener('mousemove', onMouseMove)
    window.addEventListener('mouseup', onMouseUp)
  })
  onUnmounted(() => {
    window.removeEventListener('mousemove', onMouseMove)
    window.removeEventListener('mouseup', onMouseUp)
  })
  return { pos, size, zIndex, dragging, resizing, onDragStart, onResizeStart, reset }
}
