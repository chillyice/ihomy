<!-- 相册详情页:照片墙 + 上传/备注编辑/删除(悬停显示操作),家长或上传者可管理 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: t('album.familyTitle'), to: '/album' }, { label: album.name || t('album.title') }]" />

    <div v-if="album.id" class="album-header card">
      <div class="album-head-info">
        <span class="album-name">{{ album.name }}</span>
        <el-tag size="small" :type="album.type === 'public' ? 'primary' : 'warning'">
          {{ album.type === 'public' ? t('album.public') : t('album.private') }}
        </el-tag>
        <span class="photo-count">{{ t('album.photoCountLabel', { n: album.photoCount }) }}</span>
        <p v-if="album.description" class="album-desc">{{ album.description }}</p>
      </div>
      <div class="album-head-actions">
        <el-button v-if="photos.length" @click="startSlideshow">{{ t('photoViewer.slideshow') }}</el-button>
        <el-upload
          v-if="userStore.isLoggedIn"
          multiple
          :show-file-list="false"
          :http-request="uploadPhoto"
          accept="image/*"
        >
          <el-button type="primary">{{ t('album.uploadPhotos') }}</el-button>
        </el-upload>
      </div>
    </div>

    <div v-loading="loading" class="album-body">
      <div v-if="photos.length" class="photo-wall">
        <div v-for="p in photos" :key="p.id" class="photo-card">
          <div class="photo-wrap" @click="openViewer(p)">
            <img :src="p.url" :alt="p.description || album.name" loading="lazy" />
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
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { albumApi, photoApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Edit, Delete, Location, Clock } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PhotoViewer from '@/components/PhotoViewer.vue'

const route = useRoute()
const userStore = useUserStore()
const { t } = useI18n()
const albumId = route.params.id
const album = ref({})
const photos = ref([])
const descEditor = reactive({ visible: false, value: '', currentId: null })
const viewer = reactive({ visible: false, index: 0 })

// 照片管理权限:家长或上传者本人
const canManagePhoto = (p) =>
  userStore.isLoggedIn && (userStore.isOwner || p.authorId === userStore.userInfo?.id)

const formatDateTime = (d) => (d ? new Date(d).toLocaleString('zh-CN') : '')

// 拉取相册详情与照片列表
const load = async () => {
  const data = await albumApi.detail(albumId)
  album.value = data.album || {}
  photos.value = data.photos || []
}

// 上传单张照片成功后刷新照片墙
const uploadPhoto = async (options) => {
  await photoApi.upload(albumId, [options.file])
  ElMessage.success(t('album.uploadSuccess'))
  load()
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

const startSlideshow = () => {
  viewer.index = 0
  viewer.visible = true
}

onMounted(load)
</script>

<style scoped>
.album-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 16px;
  padding: 20px;
}
.album-header h2 { color: var(--color-primary); margin-bottom: 6px; }
.photo-count { margin-left: 10px; font-size: 13px; color: var(--color-text-secondary); }
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