<!-- 家人动态流:便条散落餐桌,每条轻微旋转,hover 抬起,无循环动画 -->
<template>
  <div ref="root" class="activity-feed">
    <div class="feed-header">
      <h2 class="feed-title">{{ $t('feed.title') }}</h2>
      <span class="feed-sub">{{ $t('feed.subtitle') }}</span>
    </div>

    <div v-if="loading" class="feed-loading">
      <el-skeleton :rows="4" animated />
    </div>

    <div v-else-if="!groups.length" class="feed-empty">
      <el-empty :description="emptyText" />
    </div>

    <div v-else class="feed-list">
      <div v-for="g in groups" :key="g.label" class="feed-group">
        <div class="group-label">{{ g.label }}</div>
        <div class="group-items">
          <div v-for="(item, idx) in g.items" :key="idx" class="feed-item anim-item" :style="{ '--rot': noteRot(idx) + 'deg', '--bg': noteBg(item.type, idx) }">
            <div class="item-card">
              <div class="item-header">
                <el-avatar :size="28" :src="item.authorAvatar">
                  {{ (item.authorName || 'U').charAt(0) }}
                </el-avatar>
                <span class="item-author">{{ item.authorName || $t('feed.authorFallback') }}</span>
                <span class="item-action">{{ actionText(item) }}</span>
                <span class="item-time">{{ formatTime(item.createdAt) }}</span>
              </div>

              <div class="item-body">
                <div v-if="item.type === 'blog'" class="blog-preview" @click="goBlog(item.id)">
                  <img v-if="item.coverImage" :src="item.coverImage" class="blog-cover" />
                  <div class="blog-text">
                    <div class="blog-title">{{ item.title }}</div>
                    <div class="blog-summary">{{ item.summary }}</div>
                    <div v-if="item.tags" class="blog-tags">
                      <span v-for="tt in item.tags.split(',').slice(0,3)" :key="tt" class="tag">#{{ tt.trim() }}</span>
                    </div>
                  </div>
                </div>

                <div v-else-if="item.type === 'diary'" class="diary-preview">
                  <div class="diary-content">{{ item.content }}</div>
                  <div v-if="item.mood || item.weather" class="diary-meta">
                    <span v-if="item.mood">{{ $t('feed.mood') }}: {{ item.mood }}</span>
                    <span v-if="item.weather"> · {{ $t('feed.weather') }}: {{ item.weather }}</span>
                  </div>
                </div>

                <div v-else-if="item.type === 'photo'" class="photo-preview">
                  <div class="photo-grid">
                    <div
                      v-for="(url, i) in item.urls"
                      :key="i"
                      class="photo-thumb"
                      :style="{ backgroundImage: `url(${url})` }"
                    ></div>
                  </div>
                  <div class="photo-count">{{ $t('feed.photoCount', { n: item.count }) }}</div>
                </div>
              </div>

              <div class="item-stats" v-if="(item.likeCount || 0) > 0 || (item.commentCount || 0) > 0">
                <span v-if="(item.commentCount || 0) > 0" class="stat-chip">💬 {{ item.commentCount }}</span>
                <span v-if="(item.likeCount || 0) > 0" class="stat-chip">👍 {{ item.likeCount }}</span>
                <span v-if="item.type === 'blog' && (item.viewCount || 0) > 0" class="stat-chip">👀 {{ item.viewCount }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { publicApi, homeApi } from '@/api'
import { gsap } from 'gsap'

const props = defineProps({
  homeId: { type: [String, Number], default: '' },
  hid: { type: String, default: '' },
})

const router = useRouter()
const userStore = useUserStore()
const { t, locale } = useI18n()

const root = ref(null)
const items = ref([])
const loading = ref(false)
let ctx

const emit = defineEmits(['loaded'])

const emptyText = computed(() =>
  userStore.isGuest ? t('feed.emptyGuest') : t('feed.emptyMember'),
)

const actionText = (item) => {
  if (item.type === 'blog') return t('feed.actionBlog')
  if (item.type === 'diary') return t('feed.actionDiary')
  if (item.type === 'photo') return t('feed.actionPhoto')
  return t('feed.actionDefault')
}

const groups = computed(() => {
  if (!items.value.length) return []
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const yesterday = new Date(today)
  yesterday.setDate(yesterday.getDate() - 1)
  const weekAgo = new Date(today)
  weekAgo.setDate(weekAgo.getDate() - 7)

  const groupMap = new Map()
  for (const it of items.value) {
    const d = new Date(it.createdAt)
    let label
    if (d >= today) label = t('feed.today')
    else if (d >= yesterday) label = t('feed.yesterday')
    else if (d >= weekAgo) label = t('feed.thisWeek')
    else label = t('feed.earlier')
    if (!groupMap.has(label)) groupMap.set(label, [])
    groupMap.get(label).push(it)
  }
  const order = [t('feed.today'), t('feed.yesterday'), t('feed.thisWeek'), t('feed.earlier')]
  return order.filter(l => groupMap.has(l)).map(l => ({ label: l, items: groupMap.get(l) }))
})

const formatTime = (d) => {
  if (!d) return ''
  const date = new Date(d)
  const now = new Date()
  const diff = (now - date) / 1000
  if (diff < 60) return t('time.justNow')
  if (diff < 3600) return t('time.minuteAgo', { n: Math.floor(diff / 60) })
  if (diff < 86400) return t('time.hourAgo', { n: Math.floor(diff / 3600) })
  if (diff < 86400 * 7) return t('time.dayAgo', { n: Math.floor(diff / 86400) })
  return date.toLocaleDateString(locale.value === 'en' ? 'en-US' : 'zh-CN')
}

const goBlog = (id) => router.push(`/blog/${id}`)

// 便条旋转:按 idx 交替正负,幅度递增(像随手放,每张歪一点)
const noteRot = (idx) => (idx % 2 === 0 ? -1 : 1) * (0.8 + (idx % 4) * 0.4)

// 便条配色:三种便签纸色循环
const NOTE_COLORS = ['var(--color-sticky)', 'var(--color-sticky-2)', 'var(--color-sticky-3)']
const noteBg = (type, idx) => NOTE_COLORS[idx % NOTE_COLORS.length]

// 唯一动效:便条从上落下+轻微旋转,无循环
const playEntrance = () => {
  if (ctx) ctx.revert()
  if (!root.value) return
  ctx = gsap.context(() => {
    gsap.from('.anim-item', {
      y: -24,
      autoAlpha: 0,
      rotation: 0,
      duration: 0.5,
      ease: 'power2.out',
      stagger: 0.06,
    })
  }, root.value)
}

const load = async () => {
  loading.value = true
  try {
    items.value = props.hid
      ? await publicApi.getFeed(15, undefined, props.hid)
      : props.homeId
        ? await publicApi.getFeed(15, props.homeId)
        : userStore.isGuest
          ? await publicApi.getFeed(15)
          : await homeApi.getFeed(20)
    const today = new Date()
    today.setHours(0, 0, 0, 0)
    const todayCount = items.value.filter(it => new Date(it.createdAt) >= today).length
    emit('loaded', todayCount)
  } catch (e) {
    items.value = []
    emit('loaded', 0)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await load()
  nextTick(playEntrance)
})

onUnmounted(() => ctx?.revert())
watch(groups, () => nextTick(playEntrance))
</script>

<style scoped>
.activity-feed {
  background: transparent;
  padding: 0;
}
.feed-header { margin-bottom: 20px; }
.feed-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text);
  display: inline-block;
  margin-right: 12px;
}
.feed-sub { font-size: 13px; color: var(--color-text-secondary); }

