<template>
  <div class="page">
    <div class="wh">
      <LastDoingCard v-if="!isMobile && !isTablet" variant="mobile" />
      <header class="wh-head">
        <div>
          <h1>{{ app.familyName || $t('windowHome.family') }}</h1>
          <p>{{ dateLabel }}</p>
        </div>
        <router-link v-if="weather && enabled('weather')" to="/weather" class="wh-weather"
          ><el-icon><Sunny /></el-icon>{{ weather.text }} {{ weather.temp }}°</router-link
        >
      </header>
      <div class="wh-grid" :class="{ 'has-day': showDay }">
        <section class="wh-lead">
          <h2>{{ $t('windowHome.story') }}</h2>
          <router-link v-if="hero" :to="hero.path" class="wh-feature">
            <img v-if="hero.img" :src="hero.img" :alt="hero.title" class="wh-photo" />
            <div class="wh-feature-copy">
              <span class="wh-kind">{{ hero.type }}</span>
              <h3>{{ hero.title }}</h3>
              <p v-if="hero.author || hero.when" class="wh-byline">
                <span v-if="hero.author">{{ hero.author }}</span
                ><time v-if="hero.when">{{ hero.when }}</time>
              </p>
            </div>
          </router-link>
          <p v-else-if="feedState === 'loading'" aria-live="polite">{{ $t('common.loading') }}</p>
          <p v-else-if="feedState !== 'error'" class="wh-empty">{{ $t('windowHome.noStory') }}</p>
          <div v-if="feedState === 'error'" role="status">
            {{ $t('windowHome.failed') }} <el-button text @click="retryFeed">{{ $t('common.retry') }}</el-button>
          </div>
        </section>
        <section v-if="showDay" class="wh-agenda">
          <h2>
            <el-icon><Calendar /></el-icon>{{ $t('windowHome.next') }}
          </h2>
          <div v-if="dayState === 'error'" role="status">
            {{ $t('windowHome.failed') }} <el-button text @click="retryDay">{{ $t('common.retry') }}</el-button>
          </div>
          <div v-for="it in upcoming.slice(0, 3)" :key="it.kind + ':' + it.id" class="wh-event-row">
            <router-link class="wh-event" :to="dayEventPath(it, date)">
              <span class="wh-hour">{{ it.time || $t('windowHome.untimed') }}</span
              ><strong>{{ it.title }}</strong>
              <small v-if="it.readOnly">{{
                it.sharedFrom
                  ? $t('audience.sharedFrom', { name: it.sharedFrom.alias || it.sharedFrom.familyName || '' })
                  : $t('windowHome.shared')
              }}</small>
            </router-link>
            <el-button
              v-if="canComplete(it)"
              text
              :disabled="saving || dayState === 'loading'"
              :title="$t('schedule.actionDone')"
              :aria-label="$t('schedule.actionDone')"
              :icon="Check"
              @click="complete(it)"
            />
          </div>
          <router-link class="wh-textlink" to="/plan?tab=today"
            >{{ $t('windowHome.daily') }} <el-icon><ArrowRight /></el-icon
          ></router-link>
        </section>
      </div>
      <section v-if="recent.length" class="wh-recent">
        <h2>{{ $t('windowHome.recent') }}</h2>
        <router-link v-for="it in recent" :key="it.key" class="wh-activity" :to="it.path">
          <img v-if="it.img" :src="it.img" :alt="it.title" loading="lazy" />
          <div>
            <span class="wh-kind">{{ it.type }}</span
            ><strong>{{ it.title }}</strong
            ><small>{{ [it.author, it.when].filter(Boolean).join(' · ') }}</small>
          </div>
          <el-icon><ArrowRight /></el-icon>
        </router-link>
      </section>
      <div v-if="ownFamily && enabled('book') && user.canReadBook" class="wh-actions">
        <router-link class="wh-textlink" :to="user.canWriteBook ? '/book?new=1' : '/book'"
          ><el-icon><Wallet /></el-icon>{{ $t(user.canWriteBook ? 'windowHome.record' : 'windowHome.ledger') }}</router-link
        >
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, inject, onActivated, onDeactivated, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowRight, Calendar, Sunny, Wallet, Check } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { useDevice } from '@/composables/useDevice'
import { useDisplayMode } from '@/composables/useDisplayMode'
import { useHomeFeed } from '@/composables/useHomeFeed'
import { useHomeCardData } from '@/composables/useHomeCardData'
import { scheduleApi, reminderApi, planApi } from '@/api'
import { feedSummary, feedTypeLabel } from '@/utils/feed'
import LastDoingCard from '@/components/lastdoing/LastDoingCard.vue'
import { formatRelativeTime } from '@/utils/datetime'
import { todayStr } from '@/utils/date'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import { dayEventPath, homeFeedModule, remainingDayEvents, storyPath, storyImage } from '@/utils/windowHome'
import { isChildAllowedPath } from '@/utils/displayMode'
import { NAV_PATHS } from '@/utils/navModules'
import { isNativeApp } from '@/utils/platform'
const app = useAppStore()
const user = useUserStore()
const route = useRoute()
const { t, locale } = useI18n()
const { isMobile, isTablet } = useDevice()
const { isChild } = useDisplayMode()
const sun = inject(SUN_LIGHT_KEY, null)
const weather = computed(() => sun?.weather?.value)
const { scope, fetchFeed } = useHomeFeed()
const active = ref(true)
const now = ref(new Date())
const tick = () => {
  if (!document.hidden && active.value) now.value = new Date()
}
const date = computed(() => todayStr(now.value))
const dateLabel = computed(() =>
  new Intl.DateTimeFormat(locale.value, { month: 'long', day: 'numeric', weekday: 'long' }).format(now.value),
)
let timer
onMounted(() => {
  timer = window.setInterval(tick, 60000)
  document.addEventListener('visibilitychange', tick)
})
onBeforeUnmount(() => {
  clearInterval(timer)
  document.removeEventListener('visibilitychange', tick)
})
onActivated(() => {
  active.value = true
  tick()
})
onDeactivated(() => {
  active.value = false
})
const moduleCodes = computed(() => new Set(app.modules.filter((m) => m.enabled !== 0).map((m) => m.code)))
const enabled = (code) =>
  (code === 'weather' || moduleCodes.value.has(code)) && (!isChild.value || isChildAllowedPath(NAV_PATHS[code] || '/weather'))
