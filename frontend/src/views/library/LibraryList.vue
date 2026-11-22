<template>
  <div class="page library-page">
    <Breadcrumb :items="[{ label: $t('library.title') }]" />

    <PageToolbar>
      <div class="tb-left library-toolbar-search">
        <el-input
          v-model="searchKeyword"
          :placeholder="$t('library.search')"
          :aria-label="$t('library.search')"
          clearable
          data-search-input
          @keyup.enter="applySearch"
          @clear="applySearch"
        >
          <template #append>
            <el-button :icon="Search" :aria-label="$t('common.search')" @click="applySearch" />
          </template>
        </el-input>
      </div>

      <div class="tb-right library-toolbar-actions">
        <el-popover placement="bottom-start" trigger="click" :width="268">
          <template #reference>
            <el-button size="small" :icon="Filter" :type="activeFilterCount ? 'primary' : 'default'" plain>
              {{ $t('library.filters') }}{{ activeFilterCount ? ' · ' + activeFilterCount : '' }}
            </el-button>
          </template>
          <div class="library-filter-fields">
            <label for="library-category">{{ $t('library.category') }}</label>
            <el-cascader
              id="library-category"
              v-model="activeCategoryId"
              :options="categoryCascader"
              :props="{ checkStrictly: true, emitPath: false }"
              :placeholder="$t('library.allCategories')"
              :teleported="false"
              clearable
              @change="goToPage(1)"
            />
            <label for="library-format">{{ $t('library.filterFormat') }}</label>
            <el-select id="library-format" v-model="filterFormat" :teleported="false" @change="goToPage(1)">
              <el-option :label="$t('library.allFormats')" value="" />
              <el-option v-for="format in FORMATS" :key="format" :label="format" :value="format" />
            </el-select>
            <template v-if="userStore.isLoggedIn">
              <label for="library-status">{{ $t('library.filterStatus') }}</label>
              <el-select id="library-status" v-model="filterStatus" :teleported="false" @change="goToPage(1)">
                <el-option :label="$t('library.allStatuses')" value="" />
                <el-option :label="$t('library.statusUnread')" value="UNREAD" />
                <el-option :label="$t('library.statusReading')" value="READING" />
                <el-option :label="$t('library.statusFinished')" value="FINISHED" />
              </el-select>
            </template>
            <label for="library-sort">{{ $t('library.sortBy') }}</label>
            <el-select id="library-sort" v-model="sortBy" :teleported="false" @change="goToPage(1)">
              <el-option v-if="userStore.isLoggedIn" :label="$t('library.sortRecent')" value="recent" />
              <el-option :label="$t('library.sortCreated')" value="created" />
              <el-option :label="$t('library.sortTitle')" value="title" />
            </el-select>
            <el-button v-if="userStore.isLoggedIn" size="small" :icon="Plus" @click="openCatDialog()">
              {{ $t('library.newCategory') }}
            </el-button>
          </div>
        </el-popover>
        <div class="library-view-toggle">
          <button
            v-for="choice in VIEW_OPTIONS"
            :key="choice.mode"
            type="button"
            :aria-label="$t(choice.label)"
            :title="$t(choice.label)"
            :aria-pressed="viewMode === choice.mode"
            @click="viewMode = choice.mode"
          >
            <el-icon><component :is="choice.icon" /></el-icon>
          </button>
        </div>
        <template v-if="!batchMode && userStore.isLoggedIn">
          <el-button size="small" @click="enterBatch">{{ $t('library.batchMode') }}</el-button>
          <el-button size="small" type="primary" :icon="Plus" @click="router.push('/library/edit')">
            {{ $t('library.upload') }}
          </el-button>
        </template>
      </div>
    </PageToolbar>

    <div class="library-summary">
      <span v-if="batchMode" role="status">{{ $t('library.selectedBooks', { n: selectedIds.length }) }}</span>
      <span v-else-if="!loading && !loadError" role="status">{{ $t('library.bookCount', { n: total }) }}</span>
      <el-button v-if="hasQuery" size="small" link @click="clearFilters">{{ $t('library.clearFilters') }}</el-button>
      <div v-if="batchMode" class="library-batch-actions">
        <el-button size="small" :disabled="!selectableBooks.length || loading" @click="selectAllBooks">
          {{ $t('library.selectPage') }}
        </el-button>
        <el-button size="small" :disabled="!selectedIds.length || loading" @click="batchMove">{{ $t('library.batchMove') }}</el-button>
        <el-button size="small" type="danger" plain :disabled="!selectedIds.length || loading" @click="batchDeleteAction">
          {{ $t('library.batchDelete') }}
        </el-button>
        <el-button size="small" @click="exitBatch">{{ $t('library.cancelBatch') }}</el-button>
      </div>
    </div>

    <div ref="wideEl" class="library-cols" :class="{ 'wide-cols': bookWide, 'with-detail': bookWide && detailBookId }" :style="colStyle">
      <div ref="bookArea" v-loading="loading" class="book-area" :class="{ 'wide-col': bookWide }">
        <div v-if="loadError" class="library-empty" role="status">
          <p>{{ $t('library.loadFailed') }}</p>
          <el-button size="small" @click="load()">{{ $t('common.retry') }}</el-button>
        </div>
        <div v-else-if="list.length" class="library-books" :class="'view-' + viewMode">
          <article
            v-for="b in list"
            :key="b.id"
            class="shelf-book"
            :class="{ selected: batchMode ? selectedIds.includes(b.id) : detailBookId === b.id }"
          >
            <button
              type="button"
              class="book-entry"
              :data-book-id="b.id"
              :disabled="batchMode && !canEdit(b)"
              :aria-pressed="batchMode ? selectedIds.includes(b.id) : undefined"
              @click="onBookClick(b)"
              @pointerdown="(event) => startBookPress(event, b)"
              @pointerup="cancelPress"
              @pointercancel="cancelPress"
              @pointerleave="cancelPress"
              @contextmenu.prevent
            >
              <span v-if="batchMode && canEdit(b)" class="pick-badge" :class="{ on: selectedIds.includes(b.id) }" aria-hidden="true">
                <svg viewBox="0 0 16 16" width="11" height="11">
                  <path
                    d="M3 8.5 L6.5 12 L13 4.5"
                    fill="none"
                    stroke="#fff"
                    stroke-width="2.4"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
              </span>
              <span class="book-cover-wrap">
                <img
                  v-if="b.coverUrl && !failedCovers.has(b.coverUrl)"
                  :src="b.coverUrl"
                  class="book-cover"
                  alt=""
                  loading="lazy"
                  draggable="false"
                  @error="failedCovers.add(b.coverUrl)"
                />
                <span v-else class="book-cover book-placeholder" aria-hidden="true">
                  <el-icon><Reading /></el-icon>
                </span>
              </span>
              <span class="book-info">
                <span class="book-title" :title="b.title">{{ b.title }}</span>
                <span v-if="b.author" class="book-author" :title="b.author">{{ b.author }}</span>
                <span class="book-meta">
                  <span v-if="b.fileFormat" class="book-format">{{ b.fileFormat }}</span>
                  <span v-if="b.sharedFrom" class="book-origin" :title="sharedFromText(b.sharedFrom)">{{
                    sharedFromText(b.sharedFrom)
                  }}</span>
                </span>
              </span>
            </button>
            <div v-if="!batchMode" class="book-actions">
              <button v-if="canReadOnline(b)" type="button" class="book-read" @click="openReader(b)">{{ $t('library.read') }}</button>
              <el-dropdown
                v-if="userStore.isLoggedIn && (canEdit(b) || b.visibility !== 'PRIVATE')"
                trigger="click"
                @command="(action) => onBookAction(action, b)"
              >
                <button type="button" class="book-more" :aria-label="$t('library.bookActions', { title: b.title })">
                  <el-icon><MoreFilled /></el-icon>
                </button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-if="b.visibility !== 'PRIVATE'" command="share">{{ $t('chat.share.toChat') }}</el-dropdown-item>
                    <template v-if="canEdit(b)">
                      <el-dropdown-item command="move">{{ $t('library.moveCategory') }}</el-dropdown-item>
                      <el-dropdown-item command="delete" divided>{{ $t('common.delete') }}</el-dropdown-item>
                    </template>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </article>
        </div>
        <div v-else-if="!loading" class="library-empty" role="status">
          <p>{{ hasQuery ? $t('library.noMatches') : userStore.isGuest ? $t('library.noPublic') : $t('library.emptyHint') }}</p>
          <el-button v-if="!hasQuery && userStore.isLoggedIn" size="small" type="primary" @click="router.push('/library/edit')">
            {{ $t('library.upload') }}
          </el-button>
        </div>

        <nav v-if="!loadError && lastPage > 1" class="library-pagination" :aria-label="$t('library.pagination')">
          <el-button size="small" :disabled="page <= 1 || loading" @click="goToPage(page - 1)">{{ $t('library.prevPage') }}</el-button>
          <span>{{ page }} / {{ lastPage }}</span>
          <el-button size="small" :disabled="page >= lastPage || loading" @click="goToPage(page + 1)">{{
            $t('library.nextPage')
          }}</el-button>
        </nav>
      </div>

      <aside v-if="detailBookId" v-show="bookWide" class="wide-col library-detail-col" :aria-label="$t('library.detail')">
        <LibraryDetail
          :inline="bookWide"
          :book-id="detailBookId"
          :categories="categories"
          @close="clearDetail"
          @updated="load()"
          @read="onReadFromDetail"
        />
      </aside>
    </div>

    <!-- Category Dialog -->
    <el-dialog
      v-model="catDialog.visible"
      :title="catDialog.id ? $t('library.editCategory') : $t('library.newCategory')"
      width="380px"
      append-to-body
    >
      <el-form label-position="top">
        <el-form-item :label="$t('library.parentCategory')">
          <el-cascader
            v-model="catDialog.parentId"
            :options="parentCascaderTree"
            :props="{ expandTrigger: 'hover', checkStrictly: true, emitPath: false }"
            :placeholder="$t('library.rootCategory')"
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="$t('library.categoryPlaceholder')">
          <el-input v-model="catDialog.name" @keyup.enter="saveCategory" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="catDialog.visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button
          type="primary"
          :disabled="!catDialog.name.trim() || catDialog.saving"
          :loading="catDialog.saving"
          @click="saveCategory"
          >{{ $t('common.confirm') }}</el-button
        >
      </template>
    </el-dialog>

    <!-- Move Category Dialog -->
    <el-dialog v-model="moveDialog.visible" :title="$t('library.moveCategory')" width="380px" append-to-body>
      <el-cascader
        v-model="moveDialog.categoryId"
        :options="categoryCascader"
        :props="{ expandTrigger: 'hover', checkStrictly: true, emitPath: false }"
        :placeholder="$t('library.selectCategory')"
        clearable
        style="width: 100%"
      />
      <template #footer>
        <el-button @click="moveDialog.visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :disabled="moveDialog.saving" :loading="moveDialog.saving" @click="confirmMove">{{
          $t('common.confirm')
        }}</el-button>
      </template>
    </el-dialog>

    <!-- Reader Overlay -->
    <LibraryReader v-if="readerVisible" :book="readerBook" @close="readerVisible = false" @status-changed="load()" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Filter, Grid, List, MoreFilled, Plus, Reading, Search } from '@element-plus/icons-vue'
import { libraryApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { useDevice } from '@/composables/useDevice'
import { useWideCols } from '@/composables/useWideCols'
import { usePickSelect } from '@/composables/usePickSelect'
import { useViewMode } from '@/composables/useViewMode'
import { shareToChat } from '@/utils/chatShare'
import { ElMessage, ElMessageBox } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import LibraryDetail from './LibraryDetail.vue'
import LibraryReader from './LibraryReader.vue'

const PAGE_SIZE = 48
const READABLE_FORMATS = ['EPUB', 'PDF', 'TXT']
const FORMATS = [...READABLE_FORMATS, 'MOBI']
const VIEW_OPTIONS = [
  { mode: 'grid', label: 'library.gridView', icon: Grid },
  { mode: 'list', label: 'library.listView', icon: List },
]

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const { isWide: viewWide } = useDevice()
const list = ref([])
const page = ref(1)
const total = ref(0)
const loading = ref(true)
const loadError = ref(false)
const categories = ref([])
const activeCategoryId = ref(null)
const searchKeyword = ref('')
const appliedKeyword = ref('')
const sortBy = ref('created')
const filterFormat = ref('')
const filterStatus = ref('')
const viewMode = useViewMode('library')
const detailBookId = ref(null)
const readerVisible = ref(false)
const readerBook = ref(null)
const bookArea = ref(null)
const failedCovers = reactive(new Set())

// 双栏会在 setup 中读取 bookWide,先初始化多选状态。
const {
  selectMode: batchMode,
  selectedIds,
  toggle: toggleSelect,
  enter,
  exit,
  clear: clearSelection,
  selectAll,
  pressStart,
  cancelPress,
  swallowClick,
  dispose,
} = usePickSelect({ enabled: () => userStore.isLoggedIn, onLongPress: clearDetail })
const bookWide = computed(() => viewWide.value && !batchMode.value)
const { wideEl, colStyle, remeasure } = useWideCols(bookWide)
const activeFilterCount = computed(() => [activeCategoryId.value, filterFormat.value, filterStatus.value].filter(Boolean).length)
const hasQuery = computed(() => Boolean(appliedKeyword.value || activeFilterCount.value))
const lastPage = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))

