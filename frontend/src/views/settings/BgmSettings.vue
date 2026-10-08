<template>
  <!-- 背景音乐设置:上传单曲/专辑 + 歌单管理(设为背景音乐) -->
  <div class="card settings-card">
    <div class="section-label">{{ $t('settings.bgmTitle') }}</div>
    <el-form label-position="top">
      <el-form-item :label="$t('settings.bgmUploadMusic')">
        <div class="music-actions">
          <el-upload :show-file-list="false" :http-request="uploadMusic" accept="audio/*">
            <el-button>{{ $t('settings.bgmUploadTrack') }}</el-button>
          </el-upload>
          <el-button @click="triggerFolderInput">{{ $t('settings.bgmUploadFolder') }}</el-button>
          <input
            ref="folderInputRef"
            type="file"
            webkitdirectory
            multiple
            accept="audio/*"
            style="display: none"
            @change="onFolderChange"
          />
          <el-button class="ghost-btn" @click="openPlaylistDialog">{{ $t('settings.bgmNewPlaylist') }}</el-button>
        </div>
      </el-form-item>
      <el-form-item :label="$t('settings.bgmPlaylists')">
        <div class="bg-playlist-list">
          <BgmPlaylistItem v-for="p in visiblePlaylists" :key="p.id" :p="p" @set="setBackground" />
          <div v-a11y-click v-if="allPlaylists.length > 5" class="bg-more" @click="showAllPlaylists = true">
            {{ $t('settings.bgmMore', { n: allPlaylists.length - 5 }) }}
          </div>
        </div>
        <el-empty v-if="!allPlaylists.length && !playlistLoading" :description="$t('settings.bgmEmpty')" :image-size="40" />
      </el-form-item>
      <div class="share-tip">
        {{ $t('settings.bgmTip')
        }}<a href="javascript:void(0)" class="link-text" @click="$router.push('/music')">{{ $t('settings.bgmTipLink') }}</a>
      </div>
    </el-form>
  </div>

  <!-- 专辑上传弹窗:输入专辑名后批量上传 -->
  <el-dialog v-model="albumDialog.visible" :title="$t('settings.uploadAlbumTitle')" width="380px" append-to-body>
    <div class="share-tip">{{ $t('settings.albumFilesHint', { n: albumDialog.files.length }) }}</div>
    <el-input v-model="albumDialog.name" :placeholder="$t('settings.albumNamePh')" @keyup.enter="confirmUploadAlbum" />
    <template #footer>
      <el-button @click="albumDialog.visible = false">{{ $t('common.cancel') }}</el-button>
      <el-button
        type="primary"
        :disabled="!albumDialog.name.trim() || albumDialog.uploading"
        :loading="albumDialog.uploading"
        @click="confirmUploadAlbum"
        >{{ $t('common.upload') }}</el-button
      >
    </template>
  </el-dialog>

  <!-- 新建歌单弹窗 -->
  <el-dialog v-model="plDialog.visible" :title="$t('settings.bgmNewPlaylist')" width="380px" append-to-body>
    <el-input v-model="plDialog.name" :placeholder="$t('settings.playlistNamePh')" @keyup.enter="createPlaylist" />
    <template #footer>
      <el-button @click="plDialog.visible = false">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" @click="createPlaylist">{{ $t('settings.create') }}</el-button>
    </template>
  </el-dialog>

  <!-- 全部歌单弹窗 -->
  <el-dialog v-model="showAllPlaylists" :title="$t('settings.bgmAllPlaylists')" width="560px" append-to-body>
    <div class="bg-playlist-list">
      <BgmPlaylistItem v-for="p in allPlaylists" :key="p.id" :p="p" @set="setBackground" />
    </div>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { musicApi } from '@/api'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import BgmPlaylistItem from '@/components/BgmPlaylistItem.vue'

const { t } = useI18n()
const userStore = useUserStore()

// 背景音乐设置:歌单管理
const allPlaylists = ref([])
const playlistLoading = ref(false)
const showAllPlaylists = ref(false)
const visiblePlaylists = computed(() => allPlaylists.value.slice(0, 5))

const loadPlaylists = async () => {
  if (!userStore.isLoggedIn) return
  playlistLoading.value = true
  try {
    allPlaylists.value = await musicApi.playlistList()
  } catch (e) {
    allPlaylists.value = []
  } finally {
    playlistLoading.value = false
  }
}

