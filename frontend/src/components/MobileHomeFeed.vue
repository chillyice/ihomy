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
          @click="activeFilter = f.key"
        >{{ f.label }}</span>
      </div>
    </div>

    <!-- 动态流 -->
    <div class="feed-list" v-loading="loading">
      <template v-if="filteredFeeds.length">
        <div v-for="(f, i) in filteredFeeds" :key="i" class="feed-card" @click="goFeed(f)">
          <div class="feed-card-head">
            <el-avatar :size="32" :src="f.authorAvatar">{{ (f.authorName || 'U').charAt(0) }}</el-avatar>
            <span class="feed-author">{{ f.authorName || $t('feed.authorFallback') }}</span>
            <span class="feed-type-tag">{{ feedTypeLabel(f.type) }}</span>
            <span class="feed-time">{{ formatTime(f.createdAt) }}</span>
          </div>
          <div class="feed-card-body">
            <div v-if="f.coverUrl" class="feed-cover">
              <img :src="f.coverUrl" :alt="feedSummary(f)" loading="lazy" />
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
import { useUserStore } from '@/stores/user'
import { publicApi, homeApi } from '@/api'

const router = useRouter()
const userStore = useUserStore()
const feeds = ref([])
const loading = ref(true)
const activeFilter = ref('all')

const filters = [
  { key: 'all', label: '全部' },
  { key: 'blog', label: '博客' },
  { key: 'diary', label: '日记' },
  { key: 'photo', label: '照片' },
]

const filteredFeeds = computed(() => {
  if (activeFilter.value === 'all') return feeds.value
  return feeds.value.filter(f => f.type === activeFilter.value)
})

const feedTypeLabel = (type) => type === 'blog' ? '博客' : type === 'diary' ? '日记' : type === 'photo' ? '照片' : ''
const feedSummary = (f) => {
  if (f.type === 'blog') return f.title || ''
  if (f.type === 'diary') return (f.content || '').slice(0, 60)
  if (f.type === 'photo') return `${f.count || 0} 张照片`
  return ''
}
const formatTime = (d) => {
  if (!d) return ''
  const date = new Date(d)
  const diff = (Date.now() - date.getTime()) / 1000
  if (diff < 3600) return Math.floor(diff / 60) + ' 分钟前'
  if (diff < 86400) return Math.floor(diff / 3600) + ' 小时前'
  return date.toLocaleDateString('zh-CN')
}
const goFeed = (f) => {
  if (f.type === 'blog' && f.id) router.push(`/blog/${f.id}`)
  else if (f.type === 'diary') router.push('/diary')
  else if (f.type === 'photo') router.push('/album')
}

onMounted(async () => {
  try {
    const fn = userStore.isLoggedIn ? homeApi.getFeed : publicApi.getFeed
    feeds.value = await fn(20)
  } catch (e) { feeds.value = [] } finally { loading.value = false }
})
</script>

<style scoped>
.mobile-home { min-height: 100%; }

.filter-bar {
  position: sticky;
  top: 0;
  z-index: 10;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-bottom: 1px solid rgba(0, 0, 0, 0.04);
}
html.dark .filter-bar { background: rgba(20, 28, 45, 0.92); border-bottom-color: rgba(255,255,255,0.06); }

.filter-scroll {
  display: flex;
  gap: 8px;
  padding: 10px 16px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}
.filter-scroll::-webkit-scrollbar { display: none; }

.filter-chip {
  flex-shrink: 0;
  padding: 6px 16px;
  border-radius: 20px;
  font-size: 14px;
  color: var(--color-text-secondary, #666);
  background: rgba(0, 0, 0, 0.04);
  cursor: pointer;
  transition: all 0.2s;
  -webkit-tap-highlight-color: transparent;
}
html.dark .filter-chip { background: rgba(255,255,255,0.08); color: #aaa; }
.filter-chip.active {
  background: var(--color-primary, #b88c6e);
  color: #fff;
}
html.dark .filter-chip.active { background: #d4b298; color: #1a1a1a; }

.feed-list { padding: 12px 16px 80px; }

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
.feed-card:active { transform: scale(0.98); }
html.dark .feed-card { background: rgba(255,255,255,0.06); border-color: rgba(255,255,255,0.06); }

.feed-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.feed-author { font-size: 14px; font-weight: 500; color: var(--color-text-primary, #333); }
html.dark .feed-author { color: #E8DCC8; }
.feed-type-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 6px;
  background: rgba(184, 140, 110, 0.1);
  color: var(--color-primary, #b88c6e);
}
html.dark .feed-type-tag { background: rgba(212,178,152,0.15); color: #d4b298; }
.feed-time { margin-left: auto; font-size: 12px; color: var(--color-text-secondary, #999); }

.feed-card-body { display: flex; gap: 12px; }
.feed-cover { flex-shrink: 0; width: 80px; height: 80px; border-radius: 10px; overflow: hidden; }
.feed-cover img { width: 100%; height: 100%; object-fit: cover; }
.feed-text { flex: 1; min-width: 0; }
.feed-title { font-size: 15px; font-weight: 600; margin-bottom: 4px; color: var(--color-text-primary, #333); display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
html.dark .feed-title { color: #E8DCC8; }
.feed-summary { font-size: 13px; color: var(--color-text-secondary, #666); display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
html.dark .feed-summary { color: #aaa; }

.feed-empty { text-align: center; padding: 60px 0; color: var(--color-text-secondary, #999); font-size: 14px; }
</style>
