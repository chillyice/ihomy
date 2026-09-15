<!-- 光尘首页:12 列网格 + 天气「活窗」(光尘签名)。与暖居仪表盘(4 档 snap 拖拽)完全不同 -->
<template>
  <div class="gc-home">
    <div class="gc-main-head">
      <div>
        <h2 class="gc-h2">{{ greeting }}</h2>
        <div class="gc-subline">{{ familyName }} · {{ memberCount }} 位家人</div>
      </div>
      <div class="gc-head-btns">
        <button class="gc-btn primary sm" @click="$router.push('/blog')">+ 发点什么</button>
        <button class="gc-btn ghost sm" @click="$router.push('/member')">家人</button>
      </div>
    </div>

    <div class="gc-grid">
      <!-- 天气「活窗」 -->
      <div class="gc-card gc-win-card">
        <h3 class="gc-card-h3"><span>天气窗 · {{ weatherCity }}</span><span class="gc-muted">实时</span></h3>
        <div class="gc-glass" @click="$router.push('/weather')">
          <div v-if="weatherBg" class="gc-win-bg" :style="{ backgroundImage: `url(${weatherBg})` }"></div>
          <div class="gc-win-glow"></div>
          <span class="gc-win-dust" style="animation-delay:-1s"></span>
          <span class="gc-win-dust" style="animation-delay:-4s;left:30%;top:12%"></span>
          <span class="gc-win-dust" style="animation-delay:-7s;left:12%;top:50%"></span>
          <span class="gc-win-dust" style="animation-delay:-10s;left:44%;top:30%"></span>
          <div class="gc-win-info">
            <div class="gc-temp">{{ weatherTemp }}°<span class="gc-c">C</span>
              <span v-if="todayHigh != null" class="gc-hilo">↑{{ todayHigh }}° ↓{{ todayLow ?? '—' }}°</span>
            </div>
            <div class="gc-we">{{ weatherText }}</div>
            <div class="gc-st">{{ weatherSub }}</div>
          </div>
          <div v-if="forecast.length" class="gc-forecast">
            <div v-for="d in forecast" :key="d.fxDate" class="gc-fc-item">
              <span class="gc-fc-date">{{ fcDate(d.fxDate) }}</span>
              <span class="gc-fc-range">{{ d.tempMin }}~{{ d.tempMax }}°</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 家人动态 -->
      <div class="gc-card gc-c4">
        <h3 class="gc-card-h3">家人动态</h3>
        <div v-if="feeds.length" class="gc-list">
          <div v-for="(f, i) in feeds.slice(0, 3)" :key="i" class="gc-row" @click="goFeed(f)">
            <span class="gc-ic">{{ feedIcon(f.type) }}</span>
            <span class="gc-val">{{ f.authorName || '家人' }}</span>
            <span class="gc-muted">{{ feedSummary(f) }}</span>
          </div>
        </div>
        <div v-else class="gc-empty">暂无动态</div>
      </div>

      <!-- 纪念日 -->
      <div class="gc-card gc-c4" @click="$router.push('/anniversary')">
        <h3 class="gc-card-h3">纪念日</h3>
        <template v-if="anniversaries.length">
          <div class="gc-val-lg">{{ anniversaries[0].label }}</div>
          <div class="gc-muted">{{ anniversaries[0].date }} · 还有 {{ anniversaries[0].days }} 天</div>
          <div class="gc-meter"><i :style="{ width: Math.min(100, anniversaries[0].days) + '%' }"></i></div>
        </template>
        <div v-else class="gc-empty">暂无纪念日</div>
      </div>

      <!-- 本月收支 -->
      <div class="gc-card gc-c4" @click="$router.push('/book')">
        <h3 class="gc-card-h3">本月收支</h3>
        <div class="gc-flex-baseline">
          <div class="gc-val-lg">¥ {{ fmt(balance) }}</div>
          <div class="gc-muted">结余</div>
        </div>
        <div class="gc-fin-row">
          <span class="gc-fin in">+{{ fmt(bookSummary.income) }}</span>
          <span class="gc-fin out">-{{ fmt(bookSummary.expense) }}</span>
        </div>
      </div>

      <!-- 寻物 -->
      <div class="gc-card gc-c4">
        <h3 class="gc-card-h3">寻物</h3>
        <div class="gc-ctrl-row">
          <button class="gc-btn primary sm" @click="$router.push('/item')">找东西 →</button>
        </div>
        <div v-if="items.length" class="gc-list">
          <div v-for="it in items.slice(0, 2)" :key="it.id" class="gc-row">
            <span class="gc-ic">📦</span><span>{{ it.name }}</span><span class="gc-tag">{{ it.room_name || it.house_name || '—' }}</span>
          </div>
        </div>
        <div v-else class="gc-empty">尚未登记物品</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useThemeStore } from '@/stores/theme'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import { publicApi, bookApi, itemApi } from '@/api'