const ownFamily = computed(
  () => !!user.isLoggedIn && !route.query.hid && !route.query.home_id && !isChild.value && !user.mustChangePassword,
)
const feedRows = ref([])
const feedVisible = computed(() => active.value && !user.mustChangePassword)
const { status: feedState, retry: retryFeed } = useHomeCardData(
  feedVisible,
  scope,
  fetchFeed,
  (rows) => {
    feedRows.value = Array.isArray(rows) ? rows : []
  },
  () => {
    feedRows.value = []
  },
)
const photos = computed(() => (enabled('album') ? app.photos || [] : []))
const stories = computed(() =>
  feedRows.value
    .filter((row) => enabled(homeFeedModule(row)))
    .map((row) => {
      const img = storyImage(row)
      const photo = row.type === 'photo' ? photos.value.find((p) => p.url === img) : null
      return {
        key: row.type + ':' + row.id,
        kind: row.type,
        type: feedTypeLabel(t, row.type),
        path: storyPath(row, photo),
        img,
        title: row.descriptions?.[0] || feedSummary(t, row, 100) || row.title || feedTypeLabel(t, row.type),
        author: row.authorName,
        when: row.createdAt ? formatRelativeTime(t, row.createdAt) : '',
      }
    })
    .filter((it) => it.path),
)
const hero = computed(() => {
  const pictured = stories.value.slice(0, 8).find((it) => it.img && ['photo', 'diary', 'blog'].includes(it.kind))
  if (pictured) return pictured
  const photo = photos.value[0]
  return photo?.url
    ? {
        key: 'album:' + photo.id,
        type: t('windowHome.image'),
        path: storyPath({ type: 'photo' }, photo),
        img: photo.url,
        title: photo.description || t('windowHome.image'),
        when: formatRelativeTime(t, photo.takenAt || photo.createdAt),
        author: photo.authorName,
      }
    : stories.value[0]
})
const recent = computed(() =>
  stories.value.filter((it) => it.key !== hero.value?.key && !(it.kind === 'photo' && it.img && it.img === hero.value?.img)),
)
const dayItems = ref([])
const dayScope = computed(() => scope.value + ':' + date.value)
const dayVisible = computed(() => active.value && ownFamily.value && enabled('plan'))
const { status: dayState, retry: retryDay } = useHomeCardData(
  dayVisible,
  dayScope,
  () => scheduleApi.events(date.value, date.value),
  (result) => {
    dayItems.value = result?.days?.[date.value] || []
  },
  () => {
    dayItems.value = []
  },
)
const upcoming = computed(() =>
  remainingDayEvents(dayItems.value, now.value).filter((it) => !it.link?.startsWith('/book') || (enabled('book') && user.canReadBook)),
)
const showDay = computed(() => dayVisible.value && (upcoming.value.length || dayState.value === 'error'))
const saving = ref(false)
const canComplete = (it) => ownFamily.value && !it.readOnly && ['TASK', 'REMINDER', 'PLAN_TASK'].includes(it.kind)
async function complete(it) {
  if (saving.value || !canComplete(it)) return
  saving.value = true
  const savedScope = dayScope.value
  try {
    if (it.kind === 'PLAN_TASK') await planApi.updateTask(it.id, { done: 1 })
    else await reminderApi.toggleDone(it.id, date.value)
    if (savedScope !== dayScope.value || !active.value) return
    dayItems.value = dayItems.value.filter((row) => row.kind !== it.kind || row.id !== it.id)
    ElMessage.success(t('common.success'))
    retryDay()
    if (isNativeApp()) import('@/native/reminders').then((m) => m.syncNotifications()).catch(() => {})
  } catch {
    if (savedScope === dayScope.value) ElMessage.error(t('morning.toggleFailed'))
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.wh {
  padding: clamp(12px, 3vw, 36px) 0;
  color: var(--color-text);
}
.wh-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 26px;
}
.wh-head h1 {
  margin: 0;
  font-size: clamp(26px, 3.8vw, 42px);
  line-height: 1.2;
  letter-spacing: -0.03em;
}
.wh-head p {
  margin: 8px 0 0;
  color: var(--color-text-secondary);
  font-size: 15px;
}
.wh-weather,
.wh-textlink {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--color-brand);
  text-decoration: none;
  font-weight: 600;
  min-height: 44px;
}
.wh-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: clamp(14px, 2.2vw, 28px);
}
.wh-grid.has-day {
  grid-template-columns: minmax(0, 1.8fr) minmax(210px, 1fr);
}
.wh h2 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 19px;
  margin: 0 0 16px;
}
.wh-feature,
.wh-activity,
.wh-event {
  color: inherit;
  text-decoration: none;
}
.wh-feature {
  display: block;
  border-radius: 16px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  overflow: hidden;
}
.wh-feature:hover h3,
.wh-activity:hover strong,
.wh-event:hover strong {
  color: var(--color-brand);
}
.wh-photo {
  display: block;
  width: 100%;
  height: clamp(220px, 25vw, 370px);
  object-fit: cover;
}
.wh-feature-copy {
  padding: clamp(18px, 2vw, 28px);
}
.wh-feature h3 {
  font-size: clamp(22px, 2.5vw, 32px);
  line-height: 1.4;
  margin: 8px 0;
  overflow-wrap: anywhere;
}
.wh-kind,
.wh-byline,
.wh-activity small,
.wh-event small,
.wh-empty {
  color: var(--color-text-secondary);
  font-size: 14px;
}
.wh-byline {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin: 8px 0 0;
}
.wh-event-row {
  display: flex;
  align-items: center;
  gap: 8px;
  border-top: 1px solid var(--color-border);
}
.wh-event {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: start;
  gap: 5px;
  padding: 16px 0;
}
.wh-event strong,
.wh-activity strong {
  overflow-wrap: anywhere;
}
.wh-hour {
  background: var(--window-honey);
  color: var(--window-honey-text);
  border-radius: 5px;
  font-size: 14px;
  font-weight: 650;
  padding: 2px 6px;
}
.wh-recent {
  margin: 28px 0 0;
}
.wh-activity {
  display: flex;
  gap: 14px;
  align-items: center;
  padding: 14px 0;
  border-top: 1px solid var(--color-border);
}
.wh-activity img {
  width: 74px;
  height: 60px;
  object-fit: cover;
  border-radius: 8px;
}
.wh-activity div {
  flex: 1;
  min-width: 0;
  display: grid;
  gap: 4px;
}
.wh-actions {
  margin-top: 20px;
}
@media (max-width: 899px) and (orientation: portrait), (max-width: 599px) {
  .wh-head {
    margin-bottom: 16px;
    padding-bottom: 16px;
  }
  .wh-grid.has-day {
    display: flex;
    flex-direction: column;
  }
  .wh-agenda {
    order: -1;
  }
  .wh-agenda h2 {
    font-size: 15px;
    margin-bottom: 6px;
  }
  .wh-agenda .wh-event {
    flex-direction: row;
    align-items: center;
    gap: 10px;
    padding: 12px;
    background: var(--color-card);
    border: 1px solid var(--color-border);
    border-radius: 10px;
  }
  .wh-agenda .wh-event-row {
    border: 0;
  }
  .wh-agenda .wh-event-row ~ .wh-event-row,
  .wh-agenda .wh-textlink {
    display: none;
  }
  .wh-photo {
    height: clamp(180px, 55vw, 330px);
  }
}
</style>
