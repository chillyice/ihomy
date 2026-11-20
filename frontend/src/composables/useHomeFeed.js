import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { homeApi, publicApi } from '@/api'
import { parseShareQuery } from '@/utils/shareLanding'

export function useHomeFeed() {
  const route = useRoute()
  const user = useUserStore()
  const share = computed(() => parseShareQuery(route.fullPath.split('?')[1]?.split('#')[0]))
  const scope = computed(() => JSON.stringify([user.userInfo?.id, user.userInfo?.familyId, user.isLoggedIn, share.value]))
  const fetchFeed = () => {
    const { hid, homeId } = share.value
    if (user.isLoggedIn && !hid && !homeId) return homeApi.getFeed(20)
    return publicApi.getFeed(20, homeId ?? undefined, hid || undefined)
  }
  return { scope, fetchFeed }
}
