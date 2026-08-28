<!-- 日记本页:模仿博客列表风格,卡片式布局,hover操作菜单,显示作者 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('diary.title') }]" />

    <div class="page-toolbar card">
      <div class="tb-right">
        <button v-if="userStore.isLoggedIn" class="write-btn" @click="router.push('/diary/edit')">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/></svg>
          {{ $t('diary.newDiary') }}
        </button>
      </div>
    </div>

    <div v-loading="loading" class="diary-main">
      <div v-for="d in list" :key="d.id" class="diary-item card">
        <div class="diary-header">
          <span class="diary-date">{{ formatDate(d.createdAt) }}</span>
          <span v-if="d.mood" class="diary-icon">{{ d.mood }}</span>
          <span v-if="d.weather" class="diary-icon">{{ d.weather }}</span>
          <span v-if="d.visibility === 'PRIVATE'" class="diary-tag private-tag">{{ $t('diary.onlySelf') }}</span>
          <span v-else-if="d.visibility === 'PUBLIC'" class="diary-tag public-tag">{{ $t('diary.publicVisible') }}</span>
        </div>
        <div class="diary-content">{{ d.content }}</div>
        <div class="diary-footer">
          <span class="diary-author">{{ $t('diary.author') }}: {{ d.authorName || d.authorId || '-' }}</span>
          <div v-if="userStore.isLoggedIn && canEdit(d)" class="diary-more">
            <el-dropdown trigger="click" placement="bottom-end" @command="cmd => onCommand(cmd, d)">
              <button class="icon-btn">
                <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><circle cx="5" cy="12" r="1.8"/><circle cx="12" cy="12" r="1.8"/><circle cx="19" cy="12" r="1.8"/></svg>
              </button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="edit">{{ $t('common.edit') }}</el-dropdown-item>
                  <el-dropdown-item divided command="delete" class="danger-item">{{ $t('common.delete') }}</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </div>

      <div v-if="!loading && !list.length" class="empty-state">
        <el-empty :description="userStore.isGuest ? $t('diary.noData') : $t('diary.noData')">
          <button v-if="userStore.isLoggedIn" class="write-btn" @click="router.push('/diary/edit')">{{ $t('diary.emptyWriteBtn') }}</button>
        </el-empty>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { diaryApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb.vue'

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()
const list = ref([])
const loading = ref(false)

const formatDate = (d) => (d ? new Date(d).toLocaleString('zh-CN') : '')
const canEdit = (d) => userStore.isOwner || d.authorId === userStore.userInfo?.id

const load = async () => {
  loading.value = true
  try {
    const data = await diaryApi.list({ current: 1, size: 50 })
    list.value = data.records || []
  } finally {
    loading.value = false
  }
}

const onCommand = async (cmd, d) => {
  if (cmd === 'edit') {
    router.push(`/diary/edit/${d.id}`)
  } else if (cmd === 'delete') {
    try {
      await ElMessageBox.confirm(t('diary.deleteConfirm'), { type: 'warning', confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel'), closeOnClickModal: true })
      await diaryApi.remove(d.id)
      ElMessage.success(t('common.deleted'))
      await load()
    } catch (e) {
      if (e !== 'cancel') ElMessage.error(e.message || 'Failed')
    }
  }
}

onMounted(load)
</script>

<style scoped>
.diary-main { min-width: 0; }

.diary-item {
  margin-bottom: 14px;
  padding: 18px 20px;
  border-radius: 14px;
  position: relative;
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}
.diary-item:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 28px rgba(58,46,34,0.12);
}
html.dark .diary-item:hover { box-shadow: 0 8px 28px rgba(0,0,0,0.3); }

.diary-header { display: flex; gap: 8px; align-items: center; margin-bottom: 10px; flex-wrap: wrap; }
.diary-date { font-size: 13px; color: var(--color-text-secondary); font-weight: 500; }

.diary-tag {
  padding: 2px 8px;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.4;
  border: 1px solid transparent;
}
.diary-icon {
  font-size: 18px;
  line-height: 1;
}
.private-tag {
  background: rgba(58,46,34,0.06);
  border-color: rgba(58,46,34,0.1);
  color: var(--color-text-secondary);
}
.public-tag {
  background: rgba(107,155,107,0.1);
  border-color: rgba(107,155,107,0.15);
  color: #6b9b6b;
}
html.dark .private-tag { background: rgba(255,255,255,0.06); color: rgba(232,220,200,0.5); }
html.dark .public-tag { background: rgba(125,186,125,0.12); color: #7dba7d; }

.diary-content { white-space: pre-wrap; line-height: 1.7; color: var(--color-text); }

.diary-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
  padding-top: 8px;
  border-top: 1px solid var(--color-border);
}
.diary-author { font-size: 12px; color: var(--color-text-secondary); opacity: 0.7; }
.diary-more { opacity: 0; transition: opacity 0.2s; }
.diary-item:hover .diary-more { opacity: 1; }

.icon-btn {
  width: 28px; height: 28px;
  border: none; background: transparent;
  border-radius: 8px; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  color: var(--color-text-secondary);
  transition: background 0.2s, color 0.2s;
}
.icon-btn:hover { background: rgba(184,140,110,0.1); color: var(--color-accent, #b88c6e); }
html.dark .icon-btn:hover { background: rgba(212,178,152,0.1); color: #d4b298; }

.empty-state { padding: 48px 0; }

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
  .diary-more { opacity: 1; }
  .diary-card { padding: 12px; }
  .diary-images img { width: calc(33.33% - 4px); }
}
</style>
