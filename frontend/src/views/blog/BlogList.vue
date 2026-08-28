<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('blog.title') }]" />

    <!-- 顶部工具栏 -->
    <div class="page-toolbar card">
      <div class="tb-left">
        <el-input
          v-model="searchKeyword"
          :placeholder="$t('blog.searchKeyword')"
          clearable
          size="small"
          style="width: 220px"
          @keyup.enter="load"
          @clear="load"
        >
          <template #prefix>
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/></svg>
          </template>
        </el-input>

        <!-- 小屏分类级联 -->
        <el-cascader
          v-if="!showSidePanel && (catCountRaw.length || userStore.isLoggedIn)"
          v-model="activeCategory"
          :options="categoryCascaderOptions"
          :props="{ expandTrigger: 'hover', checkStrictly: true, emitPath: false }"
          :placeholder="$t('blog.allCategories')"
          clearable
          filterable
          size="small"
          style="width: 160px"
          @change="load"
        />

        <!-- 标签筛选(可输入搜索) -->
        <el-select
          v-if="allTags.length"
          v-model="activeTag"
          :placeholder="$t('blog.tags')"
          clearable
          filterable
          size="small"
          style="width: 140px"
        >
          <el-option v-for="t in allTags" :key="t" :label="'#' + t" :value="t" />
        </el-select>

        <!-- 排序图标 -->
        <el-tooltip :content="sortBy === 'recent' ? $t('blog.sortRecent') : $t('blog.sortViews')" placement="top">
          <button class="sort-icon-btn" @click="toggleSort">
            <svg viewBox="0 0 24 24" width="10" height="10" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="M5 9l7 7 7-7"/></svg>
            <span class="sort-label">{{ sortBy === 'recent' ? 'NEW' : 'MOST' }}</span>
          </button>
        </el-tooltip>

        <!-- 筛选后计数 -->
        <span class="blog-stats">{{ filteredList.length }} {{ $t('blog.articlesUnit') }}</span>
      </div>

      <div class="tb-right">
        <button v-if="!showSidePanel && userStore.isLoggedIn" class="cat-mgr-btn" :title="$t('blog.manageCategory')" @click="openCategoryDialog('add')">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg>
        </button>
        <button v-if="userStore.isLoggedIn" class="write-btn" @click="router.push('/blog/edit')">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/></svg>
          {{ $t('blog.newPost') }}
        </button>
      </div>
    </div>

    <div class="blog-layout" :class="{ 'no-side': !showSidePanel }">
      <!-- 大屏左侧分类面板 -->
      <aside v-if="showSidePanel && (catCountRaw.length || userStore.isLoggedIn)" class="category-side">
        <div class="side-head">
          <span class="side-title">{{ $t('blog.category') }}</span>
          <button v-if="userStore.isLoggedIn" class="side-add-btn" :title="$t('blog.newCategory')" @click="openCategoryDialog('add')">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg>
          </button>
        </div>
        <div class="cat-list">
          <div class="cat-item" :class="{ active: !activeCategory }" @click="setCategory('')">
            <span class="cat-name">{{ $t('blog.allCategories') }}</span>
            <span class="cat-count">{{ totalCatCount }}</span>
          </div>
          <div
            v-for="node in flatTree"
            :key="node.id"
            class="cat-item"
            :class="{ active: activeCategory === node.path }"
            :style="{ paddingLeft: (10 + node.depth * 16) + 'px' }"
            @click="setCategory(node.path)"
          >
            <span v-if="node.childCount > 0" class="cat-toggle" @click.stop="toggleExpand(node.path)">
              <svg viewBox="0 0 24 24" width="10" height="10" fill="none" stroke="currentColor" stroke-width="2.5" :style="{ transform: expanded[node.path] ? 'rotate(90deg)' : '' }"><path d="M9 18l6-6-6-6"/></svg>
            </span>
            <span v-else class="cat-toggle-placeholder"></span>
            <span class="cat-name">{{ node.name }}</span>
            <span class="cat-count">{{ countWithChildren(node) }}</span>
            <span v-if="userStore.isLoggedIn" class="cat-ops" @click.stop>
              <button class="cat-op-btn" :title="$t('blog.editCategory')" @click="openCategoryDialog('edit', node)">
                <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
              </button>
              <button class="cat-op-btn danger" :title="$t('blog.deleteCategory')" @click="openDeleteCategory(node)">
                <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
              </button>
            </span>
          </div>
        </div>
      </aside>

      <!-- 博客列表 -->
      <div v-loading="loading" class="blog-main">
        <div v-for="b in filteredList" :key="b.id" class="blog-item card" @click="router.push(`/blog/${b.id}`)">
          <img v-if="b.coverImage" :src="thumbUrl(b.coverImage)" class="blog-cover" />
          <div class="blog-info">
            <!-- 第一行:标题+草稿标记 -->
            <div class="blog-title-row">
              <span class="blog-title">{{ b.title }}</span>
              <span v-if="b.status === 'DRAFT'" class="draft-badge">{{ $t('blog.draft') }}</span>
            </div>
            <!-- 第二行:内容摘要 -->
            <p class="blog-summary">{{ getSummary(b) }}</p>
            <!-- 第三行:分类+标签+元数据合并 -->
            <div class="blog-footer">
              <div class="blog-sub">
                <span v-if="b.category" class="blog-cat">{{ b.category }}</span>
                <span v-if="b.tags" class="blog-tags">
                  <span
                    v-for="tg in String(b.tags).split(',').filter(Boolean)"
                    :key="tg"
                    class="tag"
                    @click.stop="filterByTag(tg)"
                  >#{{ tg }}</span>
                </span>
              </div>
              <span class="blog-meta">{{ b.viewCount }} {{ $t('blog.views') }} · {{ formatDate(b.createdAt) }}</span>
            </div>
          </div>
          <div v-if="userStore.isLoggedIn && canEdit(b)" class="blog-actions" @click.stop>
            <button class="action-btn edit" :title="$t('blog.editPost')" @click="router.push(`/blog/edit/${b.id}`)">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
            </button>
            <button class="action-btn danger" :title="$t('common.delete')" @click="onBlogDelete(b)">
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
            </button>
          </div>
        </div>
        <div v-if="!loading && !filteredList.length" class="empty-state">
          <el-empty :description="activeCategory ? $t('blog.noPostsInCategory', { cat: activeCategory }) : (userStore.isGuest ? $t('blog.noPublicBlog') : $t('blog.firstHint'))">
            <button v-if="userStore.isLoggedIn" class="write-btn" @click="router.push('/blog/edit')">{{ $t('blog.emptyWriteBtn') }}</button>
          </el-empty>
        </div>
      </div>
    </div>

    <!-- 分类弹窗 -->
    <el-dialog v-model="catDialog.visible" :title="catDialog.mode === 'add' ? $t('blog.newCategory') : $t('blog.editCategory')" width="360px" append-to-body>
      <el-form label-position="top">
        <el-form-item :label="$t('blog.parentCategory')">
          <el-cascader
            v-model="catDialog.parentId"
            :options="parentCategoryTree"
            :props="{ expandTrigger: 'hover', checkStrictly: true, emitPath: false }"
            :placeholder="$t('blog.rootCategory')"
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="$t('blog.categoryNamePlaceholder')">
          <el-input v-model="catDialog.name" @keyup.enter="saveCategory" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="catDialog.visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :disabled="!catDialog.name.trim() || catDialog.saving" :loading="catDialog.saving" @click="saveCategory">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 删除分类弹窗 -->
    <el-dialog v-model="delCatDialog.visible" :title="$t('blog.deleteCategory')" width="380px" append-to-body>
      <p class="del-cat-name">{{ delCatDialog.name }}</p>
      <p v-if="delCatDialog.blogCount > 0" class="del-cat-hint">{{ $t('blog.deleteCategoryHint') }}</p>
      <el-radio-group v-if="delCatDialog.blogCount > 0" v-model="delCatDialog.mode" class="del-cat-radios">
        <el-radio value="move">{{ $t('blog.moveToAll') }} ({{ delCatDialog.blogCount }})</el-radio>
        <el-radio value="delete">{{ $t('blog.deleteBlogs') }} ({{ delCatDialog.blogCount }})</el-radio>
      </el-radio-group>
      <p v-else class="del-cat-hint">{{ $t('common.confirm') }}{{ $t('blog.deleteCategory') }}?</p>
      <template #footer>
        <el-button @click="delCatDialog.visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="danger" :loading="delCatDialog.saving" @click="confirmDeleteCategory">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { blogApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb.vue'
