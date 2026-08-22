<template>
  <div class="page">
    <Breadcrumb :items="[{ label: '音乐' }]" />

    <div class="list-header">
      <div class="header-actions">
        <el-upload :show-file-list="false" :http-request="uploadSingle" accept="audio/*">
          <el-button class="sec-btn">上传单曲</el-button>
        </el-upload>
        <el-button class="sec-btn" @click="triggerFolderInput">上传专辑文件夹</el-button>
        <input ref="folderInputRef" type="file" webkitdirectory multiple accept="audio/*" style="display:none" @change="onFolderChange" />
        <el-button class="sec-btn" @click="openPlaylistDialog">新建歌单</el-button>
        <el-button class="sec-btn" @click="linkDialog.visible = true">添加外链</el-button>
        <template v-if="selectMode && tab === 'all'">
          <el-button type="danger" :disabled="!selectedIds.length" @click="batchDeleteTracks">删除选中 ({{ selectedIds.length }})</el-button>
          <el-button class="sec-btn" @click="exitSelect">取消多选</el-button>
        </template>
        <template v-else-if="selectMode && tab === 'album'">
          <el-button type="danger" :disabled="!selectedAlbums.length" @click="batchDeleteAlbums">删除选中 ({{ selectedAlbums.length }})</el-button>
          <el-button class="sec-btn" @click="exitSelect">取消多选</el-button>
        </template>
        <el-button v-else-if="userStore.isLoggedIn" class="sec-btn" @click="enterSelect">多选</el-button>
      </div>
    </div>

    <el-tabs v-model="tab" class="music-tabs">
      <el-tab-pane label="全部曲目" name="all">
        <div v-loading="loading">
          <div v-if="tracks.length" class="music-grid">
            <div v-for="t in tracks" :key="t.id" class="music-card card" :class="{ selected: selectedIds.includes(t.id) }">
              <div v-if="selectMode" class="card-check" @click.stop="toggleTrack(t.id)">
                <el-checkbox :model-value="selectedIds.includes(t.id)" />
              </div>
              <div class="music-cover" @click="selectMode ? toggleTrack(t.id) : play(t)">
                <img v-if="t.coverUrl" :src="t.coverUrl" class="cover-img" />
                <div v-else class="cover-placeholder"><span>🎵</span></div>
                <div class="play-btn-overlay"><div class="play-btn-circle">▶</div></div>
              </div>
              <div class="music-info">
                <div class="music-title" :title="t.title">{{ t.title || '未知曲目' }}</div>
                <div class="music-sub">
                  <span v-if="t.artist" class="music-artist">{{ t.artist }}</span>
                  <span v-if="t.album" class="music-album">{{ t.album }}</span>
                </div>
                <div class="music-meta">
                  <span v-if="t.duration">{{ formatDuration(t.duration) }}</span>
                  <span v-if="t.bitrate">· {{ t.bitrate }}kbps</span>
                </div>
              </div>
              <el-dropdown v-if="userStore.isLoggedIn && !selectMode" trigger="click" placement="bottom-end" @command="(cmd) => onTrackAction(cmd, t)">
                <div class="card-more-btn"><el-icon><MoreFilled /></el-icon></div>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="addPlaylist" :disabled="!playlists.length">加入歌单</el-dropdown-item>
                    <el-dropdown-item command="delete" divided class="dropdown-danger">删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
          <el-empty v-else description="曲库为空,上传第一首吧">
            <el-upload :show-file-list="false" :http-request="uploadSingle" accept="audio/*">
              <el-button type="primary">上传音乐</el-button>
            </el-upload>
          </el-empty>
        </div>
      </el-tab-pane>

      <el-tab-pane label="按专辑" name="album">
        <div v-loading="loading">
          <div v-if="albums.length" class="album-grid">
            <div v-for="al in albums" :key="al.album" class="album-card card" :class="{ selected: selectedAlbums.includes(al.album) }">
              <div v-if="selectMode" class="card-check" @click.stop="toggleAlbum(al.album)">
                <el-checkbox :model-value="selectedAlbums.includes(al.album)" />
              </div>
              <div class="album-cover" @click="selectMode ? toggleAlbum(al.album) : playAlbum(al)">
                <img v-if="al.coverUrl" :src="al.coverUrl" class="cover-img" />
                <div v-else class="cover-placeholder"><span>💿</span></div>
                <div class="play-btn-overlay"><div class="play-btn-circle">▶</div></div>
              </div>
              <div class="album-info">
                <div class="album-title">{{ al.album }}</div>
                <div class="album-count">{{ al.count }} 首</div>
                <div class="album-tracks">
                  <div v-for="(t, i) in al.tracks" :key="t.id || i" class="album-track-item">
                    <span class="track-idx">{{ i + 1 }}</span>
                    <span class="track-name" @click="play(t)">{{ t.title || '未知曲目' }}</span>
                    <span v-if="t.duration" class="track-dur">{{ formatDuration(t.duration) }}</span>
                  </div>
                </div>
                <div class="album-footer">
                  <el-dropdown v-if="userStore.isLoggedIn && playlists.length && !selectMode" trigger="click" @command="(pid) => addAlbumToPlaylist(pid, al)">
                    <el-button size="small">整张加入歌单</el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item v-for="p in playlists" :key="p.id" :command="p.id">{{ p.name }}</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无专辑" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="歌单" name="playlist">
        <div v-loading="playlistLoading">
          <div v-if="playlists.length" class="playlist-grid">
            <div v-for="p in playlists" :key="p.id" class="pl-card card">
              <div class="pl-cover" @click="viewPlaylist(p)">
                <img v-if="p.coverUrl" :src="p.coverUrl" class="cover-img" />
                <div v-else class="cover-placeholder pl-cover-placeholder">
                  <svg class="pl-cover-icon" viewBox="0 0 48 48" fill="none">
                    <rect x="8" y="12" width="32" height="24" rx="3" fill="#c9b8a0" opacity="0.5"/>
                    <circle cx="16" cy="20" r="2.5" fill="#b88c6e" opacity="0.6"/>
                    <path d="M22 18h12M22 24h10M22 30h8" stroke="#b88c6e" stroke-width="1.5" stroke-linecap="round" opacity="0.4"/>
                  </svg>
                </div>
                <div v-if="p.isBackground" class="bg-tag">背景音乐</div>
              </div>
              <div class="pl-info">
                <div class="pl-name">{{ p.name }}</div>
                <div class="pl-count">{{ p.trackCount || 0 }} 首</div>
                <div class="pl-footer">
                  <div class="pl-actions">
                    <el-button size="small" @click="viewPlaylist(p)">查看</el-button>
                    <el-button v-if="userStore.isLoggedIn && !p.isBackground" size="small" type="primary" @click="setBackground(p)">设为背景</el-button>
                    <el-button v-if="userStore.isLoggedIn && p.isBackground" size="small" @click="unsetBackground">取消背景</el-button>
                  </div>
                  <el-button v-if="userStore.isLoggedIn" size="small" text class="pl-delete-btn" @click="delPlaylist(p)">删除</el-button>
                </div>
              </div>
            </div>
          </div>
          <div v-else class="pl-empty">
            <svg class="pl-empty-icon" viewBox="0 0 64 64" fill="none">
              <rect x="10" y="14" width="44" height="36" rx="4" fill="#e9e2d7"/>
              <rect x="10" y="14" width="44" height="36" rx="4" stroke="#d4c5b0" stroke-width="1.5"/>
              <circle cx="20" cy="24" r="3" fill="#c9b8a0"/>
              <path d="M28 22h18M28 30h16M28 38h12" stroke="#c9b8a0" stroke-width="2" stroke-linecap="round"/>
            </svg>
            <div class="pl-empty-text">暂无歌单</div>
            <el-button class="sec-btn" @click="openPlaylistDialog">新建歌单</el-button>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 添加外链弹窗 -->
    <el-dialog v-model="linkDialog.visible" title="添加外链" class="dialog-sm" append-to-body>
      <el-form label-position="top">
        <el-form-item label="音频链接">
          <el-input v-model="linkDialog.url" placeholder="https://..." />
        </el-form-item>
        <el-form-item label="歌曲名">
          <el-input v-model="linkDialog.title" placeholder="歌曲名" />
        </el-form-item>
        <el-form-item label="艺术家">
          <el-input v-model="linkDialog.artist" placeholder="艺术家" />
        </el-form-item>
        <el-form-item label="专辑">
          <el-input v-model="linkDialog.album" placeholder="专辑名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="linkDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="addExternal">添加</el-button>
      </template>
    </el-dialog>

    <!-- 专辑上传弹窗 -->
    <el-dialog v-model="albumDialog.visible" title="上传专辑" class="dialog-sm" append-to-body>
      <div class="share-tip">已选择 {{ albumDialog.files.length }} 个音乐文件,请输入专辑名称:</div>
      <el-input v-model="albumDialog.name" placeholder="专辑名称" @keyup.enter="confirmUploadAlbum" />
      <template #footer>
        <el-button @click="albumDialog.visible = false">取消</el-button>
        <el-button type="primary" :disabled="!albumDialog.name.trim() || albumDialog.uploading" :loading="albumDialog.uploading" @click="confirmUploadAlbum">上传</el-button>
      </template>
    </el-dialog>

    <!-- 新建歌单弹窗 -->
    <el-dialog v-model="plDialog.visible" title="新建歌单" class="dialog-sm" append-to-body>
      <el-input v-model="plDialog.name" placeholder="歌单名称" @keyup.enter="createPlaylist" />
      <template #footer>
        <el-button @click="plDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="createPlaylist">创建</el-button>
      </template>
    </el-dialog>

    <!-- 歌单详情弹窗 -->
    <el-dialog v-model="plDetail.visible" :title="plDetail.playlist?.name || '歌单详情'" class="dialog-md" append-to-body>
      <div v-loading="plDetail.loading">
        <div v-if="plDetail.tracks.length" class="pl-detail-list">
          <div v-for="(t, i) in plDetail.tracks" :key="t.id || i" class="pl-detail-item">
            <img v-if="t.coverUrl" :src="t.coverUrl" class="pl-detail-cover" />
            <div v-else class="pl-detail-cover placeholder">🎵</div>
            <div class="pl-detail-info">
              <div class="pl-detail-title">{{ t.title || '未知曲目' }}</div>
              <div v-if="t.artist" class="pl-detail-artist">{{ t.artist }}</div>
            </div>
            <span v-if="t.duration" class="pl-detail-dur">{{ formatDuration(t.duration) }}</span>
            <el-button v-if="userStore.isLoggedIn" size="small" text type="danger" @click="removeFromPlaylist(t)">移除</el-button>
          </div>
        </div>
        <el-empty v-else description="歌单为空,点击下方添加曲目" :image-size="50" />
      </div>
      <template #footer>
        <el-button v-if="userStore.isLoggedIn" @click="openAddTracksDialog">添加曲目</el-button>
        <el-button @click="plDetail.visible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 添加曲目到歌单弹窗:Tab 切换(按曲目多选 / 按专辑加入) -->
    <el-dialog v-model="addTracksDialog.visible" title="添加曲目到歌单" class="dialog-md" append-to-body :close-on-click-modal="true" :close-on-press-escape="true">
      <el-tabs v-model="addTracksDialog.subTab" class="add-tracks-tabs">
        <el-tab-pane label="按曲目" name="track">
          <div v-loading="addTracksDialog.loading" class="add-tracks-list">
            <el-checkbox-group v-model="addTracksDialog.selected">
              <div v-for="t in addTracksDialog.candidates" :key="t.id" class="add-track-item">
                <el-checkbox :value="t.id">
                  <div class="add-track-row">
                    <img v-if="t.coverUrl" :src="t.coverUrl" class="add-track-cover" />
                    <div v-else class="add-track-cover placeholder">
                      <svg viewBox="0 0 24 24" fill="none" width="20" height="20"><path d="M9 18V5l12-2v13" stroke="#b8a890" stroke-width="1.5" stroke-linecap="round"/><circle cx="6" cy="18" r="3" stroke="#b8a890" stroke-width="1.5"/><circle cx="18" cy="16" r="3" stroke="#b8a890" stroke-width="1.5"/></svg>
                    </div>
                    <div class="add-track-meta">
                      <span class="add-track-title">{{ t.title || '未知曲目' }}</span>
                      <span v-if="t.artist" class="add-track-artist">{{ t.artist }}</span>
                      <span v-if="t.album" class="add-track-album">{{ t.album }}</span>
                    </div>
                  </div>
                </el-checkbox>
              </div>
            </el-checkbox-group>
            <el-empty v-if="!addTracksDialog.loading && !addTracksDialog.candidates.length" description="曲库为空" :image-size="40" />
          </div>
        </el-tab-pane>
        <el-tab-pane label="按专辑" name="album">
          <div class="add-album-list">
            <div v-for="al in addTracksDialog.albumCandidates" :key="al.album" class="add-album-item">
              <div class="add-album-info" @click="toggleAlbumInDialog(al)">
                <el-checkbox :model-value="addTracksDialog.selectedAlbums.includes(al.album)" />
                <img v-if="al.coverUrl" :src="al.coverUrl" class="add-album-cover" />
                <div v-else class="add-album-cover placeholder">
                  <svg viewBox="0 0 24 24" fill="none" width="20" height="20"><circle cx="12" cy="12" r="10" stroke="#b8a890" stroke-width="1.5"/><circle cx="12" cy="12" r="3" stroke="#b8a890" stroke-width="1.5"/></svg>
                </div>
                <div class="add-album-meta">
                  <div class="add-album-name">{{ al.album }}</div>
                  <div class="add-album-count">{{ al.count }} 首</div>
                </div>
              </div>
            </div>
            <el-empty v-if="!addTracksDialog.albumCandidates.length" description="暂无专辑" :image-size="40" />
          </div>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="addTracksDialog.visible = false">取消</el-button>
        <el-button type="primary" :disabled="!addCount" @click="confirmAddTracks">添加 ({{ addCount }})</el-button>
      </template>
    </el-dialog>

    <!-- 播放器弹窗 -->
    <el-dialog v-model="player.visible" :title="player.track?.title || '播放'" class="dialog-md" destroy-on-close>
      <div v-if="player.track" class="player-wrap">
        <img v-if="player.track.coverUrl" :src="player.track.coverUrl" class="player-cover" />
        <div v-if="player.track.artist" class="player-artist">{{ player.track.artist }}</div>
        <audio :src="player.track.url" controls autoplay class="player-audio" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { musicApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MoreFilled } from '@element-plus/icons-vue'
