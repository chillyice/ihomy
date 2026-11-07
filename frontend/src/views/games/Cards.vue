<template>
  <div ref="pageEl" class="page cards-page game-page">
    <PageToolbar root-class="game-topbar" :holder-margin="0" always>
      <GameTopActions
        :back-label="$t(backLabelKey)"
        :show-fullscreen="!!currentGame"
        :show-wide="!!currentGame"
        :is-fullscreen="isFullscreen"
        :wide="isWide"
        @back="router.push(backTarget)"
        @fullscreen="toggleFullscreen"
        @wide="toggleWide"
      />
    </PageToolbar>

    <!-- 玩法大厅:点击「纸牌」入口先到这里,选一种玩法再进牌桌,给游戏留出软件式的使用纵深 -->
    <div v-if="!currentGame" class="cards-lobby">
      <div class="cards-lobby-head">
        <div class="cards-lobby-title">{{ $t('games.cards.title') }}</div>
        <div class="cards-lobby-sub">{{ $t('games.cards.pick') }}</div>
      </div>
      <div class="cards-lobby-grid">
        <button
          v-for="g in LOBBY_GAMES"
          :key="g.key"
          type="button"
          class="cards-lobby-card card"
          @click="router.push(`/games/cards/${g.key}`)"
        >
          <div class="cards-lobby-icon">
            <el-icon :size="34"><component :is="g.icon" /></el-icon>
          </div>
          <div class="cards-lobby-name">
            {{ $t(g.titleKey) }}
            <span v-if="recent === g.key" class="cards-lobby-recent">{{ $t('games.cards.recent') }}</span>
          </div>
          <div class="cards-lobby-desc">{{ $t(g.descKey) }}</div>
          <div class="cards-lobby-best">
            <template v-if="bestOf(g.key) > 0">{{ $t('games.cards.bestScore', { n: bestOf(g.key) }) }}</template>
            <template v-else>{{ $t('games.cards.noBest') }}</template>
          </div>
          <span class="cards-lobby-enter"
            >{{ $t('games.enter') }}<el-icon><ArrowRight /></el-icon
          ></span>
        </button>
      </div>
    </div>

    <!-- 牌桌:固定一屏,牌桌内部再按宽高收缩牌面,页面不再被长牌列顶高 -->
    <div v-else ref="stageEl" class="cards-stage" :style="stageStyle">
      <KlondikeBoard v-if="currentGame === 'klondike'" />
      <SpiderBoard v-else />
    </div>
  </div>
</template>

<script setup>
// 纸牌统一入口:先玩法大厅(/games/cards)后牌桌(/games/cards/klondike|spider)两级结构。
// 页面高度锁在一屏内(暖居按主区高度 100% 的 CSS 链),牌桌组件再用 cardLayout 把牌宽与
// 叠牌步进收进这个高度;大厅展示各玩法最高分与最近在玩标记。
import { computed, inject, nextTick, onBeforeUnmount, onMounted, provide, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, Connection, Tickets } from '@element-plus/icons-vue'
import PageToolbar from '@/components/PageToolbar.vue'
import GameTopActions from '@/components/games/GameTopActions.vue'
import { useGameView } from '@/composables/useGameView'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import KlondikeBoard from './cards/KlondikeBoard.vue'
import SpiderBoard from './cards/SpiderBoard.vue'
import './cards/cards.css'

const LOBBY_KEY = 'ihomy:games:cards:mode:v1'
const LOBBY_GAMES = [
  { key: 'klondike', titleKey: 'games.solitaire.title', descKey: 'games.solitaire.desc', icon: Tickets },
  { key: 'spider', titleKey: 'games.spider.title', descKey: 'games.spider.desc', icon: Connection },
]
const BEST_KEYS = {
  klondike: () => {
    try {
      return { value: Number(localStorage.getItem('ihomy:games:solitaire:best:v1')) || 0 }
    } catch (e) {
      return { value: 0 }
    }
  },
  spider: () => {
    try {
      const raw = JSON.parse(localStorage.getItem('ihomy:games:spider:best:v1') || 'null')
      const values = raw && typeof raw === 'object' ? Object.values(raw) : []
      return { value: values.length ? Math.max(...values) : 0 }
    } catch (e) {
      return { value: 0 }
    }
  },
}

const route = useRoute()
const router = useRouter()
const sunLight = inject(SUN_LIGHT_KEY, null)

const pageEl = ref(null)
const stageEl = ref(null)
const stageH = ref(0)
// 牌桌高度由页面自己按一屏算(fit:false),这里只用全屏 / 宽屏状态
const { isFullscreen, isWide, toggleFullscreen, toggleWide } = useGameView({ page: pageEl, stage: stageEl, fit: false })
provide('gameFullscreen', isFullscreen)
const recent = ref('')
// 暖居主区(.gc-main)有确定高度,直接用 CSS 撑满,不依赖首帧测量
const fillByLayout = ref(false)
let retryTimer = null

const currentGame = computed(() => (LOBBY_GAMES.some((g) => g.key === route.params.game) ? route.params.game : null))
const backTarget = computed(() => (currentGame.value ? '/games/cards' : '/games'))
const backLabelKey = computed(() => (currentGame.value ? 'games.cards.lobby' : 'games.back'))

const bestOf = (key) => BEST_KEYS[key]?.().value || 0
const loadRecent = () => {
  try {
    const value = localStorage.getItem(LOBBY_KEY)
    recent.value = LOBBY_GAMES.some((g) => g.key === value) ? value : ''
  } catch (e) {
    recent.value = ''
  }
}

const stageStyle = computed(() => {
  if (fillByLayout.value) return {}
  const vh = window.visualViewport?.height || window.innerHeight
  return { height: `${stageH.value || Math.round(vh * 0.7)}px` }
})

