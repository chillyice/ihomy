<!-- 日记本书架:每位成员一本日记本(封面网格),点击进入翻书视图 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('diary.title') }]" />

    <div class="page-toolbar card">
      <div class="tb-right">
        <button v-if="userStore.isLoggedIn" class="write-btn" @click="router.push('/diary/edit')">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/></svg>
          {{ $t('diary.newDiary') }}
        </button>
      </div>
    </div>

    <div v-loading="loading" class="shelf-main">
      <div class="shelf">
        <div v-for="b in books" :key="b.authorId" class="notebook" @click="router.push(`/diary/book/${b.authorId}`)">
          <div class="nb-cover">
            <div class="nb-spine"></div>
            <div class="nb-label">
              <div class="nb-name" :title="b.authorName">{{ b.authorName || '-' }}</div>
              <div class="nb-count">{{ $t('diary.entryCount', { n: b.count }) }}</div>
              <div class="nb-latest">
                <div v-if="b.count > 1">{{ $t('diary.startDate', { date: fmtShort(b.earliest) }) }}</div>
                <div>{{ b.count > 1 ? $t('diary.endDate', { date: fmtShort(b.latest) }) : fmtShort(b.latest) }}</div>
              </div>
            </div>
            <div class="nb-band"></div>
          </div>
        </div>
      </div>

      <div v-if="!loading && !books.length" class="empty-state">
        <el-empty :description="$t('diary.noData')">
          <button v-if="userStore.isLoggedIn" class="write-btn" @click="router.push('/diary/edit')">{{ $t('diary.emptyWriteBtn') }}</button>
        </el-empty>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { diaryApi } from '@/api'
import { useUserStore } from '@/stores/user'
import Breadcrumb from '@/components/Breadcrumb.vue'

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()
const books = ref([])
const loading = ref(false)

const fmtShort = (s) => String(s || '').slice(0, 10)

const load = async () => {
  loading.value = true
  try {
    // ponytail: 一次拉 200 条客户端按作者分组;单作者超 200 篇再改服务端按作者分页
    const data = await diaryApi.list({ current: 1, size: 200 })
    const records = data.records || []
    const map = new Map()
    for (const d of records) {
      const key = d.authorId ?? 0
      if (!map.has(key)) map.set(key, { authorId: d.authorId, authorName: d.authorName, count: 0, latest: '', earliest: '' })
      const b = map.get(key)
      b.count++
      const day = fmtShort(d.createdAt)
      if (!b.latest || day > b.latest) b.latest = day
      if (!b.earliest || day < b.earliest) b.earliest = day
    }
    books.value = [...map.values()].sort((a, b) => (a.latest < b.latest ? 1 : -1))
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.shelf-main { min-width: 0; }

.shelf {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 26px 22px;
  padding: 6px 2px 20px;
}

.notebook { aspect-ratio: 3 / 4; cursor: pointer; position: relative; }

.nb-cover {
  position: absolute; inset: 0;
  border-radius: 5px 12px 12px 5px;
  background: linear-gradient(155deg, #A8845C 0%, #8B6F47 45%, #6B5435 100%);
  box-shadow:
    0 2px 4px rgba(58,46,34,0.18),
    0 10px 26px rgba(58,46,34,0.2),
    inset -5px 0 8px rgba(0,0,0,0.18),
    inset 0 1px 0 rgba(255,248,235,0.22);
  overflow: hidden;
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}
.nb-cover::before {
  content: '';
  position: absolute; inset: 0;
  background: repeating-linear-gradient(115deg, transparent 0 7px, rgba(255,255,255,0.025) 7px 8px);
  pointer-events: none;
}
.notebook:hover .nb-cover {
  transform: translateY(-6px);
  box-shadow:
    0 4px 8px rgba(58,46,34,0.2),
    0 18px 40px rgba(58,46,34,0.28),
    inset -5px 0 8px rgba(0,0,0,0.18),
    inset 0 1px 0 rgba(255,248,235,0.22);
}

.nb-spine {
  position: absolute; left: 0; top: 0; bottom: 0; width: 16px;
  background: linear-gradient(to right, rgba(0,0,0,0.32), rgba(0,0,0,0.08) 60%, rgba(255,248,235,0.12));
  box-shadow: 1px 0 2px rgba(0,0,0,0.2);
}

.nb-band {
  position: absolute; right: 12px; top: 0; bottom: 8%; width: 5px;
  background: linear-gradient(to bottom, rgba(168,72,58,0.75), rgba(122,50,40,0.75));
  border-radius: 0 0 3px 3px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.25);
}

.nb-label {
  position: absolute; left: 13%; right: 14%; top: 16%;
  background: rgba(255,253,248,0.94);
  border-radius: 4px;
  padding: 14px 12px 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.22);
  transform: rotate(-1.2deg);
  transition: transform 0.25s ease;
}
.nb-label::after {
  content: '';
  position: absolute; inset: 4px;
  border: 1px dashed rgba(58,46,34,0.22);
  border-radius: 3px;
  pointer-events: none;
}
.notebook:hover .nb-label { transform: rotate(0deg); }

.nb-name {
  font-size: 16px; font-weight: 600; color: #3A2E22;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.nb-count { font-size: 12px; color: #6b5c4a; margin-top: 6px; }
.nb-latest { margin-top: 4px; font-variant-numeric: tabular-nums; }
.nb-latest div { font-size: 11px; color: #8a7a64; line-height: 1.5; }

html.dark .nb-cover {
  background: linear-gradient(155deg, #5A4630 0%, #463622 45%, #322616 100%);
  box-shadow:
    0 2px 4px rgba(0,0,0,0.3),
    0 10px 26px rgba(0,0,0,0.4),
    inset -5px 0 8px rgba(0,0,0,0.3),
    inset 0 1px 0 rgba(232,220,200,0.08);
}
html.dark .nb-label { background: rgba(30,42,72,0.92); box-shadow: 0 2px 8px rgba(0,0,0,0.4); }
html.dark .nb-label::after { border-color: rgba(232,220,200,0.2); }
html.dark .nb-name { color: #E8DCC8; }
html.dark .nb-count { color: rgba(232,220,200,0.6); }
html.dark .nb-latest { color: rgba(232,220,200,0.45); }

.empty-state { padding: 48px 0; }

@media (max-width: 768px) {
  .shelf { grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 18px 14px; }
}
</style>
