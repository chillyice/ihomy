<!--
  放映厅作品详情(媒体引擎):电影显示元数据 + 播放;剧集另带分季分集与逐集观看状态。
  从「继续观看」进来时带 ?ep=分集id,自动定位到该集所在季度。
-->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('cinema.title'), to: '/cinema' }, { label: detail?.name || '...' }]" />

    <div v-loading="loading" class="detail">
      <el-empty v-if="!loading && !detail" :description="$t('cinema.notFound')" />

      <template v-else-if="detail">
        <div class="detail-head card">
          <div class="poster-box">
            <img v-if="detail.imageUrl" :src="detail.imageUrl" class="poster" :alt="detail.name" />
            <div v-else class="poster poster-placeholder">🎬</div>
          </div>
          <div class="meta">
            <div class="title-row">
              <h2 class="title">{{ detail.name }}</h2>
              <span v-if="detail.year" class="year">{{ detail.year }}</span>
              <el-tag v-if="detail.played" size="small" type="success">{{ $t('cinema.watched') }}</el-tag>
              <el-tag v-else-if="detail.type === 'Series' && detail.unplayedCount" size="small" type="warning">
                {{ $t('cinema.unplayedEpisodes', { n: detail.unplayedCount }) }}
              </el-tag>
            </div>
            <div v-if="detail.originalTitle" class="original">{{ detail.originalTitle }}</div>
            <div class="facts">
              <span v-if="detail.rating">⭐ {{ detail.rating }}</span>
              <span v-if="detail.officialRating">{{ detail.officialRating }}</span>
              <span v-if="detail.premiereDate">{{ detail.premiereDate }}</span>
              <span v-if="detail.runtimeMinutes">{{ detail.runtimeMinutes }}{{ $t('cinema.minutesSuffix') }}</span>
              <span v-if="detail.type === 'Series' && detail.seasonCount">
                {{ detail.seasonCount }}{{ $t('cinema.seasonSuffix') }}
              </span>
            </div>
            <div v-if="detail.genres?.length" class="tags">
              <span v-for="g in detail.genres" :key="g" class="tag">#{{ g }}</span>
            </div>
            <div v-if="detail.countries?.length || detail.studios?.length" class="credits">
              <div v-if="detail.countries?.length">{{ $t('cinema.regionMeta') }}{{ detail.countries.join(' / ') }}</div>
              <div v-if="detail.studios?.length">{{ $t('cinema.studioMeta') }}{{ detail.studios.join(' / ') }}</div>
            </div>
            <div v-if="detail.directors?.length" class="credits">{{ $t('cinema.directorMeta') }}{{ detail.directors.join(' / ') }}</div>
            <div v-if="detail.actors?.length" class="credits">
              {{ $t('cinema.actorsMeta') }}
              {{ detail.actors.map((a) => (a.role ? `${a.name}(${a.role})` : a.name)).join(' / ') }}
            </div>
            <p v-if="detail.overview" class="overview">{{ detail.overview }}</p>
            <div class="actions">
              <el-button type="primary" @click="openPlayer(primaryTarget())">
                <el-icon><VideoPlay /></el-icon>{{ resumeLabel }}
              </el-button>
              <el-button :loading="marking" @click="togglePlayed(primaryTarget(), !detail.played)">
                {{ detail.played ? $t('cinema.markUnwatched') : $t('cinema.markWatched') }}
              </el-button>
            </div>
          </div>
        </div>

        <!-- 剧集:分季分集 -->
        <div v-if="detail.type === 'Series'" class="card episodes-card">
          <div class="eps-head">
            <div class="section-label">{{ $t('cinema.episodeList') }}</div>
            <el-select v-model="season" size="small" style="width: 150px">
              <el-option
                v-for="s in detail.seasons"
                :key="s.id"
                :value="s.seasonNumber"
                :label="s.name + (s.unplayedCount ? ` · ${$t('cinema.unplayedEpisodes', { n: s.unplayedCount })}` : '')"
              />
            </el-select>
          </div>
          <div v-if="currentEpisodes.length" class="ep-list">
            <div v-for="e in currentEpisodes" :key="e.id" class="ep-item" :class="{ played: e.played }">
              <div class="ep-thumb" @click="openPlayer(e)">
                <img v-if="e.imageUrl" :src="e.imageUrl" :alt="e.name" loading="lazy" />
                <div v-else class="ep-thumb-ph">▶</div>
                <span v-if="progressOf(e)" class="ep-progress" :style="{ width: progressOf(e) + '%' }" />
              </div>
              <div class="ep-main">
                <div class="ep-name">
                  <span class="ep-code">E{{ String(e.episodeNumber || 0).padStart(2, '0') }}</span>
                  {{ e.name }}
                  <el-tag v-if="e.played" size="small" type="success">{{ $t('cinema.watched') }}</el-tag>
                </div>
                <div class="ep-meta">
                  <span v-if="e.runtimeMinutes">{{ e.runtimeMinutes }}{{ $t('cinema.minutesSuffix') }}</span>
                  <span v-if="progressOf(e)">{{ $t('cinema.resumePercent', { p: Math.round(progressOf(e)) }) }}</span>
                </div>
                <div v-if="e.overview" class="ep-overview">{{ e.overview }}</div>
              </div>
              <div class="ep-actions">
                <el-button size="small" type="primary" plain @click="openPlayer(e)">{{ $t('cinema.play') }}</el-button>
                <el-button size="small" text @click="togglePlayed(e, !e.played)">
                  {{ e.played ? $t('cinema.markUnwatched') : $t('cinema.markWatched') }}
                </el-button>
              </div>
            </div>
          </div>
          <el-empty v-else :description="$t('cinema.noEpisodes')" />
        </div>
      </template>
    </div>

    <MediaPlayer v-model="player.visible" :item="player.item" @played="reload" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { VideoPlay } from '@element-plus/icons-vue'
import { mediaApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb.vue'
import MediaPlayer from '@/components/MediaPlayer.vue'

const { t } = useI18n()
const route = useRoute()
const userStore = useUserStore()

const detail = ref(null)
const loading = ref(false)
const marking = ref(false)
const season = ref(null)
const player = ref({ visible: false, item: null })

const itemId = computed(() => route.params.itemId)

const currentEpisodes = computed(() => {
  const eps = detail.value?.episodes || []
  if (season.value == null) return eps
  return eps.filter((e) => e.seasonNumber === season.value)
})

const resumeLabel = computed(() => {
  const d = detail.value
  if (!d) return t('cinema.play')
  if (d.type === 'Series') return t('cinema.playSeries')
  return d.positionTicks > 0 ? t('cinema.continueWatching') : t('cinema.play')
})

// 已看比例:看过的分集不再显示进度(媒体服务器可能留着上次的位置)
const progressOf = (item) => {
  if (!item || item.played) return 0
  if (item.playedPercentage != null) return Math.min(99, item.playedPercentage)
  if (!item.positionTicks || !item.runtimeMinutes) return 0
  return Math.min(99, (item.positionTicks / 10000000 / 60 / item.runtimeMinutes) * 100)
}

const load = async () => {
  if (!userStore.isLoggedIn) return
  loading.value = true
  try {
    detail.value = await mediaApi.work(itemId.value)
    // 带 ?ep= 进来(继续观看):定位到该集所在季度
    const ep = String(route.query.ep || '')
    const hit = ep ? (detail.value.episodes || []).find((e) => e.id === ep) : null
    if (hit) season.value = hit.seasonNumber
    else if (season.value == null) season.value = detail.value.seasons?.[0]?.seasonNumber ?? null
  } catch {
    detail.value = null
  } finally {
    loading.value = false
  }
}

const reload = () => load()

// 剧集主按钮:定位到「该看的那一集」= 季度内第一个未看,没有则第一集
const primaryTarget = () => {
  const d = detail.value
  if (!d) return null
  if (d.type !== 'Series') return d
  const eps = d.episodes || []
  const target = eps.find((e) => !e.played) || eps[0]
  return target ? { ...target, seriesName: d.name } : d
}

const openPlayer = (item) => {
  if (!item?.id) return
  player.value.item = item
  player.value.visible = true
}

const togglePlayed = async (item, played) => {
  if (!item?.id) return
  marking.value = true
  try {
    await mediaApi.played(item.id, played)
    ElMessage.success(played ? t('cinema.markedWatched') : t('cinema.markedUnwatched'))
    await load()
  } finally {
    marking.value = false
  }
}

watch(itemId, () => {
  season.value = null
  load()
})

onMounted(load)
</script>

<style scoped>
.detail { display: flex; flex-direction: column; gap: 16px; }
.detail-head { display: flex; gap: 24px; padding: 20px; }
.poster-box { flex: 0 0 220px; }
.poster {
  width: 220px;
  height: 330px;
  object-fit: cover;
  border-radius: 10px;
  background: #1c2b3a;
  display: block;
}
.poster-placeholder { display: flex; align-items: center; justify-content: center; font-size: 54px; color: rgba(255, 255, 255, 0.25); }
.meta { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 8px; }
.title-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.title { font-size: 22px; font-weight: 600; color: var(--color-text); }
.year { font-size: 15px; color: var(--color-text-secondary); }
.original { font-size: 13px; color: var(--color-text-secondary); }
.facts { display: flex; gap: 12px; flex-wrap: wrap; font-size: 13px; color: var(--color-text-secondary); }
.tags { display: flex; gap: 6px; flex-wrap: wrap; }
.tags .tag { background: rgba(46, 116, 181, 0.08); color: var(--color-accent); padding: 1px 8px; border-radius: 10px; font-size: 12px; }
.credits { font-size: 13px; color: var(--color-text-secondary); }
.overview { font-size: 14px; line-height: 1.7; color: var(--color-text); }
.actions { display: flex; gap: 10px; margin-top: auto; padding-top: 12px; }
.episodes-card { padding: 20px; }
.eps-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.ep-list { display: flex; flex-direction: column; gap: 12px; }
.ep-item { display: flex; gap: 14px; padding: 10px; border-radius: 10px; background: rgba(31, 58, 95, 0.03); }
.ep-item.played { opacity: 0.72; }
.ep-thumb {
  position: relative;
  flex: 0 0 132px;
  height: 76px;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  background: #1c2b3a;
}
.ep-thumb img { width: 100%; height: 100%; object-fit: cover; }
.ep-thumb-ph { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; color: rgba(255, 255, 255, 0.5); }
.ep-progress { position: absolute; left: 0; bottom: 0; height: 3px; background: var(--color-brand); }
.ep-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4px; }
.ep-name { font-size: 14px; font-weight: 600; color: var(--color-text); display: flex; align-items: center; gap: 8px; }
.ep-code { color: var(--color-text-secondary); font-weight: 400; }
.ep-meta { font-size: 12px; color: var(--color-text-secondary); display: flex; gap: 12px; }
.ep-overview { font-size: 12px; color: var(--color-text-secondary); overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.ep-actions { display: flex; flex-direction: column; gap: 6px; justify-content: center; flex-shrink: 0; }

@media (max-width: 768px) {
  .detail-head { flex-direction: column; }
  .poster { width: 100%; height: auto; max-height: 420px; }
  .poster-box { flex: none; }
  .ep-item { flex-wrap: wrap; }
  .ep-thumb { flex: 0 0 100px; height: 60px; }
}
</style>
