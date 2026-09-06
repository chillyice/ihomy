<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('tools.title'), to: '/tools' }, { label: $t('tools.mindmap.title') }]" />

    <div class="page-toolbar card">
      <div class="tb-left">
        <span class="section-label">{{ $t('tools.mindmap.title') }}</span>
      </div>
      <div class="tb-right">
        <el-button :icon="DeleteFilled" @click="openTrash">{{ $t('tools.mindmap.trashBtn') }}</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">{{ $t('tools.mindmap.new') }}</el-button>
      </div>
    </div>

    <div v-loading="loading">
      <div v-if="list.length" class="mm-grid">
        <div v-for="m in list" :key="m.id" class="mm-card card" @click="openMap(m)">
          <el-button class="mm-del" :icon="Delete" circle text size="small" @click.stop="removeMindMap(m)" />
          <img v-if="m.thumbUrl" class="mm-card-thumb" :src="m.thumbUrl" loading="lazy" alt="" />
          <div class="mm-card-title">{{ m.title }}</div>
          <div class="mm-card-meta">
            <span>{{ $t('tools.mindmap.creator') }}：{{ m.creatorName || '-' }}</span>
            <span>{{ $t('tools.mindmap.updatedAt') }}：{{ formatTime(m.updatedAt) }}</span>
          </div>
        </div>
      </div>
      <el-empty v-else :description="$t('tools.mindmap.noData')" />
    </div>

    <!-- 新建脑图:选模板 + 标题 -->
    <el-dialog v-model="createVisible" :title="$t('tools.mindmap.new')" width="560px">
      <div class="mm-tpl-label">{{ $t('tools.mindmap.tpl.pick') }}</div>
      <div class="mm-tpl-grid">
        <div
          v-for="tpl in tplOptions"
          :key="tpl.key"
          class="mm-tpl-card"
          :class="{ active: tplKey === tpl.key }"
          @click="tplKey = tpl.key"
        >
          <div class="mm-tpl-name">
            <span class="mm-tpl-emoji">{{ tpl.emoji }}</span>{{ tpl.label }}
          </div>
          <div class="mm-tpl-desc">{{ tpl.desc }}</div>
        </div>
      </div>
      <el-input v-model="newTitle" :placeholder="$t('tools.mindmap.titlePrompt')" maxlength="100" />
      <template #footer>
        <el-button @click="createVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="creating" @click="confirmCreate">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
    <!-- 回收站 -->
    <el-dialog v-model="trashVisible" :title="$t('tools.mindmap.trashBtn')" width="520px">
      <div v-if="trashList.length" class="mm-trash-list">
        <div v-for="m in trashList" :key="m.id" class="mm-trash-item">
          <div class="mm-trash-info">
            <div class="mm-trash-title">{{ m.title }}</div>
            <div class="mm-trash-meta">{{ $t('tools.mindmap.creator') }}：{{ m.creatorName || '-' }} · {{ $t('tools.mindmap.updatedAt') }}：{{ formatTime(m.updatedAt) }}</div>
          </div>
          <div class="mm-trash-ops">
            <el-button size="small" type="primary" text @click="restoreMap(m)">{{ $t('tools.mindmap.restore') }}</el-button>
            <el-button size="small" type="danger" text @click="purgeMap(m)">{{ $t('tools.mindmap.purge') }}</el-button>
          </div>
        </div>
      </div>
      <el-empty v-else :description="$t('tools.mindmap.trashEmpty')" :image-size="48" />
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, DeleteFilled } from '@element-plus/icons-vue'
import { mindmapApi } from '@/api'
import { useUserStore } from '@/stores/user'
import Breadcrumb from '@/components/Breadcrumb.vue'
import { getMindmapTemplates } from './mindmapTemplates'

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const list = ref([])

const load = async () => {
  loading.value = true
  try {
    list.value = await mindmapApi.list()
  } finally {
    loading.value = false
  }
}

const ensureLogin = () => {
  if (userStore.isLoggedIn) return true
  router.push({ name: 'Login', query: { redirect: '/tools/mindmap' } })
  return false
}

// ---------- 新建脑图(模板 + 标题) ----------
const createVisible = ref(false)
const creating = ref(false)
const tplKey = ref('blank')
const newTitle = ref('')

const tplOptions = ref([])
const buildTplOptions = () => {
  // 空白模板置顶;预设模板带图标与描述
  const emojis = { project: '📋', weekly: '🗓️', reading: '📖', meeting: '📝', problem: '🧩', trip: '🧳' }
  tplOptions.value = [
    { key: 'blank', emoji: '📄', label: t('tools.mindmap.tpl.blank'), desc: t('tools.mindmap.tpl.blankDesc'), tree: null, layout: 'logicalStructure' },
    ...getMindmapTemplates(t).map((tpl) => ({
      ...tpl,
      emoji: emojis[tpl.key] || '📄',
      label: t(`tools.mindmap.tpl.${tpl.key}.name`),
      desc: t(`tools.mindmap.tpl.${tpl.key}.desc`),
    })),
  ]
}