import Breadcrumb from '@/components/Breadcrumb.vue'

const userStore = useUserStore()
const tab = ref('all')
const loading = ref(false)
const playlistLoading = ref(false)
const tracks = ref([])
const playlists = ref([])
const player = reactive({ visible: false, track: null })

// 多选模式
const selectMode = ref(false)
const selectedIds = ref([])
const selectedAlbums = ref([])

const enterSelect = () => { selectMode.value = true; selectedIds.value = []; selectedAlbums.value = [] }
const exitSelect = () => { selectMode.value = false; selectedIds.value = []; selectedAlbums.value = [] }
const toggleTrack = (id) => {
  const i = selectedIds.value.indexOf(id)
  if (i >= 0) selectedIds.value.splice(i, 1)
  else selectedIds.value.push(id)
}
const toggleAlbum = (album) => {
  const i = selectedAlbums.value.indexOf(album)
  if (i >= 0) selectedAlbums.value.splice(i, 1)
  else selectedAlbums.value.push(album)
}

const albums = computed(() => {
  const map = {}
  for (const t of tracks.value) {
    if (!t.album) continue
    if (!map[t.album]) map[t.album] = { album: t.album, count: 0, tracks: [], coverUrl: null }
    map[t.album].count++
    map[t.album].tracks.push(t)
    if (!map[t.album].coverUrl && t.coverUrl) map[t.album].coverUrl = t.coverUrl
  }
  return Object.values(map)
})

