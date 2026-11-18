<!-- 战绩分享弹窗:把本局分数画成一张 1080×1350 的竖版海报,
     可保存图片 / 复制图片 / 拉起系统分享(壳内走 Capacitor Share)。
     版式与绘制在 utils/games/scoreCard.js,这里是预览 + 出口;颜色一律从主题变量取,
     所以暖居/深色主题下生成的图和当前界面同一套配色。 -->
<template>
  <el-dialog
    :model-value="modelValue"
    :title="$t('games.share.dialogTitle')"
    width="min(360px, 92vw)"
    :append-to-body="appendToBody"
    destroy-on-close
    class="score-share-dialog"
    @update:model-value="emit('update:modelValue', $event)"
    @opened="render"
  >
    <div class="ss-body">
      <canvas ref="canvasEl" class="ss-canvas" :width="CARD_WIDTH" :height="CARD_HEIGHT" />
      <p class="ss-hint">{{ $t('games.share.hint') }}</p>
    </div>
    <template #footer>
      <div class="ss-actions">
        <el-button size="small" @click="save">{{ $t('games.share.save') }}</el-button>
        <el-button size="small" @click="copy">{{ $t('games.share.copy') }}</el-button>
        <el-button size="small" type="primary" @click="share">{{ $t('games.share.share') }}</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { shareBlob } from '@/native/shareOut'
import { todayStr } from '@/utils/date'
import { CARD_HEIGHT, CARD_WIDTH, computeScoreCardLayout, drawScoreCard } from '@/utils/games/scoreCard'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 海报大标题,一般是游戏名(可带对局状态后缀) */
  title: { type: String, default: '' },
  /** 数据块:最多 4 块,{ label, value } */
  stats: { type: Array, default: () => [] },
  /** 要画进海报的方块 [{ row, col, value }],不给就只画空棋盘 */
  tiles: { type: Array, default: () => [] },
  gridSize: { type: Number, default: 4 },
  /** 方块配色 (value) => ({ bg, fg }),缺省用主题主色淡底 */
  tileColor: { type: Function, default: null },
  /** 下载文件名前缀,最终 `前缀-YYYY-MM-DD.png` */
  fileBase: { type: String, default: 'ihomy-score' },
  /** 全屏时传 false:弹窗挂进全屏元素内,挂在 body 上会看不见 */
  appendToBody: { type: Boolean, default: true },
})
const emit = defineEmits(['update:modelValue'])

const { t, locale } = useI18n()
const canvasEl = ref(null)

// 日期按当前语言排版:中文「2026年11月18日」、英文「November 18, 2026」
const subtitle = computed(() => {
  try {
    return new Intl.DateTimeFormat(locale.value, { year: 'numeric', month: 'long', day: 'numeric' }).format(new Date())
  } catch (e) {
    return todayStr()
  }
})

const fileName = computed(() => `${props.fileBase || 'ihomy-score'}-${todayStr()}.png`)

// Canvas 拿不到样式表,主题色只能这样读一次(打开弹窗时读,和当前主题一致)
const readColors = () => {
  const cs = getComputedStyle(document.documentElement)
  const pick = (name, fallback) => cs.getPropertyValue(name).trim() || fallback
  return {
    bg: pick('--color-bg', '#f7f4ec'),
    card: pick('--color-card', '#ffffff'),
    board: pick('--color-card-2', '#e8dec8'),
    text: pick('--color-text', '#2f2f2f'),
    secondary: pick('--color-text-secondary', '#8a8a8a'),
    brand: pick('--color-brand', '#6b7f6e'),
  }
}

const render = () => {
  const canvas = canvasEl.value
  if (!canvas) return false
  const ctx = canvas.getContext('2d')
  if (!ctx) return false
  const layout = computeScoreCardLayout({ gridSize: props.gridSize, statsCount: props.stats.length })
  drawScoreCard(ctx, {
    layout,
    title: props.title,
    subtitle: subtitle.value,
    stats: props.stats,
    tiles: props.tiles,
    footer: t('games.share.footer'),
    colors: readColors(),
    tileColor: props.tileColor,
  })
  return true
}

// 弹窗内容是懒渲染的:开启后等一帧再画,同时用 @opened 兜底
watch(
  () => props.modelValue,
  (open) => {
    if (open) nextTick(render)
  },
)

const toBlob = () =>
  new Promise((resolve, reject) => {
    const canvas = canvasEl.value
    if (!canvas) {
      reject(new Error('canvas missing'))
      return
    }
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('blob empty'))), 'image/png')
  })

const save = async () => {
  try {
    const blob = await toBlob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = fileName.value
    a.rel = 'noopener'
    document.body.appendChild(a)
    a.click()
    a.remove()
    setTimeout(() => URL.revokeObjectURL(url), 4000)
    ElMessage.success(t('games.share.saved'))
  } catch (e) {
    console.warn('[score-share] 保存失败', e)
    ElMessage.error(t('games.share.failed'))
  }
}

const copy = async () => {
  if (!navigator.clipboard?.write || typeof ClipboardItem === 'undefined') {
    ElMessage.warning(t('games.share.copyUnsupported'))
    return
  }
  try {
    const blob = await toBlob()
    await navigator.clipboard.write([new ClipboardItem({ [blob.type || 'image/png']: blob })])
    ElMessage.success(t('games.share.copied'))
  } catch (e) {
    console.warn('[score-share] 复制失败', e)
    ElMessage.error(t('games.share.copyUnsupported'))
  }
}

const share = async () => {
  try {
    const blob = await toBlob()
    await shareBlob(blob, fileName.value, props.title)
  } catch (e) {
    console.warn('[score-share] 分享失败', e)
    ElMessage.error(t('games.share.failed'))
  }
}
</script>

<style scoped>
.ss-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}
/* 预览按宽高双限缩:手机横屏时先让高度,竖屏时先让宽度,海报比例不变 */
.ss-canvas {
  width: auto;
  height: auto;
  max-width: 100%;
  max-height: 46vh;
  border-radius: 12px;
  box-shadow: 0 10px 28px rgba(0, 0, 0, 0.18);
}
.ss-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  text-align: center;
  color: var(--color-text-secondary, #8a8a8a);
}
.ss-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
