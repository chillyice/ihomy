<!--
  放映厅媒体引擎播放器(海报墙与详情页共用一份实现):
  打开时现取播放地址(地址带播放令牌、会过期,不能存列表里),播放中按节流上报进度,播完标记看过。
  进度上报给家庭媒体服务器,所以网页与电视端 App 共用同一份「继续观看」。
-->
<template>
  <el-dialog
    :model-value="modelValue"
    append-to-body
    destroy-on-close
    width="860px"
    top="5vh"
    :title="title"
    @update:model-value="(v) => emit('update:modelValue', v)"
    @closed="onClosed"
  >
    <div v-loading="loading" class="media-player">
      <video
        v-if="url"
        ref="videoRef"
        :src="url"
        controls
        autoplay
        class="player-video"
        @loadedmetadata="onLoadedMetadata"
        @timeupdate="onTimeUpdate"
        @pause="reportProgress"
        @ended="onEnded"
      />
      <div v-else-if="!loading" class="player-error">{{ $t('cinema.playFailed') }}</div>
      <div v-if="url && !playable" class="player-warn">{{ $t('cinema.notDirectPlayable') }}</div>
    </div>
    <template #footer>
      <span v-if="item" class="player-foot">{{ item.seriesName ? `${item.seriesName} · ${code}` : item.name }}</span>
      <el-button @click="emit('update:modelValue', false)">{{ $t('common.close') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch, inject, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { mediaApi } from '@/api'
import { ElMessage } from 'element-plus'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  // { id, name, seriesName, seasonNumber, episodeNumber, positionTicks }
  item: { type: Object, default: null },
})
const emit = defineEmits(['update:modelValue', 'played'])

const { t } = useI18n()
const sunLight = inject(SUN_LIGHT_KEY, null)

const TICKS_PER_SECOND = 10000000
/** 进度上报节流:15 秒一次,不每帧打接口 */
const REPORT_INTERVAL_MS = 15000

const url = ref('')
const playable = ref(true)
const loading = ref(false)
const videoRef = ref(null)
let lastReportAt = 0
let seekToTicks = 0
/** 已播完:位置归零并标记看过,收尾时不再上报位置(否则会以 100% 留在「继续观看」里) */
let finished = false

const code = computed(() => {
  const it = props.item
  if (!it?.seasonNumber) return ''
  return `S${String(it.seasonNumber).padStart(2, '0')}E${String(it.episodeNumber || 1).padStart(2, '0')}`
})

const title = computed(() => {
  const it = props.item
  if (!it) return ''
  if (it.seriesName) return code.value ? `${it.seriesName} ${code.value}` : it.seriesName
  return it.name
})

// 打开:取地址并定位到上次进度;关闭:把最终位置补报一次,并恢复光影层
watch(() => props.modelValue, async (open) => {
  if (open) {
    sunLight?.suspendEffects()
    await load()
  } else {
    reportProgress()
  }
})

const load = async () => {
  url.value = ''
  if (!props.item?.id) return
  loading.value = true
  try {
    const r = await mediaApi.play(props.item.id)
    url.value = r.url
    playable.value = r.playable !== false
    seekToTicks = props.item.positionTicks || 0
    lastReportAt = 0
    finished = false
  } catch {
    ElMessage.error(t('cinema.playFailed'))
  } finally {
    loading.value = false
  }
}

// 续看定位必须等元数据加载完(此刻才能改 currentTime)
const onLoadedMetadata = () => {
  const el = videoRef.value
  if (el && seekToTicks > 0) el.currentTime = seekToTicks / TICKS_PER_SECOND
  seekToTicks = 0
}

const onTimeUpdate = () => {
  const now = Date.now()
  if (now - lastReportAt < REPORT_INTERVAL_MS) return
  reportProgress()
}

/** 只上报位置,不带 played —— 否则每次心跳都会把已看过的片子翻回未看 */
const reportProgress = () => {
  const el = videoRef.value
  if (!el || !props.item?.id || finished) return
  // 自然播完时 pause 与 ended 会先后触发:把「已在片尾」的那次位置上报拦掉,
  // 否则两次上报并发,位置(约 100%)可能晚于「播完」落库,这一集就永远挂在「继续观看」里
  if (el.ended || (el.duration > 0 && el.currentTime >= el.duration - 0.5)) {
    finished = true
    return
  }
  lastReportAt = Date.now()
  const ticks = Math.max(0, Math.round((el.currentTime || 0) * TICKS_PER_SECOND))
  mediaApi.progress(props.item.id, { positionTicks: ticks }).catch(() => {})
}

/** 播完:位置归零 + 标记看过,一次调用收尾(「继续观看」里自然消失) */
const onEnded = async () => {
  finished = true
  try {
    await mediaApi.progress(props.item.id, { positionTicks: 0, played: true })
    emit('played', props.item.id)
  } catch { /* 静默:下次进详情页会看到真实状态 */ }
}

const onClosed = () => {
  url.value = ''
  sunLight?.restoreEffects()
}

onBeforeUnmount(() => {
  reportProgress()
  sunLight?.restoreEffects()
})
</script>

<style scoped>
.media-player { min-height: 120px; }
.player-video { width: 100%; max-height: 72vh; background: #000; border-radius: 8px; }
.player-error { padding: 40px 0; text-align: center; color: var(--color-text-secondary); }
.player-warn { margin-top: 8px; font-size: 12px; color: var(--color-text-secondary); }
.player-foot { float: left; font-size: 13px; color: var(--color-text-secondary); line-height: 32px; }
</style>
