import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { userPrefApi } from '@/api'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { useDisplayMode } from './useDisplayMode'
import { isFeatureVisibleNow } from './useFeatureGates'
import { readPref } from '@/utils/userPref'
import { parseWindowFavorites, WINDOW_PREF, windowEntries } from '@/utils/windowDirectory'

export function useWindowDirectory() {
  const { t, te, locale } = useI18n()
  const router = useRouter()
  const app = useAppStore()
  const user = useUserStore()
  const { isChild } = useDisplayMode()
  const entries = computed(() =>
    windowEntries(app.modules, {
      hasOps: user.isOps || user.isOwner,
      loggedIn: user.isLoggedIn,
      child: isChild.value,
      isVisible: isFeatureVisibleNow,
      allows(item) {
        if (item.code === 'book' && (!user.isLoggedIn || !user.canReadBook)) return false
        if (item.code === 'vault' && !(user.isOwner || user.hasPerm('vault:view'))) return false
        return user.isLoggedIn || !router.resolve(item.path).meta.requiresAuth
      },
      label(code, title) {
        if (te('windowHome.labels.' + code)) return t('windowHome.labels.' + code)
        return locale.value === 'en' && te('warm.module.' + code) ? t('warm.module.' + code) : title
      },
    }),
  )
  const account = computed(() => (user.isLoggedIn ? user.userInfo?.id : null))
  const localKey = computed(() => 'ihomy:' + WINDOW_PREF + ':' + (account.value ?? 'guest'))
  const codes = ref([])
  const busy = ref(false)
  const favorites = computed(() => codes.value.map((code) => entries.value.find((item) => item.code === code)).filter(Boolean))
  const storeLocal = (key, raw) => {
    try {
      localStorage.setItem(key, raw)
    } catch {
      /* Remote preferences also work without local storage. */
    }
  }
  let generation = 0
  watch(
    localKey,
    async (key) => {
      const current = ++generation
      const raw = readPref(key)
      codes.value = parseWindowFavorites(raw)
      busy.value = !!account.value
      if (!account.value) return
      try {
        const remote = await userPrefApi.all()
        if (current !== generation) return
        const value = remote?.[WINDOW_PREF]
        if (typeof value === 'string') {
          codes.value = parseWindowFavorites(value)
          storeLocal(key, value)
        }
      } catch {
        /* Keep this account's cached preferences when offline. */
      } finally {
        if (current === generation) busy.value = false
      }
    },
    { immediate: true, flush: 'sync' },
  )

  async function save(next) {
    if (busy.value) return false
    const current = generation
    const raw = JSON.stringify(next)
    busy.value = true
    try {
      if (account.value) await userPrefApi.save(WINDOW_PREF, raw)
      if (generation !== current) return false
      storeLocal(localKey.value, raw)
      codes.value = [...next]
      return true
    } finally {
      if (generation === current) busy.value = false
    }
  }
  return { entries, codes, favorites, busy, save }
}
