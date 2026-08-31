<!-- 放映厅页:视频库(搜索/筛选/上传/内嵌播放/设备映射)与想看列表(提交/标记入库)两个标签页 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('cinema.title') }]" />

    <div class="page-toolbar card">
      <template v-if="!selectMode">
        <div class="tb-left">
          <el-input v-model="searchKeyword" :placeholder="$t('cinema.searchPlaceholder')" clearable size="small" style="width: 200px">
            <template #prefix>
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/></svg>
            </template>
          </el-input>
          <el-select v-model="sourceFilter" size="small" style="width: 150px" :placeholder="$t('cinema.filterSource')">
            <el-option value="" :label="$t('cinema.allSources')" />
            <el-option value="LOCAL" :label="$t('cinema.localUpload')" />
            <el-option v-for="s in sourceOptions" :key="s.value" :value="s.value" :label="s.label" />
          </el-select>
          <el-select v-model="typeFilter" size="small" style="width: 120px" :placeholder="$t('cinema.filterMediaType')">
            <el-option value="" :label="$t('cinema.allTypes')" />
            <el-option value="movie" :label="$t('cinema.movie')" />
            <el-option value="series" :label="$t('cinema.series')" />
            <el-option value="other" :label="$t('cinema.other')" />
          </el-select>
          <el-select v-model="genreFilter" size="small" clearable filterable style="width: 140px" :placeholder="$t('cinema.filterGenre')">
            <el-option v-for="g in genreOptions" :key="g" :value="g" :label="g" />
          </el-select>
        </div>
        <div class="tb-right">
          <el-button v-if="userStore.isLoggedIn && filteredList.length" @click="toggleSelect">{{ $t('cinema.select') }}</el-button>
          <el-button v-if="userStore.isLoggedIn" @click="openWishDialog">{{ $t('cinema.wish') }}</el-button>
          <el-button v-if="userStore.isOwner && hasMapped" :loading="refreshing" @click="onRefreshMap">{{ $t('cinema.refreshMap') }}</el-button>
          <el-button v-if="userStore.isOwner" @click="syncVisible = true">{{ $t('cinema.syncFromDevice') }}</el-button>
          <el-button v-if="userStore.isLoggedIn" type="primary" @click="openEditor()">{{ $t('cinema.upload') }}</el-button>
        </div>
      </template>
      <div v-else class="tb-right">
        <span class="select-count">{{ $t('cinema.selectedVideos', { n: selectedIds.length }) }}</span>
        <el-button @click="toggleSelect">{{ $t('cinema.cancelSelect') }}</el-button>
        <el-button type="danger" :loading="batchDeleting" :disabled="!selectedIds.length" @click="onBatchDelete">{{ $t('cinema.deleteSelected') }}</el-button>
      </div>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane :label="$t('cinema.library')" name="library">
        <div v-loading="loading">
          <div v-if="filteredList.length" class="video-grid">
            <div
              v-for="v in filteredList"
              :key="v.id"
              class="video-card card"
              :class="{ selected: selectMode && selectedIds.includes(v.id) }"
            >
              <div class="video-poster" @click="selectMode ? togglePick(v) : play(v)">
                <img v-if="v.poster" :src="v.poster" class="poster-img" :alt="$t('cinema.poster')" />
                <div v-else class="poster-placeholder">🎬</div>
                <div v-if="!selectMode" class="play-overlay">▶ {{ $t('cinema.play') }}</div>
                <span v-if="v.sourceDeviceName && !selectMode" class="video-source">
                  <span class="status-dot" :class="v.syncStatus || 'OFFLINE'"></span>{{ v.sourceDeviceName }}
                </span>
                <span v-if="selectMode" class="pick-badge" :class="{ on: selectedIds.includes(v.id) }">
                  <svg viewBox="0 0 16 16" width="12" height="12"><path d="M3 8.5 L6.5 12 L13 4.5" fill="none" stroke="#fff" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/></svg>
                </span>
              </div>
              <div class="video-info">
                <div class="video-title">{{ v.title }}</div>
                <div v-if="v.originalTitle" class="video-original">{{ v.originalTitle }}</div>
                <div v-if="v.genres" class="video-genres">
                  <span v-for="g in String(v.genres).split(',').filter(Boolean)" :key="g" class="tag">#{{ g }}</span>
                </div>
                <div class="video-meta">
                  <span v-if="v.year">{{ v.year }}</span>
                  <span v-if="v.region">· {{ v.region }}</span>
                  <span v-if="v.rating">· ⭐ {{ v.rating }}</span>
                  <span v-if="v.mediaType === 'series'">· {{ v.episodes || '?' }}{{ $t('cinema.episodeSuffix') }}</span>
                  <span v-else-if="v.duration">· {{ v.duration }}{{ $t('cinema.minutesSuffix') }}</span>
                </div>
                <div v-if="v.director" class="video-credit">{{ $t('cinema.directorMeta') }}{{ v.director }}</div>
                <div v-if="v.actors" class="video-credit">{{ $t('cinema.actorsMeta') }}{{ v.actors }}</div>
                <div class="video-footer">
                  <span class="video-uploader">{{ v.uploaderName }}</span>
                  <span v-if="userStore.isLoggedIn" class="video-actions">
                    <el-button size="small" text @click="openEditor(v)">{{ $t('common.edit') }}</el-button>
                    <el-button size="small" text type="danger" @click="onDel(v)">{{ $t('common.delete') }}</el-button>
                  </span>
                </div>
              </div>
            </div>
          </div>
          <el-empty v-else :description="userStore.isGuest ? $t('cinema.noData') : $t('cinema.emptyHint')" />
        </div>
      </el-tab-pane>

      <el-tab-pane :label="$t('cinema.wishList')" name="wish">
        <div v-loading="wishLoading">
          <div v-if="wishes.length" class="wish-list">
            <div v-for="w in wishes" :key="w.id" class="wish-item card">
              <div class="wish-main">
                <div class="wish-title">
                  {{ w.title }}
                  <el-tag v-if="w.status === 'IMPORTED'" size="small" type="success">{{ $t('cinema.imported') }}</el-tag>
                  <el-tag v-else size="small" type="info">{{ $t('cinema.pendingImport') }}</el-tag>
                </div>
                <div v-if="w.genres" class="wish-genres">
                  <span v-for="g in String(w.genres).split(',').filter(Boolean)" :key="g" class="tag">#{{ g }}</span>
                </div>
                <div v-if="w.reason" class="wish-reason">{{ w.reason }}</div>
                <div class="wish-meta">{{ w.requesterName }} · {{ formatDate(w.createdAt) }}</div>
              </div>
              <div class="wish-actions">
                <el-button v-if="userStore.isLoggedIn && w.status === 'PENDING'" size="small" type="primary" plain @click="onWishDone(w)">{{ $t('cinema.markImported') }}</el-button>
                <el-button v-if="userStore.isLoggedIn" size="small" text type="danger" @click="onWishDel(w)">{{ $t('common.delete') }}</el-button>
              </div>
            </div>
          </div>
          <el-empty v-else :description="userStore.isGuest ? $t('cinema.wishEmpty') : $t('cinema.wishEmptyHint')" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="wishDialog.visible" append-to-body :title="$t('cinema.submitWish')" width="460px">
      <el-form :model="wishDialog.form" label-position="top">
        <el-form-item :label="$t('cinema.name')">
          <el-input v-model="wishDialog.form.title" :placeholder="$t('cinema.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('cinema.genres')">
          <el-select v-model="wishDialog.form.genres" multiple filterable allow-create default-first-option :placeholder="$t('cinema.genrePlaceholder')" style="width: 100%">
            <el-option v-for="g in genresOptions" :key="g" :label="g" :value="g" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('cinema.reason')">
          <el-input v-model="wishDialog.form.reason" type="textarea" :rows="3" :placeholder="$t('cinema.reasonPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="wishDialog.visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="onWishSave">{{ $t('common.submit') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editor.visible" append-to-body :title="editor.form.id ? $t('cinema.editVideo') : $t('cinema.upload')" width="640px" top="5vh">
      <el-form :model="editor.form" label-position="top">
        <div class="form-row">
          <el-form-item :label="$t('cinema.name')">
            <el-input v-model="editor.form.title" :placeholder="$t('cinema.required')" />
          </el-form-item>
          <el-form-item :label="$t('cinema.originalTitle')">
            <el-input v-model="editor.form.originalTitle" :placeholder="$t('cinema.originalTitlePlaceholder')" />
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item :label="$t('cinema.mediaType')">
            <el-select v-model="editor.form.mediaType" style="width: 100%">
              <el-option :label="$t('cinema.movie')" value="movie" />
              <el-option :label="$t('cinema.series')" value="series" />
              <el-option :label="$t('cinema.other')" value="other" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('cinema.genresDouban')">
            <el-select v-model="editor.form.genres" multiple filterable allow-create default-first-option :placeholder="$t('cinema.selectOrInput')" style="width: 100%">
              <el-option v-for="g in genresOptions" :key="g" :label="g" :value="g" />
            </el-select>
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item :label="$t('cinema.region')">
            <el-select v-model="editor.form.region" filterable allow-create default-first-option :placeholder="$t('cinema.selectOrInput')" style="width: 100%">
              <el-option v-for="r in regionOptions" :key="r" :label="r" :value="r" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('cinema.year')">
            <el-input-number v-model="editor.form.year" :min="1900" :max="2100" controls-position="right" style="width: 100%" />
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item :label="$t('cinema.language')">
            <el-input v-model="editor.form.language" :placeholder="$t('cinema.languagePlaceholder')" />
          </el-form-item>
          <el-form-item :label="editor.form.mediaType === 'series' ? $t('cinema.episodes') : $t('cinema.duration')">
            <el-input-number v-model="editor.form.duration" :min="0" :max="99999" controls-position="right" style="width: 100%" />
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item :label="$t('cinema.director')">
            <el-input v-model="editor.form.director" />
          </el-form-item>
          <el-form-item :label="$t('cinema.ratingDouban')">
            <el-input-number v-model="editor.form.rating" :min="0" :max="10" :precision="1" :step="0.1" controls-position="right" style="width: 100%" />
          </el-form-item>
        </div>
        <el-form-item :label="$t('cinema.actors')">
          <el-input v-model="editor.form.actors" :placeholder="$t('cinema.actorsPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('cinema.intro')">
          <el-input v-model="editor.form.intro" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item :label="$t('cinema.poster')">
          <el-upload :show-file-list="false" :http-request="uploadPoster" accept="image/*">
            <img v-if="editor.form.poster" :src="editor.form.poster" class="poster-upload-preview" :alt="$t('cinema.poster')" />
            <el-button v-else>{{ $t('cinema.uploadPoster') }}</el-button>
          </el-upload>
        </el-form-item>
        <el-form-item v-if="editor.form.sourceDeviceId" :label="$t('cinema.videoFile')">
          <div class="video-uploaded">{{ $t('cinema.mappedFileHint') }}</div>
        </el-form-item>
        <el-form-item v-else :label="$t('cinema.videoFile')">
          <el-upload :show-file-list="false" :http-request="uploadVideo" accept="video/*">
            <el-button type="primary" plain>{{ editor.form.videoUrl ? $t('cinema.reupload') : $t('cinema.uploadVideoFile') }}</el-button>
          </el-upload>
          <div v-if="editor.form.videoUrl" class="video-uploaded">{{ $t('cinema.uploaded') }}{{ editor.form.videoUrl }}</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editor.visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="onSave">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="player.visible" append-to-body :title="player.video?.title" width="800px" top="5vh" destroy-on-close>
      <video v-if="player.video?.videoUrl" :src="player.video.videoUrl" controls autoplay class="player-video" />
    </el-dialog>
    <SyncDialog v-model="syncVisible" target="video" @synced="load" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch, inject } from 'vue'
import { useI18n } from 'vue-i18n'
import { videoApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { useSyncStore } from '@/stores/sync'
import { ElMessage, ElMessageBox } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb.vue'
import SyncDialog from '@/components/SyncDialog.vue'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'

const { t } = useI18n()
const userStore = useUserStore()
const sunLight = inject(SUN_LIGHT_KEY, null)
const tab = ref('library')
const syncVisible = ref(false)
const list = ref([])
const wishes = ref([])
const loading = ref(false)
const wishLoading = ref(false)

const genresOptions = [
  '剧情', '喜剧', '动作', '爱情', '科幻', '动画', '悬疑', '惊悚', '恐怖', '纪录片',
  '音乐', '犯罪', '冒险', '奇幻', '家庭', '历史', '战争', '武侠', '灾难', '运动',
  '歌舞', '西部', '儿童', '短片', '经典', '文艺', '枪战', '写实', '实验', '戏曲',
]

const regionOptions = [
  '中国大陆', '香港', '台湾', '美国', '英国', '日本', '韩国', '法国', '德国', '意大利',
  '西班牙', '印度', '泰国', '俄罗斯', '加拿大', '澳大利亚', '巴西', '瑞典', '丹麦', '其他',
]

// ---------- 筛选(相册式前端过滤,家庭数据量小) ----------
const searchKeyword = ref('')
const sourceFilter = ref('')
const typeFilter = ref('')
const genreFilter = ref('')
const filteredList = computed(() => list.value.filter((v) => {
  if (searchKeyword.value) {
    const k = searchKeyword.value.toLowerCase()
    const hay = `${v.title || ''} ${v.originalTitle || ''}`.toLowerCase()
    if (!hay.includes(k)) return false
  }
  if (sourceFilter.value === 'LOCAL' && v.sourceDeviceId) return false
  if (sourceFilter.value && sourceFilter.value !== 'LOCAL' && String(v.sourceDeviceId) !== sourceFilter.value) return false
  if (typeFilter.value && v.mediaType !== typeFilter.value) return false
  if (genreFilter.value && !String(v.genres || '').split(',').map((s) => s.trim()).filter(Boolean).includes(genreFilter.value)) return false
  return true
}))
// 来源筛选选项:列表数据中出现的映射设备(去重)
const sourceOptions = computed(() => {
  const map = new Map()
  for (const v of list.value) {
    if (v.sourceDeviceId && v.sourceDeviceName) map.set(String(v.sourceDeviceId), v.sourceDeviceName)
  }
  return [...map.entries()].map(([value, label]) => ({ value, label }))
})
// 题材筛选选项:默认题材 + 库中已有的题材(去重)
const genreOptions = computed(() => {
  const set = new Set(genresOptions)
  for (const v of list.value) {
    for (const g of String(v.genres || '').split(',').map((s) => s.trim()).filter(Boolean)) set.add(g)
  }
  return [...set]
})
const hasMapped = computed(() => list.value.some((v) => v.sourceDeviceId))

// 新影片/编辑共用的空表单(剧集数保存时由 duration 字段转换而来,表单内不单独维护)
const emptyForm = () => ({
  id: null, title: '', originalTitle: '', mediaType: 'movie', genres: [],
  region: '', year: null, language: '', duration: null,
  director: '', actors: '', rating: null, intro: '', poster: '', videoUrl: '',
  sourceDeviceId: null,
})

const editor = reactive({ visible: false, form: emptyForm() })
const wishDialog = reactive({ visible: false, form: { title: '', genres: [], reason: '' } })
const player = reactive({ visible: false, video: null })
watch(() => player.visible, (v) => { v ? sunLight?.suspendEffects() : sunLight?.restoreEffects() })

const formatDate = (d) => (d ? new Date(d).toLocaleDateString('zh-CN') : '')

// 拉取视频库(筛选在前端做,全量拉取)
const load = async () => {
  loading.value = true
  try {
    list.value = await videoApi.list({})
  } finally {
    loading.value = false
  }
}

// 拉取想看列表
const loadWishes = async () => {
  wishLoading.value = true
  try {
    wishes.value = await videoApi.wishList()
  } finally {
    wishLoading.value = false
  }
}

// 点击海报弹出播放器:播放地址现取(storage:// 签名 URL 10 分钟过期,列表里那份可能已失效)
const play = async (v) => {
  try {
    const { url } = await videoApi.playUrl(v.id)
    player.video = { ...v, videoUrl: url }
    player.visible = true
  } catch {
    ElMessage.error(t('cinema.playFailed'))
  }
}

// 打开编辑框:编辑时把 genres 字符串拆回数组,新增用空表单
const openEditor = (v) => {
  if (v) {
    editor.form = {
      ...v,
      genres: v.genres ? String(v.genres).split(',').filter(Boolean) : [],
    }
  } else {
    editor.form = emptyForm()
  }
  editor.visible = true
}

const uploadPoster = async ({ file }) => {
  const r = await videoApi.upload(file)
  editor.form.poster = r.url
  ElMessage.success(t('cinema.posterUploaded'))
}

const uploadVideo = async ({ file }) => {
  const r = await videoApi.upload(file)
  editor.form.videoUrl = r.url
  ElMessage.success(t('cinema.videoUploaded'))
}

// 保存影片:剧集把"片长"字段存到 episodes,电影反之;空值转 null 落库
const onSave = async () => {
  if (!editor.form.title) return ElMessage.warning(t('cinema.titleRequired'))
  if (!editor.form.videoUrl && !editor.form.sourceDeviceId) return ElMessage.warning(t('cinema.videoRequired'))
  const data = {
    title: editor.form.title,
    originalTitle: editor.form.originalTitle || null,
    mediaType: editor.form.mediaType,
    genres: editor.form.genres.length ? editor.form.genres.join(',') : null,
    region: editor.form.region || null,
    year: editor.form.year || null,
    language: editor.form.language || null,
    duration: editor.form.mediaType === 'series' ? null : editor.form.duration,
    episodes: editor.form.mediaType === 'series' ? editor.form.duration : null,
    director: editor.form.director || null,
    actors: editor.form.actors || null,
    rating: editor.form.rating || null,
    intro: editor.form.intro || null,
    poster: editor.form.poster || null,
    videoUrl: editor.form.sourceDeviceId ? null : editor.form.videoUrl,
  }
  if (editor.form.id) await videoApi.update(editor.form.id, data)
  else await videoApi.create(data)
  ElMessage.success(t('common.success'))
  editor.visible = false
  load()
}

const onDel = async (v) => {
  await ElMessageBox.confirm(t('cinema.deleteConfirm', { title: v.title }), t('common.tip'), { type: 'warning', closeOnClickModal: true })
  await videoApi.remove(v.id)
  ElMessage.success(t('common.deleted'))
  load()
}

// ---------- 多选批量删除 ----------
const selectMode = ref(false)
const selectedIds = ref([])
const batchDeleting = ref(false)
const toggleSelect = () => {
  selectMode.value = !selectMode.value
  selectedIds.value = []
}
const togglePick = (v) => {
  const i = selectedIds.value.indexOf(v.id)
  if (i >= 0) selectedIds.value.splice(i, 1)
  else selectedIds.value.push(v.id)
}
// 批量删除:混有设备映射视频时提示只删记录不动设备文件
const onBatchDelete = async () => {
  const targets = filteredList.value.filter((v) => selectedIds.value.includes(v.id))
  const msg = targets.some((v) => v.sourceDeviceId)
    ? t('cinema.batchMixedConfirm', { n: selectedIds.value.length })
    : t('cinema.batchDeleteConfirm', { n: selectedIds.value.length })
  await ElMessageBox.confirm(msg, t('common.tip'), { type: 'warning', closeOnClickModal: true })
  batchDeleting.value = true
  try {
    for (const id of [...selectedIds.value]) {
      await videoApi.remove(id)
    }
    ElMessage.success(t('common.deleted'))
    selectedIds.value = []
    selectMode.value = false
    load()
  } finally {
    batchDeleting.value = false
  }
}

// ---------- 刷新设备映射 ----------
const refreshing = ref(false)
const onRefreshMap = async () => {
  refreshing.value = true
  try {
    await videoApi.refreshMap()
    ElMessage.success(t('cinema.refreshStarted'))
  } finally {
    refreshing.value = false
  }
}

// ---------- 后台同步完成时自动刷新列表 ----------
const syncStore = useSyncStore()
watch(syncStore.doneCount, () => load())

const openWishDialog = () => {
  wishDialog.form = { title: '', genres: [], reason: '' }
  wishDialog.visible = true
}

// 提交想看:题材数组转逗号分隔字符串
const onWishSave = async () => {
  if (!wishDialog.form.title) return ElMessage.warning(t('cinema.wishTitleRequired'))
  await videoApi.addWish({
    title: wishDialog.form.title,
    genres: wishDialog.form.genres.length ? wishDialog.form.genres.join(',') : null,
    reason: wishDialog.form.reason || null,
  })
  ElMessage.success(t('cinema.wishSubmitted'))
  wishDialog.visible = false
  loadWishes()
}

const onWishDone = async (w) => {
  await videoApi.wishDone(w.id)
  ElMessage.success(t('cinema.markedImported'))
  loadWishes()
}

const onWishDel = async (w) => {
  await ElMessageBox.confirm(t('cinema.wishDeleteConfirm', { title: w.title }), t('common.tip'), { type: 'warning', closeOnClickModal: true })
  await videoApi.wishRemove(w.id)
  ElMessage.success(t('common.deleted'))
  loadWishes()
}

onMounted(() => {
  load()
  loadWishes()
})
</script>

<style scoped>
.video-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
}
.video-card { overflow: hidden; display: flex; flex-direction: column; }
.video-card.selected { outline: 3px solid var(--color-primary, #b88c6e); outline-offset: -3px; }
.video-poster {
  position: relative;
  height: 170px;
  background: #1c2b3a;
  cursor: pointer;
  overflow: hidden;
}
.poster-img { width: 100%; height: 100%; object-fit: cover; }
.poster-placeholder {
  width: 100%; height: 100%;
  display: flex; align-items: center; justify-content: center;
  font-size: 48px;
  color: rgba(255, 255, 255, 0.25);
}
.play-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  color: #fff;
  background: rgba(0, 0, 0, 0.35);
  opacity: 0;
  transition: opacity 0.15s;
}
.video-poster:hover .play-overlay { opacity: 1; }
.video-source {
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
.select-count { font-size: 13px; color: var(--color-text-secondary); margin-right: 8px; }
.video-info { padding: 14px 16px 12px; display: flex; flex-direction: column; gap: 6px; flex: 1; }
.video-title { font-size: 16px; font-weight: 600; color: var(--color-text); }
.video-original { font-size: 12px; color: var(--color-text-secondary); }
.video-genres { display: flex; gap: 6px; flex-wrap: wrap; }
.video-genres .tag { background: rgba(46, 116, 181, 0.08); color: var(--color-accent); padding: 1px 8px; border-radius: 10px; font-size: 12px; }
.video-meta { font-size: 12px; color: var(--color-text-secondary); }
.video-credit { font-size: 12px; color: var(--color-text-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.video-footer {
  margin-top: auto;
  padding-top: 8px;
  border-top: 1px solid rgba(31, 58, 95, 0.06);
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.video-uploader { font-size: 12px; color: var(--color-text-secondary); }
.video-actions { display: flex; }
.wish-list { display: flex; flex-direction: column; gap: 12px; max-width: 720px; }
.wish-item { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 16px 20px; }
.wish-title { font-size: 15px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.wish-genres { display: flex; gap: 6px; margin-top: 6px; flex-wrap: wrap; }
.wish-genres .tag { background: rgba(46, 116, 181, 0.08); color: var(--color-accent); padding: 1px 8px; border-radius: 10px; font-size: 12px; }
.wish-reason { font-size: 13px; color: var(--color-text); margin-top: 6px; }
.wish-meta { font-size: 12px; color: var(--color-text-secondary); margin-top: 6px; }
.wish-actions { display: flex; flex-direction: column; gap: 6px; flex-shrink: 0; }
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.poster-upload-preview { width: 120px; height: 170px; object-fit: cover; border-radius: 8px; display: block; }
.video-uploaded { font-size: 12px; color: var(--color-text-secondary); margin-top: 6px; word-break: break-all; }
.player-video { width: 100%; max-height: 70vh; background: #000; border-radius: 8px; }

@media (max-width: 768px) {
  .video-grid { grid-template-columns: 1fr; }
  .form-row { grid-template-columns: 1fr; }
}
</style>