// 扁平分类 → el-cascader 树
const buildCascaderTree = (excludeIds = new Set()) => {
  const byParent = {}
  for (const c of categories.value) {
    if (excludeIds.has(c.id)) continue
    const pid = c.parentId || 0
    if (!byParent[pid]) byParent[pid] = []
    byParent[pid].push(c)
  }
  const build = (pid) =>
    (byParent[pid] || []).map((c) => {
      const children = build(c.id)
      return { value: c.id, label: c.name, children: children.length ? children : undefined }
    })
  return build(0)
}

// 工具栏筛选 + 移动分类用(全部分类)
const categoryCascader = computed(() => buildCascaderTree())

// 编辑分类弹窗用(排除当前分类及其后代,防环)
const parentCascaderTree = computed(() => {
  const excludeIds = new Set()
  if (catDialog.id) {
    excludeIds.add(catDialog.id)
    const collectDescendants = (pid) => {
      for (const c of categories.value) {
        if (c.parentId === pid) {
          excludeIds.add(c.id)
          collectDescendants(c.id)
        }
      }
    }
    collectDescendants(catDialog.id)
  }
  return buildCascaderTree(excludeIds)
})

const canEdit = (b) => userStore.isLoggedIn && !b.readOnly && (userStore.isOwner || b.uploaderId === userStore.userInfo?.id)
const canReadOnline = (b) => Boolean(b.fileUrl) && READABLE_FORMATS.includes(b.fileFormat)
const selectableBooks = computed(() => list.value.filter(canEdit))
// 亲友圈分享进来的图书:标来源家庭(圈内称呼优先)
const sharedFromText = (s) => t('audience.sharedFrom', { name: s.alias || s.familyName || '' })

