<!--
  放映厅媒体引擎播放器(海报墙与详情页共用一份实现):
  打开时现取播放地址(地址带播放令牌、会过期,不能存列表里),播放中按节流上报进度,播完标记看过。
  两条播放路:直出原文件(浏览器能解的编码);放不了或直出报错时回退 HLS 转码流(hls.js)。
  字幕:文本字幕挂 <track>(签名 URL 中转,与直出/转码无关);位图字幕只能烧进转码画面,选中后重取地址。
  进度上报给家庭媒体服务器,所以同一账号在网页与电视端 App 共用一份「继续观看」。
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
        v-if="rendered"
        ref="videoRef"
        :src="src || null"
        controls
        autoplay
        class="player-video"
        @loadedmetadata="onLoadedMetadata"
        @timeupdate="onTimeUpdate"
        @pause="reportProgress"
        @ended="onEnded"
        @error="onPlaybackError"
      >
        <track
          v-for="(s, i) in textTracks"
          :key="s.index"
          :src="s.url"
          :kind="s.isForced ? 'forced' : 'subtitles'"
          :srclang="s.language || 'und'"
          :label="labelOf(s)"
          :default="i === 0 && s.isDefault"
        />
      </video>
      <div v-else-if="!loading" class="player-error">{{ $t('cinema.playFailed') }}</div>

      <div v-if="rendered" class="player-bar">
        <el-select v-if="subtitles.length" :model-value="subtitleIndex" size="small" class="sub-select" @change="onSubtitleChange">
          <el-option :value="-1" :label="$t('cinema.subtitleOff')" />
          <el-option
            v-for="s in subtitles"
            :key="s.index"
            :value="s.index"
            :label="labelOf(s) + (s.burnIn ? ` · ${$t('cinema.subtitleBurnIn')}` : '')"
          />
        </el-select>
        <span v-if="transcoding" class="player-warn">{{
          $t(transcodeReason === 'subtitle' ? 'cinema.transcodingSubtitle' : 'cinema.notDirectPlayable')
        }}</span>
        <span v-else-if="failed" class="player-warn">{{ $t('cinema.playFailed') }}</span>
      </div>
    </div>
    <template #footer>
      <span v-if="item" class="player-foot">{{ item.seriesName ? `${item.seriesName} · ${code}` : item.name }}</span>
      <el-button @click="emit('update:modelValue', false)">{{ $t('common.close') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch, inject, nextTick, onBeforeUnmount } from 'vue'
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
/** 常见字幕语言码 → 界面语言名(认不出来的直接显示原码) */
const LANG_KEYS = {
  chi: 'zh',
  zho: 'zh',
  zh: 'zh',
  cmn: 'zh',
  yue: 'zh',
  eng: 'en',
  en: 'en',
  jpn: 'ja',
  ja: 'ja',
  kor: 'ko',
  ko: 'ko',
  fra: 'fr',
  fre: 'fr',
  fr: 'fr',
  deu: 'de',
  ger: 'de',
  de: 'de',
  spa: 'es',
  es: 'es',
  rus: 'ru',
  ru: 'ru',
  por: 'pt',
  pt: 'pt',
  ita: 'it',
  it: 'it',
  tha: 'th',
  th: 'th',
}

const rendered = ref(false)
/** 直出模式给 <video src>;HLS 模式留空,由 hls.js 接管 */
const src = ref('')
const subtitles = ref([])
const subtitleIndex = ref(-1)
const transcoding = ref(false)
/** 转码原因:编码放不了 / 位图字幕要烧进画面(提示文案不同) */
const transcodeReason = ref('codec')
const failed = ref(false)
const loading = ref(false)
const videoRef = ref(null)

let hls = null
let playUrl = ''
let hlsUrl = ''
let mode = 'direct'
/** 当前烧进转码画面的位图字幕轨(-1 = 没烧);烧进去时不挂 <track>,否则同一句话会画两遍 */
let burnIn = -1
let fallbackTried = false
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

/** 只有文本字幕能挂 <track>(位图字幕得烧进转码画面) */
const textTracks = computed(() => subtitles.value.filter((s) => s.text && s.url))

const labelOf = (s) => {
  const key = LANG_KEYS[String(s.language || '').toLowerCase()]
  const name = key ? t(`cinema.lang.${key}`) : s.language || s.title || ''
  return s.isForced ? `${name} (${t('cinema.subtitleForced')})` : name || `${t('cinema.subtitle')} ${s.index}`
}

// 打开:取地址并定位到上次进度;关闭:把最终位置补报一次,并恢复光影层
watch(
  () => props.modelValue,
  async (open) => {
    if (open) {
      sunLight?.suspendEffects()
      await load()
    } else {
      reportProgress()
    }
  },
)

/**
 * 装载播放地址。
 * burnInIdx 只对位图字幕有意义(烧进转码画面),其余情况不传;
 * preferredIdx 是重取地址后要停在字幕下拉的那个选项(切字幕重取时用,别被「默认轨」顶掉);
 * atTicks 用于保留播放位置 —— 重取地址会重建 <video>,位置得自己接着。
 */
const load = async (burnInIdx, preferredIdx, atTicks) => {
  // 重建播放器前先记下当前位置:切字幕重取地址后要接着播,不能跳回打开时那一处
  const el = videoRef.value
  const fromTicks =
    atTicks != null ? atTicks : el && el.currentTime > 0 ? Math.round(el.currentTime * TICKS_PER_SECOND) : props.item?.positionTicks || 0
  reset()
  if (!props.item?.id) return
  loading.value = true
  try {
    const r = await mediaApi.play(props.item.id, burnInIdx)
    playUrl = r.url
    hlsUrl = r.hlsUrl || ''
    mode = r.mode === 'hls' ? 'hls' : 'direct'
    subtitles.value = r.subtitles || []
    burnIn = typeof r.burnInSubtitle === 'number' ? r.burnInSubtitle : -1
    // 烧进画面的位图轨不挂 <track>(避免重影);没有烧进去时才按默认文本轨/指定项打开
    subtitleIndex.value = burnIn >= 0 ? burnIn : preferredIdx != null ? preferredIdx : pickSubtitle(subtitles.value)
    transcodeReason.value = burnIn >= 0 ? 'subtitle' : 'codec'
    seekToTicks = fromTicks
    lastReportAt = 0
    finished = false
    // 先让 <video> 渲染出来(hls.js 要 attachMedia),再决定用哪条路
    rendered.value = true
    await nextTick()
    if (mode === 'hls') {
      transcoding.value = true
      await playHls(playUrl)
    } else {
      src.value = playUrl
    }
  } catch {
    failed.value = true
    ElMessage.error(t('cinema.playFailed'))
  } finally {
    loading.value = false
  }
}

/** 默认字幕:媒体服务器标了默认的文本轨就开它,否则关闭(别替用户做主) */
const pickSubtitle = (list) => {
  const def = list.find((s) => s.text && s.isDefault)
  return def ? def.index : -1
}

/** HLS 回退:交给 hls.js(Safari 等原生支持 HLS 的浏览器直接给 src) */
const playHls = async (url) => {
  destroyHls()
  src.value = ''
  const el = videoRef.value
  if (!el) return
  try {
    const { default: Hls } = await import('hls.js')
    if (Hls.isSupported()) {
      hls = new Hls()
      hls.on(Hls.Events.ERROR, (_evt, data) => {
        if (data?.fatal) onPlaybackError()
      })
      hls.loadSource(url)
      hls.attachMedia(el)
      return
    }
  } catch {
    /* 库加载失败就退回原生 src(能省一步是一步) */
  }
  src.value = url
}

const destroyHls = () => {
  if (hls) {
    try {
      hls.destroy()
    } catch {
      /* 已销毁 */
    }
    hls = null
  }
}

const reset = () => {
  destroyHls()
  src.value = ''
  rendered.value = false
  subtitles.value = []
  subtitleIndex.value = -1
  transcoding.value = false
  transcodeReason.value = 'codec'
  failed.value = false
  fallbackTried = false
  burnIn = -1
  playUrl = ''
  hlsUrl = ''
}

/**
 * 直出播放失败(浏览器其实放不了这个封装/编码)时回退到转码流;
 * 转码流也失败才算真失败(此时提示用电视/手机 App 看)。
 * 注意:没有 src / 还在挂 MSE 时不理会 error —— 空 src 会解析成当前页地址并报一次假错误。
 */
const onPlaybackError = () => {
  const el = videoRef.value
  if (!el || (!el.currentSrc && !el.src)) return
  if (mode === 'direct' && hlsUrl && !fallbackTried) {
    fallbackTried = true
    mode = 'hls'
    transcodeReason.value = 'codec'
    transcoding.value = true
    // 报错也可能发生在播放中途:位置从当前播放器取,取不到(刚起播就失败)才回到打开时的续看点
    seekToTicks = (el.currentTime > 0 ? Math.round(el.currentTime * TICKS_PER_SECOND) : 0) || props.item?.positionTicks || 0
    playHls(hlsUrl)
    return
  }
  failed.value = true
  transcoding.value = false
}

/**
 * 字幕切换:文本轨直接切 <track>;位图轨只能烧进画面 → 带 index 重取地址;
 * 从「已烧进去的」切回关闭或文本轨时也要重取一次不带它的地址,否则烧进画面的字幕关不掉。
 */
const onSubtitleChange = (value) => {
  const track = subtitles.value.find((s) => s.index === value)
  if (track && track.burnIn) {
    load(value, value)
    return
  }
  if (burnIn >= 0) {
    load(undefined, value)
    return
  }
  applyTrackMode()
}

/** 文本轨按选择开启(浏览器原生控件里的字幕菜单与这里的状态保持一致) */
const applyTrackMode = () => {
  const el = videoRef.value
  if (!el || !el.textTracks) return
  // 位图字幕已烧进画面:wanted 取 -1,所有 <track> 关掉,免得与烧进去的字幕重叠显示
  const wanted = burnIn >= 0 ? -1 : subtitleIndex.value
  const list = textTracks.value
  for (let i = 0; i < el.textTracks.length; i += 1) {
    const track = el.textTracks[i]
    track.mode = list[i] && list[i].index === wanted ? 'showing' : 'disabled'
  }
}

// 续看定位必须等元数据加载完(此刻才能改 currentTime);顺带把默认字幕开起来
const onLoadedMetadata = () => {
  const el = videoRef.value
  if (el && seekToTicks > 0) el.currentTime = seekToTicks / TICKS_PER_SECOND
  seekToTicks = 0
  applyTrackMode()
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
  } catch {
    /* 静默:下次进详情页会看到真实状态 */
  }
}

const onClosed = () => {
  reset()
  sunLight?.restoreEffects()
}

onBeforeUnmount(() => {
  reportProgress()
  destroyHls()
  sunLight?.restoreEffects()
})
</script>

<style scoped>
.media-player {
  min-height: 120px;
}
.player-video {
  width: 100%;
  max-height: 72vh;
  background: #000;
  border-radius: 8px;
}
.player-error {
  padding: 40px 0;
  text-align: center;
  color: var(--color-text-secondary);
}
.player-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
  min-height: 24px;
}
.sub-select {
  width: 200px;
}
.player-warn {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.player-foot {
  float: left;
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 32px;
}
</style>