// ---------- 固定高度(仅牌桌视图需要) ----------
const measure = () => {
  const el = stageEl.value
  if (!el || document.fullscreenElement) return
  const scroller = el.closest('.gc-main')
  const pageEl = el.closest('.page')
  const pagePad = pageEl ? parseFloat(getComputedStyle(pageEl).paddingBottom) || 0 : 0
  const vh = window.visualViewport?.height || window.innerHeight
  if (scroller) {
    // 暖居:主区有确定高度,交给 .cards-page{height:100%} + flex 撑满,不做数值测量
    const changed = !fillByLayout.value
    fillByLayout.value = true
    if (changed) nextTick(() => window.dispatchEvent(new Event('resize')))
    return
  }
  fillByLayout.value = false
  const top = el.getBoundingClientRect().top + window.scrollY
  const avail = vh - top - pagePad - 8
  const next = Math.max(320, Math.min(Math.round(avail), 920))
  if (next === stageH.value) return
  stageH.value = next
  // 高度变化后通知牌桌重新量一次(不依赖 ResizeObserver:后台标签页会暂停回调)
  nextTick(() => window.dispatchEvent(new Event('resize')))
}

// 首帧外壳可能还没完成布局,按递增间隔补量几次,保证最终高度收敛
const measureWithRetry = (attempt = 0) => {
  measure()
  if (attempt >= 7) return
  retryTimer = setTimeout(() => measureWithRetry(attempt + 1), 120 + attempt * 120)
}

let resizeObserver = null
const observeLayout = () => {
  const scroller = stageEl.value?.closest('.gc-main') || document.querySelector('.gc-main')
  if (typeof ResizeObserver === 'undefined') return
  resizeObserver = new ResizeObserver(() => measure())
  resizeObserver.observe(scroller || document.documentElement)
}

// ---------- 全屏 / 宽屏后的牌桌重算 ----------
const onFullscreenChange = () => {
  nextTick(() => {
    measure()
    window.dispatchEvent(new Event('resize'))
  })
}

// 宽屏会隐藏侧栏、放开内容宽度,窗口本身不变,不会触发 resize,这里主动通知牌桌重新反推牌宽
watch(isWide, () => {
  nextTick(() => {
    measure()
    window.dispatchEvent(new Event('resize'))
  })
})

const onResize = () => measure()

// 进出玩法时:牌桌视图要重新量高度,大厅要刷新最高分与最近标记
watch(currentGame, (value) => {
  if (value) {
    try {
      localStorage.setItem(LOBBY_KEY, value)
    } catch (e) {
      // 隐私模式忽略
    }
    nextTick(() => measureWithRetry())
  } else {
    fillByLayout.value = false
    stageH.value = 0
    loadRecent()
  }
})

onMounted(() => {
  sunLight?.suspendEffects()
  document.addEventListener('fullscreenchange', onFullscreenChange)
  window.addEventListener('resize', onResize)
  observeLayout()
  loadRecent()
  if (currentGame.value) nextTick(() => measureWithRetry())
})

onBeforeUnmount(() => {
  clearTimeout(retryTimer)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  window.removeEventListener('resize', onResize)
  resizeObserver?.disconnect()
  resizeObserver = null
  sunLight?.restoreEffects()
})
</script>

<style scoped>
.cards-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.cards-page {
  display: flex;
  flex-direction: column;
  min-height: 0;
}
html.theme-warm .cards-page {
  height: 100%;
}
.cards-stage {
  display: flex;
  flex: 1 1 auto;
  width: 100%;
  min-height: 0;
}
.cards-stage:fullscreen {
  width: 100vw;
  height: 100vh !important;
  padding: 8px;
  box-sizing: border-box;
  background: #14382a;
}

/* 玩法大厅 */
.cards-lobby {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 26px;
  padding: 18px 6px;
}
.cards-lobby-head {
  text-align: center;
}
.cards-lobby-title {
  font-size: 26px;
  font-weight: 800;
  letter-spacing: 2px;
}
.cards-lobby-sub {
  margin-top: 6px;
  font-size: 13px;
  color: var(--color-text-secondary, #8a8a8a);
  letter-spacing: 1px;
}
.cards-lobby-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(230px, 260px));
  justify-content: center;
  gap: 18px;
  width: 100%;
  max-width: 720px;
}
.cards-lobby-card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  padding: 20px 18px 16px;
  border-radius: 16px;
  text-align: left;
  cursor: pointer;
  transition: transform 0.2s ease;
  contain: layout style;
}
.cards-lobby-card:hover {
  transform: translateY(-3px);
}
.cards-lobby-icon {
  width: 54px;
  height: 54px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(var(--color-brand-rgb), 0.14);
  color: var(--color-accent, var(--color-brand));
}
.cards-lobby-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 17px;
  font-weight: 700;
}
.cards-lobby-recent {
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  background: rgba(var(--color-brand-rgb), 0.14);
  color: var(--color-accent, var(--color-brand));
}
.cards-lobby-desc {
  font-size: 13px;
  line-height: 1.5;
  color: var(--color-text-secondary, #8a8a8a);
}
.cards-lobby-best {
  font-size: 12px;
  color: var(--color-text-secondary, #8a8a8a);
  font-variant-numeric: tabular-nums;
}
.cards-lobby-enter {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 2px;
  font-size: 13px;
  color: var(--color-accent, var(--color-brand));
}
@media (max-height: 620px) {
  .cards-lobby {
    gap: 14px;
  }
  .cards-lobby-title {
    font-size: 21px;
  }
  .cards-lobby-card {
    padding: 14px 16px 12px;
  }
}
</style>
