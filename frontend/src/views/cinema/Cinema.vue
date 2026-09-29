<!-- 放映厅页:视频库(搜索/筛选/上传/内嵌播放/设备映射)与想看列表(提交/标记入库)两个标签页 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('cinema.title') }]" />

    <PageToolbar>
      <!-- 媒体库(家庭媒体服务器):按题材/地区/年代/类型前端过滤,数据量小 -->
      <template v-if="tab === 'media'">
        <div class="tb-left">
          <el-input v-model="mediaKeyword" :placeholder="$t('cinema.searchPlaceholder')" clearable size="small" style="width: 180px">
            <template #prefix>
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/></svg>
            </template>
          </el-input>
          <el-select v-model="mediaTypeFilter" size="small" style="width: 110px" :placeholder="$t('cinema.filterMediaType')">
            <el-option value="" :label="$t('cinema.allTypes')" />
            <el-option value="Movie" :label="$t('cinema.movie')" />
            <el-option value="Series" :label="$t('cinema.series')" />
          </el-select>
          <el-select v-model="mediaGenreFilter" size="small" clearable filterable style="width: 130px" :placeholder="$t('cinema.filterGenre')">
            <el-option v-for="g in mediaGenreOptions" :key="g" :value="g" :label="optLabel(g)" />
          </el-select>
          <el-select v-model="mediaCountryFilter" size="small" clearable filterable style="width: 130px" :placeholder="$t('cinema.filterCountry')">
            <el-option v-for="c in mediaCountryOptions" :key="c" :value="c" :label="optLabel(c)" />
          </el-select>
          <el-select v-model="mediaYearFilter" size="small" clearable style="width: 120px" :placeholder="$t('cinema.filterYear')">
            <el-option v-for="y in mediaYearOptions" :key="y" :value="y" :label="String(y)" />
          </el-select>
        </div>
        <div class="tb-right">
          <el-button v-if="userStore.isLoggedIn && mediaStatus.connected" @click="loadMedia()">{{ $t('cinema.refresh') }}</el-button>
        </div>
      </template>
      <template v-else-if="!selectMode">
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
            <el-option v-for="g in genreOptions" :key="g" :value="g" :label="optLabel(g)" />
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
    </PageToolbar>

    <el-tabs v-model="tab">
      <!-- 媒体库:家庭媒体服务器上的作品(海报墙);引擎未配置/未连接时给对应引导 -->
      <el-tab-pane :label="$t('cinema.mediaLibrary')" name="media">
        <div v-loading="mediaLoading">
          <template v-if="!userStore.isLoggedIn">
            <el-empty :description="$t('cinema.loginRequired')" />
          </template>
          <template v-else-if="!mediaStatus.configured">
            <el-empty :description="userStore.isOwner ? $t('cinema.engineNotConfigured') : $t('cinema.engineWaitingOwner')">
              <el-button v-if="userStore.isOwner" type="primary" @click="goEngineSettings">{{ $t('cinema.gotoEngineSettings') }}</el-button>
            </el-empty>
          </template>
          <template v-else-if="!mediaStatus.connected">
            <el-empty :description="mediaStatus.enabled === false ? $t('cinema.engineDisabled') : $t('cinema.engineOffline')">
              <el-button v-if="userStore.isOwner" @click="goEngineSettings">{{ $t('cinema.gotoEngineSettings') }}</el-button>
            </el-empty>
          </template>
          <template v-else>
            <!-- 继续观看:网页与电视端共用同一份观看进度 -->
            <div v-if="mediaResume.length" class="resume-block">
              <div class="section-label">{{ $t('cinema.continueWatching') }}</div>
              <div class="resume-row">
                <div v-for="r in mediaResume" :key="r.id" class="resume-card" @click="openMediaPlayer(r)">
                  <div class="resume-thumb">
                    <img v-if="r.imageUrl" :src="r.imageUrl" :alt="r.name" loading="lazy" />
                    <div v-else class="poster-placeholder">🎬</div>
                    <span class="resume-progress" :style="{ width: progressOf(r) + '%' }" />
                  </div>
                  <div class="resume-name">{{ r.seriesName || r.name }}</div>
                  <div class="resume-sub">
                    <span v-if="r.seriesName">S{{ String(r.seasonNumber || 1).padStart(2, '0') }}E{{ String(r.episodeNumber || 1).padStart(2, '0') }}</span>
                    <span v-if="progressOf(r)">{{ $t('cinema.resumePercent', { p: Math.round(progressOf(r)) }) }}</span>
                  </div>
                </div>
              </div>
            </div>

            <div v-if="mediaFiltered.length" class="media-grid">
              <div v-for="w in mediaFiltered" :key="w.id" class="media-card" @click="goDetail(w)">
                <div class="media-poster">
                  <img v-if="w.imageUrl" :src="w.imageUrl" :alt="w.name" loading="lazy" />
                  <div v-else class="poster-placeholder">🎬</div>
                  <span class="media-badge" :class="w.type === 'Series' ? 'series' : 'movie'">
                    {{ w.type === 'Series' ? $t('cinema.series') : $t('cinema.movie') }}
                  </span>
                  <span v-if="w.type === 'Series' && w.unplayedCount" class="media-unplayed">{{ w.unplayedCount }}</span>
                  <span v-else-if="w.played" class="media-played">✓</span>
                  <span v-if="progressOf(w)" class="media-resume-bar" :style="{ width: progressOf(w) + '%' }" />
                  <div class="media-hover">
                    <el-button v-if="w.type === 'Movie'" size="small" type="primary" @click.stop="openMediaPlayer(w)">
                      {{ progressOf(w) ? $t('cinema.continueWatching') : $t('cinema.play') }}
                    </el-button>
                    <span v-else class="media-hover-hint">{{ $t('cinema.viewSeries') }}</span>
                  </div>
                </div>
                <div class="media-info">
                  <div class="media-name" :title="w.name">{{ w.name }}</div>
                  <div class="media-meta">
                    <span v-if="w.year">{{ w.year }}</span>
                    <span v-if="w.rating">⭐ {{ w.rating }}</span>
                    <span v-if="w.seasonCount">{{ w.seasonCount }}{{ $t('cinema.seasonSuffix') }}</span>
                  </div>
                  <div v-if="w.genres?.length" class="media-genres">{{ w.genres.slice(0, 3).join(' · ') }}</div>
                </div>
              </div>
            </div>
            <el-empty v-else :description="mediaError ? $t('cinema.engineOffline') : $t('cinema.mediaEmpty')">
              <el-button v-if="mediaError" @click="loadMedia()">{{ $t('cinema.refresh') }}</el-button>
            </el-empty>
          </template>
        </div>
      </el-tab-pane>

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
                    <el-tooltip :content="$t('common.edit')" placement="top" :show-after="300">
                      <el-button size="small" text @click="openEditor(v)"><el-icon><Edit /></el-icon></el-button>
                    </el-tooltip>
                    <el-tooltip :content="$t('common.delete')" placement="top" :show-after="300">
                      <el-button size="small" text type="danger" @click="onDel(v)"><el-icon><Delete /></el-icon></el-button>
                    </el-tooltip>
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
                <el-tooltip v-if="userStore.isLoggedIn" :content="$t('common.delete')" placement="top" :show-after="300">
                  <el-button size="small" text type="danger" @click="onWishDel(w)"><el-icon><Delete /></el-icon></el-button>
                </el-tooltip>
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
            <el-option v-for="g in genresOptions" :key="g" :label="optLabel(g)" :value="g" />
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
              <el-option v-for="g in genresOptions" :key="g" :label="optLabel(g)" :value="g" />
            </el-select>
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item :label="$t('cinema.region')">
            <el-select v-model="editor.form.region" filterable allow-create default-first-option :placeholder="$t('cinema.selectOrInput')" style="width: 100%">
              <el-option v-for="r in regionOptions" :key="r" :label="optLabel(r)" :value="r" />
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
    <MediaPlayer v-model="mediaPlayer.visible" :item="mediaPlayer.item" @played="loadMedia" />
    <SyncDialog v-model="syncVisible" target="video" @synced="load" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch, inject } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { Edit, Delete } from '@element-plus/icons-vue'
