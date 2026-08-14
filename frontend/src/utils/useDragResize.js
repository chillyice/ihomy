import { ref, onMounted, onUnmounted } from 'vue'

// 可拖动+可调整大小的组合式函数,位置/大小持久化到 localStorage
// 用法:const { pos, size, reset } = useDragResize({ x, y, w, h, storageKey, minY, anchorRight })
// storageKey 为空则不持久化;minY 限制最高位置(默认 0);anchorRight=true 时 pos.x 为离右边缘距离(拖动方向取反)
export function useDragResize(initial) {
  const key = initial.storageKey
  const minY = initial.minY ?? 0
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
  const dragging = ref(false)
  const resizing = ref(false)
  let startX = 0, startY = 0, startPos = { x: 0, y: 0 }, startSize = { w: 0, h: 0 }

  const save = () => {
    if (!key) return
    try {
      localStorage.setItem(key, JSON.stringify({ x: pos.value.x, y: pos.value.y, w: size.value.w, h: size.value.h }))
    } catch (e) {}
  }

  const onDragStart = (e) => {
    if (e.target.classList.contains('resize-handle')) return
    dragging.value = true
    startX = e.clientX
    startY = e.clientY
    startPos = { ...pos.value }
    e.preventDefault()
  }
  const onResizeStart = (e) => {
    resizing.value = true
    startX = e.clientX
    startY = e.clientY
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
        nx = Math.max(0, Math.min(startPos.x - dx, Math.max(0, vw - w)))
      } else {
        nx = Math.max(0, Math.min(startPos.x + dx, Math.max(0, vw - w)))
      }
      const ny = Math.max(minY, Math.min(startPos.y + dy, Math.max(minY, vh - h)))
      pos.value = { x: nx, y: ny }
    }
    if (resizing.value) {
      size.value = {
        w: Math.min(Math.max(200, startSize.w + (e.clientX - startX)), Math.max(200, vw - pos.value.x)),
        h: Math.min(Math.max(120, startSize.h + (e.clientY - startY)), Math.max(120, vh - pos.value.y)),
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
  return { pos, size, dragging, resizing, onDragStart, onResizeStart, reset }
}
