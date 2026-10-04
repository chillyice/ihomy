<!-- 回收站:照片/相册/视频/图书的逻辑删内容,可恢复或彻底删除,超 7 天自动物理清理 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: t('recycle.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <span class="rc-hint">{{ t('recycle.retentionHint') }}</span>
      </div>
      <div class="tb-right">
        <el-button v-if="list.length" type="danger" plain @click="onEmpty">{{ t('recycle.emptyTab') }}</el-button>
      </div>
    </PageToolbar>

    <el-tabs v-model="tab" @tab-change="load">
      <el-tab-pane v-for="tb in tabs" :key="tb" :name="tb" :label="t('recycle.' + tb)" />
    </el-tabs>

    <div v-loading="loading">
      <div v-if="list.length" class="rc-grid">
        <div v-for="it in list" :key="it.id" class="rc-card card">
          <div class="rc-thumb">
            <img v-if="it.url" :src="it.url" :alt="it.title" loading="lazy" />
            <div v-else class="rc-noimg"><el-icon><Picture /></el-icon></div>
          </div>
          <div class="rc-body">
            <div class="rc-title" :title="it.title">{{ it.title }}</div>
            <div class="rc-time">{{ t('recycle.deletedAt') }}：{{ fmt(it.deletedAt) }}</div>
          </div>
          <div class="rc-actions">
            <el-button size="small" @click="onRestore(it)">{{ t('recycle.restore') }}</el-button>
            <el-button size="small" type="danger" text @click="onPurge(it)">{{ t('recycle.purge') }}</el-button>
          </div>
        </div>
      </div>
      <el-empty v-else :description="t('recycle.empty')" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { recycleApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Picture } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'

const { t } = useI18n()
const tabs = ['photo', 'album', 'video', 'book']
const tab = ref('photo')
const list = ref([])
const loading = ref(false)

const fmt = (d) => (d ? new Date(d).toLocaleString('zh-CN') : '')

const load = async () => {
  loading.value = true
  try {
    list.value = await recycleApi.list(tab.value)
  } finally {
    loading.value = false
  }
}

const onRestore = async (it) => {
  await recycleApi.restore(tab.value, it.id)
  ElMessage.success(t('recycle.restored'))
  load()
}

const onPurge = async (it) => {
  await ElMessageBox.confirm(t('recycle.purgeConfirm', { name: it.title }), t('common.tip'), { type: 'warning', closeOnClickModal: true })
  await recycleApi.purge(tab.value, it.id)
  ElMessage.success(t('recycle.purged'))
  load()
}

const onEmpty = async () => {
  await ElMessageBox.confirm(t('recycle.emptyConfirm', { tab: t('recycle.' + tab.value) }), t('common.tip'), { type: 'warning', closeOnClickModal: true })
  await recycleApi.empty(tab.value)
  ElMessage.success(t('recycle.emptied'))
  load()
}

onMounted(load)
</script>

<style scoped>
.rc-hint { font-size: 12px; color: var(--color-text-secondary); }
.rc-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}
.rc-card { overflow: hidden; display: flex; flex-direction: column; }
.rc-thumb {
  aspect-ratio: 4 / 3;
  background: rgba(31, 58, 95, 0.06);
  overflow: hidden;
}
.rc-thumb img { width: 100%; height: 100%; object-fit: cover; display: block; }
.rc-noimg {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-secondary);
  font-size: 26px;
}
.rc-body { padding: 10px 14px 0; }
.rc-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.rc-time { margin-top: 4px; font-size: 12px; color: var(--color-text-secondary); }
.rc-actions { padding: 8px 12px 12px; display: flex; justify-content: flex-end; gap: 6px; }
.rc-actions :deep(.el-button + .el-button) { margin-left: 0; }

@media (max-width: 768px) {
  .rc-grid { grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); }
}
</style>
