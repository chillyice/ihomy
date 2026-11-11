<!-- 图书详情:窄屏弹窗 / 宽屏右栏(inline)两种外壳,内容与按钮逻辑全部在 LibraryDetailBody,
     这里只管取数(详情 + 借阅状态 + 分类)与开关。 -->
<template>
  <el-dialog v-if="!inline" v-model="show" :title="$t('library.detail')" width="640px" append-to-body @close="$emit('close')">
    <LibraryDetailBody
      :book="book"
      :borrow="borrow"
      :categories="categories"
      :loading="loading"
      @set-borrow="setBorrowStatus"
      @read="$emit('read', book)"
      @close="show = false"
    />
  </el-dialog>
  <LibraryDetailBody
    v-else
    class="card"
    :book="book"
    :borrow="borrow"
    :categories="categories"
    :loading="loading"
    @set-borrow="setBorrowStatus"
    @read="$emit('read', book)"
    @close="$emit('close')"
  />
</template>

<script setup>
// 图书详情:展示元信息/分类,并提供借阅状态流转、在线阅读、下载、编辑入口。
// 借阅状态机 WANT_READ → READING → FINISHED;「重读」是状态机之外的分支(FINISHED 时上面三个
// v-else-if 全部落空,单独出这个按钮,点了回到 READING),不是写漏。
import { ref, watch, onMounted } from 'vue'
import { libraryApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import LibraryDetailBody from './LibraryDetailBody.vue'

const props = defineProps({
  bookId: [Number, String],
  // true = 宽屏右栏渲染(不套弹窗);窄屏/未传仍是 640px 弹窗
  inline: { type: Boolean, default: false },
})
const emit = defineEmits(['close', 'updated', 'deleted', 'read'])

const { t } = useI18n()
const userStore = useUserStore()
const show = ref(true)
const book = ref(null)
const borrow = ref(null)
const loading = ref(false)
const categories = ref([])

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
    try {
      borrow.value = await libraryApi.getBorrow(props.bookId)
    } catch (e) {}
  }
  try {
    categories.value = (await libraryApi.categories()) || []
  } catch (e) {}
}

const setBorrowStatus = async (status) => {
  try {
    borrow.value = await libraryApi.updateBorrow(props.bookId, { status })
    ElMessage.success(
      status === 'READING' ? t('library.readingStarted') : status === 'FINISHED' ? t('library.finishedMsg') : t('common.saveSuccess'),
    )
  } catch (e) {}
}

// 弹窗外壳:关闭走 show → close;inline 由 body 的 close 直接抛给父级
watch(show, (v) => {
  if (!v) emit('close')
})

// 宽屏右栏里换一本书:同一实例不重建,按新 bookId 重拉
watch(
  () => props.bookId,
  () => {
    borrow.value = null
    loadAll()
  },
)

onMounted(loadAll)
</script>
