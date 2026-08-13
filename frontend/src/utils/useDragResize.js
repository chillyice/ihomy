import { ref, onMounted, onUnmounted } from 'vue'

// 可拖动+可调整大小的组合式函数
// 用法:const { pos, size, dragging } = useDragResize({ x, y, w, h })
// 模板:<div :style="{ left: pos.x+'px', top: pos.y+'px', width: size.w+'px', height: size.h+'px' }" @mousedown="onDragStart">
//        <div class="drag-handle" @mousedown="onDragStart">拖动条</div>
//        <div class="resize-handle" @mousedown="onResizeStart"></div>
//      </div>
export function useDragResize(initial) {
  const pos = ref({ x: initial.x || 0, y: initial.y || 0 })
  const size = ref({ w: initial.w || 320, h: initial.h || 200 })
  const dragging = ref(false)
  const resizing = ref(false)
  let startX = 0, startY = 0, startPos = { x: 0, y: 0 }, startSize = { w: 0, h: 0 }

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
    if (dragging.value) {
      pos.value = {
        x: startPos.x + (e.clientX - startX),
        y: startPos.y + (e.clientY - startY),
      }
    }
    if (resizing.value) {
      size.value = {
        w: Math.max(200, startSize.w + (e.clientX - startX)),
        h: Math.max(120, startSize.h + (e.clientY - startY)),
      }
    }
  }
  const onMouseUp = () => {
    dragging.value = false
    resizing.value = false
  }
  onMounted(() => {
    window.addEventListener('mousemove', onMouseMove)
    window.addEventListener('mouseup', onMouseUp)
  })
  onUnmounted(() => {
    window.removeEventListener('mousemove', onMouseMove)
    window.removeEventListener('mouseup', onMouseUp)
  })
  return { pos, size, dragging, resizing, onDragStart, onResizeStart }
}
