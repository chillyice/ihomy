import { ref, readonly } from 'vue'

// 模块级单例,跨组件共享拖拽状态(AppSidebar 启动 → Home.vue 接收):
// 拖拽状态与回调都挂在模块作用域,故 onDrop/onMove 是「后写覆盖」——同一时刻只应有一个消费者注册。
const SIDEBAR_WIDTH = 220

const dragging = ref(false)
const dragType = ref(null)
const dragX = ref(0)
const dragY = ref(0)
const crossed = ref(false)
let dropCb = null
let moveCb = null
let activePointerId = null

// Pointer Events:鼠标 / 触屏 / 触控笔同一套;侧栏项上加了 touch-action:none,拖动时页面不滚
function onPointerMove(e) {
  if (activePointerId !== null && e.pointerId !== activePointerId) return
  dragX.value = e.clientX
  dragY.value = e.clientY
  // 只有拖出侧栏进入内容区(越过 220px)才算有效投放,否则在侧栏内松手不触发 drop
  if (e.clientX > SIDEBAR_WIDTH && !crossed.value) crossed.value = true
  if (moveCb) moveCb(e.clientX, e.clientY)
}

function onPointerUp(e) {
  if (activePointerId !== null && e.pointerId !== undefined && e.pointerId !== activePointerId) return
  // crossed 为 false 时(在侧栏区域内松手)直接取消,不调用 dropCb
  if (crossed.value && dropCb) dropCb(dragType.value, dragX.value, dragY.value)
  dragging.value = false
  dragType.value = null
  crossed.value = false
  activePointerId = null
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
  window.removeEventListener('pointercancel', onPointerUp)
}

export function useWidgetDrag() {
  const startDrag = (type, e) => {
    if (e.pointerType === 'mouse' && e.button !== 0) return
    dragging.value = true
    dragType.value = type
    dragX.value = e.clientX
    dragY.value = e.clientY
    crossed.value = false
    activePointerId = e.pointerId
    e.preventDefault()
    window.addEventListener('pointermove', onPointerMove)
    window.addEventListener('pointerup', onPointerUp)
    window.addEventListener('pointercancel', onPointerUp)
  }
  const onDrop = (cb) => {
    dropCb = cb
  }
  const onMove = (cb) => {
    moveCb = cb
  }
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
