import { ref, watch } from 'vue'

// Each widget owns its own request. Leaving the layout or the family invalidates the response.
export function useHomeCardData(visible, scope, load, apply, clear) {
  const status = ref('idle')
  const attempt = ref(0)
  const retry = () => attempt.value++
  watch(
    [visible, scope, attempt],
    async ([show], previous, onCleanup) => {
      let active = true
      onCleanup(() => {
        active = false
      })
      if (!show || previous[1] !== scope.value) clear()
      if (!show) {
        status.value = 'idle'
        return
      }
      status.value = 'loading'
      try {
        const value = await load()
        if (active) {
          apply(value)
          status.value = 'ready'
        }
      } catch {
        if (active) status.value = 'error'
      }
    },
    { immediate: true },
  )
  return { status, retry }
}
