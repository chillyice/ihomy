import { computed, onMounted, onScopeDispose, ref, shallowRef, toValue, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { notificationApi, planApi } from '@/api'
import { activityRevision } from '@/api/activity'
import { useAppStore } from '@/stores/app'
import { useChatStore } from '@/stores/chat'
import { useUserStore } from '@/stores/user'
import { buildDeskObjects, deskPathAllowed } from '@/utils/deskObjects'
import { materialSeed } from '@/utils/warmMaterials'
import { NEED_FILE_ORIGIN, SERVER_ORIGIN } from '@/utils/assetUrl'
import { useDisplayMode } from './useDisplayMode'
import { isPathHiddenNow } from './useFeatureGates'

const REFRESH_MS = 3 * 60 * 1000

// Keep two independent summaries: a change to a plan never refetches the unread count.
// In-flight results from a previous family or an older revision cannot replace current state.
function useSummary(scope, active, clock, revision, load) {
  const value = shallowRef(null)
  let owner,
    version,
    checked = 0
  watch(
    [scope, active, clock, revision],
    async ([nextOwner, awake, now, nextVersion], previous, onCleanup) => {
      if (owner !== nextOwner || version !== nextVersion) {
        value.value = null
        owner = nextOwner
        version = nextVersion
        checked = 0
      }
      if (!owner || !awake || now.getTime() - checked < REFRESH_MS) return
      let current = true
      onCleanup(() => {
        current = false
      })
      try {
        const result = await load({ background: true })
        if (current) value.value = result
      } catch {
        if (current) value.value = null
      } finally {
        if (current) checked = now.getTime()
      }
    },
    { immediate: true },
  )
  return value
}

export function useDeskObjects(enabled, destinations = {}, arrangement = 0) {
  const app = useAppStore()
  const user = useUserStore()
  const chat = useChatStore()
  const route = useRoute()
  const { t } = useI18n()
  const { isChild } = useDisplayMode()
  const now = shallowRef(new Date())
  const visible = ref(!document.hidden)
  const awake = computed(() => !!toValue(enabled) && visible.value)
  let timer
  const tick = () => {
    visible.value = !document.hidden
    if (visible.value) now.value = new Date()
  }
  onMounted(() => {
    document.addEventListener('visibilitychange', tick)
    window.addEventListener('focus', tick)
    timer = setInterval(tick, 60_000)
  })
  onScopeDispose(() => {
    clearInterval(timer)
    document.removeEventListener('visibilitychange', tick)
    window.removeEventListener('focus', tick)
  })

  const ownFamily = computed(() => {
    const info = user.userInfo
    return app.familyLoaded &&
      user.isLoggedIn &&
      info?.id &&
      info.familyId &&
      !user.mustChangePassword &&
      !user.isPureOps &&
      String(app.family?.id) === String(info.familyId) &&
      !route.query.hid &&
      !route.query.home_id
      ? [info.id, info.familyId, app.generation].join('/')
      : ''
  })
  const allowedPath = (path) => deskPathAllowed(path, app.modules, isChild.value, isPathHiddenNow)
  const scopeFor = (path) => computed(() => (allowedPath(path) ? ownFamily.value : ''))
  const agenda = useSummary(scopeFor('/plan'), awake, now, () => activityRevision.agenda, planApi.today)
  const unread = useSummary(scopeFor('/notification'), awake, now, () => activityRevision.notifications, notificationApi.unreadCount)

  // Album uploads, moves and visibility changes invalidate the home photo strip.
  // Coalesce a burst of writes and use the store's existing account-generation guard.
  let photoAttempt = { key: '', time: 0 }
  watch(
    [awake, now, () => app.generation, () => activityRevision.photos, () => app.loading, () => app.familyLoaded, () => app.photosUpdated],
    ([active, clock, generation, revision], previous, onCleanup) => {
      if (!active || !app.familyLoaded || app.loading || !allowedPath('/album')) return
      if (app.photosRevision === revision && clock.getTime() - app.photosUpdated < 30 * 60_000) return
      const key = generation + '/' + revision
      if (photoAttempt.key === key && clock.getTime() - photoAttempt.time < REFRESH_MS) return
      const refresh = setTimeout(() => {
        photoAttempt = { key, time: clock.getTime() }
        app.refreshPhotos().catch(() => {})
      }, 250)
      onCleanup(() => clearTimeout(refresh))
    },
    { immediate: true },
  )

  const candidates = computed(() => {
    if (!toValue(enabled) || !app.familyLoaded || user.isPureOps || user.mustChangePassword) return []
    return buildDeskObjects({
      now: now.value,
      seed: materialSeed + (Number(toValue(arrangement)) || 0),
      origin: NEED_FILE_ORIGIN ? SERVER_ORIGIN : location.origin,
      photos: app.photosRevision === activityRevision.photos && !route.query.hid && !route.query.home_id ? app.photos : [],
      familyId: app.family?.id,
      modules: app.modules,
      child: isChild.value,
      hidden: isPathHiddenNow,
      agenda: ownFamily.value ? agenda.value : null,
      notices: ownFamily.value ? Number(unread.value) : 0,
      messages: ownFamily.value ? chat.totalUnread : 0,
      destinations: toValue(destinations),
    }).map((item) =>
      item.destinationLabel
        ? {
            ...item,
            labelParams: {
              item: t(item.kind === 'cup' ? 'windowDesk.' + item.art : 'windowDesk.items.' + item.kind),
              destination: t(item.destinationLabel),
            },
          }
        : item,
    )
  })
  return { candidates }
}
