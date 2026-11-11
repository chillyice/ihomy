<!-- 图书详情内容(元信息/借阅状态/阅读/下载/编辑):弹窗与宽屏右栏渲染同一份,
     一份数据源、一份按钮逻辑,不复制模板。 -->
<template>
  <div class="detail-panel">
    <div v-loading="loading" class="detail-body">
      <div v-if="book" class="book-header">
        <div class="cover-wrap">
          <img v-if="book.coverUrl" :src="book.coverUrl" class="book-cover" :alt="book.title || ''" />
          <div v-else class="book-cover placeholder">
            <svg viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="currentColor" stroke-width="1">
              <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
              <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
            </svg>
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
    <div v-if="book" class="detail-footer">
      <div class="footer-left">
        <template v-if="userStore.isLoggedIn">
          <el-button v-if="borrow?.status === 'WANT_READ'" size="small" @click="$emit('set-borrow', 'READING')">{{
            $t('library.startReading')
          }}</el-button>
          <el-button v-else-if="borrow?.status === 'READING'" size="small" @click="$emit('set-borrow', 'FINISHED')">{{
            $t('library.markFinished')
          }}</el-button>
          <el-button v-else-if="!borrow" size="small" @click="$emit('set-borrow', 'WANT_READ')">{{ $t('library.wantRead') }}</el-button>
          <el-button v-if="borrow?.status === 'FINISHED'" size="small" @click="$emit('set-borrow', 'READING')">{{
            $t('library.reread')
          }}</el-button>
        </template>
      </div>
      <div class="footer-right">
        <el-button v-if="canReadOnline" type="primary" size="small" @click="$emit('read', book)">{{ $t('library.readOnline') }}</el-button>
        <a v-if="book.fileUrl" :href="book.fileUrl" :download="book.title" class="el-button is-default is-small">{{
          $t('library.download')
        }}</a>
        <el-button v-if="canEdit" size="small" @click="router.push(`/library/edit/${book.id}`)">{{ $t('library.editBook') }}</el-button>
        <el-button size="small" @click="$emit('close')">{{ $t('common.close') }}</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { formatDate } from '@/utils/datetime'

const props = defineProps({
  book: { type: Object, default: null },
  borrow: { type: Object, default: null },
  categories: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
})
defineEmits(['set-borrow', 'read', 'close'])

const router = useRouter()
const userStore = useUserStore()

const canEdit = computed(() => !!props.book && (userStore.isOwner || props.book.uploaderId === userStore.userInfo?.id))
// 在线阅读只支持 PDF/EPUB/TXT(与 LibraryReader 的三种渲染一致);
// 上传时 accept 含 .mobi,MOBI 可入库/下载但这里不给在线阅读入口——两处格式清单需一起改
const canReadOnline = computed(() => !!props.book && ['PDF', 'EPUB', 'TXT'].includes(props.book.fileFormat))

const catName = (id) => props.categories.find((c) => c.id === id)?.name || ''

const formatSize = (bytes) => {
  if (!bytes) return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}
</script>

<style scoped>
.detail-panel {
  min-width: 0;
}
.detail-body {
  min-height: 200px;
}
.book-header {
  display: flex;
  gap: 20px;
  margin-bottom: 16px;
}
.cover-wrap {
  width: 120px;
  flex-shrink: 0;
}
.book-cover {
  width: 100%;
  aspect-ratio: 3/4;
  object-fit: cover;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}
.book-cover.placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, rgba(var(--color-brand-rgb), 0.12), rgba(var(--color-brand-rgb), 0.04));
  color: var(--color-text-secondary);
  opacity: 0.4;
  border-radius: 8px;
}
html.dark .book-cover.placeholder {
  background: linear-gradient(135deg, rgba(var(--color-brand-rgb), 0.1), rgba(var(--color-brand-rgb), 0.03));
}
.meta-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.book-title-text {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-primary);
  line-height: 1.4;
}
.book-author-text {
  font-size: 14px;
  color: var(--color-text-secondary);
}
.meta-row {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  margin-top: 2px;
}
.format-tag {
  background: rgba(var(--color-brand-rgb), 0.12);
  color: var(--color-accent, var(--color-brand));
  padding: 2px 10px;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 600;
}
html.dark .format-tag {
  background: rgba(var(--color-brand-rgb), 0.15);
  color: var(--color-brand);
}
.meta-size {
  font-size: 12px;
  color: var(--color-text-secondary);
  opacity: 0.6;
}
.meta-views {
  font-size: 12px;
  color: var(--color-text-secondary);
  opacity: 0.6;
}
.meta-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.tag {
  background: rgba(var(--color-brand-rgb), 0.06);
  color: var(--color-accent);
  padding: 1px 8px;
  border-radius: 10px;
  font-size: 12px;
}
.meta-date {
  font-size: 12px;
  color: var(--color-text-secondary);
  opacity: 0.7;
}
.book-desc {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border);
}
.section-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-primary);
  margin-bottom: 6px;
  position: relative;
  padding-left: 10px;
}
.section-label::before {
  content: '';
  position: absolute;
  left: 0;
  top: 2px;
  bottom: 2px;
  width: 3px;
  border-radius: 2px;
  background: var(--color-accent, var(--color-brand));
}
.desc-text {
  font-size: 13px;
  line-height: 1.8;
  color: var(--color-text);
  white-space: pre-wrap;
}
.book-cats {
  margin-top: 12px;
}
.cat-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.cat-tag {
  background: rgba(var(--color-brand-rgb), 0.1);
  border: 1px solid rgba(var(--color-brand-rgb), 0.15);
  color: var(--color-accent, var(--color-brand));
  padding: 2px 8px;
  border-radius: 8px;
  font-size: 12px;
}
html.dark .cat-tag {
  background: rgba(var(--color-brand-rgb), 0.15);
  border-color: rgba(var(--color-brand-rgb), 0.2);
  color: var(--color-brand);
}
.no-cat {
  font-size: 12px;
  color: var(--color-text-secondary);
  opacity: 0.5;
}
/* 宽屏右栏只有 380px:两组按钮各自换行,不让「在线阅读/下载/编辑/关闭」被挤爆 */
.detail-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border);
}
.footer-right {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.footer-right a {
  text-decoration: none;
}
.is-small {
  height: 28px;
  padding: 0 10px;
  font-size: 12px;
}
</style>
