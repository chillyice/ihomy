<!-- 站内对象选择器:相册/视频/图书/日记/愿望五类,过滤 PRIVATE(拍板 #4),选中即生成卡片草稿 -->
<template>
  <el-dialog v-model="visible" :title="$t('chat.picker.title')" width="dialog-lg" append-to-body class="chat-object-picker" @open="onOpen">
    <el-tabs v-model="tab">
      <el-tab-pane v-for="t in TABS" :key="t.key" :name="t.key" :label="$t(`chat.picker.tab.${t.key}`)" />
    </el-tabs>
    <el-input v-model="keyword" :placeholder="$t('chat.picker.searchPlaceholder')" clearable :prefix-icon="Search" class="picker-search" />
    <div v-loading="loading" class="picker-list">
      <el-empty v-if="!loading && !filtered.length" :description="$t('chat.picker.empty')" :image-size="60" />
      <button v-for="item in filtered" :key="`${tab}-${item.objId}`" type="button" class="pick-row" @click="pick(item)">
        <img v-if="item.cover" :src="item.cover" :alt="item.title" class="pick-cover" loading="lazy" />
        <el-icon v-else class="pick-icon" :size="18"><component :is="t.icon" /></el-icon>
        <div class="pick-main">
          <div class="pick-title">{{ item.title }}</div>
          <div v-if="item.sub" class="pick-sub">{{ item.sub }}</div>
        </div>
      </button>
    </div>
  </el-dialog>
</template>

<script setup>
// 站内选择器:五类列表各拉一次,客户端过滤 PRIVATE 与关键字;产出 {objType,objId,title,sub,url,cover}
import { computed, ref, watch } from 'vue'
import { Collection, Film, Notebook, Reading, Search, Star } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { albumApi, diaryApi, libraryApi, videoApi, wishApi } from '@/api'
import { clipText } from '@/utils/chatMsg'

const visible = defineModel({ type: Boolean, default: false })
const emit = defineEmits(['pick'])

const { t } = useI18n()

const TABS = [
  { key: 'album', icon: Collection },
  { key: 'video', icon: Film },
  { key: 'book', icon: Reading },
  { key: 'diary', icon: Notebook },
  { key: 'wish', icon: Star },
]

const tab = ref('album')
const keyword = ref('')
const loading = ref(false)
const items = ref({ album: [], video: [], book: [], diary: [], wish: [] })

let loaded = false

const onOpen = async () => {
  if (loaded) return
  loading.value = true
  try {
    const [albums, videos, books, diaries, wishes] = await Promise.all([
      albumApi.list().catch(() => []),
      videoApi.list().catch(() => []),
      libraryApi.list({ current: 1, size: 200 }).catch(() => ({ records: [] })),
      diaryApi.list({ current: 1, size: 200 }).catch(() => ({ records: [] })),
      wishApi.list().catch(() => []),
    ])
    items.value = {
      album: (albums || [])
        .filter((a) => a.type !== 'private')
        .map((a) => ({
          objType: 'ALBUM',
          objId: a.id,
          title: a.name,
          sub: '',
          url: `/album/${a.id}`,
          cover: a.coverUrl || a.coverPhotoUrl || '',
        })),
      video: (videos || [])
        .filter((v) => v.visibility !== 'PRIVATE')
        .map((v) => ({
          objType: 'VIDEO',
          objId: v.id,
          title: v.title,
          sub: v.year ? String(v.year) : '',
          url: `/cinema/${v.id}`,
          cover: v.poster || '',
        })),
      book: ((books && books.records) || [])
        .filter((b) => b.visibility !== 'PRIVATE')
        .map((b) => ({
          objType: 'BOOK',
          objId: b.id,
          title: b.title,
          sub: b.author || '',
          url: `/library?book=${b.id}`,
          cover: b.coverUrl || '',
        })),
      diary: ((diaries && diaries.records) || [])
        .filter((d) => d.visibility !== 'PRIVATE')
        .map((d) => ({
          objType: 'DIARY',
          objId: d.id,
          title: clipText(d.content, 40),
          sub: '',
          url: `/diary/book/${d.authorId}`,
          cover: '',
        })),
      wish: (wishes || [])
        .filter((w) => w.visibility !== 'PRIVATE')
        .map((w) => ({
          objType: 'WISH',
          objId: w.id,
          title: w.title,
          sub: w.reason || '',
          url: '/wish',
          cover: '',
        })),
    }
    loaded = true
  } finally {
    loading.value = false
  }
}

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  const list = items.value[tab.value] || []
  if (!kw) return list
  return list.filter((i) => (i.title || '').toLowerCase().includes(kw) || (i.sub || '').toLowerCase().includes(kw))
})

const pick = (item) => {
  emit('pick', item)
  visible.value = false
}

watch(visible, (v) => {
  if (v) keyword.value = ''
})
</script>

<style scoped>
.picker-search {
  margin-bottom: 10px;
}
.picker-list {
  height: 46vh;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.pick-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition:
    transform var(--dur-fast) var(--ease-soft),
    background var(--dur-fast) ease;
}
.pick-row:hover {
  transform: translateY(-1px);
  background: rgba(var(--color-brand-rgb), 0.06);
}
.pick-cover {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  object-fit: cover;
  flex-shrink: 0;
}
.pick-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(var(--color-brand-rgb), 0.1);
  color: var(--color-brand-text);
}
.pick-main {
  min-width: 0;
}
.pick-title {
  font-size: 14px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pick-sub {
  margin-top: 2px;
  font-size: 12px;
  color: var(--color-text-secondary, #888);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
