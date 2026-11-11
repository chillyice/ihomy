// 宽屏双栏的「列内滚动净高」唯一来源:量「双栏容器顶边 → 所在滚动容器底边」还剩多高,
// 结果写进 --wide-col-max 交给 main.css 的 .wide-col。只量高度,不做视口形态判定
// (宽度与屏幕短边的判定一律在 composables/useDevice 的 isWide)。
//
// 两种外壳都按各自的滚动盒算:暖居主区是 .gc-main 自己的滚动容器,光尘是整页滚。
// 几何量一次写 ref,不放进 computed(规范 17:computed 只做纯函数)。
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

/**
 * 纯函数:滚动容器实测尺寸 → 双栏列可用高度(px)。
 * 量不到可视区(隐藏 / 未挂载)返回 0,调用方据此不写 --wide-col-max(不硬造滚动)。
 * @param {{ areaHeight?: number, topInArea?: number, min?: number, bottomGap?: number }} input
 */
export function computeColMaxHeight({ areaHeight, topInArea, min = 240, bottomGap = 24 } = {}) {
  const height = Number(areaHeight)
  const top = Number(topInArea)
  if (!Number.isFinite(height) || !Number.isFinite(top) || height <= 0) return 0
  // 下限 240:再矮就只剩标题栏,宁可让外壳稍微滚一点也不把列表压没
  return Math.max(min, Math.round(height - top - bottomGap))
}

/**
 * @param {import('vue').Ref<boolean>} enabled 一般是 useDevice 的 isWide
 * @returns {{ wideEl: import('vue').Ref, colStyle: import('vue').ComputedRef, remeasure: Function, measure: Function }}
 */
export function useWideCols(enabled) {
  const wideEl = ref(null)
  const colMaxH = ref(0)
  const colStyle = computed(() => (enabled.value && colMaxH.value > 0 ? { '--wide-col-max': `${colMaxH.value}px` } : {}))

  const measure = () => {
    if (!enabled.value) {
      colMaxH.value = 0
      return
    }
    const el = wideEl.value
    if (!el) {
      colMaxH.value = 0
      return
    }
    const box = el.closest('.gc-main')
    const areaTop = box ? box.getBoundingClientRect().top : 0
    const areaHeight = box ? box.clientHeight : window.innerHeight
    const scrolled = box ? box.scrollTop : window.scrollY
    const topInArea = el.getBoundingClientRect().top - areaTop + scrolled
    colMaxH.value = computeColMaxHeight({ areaHeight, topInArea })
  }

  const remeasure = () => nextTick(measure)
  const onViewportChange = () => remeasure()

  // 进 / 出宽屏时先量一次:DOM 的宽窄分支可能刚切换,等渲染完再量
  watch(enabled, () => remeasure())

  onMounted(() => {
    window.addEventListener('resize', onViewportChange)
    window.addEventListener('orientationchange', onViewportChange)
    remeasure()
  })

  onBeforeUnmount(() => {
    window.removeEventListener('resize', onViewportChange)
    window.removeEventListener('orientationchange', onViewportChange)
  })

  return { wideEl, colStyle, remeasure, measure }
}