let listRequest = 0
async function load(current = page.value) {
  const request = ++listRequest
  loading.value = true
  loadError.value = false
  page.value = current
  try {
    const data = await libraryApi.list({
      current,
      size: PAGE_SIZE,
      categoryId: activeCategoryId.value || undefined,
      keyword: appliedKeyword.value || undefined,
      fileFormat: filterFormat.value || undefined,
      borrowStatus: filterStatus.value || undefined,
      sortBy: sortBy.value,
    })
    if (request !== listRequest) return
    if (!Array.isArray(data?.records)) throw new Error('Unexpected library data')
    total.value = Number(data.total) || 0
    if (current > lastPage.value) return load(lastPage.value)
    list.value = data.records
    if (batchMode.value) {
      const editableIds = new Set(selectableBooks.value.map((book) => book.id))
      selectedIds.value = selectedIds.value.filter((id) => editableIds.has(id))
    }
  } catch {
    if (request === listRequest) loadError.value = true
  } finally {
    if (request === listRequest) loading.value = false
  }
}

async function goToPage(current) {
  clearDetail()
  clearSelection()
  const pending = load(current)
  const request = listRequest
  await pending
  if (request !== listRequest || loadError.value || !bookArea.value) return
  bookArea.value.scrollTop = 0
  bookArea.value.scrollIntoView({ block: 'nearest' })
}

