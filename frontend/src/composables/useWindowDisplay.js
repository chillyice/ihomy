import { computed } from 'vue'
import { useThemeStore } from '@/stores/theme'
import { useUserStore } from '@/stores/user'
import { albumApi, scheduleApi } from '@/api'
import { prefsTick } from '@/utils/screensaver'
import { originalPhotoUrl } from '@/utils/albumImages'
import { displaySchedule, displayScope, readSharedDisplay, selectedAlbumPhotos, writeSharedDisplay } from '@/utils/windowStandby'

export function useWindowDisplay() {
  const theme = useThemeStore()
  const user = useUserStore()
  const windowTheme = computed(() => theme.theme === 'window')
  const scope = computed(() => displayScope(user.userInfo))
  const shared = computed(() => {
    void prefsTick.value
    return readSharedDisplay(scope.value)
  })

  function setShared(patch) {
    writeSharedDisplay(scope.value, { ...shared.value, ...patch })
    prefsTick.value++
  }

  async function loadPhotos() {
    const id = shared.value.albumId
    if (!id) return []
    return selectedAlbumPhotos(await albumApi.detail(id), id).map((photo) => ({ ...photo, url: originalPhotoUrl(photo.url) }))
  }

  async function loadSchedule() {
    return shared.value.schedule ? displaySchedule(await scheduleApi.upcoming(7)) : []
  }

  return { windowTheme, scope, shared, setShared, loadPhotos, loadSchedule }
}
