<!-- 随机云叠在天气照片上。密度纹理只在场景切换时生成，风只负责平移云层。 -->
<template>
  <div ref="layer" class="rc" :style="driftStyle" :class="{ 'rc-still': !animated }" aria-hidden="true">
    <canvas v-for="c in clouds" :key="c.seed" class="rc-cloud" :style="c.style"></canvas>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, shallowRef, watch } from 'vue'
import { paintCloud } from './cloudTexture'

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
const pct = (n) => n.toFixed(2) + '%'
const px = (n) => n.toFixed(1) + 'px'
const sec = (n) => n.toFixed(2) + 's'

const wind = computed(() => {
  const n = Number.parseFloat(props.windScale)
  return Number.isFinite(n) ? Math.min(12, Math.max(0, n)) : 0
})

// ===== 风向 =====
// 后端 wind360 是「来向」角度(0=北风,90=东风,顺时针);没有该字段时按中文风向兜底。
const WIND_DEG = {
  北风: 0,
  北东北风: 22.5,
  东北风: 45,
  东东北风: 67.5,
  东风: 90,
  东东南风: 112.5,
  东南风: 135,
  南东南风: 157.5,
  南风: 180,
  南西南风: 202.5,
  西南风: 225,
  西西南风: 247.5,
  西风: 270,
  西西北风: 292.5,
  西北风: 315,
  北西北风: 337.5,
}
// 认不出风向时按西风带常风(由西向东)处理,比原地不动更自然
const westerly = 270

const windDeg = computed(() => {
  const n = Number.parseFloat(props.windDeg)
  if (Number.isFinite(n)) return ((n % 360) + 360) % 360
  const key = String(props.windDir || '').replace(/\s+/g, '')
  if (key in WIND_DEG) return WIND_DEG[key]
  const short = key.replace(/风$/, '')
  const shortMap = { 北: 0, 东北: 45, 东: 90, 东南: 135, 南: 180, 西南: 225, 西: 270, 西北: 315 }
  if (short in shortMap) return shortMap[short]
  const first = short.charAt(0)
  if (first in { 北: 1, 东: 1, 南: 1, 西: 1 }) return { 北: 0, 东: 90, 南: 180, 西: 270 }[first]
  return westerly
})

// 屏幕坐标:x 向右=东,y 向下=南。风「从」d 吹向 d+180,分解成屏幕分量。
const driftVec = computed(() => {
  const toward = ((windDeg.value + 180) * Math.PI) / 180
  let vx = Math.sin(toward)
  const vy = Math.cos(toward) * 0.3
  // 南北风时横向分量接近 0,云只上下挪会显得很怪:给个最小横向速度,保持整体横向漂移
  if (Math.abs(vx) < 0.32) vx = vx < -0.001 ? -0.32 : 0.32
  return { vx, vy }
})

// 风力越大云越快:210s(无风)→ 4 级约 86s → 8 级约 35s
const windDuration = computed(() => Math.max(24, 210 * Math.pow(0.8, wind.value)))

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
const clouds = shallowRef([])
const compact = typeof window !== 'undefined' && window.innerWidth < 640

const driftStyle = computed(() => {
  const { vx, vy } = driftVec.value
  return {
    '--start': vx > 0 ? '0' : '1',
    '--dx': `calc(${vx > 0 ? 1 : -1} * (100vw + 100%))`,
    '--dy': `${(vy * 22).toFixed(2)}vh`,
    '--wind-duration': sec(windDuration.value / (0.75 + 0.25 * Math.abs(vx))),
  }
})

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
      const phase = (i + rand(0.28, 0.72)) / count
      const width = compact ? 224 : 320
      const height = Math.max(56, Math.round(width * ratio))
      const low = kind === 'fogbank' || props.scene === 'fog'
      return {
        kind,
        seed: Math.floor(rand(0, 4294967296)),
        width,
        height,
        style: {
          top: pct(low ? rand(26, 60) : shape.tall ? rand(-8, 11) : rand(-7, overcast ? 38 : 28)),
          // 低矮的横屏上仍把云限制在风景线以上。
          '--width': `min(${px(wPx)}, ${wPct.toFixed(1)}%, ${((shape.tall ? 52 : 38) / ratio).toFixed(1)}vh)`,
          aspectRatio: (1 / ratio).toFixed(3),
          '--still-left': pct(-17 + phase * 105),
          '--op': rand(...shape.op).toFixed(2),
          '--speed': rand(0.87, 1.16).toFixed(3),
          '--phase': phase.toFixed(3),
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
  --start: 0;
  --dx: 0px;
  --dy: 0px;
  --wind-duration: 210s;
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  overflow: hidden;
  pointer-events: none;
}

/* 画布位图保持不变，漂移只动画 transform 与 opacity。 */
.rc-cloud {
  --op: 0.8;
  --width: 60%;
  --still-left: 0%;
  --speed: 1;
  --phase: 0;
  position: absolute;
  left: calc(var(--start) * 100% - (1 - var(--start)) * var(--width));
  width: var(--width);
  height: auto;
  opacity: var(--op);
  animation: rc-cloud-drift calc(var(--wind-duration) * var(--speed)) linear infinite;
  animation-delay: calc(-1 * var(--wind-duration) * var(--speed) * var(--phase));
}

/* 云:沿风向平移一整段(时长/距离由风力风向量算好写进 --dx/--dy),
   两端淡入淡出,循环时看不出「跳回起点」 */
@keyframes rc-cloud-drift {
  0% {
    transform: translate3d(0, 0, 0);
    opacity: 0;
  }
  7% {
    opacity: var(--op, 0.8);
  }
  90% {
    opacity: var(--op, 0.8);
  }
  100% {
    transform: translate3d(var(--dx, 0), var(--dy, 0), 0);
    opacity: 0;
  }
}

/* 静止时按相位铺开，避免云全部停在起点或挤在中央。 */
.rc-still .rc-cloud {
  animation: none;
  left: var(--still-left);
}

@media (prefers-reduced-motion: reduce) {
  .rc-cloud {
    animation: none;
    left: var(--still-left);
  }
}
</style>
