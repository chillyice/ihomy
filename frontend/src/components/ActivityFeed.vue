<!-- 家人动态流:按 今天/昨天/本周/更早 分组展示博客、日记、照片动态,访客仅看公开内容 -->
<template>
  <div class="activity-feed">
    <div class="feed-header">
      <h2 class="feed-title">家人动态</h2>
      <span class="feed-sub">最近的家人们都在做什么</span>
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
          <div v-for="(item, idx) in g.items" :key="idx" class="feed-item">
            <div class="item-line">
              <div class="item-dot" :class="'dot-' + item.type"></div>
              <div class="item-card">
                <div class="item-header">
                  <el-avatar :size="28" :src="item.authorAvatar">
                    {{ (item.authorName || 'U').charAt(0) }}
                  </el-avatar>
                  <span class="item-author">{{ item.authorName || '家人' }}</span>
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
                        <span v-for="t in item.tags.split(',').slice(0,3)" :key="t" class="tag">#{{ t.trim() }}</span>
                      </div>
                    </div>
                  </div>

                  <div v-else-if="item.type === 'diary'" class="diary-preview">
                    <div class="diary-content">{{ item.content }}</div>
                    <div v-if="item.mood || item.weather" class="diary-meta">
                      <span v-if="item.mood">心情: {{ item.mood }}</span>
                      <span v-if="item.weather"> · 天气: {{ item.weather }}</span>
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
                    <div class="photo-count">上传了 {{ item.count }} 张照片</div>
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
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { publicApi, homeApi } from '@/api'

const props = defineProps({
  homeId: { type: [String, Number], default: '' },
  hid: { type: String, default: '' },
})

const router = useRouter()
const userStore = useUserStore()

const items = ref([])
const loading = ref(false)

const emit = defineEmits(['loaded'])

// 访客与登录成员展示不同的空态提示文案
const emptyText = computed(() =>
  userStore.isGuest ? '暂无公开动态' : '家人们还没有发布动态，去写第一篇吧'
)

// 动态动作文案映射
const actionText = (item) => {
  if (item.type === 'blog') return '发布了博客'
  if (item.type === 'diary') return '写了日记'
  if (item.type === 'photo') return '上传了照片'
  return '发布了动态'
}

// 动态按发布时间归入 今天/昨天/本周/更早 四组,固定顺序输出
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
    if (d >= today) label = '今天'
    else if (d >= yesterday) label = '昨天'
    else if (d >= weekAgo) label = '本周'
    else label = '更早'
    if (!groupMap.has(label)) groupMap.set(label, [])
    groupMap.get(label).push(it)
  }
  const order = ['今天', '昨天', '本周', '更早']
  return order.filter(l => groupMap.has(l)).map(l => ({ label: l, items: groupMap.get(l) }))
})

const formatTime = (d) => {
  if (!d) return ''
  const date = new Date(d)
  const now = new Date()
  const diff = (now - date) / 1000
  if (diff < 60) return '刚刚'
  if (diff < 3600) return Math.floor(diff / 60) + ' 分钟前'
  if (diff < 86400) return Math.floor(diff / 3600) + ' 小时前'
  if (diff < 86400 * 7) return Math.floor(diff / 86400) + ' 天前'
  return date.toLocaleDateString('zh-CN')
}

const goBlog = (id) => router.push(`/blog/${id}`)

// 加载动态:按 hid > home_id > 访客公开流 > 登录家庭流 的优先级选择数据源
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

onMounted(load)
</script>

<style scoped>
.activity-feed {
  background: var(--color-card);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 24px;
}
.feed-header { margin-bottom: 20px; }
.feed-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-primary);
  display: inline-block;
  margin-right: 12px;
}
.feed-sub { font-size: 13px; color: var(--color-text-secondary); }

.feed-group { margin-bottom: 24px; }
.group-label {
  font-size: 13px;
  color: var(--color-text-secondary);
  font-weight: 600;
  margin-bottom: 12px;
  padding-left: 4px;
  position: relative;
}
.group-label::before {
  content: '';
  display: inline-block;
  width: 3px;
  height: 12px;
  background: var(--color-accent);
  border-radius: 2px;
  margin-right: 8px;
  vertical-align: middle;
}

.feed-item { margin-bottom: 16px; }
.item-line {
  display: flex;
  gap: 12px;
}
.item-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  margin-top: 16px;
  flex-shrink: 0;
  background: var(--color-text-secondary);
  border: 2px solid #fff;
  box-shadow: 0 0 0 2px rgba(31, 58, 95, 0.1);
}
.dot-blog { background: #2E74B5; }
.dot-diary { background: #67C23A; }
.dot-photo { background: #E6A23C; }

.item-card {
  flex: 1;
  background: #f8fafc;
  border-radius: 10px;
  padding: 14px 16px;
  transition: background 0.15s;
}
.item-card:hover { background: #f0f5fa; }

.item-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 14px;
}
.item-author { font-weight: 600; color: var(--color-text); }
.item-action { color: var(--color-text-secondary); }
.item-time { margin-left: auto; font-size: 12px; color: var(--color-text-secondary); }

.blog-preview {
  display: flex;
  gap: 12px;
  cursor: pointer;
}
.blog-cover {
  width: 100px;
  height: 70px;
  object-fit: cover;
  border-radius: 6px;
  flex-shrink: 0;
}
.blog-text { flex: 1; min-width: 0; }
.blog-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.blog-summary {
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.blog-tags { margin-top: 6px; }
.tag {
  font-size: 11px;
  color: var(--color-accent);
  margin-right: 6px;
}

.diary-content {
  font-size: 14px;
  color: var(--color-text);
  line-height: 1.6;
  white-space: pre-wrap;
}
.diary-meta {
  margin-top: 6px;
  font-size: 12px;
  color: var(--color-text-secondary);
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
  border-radius: 6px;
}
.photo-count { font-size: 12px; color: var(--color-text-secondary); }

.item-stats {
  display: flex;
  gap: 12px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid rgba(31, 58, 95, 0.06);
}
.stat-chip {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.feed-empty, .feed-loading { padding: 24px; }

@media (max-width: 768px) {
  .activity-feed { padding: 16px; }
  .feed-title { font-size: 17px; }
  .item-card { padding: 10px 12px; }
  .blog-cover { width: 80px; height: 56px; }
  .blog-title { font-size: 14px; }
  .photo-grid { grid-template-columns: repeat(auto-fill, minmax(60px, 1fr)); }
}
</style>