const uploadMusic = async (options) => {
  try {
    await musicApi.upload(options.file)
    ElMessage.success(t('settings.uploadedToLibrary'))
  } catch {
    ElMessage.error(t('settings.uploadFailed'))
  }
}

const folderInputRef = ref(null)
const triggerFolderInput = () => folderInputRef.value?.click()
const albumDialog = reactive({ visible: false, name: '', files: [], uploading: false })
const onFolderChange = (e) => {
  const files = Array.from(e.target.files || []).filter((f) => f.type.startsWith('audio/'))
  if (!files.length) {
    ElMessage.warning(t('settings.noAudioInFolder'))
    e.target.value = ''
    return
  }
  const rel = files[0].webkitRelativePath || ''
  albumDialog.files = files
  albumDialog.name = rel ? rel.split('/')[0] : ''
  albumDialog.visible = true
  e.target.value = ''
}
const confirmUploadAlbum = async () => {
  const albumName = albumDialog.name.trim()
  if (!albumName || !albumDialog.files.length) return
  albumDialog.uploading = true
  try {
    await musicApi.uploadAlbum(albumDialog.files, albumName)
    ElMessage.success(t('settings.albumUploaded', { name: albumName, n: albumDialog.files.length }))
    albumDialog.visible = false
  } catch (e) {
    ElMessage.error(t('settings.uploadFailed'))
  } finally {
    albumDialog.uploading = false
  }
}

const plDialog = reactive({ visible: false, name: '' })
const openPlaylistDialog = () => {
  plDialog.name = ''
  plDialog.visible = true
}
const createPlaylist = async () => {
  if (!plDialog.name.trim()) return
  try {
    await musicApi.createPlaylist(plDialog.name.trim())
    ElMessage.success(t('settings.playlistCreated'))
    plDialog.visible = false
    await loadPlaylists()
  } catch (e) {
    ElMessage.error(t('settings.createFailed'))
  }
}

const setBackground = async (p) => {
  try {
    await musicApi.setBackground(p.id)
    ElMessage.success(t('settings.bgmSetDone', { name: p.name }))
    userStore.bumpBgMusic()
    await loadPlaylists()
  } catch (e) {
    ElMessage.error(t('settings.setFailed'))
  }
}

onMounted(() => {
  loadPlaylists()
})
</script>

<style scoped>
.settings-card {
  margin-bottom: 16px;
  background: var(--color-card);
}
.share-tip {
  color: #776e62;
  font-size: 12px;
  margin-top: 8px;
  line-height: 1.4;
  width: 100%;
}
html.dark .share-tip {
  color: #9a9088;
}
.music-name {
  color: var(--color-text-2);
  font-size: 13px;
}
.music-audio {
  width: 100%;
  height: 36px;
}
.music-actions {
  display: flex;
  gap: 8px;
}
.playlist-mgmt {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
}
.playlist-mgmt-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: var(--color-card-2);
  border-radius: 8px;
  font-size: 13px;
}
.playlist-mgmt-item .pl-idx {
  width: 20px;
  text-align: center;
  opacity: 0.5;
}
.playlist-mgmt-item .pl-album {
  font-size: 11px;
  color: var(--color-accent);
  background: rgba(168, 72, 58, 0.06);
  padding: 1px 7px;
  border-radius: 6px;
  white-space: nowrap;
}
.playlist-mgmt-item .pl-title {
  flex: 1;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.playlist-mgmt-item .pl-url {
  font-size: 11px;
  color: var(--color-text-secondary);
  max-width: 200px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  opacity: 0.6;
}

/* 背景音乐歌单列表(条目样式见 BgmPlaylistItem.vue) */
.bg-playlist-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}
.bg-more {
  text-align: center;
  padding: 8px;
  font-size: 13px;
  color: var(--color-accent);
  cursor: pointer;
  border-radius: 8px;
}
.bg-more:hover {
  background: rgba(168, 72, 58, 0.06);
}
.link-text {
  color: var(--color-accent);
  text-decoration: underline;
}

@media (max-width: 768px) {
  /* 窄屏下横向操作行换行,避免溢出 */
  .music-actions {
    flex-wrap: wrap;
  }
}
</style>
