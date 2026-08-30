<!-- 相册详情页:照片墙 + 分享/上传/备注编辑/删除(悬停显示操作),家长或上传者可管理;?token 分享模式供游客观看公开相册 -->
<template>
  <div class="page">
    <Breadcrumb :items="breadcrumbItems" />

    <div v-if="album.id" class="album-header card">
      <div class="album-head-info">
        <span class="album-name">{{ album.name }}</span>
        <el-tag size="small" :type="album.type === 'public' ? 'primary' : 'warning'">
          {{ album.type === 'public' ? t('album.public') : t('album.private') }}
        </el-tag>
        <span v-if="isMapped" class="mapped-badge">
          <span class="status-dot" :class="album.syncStatus || 'OFFLINE'" :title="statusText"></span>
          {{ t('album.mappedReadOnly') }} · {{ album.sourceDeviceName || '' }}
          <span v-if="album.lastSyncedAt" class="synced-at">· {{ t('album.lastSynced') }} {{ timeAgo(album.lastSyncedAt) }}</span>
        </span>
        <span class="photo-count">{{ t('album.photoCountLabel', { n: photos.length }) }}</span>
        <p v-if="album.description" class="album-desc">{{ album.description }}</p>
      </div>
      <div class="album-head-actions">
        <el-button
          v-if="isMapped && userStore.isOwner"
          class="ghost-btn"
          :loading="refreshing"
          @click="onRefresh"
        >{{ t('album.refreshMap') }}</el-button>
        <el-button v-if="canManageAlbum" class="ghost-btn" @click="pickCover">{{ t('album.setCover') }}</el-button>
        <el-button
          v-if="canManageAlbum && photos.length && !isMapped"
          class="ghost-btn"
          @click="toggleSelect"
        >{{ selectMode ? t('album.cancelSelect') : t('album.select') }}</el-button>
        <el-button
          v-if="album.type === 'public' && album.shareToken"
          class="ghost-btn"
          @click="copyAlbumShare"
        >{{ t('album.share') }}</el-button>
        <el-upload
          v-if="userStore.isLoggedIn && !shareToken && !isMapped"
          multiple
          :show-file-list="false"
          :http-request="uploadPhoto"
          accept="image/*"
        >
          <el-button type="primary">{{ t('album.uploadPhotos') }}</el-button>
        </el-upload>
      </div>
    </div>
    <input ref="coverInput" type="file" accept="image/*" class="hidden-input" @change="onCoverPicked" />

    <!-- 子相册(设备目录映射层级):方块/列表两种展示模式 -->
    <div v-if="children.length" class="child-section">
      <div class="child-toolbar">
        <span class="section-label">{{ t('album.childAlbums') }}</span>
        <div class="view-toggle">
          <button class="vt-btn" :class="{ active: childView === 'grid' }" :title="t('album.gridView')" @click="setChildView('grid')">
            <svg viewBox="0 0 16 16" width="14" height="14"><rect x="1.5" y="1.5" width="5" height="5" rx="1" fill="currentColor"/><rect x="9.5" y="1.5" width="5" height="5" rx="1" fill="currentColor"/><rect x="1.5" y="9.5" width="5" height="5" rx="1" fill="currentColor"/><rect x="9.5" y="9.5" width="5" height="5" rx="1" fill="currentColor"/></svg>
          </button>
          <button class="vt-btn" :class="{ active: childView === 'list' }" :title="t('album.listView')" @click="setChildView('list')">
            <svg viewBox="0 0 16 16" width="14" height="14"><rect x="1.5" y="2.5" width="13" height="3" rx="1" fill="currentColor"/><rect x="1.5" y="6.5" width="13" height="3" rx="1" fill="currentColor"/><rect x="1.5" y="10.5" width="13" height="3" rx="1" fill="currentColor"/></svg>
          </button>
        </div>
      </div>
      <!-- 方块模式:大封面卡片 -->
      <div v-if="childView === 'grid'" class="child-grid">
        <div v-for="c in children" :key="c.id" class="child-tile card" @click="$router.push(`/album/${c.id}`)">
          <div class="child-tile-cover">
            <template v-if="c.cover">
              <div class="child-tile-img" :style="{ backgroundImage: `url(${c.cover}&thumb=1)` }"></div>
            </template>
            <AlbumDefaultCover v-else :size="56" />
            <span class="status-dot" :class="c.syncStatus || 'OFFLINE'"></span>
          </div>
          <div class="child-tile-info">
            <span class="child-tile-name">{{ c.name }}</span>
            <span class="child-tile-meta">
              {{ t('album.photoCount', { n: c.photoCount }) }}<template v-if="c.childCount"> · {{ t('album.subAlbumCount', { n: c.childCount }) }}</template>
            </span>
          </div>
        </div>
      </div>
      <!-- 列表模式:横条 -->
      <div v-else class="child-list">
        <div
          v-for="c in children"
          :key="c.id"
          class="child-card card"
          @click="$router.push(`/album/${c.id}`)"
        >
          <div class="child-cover" :style="c.cover ? { backgroundImage: `url(${c.cover}&thumb=1)` } : {}">
            <AlbumDefaultCover v-if="!c.cover" :size="30" />
            <span class="status-dot" :class="c.syncStatus || 'OFFLINE'"></span>
          </div>
          <div class="child-info">
            <span class="child-name">{{ c.name }}</span>
            <span class="child-meta">
              {{ t('album.photoCount', { n: c.photoCount }) }}<template v-if="c.childCount"> · {{ t('album.subAlbumCount', { n: c.childCount }) }}</template>
            </span>
          </div>
        </div>
      </div>
    </div>

    <div v-loading="loading" class="album-body">
      <!-- 多选工具条 -->
      <div v-if="selectMode" class="select-bar card">
        <span>{{ t('album.selectedCount', { n: selectedIds.length }) }}</span>
        <div class="tb-right">
          <el-button size="small" :disabled="batchDeleting" @click="toggleSelect">{{ t('album.cancelSelect') }}</el-button>
          <el-button type="danger" size="small" :loading="batchDeleting" :disabled="!selectedIds.length" @click="onBatchDelete">{{ t('album.deleteSelected') }}</el-button>
        </div>
      </div>
      <div v-if="photos.length" class="photo-wall">
        <div v-for="p in photos" :key="p.id" class="photo-card" :class="{ selected: selectMode && selectedIds.includes(p.id) }">
          <div class="photo-wrap" @click="selectMode ? togglePick(p) : openViewer(p)">
            <img :src="thumbUrl(p.url)" :alt="p.description || album.name" loading="lazy" />
            <span v-if="selectMode" class="pick-badge" :class="{ on: selectedIds.includes(p.id) }">
              <svg viewBox="0 0 16 16" width="11" height="11"><path d="M3 8.5 L6.5 12 L13 4.5" fill="none" stroke="#fff" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/></svg>
            </span>
            <div v-if="!selectMode" class="photo-hover">
              <span v-if="canManagePhoto(p)" @click.stop="openDesc(p)"><el-icon><Edit /></el-icon>{{ t('album.editNote') }}</span>
              <span v-if="canManagePhoto(p)" class="danger" @click.stop="onDelPhoto(p)"><el-icon><Delete /></el-icon>{{ t('common.delete') }}</span>
            </div>
          </div>
          <div v-if="p.description" class="photo-desc">{{ p.description }}</div>
          <div v-if="p.location || p.takenAt" class="photo-meta">
            <el-icon v-if="p.location"><Location /></el-icon>{{ p.location }}
            <el-icon v-if="p.takenAt"><Clock /></el-icon>{{ formatDateTime(p.takenAt) }}
          </div>
        </div>
      </div>
      <el-empty v-else :description="userStore.isGuest ? t('album.noPublicPhotos') : t('album.emptyPhotoHint')" />
    </div>

    <el-dialog v-model="descEditor.visible" append-to-body :title="t('album.noteTitle')" width="420px">
      <el-input v-model="descEditor.value" type="textarea" :rows="3" :placeholder="t('album.notePlaceholder')" />
      <template #footer>
        <el-button @click="descEditor.visible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="onSaveDesc">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <PhotoViewer
      v-model:visible="viewer.visible"
      :photos="photos"
      :initial-index="viewer.index"
      :share-base="shareBase"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { albumApi, photoApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Edit, Delete, Location, Clock } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PhotoViewer from '@/components/PhotoViewer.vue'
