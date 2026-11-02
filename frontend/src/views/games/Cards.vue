<template>
  <div class="page cards-page">
    <PageToolbar root-class="game-topbar" :holder-margin="0" always>
      <div class="cards-actions">
        <el-button size="small" round @click="router.push('/games')">
          <el-icon><ArrowLeft /></el-icon>
          {{ $t('games.back') }}
        </el-button>
        <div class="cards-mode" role="tablist">
          <button
            v-for="m in MODES"
            :key="m.value"
            type="button"
            role="tab"
            class="cards-mode-btn"
            :class="{ active: mode === m.value }"
            :aria-selected="mode === m.value"
            @click="setMode(m.value)"
          >
            {{ $t(m.labelKey) }}
          </button>
        </div>
        <el-button size="small" round @click="toggleFullscreen">
          <el-icon><FullScreen /></el-icon>
          {{ isFullscreen ? $t('games.exitFullscreen') : $t('games.fullscreen') }}
        </el-button>
      </div>
    </PageToolbar>

    <!-- 固定一屏:暖居下按主区高度 100% 撑满(CSS 链),光尘下按实测视口高度锁高 -->
    <div ref="stageEl" class="cards-stage" :style="stageStyle">
      <KlondikeBoard v-if="mode === 'klondike'" />
      <SpiderBoard v-else />
    </div>
  </div>
</template>

<script setup>
// 纸牌统一入口:接龙与蜘蛛纸牌共用一个页面与一个入口,顶部切换玩法。
// 页面高度锁在一屏内(按所在滚动容器实测),牌桌组件再用 cardLayout 把牌宽与叠牌步进收进这个高度。
import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, FullScreen } from '@element-plus/icons-vue'
import PageToolbar from '@/components/PageToolbar.vue'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import KlondikeBoard from './cards/KlondikeBoard.vue'
import SpiderBoard from './cards/SpiderBoard.vue'
import './cards/cards.css'

const MODE_KEY = 'ihomy:games:cards:mode:v1'
const MODES = [
  { value: 'klondike', labelKey: 'games.solitaire.title' },
  { value: 'spider', labelKey: 'games.spider.title' },
]

const router = useRouter()
const route = useRoute()
const sunLight = inject(SUN_LIGHT_KEY, null)

const stageEl = ref(null)
const stageH = ref(0)
const isFullscreen = ref(false)
// 暖居主区(.gc-main)有确定高度,直接用 CSS 撑满,不依赖首帧测量
const fillByLayout = ref(false)
let retryTimer = null

const stageStyle = computed(() => {
  if (fillByLayout.value) return {}
  const vh = window.visualViewport?.height || window.innerHeight
  return { height: `${stageH.value || Math.round(vh * 0.7)}px` }
})

const initialMode = () => {
  const query = route.query.mode
  if (query === 'spider' || query === 'klondike') return query
  try {
    return localStorage.getItem(MODE_KEY) === 'spider' ? 'spider' : 'klondike'
  } catch (e) {
    return 'klondike'
  }
}
const mode = ref(initialMode())

watch(mode, (value) => {
  try {
    localStorage.setItem(MODE_KEY, value)
  } catch (e) {
    // 隐私模式忽略
  }
})

const setMode = (value) => {
  mode.value = value
}

// ---------- 固定高度 ----------
const measure = () => {
  const el = stageEl.value
  if (!el || document.fullscreenElement) return
  const scroller = el.closest('.gc-main')
  const pageEl = el.closest('.page')
  const pagePad = pageEl ? parseFloat(getComputedStyle(pageEl).paddingBottom) || 0 : 0
  const vh = window.visualViewport?.height || window.innerHeight
  let avail
  if (scroller) {
    // 暖居:主区有确定高度,交给 .cards-page{height:100%} + flex 撑满,不做数值测量
    const changed = !fillByLayout.value
    fillByLayout.value = true
    if (changed) nextTick(() => window.dispatchEvent(new Event('resize')))
    return
  }
  fillByLayout.value = false
  const top = el.getBoundingClientRect().top + window.scrollY
  avail = vh - top - pagePad - 8
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
  const scroller = stageEl.value?.closest('.gc-main')
  if (typeof ResizeObserver === 'undefined') return
  resizeObserver = new ResizeObserver(() => measure())
  resizeObserver.observe(scroller || document.documentElement)
}

// ---------- 全屏 ----------
const toggleFullscreen = async () => {
  try {
    if (document.fullscreenElement) await document.exitFullscreen?.()
    else await stageEl.value?.requestFullscreen?.()
  } catch (e) {
    // 浏览器拒绝(iOS)时保持窗口态
  }
}
const onFullscreenChange = () => {
  isFullscreen.value = !!document.fullscreenElement
  nextTick(() => {
    measure()
    window.dispatchEvent(new Event('resize'))
  })
}

const onResize = () => measure()

onMounted(() => {
  sunLight?.suspendEffects()
  document.addEventListener('fullscreenchange', onFullscreenChange)
  window.addEventListener('resize', onResize)
  observeLayout()
  nextTick(() => {
    measureWithRetry()
  })
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
.cards-mode {
  display: flex;
  gap: 2px;
  margin: 0 auto;
  padding: 2px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.06);
}
.cards-mode-btn {
  border: none;
  background: transparent;
  color: var(--color-text-secondary, #8a8a8a);
  font-size: 13px;
  line-height: 1.4;
  padding: 4px 14px;
  border-radius: 999px;
  cursor: pointer;
  white-space: nowrap;
  transition:
    background-color 0.16s ease,
    color 0.16s ease;
}
.cards-mode-btn:hover {
  color: var(--color-accent, var(--color-brand));
}
.cards-mode-btn.active {
  background: var(--color-brand);
  color: var(--color-brand-text, #fff);
  font-weight: 600;
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
html.dark .cards-mode {
  background: rgba(255, 255, 255, 0.1);
}
@media (max-width: 560px) {
  .cards-mode-btn {
    padding: 4px 9px;
    font-size: 12px;
  }
}
</style>
