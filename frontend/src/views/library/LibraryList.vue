<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('library.title') }]">
      <template #right>
        <button v-if="userStore.isLoggedIn" class="write-btn" @click="router.push('/library/edit')">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 5v14M5 12h14"/></svg>
          {{ $t('library.upload') }}
        </button>
      </template>
    </Breadcrumb>
    <div class="lib-layout">
      <aside v-if="categories.length || userStore.isLoggedIn" class="category-side">
        <div class="side-head">
          <span class="side-title">{{ $t('library.category') }}</span>
          <button v-if="userStore.isLoggedIn" class="side-add-btn" :title="$t('library.manageCategory')" @click="openCategoryDialog('add')">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg>
          </button>
        </div>
        <div class="cat-item" :class="{ active: !activeCategory }" @click="setCategory('')">{{ $t('library.allCategories') }}</div>
        <div
          v-for="c in categories"
          :key="c"
          class="cat-item"
          :class="{ active: activeCategory === c }"
          @click="setCategory(c)"
        >
          <span class="cat-name">{{ c }}</span>
          <span v-if="userStore.isLoggedIn" class="cat-ops" @click.stop>
            <button class="cat-op-btn" :title="$t('library.editCategory')" @click="openCategoryDialog('edit', c)">
              <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
            </button>
            <button class="cat-op-btn danger" :title="$t('library.deleteCategory')" @click="openDeleteCategory(c)">
              <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
            </button>
          </span>
        </div>
      </aside>
      <div v-loading="loading" class="lib-main">
        <div class="book-grid">
          <div v-for="b in list" :key="b.id" class="book-card card" @click="router.push(`/library/${b.id}`)">
            <div class="book-cover-wrap">
              <img v-if="b.coverUrl" :src="b.coverUrl" class="book-cover" />
              <div v-else class="book-cover placeholder">
                <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.2"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
              </div>
              <span class="format-badge">{{ b.fileFormat }}</span>
            </div>
            <div class="book-info">
              <div class="book-title">{{ b.title }}</div>
              <div v-if="b.author" class="book-author">{{ b.author }}</div>
              <div class="book-meta">
                <span v-if="b.category" class="book-cat">{{ b.category }}</span>
                <span class="book-views">{{ b.viewCount }} {{ $t('library.views') }}</span>
              </div>
            </div>
            <div v-if="userStore.isLoggedIn && canEdit(b)" class="book-more" @click.stop>
              <el-dropdown trigger="click" placement="bottom-end" @command="cmd => onBookCommand(cmd, b)">
                <button class="icon-btn more-trigger">
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><circle cx="5" cy="12" r="1.8"/><circle cx="12" cy="12" r="1.8"/><circle cx="19" cy="12" r="1.8"/></svg>
                </button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="edit">{{ $t('library.editBook') }}</el-dropdown-item>
                    <el-dropdown-item command="copy">{{ $t('library.copyLink') }}</el-dropdown-item>
                    <el-dropdown-item divided command="delete" class="danger-item">{{ $t('common.delete') }}</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
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

    <el-dialog v-model="catDialog.visible" :title="catDialog.mode === 'add' ? $t('library.newCategory') : $t('library.editCategory')" width="360px" append-to-body>
      <el-input v-model="catDialog.name" :placeholder="$t('library.categoryPlaceholder')" @keyup.enter="saveCategory" />
      <template #footer>
        <el-button @click="catDialog.visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :disabled="!catDialog.name.trim() || catDialog.saving" :loading="catDialog.saving" @click="saveCategory">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="delCatDialog.visible" :title="$t('library.deleteCategory')" width="380px" append-to-body>
      <p class="del-cat-name">{{ delCatDialog.category }}</p>
      <p v-if="delCatDialog.bookCount > 0" class="del-cat-hint">{{ $t('library.deleteCategoryHint') }}</p>
      <el-radio-group v-if="delCatDialog.bookCount > 0" v-model="delCatDialog.mode" class="del-cat-radios">
        <el-radio value="move">{{ $t('library.moveToAll') }} ({{ delCatDialog.bookCount }})</el-radio>
        <el-radio value="delete">{{ $t('library.deleteBooks') }} ({{ delCatDialog.bookCount }})</el-radio>
      </el-radio-group>
      <p v-else class="del-cat-hint">{{ $t('common.confirm') }}{{ $t('library.deleteCategory') }}?</p>
      <template #footer>
        <el-button @click="delCatDialog.visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="danger" :loading="delCatDialog.saving" @click="confirmDeleteCategory">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { libraryApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb.vue'

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()
const list = ref([])
const categories = ref([])
const activeCategory = ref('')
const loading = ref(false)