const router = useRouter()
const themeStore = useThemeStore()
const appStore = useAppStore()
const userStore = useUserStore()
const sunLight = inject(SUN_LIGHT_KEY)

defineProps({ weatherBg: { type: String, default: '' } })

const familyName = computed(() => appStore.familyName)
const memberCount = computed(() => appStore.stats.memberCount || 0)
const anniversaries = computed(() => appStore.stats.upcomingEvents || [])

const feeds = ref([])
const bookSummary = ref({ income: 0, expense: 0, balance: 0 })
const items = ref([])
const balance = computed(() => bookSummary.value?.balance ?? 0)

onMounted(async () => {
  const [feed, book, it] = await Promise.all([
    publicApi.getFeed(20).catch(() => []),
    bookApi.summary().catch(() => null),
    itemApi.list({}).catch(() => []),
  ])
  feeds.value = feed || []
  if (book) bookSummary.value = book
  items.value = (it || []).slice(0, 4)
})

const weatherCity = computed(() => sunLight?.weather?.value?.city || appStore.familyName || '杭州')
const weatherTemp = computed(() => sunLight?.weather?.value?.temp ?? 24)
const weatherText = computed(() => sunLight?.weather?.value?.text || (sunLight?.weather?.value?.condition === 'cloud' ? '多云' : '晴 · 白云缓移'))
const weatherSub = computed(() => {
  const t = sunLight?.weather?.value?.temp
  if (t != null) return t >= 28 ? '仿佛窗外就是午后阳光' : t >= 15 ? '不冷不热，正好在家' : '屋里有灯，心里就暖'
  return ''
})
const weatherDetail = computed(() => sunLight?.weatherDetail?.value)
const todayHigh = computed(() => weatherDetail.value?.daily?.[0]?.tempMax ?? null)
const todayLow = computed(() => weatherDetail.value?.daily?.[0]?.tempMin ?? null)
const forecast = computed(() => (weatherDetail.value?.daily || []).slice(1, 4))
const fcDate = (d) => { const dt = new Date(d); return `${dt.getMonth() + 1}/${dt.getDate()}` }

const FEED_ICON = { blog: '📝', diary: '📖', photo: '📷', video: '🎬', wish: '🎁', task: '🎯', recipe: '🍳', book: '📚' }
const FEED_ROUTES = { blog: '/blog', diary: '/diary', photo: '/album', video: '/cinema', wish: '/wish', task: '/task', recipe: '/kitchen', book: '/library' }
const feedIcon = (type) => FEED_ICON[type] || '✨'
const feedSummary = (f) => {
  if (f.type === 'blog') return f.title || ''
  if (f.type === 'diary') return (f.content || '').slice(0, 40)
  if (f.type === 'photo') return `${f.count || 0} 张照片`
  if (f.type === 'video') return `上传了影片:${f.title || ''}`
  if (f.type === 'wish') return f.status === 'ACHIEVED' ? `实现了愿望:${f.title || ''}` : `许下愿望:${f.title || ''}`
  if (f.type === 'task') return `发布任务:${f.title || ''}`
  if (f.type === 'recipe') return `分享菜谱:${f.title || ''}`
  if (f.type === 'book') return `上架图书:《${f.title || ''}》`
  return ''
}
const goFeed = (f) => { if (f.type === 'blog' && f.id) router.push(`/blog/${f.id}`); else if (FEED_ROUTES[f.type]) router.push(FEED_ROUTES[f.type]) }
const fmt = (n) => (Number(n) || 0).toFixed(2)

