<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('tools.title'), to: '/tools' }, { label: $t('tools.mindmap.title') }]" />

    <div class="page-toolbar card">
      <div class="tb-left">
        <span class="section-label">{{ $t('tools.mindmap.title') }}</span>
      </div>
      <div class="tb-right">
        <el-button type="primary" :icon="Plus" @click="createMindMap">{{ $t('tools.mindmap.new') }}</el-button>
      </div>
    </div>

    <div v-loading="loading">
      <div v-if="list.length" class="mm-grid">
        <div v-for="m in list" :key="m.id" class="mm-card card" @click="openMap(m)">
          <el-button class="mm-del" :icon="Delete" circle text size="small" @click.stop="removeMindMap(m)" />
          <div class="mm-card-title">{{ m.title }}</div>
          <div class="mm-card-meta">
            <span>{{ $t('tools.mindmap.creator') }}：{{ m.creatorName || '-' }}</span>
            <span>{{ $t('tools.mindmap.updatedAt') }}：{{ formatTime(m.updatedAt) }}</span>
          </div>
        </div>
      </div>
      <el-empty v-else :description="$t('tools.mindmap.noData')" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import { mindmapApi } from '@/api'
import { useUserStore } from '@/stores/user'
import Breadcrumb from '@/components/Breadcrumb.vue'

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

const createMindMap = async () => {
  if (!ensureLogin()) return
  const { value } = await ElMessageBox.prompt(t('tools.mindmap.titlePrompt'), t('tools.mindmap.new'), {
    inputValue: t('tools.mindmap.untitled'),
    inputValidator: (v) => !!(v && v.trim()) || t('tools.mindmap.titlePrompt'),
  })
  const created = await mindmapApi.create({ title: value.trim() })
  ElMessage.success(t('tools.mindmap.created'))
  router.push(`/tools/mindmap/${created.id}`)
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
</style>