import { thumbUrl } from '@/utils/image'

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()
const list = ref([])
const catCountRaw = ref([])
const activeCategory = ref('')
const activeTag = ref('')
const searchKeyword = ref('')
const sortBy = ref('recent')
const loading = ref(false)
const expanded = ref({})

const formatDate = (d) => (d ? new Date(d).toLocaleDateString('zh-CN') : '')
const canEdit = (b) => userStore.isOwner || b.authorId === userStore.userInfo?.id

const showSidePanel = ref(window.innerWidth >= 1400)
const onResize = () => { showSidePanel.value = window.innerWidth >= 1400 }
window.addEventListener('resize', onResize)
onUnmounted(() => window.removeEventListener('resize', onResize))

const getSummary = (b) => {
  if (b.summary) return b.summary
  if (!b.content) return ''
  const text = b.content.replace(/[#*`>\-_\[\]\(\)!]/g, '').replace(/\n+/g, ' ').trim()
  return text.slice(0, 80) + (text.length > 80 ? '...' : '')
}

const allTags = computed(() => {
  const set = new Set()
  for (const b of list.value) {
    if (b.tags) String(b.tags).split(',').filter(Boolean).forEach(tg => set.add(tg))
  }
  return [...set]
})

const filteredList = computed(() => {
  let arr = list.value
  if (activeTag.value) {
    arr = arr.filter(b => b.tags && String(b.tags).split(',').filter(Boolean).includes(activeTag.value))
  }
  if (sortBy.value === 'views') {
    arr = [...arr].sort((a, b) => (b.viewCount || 0) - (a.viewCount || 0))
  }
  return arr
})

// 后端返回扁平分类树(含 id/name/parentId/path/depth/childCount),前端直接用
const totalCatCount = computed(() => catCountRaw.value.reduce((sum, item) => sum + Number(item.cnt || 0), 0))

// 工具栏分类级联(value=path,checkStrictly 单选任意层级)
const categoryCascaderOptions = computed(() => {
  const byParent = {}
  for (const item of catCountRaw.value) {
    const pid = item.parentId || 0
    if (!byParent[pid]) byParent[pid] = []
    byParent[pid].push(item)
  }
  const build = (parentId) => (byParent[parentId] || []).map(item => ({
    value: item.path,
    label: item.name,
    children: item.childCount > 0 ? build(item.id) : undefined,
  }))
  return build(0)
})

const toggleSort = () => {
  sortBy.value = sortBy.value === 'recent' ? 'views' : 'recent'
  load()
}

// 父分类级联树(el-cascader options 格式);编辑时排除当前分类及其后代(防环)
const parentCategoryTree = computed(() => {
  const excludeIds = new Set()
  if (catDialog.mode === 'edit' && catDialog.id) {
    excludeIds.add(catDialog.id)
    const collectDescendants = (pid) => {
      for (const item of catCountRaw.value) {
        if (item.parentId === pid) { excludeIds.add(item.id); collectDescendants(item.id) }
      }
    }
    collectDescendants(catDialog.id)
  }
  const byParent = {}
  for (const item of catCountRaw.value) {
    if (excludeIds.has(item.id)) continue
    const pid = item.parentId || 0
    if (!byParent[pid]) byParent[pid] = []
    byParent[pid].push(item)
  }
  const build = (parentId) => (byParent[parentId] || []).map(item => ({
    value: item.id,
    label: item.name,
    children: item.childCount > 0 ? build(item.id) : undefined,
  }))
  return build(0)
})

// 扁平化分类(含depth),用于侧边栏渲染;折叠的节点隐藏子级
const flatTree = computed(() => {
  const arr = []
  const byParent = {}
  for (const item of catCountRaw.value) {
    const pid = item.parentId || 0
    if (!byParent[pid]) byParent[pid] = []
    byParent[pid].push(item)
  }
  const walk = (parentId, depth) => {
    for (const item of (byParent[parentId] || [])) {
      arr.push({ ...item, depth })
      if (item.childCount > 0 && expanded.value[item.path]) walk(item.id, depth + 1)
    }
  }
  walk(0, 0)
  return arr
})

// 累加子分类 count
const countWithChildren = (item) => {
  let sum = Number(item.cnt || 0)
  for (const child of catCountRaw.value) {
    if (child.parentId === item.id) sum += countWithChildren(child)
  }
  return sum
}

const load = async () => {
  loading.value = true
  try {
    const params = { current: 1, size: 50 }
    if (activeCategory.value) params.category = activeCategory.value
    if (searchKeyword.value) params.keyword = searchKeyword.value
    const data = await blogApi.list(params)
    list.value = data.records || []
  } finally {
    loading.value = false
  }
}

const loadCategoryCounts = async () => {
  try {
    catCountRaw.value = await blogApi.categoryCounts() || []
  } catch (e) {}
}

const setCategory = (c) => {
  activeCategory.value = c
  load()
}

const filterByTag = (tg) => {
  activeTag.value = activeTag.value === tg ? '' : tg
}

const toggleExpand = (path) => {
  expanded.value[path] = !expanded.value[path]
}

const catDialog = reactive({ visible: false, mode: 'add', id: null, name: '', parentId: null, oldPath: '', saving: false })
const openCategoryDialog = (mode, node = null) => {
  catDialog.mode = mode
  if (mode === 'edit' && node) {
    catDialog.id = node.id
    catDialog.name = node.name
    catDialog.parentId = node.parentId
    catDialog.oldPath = node.path
  } else {
    catDialog.id = null
    catDialog.name = ''
    catDialog.parentId = null
    catDialog.oldPath = ''
  }
  catDialog.visible = true
}
const saveCategory = async () => {
  const partName = catDialog.name.trim()
  if (!partName || catDialog.saving) return
  catDialog.saving = true
  try {
    if (catDialog.mode === 'add') {
      await blogApi.addCategory(partName, catDialog.parentId)
    } else {
      await blogApi.renameCategory(catDialog.id, partName, catDialog.parentId)
    }
    ElMessage.success(t('common.saveSuccess'))
    catDialog.visible = false
    await loadCategoryCounts()
    if (catDialog.mode === 'edit' && activeCategory.value === catDialog.oldPath) {
      // 选中态保持,路径可能变了,loadCategoryCounts 后路径已更新
      load()
    }
  } catch (e) {
    ElMessage.error(e.message || 'Failed')
  } finally {
    catDialog.saving = false
  }
}

const delCatDialog = reactive({ visible: false, id: null, name: '', path: '', blogCount: 0, mode: 'move', saving: false })
const openDeleteCategory = async (node) => {
  delCatDialog.id = node.id
  delCatDialog.name = node.name
  delCatDialog.path = node.path
  delCatDialog.blogCount = countWithChildren(node)
  delCatDialog.mode = 'move'
  delCatDialog.visible = true
}
const confirmDeleteCategory = async () => {
  if (delCatDialog.saving) return
  delCatDialog.saving = true
  try {
    await blogApi.deleteCategory(delCatDialog.id, delCatDialog.mode)
    ElMessage.success(t('common.deleted'))
    delCatDialog.visible = false
    if (activeCategory.value === delCatDialog.path) activeCategory.value = ''
    await loadCategoryCounts()
    await load()
  } catch (e) {
    ElMessage.error(e.message || 'Failed')
  } finally {
    delCatDialog.saving = false
  }
}

const onBlogDelete = async (b) => {
  try {
    await ElMessageBox.confirm(t('blog.deleteConfirm'), { type: 'warning', confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel'), closeOnClickModal: true })
    await blogApi.delete(b.id)
    ElMessage.success(t('common.deleted'))
    await load()
    await loadCategoryCounts()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || 'Failed')
  }
}

onMounted(() => {
  loadCategoryCounts()
  load()
})
</script>

<style scoped>
/* ========== 工具栏(全局 .page-toolbar 补充) ========== */
.blog-stats { font-size: 12px; color: var(--color-text-secondary); opacity: 0.7; white-space: nowrap; }

.sort-icon-btn {
  height: 24px; width: 56px;
  border: none; background: transparent;
  cursor: pointer; display: inline-flex; align-items: center; justify-content: center; gap: 2px;
  font-size: 11px; font-weight: 700; color: var(--color-text-secondary);
  transition: color 0.2s;
}
.sort-icon-btn:hover { color: var(--color-accent, #b88c6e); }
html.dark .sort-icon-btn:hover { color: #d4b298; }
.sort-label { letter-spacing: 0.5px; }

.cat-mgr-btn {
  width: 32px; height: 32px;
  border: none; background: rgba(184,140,110,0.06);
  border-radius: 10px; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  color: var(--color-text-secondary);
  transition: background 0.2s, color 0.2s;
}
.cat-mgr-btn:hover { background: rgba(184,140,110,0.12); color: var(--color-accent, #b88c6e); }
html.dark .cat-mgr-btn { background: rgba(212,178,152,0.08); }
html.dark .cat-mgr-btn:hover { background: rgba(212,178,152,0.15); color: #d4b298; }

/* ========== 布局 ========== */
.blog-layout { display: grid; grid-template-columns: 220px 1fr; gap: 20px; }
.blog-layout.no-side { grid-template-columns: 1fr; }

/* ========== 左侧分类面板 ========== */
.category-side {
  background: rgba(255,255,255,0.45);
  backdrop-filter: blur(24px) saturate(1.2);
  -webkit-backdrop-filter: blur(24px) saturate(1.2);
  border-radius: 14px;
  box-shadow: 0 2px 12px rgba(58,46,34,0.06);
  padding: 12px 10px;
  height: fit-content;
  position: sticky;
  top: 42px;
  border: 1px solid rgba(255,255,255,0.4);
  max-height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
}
html.dark .category-side {
  background: rgba(30,42,72,0.45);
  border-color: rgba(255,255,255,0.08);
  box-shadow: 0 2px 12px rgba(0,0,0,0.15);
}
.side-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; padding: 0 6px; flex-shrink: 0; }
.side-title { font-size: 13px; font-weight: 600; color: var(--color-text-secondary); }

.side-add-btn {
  width: 32px; height: 32px;
  border: none; background: rgba(184,140,110,0.08);
  border-radius: 8px; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  color: var(--color-text-secondary);
  transition: background 0.2s, color 0.2s, transform 0.2s;
}
.side-add-btn:hover {
  background: rgba(184,140,110,0.15);
  color: var(--color-accent, #b88c6e);
  transform: scale(1.1);
}
html.dark .side-add-btn { background: rgba(212,178,152,0.1); }
html.dark .side-add-btn:hover { background: rgba(212,178,152,0.18); color: #d4b298; }

.cat-list { flex: 1; overflow-y: auto; min-height: 0; }
.cat-list::-webkit-scrollbar { width: 4px; }
.cat-list::-webkit-scrollbar-track { background: transparent; }
.cat-list::-webkit-scrollbar-thumb { background: rgba(58,46,34,0.12); border-radius: 2px; }
html.dark .cat-list::-webkit-scrollbar-thumb { background: rgba(232,220,200,0.12); }

.cat-item {
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  color: var(--color-text);
  transition: background 0.2s ease, color 0.2s ease;
  display: flex;
  align-items: center;
  gap: 4px;
  position: relative;
  margin-bottom: 2px;
}
.cat-item:hover { background: rgba(184,140,110,0.06); }
html.dark .cat-item:hover { background: rgba(212,178,152,0.08); }

.cat-item.active {
  background: rgba(184,140,110,0.12);
  color: var(--color-accent, #b88c6e);
  font-weight: 600;
}
.cat-item.active::before {
  content: '';
  position: absolute;
  left: 0; top: 6px; bottom: 6px;
  width: 3px;
  border-radius: 2px;
  background: var(--color-accent, #b88c6e);
}
html.dark .cat-item.active { background: rgba(212,178,152,0.15); color: #d4b298; }
html.dark .cat-item.active::before { background: #d4b298; }

.cat-toggle { width: 16px; height: 16px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; color: var(--color-text-secondary); cursor: pointer; }
.cat-toggle svg { transition: transform 0.2s; }
.cat-toggle-placeholder { width: 16px; flex-shrink: 0; }

.cat-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; }
.cat-count { font-size: 11px; color: var(--color-text-secondary); opacity: 0.6; flex-shrink: 0; font-variant-numeric: tabular-nums; }
.cat-ops { display: none; gap: 2px; flex-shrink: 0; }
.cat-item:hover .cat-ops { display: flex; }
.cat-item:hover .cat-count { display: none; }
.cat-op-btn {
  width: 24px; height: 24px;
  border: none; background: transparent;
  border-radius: 6px; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  color: var(--color-text-secondary);
  transition: background 0.15s, color 0.15s;
}
.cat-op-btn:hover { background: rgba(184,140,110,0.1); color: var(--color-accent, #b88c6e); }
html.dark .cat-op-btn:hover { background: rgba(212,178,152,0.1); color: #d4b298; }
.cat-op-btn.danger:hover { background: rgba(201,116,116,0.1); color: #c97474; }

/* ========== 博客卡片 ========== */
.blog-main { min-width: 0; }
.blog-item {
  display: flex;
  gap: 16px;
  margin-bottom: 14px;
  cursor: pointer;
  padding: 18px 20px;
  border-radius: 14px;
  position: relative;
  min-height: 90px;
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}
.blog-item:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 28px rgba(58,46,34,0.12);
}
html.dark .blog-item:hover { box-shadow: 0 8px 28px rgba(0,0,0,0.3); }

.blog-cover {
  width: 120px; height: 80px;
  object-fit: cover;
  object-position: center;
  border-radius: 10px;
  flex-shrink: 0;
}

.blog-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4px; }

/* 第一行:标题 */
.blog-title-row { display: flex; align-items: center; gap: 8px; }
.blog-title {
  font-size: 16px;
  font-weight: 600;
  line-height: 1.5;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.draft-badge {
  flex-shrink: 0;
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 6px;
  background: rgba(138,109,59,0.12);
  color: #8a6d3b;
  font-weight: 500;
}
html.dark .draft-badge { background: rgba(212,178,152,0.15); color: #d4b86a; }

/* 第二行:摘要 */
.blog-summary {
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 70%;
}

/* 第三行:分类+标签+元数据合并一行 */
.blog-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 2px;
}
.blog-sub { display: flex; gap: 6px; flex-wrap: nowrap; align-items: center; overflow: hidden; }
.blog-cat {
  background: rgba(184,140,110,0.1);
  border: 1px solid rgba(184,140,110,0.15);
  color: var(--color-accent, #b88c6e);
  padding: 2px 8px;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.4;
  backdrop-filter: blur(8px);
  flex-shrink: 0;
}
html.dark .blog-cat {
  background: rgba(212,178,152,0.15);
  border-color: rgba(212,178,152,0.2);
  color: #d4b298;
}

.blog-tags { display: flex; gap: 4px; flex-wrap: nowrap; overflow: hidden; }
.blog-tags .tag {
  background: rgba(184,140,110,0.06);
  border: 1px solid rgba(184,140,110,0.1);
  color: var(--color-text-secondary);
  padding: 2px 7px;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.4;
  backdrop-filter: blur(8px);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
  white-space: nowrap;
  flex-shrink: 0;
}
.blog-tags .tag:hover {
  background: rgba(184,140,110,0.15);
  color: var(--color-accent, #b88c6e);
}
html.dark .blog-tags .tag {
  background: rgba(212,178,152,0.1);
  border-color: rgba(212,178,152,0.12);
  color: rgba(232,220,200,0.6);
}
html.dark .blog-tags .tag:hover {
  background: rgba(212,178,152,0.18);
  color: #d4b298;
}

.blog-meta {
  font-size: 11px;
  color: var(--color-text-secondary);
  opacity: 0.6;
  line-height: 1.4;
  white-space: nowrap;
  flex-shrink: 0;
}

/* hover 快捷操作按钮 */
.blog-actions {
  position: absolute;
  top: 12px;
  right: 14px;
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s;
}
.blog-item:hover .blog-actions { opacity: 1; }
.action-btn {
  width: 28px; height: 28px;
  border: none; background: rgba(255,255,255,0.7);
  backdrop-filter: blur(8px);
  border-radius: 8px; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  color: var(--color-text-secondary);
  transition: background 0.15s, color 0.15s;
}
.action-btn:hover { background: rgba(184,140,110,0.15); color: var(--color-accent, #b88c6e); }
.action-btn.danger:hover { background: rgba(201,116,116,0.15); color: #c97474; }
html.dark .action-btn { background: rgba(30,42,72,0.7); }
html.dark .action-btn:hover { background: rgba(212,178,152,0.15); color: #d4b298; }
html.dark .action-btn.danger:hover { background: rgba(201,116,116,0.15); color: #c97474; }

/* 空状态 */
.empty-state { padding: 48px 0; }

/* 删除分类弹窗 */
.del-cat-name { font-weight: 600; font-size: 15px; color: var(--color-primary); margin-bottom: 8px; }
.del-cat-hint { font-size: 13px; color: var(--color-text-secondary); line-height: 1.5; }
.del-cat-radios { display: flex; flex-direction: column; gap: 8px; margin-top: 12px; }

@media (max-width: 768px) {
  .page-toolbar { padding: 8px 12px; gap: 8px; }
  .tb-left { width: 100%; }
  .blog-cover { width: 90px; height: 64px; }
  .blog-actions { opacity: 1; }
  .blog-item { padding: 12px; }
  .blog-title { font-size: 15px; }
  .blog-meta { font-size: 12px; }
  .blog-footer { flex-direction: column; align-items: flex-start; gap: 4px; }
}
</style>
