import { ref, onMounted, onUnmounted } from 'vue'
import { haptic } from '@/utils/haptics'
import { clampFloatRect } from '@/utils/floatRect'

// 可拖动+可调整大小的组合式函数,位置/大小持久化到 localStorage
// 用法:const { pos, size, reset } = useDragResize({ x, y, w, h, storageKey, minY, anchorRight, marginLeft })
// storageKey 为空则不持久化;minY 限制最高位置(默认 0);anchorRight=true 时 pos.x 为离右边缘距离(拖动方向取反)
// marginLeft 限制左侧边界(默认 0,沉浸式首页导航栏宽 220 时传 220)
// zIndex:面板层级,拖拽/resize 时自动置顶(但不超过侧栏 60),由模块级计数器管理
// 事件:统一 Pointer Events(鼠标/触摸一把抓),把手容器加 touch-action:none 触屏才不会被页面滚动抢走;
// 指针捕获只在真正拖动/缩放期间做(踩坑速查 §3.35:pointerdown 就捕获会让容器内所有卡片点不动)
const PANEL_BASE_Z = 20
const PANEL_TOP_MAX = 60 // 低于侧栏(60)与全部光影(270+)
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
    } catch (e) {
      return null
    }
  }
  // 视口尺寸:固定定位元素用布局视口(innerWidth/innerHeight),不用 visualViewport(键盘弹出时会把小窗推乱)
  const viewportSize = () => ({ width: window.innerWidth || 0, height: window.innerHeight || 0 })
  // 最小可用尺寸:面板允许缩到 200×120,浮球/小窗按自身尺寸(不会被放大)
  const clampOptions = () => ({
    minY,
    marginLeft,
    anchorRight,
    minW: Math.min(200, initial.w ?? 200),
    minH: Math.min(120, initial.h ?? 120),
  })
  const saved = load()
  // 初始化就夹一次:上次在大屏存的位置,这次可能已经在屏幕外(窗口缩小/换设备/旋屏后)
  const startRect = clampFloatRect(
    {
      x: saved?.x ?? initial.x ?? 0,
      y: saved?.y ?? initial.y ?? 0,
      w: saved?.w ?? initial.w ?? 320,
      h: saved?.h ?? initial.h ?? 200,
    },
    viewportSize(),
    clampOptions(),
  )
  const pos = ref({ x: startRect.x, y: startRect.y })
  const size = ref({ w: startRect.w, h: startRect.h })
  const zIndex = ref(PANEL_BASE_Z)
  const dragging = ref(false)
  const resizing = ref(false)
  let startX = 0,
    startY = 0,
    startPos = { x: 0, y: 0 },
    startSize = { w: 0, h: 0 }
  let captureEl = null

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

  /**
   * 屏幕变小后把当前位置/尺寸夹回视口内(窗口缩放、旋屏、地址栏收起、回前台都调用)。
   * 拖拽/缩放进行中不动手:指针移动本身已经逐帧夹紧,避免两边抢着改同一个 ref。
   */
  const clampToViewport = () => {
    if (dragging.value || resizing.value) return
    const next = clampFloatRect({ x: pos.value.x, y: pos.value.y, w: size.value.w, h: size.value.h }, viewportSize(), clampOptions())
    const changed = next.x !== pos.value.x || next.y !== pos.value.y || next.w !== size.value.w || next.h !== size.value.h
    if (!changed) return
    pos.value = { x: next.x, y: next.y }
    size.value = { w: next.w, h: next.h }
    save()
  }

  // 监听按需挂载:resize / 旋屏 / visualViewport(移动端键盘与地址栏) / 回前台,
  // 只在挂载期间存在,卸载一并摘除。
  const onViewportChange = () => clampToViewport()
  const onVisibilityChange = () => {
    if (document.visibilityState === 'visible') clampToViewport()
  }
  onMounted(() => {
    window.addEventListener('resize', onViewportChange)
    window.addEventListener('orientationchange', onViewportChange)
    window.addEventListener('visibilitychange', onVisibilityChange)
    window.visualViewport?.addEventListener('resize', onViewportChange)
    clampToViewport()
  })

  /** 指针捕获期间移动/抬起都派发到捕获元素上,直接改 pos/size */
  const onPointerMove = (e) => {
    const vw = window.innerWidth
    const vh = window.innerHeight
    if (dragging.value) {
      const w = size.value.w
      const h = size.value.h
      const dx = e.clientX - startX
      const dy = e.clientY - startY
      let nx
      if (anchorRight) {
        // right 模式:pos.x 是离右边缘距离,指针向左(dx<0)→ nx 增大(面板向左)
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
        // right 模式 + 左下角把手:右边缘固定(pos.x 不变),指针向左(dx<0)→ 宽度增大(左边缘左移)
        // 左边缘 = vw - pos.x - startSize.w,新左边缘 = 左边缘 + dx,新宽度 = startSize.w - dx
        // 宽度范围:最小 200,最大 = vw - marginLeft - startPos.x(左边缘不越过 marginLeft)
        const maxW = Math.max(200, vw - marginLeft - startPos.x)
        const newW = Math.min(Math.max(200, startSize.w - dx), maxW)
        pos.value = { x: startPos.x, y: startPos.y }
        size.value = { w: newW, h: Math.min(Math.max(120, startSize.h + dy), Math.max(120, vh - startPos.y)) }
      } else {
        // left 模式:左边固定,指针向右(dx>0)→ 宽度增大,右边右移,但不超过 vw
        size.value = {
          w: Math.min(Math.max(200, startSize.w + dx), Math.max(200, vw - startPos.x)),
          h: Math.min(Math.max(120, startSize.h + dy), Math.max(120, vh - startPos.y)),
        }
      }
    }
  }

  const stopListening = () => {
    if (captureEl) {
      captureEl.removeEventListener('pointermove', onPointerMove)
      captureEl.removeEventListener('pointerup', onPointerUp)
      captureEl.removeEventListener('pointercancel', onPointerUp)
      try {
        if (captureEl.hasPointerCapture?.(0)) captureEl.releasePointerCapture(0)
      } catch (e) {}
      captureEl = null
    }
  }

  const onPointerUp = () => {
    if (dragging.value || resizing.value) {
      save()
      haptic('tap')
    }
    dragging.value = false
    resizing.value = false
    // 拖拽/缩放结束立即释放捕获并移除监听器
    stopListening()
  }

  const beginPointer = (e) => {
    captureEl = e.currentTarget
    captureEl.addEventListener('pointermove', onPointerMove)
    captureEl.addEventListener('pointerup', onPointerUp)
    captureEl.addEventListener('pointercancel', onPointerUp)
    try {
      captureEl.setPointerCapture(e.pointerId)
    } catch (e2) {}
  }

  const onDragStart = (e) => {
    if (e.target.classList.contains('resize-handle')) return
    dragging.value = true
    bringToFront()
    startX = e.clientX
    startY = e.clientY
    startPos = { ...pos.value }
    e.preventDefault()
    // 拖拽开始才捕获指针挂监听器,空闲无 listener(参考 AvatarCropper.vue 的写法)
    beginPointer(e)
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
    // resize 开始才捕获指针挂监听器
    beginPointer(e)
  }
  const reset = () => {
    const rect = clampFloatRect(
      { x: initial.x ?? 0, y: initial.y ?? 0, w: initial.w ?? 320, h: initial.h ?? 200 },
      viewportSize(),
      clampOptions(),
    )
    pos.value = { x: rect.x, y: rect.y }
    size.value = { w: rect.w, h: rect.h }
    if (key) {
      try {
        localStorage.removeItem(key)
      } catch (e) {}
    }
  }
  // 监听器只在拖拽期间存在;onUnmounted 兜底清理(避免异常退出路径漏移除)
  onUnmounted(() => {
    stopListening()
    window.removeEventListener('resize', onViewportChange)
    window.removeEventListener('orientationchange', onViewportChange)
    window.removeEventListener('visibilitychange', onVisibilityChange)
    window.visualViewport?.removeEventListener('resize', onViewportChange)
  })
  return { pos, size, zIndex, dragging, resizing, onDragStart, onResizeStart, reset }
}
