<template>
  <div ref="root" class="mobile-home" :class="{ 'warm-feed': isWarm }">
    <!-- 分享落地:被分享家庭的封面门脸(自己家不挂) -->
    <div v-if="showDoorSign" class="m-door-sign">
      <FamilyCoverBanner :family="appStore.family" />
    </div>
    <h2 v-if="isWarm" class="feed-heading">{{ $t('home.familyFeed') }}</h2>
    <!-- 顶部筛选栏 -->
    <div class="filter-bar">
      <div v-horizontal-wheel class="filter-scroll">
        <button
          v-for="f in filters"
          :key="f.key"
          class="filter-chip"
          :class="{ active: activeFilter === f.key }"
          type="button"
          :aria-pressed="activeFilter === f.key"
          @click="activeFilter = f.key"
        >
          {{ f.label }}
        </button>
      </div>
    </div>

    <!-- 动态流:切换筛选项时整组淡出再淡入,避免列表内容硬切 -->
    <div class="feed-list">
      <div v-if="loading && !feeds.length" v-loading="true" class="feed-loading" />
      <div v-if="loadError" role="status" class="feed-retry">
        {{ $t('common.loadFailed') }}
        <el-button text :disabled="loading" @click="load().catch(() => {})">{{ $t('common.retry') }}</el-button>
      </div>
      <Transition name="feed-swap" mode="out-in">
        <div v-if="filteredFeeds.length" :key="activeFilter" class="feed-list-inner">
          <div
            v-for="(f, i) in filteredFeeds"
            :key="f.type + '-' + (f.id ?? i)"
            class="feed-card anim-rise"
            :data-paper="isWarm ? cardPaper(f.type + '-' + (f.id ?? f.createdAt)) : undefined"
            :style="{ '--i': Math.min(i, 12) }"
            v-a11y-click
            @click="goFeed(f)"
          >
            <div class="feed-card-head">
              <el-avatar :size="32" :src="f.authorAvatar">{{ (f.authorName || 'U').charAt(0) }}</el-avatar>
              <span class="feed-author">{{ f.authorName || $t('feed.authorFallback') }}</span>
              <span class="feed-type-tag">{{ feedTypeLabel(f.type) }}</span>
              <span class="feed-time">{{ formatTime(f.createdAt) }}</span>
            </div>
            <div class="feed-card-body" :class="{ 'photo-story': f.type === 'photo' }">
              <div
                v-if="themeStore.theme === 'warm' && f.type === 'photo' && Array.isArray(f.urls) && f.urls.length > 1"
                v-horizontal-wheel
                class="feed-cover feed-photo-strip"
              >
                <img
                  v-for="(url, index) in f.urls"
                  :key="index"
                  :src="url"
                  :alt="f.descriptions?.[index] || $t('feed.type.photo')"
                  loading="lazy"
                />
              </div>
              <div v-else-if="feedCover(f)" class="feed-cover">
                <img :src="feedCover(f)" :alt="feedSummary(f)" loading="lazy" />
              </div>
              <div class="feed-text">
                <div v-if="f.type === 'blog'" class="feed-title">{{ f.title }}</div>
                <div v-if="f.type !== 'blog' || (f.summary && f.summary !== f.title)" class="feed-summary">
                  {{ f.type === 'blog' ? f.summary : feedSummary(f) }}
                </div>
              </div>
            </div>
          </div>
        </div>
        <div v-else-if="!loading && !loadError" key="empty" class="feed-empty">{{ $t('common.empty') }}</div>
      </Transition>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { useThemeStore } from '@/stores/theme'
import FamilyCoverBanner from '@/components/FamilyCoverBanner.vue'
import { useHomeFeed } from '@/composables/useHomeFeed'
import { feedPath, feedTypeLabel as feedTypeLabelOf, feedSummary as feedSummaryOf } from '@/utils/feed'
import { formatRelativeTime } from '@/utils/datetime'
import { showCoverBanner } from '@/utils/shareLanding'
import { usePullRefresh } from '@/composables/usePullRefresh'
import { bindHorizontalWheel } from '@/utils/horizontalWheel'
import { cardPaper } from '@/utils/warmMaterials'

