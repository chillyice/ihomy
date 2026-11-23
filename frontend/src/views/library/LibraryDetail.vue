<template>
  <el-dialog
    v-if="!inline"
    :model-value="true"
    :title="$t('library.detail')"
    width="640px"
    append-to-body
    @update:model-value="(open) => !open && $emit('close')"
  >
    <LibraryDetailBody
      :book="book"
      :borrow="borrow"
      :categories="categories"
      :loading="loading"
      :updating="updating"
      @retry="loadAll()"
      @set-borrow="setBorrowStatus"
      @read="$emit('read', book)"
    />
  </el-dialog>
  <div v-else class="card library-preview">
    <div class="library-preview-head paper-heading">
      <span>{{ $t('library.detail') }}</span>
      <el-button :icon="Close" size="small" text :aria-label="$t('common.close')" @click="$emit('close')" />
    </div>
    <LibraryDetailBody
      :book="book"
      :borrow="borrow"
      :categories="categories"
      :loading="loading"
      :updating="updating"
      @retry="loadAll()"
      @set-borrow="setBorrowStatus"
      @read="$emit('read', book)"
    />
  </div>
</template>

<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import { Close } from '@element-plus/icons-vue'
import { libraryApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import LibraryDetailBody from './LibraryDetailBody.vue'

const props = defineProps({
  bookId: [Number, String],
  inline: Boolean,
  categories: { type: Array, default: () => [] },
})
const emit = defineEmits(['close', 'updated', 'read'])

const { t } = useI18n()
const userStore = useUserStore()
const book = ref(null)
const borrow = ref(null)
const loading = ref(true)
const updating = ref(false)
let revision = 0

async function loadAll() {
  const bookId = props.bookId
  const request = ++revision
  loading.value = true
  updating.value = false
  book.value = null
  borrow.value = null
  const [detail, status] = await Promise.allSettled([libraryApi.detail(bookId), userStore.isLoggedIn ? libraryApi.getBorrow(bookId) : null])
  if (request !== revision) return
  book.value = detail.status === 'fulfilled' ? detail.value : null
  borrow.value = status.status === 'fulfilled' ? status.value : null
  loading.value = false
}

async function setBorrowStatus(status) {
  if (updating.value) return
  const request = revision
  updating.value = true
  try {
    const data = await libraryApi.updateBorrow(props.bookId, { status })
    if (request === revision) borrow.value = data
    ElMessage.success(
      status === 'READING' ? t('library.readingStarted') : status === 'FINISHED' ? t('library.finishedMsg') : t('common.saveSuccess'),
    )
    emit('updated')
  } catch {
    // 请求层统一展示错误。
  } finally {
    if (request === revision) updating.value = false
  }
}

watch(() => props.bookId, loadAll, { immediate: true })
onBeforeUnmount(() => {
  revision += 1
})
</script>

<style scoped>
.library-preview {
  padding: 12px;
  min-width: 0;
}
.library-preview-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  color: var(--color-text-secondary);
  font-size: 13px;
}
</style>