import AlbumDefaultCover from '@/components/AlbumDefaultCover.vue'
import { shareId } from '@/utils/shareId'
import { useSyncStore } from '@/stores/sync'

const route = useRoute()
const userStore = useUserStore()
const { t } = useI18n()
const shareToken = route.params.token || ''
const albumId = route.params.id
const album = ref({})
const photos = ref([])
const children = ref([])
const refreshing = ref(false)
const descEditor = reactive({ visible: false, value: '', currentId: null })
const viewer = reactive({ visible: false, index: 0 })

// 映射相册:只读(影子记录),隐藏上传与照片管理
const isMapped = computed(() => !!album.value.sourceDeviceId)
const statusText = computed(() => ({
  VALID: t('album.statusValid'),
  OFFLINE: t('album.statusOffline'),
  MISSING: t('album.statusMissing'),
  SYNCING: t('album.statusSyncing'),
}[album.value.syncStatus] || ''))

// 相对时间(x 前)
const timeAgo = (d) => {
  const s = (Date.now() - new Date(d).getTime()) / 1000
  if (s < 60) return t('album.justNow')
  if (s < 3600) return Math.floor(s / 60) + t('album.minutesAgo')
  if (s < 86400) return Math.floor(s / 3600) + t('album.hoursAgo')
  return Math.floor(s / 86400) + t('album.daysAgo')
}

