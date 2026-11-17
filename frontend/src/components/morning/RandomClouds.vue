<!-- 随机云层:叠在每日早安的天气照片上,只负责「云」,底图与光照仍由 MorningScene 提供。
     一朵云一个元素,由多团随机位置/半径的径向渐变叠成;按天气混入积云、积雨云、层云、雨层云、
     卷云、高积云、层积云与雾堤,形状、尺寸、明暗、速度每次重新抽取;边界硬度按云型定(个体状云边利落、层状云边化开)。
     风把云按风向吹着走,风力越大越快;关闭动效或系统「减少动态效果」时停在可读的静态位置。 -->
<template>
  <div class="rc" :class="{ 'rc-still': !animated }" aria-hidden="true">
    <div v-for="c in clouds" :key="c.id" class="rc-cloud" :style="c.style"></div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  // 归一化后的天气码:clear / cloud / overcast / rain / snow / fog / thunder
  scene: { type: String, default: 'clear' },
  night: { type: Boolean, default: false },
  // 风力(级),字符串 "3-4" 也吃,取第一段数字
  windScale: { type: [Number, String], default: 0 },
  // 风向:优先用后端的 wind360(0=北风,顺时针),没有则按中文 windDir 兜底
  windDeg: { type: [Number, String], default: null },
  windDir: { type: String, default: '' },
  // 关闭后云停在行程中段,不再漂移
  animated: { type: Boolean, default: true },
})

const rand = (min, max) => min + Math.random() * (max - min)
const pct = (n) => n.toFixed(2) + '%'
const px = (n) => n.toFixed(1) + 'px'
const sec = (n) => n.toFixed(2) + 's'
const clamp01 = (n) => Math.min(1, Math.max(0, n))

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
  if (Math.abs(vx) < 0.32) vx = 0.32
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
const rgb = (c) => `${c[0]}, ${c[1]}, ${c[2]}`

// 云是压在天气照片上的:照片里的天空亮度大约在 130–150,云团本身的中间调正好也是这个区间,
// 直接用「天空色」画云会和底图糊成一片(实测几乎看不出形状)。所以把亮部推到近白、暗部压深,
// 只保留色相:亮部靠亮度压住天空,暗部(云底)才是真正让轮廓看得见的那一层。
const cloudTone = () => {
  const base = (props.night ? NIGHT_TONES : DAY_TONES)[props.scene] || DAY_TONES.clear
  return {
    hi: mixRgb(base.hi, [255, 255, 255], 0.55),
    lo: mixRgb(base.lo, props.night ? [16, 22, 34] : [58, 70, 88], 0.5),
  }
}

/**
 * 一团椭圆:透明度分 5 段收到 0。
 * soft 控制边界硬度 —— 这是「云有没有轮廓」的唯一开关:
 *   0.15 ≈ 利落边(积雨云塔、积云泡),实心平台一路铺到 ~52% 半径才收尾;
 *   0.95 ≈ 没有边界(雾堤、雨层云),20% 半径就开始一路化开。
 * 默认 0.55 就是调参前的旧曲线。同一朵云里可以分层混用(上缘利落、云底更实)。
 */