const canEdit = (b) => userStore.isOwner || b.uploaderId === userStore.userInfo?.id

const load = async () => {
  loading.value = true
  try {
    const params = { current: 1, size: 50 }
    if (activeCategory.value) params.category = activeCategory.value
    const data = await libraryApi.list(params)
    list.value = data.records || []
  } finally {
    loading.value = false
  }
}

const loadCategories = async () => {
  try {
    categories.value = await libraryApi.categories() || []
  } catch (e) {}
}

const setCategory = (c) => {
  activeCategory.value = c
  load()
}

const catDialog = reactive({ visible: false, mode: 'add', oldName: '', name: '', saving: false })
const openCategoryDialog = (mode, name = '') => {
  catDialog.mode = mode
  catDialog.oldName = name
  catDialog.name = mode === 'edit' ? name : ''
  catDialog.visible = true
}
const saveCategory = async () => {
  const name = catDialog.name.trim()
  if (!name || catDialog.saving) return
  catDialog.saving = true
  try {
    if (catDialog.mode === 'add') {
      await libraryApi.addCategory(name)
    } else {
      await libraryApi.renameCategory(catDialog.oldName, name)
    }
    ElMessage.success(t('common.saveSuccess'))
    catDialog.visible = false
    await loadCategories()
    if (catDialog.mode === 'edit' && activeCategory.value === catDialog.oldName) {
      activeCategory.value = name
      load()
    }
  } catch (e) {
    ElMessage.error(e.message || 'Failed')
  } finally {
    catDialog.saving = false
  }
}

const delCatDialog = reactive({ visible: false, category: '', bookCount: 0, mode: 'move', saving: false })
const openDeleteCategory = async (category) => {
  const data = await libraryApi.list({ current: 1, size: 1, category })
  delCatDialog.bookCount = data.total || 0
  delCatDialog.category = category
  delCatDialog.mode = 'move'
  delCatDialog.visible = true
}
const confirmDeleteCategory = async () => {
  if (delCatDialog.saving) return
  delCatDialog.saving = true
  try {
    await libraryApi.deleteCategory(delCatDialog.category, delCatDialog.mode)
    ElMessage.success(t('common.deleted'))
    delCatDialog.visible = false
    if (activeCategory.value === delCatDialog.category) activeCategory.value = ''
    await loadCategories()
    await load()
  } catch (e) {
    ElMessage.error(e.message || 'Failed')
  } finally {
    delCatDialog.saving = false
  }
}

