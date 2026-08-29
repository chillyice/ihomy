<!-- 日记本翻书视图:双页信纸展示(桌面)/单页(移动),左右方向键+按钮翻页,目录跳转 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('diary.title'), to: '/diary' }, { label: bookTitle }]" />

    <div v-loading="loading" class="book-main">
      <div v-if="!loading && entries.length" class="book-wrap">
        <div :key="pos + (isMobile ? 'm' : 'd')" class="book-spread" :class="'flip-' + dir">
          <template v-if="!isMobile">
            <DiaryPage :page="pages[pos * 2] || blankPage" :no="pos * 2 + 1" side="left" :can-edit="canEdit(pages[pos * 2])" @action="(a) => onAction(a, pages[pos * 2])" />
            <DiaryPage :page="pages[pos * 2 + 1] || blankPage" :no="pos * 2 + 2" side="right" :can-edit="canEdit(pages[pos * 2 + 1])" @action="(a) => onAction(a, pages[pos * 2 + 1])" />
          </template>
          <DiaryPage v-else :page="pages[pos] || blankPage" :no="pos + 1" side="single" :can-edit="canEdit(pages[pos])" @action="(a) => onAction(a, pages[pos])" />
        </div>

        <div class="book-toolbar">
          <button class="ghost-btn" :disabled="!canPrev" @click="flip(-1)">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 18l-6-6 6-6"/></svg>
            {{ $t('diary.prevPage') }}
          </button>
          <el-dropdown trigger="click" @command="jumpToEntry">
            <button class="ghost-btn">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
              {{ $t('diary.toc') }}
            </button>
            <template #dropdown>
              <el-dropdown-menu class="toc-menu">
                <el-dropdown-item v-for="(e, i) in entries" :key="e.id" :command="i">
                  <span class="toc-date">{{ shortDate(e.createdAt) }}</span>
                  <span class="toc-pages">{{ e._pages }}P</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <span class="page-indicator">{{ indicator }}</span>
          <button class="ghost-btn" :disabled="!canNext" @click="flip(1)">
            {{ $t('diary.nextPage') }}
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18l6-6-6-6"/></svg>
          </button>
        </div>
      </div>

      <div v-if="!loading && !entries.length" class="empty-state">
        <el-empty :description="$t('diary.emptyBook')">
          <button class="ghost-btn" @click="router.push('/diary')">{{ $t('common.back') }}</button>
        </el-empty>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { diaryApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { useDevice } from '@/composables/useDevice'
import { LINES_PER_PAGE, PAGE_H, measureDiaryLines } from '@/utils/diary'
import { parseDoodle, doodleExtentY } from '@/utils/doodle'
import Breadcrumb from '@/components/Breadcrumb.vue'
import DiaryPage from './DiaryPage.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { isMobile } = useDevice()

const loading = ref(false)
const entries = ref([])
const pages = ref([])
const pos = ref(0)
const dir = ref('next')
const blankPage = { blank: true }

const bookTitle = computed(() => (authorName.value ? t('diary.bookOf', { name: authorName.value }) : t('diary.title')))
const authorName = computed(() => entries.value[0]?.authorName || '')

const maxPos = computed(() => (isMobile.value ? pages.value.length - 1 : Math.ceil(pages.value.length / 2) - 1))
const canPrev = computed(() => pos.value > 0)
const canNext = computed(() => pos.value < maxPos.value)

const indicator = computed(() => {
  const n = pages.value.length
  if (!n) return ''
  if (isMobile.value) return t('diary.singlePageOf', { a: pos.value + 1, n })
  const a = pos.value * 2 + 1
  const b = Math.min(pos.value * 2 + 2, n)
  return b > a ? t('diary.pageOf', { a, b, n }) : t('diary.singlePageOf', { a, n })
})

const canEdit = (page) => !!page && !page.blank && (userStore.isOwner || page.e.authorId === userStore.userInfo?.id)
const shortDate = (s) => String(s || '').slice(0, 10)

const buildPages = () => {
  const arr = []
  for (const e of entries.value) {
    // 紧凑连续排页:上一篇写完下一篇紧接其后,不补空白页(仅总页数为奇数时最后一页右侧留白)
    e._firstPage = arr.length
    for (let p = 0; p < e._pages; p++) arr.push({ e, p, first: p === 0 })
  }
  if (!arr.length) arr.push(blankPage)
  pages.value = arr
}

const load = async () => {
  loading.value = true
  try {
    // ponytail: 拉全量后客户端按作者过滤;单作者超 200 篇再改服务端按作者查询
    const data = await diaryApi.list({ current: 1, size: 200 })
    entries.value = (data.records || []).filter((d) => String(d.authorId ?? 0) === String(route.params.authorId))
    entries.value.forEach((e) => {
      // 页数 = max(正文行数, 涂鸦高度) 折算的页数,防止涂鸦超出文本被裁掉
      const extent = doodleExtentY(parseDoodle(e.doodle))
      e._pages = Math.max(1, Math.ceil(measureDiaryLines(e.content) / LINES_PER_PAGE), Math.ceil(extent / PAGE_H))
    })
    buildPages()
    pos.value = 0
  } finally {
    loading.value = false
  }
}

const flip = (d) => {
  if ((d < 0 && !canPrev.value) || (d > 0 && !canNext.value)) return
  dir.value = d > 0 ? 'next' : 'prev'
  pos.value += d
}

const jumpToEntry = (i) => {
  const e = entries.value[i]
  if (!e) return
  const idx = e._firstPage ?? 0
  dir.value = idx > (isMobile.value ? pos.value : pos.value * 2) ? 'next' : 'prev'
  pos.value = isMobile.value ? idx : Math.floor(idx / 2)
}

const onAction = async (action, page) => {
  if (!page || page.blank) return
  if (action === 'edit') {
    router.push(`/diary/edit/${page.e.id}`)
  } else if (action === 'delete') {
    try {
      await ElMessageBox.confirm(t('diary.deleteConfirm'), { type: 'warning', confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel'), closeOnClickModal: true })
      await diaryApi.remove(page.e.id)
      ElMessage.success(t('common.deleted'))
      await load()
      pos.value = Math.min(pos.value, maxPos.value)
    } catch (e) {
      if (e !== 'cancel') ElMessage.error(e.message || 'Failed')
    }
  }
}

const onKeydown = (e) => {
  if (e.key === 'ArrowLeft') flip(-1)
  else if (e.key === 'ArrowRight') flip(1)
}

// 桌面<->移动切换时转换页码语义(双页spread <-> 单页)
watch(isMobile, (m) => {
  pos.value = m ? Math.min(maxPos.value, pos.value * 2) : Math.floor(pos.value / 2)
})

onMounted(() => {
  load()
  window.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<style scoped>
.book-main { min-width: 0; }

.book-wrap { display: flex; flex-direction: column; align-items: center; gap: 18px; padding-bottom: 20px; }

.book-spread { display: flex; justify-content: center; gap: 10px; }

/* 翻页动画:方向性滑入(下次翻页重挂载触发) */
.book-spread { will-change: transform, opacity; }
.flip-next { animation: flipNext 0.28s ease both; }
.flip-prev { animation: flipPrev 0.28s ease both; }
@keyframes flipNext { from { opacity: 0; transform: translateX(28px); } to { opacity: 1; transform: translateX(0); } }
@keyframes flipPrev { from { opacity: 0; transform: translateX(-28px); } to { opacity: 1; transform: translateX(0); } }

.book-toolbar {
  display: flex; align-items: center; gap: 14px;
  background: rgba(255,255,255,0.45);
  backdrop-filter: blur(24px) saturate(1.2);
  -webkit-backdrop-filter: blur(24px) saturate(1.2);
  border: 1px solid rgba(255,255,255,0.4);
  border-radius: 14px;
  padding: 8px 14px;
  box-shadow: 0 2px 12px rgba(58,46,34,0.06);
}
html.dark .book-toolbar {
  background: rgba(30,42,72,0.45);
  border-color: rgba(255,255,255,0.08);
  box-shadow: 0 2px 12px rgba(0,0,0,0.15);
}

.page-indicator { font-size: 13px; color: var(--color-text-secondary); font-variant-numeric: tabular-nums; min-width: 150px; text-align: center; }

:deep(.toc-menu) { max-height: 320px; overflow-y: auto; }
.toc-date { font-variant-numeric: tabular-nums; }
.toc-pages { margin-left: 10px; font-size: 11px; color: var(--color-text-secondary); opacity: 0.7; }

.empty-state { padding: 48px 0; }

@media (max-width: 768px) {
  .book-spread { gap: 0; }
  .book-toolbar { gap: 8px; padding: 6px 10px; }
  .page-indicator { min-width: 90px; font-size: 12px; }
}
</style>
