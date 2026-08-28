<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('library.title') }]" />

    <!-- Toolbar -->
    <div class="page-toolbar card">
      <div class="tb-left">
        <el-input v-model="searchKeyword" :placeholder="$t('library.search')" clearable size="small" style="width: 180px" @keyup.enter="load" @clear="load" />
        <el-cascader
          v-model="activeCategoryId"
          :options="categoryCascader"
          :props="{ expandTrigger: 'hover', checkStrictly: true, emitPath: false }"
          :placeholder="$t('library.category')"
          clearable
          size="small"
          style="width: 160px"
          @change="load"
        />
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
        <button v-if="userStore.isLoggedIn" class="cat-add-btn" :title="$t('library.newCategory')" @click="openCatDialog()">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg>
        </button>
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

    <!-- Category Dialog -->
    <el-dialog v-model="catDialog.visible" :title="catDialog.id ? $t('library.editCategory') : $t('library.newCategory')" width="380px" append-to-body>
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
        <el-button type="primary" :disabled="!catDialog.name.trim() || catDialog.saving" :loading="catDialog.saving" @click="saveCategory">{{ $t('common.confirm') }}</el-button>
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

// 扁平分类 → el-cascader 树
const buildCascaderTree = (excludeId = null) => {
  const byParent = {}
  for (const c of categories.value) {
    if (c.id === excludeId) continue
    const pid = c.parentId || 0
    if (!byParent[pid]) byParent[pid] = []
    byParent[pid].push(c)
  }
  const build = (pid) => (byParent[pid] || []).map(c => ({
    value: c.id,
    label: c.name,
    children: build(c.id).length ? build(c.id) : undefined,
  }))
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
        if (c.parentId === pid) { excludeIds.add(c.id); collectDescendants(c.id) }
      }
    }
    collectDescendants(catDialog.id)
  }
  const byParent = {}
  for (const c of categories.value) {
    if (excludeIds.has(c.id)) continue
    const pid = c.parentId || 0
    if (!byParent[pid]) byParent[pid] = []
    byParent[pid].push(c)
  }
  const build = (pid) => (byParent[pid] || []).map(c => ({
    value: c.id,
    label: c.name,
    children: build(c.id).length ? build(c.id) : undefined,
  }))
  return build(0)
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

const onDeleteCategory = async (cat) => {
  try {
    await ElMessageBox.confirm(`${t('library.deleteCategory')}: ${cat.name}? ${t('library.deleteCategoryHint')}`, { type: 'warning', confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel'), closeOnClickModal: true })
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
    await ElMessageBox.confirm(t('library.deleteConfirm'), { type: 'warning', confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel'), closeOnClickModal: true })
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
    await ElMessageBox.confirm(t('library.batchDeleteConfirm', { n: selectedIds.value.length }), { type: 'warning', confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel'), closeOnClickModal: true })
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
/* Toolbar: global .page-toolbar */
.cat-add-btn { width: 28px; height: 28px; border: none; background: transparent; border-radius: 8px; cursor: pointer; display: flex; align-items: center; justify-content: center; color: var(--color-text-secondary); transition: background 0.2s, color 0.2s, transform 0.2s; }
.cat-add-btn:hover { background: rgba(184,140,110,0.1); color: var(--color-accent, #b88c6e); transform: scale(1.1); }
html.dark .cat-add-btn:hover { background: rgba(212,178,152,0.12); color: #d4b298; }
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
  .tb-left { width: 100%; }
}
</style>