function applySearch() {
  appliedKeyword.value = searchKeyword.value.trim()
  goToPage(1)
}

function clearFilters() {
  searchKeyword.value = ''
  appliedKeyword.value = ''
  activeCategoryId.value = null
  filterFormat.value = ''
  filterStatus.value = ''
  goToPage(1)
}

const loadCategories = async () => {
  try {
    categories.value = (await libraryApi.categories()) || []
  } catch {
    // 请求层统一显示加载错误。
  }
}

// Category dialog
const catDialog = reactive({ visible: false, id: null, name: '', parentId: null, saving: false })
const openCatDialog = (cat = null) => {
  catDialog.id = cat?.id || null
  catDialog.name = cat?.name || ''
  catDialog.parentId = cat?.parentId || null
  catDialog.saving = false
  catDialog.visible = true
}
const saveCategory = async () => {
  const name = catDialog.name.trim()
  if (!name || catDialog.saving) return
  catDialog.saving = true
  try {
    if (catDialog.id) {
      await libraryApi.updateCategory(catDialog.id, name, catDialog.parentId)
    } else {
      await libraryApi.addCategory(name, catDialog.parentId)
    }
    ElMessage.success(t('common.saveSuccess'))
    catDialog.visible = false
    await loadCategories()
  } catch (e) {
    ElMessage.error(e.message || 'Failed')
  } finally {
    catDialog.saving = false
  }
}

function startBookPress(event, book) {
  if (event.button === 0 && !loading.value && canEdit(book)) pressStart(book)
}

const onBookClick = (b) => {
  // 长按已选中这本:吞掉松手后的 click,避免又打开详情
  if (swallowClick() || loading.value) return
  if (batchMode.value) {
    if (canEdit(b)) toggleSelect(b.id)
  } else {
    openDetail(b)
  }
}

const enterBatch = () => {
  clearDetail()
  enter()
}
const exitBatch = () => {
  exit()
}
const selectAllBooks = () => selectAll(selectableBooks.value.map((b) => b.id))

const openDetail = async (book) => {
  detailBookId.value = book.id
  if (!bookWide.value) return
  await nextTick()
  if (detailBookId.value === book.id) {
    bookArea.value?.querySelector(`[data-book-id="${CSS.escape(String(book.id))}"]`)?.scrollIntoView({ block: 'nearest' })
  }
}

function clearDetail() {
  detailBookId.value = null
}

const openReader = (b) => {
  readerBook.value = b
  readerVisible.value = true
}

// 分享图书到聊天室:封面/作者快照,深链回书架并直接打开阅读器
const shareBookToChat = (b) => {
  if (!b?.id) return
  shareToChat({
    objType: 'BOOK',
    objId: b.id,
    title: b.title,
    sub: b.author || '',
    url: `/library?book=${b.id}`,
    cover: b.coverUrl || '',
  })
}

const onReadFromDetail = (book) => {
  if (!bookWide.value) clearDetail()
  openReader(book)
}

const onBookAction = (action, book) => {
  if (action === 'share') shareBookToChat(book)
  if (action === 'move') moveCategory(book)
  if (action === 'delete') onDeleteBook(book)
}

const onDeleteBook = async (b) => {
  try {
    await ElMessageBox.confirm(t('library.deleteConfirm'), {
      type: 'warning',
      confirmButtonText: t('common.delete'),
      cancelButtonText: t('common.cancel'),
      closeOnClickModal: true,
    })
    await libraryApi.delete(b.id)
    if (detailBookId.value === b.id) clearDetail()
    ElMessage.success(t('common.deleted'))
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || 'Failed')
  }
}

