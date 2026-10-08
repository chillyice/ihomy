<template>
  <div class="mobile-home">
    <!-- 顶部筛选栏 -->
    <div class="filter-bar">
      <div class="filter-scroll">
        <span
          v-for="f in filters"
          :key="f.key"
          class="filter-chip"
          :class="{ active: activeFilter === f.key }"
          v-a11y-click
          @click="activeFilter = f.key"
          >{{ f.label }}</span
        >
      </div>
    </div>

    <!-- 动态流 -->
    <div class="feed-list" v-loading="loading">
      <template v-if="filteredFeeds.length">
        <div v-for="(f, i) in filteredFeeds" :key="i" class="feed-card" v-a11y-click @click="goFeed(f)">
          <div class="feed-card-head">
            <el-avatar :size="32" :src="f.authorAvatar">{{ (f.authorName || 'U').charAt(0) }}</el-avatar>
            <span class="feed-author">{{ f.authorName || $t('feed.authorFallback') }}</span>
            <span class="feed-type-tag">{{ feedTypeLabel(f.type) }}</span>
            <span class="feed-time">{{ formatTime(f.createdAt) }}</span>
          </div>
          <div class="feed-card-body">
            <div v-if="feedCover(f)" class="feed-cover">
              <img :src="feedCover(f)" :alt="feedSummary(f)" loading="lazy" />
            </div>
            <div class="feed-text">
              <div v-if="f.type === 'blog'" class="feed-title">{{ f.title }}</div>
              <div class="feed-summary">{{ feedSummary(f) }}</div>
            </div>
          </div>
        </div>
      </template>
      <div v-else-if="!loading" class="feed-empty">{{ $t('common.empty') }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { publicApi, homeApi } from '@/api'
import { feedTypeLabel as feedTypeLabelOf, feedSummary as feedSummaryOf } from '@/utils/feed'
import { formatRelativeTime } from '@/utils/datetime'

const router = useRouter()
const userStore = useUserStore()
const { t } = useI18n()
const feeds = ref([])
const loading = ref(true)
const activeFilter = ref('all')

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
const FEED_ROUTES = {
  diary: '/diary',
  photo: '/album',
  video: '/cinema',
  wish: '/wish',
  task: '/task',
  recipe: '/kitchen',
  book: '/library',
}
const goFeed = (f) => {
  if (f.type === 'blog' && f.id) router.push(`/blog/${f.id}`)
  else if (FEED_ROUTES[f.type]) router.push(FEED_ROUTES[f.type])
}

onMounted(async () => {
  try {
    const fn = userStore.isLoggedIn ? homeApi.getFeed : publicApi.getFeed
    feeds.value = await fn(20)
  } catch (e) {
    feeds.value = []
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.mobile-home {
  min-height: 100%;
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
  flex-shrink: 0;
  position: relative;
  padding: 4px 0;
  font-size: 15px;
  color: var(--color-text-secondary, #8a8378);
  background: transparent;
  cursor: pointer;
  white-space: nowrap;
  transition: color 0.2s;
  -webkit-tap-highlight-color: transparent;
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

.feed-card {
  background: rgba(255, 255, 255, 0.6);
  border-radius: 14px;
  padding: 14px;
  margin-bottom: 12px;
  border: 1px solid rgba(0, 0, 0, 0.04);
  cursor: pointer;
  transition: transform 0.15s;
  -webkit-tap-highlight-color: transparent;
}
.feed-card:active {
  transform: scale(0.98);
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