const load = async () => {
  loading.value = true
  try {
    tracks.value = await musicApi.list()
  } finally { loading.value = false }
}
const loadPlaylists = async () => {
  playlistLoading.value = true
  try {
    playlists.value = await musicApi.playlistList()
  } finally { playlistLoading.value = false }
}

const formatDuration = (s) => {
  if (!s) return ''
  const m = Math.floor(s / 60)
  const sec = s % 60
  return `${m}:${sec.toString().padStart(2, '0')}`
}

const play = (t) => { player.track = t; player.visible = true }
const playAlbum = (al) => { player.track = al.tracks[0]; player.visible = true }

// 上传单曲
const uploadSingle = async (options) => {
  try {
    await musicApi.upload(options.file)
    ElMessage.success('已上传并添加到曲库')
    await load()
  } catch { ElMessage.error('上传失败') }
}

// 上传专辑文件夹
const albumDialog = reactive({ visible: false, name: '', files: [], uploading: false })
const folderInputRef = ref(null)
const triggerFolderInput = () => folderInputRef.value?.click()
const onFolderChange = (e) => {
  const files = Array.from(e.target.files || []).filter(f => f.type.startsWith('audio/'))
  if (!files.length) { ElMessage.warning('文件夹中没有音频文件'); e.target.value = ''; return }
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
    ElMessage.success(`已上传专辑《${albumName}》共 ${albumDialog.files.length} 首`)
    albumDialog.visible = false
    await load()
  } catch { ElMessage.error('上传失败') }
  finally { albumDialog.uploading = false }
}