// Move category dialog
const moveDialog = reactive({ visible: false, categoryId: null, bookIds: [], saving: false })
const moveCategory = (b) => {
  moveDialog.bookIds = [b.id]
  moveDialog.categoryId = null
  moveDialog.visible = true
}
const batchMove = () => {
  if (!selectedIds.value.length) return ElMessage.warning(t('library.selectCategory'))
  moveDialog.bookIds = [...selectedIds.value]
  moveDialog.categoryId = null
  moveDialog.visible = true
}
const confirmMove = async () => {
  if (moveDialog.saving) return
  moveDialog.saving = true
  try {
    await libraryApi.batchMove(moveDialog.bookIds, moveDialog.categoryId)
    ElMessage.success(t('common.saveSuccess'))
    moveDialog.visible = false
    if (batchMode.value) exitBatch()
    await load()
  } catch (e) {
    ElMessage.error(e.message || 'Failed')
  } finally {
    moveDialog.saving = false
  }
}

const batchDeleteAction = async () => {
  if (!selectedIds.value.length) return
  try {
    await ElMessageBox.confirm(t('library.batchDeleteConfirm', { n: selectedIds.value.length }), {
      type: 'warning',
      confirmButtonText: t('common.delete'),
      cancelButtonText: t('common.cancel'),
      closeOnClickModal: true,
    })
    const failed = await libraryApi.batchDelete(selectedIds.value)
    if (Array.isArray(failed) && failed.length) {
      ElMessage.warning(t('library.batchDeletePartial', { fail: failed.length }))
    } else {
      ElMessage.success(t('common.deleted'))
    }
    exitBatch()
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || 'Failed')
  }
}

onBeforeUnmount(() => {
  listRequest += 1
  dispose()
})

onMounted(async () => {
  await Promise.all([loadCategories(), load()])
  remeasure()
  // 聊天图书卡深链:?book=<id> 直接打开阅读器(卡片点击落书架后无动作断裂);
  // 直查详情,不受书架当前筛选影响
  const bookId = Number(route.query.book)
  if (bookId > 0) {
    try {
      const b = await libraryApi.detail(bookId)
      if (b && b.visibility !== 'PRIVATE' && canReadOnline(b)) {
        // 宽屏进页直接把书选进右栏;窄屏沿用旧行为(直接打开全屏阅读器)
        if (bookWide.value) detailBookId.value = bookId
        else openReader(b)
      }
    } catch {
      /* 书已删/无权限:落书架空态即可 */
    }
  }
})
</script>

