<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('library.title'), to: '/library' }, { label: book?.title || $t('library.detail') }]" />
    <div v-if="book" class="lib-layout">
      <div class="card detail">
        <div class="book-header">
          <div class="book-cover-large-wrap">
            <img v-if="book.coverUrl" :src="book.coverUrl" class="book-cover-large" />
            <div v-else class="book-cover-large placeholder">
              <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="currentColor" stroke-width="1"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
            </div>
          </div>
          <div class="book-meta-info">
            <div class="book-title-text">{{ book.title }}</div>
            <div v-if="book.author" class="book-author-text">{{ book.author }}</div>
            <div class="meta-row">
              <span class="format-tag">{{ book.fileFormat }}</span>
              <span v-if="book.category" class="meta-cat">{{ book.category }}</span>
              <span v-if="book.fileSize" class="meta-size">{{ formatSize(book.fileSize) }}</span>
            </div>
            <div v-if="book.tags" class="meta-tags">
              <span v-for="t in String(book.tags).split(',').filter(Boolean)" :key="t" class="tag">#{{ t }}</span>
            </div>
            <div class="meta-stats">{{ book.viewCount }} {{ $t('library.views') }} · {{ formatDate(book.createdAt) }}</div>
            <div class="book-actions">
              <el-button v-if="canReadOnline" type="primary" @click="startReading">{{ $t('library.readOnline') }}</el-button>
              <a v-if="book.fileUrl" :href="book.fileUrl" :download="book.title" class="el-button is-default">{{ $t('library.download') }}</a>
              <template v-if="userStore.isLoggedIn">
                <el-button v-if="borrow?.status === 'WANT_READ'" @click="setBorrowStatus('READING')">{{ $t('library.startReading') }}</el-button>
                <el-button v-else-if="borrow?.status === 'READING'" @click="setBorrowStatus('FINISHED')">{{ $t('library.markFinished') }}</el-button>
                <el-button v-else-if="!borrow" @click="setBorrowStatus('WANT_READ')">{{ $t('library.wantRead') }}</el-button>
                <el-button v-if="borrow?.status === 'FINISHED'" @click="setBorrowStatus('READING')">{{ $t('library.reread') }}</el-button>
              </template>
            </div>
          </div>
        </div>
        <div v-if="book.description" class="book-description">
          <div class="section-label">{{ $t('library.description') }}</div>
          <div class="desc-text">{{ book.description }}</div>
        </div>
      </div>
    </div>

    <div v-if="reading" class="reader-overlay" @keydown.esc="stopReading">
      <div class="reader-bar">
        <span class="reader-title">{{ book?.title }}</span>
        <div class="reader-controls">
          <el-button v-if="book?.fileFormat === 'TXT'" size="small" @click="prevPage" :disabled="readerPage <= 0">{{ $t('library.prevPage') }}</el-button>
          <span v-if="book?.fileFormat === 'TXT'" class="page-info">{{ readerPage + 1 }} / {{ totalPages }}</span>
          <el-button v-if="book?.fileFormat === 'TXT'" size="small" @click="nextPage" :disabled="readerPage >= totalPages - 1">{{ $t('library.nextPage') }}</el-button>
          <el-button size="small" @click="stopReading">{{ $t('common.close') }}</el-button>
        </div>
      </div>
      <div class="reader-content" ref="readerRef">
        <iframe v-if="book?.fileFormat === 'PDF'" :src="book.fileUrl" class="pdf-frame" />
        <div v-else-if="book?.fileFormat === 'TXT'" ref="txtRef" class="txt-reader">{{ currentPageText }}</div>
        <div v-else-if="book?.fileFormat === 'EPUB'" ref="epubRef" class="epub-reader"></div>
        <div v-else class="unsupported-format">
          <el-empty :description="$t('library.unsupportedFormat')" />
        </div>
      </div>
    </div>

    <el-empty v-if="!book && !loading" :description="$t('library.notFound')" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { libraryApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb.vue'

const { t } = useI18n()
const route = useRoute()
const userStore = useUserStore()
const book = ref(null)
const borrow = ref(null)
const loading = ref(false)
const reading = ref(false)
const readerRef = ref(null)
const txtRef = ref(null)
const epubRef = ref(null)
const txtContent = ref('')
const readerPage = ref(0)
const charsPerPage = 2000

const canReadOnline = computed(() => {
  if (!book.value) return false
  const fmt = book.value.fileFormat
  return fmt === 'PDF' || fmt === 'EPUB' || fmt === 'TXT'
})

const totalPages = computed(() => {
  if (!txtContent.value) return 1
  return Math.ceil(txtContent.value.length / charsPerPage)
})

const currentPageText = computed(() => {
  if (!txtContent.value) return ''
  const start = readerPage.value * charsPerPage
  return txtContent.value.slice(start, start + charsPerPage)
})

const formatDate = (d) => (d ? new Date(d).toLocaleDateString('zh-CN') : '')
const formatSize = (bytes) => {
  if (!bytes) return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

const canEdit = computed(() => book.value && (userStore.isOwner || book.value.uploaderId === userStore.userInfo?.id))

const loadAll = async () => {
  loading.value = true
  try {
    book.value = await libraryApi.detail(route.params.id)
  } catch (e) {
    book.value = null
  } finally {
    loading.value = false
  }
  if (userStore.isLoggedIn) {
    try {
      borrow.value = await libraryApi.getBorrow(route.params.id)
    } catch (e) {}
  }
}

const startReading = async () => {
  reading.value = true
  if (book.value.fileFormat === 'TXT') {
    if (!txtContent.value) {
      try {
        const res = await fetch(book.value.fileUrl)
        txtContent.value = await res.text()
      } catch (e) {
        ElMessage.error(t('library.loadFailed'))
        reading.value = false
        return
      }
    }
    readerPage.value = 0
  } else if (book.value.fileFormat === 'EPUB') {
    await nextTick()
    loadEpub()
  }
  if (borrow.value?.status !== 'READING' && userStore.isLoggedIn) {
    setBorrowStatus('READING')
  }
}

const stopReading = () => {
  reading.value = false
  if (epubRendition) {
    epubRendition.destroy()
    epubRendition = null
  }
}

let epubRendition = null
const loadEpub = async () => {
  if (!epubRef.value || !book.value?.fileUrl) return
  try {
    const ePub = (await import('epubjs')).default
    const epub = ePub(book.value.fileUrl)
    epubRendition = epub.renderTo(epubRef.value, { width: '100%', height: '100%' })
    epubRendition.display()
  } catch (e) {
    ElMessage.error(t('library.loadFailed'))
  }
}

const prevPage = () => {
  if (readerPage.value > 0) readerPage.value--
}
const nextPage = () => {
  if (readerPage.value < totalPages.value - 1) readerPage.value++
}

const setBorrowStatus = async (status) => {
  try {
    borrow.value = await libraryApi.updateBorrow(route.params.id, { status })
    if (status === 'READING') ElMessage.success(t('library.readingStarted'))
    else if (status === 'FINISHED') ElMessage.success(t('library.finishedMsg'))
  } catch (e) {}
}

onBeforeUnmount(() => {
  if (epubRendition) epubRendition.destroy()
})

onMounted(loadAll)
</script>

<style scoped>
.lib-layout { display: flex; gap: 24px; align-items: flex-start; }
.detail { flex: 1; min-width: 0; padding: 24px 28px; }

.book-header { display: flex; gap: 24px; margin-bottom: 20px; }
.book-cover-large-wrap { width: 140px; flex-shrink: 0; }
.book-cover-large { width: 100%; aspect-ratio: 3/4; object-fit: cover; border-radius: 8px; box-shadow: 0 4px 16px rgba(0,0,0,0.1); }
.book-cover-large.placeholder { display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, rgba(184,140,110,0.12), rgba(184,140,110,0.04)); color: var(--color-text-secondary); opacity: 0.4; }
html.dark .book-cover-large.placeholder { background: linear-gradient(135deg, rgba(212,178,152,0.1), rgba(212,178,152,0.03)); }

.book-meta-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 6px; }
.book-title-text { font-size: 24px; font-weight: 700; color: var(--color-primary); line-height: 1.4; }
.book-author-text { font-size: 15px; color: var(--color-text-secondary); }
.meta-row { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-top: 4px; }
.format-tag { background: rgba(184,140,110,0.12); color: var(--color-accent, #b88c6e); padding: 2px 10px; border-radius: 8px; font-size: 12px; font-weight: 600; }
html.dark .format-tag { background: rgba(212,178,152,0.15); color: #d4b298; }
.meta-cat { background: rgba(184,140,110,0.06); border: 1px solid rgba(184,140,110,0.1); color: var(--color-text-secondary); padding: 2px 8px; border-radius: 8px; font-size: 12px; }
html.dark .meta-cat { background: rgba(212,178,152,0.1); border-color: rgba(212,178,152,0.12); }
.meta-size { font-size: 12px; color: var(--color-text-secondary); opacity: 0.6; }
.meta-tags { display: flex; gap: 6px; flex-wrap: wrap; }
.tag { background: rgba(184,140,110,0.06); color: var(--color-accent); padding: 1px 8px; border-radius: 10px; font-size: 12px; }
.meta-stats { font-size: 13px; color: var(--color-text-secondary); opacity: 0.7; }
.book-actions { display: flex; gap: 8px; margin-top: 12px; flex-wrap: wrap; align-items: center; }
.book-actions a { text-decoration: none; }

.book-description { margin-top: 20px; padding-top: 16px; border-top: 1px solid var(--color-border); }
.section-label { font-size: 16px; font-weight: 600; color: var(--color-primary); margin-bottom: 8px; position: relative; padding-left: 10px; }
.section-label::before { content: ''; position: absolute; left: 0; top: 3px; bottom: 3px; width: 3px; border-radius: 2px; background: var(--color-accent, #b88c6e); }
.desc-text { font-size: 14px; line-height: 1.8; color: var(--color-text); white-space: pre-wrap; }

.reader-overlay {
  position: fixed; inset: 0; z-index: 200; background: var(--color-bg, #fff);
  display: flex; flex-direction: column;
}
html.dark .reader-overlay { background: #1a1a2e; }
.reader-bar {
  display: flex; justify-content: space-between; align-items: center; padding: 10px 20px;
  border-bottom: 1px solid var(--color-border); flex-shrink: 0; min-height: 50px;
}
.reader-title { font-size: 15px; font-weight: 600; color: var(--color-text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.reader-controls { display: flex; gap: 8px; align-items: center; }
.page-info { font-size: 13px; color: var(--color-text-secondary); min-width: 60px; text-align: center; }
.reader-content { flex: 1; overflow: hidden; position: relative; }
.pdf-frame { width: 100%; height: 100%; border: none; }
.txt-reader { padding: 40px 60px; font-size: 16px; line-height: 2; color: var(--color-text); height: 100%; overflow-y: auto; white-space: pre-wrap; word-wrap: break-word; }
.epub-reader { width: 100%; height: 100%; }
.unsupported-format { display: flex; align-items: center; justify-content: center; height: 100%; }

@media (max-width: 900px) {
  .book-header { flex-direction: column; align-items: center; text-align: center; }
  .book-cover-large-wrap { width: 120px; }
  .book-meta-info { align-items: center; }
  .meta-tags { justify-content: center; }
  .txt-reader { padding: 20px; }
}
</style>
