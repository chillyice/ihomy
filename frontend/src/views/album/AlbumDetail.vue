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

    <!-- 子相册(设备目录映射层级) -->
    <div v-if="children.length" class="child-albums">
      <div
        v-for="c in children"
        :key="c.id"
        class="child-card card"
        @click="$router.push(`/album/${c.id}`)"
      >
        <div class="child-cover" :style="c.cover ? { backgroundImage: `url(${c.cover}&thumb=1)` } : {}">
          <span v-if="!c.cover" class="child-cover-empty">📷</span>
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

    <div v-loading="loading" class="album-body">
      <div v-if="photos.length" class="photo-wall">
        <div v-for="p in photos" :key="p.id" class="photo-card">
          <div class="photo-wrap" @click="openViewer(p)">
            <img :src="thumbUrl(p.url)" :alt="p.description || album.name" loading="lazy" />
            <div class="photo-hover">
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
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { albumApi, photoApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Edit, Delete, Location, Clock } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PhotoViewer from '@/components/PhotoViewer.vue'
import { shareId } from '@/utils/shareId'

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

onMounted(load)
</script>

<style scoped>
.album-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
  padding: 20px;
}
.album-header h2 { color: var(--color-primary); margin-bottom: 6px; }
.album-head-info { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.album-name {
  font-size: 20px;
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
.child-albums {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 14px;
  margin-bottom: 20px;
}
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
  background-size: cover;
  background-position: center;
  background-color: var(--color-bg-2, #eef2f7);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.child-cover-empty { font-size: 22px; }
.child-cover .status-dot { position: absolute; bottom: -2px; right: -2px; border: 2px solid var(--color-bg, #fcf8f0); }
.child-info { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.child-name { font-size: 14px; font-weight: 600; color: var(--color-text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.child-meta { font-size: 12px; color: var(--color-text-secondary); }
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