import { videoApi, mediaApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { useSyncStore } from '@/stores/sync'
import { ElMessage, ElMessageBox } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import SyncDialog from '@/components/SyncDialog.vue'
import MediaPlayer from '@/components/MediaPlayer.vue'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'

const { t } = useI18n()
const userStore = useUserStore()
const router = useRouter()
const sunLight = inject(SUN_LIGHT_KEY, null)
// 默认落在媒体库页签(家里的片子在媒体服务器上);未登录时退回本地视频库
const tab = ref(userStore.isLoggedIn ? 'media' : 'library')
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

// 题材/地区:值沿用中文(与库中既有数据一致),标签走 i18n —— 英文界面不再显示中文
const GENRE_KEYS = {
  剧情: 'drama', 喜剧: 'comedy', 动作: 'action', 爱情: 'romance', 科幻: 'scifi', 动画: 'animation',
  悬疑: 'mystery', 惊悚: 'thriller', 恐怖: 'horror', 纪录片: 'documentary', 音乐: 'music', 犯罪: 'crime',
  冒险: 'adventure', 奇幻: 'fantasy', 家庭: 'family', 历史: 'history', 战争: 'war', 武侠: 'wuxia',
  灾难: 'disaster', 运动: 'sports', 歌舞: 'musical', 西部: 'western', 儿童: 'kids', 短片: 'short',
  经典: 'classic', 文艺: 'arthouse', 枪战: 'gunfight', 写实: 'realistic', 实验: 'experimental', 戏曲: 'opera',
}

const REGION_KEYS = {
  中国大陆: 'cn', 香港: 'hk', 台湾: 'tw', 美国: 'us', 英国: 'uk', 日本: 'jp', 韩国: 'kr',
  法国: 'fr', 德国: 'de', 意大利: 'it', 西班牙: 'es', 印度: 'in', 泰国: 'th', 俄罗斯: 'ru',
  加拿大: 'ca', 澳大利亚: 'au', 巴西: 'br', 瑞典: 'se', 丹麦: 'dk', 其他: 'other',
}

// 认不出来的值(用户在库里自己输入的题材)原样显示
const optLabel = (v) => {
  if (GENRE_KEYS[v]) return t(`cinema.genreTag.${GENRE_KEYS[v]}`)
  if (REGION_KEYS[v]) return t(`cinema.regionTag.${REGION_KEYS[v]}`)
  return v
}

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
  } catch {
    // 取数失败按空库渲染(筛选/空状态照常),不留未捕获拒绝
    list.value = []
  } finally {
    loading.value = false
  }
}