// 外链
const linkDialog = reactive({ visible: false, url: '', title: '', artist: '', album: '' })
const addExternal = async () => {
  if (!linkDialog.url) return ElMessage.warning('请填写音频链接')
  try {
    await musicApi.add({ url: linkDialog.url, title: linkDialog.title || null, artist: linkDialog.artist || null, album: linkDialog.album || null })
    ElMessage.success('已添加')
    linkDialog.visible = false
    linkDialog.url = ''; linkDialog.title = ''; linkDialog.artist = ''; linkDialog.album = ''
    await load()
  } catch {}
}

// 曲目操作
const onTrackAction = async (cmd, t) => {
  if (cmd === 'delete') {
    await ElMessageBox.confirm(`确定删除《${t.title || '未知曲目'}》?`, '提示', { type: 'warning' })
    await musicApi.remove(t.id)
    ElMessage.success('已删除')
    load()
  } else if (cmd === 'addPlaylist') {
    if (!playlists.value.length) return ElMessage.warning('请先创建歌单')
    plDetail.playlist = playlists.value[0]
    await viewPlaylist(playlists.value[0])
    await openAddTracksDialog()
  }
}

// 批量删除曲目
const batchDeleteTracks = async () => {
  if (!selectedIds.value.length) return
  await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 首曲目?`, '批量删除', { type: 'warning' })
  await musicApi.batchRemove(selectedIds.value)
  ElMessage.success(`已删除 ${selectedIds.value.length} 首`)
  exitSelect()
  load()
}

// 批量删除专辑
const batchDeleteAlbums = async () => {
  if (!selectedAlbums.value.length) return
  await ElMessageBox.confirm(`确定删除选中的 ${selectedAlbums.value.length} 个专辑(含所有曲目)?`, '批量删除', { type: 'warning' })
  for (const album of selectedAlbums.value) {
    await musicApi.removeByAlbum(album)
  }
  ElMessage.success(`已删除 ${selectedAlbums.value.length} 个专辑`)
  exitSelect()
  load()
}

// 歌单管理
const plDialog = reactive({ visible: false, name: '' })
const openPlaylistDialog = () => { plDialog.name = ''; plDialog.visible = true }
const createPlaylist = async () => {
  if (!plDialog.name.trim()) return
  await musicApi.createPlaylist(plDialog.name.trim())
  ElMessage.success('歌单已创建')
  plDialog.visible = false
  await loadPlaylists()
}

const plDetail = reactive({ visible: false, playlist: null, tracks: [], loading: false })
const viewPlaylist = async (p) => {
  plDetail.playlist = p
  plDetail.visible = true
  plDetail.loading = true
  plDetail.tracks = []
  try {
    plDetail.tracks = await musicApi.playlistTracks(p.id)
  } finally { plDetail.loading = false }
}
const removeFromPlaylist = async (t) => {
  await musicApi.removeTrack(plDetail.playlist.id, t.id)
  ElMessage.success('已移除')
  await viewPlaylist(plDetail.playlist)
  await loadPlaylists()
}

// 添加曲目到歌单弹窗
const addTracksDialog = reactive({ visible: false, loading: false, subTab: 'track', candidates: [], selected: [], albumCandidates: [], selectedAlbums: [] })
const addCount = computed(() => {
  if (addTracksDialog.subTab === 'track') return addTracksDialog.selected.length
  return addTracksDialog.selectedAlbums.reduce((sum, album) => {
    const al = addTracksDialog.albumCandidates.find(a => a.album === album)
    return sum + (al?.count || 0)
  }, 0)
})
const openAddTracksDialog = async () => {
  addTracksDialog.visible = true
  addTracksDialog.subTab = 'track'
  addTracksDialog.loading = true
  addTracksDialog.selected = []
  addTracksDialog.selectedAlbums = []
  try {
    const allTracks = await musicApi.list()
    const existingIds = new Set(plDetail.tracks.map(t => t.id))
    addTracksDialog.candidates = allTracks.filter(t => !existingIds.has(t.id))
    // build album candidates from non-existing tracks
    const albumMap = {}
    for (const t of allTracks) {
      if (!t.album || existingIds.has(t.id)) continue
      if (!albumMap[t.album]) albumMap[t.album] = { album: t.album, count: 0, coverUrl: null }
      albumMap[t.album].count++
      if (!albumMap[t.album].coverUrl && t.coverUrl) albumMap[t.album].coverUrl = t.coverUrl
    }
    addTracksDialog.albumCandidates = Object.values(albumMap)
  } finally { addTracksDialog.loading = false }
}
const toggleAlbumInDialog = (al) => {
  const i = addTracksDialog.selectedAlbums.indexOf(al.album)
  if (i >= 0) addTracksDialog.selectedAlbums.splice(i, 1)
  else addTracksDialog.selectedAlbums.push(al.album)
}
const confirmAddTracks = async () => {
  const ids = addTracksDialog.subTab === 'track'
    ? addTracksDialog.selected
    : addTracksDialog.candidates.filter(t => addTracksDialog.selectedAlbums.includes(t.album)).map(t => t.id)
  if (!ids.length) return
  await musicApi.addTracks(plDetail.playlist.id, ids)
  ElMessage.success(`已添加 ${ids.length} 首曲目`)
  addTracksDialog.visible = false
  await viewPlaylist(plDetail.playlist)
  await loadPlaylists()
}

const addAlbumToPlaylist = async (pid, al) => {
  const ids = al.tracks.map(t => t.id).filter(Boolean)
  await musicApi.addTracks(pid, ids)
  ElMessage.success(`已将《${al.album}》${ids.length} 首加入歌单`)
  await loadPlaylists()
}

const setBackground = async (p) => {
  await musicApi.setBackground(p.id)
  ElMessage.success(`已将《${p.name}》设为背景音乐`)
  userStore.bumpBgMusic()
  await loadPlaylists()
}
const unsetBackground = async () => {
  await musicApi.unsetBackground()
  ElMessage.success('已取消背景音乐')
  userStore.bumpBgMusic()
  await loadPlaylists()
}

const delPlaylist = async (p) => {
  await ElMessageBox.confirm(`确定删除歌单《${p.name}》?`, '提示', { type: 'warning' })
  await musicApi.deletePlaylist(p.id)
  ElMessage.success('已删除')
  await loadPlaylists()
}

onMounted(() => { load(); loadPlaylists() })
</script>

<style scoped>
/* ========== 顶部按钮组 ========== */
.list-header { display: flex; justify-content: flex-start; align-items: center; margin-bottom: 16px; }
.header-actions { display: flex; gap: 12px; flex-wrap: wrap; }
.sec-btn {
  height: 34px !important;
  border-radius: 12px !important;
  background: #f3eee6 !important;
  color: #5c4c3d !important;
  border: none !important;
  font-size: 13px !important;
  transition: all 0.2s;
}
.sec-btn:hover { background: #e8e0d2 !important; transform: translateY(-1px); }
html.dark .sec-btn { background: rgba(232,220,200,0.1) !important; color: #E8DCC8 !important; }

/* ========== Tab 标签栏 ========== */
.music-tabs :deep(.el-tabs__header) { margin-bottom: 20px; }
.music-tabs :deep(.el-tabs__nav-wrap::after) { display: none; }
.music-tabs :deep(.el-tabs__item) {
  font-size: 15px;
  color: #aaa098;
  padding: 0 20px;
  height: 40px;
  line-height: 40px;
  transition: color 0.2s;
}
.music-tabs :deep(.el-tabs__item:hover) { color: #8a7e72; }
.music-tabs :deep(.el-tabs__item.is-active) { color: #5c4c3d; font-weight: 500; }
.music-tabs :deep(.el-tabs__active-bar) {
  background: #c4a884;
  height: 2px;
  border-radius: 1px;
  opacity: 0.7;
}
html.dark .music-tabs :deep(.el-tabs__item) { color: rgba(232,220,200,0.4); }
html.dark .music-tabs :deep(.el-tabs__item:hover) { color: rgba(232,220,200,0.6); }
html.dark .music-tabs :deep(.el-tabs__item.is-active) { color: #E8DCC8; }

/* ========== 卡片网格通用 ========== */
.music-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 24px; }
.album-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 24px; }

/* ========== 封面占位(浅米暖调) ========== */
.cover-placeholder {
  width: 100%; height: 100%;
  display: flex; align-items: center; justify-content: center;
  background: #e9e2d7;
}
.cover-placeholder span { font-size: 42px; color: #b8a890; opacity: 0.5; }
html.dark .cover-placeholder { background: rgba(232,220,200,0.06); }
html.dark .cover-placeholder span { color: rgba(232,220,200,0.2); }
.cover-img { width: 100%; height: 100%; object-fit: cover; }

/* ========== hover 播放按钮 ========== */
.play-btn-overlay {
  position: absolute; inset: 0;
  display: flex; align-items: center; justify-content: center;
  background: rgba(0,0,0,0.25);
  opacity: 0;
  transition: opacity 0.2s;
}
.music-cover:hover .play-btn-overlay,
.album-cover:hover .play-btn-overlay { opacity: 1; }
.play-btn-circle {
  width: 44px; height: 44px;
  border-radius: 50%;
  background: rgba(255,255,255,0.9);
  display: flex; align-items: center; justify-content: center;
  font-size: 16px; color: #b88c6e;
  box-shadow: 0 4px 12px rgba(0,0,0,0.2);
  transform: scale(0.85);
  transition: transform 0.2s;
}
.music-cover:hover .play-btn-circle,
.album-cover:hover .play-btn-circle { transform: scale(1); }

/* ========== 曲目卡片 ========== */
.music-card {
  position: relative;
  overflow: hidden;
  display: flex; flex-direction: column;
  border-radius: 16px !important;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05) !important;
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}
.music-card:hover { transform: translateY(-4px); box-shadow: 0 8px 28px rgba(58,46,34,0.12) !important; }
.music-cover { position: relative; height: 160px; cursor: pointer; overflow: hidden; }
.music-info { padding: 16px 20px 20px; display: flex; flex-direction: column; gap: 6px; flex: 1; }
.music-title {
  font-size: 15px; font-weight: 600; color: var(--color-text);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.music-sub { display: flex; gap: 6px; align-items: baseline; flex-wrap: wrap; }
.music-artist { font-size: 12px; color: var(--color-text-secondary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.music-album { font-size: 11px; color: var(--color-text-secondary); opacity: 0.7; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.music-meta { font-size: 11px; color: var(--color-text-secondary); opacity: 0.6; margin-top: 2px; }

/* ========== 卡片更多按钮(⋮) ========== */
.card-more-btn {
  position: absolute;
  bottom: 10px; right: 10px;
  width: 28px; height: 28px;
  border-radius: 50%;
  background: rgba(255,255,255,0.8);
  backdrop-filter: blur(8px);
  display: flex; align-items: center; justify-content: center;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s, background 0.2s;
  z-index: 5;
}
.music-card:hover .card-more-btn { opacity: 1; }
.card-more-btn:hover { background: rgba(255,255,255,0.95); }
.card-more-btn .el-icon { font-size: 16px; color: #5c4c3d; }
html.dark .card-more-btn { background: rgba(30,42,72,0.8); }
html.dark .card-more-btn .el-icon { color: #E8DCC8; }

/* ========== 曲目卡片 ========== */
.album-card {
  overflow: hidden; display: flex; flex-direction: column;
  border-radius: 16px !important;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05) !important;
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}
.album-card:hover { transform: translateY(-4px); box-shadow: 0 8px 28px rgba(58,46,34,0.12) !important; }
.album-cover { position: relative; height: 140px; cursor: pointer; overflow: hidden; }
.album-info { padding: 16px 20px 20px; display: flex; flex-direction: column; gap: 6px; flex: 1; }
.album-title { font-size: 16px; font-weight: 600; }
.album-count { font-size: 12px; color: var(--color-text-secondary); }
.album-tracks { margin-top: 4px; display: flex; flex-direction: column; gap: 2px; max-height: 120px; overflow-y: auto; }
.album-track-item { display: flex; align-items: center; gap: 8px; padding: 3px 0; font-size: 13px; }
.track-idx { width: 18px; text-align: center; opacity: 0.4; font-size: 11px; }
.track-name { flex: 1; cursor: pointer; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.track-name:hover { color: var(--color-accent); }
.track-dur { font-size: 11px; color: var(--color-text-secondary); }
.album-footer { margin-top: auto; padding-top: 8px; }

/* ========== 歌单卡片 ========== */
.playlist-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 24px; }
.pl-card {
  overflow: hidden; display: flex; flex-direction: column;
  border-radius: 16px !important;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05) !important;
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}
.pl-card:hover { transform: translateY(-4px); box-shadow: 0 8px 28px rgba(58,46,34,0.12) !important; }
.pl-cover { position: relative; height: 160px; cursor: pointer; overflow: hidden; }
.pl-cover .cover-img { border-radius: 0; }
.pl-cover-placeholder { background: linear-gradient(135deg, #ede5d8 0%, #e2d8c8 100%); }
.pl-cover-icon { width: 48px; height: 48px; }
.bg-tag {
  position: absolute; top: 10px; left: 10px;
  background: rgba(255,255,255,0.82);
  backdrop-filter: blur(8px);
  color: #8a6d4f;
  font-size: 11px; font-weight: 500;
  padding: 3px 10px;
  border-radius: 8px;
  border: 1px solid rgba(184,140,110,0.15);
}
html.dark .bg-tag {
  background: rgba(30,42,72,0.82);
  color: #c9b8a0;
  border-color: rgba(184,140,110,0.2);
}
.pl-info { padding: 20px; display: flex; flex-direction: column; gap: 4px; flex: 1; }
.pl-name { font-size: 16px; font-weight: 600; color: var(--color-text); }
.pl-count { font-size: 12px; color: var(--color-text-secondary); opacity: 0.7; }
.pl-footer {
  margin-top: auto; padding-top: 16px;
  display: flex; align-items: center; justify-content: space-between; gap: 8px;
}
.pl-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.pl-delete-btn {
  color: #b96058 !important;
  font-size: 12px !important;
  padding: 4px 8px !important;
}
.pl-delete-btn:hover { background: rgba(185,96,88,0.08) !important; color: #a04a42 !important; }
html.dark .pl-delete-btn { color: #d9665a !important; }
html.dark .pl-delete-btn:hover { background: rgba(185,96,88,0.12) !important; }

/* ========== 歌单空状态 ========== */
.pl-empty {
  display: flex; flex-direction: column; align-items: center; gap: 12px;
  padding: 60px 20px;
}
.pl-empty-icon { width: 64px; height: 64px; }
.pl-empty-text { font-size: 14px; color: var(--color-text-secondary); opacity: 0.6; }

/* ========== 歌单详情弹窗 ========== */
.pl-detail-list { display: flex; flex-direction: column; gap: 6px; max-height: 400px; overflow-y: auto; }
.pl-detail-item { display: flex; align-items: center; gap: 10px; padding: 8px; border-radius: 8px; }
.pl-detail-item:hover { background: rgba(58,46,34,0.04); }
.pl-detail-cover { width: 40px; height: 40px; border-radius: 6px; object-fit: cover; flex-shrink: 0; }
.pl-detail-cover.placeholder { display: flex; align-items: center; justify-content: center; background: #e9e2d7; font-size: 18px; color: #b8a890; }
.pl-detail-info { flex: 1; min-width: 0; }
.pl-detail-title { font-size: 13px; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.pl-detail-artist { font-size: 11px; color: var(--color-text-secondary); }
.pl-detail-dur { font-size: 11px; color: var(--color-text-secondary); }

/* ========== 添加曲目弹窗 ========== */
.add-tracks-tabs :deep(.el-tabs__header) { margin-bottom: 16px; }
.add-tracks-list {
  max-height: 300px;
  overflow-y: auto;
}
.add-tracks-list::-webkit-scrollbar,
.add-album-list::-webkit-scrollbar { width: 5px; }
.add-tracks-list::-webkit-scrollbar-track,
.add-album-list::-webkit-scrollbar-track { background: transparent; }
.add-tracks-list::-webkit-scrollbar-thumb,
.add-album-list::-webkit-scrollbar-thumb { background: rgba(58,46,34,0.12); border-radius: 3px; }
.add-tracks-list::-webkit-scrollbar-thumb:hover,
.add-album-list::-webkit-scrollbar-thumb:hover { background: rgba(58,46,34,0.2); }
html.dark .add-tracks-list::-webkit-scrollbar-thumb,
html.dark .add-album-list::-webkit-scrollbar-thumb { background: rgba(232,220,200,0.12); }

.add-track-item {
  padding: 12px 0;
  border-bottom: 1px solid rgba(58,46,34,0.05);
}
.add-track-item:last-child { border-bottom: none; }
.add-track-item .el-checkbox { width: 100%; align-items: center; }
.add-track-item .el-checkbox__label { padding-left: 8px; width: 100%; }
.add-track-row { display: flex; align-items: center; gap: 12px; }
.add-track-cover {
  width: 44px; height: 44px;
  border-radius: 8px;
  object-fit: cover;
  flex-shrink: 0;
}
.add-track-cover.placeholder {
  display: flex; align-items: center; justify-content: center;
  background: #ede5d8;
}
html.dark .add-track-cover.placeholder { background: rgba(232,220,200,0.06); }
.add-track-meta { display: flex; flex-direction: column; gap: 2px; min-width: 0; flex: 1; }
.add-track-title { font-size: 13px; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.add-track-artist { font-size: 11px; color: var(--color-text-secondary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.add-track-album { font-size: 11px; color: var(--color-text-secondary); opacity: 0.6; }

/* 专辑子 Tab */
.add-album-list { max-height: 300px; overflow-y: auto; }
.add-album-item { padding: 4px 0; }
.add-album-info { display: flex; align-items: center; gap: 12px; padding: 10px 8px; border-radius: 10px; cursor: pointer; transition: background 0.15s; }
.add-album-info:hover { background: rgba(58,46,34,0.04); }
.add-album-cover {
  width: 44px; height: 44px;
  border-radius: 8px;
  object-fit: cover;
  flex-shrink: 0;
}
.add-album-cover.placeholder {
  display: flex; align-items: center; justify-content: center;
  background: #ede5d8;
}
html.dark .add-album-cover.placeholder { background: rgba(232,220,200,0.06); }
.add-album-meta { flex: 1; min-width: 0; }
.add-album-name { font-size: 13px; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.add-album-count { font-size: 11px; color: var(--color-text-secondary); }

/* ========== 多选模式 ========== */
.music-card.selected, .album-card.selected {
  outline: 2px solid #b88c6e;
  outline-offset: -2px;
}
.card-check {
  position: absolute;
  top: 6px; left: 6px;
  z-index: 6;
  background: rgba(255,255,255,0.85);
  border-radius: 6px;
  padding: 2px;
}
html.dark .card-check { background: rgba(30,42,72,0.85); }

/* ========== 播放器弹窗 ========== */
.share-tip { color: #776e62; font-size: 12px; margin-bottom: 12px; line-height: 1.4; }
.player-wrap { display: flex; flex-direction: column; align-items: center; gap: 12px; }
.player-cover { width: 160px; height: 160px; border-radius: 12px; object-fit: cover; }
.player-artist { font-size: 14px; color: var(--color-text-secondary); }
.player-audio { width: 100%; }

@media (max-width: 768px) {
  .music-grid, .album-grid, .playlist-grid { grid-template-columns: 1fr; }
  .header-actions { flex-wrap: wrap; }
}
</style>