const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()
const themeStore = useThemeStore()
const isWarm = computed(() => themeStore.theme === 'warm')
const vHorizontalWheel = {
  mounted(el) {
    el.unbindWheel = bindHorizontalWheel(el)
  },
  unmounted(el) {
    el.unbindWheel()
  },
}
const { t } = useI18n()
const root = ref(null)
const feeds = ref([])
const loading = ref(true)
const loadError = ref(false)
const { scope, fetchFeed } = useHomeFeed()
let latestRequest = 0
const activeFilter = ref('all')
// 分享落地门脸:别人家有封面才挂横幅,自己家不挂(判定见 utils/shareLanding)
const showDoorSign = computed(() => showCoverBanner(appStore.family, userStore.userInfo?.familyId))

const FILTER_KEYS = ['all', 'blog', 'diary', 'photo', 'video', 'wish', 'task', 'recipe', 'book']
const filters = computed(() => FILTER_KEYS.map((k) => ({ key: k, label: k === 'all' ? t('feed.all') : t('feed.type.' + k) })))

const filteredFeeds = computed(() => {
  if (activeFilter.value === 'all') return feeds.value
  return feeds.value.filter((f) => f.type === activeFilter.value)
})

// 类型标签/摘要走共享 utils/feed.js;相对时间走 utils/datetime.js(与首页、暖居首页同口径;移动端历史摘要截断 60)
const feedTypeLabel = (type) => feedTypeLabelOf(t, type)
const feedCover = (f) => f.coverImage || (Array.isArray(f.urls) && f.urls.length ? f.urls[0] : '')
const feedSummary = (f) => feedSummaryOf(t, f, 60)
const formatTime = (d) => formatRelativeTime(t, d)
const goFeed = (f) => {
  const path = feedPath(f)
  if (path) router.push(path)
}

const load = async () => {
  const request = ++latestRequest
  loading.value = true
  try {
    const data = await fetchFeed()
    if (request !== latestRequest) return
    feeds.value = data || []
    loadError.value = false
  } catch (e) {
    if (request !== latestRequest) return
    loadError.value = true
    throw e
  } finally {
    if (request === latestRequest) loading.value = false
  }
}

// 手机首页是真正的「首页 Tab」:下拉刷新重拉动态流,失败由指示器统一提示
usePullRefresh({ target: root, onRefresh: load })

watch(
  scope,
  () => {
    feeds.value = []
    loadError.value = false
    load().catch(() => {})
  },
  { immediate: true },
)
</script>

<style scoped>
.mobile-home {
  min-height: 100%;
}

/* 分享门脸:与下方动态列表同一条 16px 边距线 */
.m-door-sign {
  padding: 12px 16px 0;
}

.filter-bar {
  position: sticky;
  top: 0;
  z-index: 10;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-bottom: 1px solid rgba(0, 0, 0, 0.04);
}
html.dark .filter-bar {
  background: rgba(var(--color-card-rgb), 0.92);
  border-bottom-color: rgba(255, 255, 255, 0.06);
}

.filter-scroll {
  display: flex;
  gap: 22px;
  padding: 12px 20px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}
.filter-scroll::-webkit-scrollbar {
  display: none;
}

