<template>
  <el-dialog v-model="show" :title="$t('library.detail')" width="640px" append-to-body @close="$emit('close')">
    <div v-loading="loading" class="detail-body">
      <div v-if="book" class="book-header">
        <div class="cover-wrap">
          <img v-if="book.coverUrl" :src="book.coverUrl" class="book-cover" />
          <div v-else class="book-cover placeholder">
            <svg viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="currentColor" stroke-width="1"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
          </div>
        </div>
        <div class="meta-info">
          <div class="book-title-text">{{ book.title }}</div>
          <div v-if="book.author" class="book-author-text">{{ book.author }}</div>
          <div class="meta-row">
            <span class="format-tag">{{ book.fileFormat }}</span>
            <span v-if="book.fileSize" class="meta-size">{{ formatSize(book.fileSize) }}</span>
            <span class="meta-views">{{ book.viewCount }} {{ $t('library.views') }}</span>
          </div>
          <div v-if="book.tags" class="meta-tags">
            <span v-for="t in String(book.tags).split(',').filter(Boolean)" :key="t" class="tag">#{{ t }}</span>
          </div>
          <div class="meta-date">{{ formatDate(book.createdAt) }}</div>
        </div>
      </div>
      <div v-if="book?.description" class="book-desc">
        <div class="section-label">{{ $t('library.description') }}</div>
        <div class="desc-text">{{ book.description }}</div>
      </div>
      <div v-if="book" class="book-cats">
        <div class="section-label">{{ $t('library.categoriesLabel') }}</div>
        <div class="cat-tags">
          <span v-for="cid in book.categoryIds" :key="cid" class="cat-tag">{{ catName(cid) }}</span>
          <span v-if="!book.categoryIds?.length" class="no-cat">{{ $t('library.uncategorized') }}</span>
        </div>
      </div>
    </div>
    <template #footer v-if="book">
      <div class="detail-footer">
        <div class="footer-left">
          <template v-if="userStore.isLoggedIn">
            <el-button v-if="borrow?.status === 'WANT_READ'" size="small" @click="setBorrowStatus('READING')">{{ $t('library.startReading') }}</el-button>
            <el-button v-else-if="borrow?.status === 'READING'" size="small" @click="setBorrowStatus('FINISHED')">{{ $t('library.markFinished') }}</el-button>
            <el-button v-else-if="!borrow" size="small" @click="setBorrowStatus('WANT_READ')">{{ $t('library.wantRead') }}</el-button>
            <el-button v-if="borrow?.status === 'FINISHED'" size="small" @click="setBorrowStatus('READING')">{{ $t('library.reread') }}</el-button>
          </template>
        </div>
        <div class="footer-right">
          <el-button v-if="canReadOnline" type="primary" size="small" @click="$emit('read', book)">{{ $t('library.readOnline') }}</el-button>
          <a v-if="book.fileUrl" :href="book.fileUrl" :download="book.title" class="el-button is-default is-small">{{ $t('library.download') }}</a>
          <el-button v-if="canEdit" size="small" @click="router.push(`/library/edit/${book.id}`)">{{ $t('library.editBook') }}</el-button>
          <el-button @click="show = false" size="small">{{ $t('common.close') }}</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { libraryApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'

const props = defineProps({ bookId: [Number, String] })
const emit = defineEmits(['close', 'updated', 'deleted', 'read'])

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()
const show = ref(true)
const book = ref(null)
const borrow = ref(null)
const loading = ref(false)
const categories = ref([])

const canEdit = computed(() => book.value && (userStore.isOwner || book.value.uploaderId === userStore.userInfo?.id))
const canReadOnline = computed(() => book.value && ['PDF', 'EPUB', 'TXT'].includes(book.value.fileFormat))

const catName = (id) => categories.value.find(c => c.id === id)?.name || ''

const formatDate = (d) => (d ? new Date(d).toLocaleDateString('zh-CN') : '')
const formatSize = (bytes) => {
  if (!bytes) return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

const loadAll = async () => {
  loading.value = true
  try {
    book.value = await libraryApi.detail(props.bookId)
  } catch (e) {
    book.value = null
  } finally {
    loading.value = false
  }
  if (userStore.isLoggedIn) {
    try { borrow.value = await libraryApi.getBorrow(props.bookId) } catch (e) {}
  }
  try { categories.value = await libraryApi.categories() || [] } catch (e) {}
}

const setBorrowStatus = async (status) => {
  try {
    borrow.value = await libraryApi.updateBorrow(props.bookId, { status })
    ElMessage.success(status === 'READING' ? t('library.readingStarted') : status === 'FINISHED' ? t('library.finishedMsg') : t('common.saveSuccess'))
  } catch (e) {}
}

watch(show, (v) => { if (!v) emit('close') })

onMounted(loadAll)
</script>

<style scoped>
.detail-body { min-height: 200px; }
.book-header { display: flex; gap: 20px; margin-bottom: 16px; }
.cover-wrap { width: 120px; flex-shrink: 0; }
.book-cover { width: 100%; aspect-ratio: 3/4; object-fit: cover; border-radius: 8px; box-shadow: 0 4px 16px rgba(0,0,0,0.1); }
.book-cover.placeholder { display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, rgba(184,140,110,0.12), rgba(184,140,110,0.04)); color: var(--color-text-secondary); opacity: 0.4; border-radius: 8px; }
html.dark .book-cover.placeholder { background: linear-gradient(135deg, rgba(212,178,152,0.1), rgba(212,178,152,0.03)); }
.meta-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 5px; }
.book-title-text { font-size: 20px; font-weight: 700; color: var(--color-primary); line-height: 1.4; }
.book-author-text { font-size: 14px; color: var(--color-text-secondary); }
.meta-row { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-top: 2px; }
.format-tag { background: rgba(184,140,110,0.12); color: var(--color-accent, #b88c6e); padding: 2px 10px; border-radius: 8px; font-size: 12px; font-weight: 600; }
html.dark .format-tag { background: rgba(212,178,152,0.15); color: #d4b298; }
.meta-size { font-size: 12px; color: var(--color-text-secondary); opacity: 0.6; }
.meta-views { font-size: 12px; color: var(--color-text-secondary); opacity: 0.6; }
.meta-tags { display: flex; gap: 6px; flex-wrap: wrap; }
.tag { background: rgba(184,140,110,0.06); color: var(--color-accent); padding: 1px 8px; border-radius: 10px; font-size: 12px; }
.meta-date { font-size: 12px; color: var(--color-text-secondary); opacity: 0.7; }
.book-desc { margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--color-border); }
.section-label { font-size: 14px; font-weight: 600; color: var(--color-primary); margin-bottom: 6px; position: relative; padding-left: 10px; }
.section-label::before { content: ''; position: absolute; left: 0; top: 2px; bottom: 2px; width: 3px; border-radius: 2px; background: var(--color-accent, #b88c6e); }
.desc-text { font-size: 13px; line-height: 1.8; color: var(--color-text); white-space: pre-wrap; }
.book-cats { margin-top: 12px; }
.cat-tags { display: flex; gap: 6px; flex-wrap: wrap; }
.cat-tag { background: rgba(184,140,110,0.1); border: 1px solid rgba(184,140,110,0.15); color: var(--color-accent, #b88c6e); padding: 2px 8px; border-radius: 8px; font-size: 12px; }
html.dark .cat-tag { background: rgba(212,178,152,0.15); border-color: rgba(212,178,152,0.2); color: #d4b298; }
.no-cat { font-size: 12px; color: var(--color-text-secondary); opacity: 0.5; }
.detail-footer { display: flex; justify-content: space-between; align-items: center; }
.footer-right { display: flex; gap: 8px; align-items: center; }
.footer-right a { text-decoration: none; }
.is-small { height: 28px; padding: 0 10px; font-size: 12px; }
</style>
