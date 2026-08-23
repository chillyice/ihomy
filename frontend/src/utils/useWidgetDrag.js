import { ref, readonly } from 'vue'

// 模块级单例,跨组件共享拖拽状态(AppSidebar 启动 → Home.vue 接收)
const SIDEBAR_WIDTH = 220

const dragging = ref(false)
const dragType = ref(null)
const dragX = ref(0)
const dragY = ref(0)
const crossed = ref(false)
let dropCb = null
let moveCb = null

function onMouseMove(e) {
  dragX.value = e.clientX
  dragY.value = e.clientY
  if (e.clientX > SIDEBAR_WIDTH && !crossed.value) crossed.value = true
  if (moveCb) moveCb(e.clientX, e.clientY)
}

function onMouseUp() {
  if (crossed.value && dropCb) dropCb(dragType.value, dragX.value, dragY.value)
  dragging.value = false
  dragType.value = null
  crossed.value = false
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', onMouseUp)
}

export function useWidgetDrag() {
  const startDrag = (type, e) => {
    dragging.value = true
    dragType.value = type
    dragX.value = e.clientX
    dragY.value = e.clientY
    crossed.value = false
    e.preventDefault()
    window.addEventListener('mousemove', onMouseMove)
    window.addEventListener('mouseup', onMouseUp)
  }
  const onDrop = (cb) => { dropCb = cb }
  const onMove = (cb) => { moveCb = cb }
  return {
    dragging: readonly(dragging),
    dragType: readonly(dragType),
    dragX: readonly(dragX),
    dragY: readonly(dragY),
    crossed: readonly(crossed),
    startDrag,
    onDrop,
    onMove,
  }
}
