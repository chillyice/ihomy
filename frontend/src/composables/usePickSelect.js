// 资源页长按多选(统一封装):
// - 长按(PICK_PRESS_MS)进入多选模式并预选该项,触觉反馈 haptic('select');
// - 松手后的 click 用 swallowUntil 时间窗吞掉(与 useRowGestures 同款时间戳范式),
//   由页面 click 处理器开头调 swallowClick() 判断,避免长按选中后又弹出详情;
// - 选择集/计数/全选/清除都在这里,批量操作条与确认逻辑仍由各页组织。
import { computed, ref } from 'vue'
import { haptic } from '@/utils/haptics'

/** 长按进多选的按压时长(与 useRowGestures 长按菜单同量级) */
export const PICK_PRESS_MS = 450

export function usePickSelect({ enabled = () => true, onLongPress } = {}) {
  const selectMode = ref(false)
  const selectedIds = ref([])
  let pressTimer = null
  let swallowUntil = 0

  const count = computed(() => selectedIds.value.length)
  const has = (id) => selectedIds.value.includes(id)

  const toggle = (id) => {
    const i = selectedIds.value.indexOf(id)
    if (i >= 0) selectedIds.value.splice(i, 1)
    else selectedIds.value.push(id)
  }

  /** 确保选中(长按进多选时的预选) */
  const pick = (id) => {
    if (id != null && !selectedIds.value.includes(id)) selectedIds.value.push(id)
  }

  const enter = (firstId = null) => {
    selectMode.value = true
    selectedIds.value = firstId != null ? [firstId] : []
  }

  const exit = () => {
    selectMode.value = false
    selectedIds.value = []
  }

  const clear = () => {
    selectedIds.value = []
  }

  const selectAll = (ids) => {
    selectedIds.value = [...ids]
  }

  // ---- 长按进入多选 ----
  /**
   * pointerdown 时调用;返回 true 表示已接管计时(页面照常记 pointerup/cancel 取消)。
   * 已在多选模式或不启用长按(桌面/只读)时不接管,返回 false。
   */
  const pressStart = (item) => {
    if (!enabled() || selectMode.value) return false
    cancelPress()
    pressTimer = setTimeout(() => {
      pressTimer = null
      // 吞掉松手 click,避免长按选中后紧接着弹出详情
      swallowUntil = Date.now() + 700
      haptic('select')
      enter(item?.id)
      onLongPress?.(item)
    }, PICK_PRESS_MS)
    return true
  }

  const cancelPress = () => {
    if (pressTimer) {
      clearTimeout(pressTimer)
      pressTimer = null
    }
  }

  /** 页面 click 处理器开头调用:刚发生长按时返回 true,该次 click 应忽略 */
  const swallowClick = () => {
    if (Date.now() < swallowUntil) {
      swallowUntil = 0
      return true
    }
    return false
  }

  const dispose = () => cancelPress()

  return {
    selectMode,
    selectedIds,
    count,
    has,
    toggle,
    pick,
    enter,
    exit,
    clear,
    selectAll,
    pressStart,
    cancelPress,
    swallowClick,
    dispose,
  }
}