// 照片管理权限:家长或上传者本人;映射相册只读,不提供编辑/删除
const canManagePhoto = (p) =>
  !isMapped.value && userStore.isLoggedIn && (userStore.isOwner || p.authorId === userStore.userInfo?.id)

// 相册分享链接基准(公开相册才有):游客凭链接查看,家庭需已公开
const shareBase = computed(() =>
  album.value.shareToken ? `${location.origin}/album/shared/${album.value.shareToken}` : ''
)

const formatDateTime = (d) => (d ? new Date(d).toLocaleString('zh-CN') : '')

// 拉取相册详情与照片列表:分享模式走令牌接口(游客可访问),普通模式走详情接口
const load = async () => {
  const data = shareToken ? await albumApi.shared(shareToken) : await albumApi.detail(albumId)
  album.value = data.album || {}
  photos.value = data.photos || []
  children.value = data.children || []
  parents.value = data.parents || []
  // 分享链接带 ?p= 混淆照片ID时,定位到该照片并打开播放页
  const p = route.query.p
  if (p) {
    const idx = photos.value.findIndex(x => shareId(x.id) === String(p))
    if (idx >= 0) {
      viewer.index = idx
      viewer.visible = true
    }
  }
}

// 面包屑:家庭相册 → 各级父相册(可点) → 当前相册
const parents = ref([])
const breadcrumbItems = computed(() => [
  { label: t('album.familyTitle'), to: '/album' },
  ...parents.value.map((p) => ({ label: p.name, to: `/album/${p.id}` })),
  { label: album.value.name || t('album.title') },
])

// 网格用 480px 缓存缩略图(首次访问服务端生成后秒回);PhotoViewer 播放/下载仍用原图
const thumbUrl = (url) => (url ? `${url}&thumb=1` : url)

// 手动刷新映射相册(递归子树,后台执行)
const onRefresh = async () => {
  refreshing.value = true
  try {
    await albumApi.refresh(albumId)
    ElMessage.success(t('album.refreshStarted'))
  } finally {
    refreshing.value = false
  }
}

