<!-- 随机云叠在天气照片上。密度纹理只在场景切换时生成，风只负责平移云层。 -->
<template>
  <div ref="layer" class="rc" :class="{ 'morning-cloud-still': !animated }" aria-hidden="true">
    <canvas
      v-for="(c, i) in movingClouds"
      :key="c.seed"
      class="rc-cloud morning-cloud-motion"
      :style="c.style"
      @animationend="recycleCloud($event, i)"
    ></canvas>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { paintCloud } from './cloudTexture'
import { morningWindVector, sampleCloudFlight } from './cloudMotion'
import { useCloudFlights } from './useCloudFlights'
import './cloudMotion.css'

const props = defineProps({
  // 归一化后的天气码:clear / cloud / overcast / rain / snow / fog / thunder
  scene: { type: String, default: 'clear' },
  night: { type: Boolean, default: false },
  // 风力(级),字符串 "3-4" 也吃,取第一段数字
  windScale: { type: [Number, String], default: 0 },
  // 风向:优先用后端的 wind360(0=北风,顺时针),没有则按中文 windDir 兜底
  windDeg: { type: [Number, String], default: null },
  windDir: { type: String, default: '' },
  // 关闭后保留整片静态云，不再漂移
  animated: { type: Boolean, default: true },
})

const rand = (min, max) => min + Math.random() * (max - min)
const px = (n) => n.toFixed(1) + 'px'

const wind = computed(() => {
  const n = Number.parseFloat(props.windScale)
  return Number.isFinite(n) ? Math.min(12, Math.max(0, n)) : 0
})

const drift = computed(() => morningWindVector(props.windDeg, props.windDir))

// ===== 云的配色(上亮下暗,给云团体积感) =====
// 阴天/雨天是「满天阴云」,云本身是灰的:比天顶亮、比地平线暗,不要用纯白(叠几层会糊成一片白)
const DAY_TONES = {
  clear: { hi: [255, 255, 255], lo: [210, 224, 238] },
  cloud: { hi: [228, 235, 243], lo: [146, 161, 179] },
  overcast: { hi: [206, 214, 224], lo: [118, 130, 146] },
  rain: { hi: [210, 220, 232], lo: [110, 124, 143] },
  snow: { hi: [246, 250, 255], lo: [170, 187, 205] },
  fog: { hi: [244, 247, 250], lo: [176, 186, 196] },
  thunder: { hi: [150, 164, 182], lo: [62, 75, 93] },
}
const NIGHT_TONES = {
  clear: { hi: [198, 212, 236], lo: [92, 106, 134] },
  cloud: { hi: [150, 164, 188], lo: [60, 73, 96] },
  overcast: { hi: [132, 145, 166], lo: [48, 60, 80] },
  rain: { hi: [132, 146, 168], lo: [48, 60, 80] },
  snow: { hi: [172, 186, 208], lo: [74, 88, 112] },
  fog: { hi: [166, 178, 196], lo: [64, 76, 94] },
  thunder: { hi: [104, 117, 136], lo: [34, 44, 58] },
}

const mixRgb = (a, b, t) => a.map((v, i) => Math.round(v + (b[i] - v) * t))

// 叠在天气照片上的云保留亮部与阴影的对比。夜间和雷雨少掺白，贴近底图的光照。
const cloudTone = () => {
  const base = (props.night ? NIGHT_TONES : DAY_TONES)[props.scene] || DAY_TONES.clear
  return {
    hi: mixRgb(base.hi, [255, 255, 255], props.night || props.scene === 'thunder' ? 0.16 : 0.49),
    lo: mixRgb(base.lo, props.night ? [16, 22, 34] : [58, 70, 88], 0.25),
  }
}

/**
 * 云的种类:同一片天里混着不同形态才像真的。
 * 每种天气给一组权重,抽签决定这一朵画成什么云。
 */
const CLOUD_KINDS = {
  clear: [
    ['cirrus', 5],
    ['altocumulus', 1],
    ['cumulus', 1],
  ],
  cloud: [
    ['cumulus', 3],
    ['stratocumulus', 2],
    ['altocumulus', 2],
    ['cirrus', 1],
  ],
  overcast: [
    ['stratus', 3],
    ['stratocumulus', 2],
    ['nimbostratus', 1],
    ['cumulus', 1],
  ],
  rain: [
    ['nimbostratus', 3],
    ['stratocumulus', 2],
    ['cumulonimbus', 1],
  ],
  snow: [
    ['stratus', 2],
    ['cumulus', 2],
    ['nimbostratus', 1],
    ['altocumulus', 1],
  ],
  fog: [
    ['stratus', 3],
    ['fogbank', 2],
  ],
  thunder: [
    ['cumulonimbus', 3],
    ['nimbostratus', 2],
    ['stratocumulus', 1],
  ],
}

