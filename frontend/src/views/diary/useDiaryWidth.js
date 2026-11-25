import { ref, onMounted, onBeforeUnmount } from 'vue'

// 侧栏开合、平板分屏都会改变纸张可用宽度，视口尺寸可能不变。
export function useDiaryWidth(container) {
  const width = ref(0)
  let observer
  const measure = () => {
    width.value = container.value?.clientWidth || 0
  }
  onMounted(() => {
    measure()
    if (typeof ResizeObserver !== 'undefined') {
      observer = new ResizeObserver(measure)
      observer.observe(container.value)
    } else {
      window.addEventListener('resize', measure)
    }
  })
  onBeforeUnmount(() => {
    observer?.disconnect()
    window.removeEventListener('resize', measure)
  })
  return width
}