// 上传单张照片成功后刷新照片墙
const uploadPhoto = async (options) => {
  await photoApi.upload(albumId, [options.file])
  ElMessage.success(t('album.uploadSuccess'))
  load()
}

// 复制相册分享链接(仅家庭已公开时外人可打开,否则 404)
const copyAlbumShare = async () => {
  try {
    await navigator.clipboard.writeText(shareBase.value)
    ElMessage.success(t('photoViewer.linkCopied'))
  } catch {
    ElMessage.error(t('photoViewer.copyFailed'))
  }
}

// 打开备注编辑框,记录当前照片 id
const openDesc = (p) => {
  descEditor.currentId = p.id
  descEditor.value = p.description || ''
  descEditor.visible = true
}

const onSaveDesc = async () => {
  await photoApi.updateDesc(descEditor.currentId, descEditor.value)
  ElMessage.success(t('common.saved'))
  descEditor.visible = false
  load()
}

const onDelPhoto = async (p) => {
  await ElMessageBox.confirm(t('album.photoDeleteConfirm'), t('common.tip'), { type: 'warning', closeOnClickModal: true })
  await photoApi.remove(p.id)
  ElMessage.success(t('common.deleted'))
  load()
}

const openViewer = (p) => {
  viewer.index = photos.value.findIndex(x => x.id === p.id)
  if (viewer.index < 0) viewer.index = 0
  viewer.visible = true
}

// ---------- 相册封面(自定义,优先于照片封面) ----------
const coverInput = ref(null)
// 相册管理权限:家长或创建者,且非游客分享模式
const canManageAlbum = computed(() =>
  !shareToken && userStore.isLoggedIn && (userStore.isOwner || album.value.createdBy === userStore.userInfo?.id)
)
const pickCover = () => coverInput.value?.click()
const onCoverPicked = async (e) => {
  const file = e.target.files?.[0]
  if (!file) return
  await albumApi.setCover(albumId, file)
  ElMessage.success(t('common.saved'))
  e.target.value = ''
  load()
}

// ---------- 子相册展示模式(方块/列表,localStorage 记忆) ----------
const childView = ref(localStorage.getItem('ihomy:album:childView') || 'grid')
const setChildView = (v) => {
  childView.value = v
  localStorage.setItem('ihomy:album:childView', v)
}

// ---------- 照片多选删除 ----------
const selectMode = ref(false)
const selectedIds = ref([])
const batchDeleting = ref(false)
const toggleSelect = () => {
  selectMode.value = !selectMode.value
  selectedIds.value = []
}
const togglePick = (p) => {
  const i = selectedIds.value.indexOf(p.id)
  if (i >= 0) selectedIds.value.splice(i, 1)
  else selectedIds.value.push(p.id)
}
const onBatchDelete = async () => {
  await ElMessageBox.confirm(t('album.photoBatchDeleteConfirm', { n: selectedIds.value.length }), t('common.tip'), { type: 'warning', closeOnClickModal: true })
  batchDeleting.value = true
  try {
    for (const id of [...selectedIds.value]) {
      await photoApi.remove(id)
    }
    ElMessage.success(t('common.deleted'))
    selectedIds.value = []
    selectMode.value = false
    load()
  } finally {
    batchDeleting.value = false
  }
}

// ---------- 后台同步完成时自动刷新(停留在本页也能看到新照片) ----------
const syncStore = useSyncStore()
watch(syncStore.doneCount, () => load())

onMounted(load)
</script>