.feed-group { margin-bottom: 24px; }
.group-label {
  font-size: 13px;
  color: var(--color-text-secondary);
  font-weight: 600;
  margin-bottom: 14px;
  padding-left: 4px;
  position: relative;
}
.group-label::before {
  content: '';
  display: inline-block;
  width: 4px;
  height: 14px;
  background: var(--color-accent);
  border-radius: 2px;
  margin-right: 8px;
  vertical-align: middle;
}

.feed-item { margin-bottom: 14px; }
/* 便条:便签纸色,轻微旋转,纸张阴影 */
.item-card {
  background: var(--bg, var(--color-sticky));
  border-radius: 3px;
  padding: 14px 16px;
  transform: rotate(var(--rot, 0deg));
  transition: transform 0.25s cubic-bezier(.34,1.56,.64,1), box-shadow 0.2s;
  box-shadow: var(--shadow-paper);
  cursor: default;
}
.item-card:hover {
  transform: rotate(0deg) translateY(-3px);
  box-shadow: var(--shadow-lift);
}

.item-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 14px;
}
.item-author { font-weight: 600; color: #5C4332; }
.item-action { color: #8B6F47; }
.item-time { margin-left: auto; font-size: 12px; color: #8B6F47; }

.blog-preview {
  display: flex;
  gap: 12px;
  cursor: pointer;
}
.blog-cover {
  width: 100px;
  height: 70px;
  object-fit: cover;
  border-radius: 4px;
  flex-shrink: 0;
  box-shadow: var(--shadow-paper);
}
.blog-text { flex: 1; min-width: 0; }
.blog-title {
  font-size: 15px;
  font-weight: 600;
  color: #3D2E20;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.blog-summary {
  font-size: 13px;
  color: #5C4332;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.blog-tags { margin-top: 6px; }
.tag {
  font-size: 11px;
  color: var(--color-pen);
  margin-right: 6px;
}

.diary-content {
  font-size: 14px;
  color: #3D2E20;
  line-height: 1.6;
  white-space: pre-wrap;
}
.diary-meta {
  margin-top: 6px;
  font-size: 12px;
  color: #8B6F47;
}

.photo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(80px, 1fr));
  gap: 6px;
  margin-bottom: 6px;
}
.photo-thumb {
  aspect-ratio: 1;
  background-size: cover;
  background-position: center;
  border-radius: 4px;
  box-shadow: var(--shadow-paper);
}
.photo-count { font-size: 12px; color: #8B6F47; }

.item-stats {
  display: flex;
  gap: 12px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed rgba(61, 46, 32, 0.15);
}
.stat-chip {
  font-size: 12px;
  color: #8B6F47;
}

.feed-empty, .feed-loading { padding: 24px; }

@media (max-width: 768px) {
  .feed-title { font-size: 17px; }
  .item-card { padding: 10px 12px; }
  .blog-cover { width: 80px; height: 56px; }
  .blog-title { font-size: 14px; }
  .photo-grid { grid-template-columns: repeat(auto-fill, minmax(60px, 1fr)); }
}
</style>