.filter-chip {
  border: 0;
  font: inherit;
  flex-shrink: 0;
  position: relative;
  padding: 4px 0;
  font-size: 15px;
  color: var(--color-text-secondary, #8a8378);
  background: transparent;
  cursor: pointer;
  white-space: nowrap;
  transition:
    color 0.2s,
    transform 0.16s var(--ease-soft);
  -webkit-tap-highlight-color: transparent;
}
.filter-chip:active {
  transform: scale(0.95);
}
html.dark .filter-chip {
  color: #9aa0a8;
}

/* 选中态下划线:从中心展开 */
.filter-chip::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: -2px;
  transform: translateX(-50%);
  width: 0;
  height: 2.5px;
  border-radius: 2px;
  background: var(--color-primary, var(--color-brand));
  transition: width 0.25s ease;
}
.filter-chip.active {
  color: var(--color-text-primary, #3a2e22);
  font-weight: 600;
}
.filter-chip.active::after {
  width: 60%;
}
html.dark .filter-chip.active {
  color: #e8dcc8;
}
html.dark .filter-chip::after {
  background: var(--color-brand);
}

.feed-list {
  padding: 12px 16px 80px;
}
.warm-feed {
  display: flow-root;
  padding-top: var(--safe-area-inset-top);
}
.feed-heading {
  margin: 18px 16px 8px;
  font-size: 17px;
  line-height: 1.4;
  color: var(--color-text);
}
.warm-feed .filter-bar {
  top: var(--safe-area-inset-top);
}
.warm-feed .filter-scroll {
  gap: 8px;
  padding: 0 16px;
  mask-image: linear-gradient(to right, transparent, black 8px, black calc(100% - 8px), transparent);
}
.warm-feed .filter-chip {
  min-width: 44px;
  min-height: 44px;
}
.warm-feed .filter-chip::after {
  bottom: 4px;
}
.warm-feed .filter-chip:focus-visible {
  outline: 2px solid var(--color-brand);
  outline-offset: -3px;
}
.warm-feed .feed-list {
  padding-bottom: 12px;
}
.feed-retry {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  color: var(--color-text-secondary);
}
.feed-loading {
  min-height: 56px;
}
.photo-story {
  flex-direction: column;
}
.photo-story .feed-cover {
  width: 100%;
  height: auto;
  aspect-ratio: 16 / 10;
}

/* 筛选切换:整组先淡出再淡入,不做逐卡删除/插入,避免列表抖动 */
.feed-swap-enter-active {
  transition:
    opacity 0.2s ease,
    transform 0.24s var(--ease-soft);
}
.feed-swap-leave-active {
  transition: opacity 0.14s ease;
}
.feed-swap-enter-from {
  opacity: 0;
  transform: translateY(6px);
}
.feed-swap-leave-to {
  opacity: 0;
}

.feed-card {
  background: rgba(255, 255, 255, 0.6);
  border-radius: 14px;
  padding: 14px;
  margin-bottom: 12px;
  border: 1px solid rgba(0, 0, 0, 0.04);
  cursor: pointer;
  transition:
    transform 0.18s var(--ease-soft),
    box-shadow 0.18s ease;
  -webkit-tap-highlight-color: transparent;
}
.feed-card:active {
  transform: scale(0.98);
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.04);
}
html.dark .feed-card {
  background: rgba(255, 255, 255, 0.06);
  border-color: rgba(255, 255, 255, 0.06);
}

.feed-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.feed-author {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-primary, #333);
}
html.dark .feed-author {
  color: #e8dcc8;
}
.feed-type-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 6px;
  background: rgba(184, 140, 110, 0.1);
  color: var(--color-primary, var(--color-brand));
}
html.dark .feed-type-tag {
  background: rgba(var(--color-brand-rgb), 0.15);
  color: var(--color-brand);
}
.feed-time {
  margin-left: auto;
  font-size: 12px;
  color: var(--color-text-secondary, #999);
}

.feed-card-body {
  display: flex;
  gap: 12px;
}
.feed-cover {
  flex-shrink: 0;
  width: 80px;
  height: 80px;
  border-radius: 10px;
  overflow: hidden;
}
.feed-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.photo-story .feed-photo-strip {
  display: flex;
  gap: 6px;
  overflow-x: auto;
  overscroll-behavior-x: contain;
  scrollbar-width: none;
  mask-image: linear-gradient(to right, transparent, black 4px, black calc(100% - 4px), transparent);
}
.feed-photo-strip img {
  width: 94%;
  flex: none;
  scroll-snap-align: start;
}
@media (pointer: coarse) {
  .feed-photo-strip {
    scroll-snap-type: x mandatory;
  }
}
.feed-text {
  flex: 1;
  min-width: 0;
}
.feed-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 4px;
  color: var(--color-text-primary, #333);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
html.dark .feed-title {
  color: #e8dcc8;
}
.feed-summary {
  font-size: 13px;
  color: var(--color-text-secondary, #666);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
html.dark .feed-summary {
  color: #aaa;
}

.feed-empty {
  text-align: center;
  padding: 60px 0;
  color: var(--color-text-secondary, #999);
  font-size: 14px;
}
</style>
