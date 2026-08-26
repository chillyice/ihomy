<template>
  <div class="mobile-more">
    <div class="more-section" v-for="g in groups" :key="g.category">
      <div class="more-section-title">{{ g.label }}</div>
      <div class="more-grid">
        <div
          v-for="m in g.modules"
          :key="m.code"
          class="more-item"
          @click="navigate(m.path)"
        >
          <span class="more-icon">
            <svg v-if="m.code === 'settings'" viewBox="0 0 24 24" width="24" height="24" fill="currentColor"><path d="M12 2L9.5 4.5L6 4L5 7.5L2 9.5L3.5 13L2 16.5L5 18.5L6 22L9.5 21.5L12 24L14.5 21.5L18 22L19 18.5L22 16.5L20.5 13L22 9.5L19 7.5L18 4L14.5 4.5L12 2ZM12 16A4 4 0 1 1 12 8A4 4 0 0 1 12 16Z"/></svg>
                <svg v-else-if="m.code === 'ops'" viewBox="0 0 24 24" width="24" height="24" fill="currentColor"><path d="M3 4H21V16H3V4ZM5 6V14H19V6H5ZM2 18H22V20H2V18Z"/></svg>
                <el-icon v-else><component :is="iconComp(m.code)" /></el-icon>
          </span>
          <span class="more-label">{{ m.title }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { Document, Notebook, Picture, Calendar, VideoPlay, Trophy, Aim, AlarmClock, List, Star, Wallet, PictureRounded, Share, User, Box, MapLocation, ChatDotRound, Food, Reading, Headset } from '@element-plus/icons-vue'

const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const ICON_MAP = {
  blog: Document, diary: Notebook, album: Picture, anniversary: Calendar, cinema: VideoPlay, music: Headset,
  points: Trophy, task: Aim, reminder: AlarmClock, plan: List, wish: Star,
  book: Wallet, cascade: PictureRounded, tree: Share, member: User, storage: Box, item: MapLocation,
  chat: ChatDotRound, kitchen: Food, library: Reading,
}
const iconComp = (code) => ICON_MAP[code] || Document

const NAV_PATHS = {
  blog: '/blog', diary: '/diary', album: '/album', anniversary: '/anniversary',
  cinema: '/cinema', music: '/music', member: '/member', points: '/points', task: '/task',
  reminder: '/reminder', plan: '/plan', wish: '/wish', book: '/book',
  chat: '/chat', tree: '/tree', cascade: '/cascade',
  item: '/item', kitchen: '/kitchen', library: '/library', settings: '/settings', ops: '/ops',
}

const CATEGORY_LABELS = { content: '内容', life: '生活', social: '成员', system: '系统' }

const groups = computed(() => {
  const list = !appStore.modules.length ? [] : appStore.modules
    .filter(m => NAV_PATHS[m.code] && m.enabled !== 0 && m.code !== 'storage')
    .map(m => ({
      code: m.code, title: m.title, path: NAV_PATHS[m.code],
      category: m.category === 'album' ? 'content' : (m.category || 'life'),
    }))
  list.push({ code: 'settings', title: '设置', path: '/settings', category: 'system' })
  if (userStore.hasPerm('ops:view')) {
    list.push({ code: 'ops', title: '运维管理', path: '/ops', category: 'system' })
  }
  const grouped = {}
  for (const m of list) {
    if (!grouped[m.category]) grouped[m.category] = []
    grouped[m.category].push(m)
  }
  const order = ['content', 'life', 'social', 'system']
  return order.filter(c => grouped[c]?.length).map(c => ({ category: c, label: CATEGORY_LABELS[c] || '功能', modules: grouped[c] }))
})

const navigate = (path) => router.push(path)
</script>

<style scoped>
.mobile-more { padding: 16px 16px 80px; }

.more-section { margin-bottom: 24px; }
.more-section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-secondary, #888);
  margin-bottom: 12px;
  padding-left: 4px;
}
html.dark .more-section-title { color: rgba(232,220,200,0.5); }

.more-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px 8px;
}

.more-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.more-item:active { opacity: 0.6; }

.more-icon {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(184, 140, 110, 0.08);
  color: var(--color-primary, #b88c6e);
  font-size: 24px;
}
html.dark .more-icon { background: rgba(212,178,152,0.12); color: #d4b298; }

.more-label {
  font-size: 12px;
  color: var(--color-text-primary, #333);
  text-align: center;
  white-space: nowrap;
}
html.dark .more-label { color: #E8DCC8; }
</style>
