<!-- 老人模式首页:大字、高对比、只留常用入口与今日安排;不加载自由布局仪表盘 -->
<template>
  <div class="elder-home">
    <header class="elder-head">
      <div class="elder-hello">{{ helloText }}</div>
      <div class="elder-date">{{ dateText }}</div>
      <div v-if="weather" class="elder-weather">
        <span class="ew-temp">{{ Math.round(Number(weather.temp) || 0) }}°</span>
        <span class="ew-text">{{ weather.text }}</span>
      </div>
    </header>

    <section class="elder-card">
      <div class="elder-section-title">{{ $t('elderHome.today') }}</div>
      <ul v-if="todayItems.length" class="elder-agenda">
        <li v-for="it in todayItems" :key="it.key" class="elder-agenda-row">
          <span class="ea-time">{{ it.timeLabel }}</span>
          <span class="ea-title">{{ it.title }}</span>
        </li>
      </ul>
      <div v-else class="elder-empty">{{ $t('elderHome.noToday') }}</div>
      <el-button class="elder-more" round size="large" @click="go('/plan?tab=today')">
        {{ $t('elderHome.viewSchedule') }}
      </el-button>
    </section>

    <nav class="elder-tiles">
      <button v-for="tile in tiles" :key="tile.path" type="button" class="elder-tile" @click="go(tile.path)">
        <span class="et-icon" aria-hidden="true">{{ tile.icon }}</span>
        <span class="et-label">{{ $t(tile.labelKey) }}</span>
      </button>
    </nav>

    <el-button class="elder-settings" text @click="go('/settings?tab=display')">
      {{ $t('elderHome.settings') }}
    </el-button>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { scheduleApi } from '@/api'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()
const sunLight = inject(SUN_LIGHT_KEY, null)

const now = ref(new Date())
const items = ref([])
const weather = computed(() => sunLight?.weather?.value || null)

// 大按钮入口:老人模式只保留最常用的六件事
const tiles = [
  { path: '/album', icon: '🖼️', labelKey: 'elderHome.tileAlbum' },
  { path: '/book', icon: '🧾', labelKey: 'elderHome.tileBook' },
  { path: '/plan?tab=today', icon: '🗓️', labelKey: 'elderHome.tileSchedule' },
  { path: '/cinema', icon: '🎬', labelKey: 'elderHome.tileCinema' },
  { path: '/music', icon: '🎵', labelKey: 'elderHome.tileMusic' },
  { path: '/chat', icon: '💬', labelKey: 'elderHome.tileChat' },
]

const pad = (n) => String(n).padStart(2, '0')
const todayStr = computed(() => `${now.value.getFullYear()}-${pad(now.value.getMonth() + 1)}-${pad(now.value.getDate())}`)
const dateText = computed(() =>
  t('elderHome.dateFmt', { m: now.value.getMonth() + 1, d: now.value.getDate(), w: t(`weatherPage.w${now.value.getDay()}`) }),
)
const helloText = computed(() => {
  const h = now.value.getHours()
  const part = h < 11 ? 'morning' : h < 18 ? 'afternoon' : 'evening'
  const name = userStore.userInfo?.nickname || userStore.userInfo?.username || ''
  return name ? t(`elderHome.hello.${part}Name`, { name }) : t(`elderHome.hello.${part}`)
})

const todayItems = computed(() =>
  items.value
    .filter((it) => it.date === todayStr.value && it.title)
    .slice(0, 5)
    .map((it) => ({
      key: it.key || `${it.kind}:${it.id}:${it.date}`,
      timeLabel: it.time ? String(it.time).slice(0, 5) : t('elderHome.allDay'),
      title: it.title,
    })),
)

const loadToday = async () => {
  if (!userStore.isLoggedIn) return
  try {
    const data = await scheduleApi.upcoming(3)
    items.value = Array.isArray(data?.items) ? data.items : Array.isArray(data) ? data : []
  } catch (e) {
    // 首页不弹错:网络抖动时保留空态
  }
}

const go = (path) => router.push(path)

onMounted(() => {
  now.value = new Date()
  loadToday()
})
</script>

<style scoped>
.elder-home {
  min-height: 100vh;
  padding: 24px clamp(16px, 4vw, 40px) 48px;
  max-width: 960px;
  margin: 0 auto;
  font-size: 18px;
}

.elder-head {
  margin-bottom: 20px;
}
.elder-hello {
  font-size: 34px;
  font-weight: 700;
  line-height: 1.25;
  color: var(--color-text);
}
.elder-date {
  margin-top: 6px;
  font-size: 20px;
  color: var(--color-text-secondary);
}
.elder-weather {
  margin-top: 10px;
  display: inline-flex;
  align-items: baseline;
  gap: 10px;
  font-size: 22px;
  color: var(--color-text);
}
.ew-temp {
  font-size: 32px;
  font-weight: 700;
}

.elder-card {
  background: var(--color-card);
  border: 2px solid var(--color-border);
  border-radius: 16px;
  padding: 18px 20px;
  box-shadow: var(--shadow);
}
.elder-section-title {
  font-size: 22px;
  font-weight: 700;
  margin-bottom: 12px;
}
.elder-agenda {
  list-style: none;
  margin: 0 0 12px;
  padding: 0;
}
.elder-agenda-row {
  display: flex;
  align-items: baseline;
  gap: 14px;
  padding: 10px 0;
  border-bottom: 1px solid var(--color-border);
  font-size: 20px;
}
.elder-agenda-row:last-child {
  border-bottom: none;
}
.ea-time {
  flex-shrink: 0;
  font-weight: 700;
  color: var(--color-accent);
  min-width: 64px;
}
.ea-title {
  word-break: break-word;
}
.elder-empty {
  font-size: 20px;
  color: var(--color-text-secondary);
  padding: 12px 0 16px;
}
.elder-more {
  font-size: 18px;
}

.elder-tiles {
  margin-top: 20px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}
.elder-tile {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 128px;
  padding: 16px 10px;
  border: 2px solid var(--color-border);
  border-radius: 16px;
  background: var(--color-card);
  color: var(--color-text);
  font-size: 20px;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.15s ease;
  -webkit-tap-highlight-color: transparent;
}
.elder-tile:active {
  transform: scale(0.97);
}
.et-icon {
  font-size: 38px;
  line-height: 1;
}
.et-label {
  text-align: center;
  line-height: 1.3;
}
.elder-settings {
  margin-top: 24px;
  font-size: 18px;
}

@media (max-width: 520px) {
  .elder-tiles {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .elder-hello {
    font-size: 28px;
  }
  .elder-tile {
    min-height: 116px;
  }
}
</style>