<style scoped>
.hidden-input { display: none; }
.album-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
  padding: 10px 16px; /* 对齐全局 .page-toolbar 规范高度 */
}
.album-header h2 { color: var(--color-primary); margin-bottom: 6px; }
.album-head-info { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.album-name {
  font-size: 17px;
  font-weight: 600;
  color: var(--color-primary);
  display: inline-flex;
  align-items: center;
}
.photo-count { font-size: 13px; color: var(--color-text-secondary); }
.mapped-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--color-text-secondary);
  padding: 3px 10px;
  border-radius: 8px;
  background: var(--color-bg-2, #f3eee6);
}
.mapped-badge .synced-at { color: var(--color-text-secondary); opacity: 0.7; }
.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}
.status-dot.VALID { background: #67b26b; box-shadow: 0 0 4px rgba(103, 178, 107, 0.9); }
.status-dot.OFFLINE, .status-dot.SYNCING { background: #9a9a9a; }
.status-dot.MISSING { background: #b96058; box-shadow: 0 0 4px rgba(185, 96, 88, 0.9); }
.child-section { margin-bottom: 20px; }
.child-toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; }
.child-list { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 10px; }
.child-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 14px; }
.child-tile { overflow: hidden; cursor: pointer; transition: transform 0.15s, box-shadow 0.15s; }
.child-tile:hover { transform: translateY(-3px); box-shadow: 0 8px 24px rgba(31,58,95,0.15); }
.child-tile-cover { position: relative; aspect-ratio: 4 / 3; }
.child-tile-img { width: 100%; height: 100%; background-size: cover; background-position: center; }
.child-tile-cover .status-dot { position: absolute; top: 8px; right: 8px; border: 2px solid var(--color-bg, #fcf8f0); }
.child-tile-info { padding: 10px 12px; display: flex; flex-direction: column; gap: 3px; }
.child-tile-name { font-size: 14px; font-weight: 600; color: var(--color-text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.child-tile-meta { font-size: 12px; color: var(--color-text-secondary); }
.child-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}
.child-card:hover { transform: translateY(-2px); box-shadow: 0 6px 18px rgba(31,58,95,0.12); }
.child-cover {
  position: relative;
  width: 52px;
  height: 52px;
  border-radius: 8px;
  overflow: hidden;
  background-size: cover;
  background-position: center;
  background-color: var(--color-bg-2, #eef2f7);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.child-cover .status-dot { position: absolute; bottom: -2px; right: -2px; border: 2px solid var(--color-bg, #fcf8f0); }
.child-info { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.child-name { font-size: 14px; font-weight: 600; color: var(--color-text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.child-meta { font-size: 12px; color: var(--color-text-secondary); }
.select-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.select-bar .tb-right { display: flex; gap: 8px; }
.photo-card.selected .photo-wrap { outline: 3px solid var(--color-primary, #b88c6e); outline-offset: -3px; }
.pick-badge {
  position: absolute;
  top: 8px;
  left: 8px;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.85);
  border: 2px solid rgba(184, 140, 110, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
}
.pick-badge.on { background: #b88c6e; border-color: #b88c6e; }
.album-desc { margin-top: 8px; color: var(--color-text-secondary); font-size: 13px; }
.album-head-actions { display: flex; flex-direction: row; align-items: center; gap: 8px; flex-shrink: 0; }
.album-body { margin-top: 20px; }
.photo-wall {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 14px;
}
.photo-card { position: relative; }
.photo-wrap {
  position: relative;
  aspect-ratio: 1 / 1;
  overflow: hidden;
  border-radius: 10px;
  cursor: pointer;
}
.photo-wrap img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.2s;
}
.photo-wrap:hover img { transform: scale(1.05); }
.photo-hover {
  position: absolute;
  inset: 0;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
  background: rgba(0, 0, 0, 0.45);
  opacity: 0;
  transition: opacity 0.2s;
}
.photo-wrap:hover .photo-hover { opacity: 1; }
.photo-hover span {
  color: #fff;
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.18);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.photo-hover span.danger:hover { background: rgba(214, 48, 49, 0.7); }
.photo-desc {
  margin-top: 6px;
  font-size: 12px;
  color: var(--color-text-secondary);
  line-height: 1.4;
}
.photo-meta {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-top: 2px;
  font-size: 11px;
  color: var(--color-text-secondary);
}

@media (max-width: 768px) {
  .photo-wall { grid-template-columns: repeat(2, 1fr); gap: 6px; }
  .album-header { flex-direction: column; align-items: flex-start; }
  .album-title { font-size: 18px; }
  .upload-bar { flex-direction: column; gap: 8px; }
}
</style>