<style scoped>
.library-page {
  --shelf-hover: rgba(var(--color-brand-rgb), 0.09);
}
.library-toolbar-search {
  flex: 1 1 220px;
  min-width: min(100%, 180px);
  max-width: 360px;
}
.library-toolbar-actions {
  flex-wrap: wrap;
}
.library-toolbar-actions .el-button + .el-button,
.library-batch-actions .el-button + .el-button {
  margin-inline-start: 0;
}
.library-filter-fields {
  display: grid;
  gap: 6px;
}
.library-filter-fields label:not(:first-child) {
  margin-top: 4px;
}
.library-filter-fields > .el-button {
  justify-self: end;
  margin-top: 6px;
}
.library-view-toggle {
  display: flex;
  padding: 2px;
  gap: 2px;
  border: 1px solid var(--color-border);
  border-radius: 10px;
}
.library-view-toggle button {
  display: grid;
  place-items: center;
  width: 32px;
  height: 28px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
}
.library-view-toggle button:hover {
  background: var(--shelf-hover);
  color: var(--color-text-primary);
}
.library-view-toggle button[aria-pressed='true'] {
  background: var(--leaf-wash);
  color: var(--leaf-ink);
}
.library-view-toggle .el-icon {
  font-size: 16px;
}
.library-summary,
.library-batch-actions,
.library-pagination {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px 10px;
}
.library-summary {
  min-height: 32px;
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.library-summary > .el-button {
  margin: 0;
}
.library-batch-actions {
  margin-inline-start: auto;
}

.library-cols.wide-cols.with-detail {
  display: grid;
  grid-template-columns: minmax(0, 1fr) clamp(244px, 38%, 340px);
  gap: 14px;
}
.library-detail-col {
  min-width: 0;
}
.book-area {
  min-width: 0;
  min-height: 160px;
  padding: 2px;
}
.library-books {
  display: grid;
  gap: 12px;
}
.library-books.view-grid {
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 128px), 1fr));
}
.shelf-book {
  position: relative;
  display: flex;
  flex-direction: column;
  min-width: 0;
  border: 1px solid transparent;
  border-radius: 12px;
}
.shelf-book:hover,
.shelf-book:focus-within {
  background: var(--shelf-hover);
}
.shelf-book.selected {
  background: var(--leaf-wash);
  border-color: var(--leaf-accent);
}
.book-entry {
  position: relative;
  display: grid;
  gap: 8px;
  align-content: start;
  flex: 1;
  width: 100%;
  min-width: 0;
  padding: 7px;
  border: 0;
  border-radius: inherit;
  background: transparent;
  color: var(--color-text-primary);
  font: inherit;
  text-align: start;
  cursor: pointer;
}
.book-entry:disabled {
  opacity: 0.46;
  cursor: default;
}
.book-cover-wrap {
  position: relative;
  display: block;
  min-width: 0;
  aspect-ratio: 2 / 3;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: 4px 8px 8px 4px;
  background: var(--color-card);
}
.book-cover-wrap::after {
  position: absolute;
  inset: 0 auto 0 0;
  width: 5px;
  background: linear-gradient(to right, rgba(0, 0, 0, 0.15), transparent);
  content: '';
  pointer-events: none;
}
.book-cover {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.book-placeholder {
  display: grid;
  place-items: center;
  background: rgba(var(--color-brand-rgb), 0.12);
  color: var(--color-text-secondary);
  font-size: 34px;
}
.book-info {
  display: grid;
  align-content: center;
  gap: 2px;
  min-width: 0;
}
.book-title {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow-wrap: anywhere;
  font-size: 14px;
  font-weight: 700;
  line-height: 1.35;
}
.book-author,
.book-origin {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.book-author,
.book-meta {
  color: var(--color-text-secondary);
  font-size: 12px;
}
.book-meta {
  display: flex;
  align-items: center;
  gap: 6px;
}
.book-format {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.02em;
}
.book-actions {
  display: flex;
  align-items: center;
  flex: none;
  gap: 2px;
  padding: 0 4px 3px;
}
.book-actions > .el-dropdown {
  margin-inline-start: auto;
}
.book-read,
.book-more {
  min-height: 32px;
  padding: 4px 8px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text-primary);
  font: inherit;
  font-size: 13px;
  white-space: nowrap;
  cursor: pointer;
}
.book-read {
  font-weight: 600;
}
.book-more {
  display: grid;
  place-items: center;
  min-width: 32px;
  font-size: 15px;
}
.book-read:hover,
.book-more:hover {
  background: var(--shelf-hover);
}
.view-list {
  gap: 2px;
}
.view-list .shelf-book {
  flex-direction: row;
  align-items: center;
  border-bottom-color: var(--color-border);
}
.view-list .shelf-book.selected {
  border-bottom-color: var(--leaf-accent);
}
.view-list .book-entry {
  grid-template-columns: 42px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
}
.view-list .book-placeholder {
  font-size: 18px;
}
.view-list .book-actions {
  padding: 0 5px 0 0;
}
.library-pagination {
  padding-inline: 7px;
  margin: 16px 0 8px;
  color: var(--color-text-secondary);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}
.library-empty {
  display: grid;
  justify-items: center;
  align-content: center;
  gap: 10px;
  min-height: 160px;
  padding: 12px;
  color: var(--color-text-secondary);
  text-align: center;
}
.library-empty p {
  margin: 0;
}
@media (max-width: 600px) {
  .library-toolbar-search {
    max-width: none;
  }
  .library-toolbar-actions {
    justify-content: flex-start;
    margin-inline-start: 0;
  }
  .library-books.view-grid {
    grid-template-columns: repeat(auto-fill, minmax(min(100%, 120px), 1fr));
    gap: 8px;
  }
  .book-read,
  .book-more {
    min-height: 36px;
  }
  .book-more {
    min-width: 36px;
  }
}
</style>
