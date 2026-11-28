import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { useAppStore } from '@/stores/app'
import { buildDoingPath, useLastDoing, visibleDoing } from '@/utils/lastDoing'
import { deskPathAllowed } from '@/utils/deskObjects'
import { useDisplayMode } from './useDisplayMode'
import { isPathHiddenNow } from './useFeatureGates'

export function useRecentActivities() {
  const app = useAppStore()
  const { isChild } = useDisplayMode()
  const { items } = useLastDoing()
  const now = ref(Date.now())
  let timer
  onMounted(() => {
    timer = setInterval(() => {
      now.value = Date.now()
    }, 60_000)
  })
  onBeforeUnmount(() => clearInterval(timer))
  return computed(() =>
    visibleDoing(items.value, now.value).filter((entry) =>
      deskPathAllowed(buildDoingPath(entry), app.modules, isChild.value, isPathHiddenNow),
    ),
  )
}