const greeting = computed(() => {
  const h = new Date().getHours()
  const dawn = themeStore.mode === 'dawn'
  if (dawn) {
    if (h < 6) return '凌晨 · 好梦'
    if (h < 12) return '早安'
    if (h < 18) return '午后'
    return '傍晚'
  }
  return '晚上好 · 家人都在'
})
</script>

<style>
.gc-home { color: var(--color-text); }

.gc-main-head { display: flex; align-items: flex-end; justify-content: space-between; margin-bottom: 20px; }
.gc-h2 { font-size: 20px; margin: 0; font-weight: 650; color: var(--color-text); }
.gc-subline { font-size: 12.5px; color: var(--color-text-tertiary); margin-top: 3px; }
.gc-head-btns { display: flex; gap: 8px; }

.gc-grid { display: grid; grid-template-columns: repeat(12, 1fr); gap: 16px; }
.gc-c12 { grid-column: span 12; }
.gc-c8 { grid-column: span 8; }
.gc-c4 { grid-column: span 4; }

/* 卡片 = 家具块 */
.gc-card { background: var(--color-card); border: 1px solid var(--color-border); border-radius: 16px;
  box-shadow: var(--shadow); padding: 18px; position: relative; transition: box-shadow .35s ease, transform .35s ease; }
.gc-card:hover { box-shadow: var(--shadow-hover); }
.gc-card-h3 { font-size: 13px; font-weight: 650; margin: 0 0 12px; color: var(--color-text); display: flex; align-items: center; justify-content: space-between; }
.gc-muted { font-size: 11.5px; color: var(--color-text-tertiary); }
.gc-val-lg { font-size: 16px; font-weight: 650; color: var(--color-brand); margin-bottom: 4px; }
.gc-flex-baseline { display: flex; justify-content: space-between; align-items: baseline; }
.gc-ctrl-row { display: flex; gap: 8px; margin: 4px 0 12px; flex-wrap: wrap; }
.gc-empty { font-size: 12px; color: var(--color-text-tertiary); padding: 20px 0; text-align: center; }

/* 天气活窗 */
.gc-win-card { grid-column: span 8; overflow: hidden; }
.gc-glass { position: relative; height: 230px; border-radius: 12px; overflow: hidden; cursor: pointer;
  background: linear-gradient(160deg, var(--color-bg-2), var(--color-card-2));
  box-shadow: inset 0 0 0 1px var(--color-line), inset 0 14px 40px rgba(122, 90, 60, .08); }
.gc-glass::before { content: ""; position: absolute; inset: 0;
  background: radial-gradient(360px 200px at 74% 18%, var(--glow-warm), transparent 62%); }
.gc-glass::after { content: ""; position: absolute; left: 14%; top: -4%; width: 46%; height: 110%;
  background: url("data:image/svg+xml,%3Csvg width='340' height='760' xmlns='http://www.w3.org/2000/svg'%3E%3Cdefs%3E%3ClinearGradient id='b' x1='0' y1='0' x2='1' y2='1'%3E%3Cstop offset='0' stop-color='%23FFE9C4' stop-opacity='.38'/%3E%3Cstop offset='.55' stop-color='%23FFDDA6' stop-opacity='.13'/%3E%3Cstop offset='1' stop-color='%23FFDDA6' stop-opacity='0'/%3E%3C/linearGradient%3E%3C/defs%3E%3Crect width='340' height='760' fill='url(%23b)' transform='rotate(14 170 380)'/%3E%3C/svg%3E");
  background-size: 100% 100%; mix-blend-mode: screen; opacity: .8; transform: rotate(10deg); }
html.dark .gc-glass::after { opacity: .5; }
.gc-win-glow { position: absolute; top: 16px; right: 20px; width: 130px; height: 130px; border-radius: 50%;
  background: radial-gradient(circle, var(--glow-warm), transparent 66%); filter: blur(20px); }