const onBookCommand = async (cmd, b) => {
  if (cmd === 'edit') {
    router.push(`/library/edit/${b.id}`)
  } else if (cmd === 'copy') {
    const url = `${window.location.origin}/library/${b.id}`
    try {
      await navigator.clipboard.writeText(url)
      ElMessage.success(t('library.linkCopied'))
    } catch (e) {
      ElMessage.error('Failed')
    }
  } else if (cmd === 'delete') {
    try {
      await ElMessageBox.confirm(t('library.deleteConfirm'), { type: 'warning', confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel') })
      await libraryApi.delete(b.id)
      ElMessage.success(t('common.deleted'))
      await load()
      await loadCategories()
    } catch (e) {
      if (e !== 'cancel') ElMessage.error(e.message || 'Failed')
    }
  }
}

onMounted(() => {
  loadCategories()
  load()
})
</script>

<style scoped>
.lib-layout { display: grid; grid-template-columns: 180px 1fr; gap: 16px; }

.category-side {
  background: rgba(255,255,255,0.45);
  backdrop-filter: blur(24px) saturate(1.2);
  -webkit-backdrop-filter: blur(24px) saturate(1.2);
  border-radius: 14px;
  box-shadow: 0 2px 12px rgba(58,46,34,0.06);
  padding: 10px;
  height: fit-content;
  position: sticky;
  top: 42px;
  border: 1px solid rgba(255,255,255,0.4);
}
html.dark .category-side {
  background: rgba(30,42,72,0.45);
  border-color: rgba(255,255,255,0.08);
  box-shadow: 0 2px 12px rgba(0,0,0,0.15);
}
.side-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; padding: 0 6px; }
.side-title { font-size: 13px; font-weight: 600; color: var(--color-text-secondary); }
.side-add-btn {
  width: 28px; height: 28px; border: none; background: transparent; border-radius: 8px; cursor: pointer;
  display: flex; align-items: center; justify-content: center; color: var(--color-text-secondary);
  transition: background 0.2s, color 0.2s, transform 0.2s;
}
.side-add-btn:hover { background: rgba(184,140,110,0.1); color: var(--color-accent, #b88c6e); transform: scale(1.1); }
html.dark .side-add-btn:hover { background: rgba(212,178,152,0.12); color: #d4b298; }

.cat-item {
  padding: 8px 10px; border-radius: 8px; cursor: pointer; font-size: 14px; color: var(--color-text);
  transition: background 0.2s ease, color 0.2s ease; display: flex; justify-content: space-between; align-items: center; position: relative;
}
.cat-item:hover { background: rgba(184,140,110,0.06); }
html.dark .cat-item:hover { background: rgba(212,178,152,0.08); }
.cat-item.active { background: rgba(184,140,110,0.12); color: var(--color-accent, #b88c6e); font-weight: 600; }
.cat-item.active::before { content: ''; position: absolute; left: 0; top: 6px; bottom: 6px; width: 3px; border-radius: 2px; background: var(--color-accent, #b88c6e); }
html.dark .cat-item.active { background: rgba(212,178,152,0.15); color: #d4b298; }
html.dark .cat-item.active::before { background: #d4b298; }
.cat-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cat-ops { display: none; gap: 2px; flex-shrink: 0; }
.cat-item:hover .cat-ops { display: flex; }
.cat-op-btn {
  width: 24px; height: 24px; border: none; background: transparent; border-radius: 6px; cursor: pointer;
  display: flex; align-items: center; justify-content: center; color: var(--color-text-secondary); transition: background 0.15s, color 0.15s;
}
.cat-op-btn:hover { background: rgba(184,140,110,0.1); color: var(--color-accent, #b88c6e); }
html.dark .cat-op-btn:hover { background: rgba(212,178,152,0.1); color: #d4b298; }
.cat-op-btn.danger:hover { background: rgba(201,116,116,0.1); color: #c97474; }

.icon-btn {
  width: 28px; height: 28px; border: none; background: transparent; border-radius: 8px; cursor: pointer;
  display: flex; align-items: center; justify-content: center; color: var(--color-text-secondary); transition: background 0.2s, color 0.2s;
}
.icon-btn:hover { background: rgba(184,140,110,0.1); color: var(--color-accent, #b88c6e); }
html.dark .icon-btn:hover { background: rgba(212,178,152,0.1); color: #d4b298; }

.lib-main { min-width: 0; }
.book-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 16px; }
.book-card {
  cursor: pointer; padding: 14px; border-radius: 14px; position: relative;
  transition: transform 0.25s ease, box-shadow 0.25s ease; display: flex; flex-direction: column; gap: 10px;
}
.book-card:hover { transform: translateY(-4px); box-shadow: 0 8px 28px rgba(58,46,34,0.12); }
html.dark .book-card:hover { box-shadow: 0 8px 28px rgba(0,0,0,0.3); }

.book-cover-wrap { position: relative; aspect-ratio: 3/4; border-radius: 8px; overflow: hidden; }
.book-cover { width: 100%; height: 100%; object-fit: cover; }
.book-cover.placeholder {
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, rgba(184,140,110,0.12), rgba(184,140,110,0.04));
  color: var(--color-text-secondary); opacity: 0.4;
}
html.dark .book-cover.placeholder { background: linear-gradient(135deg, rgba(212,178,152,0.1), rgba(212,178,152,0.03)); }
.format-badge {
  position: absolute; top: 6px; right: 6px; padding: 2px 6px; border-radius: 4px;
  background: rgba(0,0,0,0.5); color: #fff; font-size: 10px; font-weight: 600; backdrop-filter: blur(4px);
}

.book-info { display: flex; flex-direction: column; gap: 4px; }
.book-title { font-size: 14px; font-weight: 600; line-height: 1.4; color: var(--color-text); overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.book-author { font-size: 12px; color: var(--color-text-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.book-meta { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
.book-cat { background: rgba(184,140,110,0.1); border: 1px solid rgba(184,140,110,0.15); color: var(--color-accent, #b88c6e); padding: 1px 7px; border-radius: 8px; font-size: 11px; }
html.dark .book-cat { background: rgba(212,178,152,0.15); border-color: rgba(212,178,152,0.2); color: #d4b298; }
.book-views { font-size: 11px; color: var(--color-text-secondary); opacity: 0.6; }

.book-more { position: absolute; top: 8px; right: 8px; opacity: 0; transition: opacity 0.2s; z-index: 1; }
.book-card:hover .book-more { opacity: 1; }
.more-trigger { width: 28px; height: 28px; }

.empty-state { padding: 48px 0; }
.write-btn {
  height: 34px; padding: 0 16px; border: none; border-radius: 10px; background: #b88c6e; color: #fff;
  font-size: 13px; font-weight: 500; cursor: pointer; display: inline-flex; align-items: center; gap: 6px; transition: background 0.2s, transform 0.2s;
}
.write-btn:hover { background: #a87c5e; transform: translateY(-1px); }
html.dark .write-btn { background: #d4b298; color: #2a2018; }
html.dark .write-btn:hover { background: #e0c2aa; }

.del-cat-name { font-weight: 600; font-size: 15px; color: var(--color-primary); margin-bottom: 8px; }
.del-cat-hint { font-size: 13px; color: var(--color-text-secondary); line-height: 1.5; }
.del-cat-radios { display: flex; flex-direction: column; gap: 8px; margin-top: 12px; }

:deep(.danger-item) { color: #b04a3a !important; }
:deep(.danger-item:hover) { background: rgba(176,74,58,0.08) !important; color: #b04a3a !important; }
html.dark :deep(.danger-item) { color: #c97474 !important; }
html.dark :deep(.danger-item:hover) { background: rgba(201,116,116,0.12) !important; color: #c97474 !important; }
:deep(.el-dropdown-menu__item:hover) { background: rgba(184,140,110,0.06) !important; }
html.dark :deep(.el-dropdown-menu__item:hover) { background: rgba(212,178,152,0.08) !important; }
html.dark :deep(.el-dropdown-menu) { background: rgba(30,42,72,0.95) !important; border-color: rgba(255,255,255,0.1) !important; }
html.dark :deep(.el-dropdown-menu__item) { color: rgba(232,220,200,0.85) !important; }
html.dark :deep(.el-dropdown-menu__item:hover) { color: #E8DCC8 !important; }

@media (max-width: 768px) {
  .lib-layout { grid-template-columns: 1fr; }
  .category-side { display: flex; gap: 6px; overflow-x: auto; padding: 8px; }
  .side-head { display: none; }
  .cat-item { white-space: nowrap; }
  .cat-ops { display: none !important; }
  .book-grid { grid-template-columns: repeat(auto-fill, minmax(130px, 1fr)); gap: 10px; }
  .book-more { opacity: 1; }
}
</style>
