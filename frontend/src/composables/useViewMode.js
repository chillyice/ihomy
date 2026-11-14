// 资源页视图模式(网格 grid / 列表 list)持久化:每页一个键 ihomy:view:<pageKey>。
// 列表模式自带多选(行首 pick-badge),网格模式靠长按/工具栏进入多选。
import { ref, watch } from 'vue'

export function useViewMode(pageKey, dflt = 'grid') {
  const viewMode = ref(localStorage.getItem(`ihomy:view:${pageKey}`) || dflt)
  watch(viewMode, (v) => {
    try {
      localStorage.setItem(`ihomy:view:${pageKey}`, v)
    } catch {
      /* 存不下就用本会话内存态 */
    }
  })
  return viewMode
}