const blob = (c, cx, cy, rx, ry, a, soft = 0.55) => {
  const s = clamp01(soft)
  const k = 0.55 - s // 正数=更硬,负数=更柔
  const p1 = 36 + k * 40
  const p2 = 62 + k * 26
  const p3 = 84 + k * 14
  const a1 = Math.max(0, 0.82 + k * 0.3)
  const a2 = Math.max(0, 0.44 + k * 0.42)
  const a3 = Math.max(0, 0.13 + k * 0.26)
  const alpha = (m) => Math.max(0, a * m).toFixed(2)
  return `radial-gradient(ellipse ${rx.toFixed(1)}% ${ry.toFixed(1)}% at ${cx.toFixed(1)}% ${cy.toFixed(1)}%, rgba(${c}, ${alpha(1)}) 0%, rgba(${c}, ${alpha(a1)}) ${p1.toFixed(1)}%, rgba(${c}, ${alpha(a2)}) ${p2.toFixed(1)}%, rgba(${c}, ${alpha(a3)}) ${p3.toFixed(1)}%, rgba(${c}, 0) 97%)`
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

// 每种云的比例、尺寸、透明度与模糊比(体积云高耸、卷云薄而扁)
// 云要「压得住」天空:透明度整体偏高(薄云仍留 0.4 起步),尺寸往大走,模糊比略收,边界更实
const KIND_SHAPE = {
  cumulus: { ratio: [0.36, 0.52], w: [52, 96], px: [460, 880], op: [0.72, 0.98], blur: [0.012, 0.024] },
  cumulonimbus: { ratio: [0.6, 0.95], w: [42, 78], px: [420, 780], op: [0.85, 1], blur: [0.009, 0.019], tall: true },
  stratus: { ratio: [0.15, 0.25], w: [60, 104], px: [500, 1040], op: [0.62, 0.88], blur: [0.014, 0.026] },
  nimbostratus: { ratio: [0.2, 0.34], w: [62, 106], px: [520, 1080], op: [0.75, 0.98], blur: [0.013, 0.024] },
  cirrus: { ratio: [0.1, 0.18], w: [54, 96], px: [420, 860], op: [0.5, 0.8], blur: [0.004, 0.01], minBlur: 2.5 },
  altocumulus: { ratio: [0.18, 0.3], w: [44, 80], px: [340, 640], op: [0.7, 0.94], blur: [0.008, 0.016], minBlur: 4 },
  stratocumulus: { ratio: [0.26, 0.42], w: [54, 92], px: [450, 860], op: [0.72, 0.96], blur: [0.011, 0.022] },
  fogbank: { ratio: [0.13, 0.22], w: [64, 108], px: [520, 1060], op: [0.46, 0.7], blur: [0.02, 0.036] },
}

/**
 * 边界硬度(0=刀切一样利落,1=完全没有轮廓)——按云型的真实形态定,不再随机:
 * 积雨云/积云/高积云是「个体」,边要看得见;层云/雨层云/雾堤是「一层»,边要化开。
 * 这一列同时决定两件事:每团渐变的收尾速度(见 blob)和整朵云的外层模糊倍率(见 cvBlurRatio)。
 */
const KIND_SOFT = {
  cumulus: 0.24,
  cumulonimbus: 0.16,
  stratus: 0.9,
  nimbostratus: 0.84,
  cirrus: 0.34,
  altocumulus: 0.32,
  stratocumulus: 0.5,
  fogbank: 0.98,
}

/** 整朵云的外层模糊:硬边云只留一点点化边,层状云任其散开 */
const cvBlurRatio = (kind, factor) => factor * (0.45 + (KIND_SOFT[kind] ?? 0.55))

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

/**
 * 一朵云的形状,按种类画不同的层:
 * 积云 = 一堆向上鼓的云泡 + 暗云底;积雨云 = 砧状顶 + 立起来的云塔 + 更黑的底;
 * 层云/雨层云 = 几条极宽极扁的云幕;卷云 = 细长丝缕;高积云 = 成排小云团;雾堤 = 贴地的模糊雾带。
 * 每层带 soft:云的「个体感」全在这里 —— 泡状云边利落、层状云边化开。
 */
function cloudBackground(tone, kind = 'cumulus') {
  const layers = []
  const hi = rgb(tone.hi)
  const lo = rgb(tone.lo)
  const s = KIND_SOFT[kind] ?? 0.55

  if (kind === 'cumulonimbus') {
    // 砧状云顶:顶部向两侧铺开的宽扁亮片,砧的边缘是整朵云最利的
    layers.push(blob(hi, rand(34, 66), rand(9, 20), rand(30, 46), rand(5, 9), rand(0.34, 0.55), s - 0.04))
    layers.push(blob(hi, rand(40, 60), rand(12, 24), rand(46, 66), rand(3, 6), rand(0.18, 0.3), s))
    // 云塔:从云底往上堆,越高越亮、越窄,像一堵立起来的墙
    const puffs = 11 + Math.floor(rand(0, 4))
    for (let i = 0; i < puffs; i++) {
      const t = i / (puffs - 1) // 0 = 云底,1 = 云顶
      const cy = 80 - t * 56 + rand(-4, 4)
      const cx = 50 + rand(-1, 1) * rand(6, 20) * (1 - t * 0.55)
      const rx = rand(14, 24) * (1 - t * 0.45)
      const ry = rand(10, 17) * (1 - t * 0.3)
      const c = rgb(mixRgb(tone.lo, tone.hi, clamp01(0.18 + t * 0.85 + rand(-0.12, 0.12))))
      layers.push(blob(c, cx, cy, rx, ry, rand(0.5, 0.82), s + 0.05))
    }
    // 更黑的云底:雨还没落下来就能看出沉,底线要平要实
    layers.push(blob(lo, rand(38, 62), rand(84, 92), rand(40, 52), rand(9, 14), rand(0.6, 0.82), s - 0.04))
    layers.push(blob(rgb(mixRgb(tone.lo, [30, 38, 50], 0.35)), rand(40, 60), rand(88, 96), rand(34, 46), rand(6, 10), rand(0.45, 0.7), s))
    return layers.join(', ')
  }

  if (kind === 'stratus' || kind === 'nimbostratus') {
    // 层状云:几条宽而扁、明暗接近的云幕,没有云顶也没有边 —— 这里刻意不收紧 soft
    const deck = kind === 'nimbostratus'
    const bands = (deck ? 5 : 3) + Math.floor(rand(0, 3))
    for (let i = 0; i < bands; i++) {
      const cy = rand(24, deck ? 80 : 68)
      const c = rgb(mixRgb(tone.lo, tone.hi, deck ? rand(0.05, 0.4) : rand(0.2, 0.55)))
      layers.push(blob(c, rand(10, 90), cy, rand(34, 56), rand(5, 12), rand(0.32, 0.62), s + rand(-0.04, 0.04)))
    }
    // 上缘一点点亮边,让云幕不至于死平
    layers.push(blob(hi, rand(24, 76), rand(18, 34), rand(30, 44), rand(4, 8), rand(0.14, 0.3), s + 0.06))
    return layers.join(', ')
  }

  if (kind === 'cirrus') {
    // 卷云:几缕细长丝片,像被高空风拉出来的絮 —— 丝缕本身是清楚的,只是太薄
    const c = rgb(mixRgb(tone.hi, [255, 255, 255], 0.28))
    const streaks = 4 + Math.floor(rand(0, 4))
    for (let i = 0; i < streaks; i++) {
      layers.push(blob(c, rand(8, 92), rand(16, 46), rand(10, 26), rand(2.2, 4.6), rand(0.18, 0.4), s))
    }
    // 一缕更长的云尾,尾巴化开
    layers.push(blob(c, rand(30, 70), rand(24, 46), rand(30, 46), rand(3, 5), rand(0.12, 0.26), s + 0.14))
    return layers.join(', ')
  }

  if (kind === 'altocumulus') {
    // 高积云:成排的小云团,颗颗分明,边缘比层状云利落得多
    const rows = 2 + Math.floor(rand(0, 2))
    const puffs = 6 + Math.floor(rand(0, 5))
    for (let r = 0; r < rows; r++) {
      const cy = 30 + r * rand(12, 18)
      for (let i = 0; i < puffs; i++) {
        const cx = (i + rand(0.2, 0.8)) * (100 / puffs)
        const c = rgb(mixRgb(tone.lo, tone.hi, clamp01(0.55 + rand(-0.2, 0.3) - r * 0.12)))
        layers.push(blob(c, cx, cy + rand(-4, 4), rand(4.5, 9), rand(3.2, 6), rand(0.4, 0.68), s))
      }
    }
    return layers.join(', ')
  }

  if (kind === 'stratocumulus') {
    // 层积云:先铺一层化开的云底,再散几团中等云泡 —— 底部连成片,顶上还看得出一颗颗
    layers.push(
      blob(rgb(mixRgb(tone.lo, tone.hi, 0.35)), rand(42, 58), rand(58, 74), rand(40, 52), rand(10, 16), rand(0.4, 0.62), s + 0.16),
    )
    const puffs = 5 + Math.floor(rand(0, 4))
    for (let i = 0; i < puffs; i++) {
      const cy = rand(28, 62)
      const c = rgb(mixRgb(tone.lo, tone.hi, clamp01(1 - cy / 90 + rand(-0.14, 0.14))))
      layers.push(blob(c, rand(10, 90), cy, rand(10, 20), rand(7, 14), rand(0.4, 0.66), s - 0.06))
    }
    return layers.join(', ')
  }

  if (kind === 'fogbank') {
    // 雾堤:贴地的一条条极淡雾带,几乎看不出轮廓
    for (let i = 0; i < 4; i++) {
      layers.push(blob(hi, rand(10, 90), rand(42, 72), rand(36, 54), rand(5, 9), rand(0.2, 0.38), s))
    }
    return layers.join(', ')
  }

  // 积云(默认):一团团向上鼓的云泡 + 顶光 + 压扁的暗云底
  layers.push(blob(hi, rand(26, 58), rand(14, 30), rand(11, 20), rand(8, 14), rand(0.6, 0.82), s - 0.06))
  const billows = 6 + Math.floor(rand(0, 4))
  for (let i = 0; i < billows; i++) {
    const cy = rand(17, 57)
    // 越靠上越亮(顶部受光),越靠下越暗(云底),同一朵云内部也有明暗过渡
    const c = rgb(mixRgb(tone.lo, tone.hi, clamp01(1 - cy / 74 + rand(-0.16, 0.16))))
    layers.push(blob(c, rand(13, 87), cy, rand(15, 31), rand(12, 27), rand(0.55, 0.9), s + rand(-0.04, 0.04)))
  }
  // 云底暗部:宽而扁,把上面几团「压」成一体,底线比云顶硬(淡积云的平底就是这么来的)
  layers.push(blob(lo, rand(40, 60), rand(66, 80), rand(36, 47), rand(12, 19), rand(0.55, 0.7), s - 0.12))
  return layers.join(', ')
}

// ===== 云的天幕:每朵一个元素,形状随机、时长随机,风越大越快 =====
const clouds = ref([])

// 手机屏小:云少一点、模糊半径按「实际渲染宽度」算,免得一朵云糊成一团占满半屏
const compact = typeof window !== 'undefined' && window.innerWidth < 640
const viewportPx = typeof window !== 'undefined' ? window.innerWidth : 1200

function buildClouds() {
  const tone = cloudTone()
  const overcast = props.scene === 'cloud' || props.scene === 'overcast'
  const { vx, vy } = driftVec.value
  const dur = windDuration.value
  // 晴天只点缀几缕高空卷云,别把「晴」画成多云
  const count = props.scene === 'clear' ? (compact ? 3 : 4) : overcast ? (compact ? 8 : 11) : compact ? 6 : props.scene === 'snow' ? 8 : 9
  const kinds = makeKindBag(count)
  clouds.value = Array.from({ length: count }, (_, i) => {
    const kind = kinds[i]
    const shape = KIND_SHAPE[kind]
    // depth:远小近大,拉开层次,避免所有云一个尺寸
    const depth = rand(0.52, 1.25)
    const wPct = rand(shape.w[0], shape.w[1])
    const wPx = rand(shape.px[0], shape.px[1]) * depth
    const ratio = rand(shape.ratio[0], shape.ratio[1])
    const hPx = wPx * ratio
    // 高度上限给够:高积云/卷云靠 px 决定高度,积雨云才需要占到大半屏
    const hPct = Math.min(92, Math.max(9, ratio * 168))
    // 实际渲染宽度 = min(px 上限, 屏宽百分比),模糊半径跟着它走
    const shownW = Math.min(wPx, (wPct / 100) * viewportPx)
    const speed = rand(0.88, 1.15)
    const life = dur * speed
    // 相位均匀铺开(不是纯随机):任意时刻都有云分散在整条行程上,开屏不会全挤在一侧
    const phase = (i + rand(0.2, 0.8)) / count
    // 阴天云多,单朵压得淡一些,叠起来才不会糊成一块死白
    const op = rand(shape.op[0], shape.op[1]) * (overcast ? 0.9 : 1)
    return {
      id: 'c' + i,
      style: {
        // 起步位只留一点抖动:配合均匀相位,整条行程上的云才是均匀铺开的
        left: pct(vx >= 0 ? rand(-18, 0) : rand(100, 118)),
        top: pct(shape.tall ? rand(-10, 22) : rand(-6, overcast ? 50 : 30)),
        width: `min(${px(wPx)}, ${wPct.toFixed(1)}%)`,
        height: `min(${px(hPx)}, ${hPct.toFixed(1)}%)`,
        '--op': op.toFixed(2),
        '--dx': `${Math.round(vx * 152)}vw`,
        '--dy': `${Math.round(vy * 14)}vh`,
        // 云越小模糊越小:边缘化开但不糊成一片(卷云这类细丝不能按宽云的距离糊);
        // 再乘云型的边界硬度 —— 积云/积雨云几乎不留外层模糊,层云/雾堤任其散开
        '--blur': px(Math.min(30, Math.max(shape.minBlur ?? 2, shownW * cvBlurRatio(kind, rand(shape.blur[0], shape.blur[1]))))),
        backgroundImage: cloudBackground(tone, kind),
        animationDuration: sec(life),
        animationDelay: sec(-phase * life),
      },
    }
  })
}

// 天气/昼夜/风力都是异步到位的:到位一次重建一次(一次性生成,运行期不用响应式)
watch([() => props.scene, () => props.night, () => wind.value, () => windDeg.value], buildClouds, { immediate: true })
</script>

<style scoped>
.rc {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  overflow: hidden;
  pointer-events: none;
}

/* 每朵云是一个元素,背景是多团随机位置/半径的柔边径向渐变(见 cloudBackground),
   整体再 blur 一次 → 边界完全化开、云团之间互相渗透,有体积感而不是一张白纸剪影。 */
.rc-cloud {
  /* 默认值:实际值由脚本按天气/风力算好写进 style(这里留默认值也让样式自查守门看得见) */
  --op: 0.8;
  --blur: 16px;
  --dx: 0;
  --dy: 0;
  position: absolute;
  background-repeat: no-repeat;
  opacity: var(--op, 0.8);
  filter: blur(var(--blur, 16px));
  will-change: transform;
  animation-name: rc-cloud-drift;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
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

/* 关闭动效:云停在行程中段(否则会停在屏外) */
.rc-still .rc-cloud {
  animation: none !important;
  opacity: var(--op, 0.8);
  transform: translate3d(calc(var(--dx, 0) * 0.42), calc(var(--dy, 0) * 0.42), 0);
}

@media (prefers-reduced-motion: reduce) {
  .rc-cloud {
    animation: none !important;
    opacity: var(--op, 0.8);
    transform: translate3d(calc(var(--dx, 0) * 0.42), calc(var(--dy, 0) * 0.42), 0);
  }
}
</style>
