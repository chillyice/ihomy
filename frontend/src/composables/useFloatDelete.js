import { ref } from 'vue'

/** 底部删除热区高度:指针进入视口底边往上这一段 = 松手删除(与 FloatDeleteDock 视觉高度一致) */
export const FLOAT_DELETE_ZONE = 104

/** 接近热区:拖到窗口下方这个范围才浮出删除条(再往下进入删除热区就发亮) */
export const FLOAT_APPROACH_ZONE = 260

/**
 * 浮窗「拖到底部删除」状态机(聊天浮窗 / 悬浮播放器共用)。
 * 调用顺序:拖动位移超过阈值后 begin() → 每次指针移动 update(clientY) → 松手 end() 返回是否命中。
 * begin 之前不动状态,所以「点一下悬浮球」不会被误判成删除(调用方先过 6px 位移阈值);
 * 删除条只在指针进入窗口下方 FLOAT_APPROACH_ZONE 才出现,进入 FLOAT_DELETE_ZONE 才亮。
 */
export function useFloatDelete() {
  const dragging = ref(false)
  const active = ref(false)
  const over = ref(false)

  const begin = () => {
    dragging.value = true
  }
  const update = (clientY) => {
    if (!dragging.value) return
    const fromBottom = window.innerHeight - clientY
    active.value = fromBottom <= FLOAT_APPROACH_ZONE
    over.value = fromBottom <= FLOAT_DELETE_ZONE
  }
  const end = () => {
    const hit = dragging.value && over.value
    dragging.value = false
    active.value = false
    over.value = false
    return hit
  }
  const cancel = () => {
    dragging.value = false
    active.value = false
    over.value = false
  }

  return { dragging, active, over, begin, update, end, cancel }
}
