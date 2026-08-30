<!-- 相册列表页:封面/类型/照片数卡片网格,登录后可新建,家长或创建者可管理 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: t('album.title') }]" />

    <div class="page-toolbar card">
      <div class="tb-right">
        <template v-if="!selectMode">
          <el-button v-if="userStore.isLoggedIn && topAlbums.length" @click="toggleSelect">{{ t('album.select') }}</el-button>
          <el-button v-if="userStore.isLoggedIn" type="primary" @click="openEditor()">{{ t('album.newAlbum') }}</el-button>
          <el-button v-if="userStore.isOwner" @click="syncVisible = true">{{ t('album.syncFromDevice') }}</el-button>
        </template>
        <template v-else>
          <span class="select-count">{{ t('album.selectedAlbums', { n: selectedIds.length }) }}</span>
          <el-button size="small" :disabled="batchDeleting" @click="toggleSelect">{{ t('album.cancelSelect') }}</el-button>
          <el-button type="danger" size="small" :loading="batchDeleting" :disabled="!selectedIds.length" @click="onBatchDelete">{{ t('album.deleteSelected') }}</el-button>
        </template>
      </div>
    </div>

    <div v-loading="loading">
      <div v-if="topAlbums.length" class="album-grid">
        <div
          v-for="a in topAlbums"
          :key="a.id"
          class="album-card card"
          :class="{ selected: selectMode && selectedIds.includes(a.id) }"
          @click="selectMode ? togglePick(a) : $router.push(`/album/${a.id}`)"
        >
          <div class="album-cover-wrap">
            <div
              v-if="a.cover"
              class="album-cover"
              :style="{ backgroundImage: `url(${a.cover})` }"
            ></div>
            <div v-else class="album-cover">
              <AlbumDefaultCover :size="64" />
            </div>
            <span class="album-type" :class="a.type">{{ a.type === 'public' ? t('album.public') : t('album.private') }}</span>
            <span v-if="a.sourceDeviceName" class="album-source">
              <span class="status-dot" :class="a.syncStatus || 'OFFLINE'"></span>{{ a.sourceDeviceName }}
            </span>
            <span class="album-count">{{ t('album.photoCount', { n: a.totalPhotoCount ?? a.photoCount }) }}</span>
            <span v-if="a.childCount" class="album-subcount">{{ t('album.subAlbumCount', { n: a.childCount }) }}</span>
            <span v-if="selectMode" class="pick-badge" :class="{ on: selectedIds.includes(a.id) }">
              <svg viewBox="0 0 16 16" width="12" height="12"><path d="M3 8.5 L6.5 12 L13 4.5" fill="none" stroke="#fff" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/></svg>
            </span>
          </div>
          <div class="album-info">
            <div class="album-name">{{ a.name }}</div>
            <div class="album-meta">{{ formatDate(a.createdAt) }}</div>
          </div>
          <div v-if="!selectMode && canManage(a)" class="album-actions" @click.stop>
            <el-button size="small" text @click="openEditor(a)">{{ t('common.edit') }}</el-button>
            <el-button size="small" text type="danger" @click="onDel(a)">{{ t('common.delete') }}</el-button>
          </div>
        </div>
      </div>
      <el-empty v-else :description="userStore.isGuest ? t('album.noPublicAlbum') : t('album.emptyHint')" />
    </div>

    <el-dialog v-model="editor.visible" append-to-body :title="editor.form.id ? t('album.editTitle') : t('album.newAlbum')" width="420px">
      <el-form :model="editor.form" label-position="top">
        <el-form-item :label="t('album.albumName')">
          <el-input v-model="editor.form.name" :placeholder="t('album.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('album.visibility')">
          <el-radio-group v-model="editor.form.type">
            <el-radio value="public">{{ t('album.publicOption') }}</el-radio>
            <el-radio value="private">{{ t('album.privateOption') }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editor.visible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="onSave">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
    <SyncDialog v-model="syncVisible" @synced="load" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { albumApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { useSyncStore } from '@/stores/sync'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import Breadcrumb from '@/components/Breadcrumb.vue'
import SyncDialog from '@/components/SyncDialog.vue'
import AlbumDefaultCover from '@/components/AlbumDefaultCover.vue'

const { t } = useI18n()
const userStore = useUserStore()
const syncVisible = ref(false)
const albums = ref([])
const loading = ref(false)
const editor = reactive({ visible: false, form: { id: null, name: '', type: 'public' } })

// 顶层相册:层级映射的子相册不直接出现在列表页,进入父相册查看
const topAlbums = computed(() => albums.value.filter((a) => !a.parentId))

// 管理权限:家长或相册创建者本人
const canManage = (a) =>
  userStore.isLoggedIn && (userStore.isOwner || a.createdBy === userStore.userInfo?.id)
const formatDate = (d) => (d ? new Date(d).toLocaleDateString('zh-CN') : '')

const load = async () => {
  loading.value = true
  try {
    albums.value = await albumApi.list()
  } finally {
    loading.value = false
  }
}

const openEditor = (a) => {
  if (a) Object.assign(editor.form, { id: a.id, name: a.name, type: a.type })
  else Object.assign(editor.form, { id: null, name: '', type: 'public' })
  editor.visible = true
}

const onSave = async () => {
  if (!editor.form.name) return ElMessage.warning(t('album.nameRequired'))
  if (editor.form.id) await albumApi.update(editor.form.id, editor.form)
  else await albumApi.create(editor.form)
  ElMessage.success(t('album.saved'))
  editor.visible = false
  load()
}

// 删除相册:映射相册=解除映射(不碰设备文件),普通相册=连同照片删除
const onDel = async (a) => {
  const msg = a.sourceDeviceId
    ? t('album.unmapConfirm', { name: a.name, device: a.sourceDeviceName })
    : t('album.deleteConfirm', { name: a.name })
  await ElMessageBox.confirm(msg, t('common.tip'), { type: 'warning', closeOnClickModal: true })
  await albumApi.remove(a.id)
  ElMessage.success(t('common.deleted'))
  load()
}

// ---------- 多选删除 ----------
const selectMode = ref(false)
const selectedIds = ref([])
const batchDeleting = ref(false)
const toggleSelect = () => {
  selectMode.value = !selectMode.value
  selectedIds.value = []
}
const togglePick = (a) => {
  const i = selectedIds.value.indexOf(a.id)
  if (i >= 0) selectedIds.value.splice(i, 1)
  else selectedIds.value.push(a.id)
}
const onBatchDelete = async () => {
  const targets = topAlbums.value.filter((a) => selectedIds.value.includes(a.id))
  const mapped = targets.filter((a) => a.sourceDeviceId)
  const msg = mapped.length
    ? t('album.batchUnmapConfirm', { n: selectedIds.value.length })
    : t('album.batchDeleteConfirm', { n: selectedIds.value.length })
  await ElMessageBox.confirm(msg, t('common.tip'), { type: 'warning', closeOnClickModal: true })
  batchDeleting.value = true
  try {
    for (const id of [...selectedIds.value]) {
      await albumApi.remove(id)
    }
    ElMessage.success(t('common.deleted'))
    selectedIds.value = []
    selectMode.value = false
    load()
  } finally {
    batchDeleting.value = false
  }
}

// ---------- 后台同步完成时自动刷新列表 ----------
const syncStore = useSyncStore()
watch(syncStore.doneCount, () => load())

onMounted(load)
</script>

<style scoped>
.select-count { font-size: 13px; color: var(--color-text-secondary); margin-right: 8px; }
.album-card.selected { outline: 3px solid var(--color-primary, #b88c6e); outline-offset: -3px; }
.pick-badge {
  position: absolute;
  top: 10px;
  left: 10px;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.85);
  border: 2px solid rgba(184, 140, 110, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2;
}
.pick-badge.on { background: #b88c6e; border-color: #b88c6e; }
.album-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}
.album-card { overflow: hidden; cursor: pointer; transition: transform 0.15s, box-shadow 0.15s; }
.album-card:hover { transform: translateY(-3px); box-shadow: 0 8px 24px rgba(31,58,95,0.15); }
.album-cover-wrap { position: relative; aspect-ratio: 4 / 3; }
.album-cover {
  width: 100%;
  height: 100%;
  background-size: cover;
  background-position: center;
}
.album-type {
  position: absolute;
  top: 10px;
  left: 10px;
  font-size: 11px;
  color: #fff;
  padding: 2px 10px;
  border-radius: 10px;
  background: rgba(46, 116, 181, 0.85);
}
.album-type.private { background: rgba(230, 162, 60, 0.9); }
.album-source {
  position: absolute;
  top: 10px;
  right: 10px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  color: #fff;
  padding: 2px 10px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
}
.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  display: inline-block;
}
.status-dot.VALID { background: #67b26b; box-shadow: 0 0 4px rgba(103, 178, 107, 0.9); }
.status-dot.OFFLINE, .status-dot.SYNCING { background: #9a9a9a; }
.status-dot.MISSING { background: #b96058; box-shadow: 0 0 4px rgba(185, 96, 88, 0.9); }
.album-count {
  position: absolute;
  bottom: 10px;
  right: 10px;
  font-size: 12px;
  color: #fff;
  background: rgba(0, 0, 0, 0.5);
  padding: 2px 10px;
  border-radius: 10px;
}
.album-subcount {
  position: absolute;
  bottom: 10px;
  left: 10px;
  font-size: 11px;
  color: #fff;
  background: rgba(0, 0, 0, 0.5);
  padding: 2px 10px;
  border-radius: 10px;
}
.album-info { padding: 14px 16px 6px; }
.album-name { font-size: 15px; font-weight: 600; color: var(--color-text); }
.album-meta { font-size: 12px; color: var(--color-text-secondary); margin-top: 4px; }
.album-actions { padding: 6px 8px 10px; text-align: right; }

@media (max-width: 768px) {
  .album-grid { grid-template-columns: repeat(2, 1fr); gap: 10px; }
  .album-name { font-size: 14px; }
}
</style>