import { getCurrentScope, onScopeDispose, ref } from 'vue'

// 侧栏模块拖入首页的共享拖拽状态(模块级单例;WarmLayout 启动拖拽 → WarmHome 消费)。
// 原生 HTML5 DnD 的拖拽图是静态截图、无法在拖拽中变形,故改用 Pointer Events + 自定义浮层幽灵:
// 指针在侧栏 → 幽灵为「导航胶囊」形态;进入内容区网格 → 幽灵放大为「4 列 × 2 行」卡片(交给 WarmHome 的网格占位呈现)。
const dragging = ref(false)
const code = ref(null)
const label = ref('')
const x = ref(0)
const y = ref(0)
const overGrid = ref(false) // 指针是否在内容区网格内(决定幽灵形态)

let moveCb = null
let dropCb = null
let activePointerId = null

function onPointerMove(e) {
  if (e.pointerId !== activePointerId) return
  x.value = e.clientX
  y.value = e.clientY
  if (moveCb) moveCb(e.clientX, e.clientY)
}

function finishDrag() {
  dragging.value = false
  code.value = null
  label.value = ''
  overGrid.value = false
  activePointerId = null
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
  window.removeEventListener('pointercancel', onPointerCancel)
  window.removeEventListener('blur', finishDrag)
  window.removeEventListener('keydown', onKeyDown)
}

function onPointerUp(e) {
  if (e.pointerId !== activePointerId) return
  try {
    if (e.clientX !== x.value || e.clientY !== y.value) onPointerMove(e)
    if (overGrid.value) dropCb?.(code.value)
  } finally {
    finishDrag()
  }
}

function onPointerCancel(e) {
  if (e.pointerId === activePointerId) finishDrag()
}

function onKeyDown(e) {
  if (e.key === 'Escape') finishDrag()
}

export function useWarmWidgetDrag() {
  const startDrag = (_code, _label, e) => {
    if (dragging.value || e.isPrimary === false || e.button !== 0) return
    code.value = _code
    label.value = _label
    x.value = e.clientX
    y.value = e.clientY
    overGrid.value = false
    dragging.value = true
    activePointerId = e.pointerId
    e.preventDefault()
    window.addEventListener('pointermove', onPointerMove)
    window.addEventListener('pointerup', onPointerUp)
    window.addEventListener('pointercancel', onPointerCancel)
    window.addEventListener('blur', finishDrag)
    window.addEventListener('keydown', onKeyDown)
  }
  let localDrop = null
  let localMove = null
  const onDrop = (cb) => {
    dropCb = localDrop = cb
    return () => {
      if (dropCb === cb) dropCb = null
    }
  }
  const onMove = (cb) => {
    moveCb = localMove = cb
    return () => {
      if (moveCb === cb) moveCb = null
    }
  }
  if (getCurrentScope()) {
    onScopeDispose(() => {
      if (moveCb === localMove) moveCb = null
      if (dropCb === localDrop) dropCb = null
      if (dragging.value) finishDrag()
    })
  }
  const setOverGrid = (v) => {
    overGrid.value = v
  }
  return { dragging, code, label, x, y, overGrid, startDrag, onDrop, onMove, setOverGrid }
}
