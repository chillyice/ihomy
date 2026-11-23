import { computed, onScopeDispose, ref, watch } from 'vue'

// 首页横滑卡片的公共内核:当前索引 + 自动切换定时器 + hover 暂停 + 作用域销毁时清场。
// 相册轮播 / 纪念日横滑卡都用它,别再各写一份 setInterval(旧代码两份重复计时器,其中一份漏清理)。
// count: () => number(条目数);active: () => boolean(卡片在页面上且不在编辑态);interval: 毫秒
export function useSlideShow({ count, active = () => true, interval = 5000 } = {}) {
  const index = ref(0)
  const hovered = ref(false)
  const total = computed(() => Math.max(0, Number(count?.()) || 0))
  let timer = null

  const next = () => {
    if (total.value > 1) index.value = (index.value + 1) % total.value
  }
  const prev = () => {
    if (total.value > 1) index.value = (index.value - 1 + total.value) % total.value
  }
  const go = (i) => {
    if (!total.value) return
    index.value = (((Number(i) || 0) % total.value) + total.value) % total.value
  }
  const start = () => {
    if (timer) return
    timer = setInterval(() => {
      if (!hovered.value) next()
    }, interval)
  }
  const stop = () => {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }
  const pause = (on = true) => {
    hovered.value = Boolean(on)
  }

  // 条目变少/清空时把索引收回有效区间,避免指向不存在的 slide
  watch(total, () => {
    if (!total.value) index.value = 0
    else index.value %= total.value
  })
  watch(
    [() => Boolean(active()), total],
    ([on, n]) => {
      if (on && n > 1) start()
      else stop()
    },
    { immediate: true },
  )
  onScopeDispose(stop)

  return { index, hovered, total, next, prev, go, start, stop, pause }
}