.gc-win-dust { width: 4px; height: 4px; border-radius: 50%; background: #FFF0D0; position: absolute; left: 6%; top: 6%;
  filter: blur(.3px); opacity: 0; animation: gcWd 13s ease-in-out infinite; box-shadow: 0 0 6px 1px rgba(255, 236, 196, .8); }
@keyframes gcWd {
  0% { transform: translate(0, 0) rotate(0); opacity: 0; }
  14% { opacity: .9; }
  36% { transform: translate(120px, 70px) rotate(160deg); opacity: 1; }
  58% { transform: translate(40px, 150px) rotate(280deg); opacity: .7; }
  80% { transform: translate(150px, 210px) rotate(420deg); opacity: .3; }
  100% { transform: translate(90px, 250px); opacity: 0; }
}
.gc-win-info { position: absolute; left: 22px; bottom: 22px; color: var(--color-text); }
.gc-temp { font-size: 40px; font-weight: 650; line-height: 1; letter-spacing: -1px; }
.gc-c { font-size: 18px; vertical-align: top; }
.gc-we { font-size: 14px; font-weight: 600; margin-top: 6px; }
.gc-st { font-size: 12px; color: var(--color-text-secondary); margin-top: 3px; }
.gc-win-bg { position: absolute; inset: 0; background-size: cover; background-position: center; opacity: .5; }
.gc-hilo { font-size: 13px; font-weight: 600; color: var(--color-text-secondary); margin-left: 12px; }
.gc-forecast { position: absolute; right: 16px; bottom: 16px; display: flex; gap: 14px; }
.gc-fc-item { display: flex; flex-direction: column; align-items: center; gap: 3px; font-size: 11.5px; color: var(--color-text-secondary); }
.gc-fc-range { font-variant-numeric: tabular-nums; font-weight: 600; }

/* 按钮(扩展 primary/accent) */
.gc-btn.primary { background: var(--color-brand); border-color: var(--color-brand); color: var(--color-card); box-shadow: var(--shadow); }
.gc-btn.primary:hover { background: var(--color-brand-hover); }
.gc-btn.accent { background: var(--color-accent); border-color: var(--color-accent); color: #FFF7F0; box-shadow: var(--shadow); }

/* 标签 */
.gc-tag { display: inline-flex; align-items: center; gap: 4px; padding: 4px 11px; border-radius: 9px; font-size: 12px; background: var(--color-line); color: var(--color-text-secondary); }
.gc-tag.acc { background: var(--color-accent); color: #FFF7F0; }
.gc-tag.grn { background: var(--color-green); color: var(--color-card); }
.gc-tag.pri { background: var(--color-brand); color: var(--color-card); }

/* 进度条 */
.gc-meter { height: 8px; border-radius: 6px; background: var(--color-line); overflow: hidden; margin-top: 8px; }
.gc-meter > i { display: block; height: 100%; background: linear-gradient(90deg, var(--color-brand), var(--color-accent)); border-radius: 6px; }

/* 收支 */
.gc-fin-row { display: flex; gap: 14px; margin-top: 10px; font-size: 12.5px; }
.gc-fin { font-variant-numeric: tabular-nums; }
.gc-fin.in { color: var(--color-green); }
.gc-fin.out { color: var(--color-accent); }

/* 列表 */
.gc-list .gc-row { display: flex; align-items: center; gap: 8px; padding: 9px 0; border-bottom: 1px solid var(--color-line); font-size: 13px; cursor: pointer; }
.gc-list .gc-row:last-child { border-bottom: none; }
.gc-list .gc-row .gc-muted { margin-left: auto; text-align: right; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 45%; }
.gc-ic { width: 26px; height: 26px; border-radius: 8px; display: grid; place-items: center; font-size: 13px; background: var(--color-line); flex-shrink: 0; }
.gc-val { font-weight: 650; color: var(--color-brand); }

@media (max-width: 880px) {
  .gc-win-card, .gc-c4, .gc-c12 { grid-column: span 12; }
}
</style>
