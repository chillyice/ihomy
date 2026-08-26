<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('library.title') }]" />
    <div class="lib-layout">
      <!-- Left: Category Tree -->
      <aside class="cat-panel">
        <div class="cat-head">
          <span class="cat-title">{{ $t('library.category') }}</span>
          <button v-if="userStore.isLoggedIn" class="cat-add-btn" :title="$t('library.newCategory')" @click="openCatDialog()">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg>
          </button>
        </div>
        <div class="cat-tree" v-loading="catLoading">
          <div class="cat-node root" :class="{ active: !activeCategoryId }" @click="selectCategory(null)">
            <span class="cat-icon"><svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg></span>
            <span class="cat-name">{{ $t('library.allCategories') }}</span>
          </div>
          <template v-for="node in categoryTree" :key="node.id">
            <div class="cat-node" :class="{ active: activeCategoryId === node.id }" @click="selectCategory(node.id)">
              <span v-if="node.children?.length" class="cat-toggle" @click.stop="toggleExpand(node.id)">
                <svg viewBox="0 0 24 24" width="10" height="10" fill="none" stroke="currentColor" stroke-width="2.5" :style="{ transform: expanded[node.id] ? 'rotate(90deg)' : '' }"><path d="M9 18l6-6-6-6"/></svg>
              </span>
              <span v-else class="cat-toggle-placeholder"></span>
              <span class="cat-name">{{ node.name }}</span>
              <span v-if="userStore.isLoggedIn" class="cat-ops" @click.stop>
                <button class="cat-op" :title="$t('library.editCategory')" @click="openCatDialog(node)"><svg viewBox="0 0 24 24" width="11" height="11" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg></button>
                <button class="cat-op danger" :title="$t('library.deleteCategory')" @click="onDeleteCategory(node)"><svg viewBox="0 0 24 24" width="11" height="11" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 6h18"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg></button>
              </span>
            </div>
            <template v-if="node.children?.length && expanded[node.id]">
              <div v-for="child in node.children" :key="child.id" class="cat-node child" :class="{ active: activeCategoryId === child.id }" @click="selectCategory(child.id)">
                <span class="cat-toggle-placeholder"></span>
                <span class="cat-name">{{ child.name }}</span>
                <span v-if="userStore.isLoggedIn" class="cat-ops" @click.stop>
                  <button class="cat-op" :title="$t('library.editCategory')" @click="openCatDialog(child)"><svg viewBox="0 0 24 24" width="11" height="11" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg></button>
                  <button class="cat-op danger" :title="$t('library.deleteCategory')" @click="onDeleteCategory(child)"><svg viewBox="0 0 24 24" width="11" height="11" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 6h18"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg></button>
                </span>
              </div>
            </template>
          </template>
        </div>
      </aside>

      <!-- Right: Main Content -->
      <div class="lib-main">
        <!-- Toolbar -->
        <div class="toolbar card">
          <div class="tb-left">
            <el-input v-model="searchKeyword" :placeholder="$t('library.search')" clearable size="small" style="width: 200px" @keyup.enter="load" @clear="load" />
            <el-select v-model="sortBy" size="small" style="width: 120px" @change="load">
              <el-option :label="$t('library.sortRecent')" value="recent" />
              <el-option :label="$t('library.sortCreated')" value="created" />
              <el-option :label="$t('library.sortTitle')" value="title" />
            </el-select>
            <el-select v-model="filterFormat" size="small" style="width: 110px" clearable :placeholder="$t('library.filterFormat')" @change="load">
              <el-option :label="$t('library.allFormats')" value="" />
              <el-option label="EPUB" value="EPUB" />
              <el-option label="PDF" value="PDF" />
              <el-option label="TXT" value="TXT" />
              <el-option label="MOBI" value="MOBI" />
            </el-select>
            <el-select v-model="filterStatus" size="small" style="width: 120px" clearable :placeholder="$t('library.filterStatus')" @change="load">
              <el-option :label="$t('library.allStatuses')" value="" />
              <el-option :label="$t('library.statusUnread')" value="UNREAD" />
              <el-option :label="$t('library.statusReading')" value="READING" />
              <el-option :label="$t('library.statusFinished')" value="FINISHED" />
            </el-select>
          </div>
          <div class="tb-right">
            <div class="view-toggle">
              <button class="vt-btn" :class="{ active: viewMode === 'grid' }" @click="viewMode = 'grid'"><svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/></svg></button>
              <button class="vt-btn" :class="{ active: viewMode === 'list' }" @click="viewMode = 'list'"><svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8"><line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/><line x1="3" y1="6" x2="3.01" y2="6"/><line x1="3" y1="12" x2="3.01" y2="12"/><line x1="3" y1="18" x2="3.01" y2="18"/></svg></button>
            </div>
            <button v-if="userStore.isLoggedIn && !batchMode" class="write-btn" @click="router.push('/library/edit')">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg>
              {{ $t('library.upload') }}
            </button>
            <template v-if="batchMode">
              <button class="ghost-btn" @click="batchMove">
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M22 12H2"/><path d="M5 9l-3 3 3 3"/><path d="M19 9l3 3-3 3"/></svg>
                {{ $t('library.batchMove') }}
              </button>
              <button class="danger-btn" @click="batchDeleteAction">
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 6h18"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/></svg>
                {{ $t('library.batchDelete') }}
              </button>
              <button class="ghost-btn" @click="exitBatch">{{ $t('library.cancelBatch') }}</button>
            </template>
            <button v-if="userStore.isLoggedIn && !batchMode" class="ghost-btn" @click="enterBatch">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>
              {{ $t('library.batchMode') }}
            </button>
          </div>
        </div>

        <!-- Book Grid/List -->
        <div v-loading="loading" class="book-area">
          <!-- Grid View -->
          <div v-if="viewMode === 'grid'" class="book-grid">
            <div v-for="b in list" :key="b.id" class="book-card card" :class="{ selected: selectedIds.includes(b.id) }" @click="onBookClick(b)">
              <div v-if="batchMode" class="book-check" @click.stop="toggleSelect(b.id)">
                <svg v-if="selectedIds.includes(b.id)" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="#fff" stroke-width="3"><path d="M5 12l5 5L20 7"/></svg>
              </div>
              <div class="book-cover-wrap">
                <img v-if="b.coverUrl" :src="b.coverUrl" class="book-cover" />
                <div v-else class="book-cover placeholder">
                  <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.2"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
                </div>
                <span class="format-badge">{{ b.fileFormat }}</span>
                <div v-if="!batchMode" class="book-overlay">
                  <button v-if="canReadOnline(b)" class="ovl-btn primary" @click.stop="openReader(b)">{{ $t('library.read') }}</button>
                  <button class="ovl-btn" @click.stop="openDetail(b)">{{ $t('library.viewDetail') }}</button>
                  <template v-if="userStore.isLoggedIn && canEdit(b)">
                    <button class="ovl-btn" @click.stop="moveCategory(b)">{{ $t('library.moveCategory') }}</button>
                    <button class="ovl-btn danger" @click.stop="onDeleteBook(b)">{{ $t('common.delete') }}</button>
                  </template>
                </div>
              </div>
              <div class="book-info">
                <div class="book-title">{{ b.title }}</div>
                <div v-if="b.author" class="book-author">{{ b.author }}</div>
                <div class="book-meta"><span class="book-views">{{ b.viewCount }} {{ $t('library.views') }}</span></div>
              </div>
            </div>
          </div>

          <!-- List View -->
          <div v-else class="book-list">
            <div v-for="b in list" :key="b.id" class="book-row card" :class="{ selected: selectedIds.includes(b.id) }" @click="onBookClick(b)">
              <div v-if="batchMode" class="book-check" @click.stop="toggleSelect(b.id)">
                <svg v-if="selectedIds.includes(b.id)" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="#fff" stroke-width="3"><path d="M5 12l5 5L20 7"/></svg>
              </div>
              <img v-if="b.coverUrl" :src="b.coverUrl" class="row-cover" />
              <div v-else class="row-cover placeholder"><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.2"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg></div>
              <div class="row-info">
                <div class="row-title">{{ b.title }}</div>
                <div class="row-sub">
                  <span v-if="b.author">{{ b.author }}</span>
                  <span class="format-badge small">{{ b.fileFormat }}</span>
                  <span class="book-views">{{ b.viewCount }} {{ $t('library.views') }}</span>
                </div>
              </div>
              <div v-if="!batchMode" class="row-actions" @click.stop>
                <button v-if="canReadOnline(b)" class="ghost-btn small" @click="openReader(b)">{{ $t('library.read') }}</button>
                <button class="ghost-btn small" @click="openDetail(b)">{{ $t('library.viewDetail') }}</button>
                <button v-if="userStore.isLoggedIn && canEdit(b)" class="ghost-btn small danger" @click="onDeleteBook(b)">{{ $t('common.delete') }}</button>
              </div>
            </div>
          </div>

          <div v-if="!loading && !list.length" class="empty-state">
            <el-empty :description="userStore.isGuest ? $t('library.noPublic') : $t('library.emptyHint')">
              <button v-if="userStore.isLoggedIn" class="write-btn" @click="router.push('/library/edit')">{{ $t('library.upload') }}</button>
            </el-empty>
          </div>
        </div>
      </div>
    </div>

    <!-- Category Dialog -->
    <el-dialog v-model="catDialog.visible" :title="catDialog.id ? $t('library.editCategory') : $t('library.newCategory')" width="360px" append-to-body>
      <el-input v-model="catDialog.name" :placeholder="$t('library.categoryPlaceholder')" @keyup.enter="saveCategory" />
      <div style="margin-top: 12px">
        <el-select v-model="catDialog.parentId" clearable :placeholder="$t('library.parentCategory')" style="width: 100%">
          <el-option :label="$t('library.rootCategory')" :value="0" />
          <el-option v-for="c in flatCategories" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
      </div>
      <template #footer>
        <el-button @click="catDialog.visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :disabled="!catDialog.name.trim() || catDialog.saving" :loading="catDialog.saving" @click="saveCategory">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- Move Category Dialog -->
    <el-dialog v-model="moveDialog.visible" :title="$t('library.moveCategory')" width="380px" append-to-body>
      <el-select v-model="moveDialog.categoryId" clearable :placeholder="$t('library.selectCategory')" style="width: 100%">
        <el-option v-for="c in flatCategories" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <template #footer>
        <el-button @click="moveDialog.visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :disabled="moveDialog.saving" :loading="moveDialog.saving" @click="confirmMove">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- Detail Dialog -->
    <LibraryDetail v-if="detailVisible" :bookId="detailBookId" @close="detailVisible = false" @updated="onBookUpdated" @deleted="onBookDeleted" @read="onReadFromDetail" />

    <!-- Reader Overlay -->
    <LibraryReader v-if="readerVisible" :book="readerBook" @close="readerVisible = false" @statusChanged="load" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { libraryApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb.vue'
import LibraryDetail from './LibraryDetail.vue'
import LibraryReader from './LibraryReader.vue'

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()
const list = ref([])
const loading = ref(false)
const catLoading = ref(false)
const categories = ref([])
const activeCategoryId = ref(null)
const expanded = ref({})
const searchKeyword = ref('')
const sortBy = ref('created')
const filterFormat = ref('')
const filterStatus = ref('')
const viewMode = ref('grid')
const batchMode = ref(false)
const selectedIds = ref([])
const detailVisible = ref(false)
const detailBookId = ref(null)
const readerVisible = ref(false)
const readerBook = ref(null)

const flatCategories = computed(() => {
  const result = []
  for (const c of categories.value) {
    result.push(c)
    if (c.children) result.push(...c.children)
  }
  return result
})

const categoryTree = computed(() => {
  const map = {}
  const roots = []
  for (const c of categories.value) {
    map[c.id] = { ...c, children: [] }
  }
  for (const c of categories.value) {
    if (c.parentId && map[c.parentId]) {
      map[c.parentId].children.push(map[c.id])
    } else {
      roots.push(map[c.id])
    }
  }
  return roots
})

const canEdit = (b) => userStore.isOwner || b.uploaderId === userStore.userInfo?.id
const canReadOnline = (b) => b.fileFormat === 'PDF' || b.fileFormat === 'EPUB' || b.fileFormat === 'TXT'

const load = async () => {
  loading.value = true
  try {
    const params = { current: 1, size: 200 }
    if (activeCategoryId.value) params.categoryId = activeCategoryId.value
    if (searchKeyword.value) params.keyword = searchKeyword.value
    if (filterFormat.value) params.fileFormat = filterFormat.value
    if (filterStatus.value) params.borrowStatus = filterStatus.value
    if (sortBy.value) params.sortBy = sortBy.value
    const data = await libraryApi.list(params)
    list.value = data.records || []
  } finally {
    loading.value = false
  }
}

const loadCategories = async () => {
  catLoading.value = true
  try {
    categories.value = await libraryApi.categories() || []
  } catch (e) {} finally { catLoading.value = false }
}

const selectCategory = (id) => {
  activeCategoryId.value = id
  load()
}

const toggleExpand = (id) => {
  expanded.value[id] = !expanded.value[id]
}

// Category dialog
const catDialog = reactive({ visible: false, id: null, name: '', parentId: 0, saving: false })
const openCatDialog = (cat = null) => {
  catDialog.id = cat?.id || null
  catDialog.name = cat?.name || ''
  catDialog.parentId = cat?.parentId || 0
  catDialog.saving = false
  catDialog.visible = true
}
const saveCategory = async () => {
  const name = catDialog.name.trim()
  if (!name || catDialog.saving) return
  catDialog.saving = true
  try {
    if (catDialog.id) {
      await libraryApi.updateCategory(catDialog.id, name)
    } else {
      await libraryApi.addCategory(name, catDialog.parentId || 0)
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

const onDeleteCategory = async (cat) => {
  try {
    await ElMessageBox.confirm(`${t('library.deleteCategory')}: ${cat.name}? ${t('library.deleteCategoryHint')}`, { type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel') })
    await libraryApi.deleteCategory(cat.id, 'move')
    ElMessage.success(t('common.deleted'))
    if (activeCategoryId.value === cat.id) activeCategoryId.value = null
    await loadCategories()
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || 'Failed')
  }
}

// Book actions
const onBookClick = (b) => {
  if (batchMode.value) {
    toggleSelect(b.id)
  } else {
    openDetail(b)
  }
}

const toggleSelect = (id) => {
  const idx = selectedIds.value.indexOf(id)
  if (idx >= 0) selectedIds.value.splice(idx, 1)
  else selectedIds.value.push(id)
}

const enterBatch = () => {
  batchMode.value = true
  selectedIds.value = []
}
const exitBatch = () => {
  batchMode.value = false
  selectedIds.value = []
}

const openDetail = (b) => {
  detailBookId.value = b.id
  detailVisible.value = true
}

const openReader = (b) => {
  readerBook.value = b
  readerVisible.value = true
}

const onReadFromDetail = (book) => {
  detailVisible.value = false
  readerBook.value = book
  readerVisible.value = true
}

const onBookUpdated = () => { load() }
const onBookDeleted = () => { detailVisible.value = false; load() }

const onDeleteBook = async (b) => {
  try {
    await ElMessageBox.confirm(t('library.deleteConfirm'), { type: 'warning', confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel') })
    await libraryApi.delete(b.id)
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
    await ElMessageBox.confirm(t('library.batchDeleteConfirm', { n: selectedIds.value.length }), { type: 'warning', confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel') })
    await libraryApi.batchDelete(selectedIds.value)
    ElMessage.success(t('common.deleted'))
    exitBatch()
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || 'Failed')
  }
}

onMounted(() => {
  loadCategories()
  load()
})
</script>

<style scoped>
.lib-layout { display: grid; grid-template-columns: 200px 1fr; gap: 16px; }

/* Category Panel */
.cat-panel {
  background: rgba(255,255,255,0.45); backdrop-filter: blur(24px) saturate(1.2); -webkit-backdrop-filter: blur(24px) saturate(1.2);
  border-radius: 14px; box-shadow: 0 2px 12px rgba(58,46,34,0.06); padding: 10px; height: fit-content; position: sticky; top: 42px;
  border: 1px solid rgba(255,255,255,0.4);
}
html.dark .cat-panel { background: rgba(30,42,72,0.45); border-color: rgba(255,255,255,0.08); box-shadow: 0 2px 12px rgba(0,0,0,0.15); }
.cat-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; padding: 0 6px; }
.cat-title { font-size: 13px; font-weight: 600; color: var(--color-text-secondary); }
.cat-add-btn { width: 26px; height: 26px; border: none; background: transparent; border-radius: 8px; cursor: pointer; display: flex; align-items: center; justify-content: center; color: var(--color-text-secondary); transition: background 0.2s, color 0.2s, transform 0.2s; }
.cat-add-btn:hover { background: rgba(184,140,110,0.1); color: var(--color-accent, #b88c6e); transform: scale(1.1); }
html.dark .cat-add-btn:hover { background: rgba(212,178,152,0.12); color: #d4b298; }
.cat-tree { display: flex; flex-direction: column; gap: 2px; max-height: calc(100vh - 200px); overflow-y: auto; }
.cat-node { padding: 7px 8px; border-radius: 8px; cursor: pointer; font-size: 13px; color: var(--color-text); transition: background 0.2s; display: flex; align-items: center; gap: 4px; position: relative; }
.cat-node:hover { background: rgba(184,140,110,0.06); }
html.dark .cat-node:hover { background: rgba(212,178,152,0.08); }
.cat-node.active { background: rgba(184,140,110,0.12); color: var(--color-accent, #b88c6e); font-weight: 600; }
.cat-node.active::before { content: ''; position: absolute; left: 0; top: 5px; bottom: 5px; width: 3px; border-radius: 2px; background: var(--color-accent, #b88c6e); }
html.dark .cat-node.active { background: rgba(212,178,152,0.15); color: #d4b298; }
html.dark .cat-node.active::before { background: #d4b298; }
.cat-node.child { padding-left: 24px; }
.cat-toggle { width: 16px; height: 16px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; color: var(--color-text-secondary); cursor: pointer; }
.cat-toggle svg { transition: transform 0.2s; }
.cat-toggle-placeholder { width: 16px; flex-shrink: 0; }
.cat-icon { width: 16px; flex-shrink: 0; display: flex; align-items: center; justify-content: center; }
.cat-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; }
.cat-ops { display: none; gap: 2px; flex-shrink: 0; }
.cat-node:hover .cat-ops { display: flex; }
.cat-op { width: 22px; height: 22px; border: none; background: transparent; border-radius: 5px; cursor: pointer; display: flex; align-items: center; justify-content: center; color: var(--color-text-secondary); transition: background 0.15s, color 0.15s; }
.cat-op:hover { background: rgba(184,140,110,0.1); color: var(--color-accent, #b88c6e); }
html.dark .cat-op:hover { background: rgba(212,178,152,0.1); color: #d4b298; }
.cat-op.danger:hover { background: rgba(201,116,116,0.1); color: #c97474; }

/* Toolbar */
.lib-main { min-width: 0; display: flex; flex-direction: column; gap: 12px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; padding: 10px 14px; flex-wrap: wrap; gap: 8px; }
.tb-left { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.tb-right { display: flex; gap: 8px; align-items: center; }
.view-toggle { display: flex; gap: 2px; background: rgba(58,46,34,0.06); border-radius: 8px; padding: 2px; }
html.dark .view-toggle { background: rgba(255,255,255,0.06); }
.vt-btn { width: 28px; height: 28px; border: none; background: transparent; border-radius: 6px; cursor: pointer; display: flex; align-items: center; justify-content: center; color: var(--color-text-secondary); transition: background 0.2s, color 0.2s; }
.vt-btn.active { background: rgba(255,255,255,0.8); color: var(--color-accent, #b88c6e); }
html.dark .vt-btn.active { background: rgba(255,255,255,0.15); color: #d4b298; }

.write-btn { height: 32px; padding: 0 14px; border: none; border-radius: 10px; background: #b88c6e; color: #fff; font-size: 13px; font-weight: 500; cursor: pointer; display: inline-flex; align-items: center; gap: 5px; transition: background 0.2s, transform 0.2s; }
.write-btn:hover { background: #a87c5e; transform: translateY(-1px); }
html.dark .write-btn { background: #d4b298; color: #2a2018; }
html.dark .write-btn:hover { background: #e0c2aa; }
.ghost-btn { height: 32px; padding: 0 12px; border: 1px solid var(--color-border); background: transparent; border-radius: 10px; color: var(--color-text); font-size: 13px; cursor: pointer; display: inline-flex; align-items: center; gap: 5px; transition: background 0.2s; }
.ghost-btn:hover { background: rgba(184,140,110,0.06); }
html.dark .ghost-btn:hover { background: rgba(255,255,255,0.06); }
.ghost-btn.small { height: 28px; padding: 0 10px; font-size: 12px; }
.ghost-btn.danger { color: #b04a3a; border-color: rgba(176,74,58,0.2); }
html.dark .ghost-btn.danger { color: #c97474; }
.ghost-btn.danger:hover { background: rgba(176,74,58,0.06); }
html.dark .ghost-btn.danger:hover { background: rgba(201,116,116,0.08); }
.danger-btn { height: 32px; padding: 0 12px; border: none; border-radius: 10px; background: rgba(249,236,234,0.95); color: #b04a3a; font-size: 13px; cursor: pointer; display: inline-flex; align-items: center; gap: 5px; transition: background 0.2s; }
.danger-btn:hover { background: rgba(240,222,219,1); }
html.dark .danger-btn { background: rgba(201,116,116,0.15); color: #c97474; }
html.dark .danger-btn:hover { background: rgba(201,116,116,0.25); }

/* Book Grid */
.book-area { min-height: 200px; }
.book-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 14px; }
.book-card { cursor: pointer; padding: 12px; border-radius: 14px; position: relative; transition: transform 0.25s ease, box-shadow 0.25s ease; display: flex; flex-direction: column; gap: 8px; }
.book-card:hover { transform: translateY(-4px); box-shadow: 0 8px 28px rgba(58,46,34,0.12); }
html.dark .book-card:hover { box-shadow: 0 8px 28px rgba(0,0,0,0.3); }
.book-card.selected { box-shadow: 0 0 0 2px var(--color-accent, #b88c6e); }
.book-check { position: absolute; top: 6px; left: 6px; width: 22px; height: 22px; border-radius: 6px; border: 2px solid rgba(255,255,255,0.6); background: rgba(0,0,0,0.3); z-index: 2; display: flex; align-items: center; justify-content: center; cursor: pointer; }
.book-check:has(svg) { background: var(--color-accent, #b88c6e); border-color: var(--color-accent, #b88c6e); }
html.dark .book-check:has(svg) { background: #d4b298; border-color: #d4b298; }
.book-cover-wrap { position: relative; aspect-ratio: 3/4; border-radius: 8px; overflow: hidden; }
.book-cover { width: 100%; height: 100%; object-fit: cover; }
.book-cover.placeholder { display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, rgba(184,140,110,0.12), rgba(184,140,110,0.04)); color: var(--color-text-secondary); opacity: 0.4; }
html.dark .book-cover.placeholder { background: linear-gradient(135deg, rgba(212,178,152,0.1), rgba(212,178,152,0.03)); }
.format-badge { position: absolute; top: 5px; right: 5px; padding: 2px 6px; border-radius: 4px; background: rgba(0,0,0,0.5); color: #fff; font-size: 10px; font-weight: 600; backdrop-filter: blur(4px); }
.format-badge.small { position: static; font-size: 10px; padding: 1px 5px; }
.book-overlay { position: absolute; inset: 0; background: rgba(0,0,0,0.5); display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; opacity: 0; transition: opacity 0.2s; border-radius: 8px; }
.book-card:hover .book-overlay { opacity: 1; }
.ovl-btn { padding: 4px 12px; border: 1px solid rgba(255,255,255,0.4); background: rgba(255,255,255,0.15); color: #fff; border-radius: 8px; font-size: 12px; cursor: pointer; transition: background 0.2s; backdrop-filter: blur(4px); }
.ovl-btn:hover { background: rgba(255,255,255,0.3); }
.ovl-btn.primary { background: rgba(184,140,110,0.8); border-color: rgba(184,140,110,0.8); }
.ovl-btn.primary:hover { background: rgba(168,124,94,0.9); }
.ovl-btn.danger { border-color: rgba(255,100,100,0.4); }
.ovl-btn.danger:hover { background: rgba(180,60,60,0.5); }
.book-info { display: flex; flex-direction: column; gap: 3px; }
.book-title { font-size: 13px; font-weight: 600; line-height: 1.4; color: var(--color-text); overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.book-author { font-size: 11px; color: var(--color-text-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.book-meta { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
.book-views { font-size: 11px; color: var(--color-text-secondary); opacity: 0.6; }

/* Book List */
.book-list { display: flex; flex-direction: column; gap: 8px; }
.book-row { display: flex; align-items: center; gap: 12px; padding: 10px 14px; border-radius: 12px; cursor: pointer; transition: transform 0.2s, box-shadow 0.2s; }
.book-row:hover { transform: translateY(-2px); box-shadow: 0 4px 16px rgba(58,46,34,0.08); }
html.dark .book-row:hover { box-shadow: 0 4px 16px rgba(0,0,0,0.2); }
.book-row.selected { box-shadow: 0 0 0 2px var(--color-accent, #b88c6e); }
.row-cover { width: 40px; height: 54px; border-radius: 4px; object-fit: cover; flex-shrink: 0; }
.row-cover.placeholder { display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, rgba(184,140,110,0.12), rgba(184,140,110,0.04)); color: var(--color-text-secondary); opacity: 0.3; }
html.dark .row-cover.placeholder { background: linear-gradient(135deg, rgba(212,178,152,0.1), rgba(212,178,152,0.03)); }
.row-info { flex: 1; min-width: 0; }
.row-title { font-size: 14px; font-weight: 600; color: var(--color-text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.row-sub { display: flex; gap: 8px; align-items: center; margin-top: 2px; font-size: 12px; color: var(--color-text-secondary); }
.row-actions { display: flex; gap: 6px; flex-shrink: 0; }

.empty-state { padding: 48px 0; }

@media (max-width: 768px) {
  .lib-layout { grid-template-columns: 1fr; }
  .cat-panel { display: flex; gap: 4px; overflow-x: auto; padding: 8px; }
  .cat-head { display: none; }
  .cat-tree { flex-direction: row; max-height: none; }
  .cat-node { white-space: nowrap; }
  .cat-ops { display: none !important; }
  .cat-node.child { padding-left: 8px; }
  .cat-toggle, .cat-toggle-placeholder { display: none; }
  .book-grid { grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); gap: 10px; }
  .toolbar { padding: 8px; }
  .tb-left { width: 100%; }
}
</style>