// 近处的积云厚，远处的卷云薄；边缘与内部纹理由 cloudTexture 决定。
const KIND_SHAPE = {
  cumulus: { ratio: [0.36, 0.57], w: [56, 100], px: [460, 880], op: [0.78, 1] },
  cumulonimbus: { ratio: [0.6, 0.95], w: [56, 92], px: [440, 850], op: [0.85, 1], tall: true },
  stratus: { ratio: [0.15, 0.27], w: [78, 126], px: [640, 1220], op: [0.58, 0.84] },
  nimbostratus: { ratio: [0.21, 0.39], w: [84, 128], px: [660, 1260], op: [0.74, 0.96] },
  cirrus: { ratio: [0.13, 0.27], w: [65, 106], px: [480, 950], op: [0.63, 0.86] },
  altocumulus: { ratio: [0.19, 0.32], w: [58, 96], px: [430, 770], op: [0.69, 0.93] },
  stratocumulus: { ratio: [0.26, 0.42], w: [65, 110], px: [560, 970], op: [0.68, 0.94] },
  fogbank: { ratio: [0.16, 0.28], w: [88, 132], px: [660, 1260], op: [0.42, 0.62] },
}

/** 按权重抽一种云;整片天用抽样袋循环,保证相邻几朵不是同一种 */
function makeKindBag(count) {
  const table = CLOUD_KINDS[props.scene] || CLOUD_KINDS.cloud
  const bag = []
  table.forEach(([kind, weight]) => {
    for (let i = 0; i < weight; i++) bag.push(kind)
  })
  for (let i = bag.length - 1; i > 0; i--) {
    const j = Math.floor(rand(0, i + 1))
    const tmp = bag[i]
    bag[i] = bag[j]
    bag[j] = tmp
  }
  return Array.from({ length: count }, (_, i) => bag[i % bag.length])
}

const layer = ref(null)
const { clouds, movingClouds, recycleCloud } = useCloudFlights(drift, wind)
const compact = typeof window !== 'undefined' && window.innerWidth < 640

watch(
  [() => props.scene, () => props.night],
  async (_, __, onCleanup) => {
    let cancelled = false
    let timer
    onCleanup(() => {
      cancelled = true
      clearTimeout(timer)
    })

    const overcast = ['cloud', 'overcast', 'rain', 'thunder'].includes(props.scene)
    const count = props.scene === 'clear' ? (compact ? 3 : 4) : compact ? 6 : overcast ? 9 : 7
    const kinds = makeKindBag(count)
    const tone = cloudTone()
    const fleet = kinds.map((kind, i) => {
      const shape = KIND_SHAPE[kind]
      const depth = rand(0.62, 1.18)
      const wPct = rand(...shape.w)
      const wPx = rand(...shape.px) * depth
      const ratio = rand(...shape.ratio)
      const width = compact ? 224 : 320
      const height = Math.max(56, Math.round(width * ratio))
      const low = kind === 'fogbank' || props.scene === 'fog'
      const heightBand = low ? [34, 61] : shape.tall ? [16, 28] : [8, overcast ? 44 : 34]
      return {
        kind,
        seed: Math.floor(rand(0, 4294967296)),
        width,
        height,
        heightBand,
        rest: { x: ((i + rand(0.18, 0.82)) / count) * 104 - 2, y: rand(...heightBand) },
        flight: sampleCloudFlight(heightBand, 1, i),
        style: {
          // 低矮的横屏上仍把云限制在风景线以上。
          width: `min(${px(wPx)}, ${wPct.toFixed(1)}%, ${((shape.tall ? 52 : 38) / ratio).toFixed(1)}vh)`,
          aspectRatio: (1 / ratio).toFixed(3),
          '--track-opacity': rand(...shape.op).toFixed(2),
        },
      }
    })
    clouds.value = fleet
    await nextTick()
    if (cancelled || !layer.value) return

    // 一次只画一朵，把首屏让给问候界面；换天气或关闭时取消剩余绘制。
    const canvases = layer.value.querySelectorAll('canvas')
    let i = 0
    const paintNext = () => {
      const { kind, seed, width, height } = fleet[i]
      paintCloud(canvases[i], kind, seed, tone, width, height)
      if (++i < fleet.length) timer = setTimeout(paintNext, 0)
    }
    timer = setTimeout(paintNext, 0)
  },
  { immediate: true },
)
</script>

<style scoped>
.rc {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.rc-cloud {
  height: auto;
}
</style>