// 拉取想看列表
const loadWishes = async () => {
  wishLoading.value = true
  try {
    wishes.value = await videoApi.wishList()
  } catch {
    wishes.value = []
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

// ---------- 媒体库(家庭媒体服务器) ----------
const mediaStatus = ref({ configured: false, connected: false })
const mediaWorks = ref([])
const mediaResume = ref([])
const mediaLoading = ref(false)
const mediaError = ref(false)
const mediaLoaded = ref(false)
const mediaPlayer = reactive({ visible: false, item: null })
const mediaKeyword = ref('')
const mediaTypeFilter = ref('')
const mediaGenreFilter = ref('')
const mediaCountryFilter = ref('')
const mediaYearFilter = ref('')

// 筛选与选项均在前端算(家庭库几百条,与相册同款做法)
const mediaFiltered = computed(() => mediaWorks.value.filter((w) => {
  if (mediaKeyword.value) {
    const k = mediaKeyword.value.toLowerCase()
    const hay = `${w.name || ''} ${w.originalTitle || ''}`.toLowerCase()
    if (!hay.includes(k)) return false
  }
  if (mediaTypeFilter.value && w.type !== mediaTypeFilter.value) return false
  if (mediaGenreFilter.value && !(w.genres || []).includes(mediaGenreFilter.value)) return false
  if (mediaCountryFilter.value && !(w.countries || []).includes(mediaCountryFilter.value)) return false
  if (mediaYearFilter.value && w.year !== mediaYearFilter.value) return false
  return true
}))
const mediaGenreOptions = computed(() => {
  const set = new Set()
  mediaWorks.value.forEach((w) => (w.genres || []).forEach((g) => set.add(g)))
  return [...set]
})
const mediaCountryOptions = computed(() => {
  const set = new Set()
  mediaWorks.value.forEach((w) => (w.countries || []).forEach((c) => set.add(c)))
  return [...set]
})
const mediaYearOptions = computed(() => {
  const set = new Set()
  mediaWorks.value.forEach((w) => w.year && set.add(w.year))
  return [...set].sort((a, b) => b - a)
})
// 已看比例:媒体服务器给了百分比就用它,否则按位置/片长估算;看过的片子不再显示进度条
const progressOf = (item) => {
  if (!item || item.played) return 0
  if (item.playedPercentage != null) return Math.min(99, item.playedPercentage)
  if (!item.positionTicks || !item.runtimeMinutes) return 0
  return Math.min(99, (item.positionTicks / 10000000 / 60 / item.runtimeMinutes) * 100)
}

// 拉媒体库:未配置/未连接时只取状态,不打注定失败的内容接口
const loadMedia = async () => {
  if (!userStore.isLoggedIn) return
  mediaLoading.value = true
  mediaError.value = false
  try {
    mediaStatus.value = await mediaApi.status()
    if (mediaStatus.value.connected) {
      const [works, resumeList] = await Promise.all([mediaApi.works(), mediaApi.resume()])
      mediaWorks.value = works
      mediaResume.value = resumeList
    } else {
      mediaWorks.value = []
      mediaResume.value = []
    }
    mediaLoaded.value = true
  } catch {
    mediaError.value = true
    mediaWorks.value = []
    mediaResume.value = []
  } finally {
    mediaLoading.value = false
  }
}

// 卡片点进详情;(剧集要选季度分集,不在卡片上直接播)
const goDetail = (w) => router.push(`/cinema/${w.id}`)
const openMediaPlayer = (item) => {
  mediaPlayer.item = item
  mediaPlayer.visible = true
}
const goEngineSettings = () => router.push('/settings?tab=media')

// 首次切到媒体库页签才取数(避免开着视频库也打一串媒体服务器请求)
watch(tab, (v) => {
  if (v === 'media' && !mediaLoaded.value) loadMedia()
})

onMounted(() => {
  load()
  loadWishes()
  if (tab.value === 'media') loadMedia()
})
</script>

<style scoped>
.video-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
}
.video-card { overflow: hidden; display: flex; flex-direction: column; }
.video-card.selected { outline: 3px solid var(--color-primary, var(--color-brand)); outline-offset: -3px; }
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
.pick-badge.on { background: var(--color-brand); border-color: var(--color-brand); }
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
.video-actions :deep(.el-button) { padding: 5px 6px; }
.video-actions :deep(.el-button + .el-button) { margin-left: 4px; }
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

/* ---------- 媒体库(海报墙) ---------- */
.resume-block { margin-bottom: 18px; }
.resume-row { display: flex; gap: 12px; overflow-x: auto; padding-bottom: 6px; }
.resume-card { flex: 0 0 168px; cursor: pointer; }
.resume-thumb {
  position: relative;
  height: 96px;
  border-radius: 8px;
  overflow: hidden;
  background: #1c2b3a;
}
.resume-thumb img { width: 100%; height: 100%; object-fit: cover; }
.resume-progress { position: absolute; left: 0; bottom: 0; height: 3px; background: var(--color-brand); }
.resume-name { margin-top: 6px; font-size: 13px; color: var(--color-text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.resume-sub { font-size: 12px; color: var(--color-text-secondary); display: flex; gap: 8px; }
.media-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 16px;
}
.media-card { cursor: pointer; }
.media-poster {
  position: relative;
  aspect-ratio: 2 / 3;
  border-radius: 10px;
  overflow: hidden;
  background: #1c2b3a;
}
.media-poster img { width: 100%; height: 100%; object-fit: cover; transition: transform 0.25s; }
.media-card:hover .media-poster img { transform: scale(1.04); }
.media-badge {
  position: absolute;
  top: 8px;
  left: 8px;
  font-size: 11px;
  color: #fff;
  padding: 1px 8px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(4px);
}
.media-unplayed, .media-played {
  position: absolute;
  top: 8px;
  right: 8px;
  min-width: 20px;
  height: 20px;
  padding: 0 5px;
  border-radius: 10px;
  font-size: 12px;
  line-height: 20px;
  text-align: center;
  color: #fff;
  background: var(--color-brand);
}
.media-played { background: #67b26b; }
.media-resume-bar { position: absolute; left: 0; bottom: 0; height: 3px; background: var(--color-brand); }
.media-hover {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.4);
  opacity: 0;
  transition: opacity 0.15s;
}
.media-card:hover .media-hover { opacity: 1; }
.media-hover-hint { color: #fff; font-size: 13px; }
.media-info { padding: 8px 2px 0; }
.media-name { font-size: 14px; font-weight: 600; color: var(--color-text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.media-meta { font-size: 12px; color: var(--color-text-secondary); display: flex; gap: 8px; margin-top: 2px; }
.media-genres { font-size: 12px; color: var(--color-text-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

@media (max-width: 768px) {
  .video-grid { grid-template-columns: 1fr; }
  .form-row { grid-template-columns: 1fr; }
  .media-grid { grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); gap: 12px; }
  .resume-card { flex: 0 0 132px; }
}
</style>
