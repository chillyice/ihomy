<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('games.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <span class="section-label">{{ $t('games.title') }}</span>
      </div>
      <div class="tb-right">
        <el-button type="primary" size="small" round @click="openImport">
          <el-icon><Plus /></el-icon>
          {{ $t('games.import') }}
        </el-button>
      </div>
    </PageToolbar>

    <div v-loading="loading" class="tool-grid">
      <!-- 花园固定入口(植物养殖改名而来) -->
      <div class="tool-card card" @click="$router.push('/tools/plant')">
        <div class="tool-icon"><el-icon :size="30"><Sunny /></el-icon></div>
        <div class="tool-name">{{ $t('games.garden.title') }}</div>
        <div class="tool-desc">{{ $t('games.garden.desc') }}</div>
        <span class="tool-enter">{{ $t('games.enter') }}<el-icon><ArrowRight /></el-icon></span>
      </div>

      <!-- 宠物连连看固定入口(H5 原生小游戏,通关发积分) -->
      <div class="tool-card card" @click="$router.push('/games/petlink')">
        <div class="tool-icon"><el-icon :size="30"><Grid /></el-icon></div>
        <div class="tool-name">{{ $t('games.petlink.title') }}</div>
        <div class="tool-desc">{{ $t('games.petlink.desc') }}</div>
        <span class="tool-enter">{{ $t('games.enter') }}<el-icon><ArrowRight /></el-icon></span>
      </div>

      <!-- 导入的小游戏 -->
      <div v-for="g in games" :key="g.id" class="tool-card card" @click="play(g)">
        <div class="tool-icon"><el-icon :size="30"><VideoPlay /></el-icon></div>
        <div class="tool-name">{{ g.name }}</div>
        <div class="tool-desc">{{ g.description || $t('games.noDesc') }}</div>
        <div class="tool-meta">
          <div class="tool-type-tag">{{ g.type }}</div>
          <span class="tool-enter">{{ $t('games.play') }}<el-icon><ArrowRight /></el-icon></span>
          <el-dropdown trigger="click" @command="(cmd) => onGameAction(cmd, g)">
            <span class="game-more" @click.stop><el-icon><MoreFilled /></el-icon></span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="edit">{{ $t('games.edit') }}</el-dropdown-item>
                <el-dropdown-item command="delete" divided>{{ $t('games.delete') }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>

      <div v-if="!loading && games.length === 0" class="tool-empty">{{ $t('games.empty') }}</div>
    </div>

    <!-- 导入对话框 -->
    <el-dialog v-model="importVisible" :title="$t('games.import')" width="440px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item :label="$t('games.file')">
          <input ref="fileInput" type="file" accept=".swf,.gba" class="game-file-input" @change="onFilePicked" />
        </el-form-item>
        <el-form-item :label="$t('games.name')">
          <el-input v-model="importForm.name" maxlength="100" :placeholder="$t('games.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('games.desc')">
          <el-input v-model="importForm.description" type="textarea" :rows="2" maxlength="500" :placeholder="$t('games.descPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="importVisible = false">{{ $t('games.cancel') }}</el-button>
        <el-button type="primary" size="small" :loading="importing" @click="submitImport">{{ $t('games.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 编辑对话框 -->
    <el-dialog v-model="editVisible" :title="$t('games.edit')" width="440px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item :label="$t('games.name')">
          <el-input v-model="editForm.name" maxlength="100" />
        </el-form-item>
        <el-form-item :label="$t('games.desc')">
          <el-input v-model="editForm.description" type="textarea" :rows="2" maxlength="500" :placeholder="$t('games.descPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="editVisible = false">{{ $t('games.cancel') }}</el-button>
        <el-button type="primary" size="small" :loading="saving" @click="submitEdit">{{ $t('games.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Sunny, VideoPlay, ArrowRight, MoreFilled, Grid } from '@element-plus/icons-vue'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import { gameApi } from '@/api'

const router = useRouter()
const { t } = useI18n()

const games = ref([])
const loading = ref(false)

const importVisible = ref(false)
const importing = ref(false)
const importForm = reactive({ file: null, name: '', description: '' })

const editVisible = ref(false)
const saving = ref(false)
const editForm = reactive({ id: null, name: '', description: '' })

const load = async () => {
  loading.value = true
  try {
    games.value = (await gameApi.list()) || []
  } finally {
    loading.value = false
  }
}

const play = (g) => router.push(`/games/play/${g.id}`)

const openImport = () => {
  importForm.file = null
  importForm.name = ''
  importForm.description = ''
  importVisible.value = true
}

const onFilePicked = (e) => {
  const f = e.target.files?.[0]
  if (!f) return
  importForm.file = f
  if (!importForm.name.trim()) {
    importForm.name = f.name.replace(/\.(swf|gba)$/i, '')
  }
  e.target.value = ''
}

const submitImport = async () => {
  if (!importForm.file) {
    ElMessage.warning(t('games.needFile'))
    return
  }
  if (!importForm.name.trim()) {
    ElMessage.warning(t('games.needName'))
    return
  }
  importing.value = true
  try {
    const fd = new FormData()
    fd.append('file', importForm.file)
    fd.append('name', importForm.name.trim())
    fd.append('description', importForm.description.trim())
    await gameApi.import(fd)
    ElMessage.success(t('games.imported'))
    importVisible.value = false
    load()
  } finally {
    importing.value = false
  }
}

const openEdit = (g) => {
  editForm.id = g.id
  editForm.name = g.name
  editForm.description = g.description || ''
  editVisible.value = true
}

const submitEdit = async () => {
  if (!editForm.name.trim()) {
    ElMessage.warning(t('games.needName'))
    return
  }
  saving.value = true
  try {
    await gameApi.update(editForm.id, { name: editForm.name.trim(), description: editForm.description.trim() })
    ElMessage.success(t('games.saved'))
    editVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

const removeGame = async (g) => {
  try {
    await ElMessageBox.confirm(t('games.deleteConfirm', { name: g.name }), t('games.delete'), { type: 'warning' })
  } catch (e) {
    return
  }
  await gameApi.remove(g.id)
  ElMessage.success(t('games.deleted'))
  load()
}

const onGameAction = (cmd, g) => {
  if (cmd === 'edit') openEdit(g)
  else if (cmd === 'delete') removeGame(g)
}

onMounted(load)
</script>

<style scoped>
.tool-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
  min-height: 120px;
}
.tool-card {
  position: relative;
  cursor: pointer;
  transition: transform 0.2s ease;
  contain: layout style;
}
.tool-card:hover { transform: translateY(-3px); }
.tool-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-accent-weak, rgba(184, 140, 110, 0.14));
  color: var(--color-accent, var(--color-brand));
  margin-bottom: 14px;
}
.tool-name { font-size: 16px; font-weight: 600; margin-bottom: 6px; }
.tool-desc {
  font-size: 13px;
  color: var(--color-text-secondary, #8a8a8a);
  line-height: 1.5;
  min-height: 20px;
}
.tool-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}
.tool-type-tag {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.06);
  color: var(--color-text-secondary, #999);
  font-weight: 500;
}
.tool-enter {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--color-accent, var(--color-brand));
}
.game-more {
  display: inline-flex;
  align-items: center;
  padding: 2px 6px;
  border-radius: 6px;
  color: var(--color-text-secondary, #999);
  cursor: pointer;
}
.game-more:hover { color: var(--color-accent, var(--color-brand)); background: rgba(0, 0, 0, 0.04); }
.tool-empty {
  grid-column: 1 / -1;
  text-align: center;
  padding: 40px 0;
  color: var(--color-text-secondary, #999);
  font-size: 13px;
}
.game-file-input {
  width: 100%;
  font-size: 13px;
  color: var(--color-text-secondary, #888);
}
</style>