const openCreateDialog = () => {
  if (!ensureLogin()) return
  buildTplOptions()
  tplKey.value = 'blank'
  newTitle.value = ''
  createVisible.value = true
}

const confirmCreate = async () => {
  const title = newTitle.value.trim()
  if (!title) {
    ElMessage.warning(t('tools.mindmap.titlePrompt'))
    return
  }
  const tpl = tplOptions.value.find((o) => o.key === tplKey.value)
  const root = tpl?.tree
    ? JSON.parse(JSON.stringify(tpl.tree))
    : { data: { text: title, expand: true }, children: [] }
  // data 存 simple-mind-map 全量结构,编辑器 load() 按 root/layout/theme 解析
  const data = JSON.stringify({ root, layout: tpl?.layout || 'logicalStructure', theme: { template: 'default', config: {} } })
  creating.value = true
  try {
    const created = await mindmapApi.create({ title, data })
    createVisible.value = false
    ElMessage.success(t('tools.mindmap.created'))
    router.push(`/tools/mindmap/${created.id}`)
  } finally {
    creating.value = false
  }
}

const openMap = (m) => {
  router.push(`/tools/mindmap/${m.id}`)
}

const removeMindMap = async (m) => {
  await ElMessageBox.confirm(t('tools.mindmap.deleteConfirm', { name: m.title }), {
    type: 'warning',
  })
  await mindmapApi.remove(m.id)
  ElMessage.success(t('tools.mindmap.deleted'))
  load()
}

// ---------- 回收站 ----------
const trashVisible = ref(false)
const trashList = ref([])

const openTrash = async () => {
  if (!ensureLogin()) return
  trashVisible.value = true
  trashList.value = await mindmapApi.trash()
}

const restoreMap = async (m) => {
  await mindmapApi.restore(m.id)
  ElMessage.success(t('tools.mindmap.restored'))
  trashList.value = await mindmapApi.trash()
  load()
}

const purgeMap = async (m) => {
  await ElMessageBox.confirm(t('tools.mindmap.purgeConfirm', { name: m.title }), { type: 'warning' })
  await mindmapApi.purge(m.id)
  ElMessage.success(t('tools.mindmap.purged'))
  trashList.value = await mindmapApi.trash()
}

const formatTime = (s) => (s ? String(s).slice(0, 16).replace('T', ' ') : '-')

onMounted(load)
</script>

<style scoped>
.mm-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 16px;
}
.mm-card {
  position: relative;
  cursor: pointer;
  transition: transform 0.2s ease;
  contain: layout style;
}
.mm-card:hover { transform: translateY(-3px); }
.mm-del {
  position: absolute;
  top: 12px;
  right: 12px;
  color: var(--color-text-secondary, #8a8a8a);
}
.mm-del:hover { color: var(--color-danger, #e05c5c); }
.mm-card-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 10px;
  padding-right: 32px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mm-card-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--color-text-secondary, #8a8a8a);
}
.mm-card-thumb {
  width: 100%;
  max-height: 150px;
  object-fit: contain;
  border-radius: 8px;
  margin-bottom: 10px;
  background: var(--color-card-2, rgba(0, 0, 0, 0.03));
}

/* 回收站 */
.mm-trash-list { display: flex; flex-direction: column; gap: 8px; max-height: 420px; overflow: auto; }
.mm-trash-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--color-border, #e5e5e5);
  border-radius: 10px;
}
.mm-trash-title { font-size: 14px; font-weight: 600; margin-bottom: 2px; }
.mm-trash-meta { font-size: 12px; color: var(--color-text-secondary, #8a8a8a); }
.mm-trash-ops { flex-shrink: 0; }

/* 新建模板选择 */
.mm-tpl-label {
  font-size: 13px;
  color: var(--color-text-secondary, #8a8a8a);
  margin-bottom: 10px;
}
.mm-tpl-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}
.mm-tpl-card {
  border: 1px solid var(--color-border, #e5e5e5);
  border-radius: 10px;
  padding: 10px 12px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}
.mm-tpl-card:hover { border-color: var(--color-primary, #b88c6e); }
.mm-tpl-card.active {
  border-color: var(--color-primary, #b88c6e);
  background: var(--color-primary-light, rgba(184, 140, 110, 0.08));
}
.mm-tpl-name {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 4px;
  display: flex;
  align-items: center;
  gap: 5px;
}
.mm-tpl-emoji { font-size: 15px; }
.mm-tpl-desc {
  font-size: 12px;
  color: var(--color-text-secondary, #8a8a8a);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
@media (max-width: 768px) {
  .mm-tpl-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>
