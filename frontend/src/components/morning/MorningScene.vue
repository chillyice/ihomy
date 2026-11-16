<!-- 早安页天气场景:按当前天气渲染整屏背景。
     晴 = 蓝天渐变 + 太阳 + 旋转光柱;多云 = 满天阴云(顶棚式云层);雨/雪/雾/雷各有粒子与压暗;
     风:云与落叶按风向漂移,风力越大漂得越快。
     云是一朵一个元素、由 6–9 团随机位置的柔边渐变叠成(无硬边、每朵形状都不同),
     只跑 transform/opacity 的 CSS 动画;关闭动效或系统「减少动态效果」时停在可读的静态位置。 -->
<template>
  <!-- 天气修饰类统一加 ms-cond- 前缀:根元素与内部元素(.ms-cloud/.ms-rain/.ms-snow/.ms-fog)
       同名会把整屏根节点套上云/雨雪的图形样式,表现为画面中间一大团模糊椭圆。 -->
  <div class="ms" :class="[`ms-cond-${scene}`, { 'ms-night': night, 'ms-static': !animated }]" aria-hidden="true">
    <div class="ms-sky"></div>

    <!-- 晴/夜:太阳或月亮 + 光柱 + 星星 -->
    <template v-if="scene === 'clear'">
      <div class="ms-sun"></div>
      <div class="ms-rays"></div>
      <div v-if="night" class="ms-stars">
        <span v-for="s in stars" :key="s.id" class="ms-star" :style="s.style"></span>
      </div>
    </template>

    <!-- 阴/雨/雷/雪:云层(每朵形状随机,按风向漂移) -->
    <div v-if="hasClouds" class="ms-clouds">
      <div v-for="c in clouds" :key="c.id" class="ms-cloud" :style="c.style"></div>
    </div>

    <!-- 雨 -->
    <div v-if="scene === 'rain' || scene === 'thunder'" class="ms-rain" :style="rainFlow">
      <span v-for="r in rain" :key="r.id" class="ms-drop" :style="r.style"></span>
    </div>

    <!-- 雪 -->
    <div v-if="scene === 'snow'" class="ms-snow">
      <span v-for="f in snow" :key="f.id" class="ms-flake" :style="f.style"></span>
    </div>

    <!-- 雾 -->
    <div v-if="scene === 'fog'" class="ms-fog">
      <span v-for="f in fogBands" :key="f.id" class="ms-fog-band" :style="f.style"></span>
    </div>

    <!-- 雷闪 -->
    <div v-if="scene === 'thunder'" class="ms-flash"></div>

    <!-- 风:吹过的落叶(方向与速度跟风走) -->
    <div v-if="windy" class="ms-leaves" :style="leafFlow">
      <span v-for="l in leaves" :key="l.id" class="ms-leaf" :style="l.style"></span>
    </div>

    <div class="ms-scrim"></div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  // 后端归一化天气码:clear / cloud / rain / snow / fog / thunder
  condition: { type: String, default: 'clear' },
  night: { type: Boolean, default: false },
  // 风力(级),字符串 "3-4" 也吃,取第一段数字
  windScale: { type: [Number, String], default: 0 },
  // 风向:优先用后端的 wind360(0=北风,顺时针),没有则按中文 windDir 兜底
  windDeg: { type: [Number, String], default: null },
  windDir: { type: String, default: '' },
  // 降水强度档 1–6,决定雨雪粒子密度
  precipLevel: { type: [Number, String], default: 2 },
  // 关闭后保留配色,不播放动画(设置里可关,系统减少动效时也走这里)
  animated: { type: Boolean, default: true },
})

const SCENES = ['clear', 'cloud', 'rain', 'snow', 'fog', 'thunder']

const scene = computed(() => {
  const c = String(props.condition || 'clear').toLowerCase()
  return SCENES.includes(c) ? c : 'clear'
})

const level = computed(() => {
  const n = Number.parseFloat(props.precipLevel)
  return Number.isFinite(n) ? Math.min(6, Math.max(0, n)) : 2
})

const wind = computed(() => {
  const n = Number.parseFloat(props.windScale)
  return Number.isFinite(n) ? Math.min(12, Math.max(0, n)) : 0
})

const hasClouds = computed(() => ['cloud', 'rain', 'snow', 'thunder'].includes(scene.value))
// 雨雪雷本身已有粒子,不再叠落叶;晴/多云/雾的大风天才有落叶
const windy = computed(() => wind.value >= 3 && ['clear', 'cloud', 'fog'].includes(scene.value))

const rand = (min, max) => min + Math.random() * (max - min)
const pct = (n) => n.toFixed(2) + '%'
const px = (n) => n.toFixed(1) + 'px'
const sec = (n) => n.toFixed(2) + 's'
const clamp01 = (n) => Math.min(1, Math.max(0, n))

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
  rain: { hi: [210, 220, 232], lo: [110, 124, 143] },
  snow: { hi: [246, 250, 255], lo: [170, 187, 205] },
  fog: { hi: [244, 247, 250], lo: [176, 186, 196] },
  thunder: { hi: [150, 164, 182], lo: [62, 75, 93] },
}
const NIGHT_TONES = {
  clear: { hi: [198, 212, 236], lo: [92, 106, 134] },
  cloud: { hi: [150, 164, 188], lo: [60, 73, 96] },
  rain: { hi: [132, 146, 168], lo: [48, 60, 80] },
  snow: { hi: [172, 186, 208], lo: [74, 88, 112] },
  fog: { hi: [166, 178, 196], lo: [64, 76, 94] },
  thunder: { hi: [104, 117, 136], lo: [34, 44, 58] },
}

const mixRgb = (a, b, t) => a.map((v, i) => Math.round(v + (b[i] - v) * t))
const rgb = (c) => `${c[0]}, ${c[1]}, ${c[2]}`

/**
 * 一朵云的形状:6–9 团随机位置/半径的柔边圆(顶部受光偏亮、云底偏暗),
 * 再压一层宽扁暗底和一小团顶光。全部用 transparent 收尾,没有硬边界。
 */
function cloudBackground(tone) {
  const layers = []
  // 顶光(最先画 = 最上层)
  const hi = rgb(tone.hi)
  layers.push(
    `radial-gradient(ellipse ${rand(11, 20).toFixed(1)}% ${rand(8, 14).toFixed(1)}% at ${rand(26, 58).toFixed(1)}% ${rand(14, 30).toFixed(1)}%, rgba(${hi}, 0.82) 0%, rgba(${hi}, 0.3) 56%, rgba(${hi}, 0) 96%)`,
  )
  const billows = 6 + Math.floor(rand(0, 4))
  for (let i = 0; i < billows; i++) {
    const cx = rand(13, 87)
    const cy = rand(17, 57)
    const rx = rand(15, 31)
    const ry = rand(12, 27)
    // 越靠上越亮(顶部受光),越靠下越暗(云底),同一朵云内部也有明暗过渡
    const lift = clamp01(1 - cy / 74 + rand(-0.16, 0.16))
    const c = rgb(mixRgb(tone.lo, tone.hi, lift))
    const a = rand(0.55, 0.9)
    layers.push(
      `radial-gradient(ellipse ${rx.toFixed(1)}% ${ry.toFixed(1)}% at ${cx.toFixed(1)}% ${cy.toFixed(1)}%, rgba(${c}, ${a.toFixed(2)}) 0%, rgba(${c}, ${(a * 0.68).toFixed(2)}) 34%, rgba(${c}, ${(a * 0.32).toFixed(2)}) 60%, rgba(${c}, ${(a * 0.09).toFixed(2)}) 81%, rgba(${c}, 0) 97%)`,
    )
  }
  // 云底暗部:宽而扁,把上面几团「压」成一体
  const lo = rgb(tone.lo)
  layers.push(
    `radial-gradient(ellipse ${rand(36, 47).toFixed(1)}% ${rand(12, 19).toFixed(1)}% at ${rand(40, 60).toFixed(1)}% ${rand(66, 80).toFixed(1)}%, rgba(${lo}, 0.7) 0%, rgba(${lo}, 0.32) 52%, rgba(${lo}, 0) 94%)`,
  )
  return layers.join(', ')
}

// ===== 云的天幕:每朵一个元素,形状随机、时长随机,风越大越快 =====
const clouds = ref([])

// 手机屏小:云少一点、模糊半径按「实际渲染宽度」算,免得一朵云糊成一团占满半屏
const compact = typeof window !== 'undefined' && window.innerWidth < 640
const viewportPx = typeof window !== 'undefined' ? window.innerWidth : 1200

function buildClouds() {
  const tone = (props.night ? NIGHT_TONES : DAY_TONES)[scene.value] || DAY_TONES.clear
  const overcast = scene.value === 'cloud'
  const { vx, vy } = driftVec.value
  const dur = windDuration.value
  const count = overcast ? (compact ? 8 : 11) : compact ? 6 : scene.value === 'snow' ? 8 : 9
  clouds.value = Array.from({ length: count }, (_, i) => {
    // depth:远小近大,拉开层次,避免所有云一个尺寸
    const depth = rand(0.52, 1.25)
    const wPct = rand(44, 88)
    const hPct = rand(15, 33)
    const wPx = (overcast ? rand(420, 860) : rand(380, 720)) * depth
    const hPx = wPx * rand(0.34, 0.5)
    // 实际渲染宽度 = min(px 上限, 屏宽百分比),模糊半径跟着它走
    const shownW = Math.min(wPx, (wPct / 100) * viewportPx)
    const speed = rand(0.88, 1.15)
    const life = dur * speed
    // 相位均匀铺开(不是纯随机):任意时刻都有云分散在整条行程上,开屏不会全挤在一侧
    const phase = (i + rand(0.2, 0.8)) / count
    // 阴天云多,单朵压得淡一些,叠起来才不会糊成一块死白
    const opLo = overcast ? 0.4 : 0.46
    const opHi = overcast ? 0.76 : 0.86
    return {
      id: 'c' + i,
      style: {
        // 起步位只留一点抖动:配合均匀相位,整条行程上的云才是均匀铺开的
        left: pct(vx >= 0 ? rand(-18, 0) : rand(100, 118)),
        top: pct(rand(-6, overcast ? 50 : 30)),
        width: `min(${px(wPx)}, ${wPct.toFixed(1)}%)`,
        height: `min(${px(hPx)}, ${hPct.toFixed(1)}%)`,
        '--op': rand(opLo, opHi).toFixed(2),
        '--dx': `${Math.round(vx * 152)}vw`,
        '--dy': `${Math.round(vy * 14)}vh`,
        // 云越小模糊越小:边缘化开但不糊成一片
        '--blur': px(Math.min(30, Math.max(7, shownW * rand(0.026, 0.05)))),
        backgroundImage: cloudBackground(tone),
        animationDuration: sec(life),
        animationDelay: sec(-phase * life),
      },
    }
  })
}

// ===== 落叶:方向跟风,风越大越快 =====
const leaves = ref([])
const leafFlow = computed(() => ({
  '--lx': driftVec.value.vx >= 0 ? '132vw' : '-132vw',
  '--lx-start': driftVec.value.vx >= 0 ? '-14vw' : '104vw',
}))
// 雨丝也跟着风斜:风往哪吹,雨就往哪斜
const rainFlow = computed(() => {
  const vx = driftVec.value.vx
  return { '--rx': `${(vx * 10).toFixed(1)}vw`, '--tilt': `${vx >= 0 ? -11 : 11}deg` }
})

function buildLeaves() {
  if (!windy.value) {
    leaves.value = []
    return
  }
  const count = Math.round(6 + wind.value * 2.2)
  leaves.value = Array.from({ length: count }, (_, i) => ({
    id: 'l' + i,
    style: {
      top: pct(rand(4, 84)),
      width: px(rand(9, 16)),
      height: px(rand(5, 9)),
      opacity: rand(0.55, 0.95).toFixed(2),
      '--ly': `${rand(-14, 12).toFixed(1)}vh`,
      '--rot': `${Math.round(rand(420, 1080) * (driftVec.value.vx >= 0 ? 1 : -1))}deg`,
      animationDuration: sec(rand(9, 15) / Math.max(0.6, wind.value / 3)),
      animationDelay: sec(-rand(0, 12)),
    },
  }))
}

function buildScene() {
  buildClouds()
  buildLeaves()
}

// 天气/昼夜/风力都是异步到位的:到位一次重建一次(一次性生成,运行期不用响应式)
watch([() => scene.value, () => props.night, () => wind.value, () => windDeg.value], buildScene, { immediate: true })

const stars = Array.from({ length: 38 }, (_, i) => ({
  id: 's' + i,
  style: {
    left: pct(rand(0, 100)),
    top: pct(rand(0, 62)),
    width: px(rand(1.5, 3.4)),
    height: px(rand(1.5, 3.4)),
    opacity: rand(0.35, 0.95).toFixed(2),
    animationDuration: sec(rand(2.4, 6.5)),
    animationDelay: sec(-rand(0, 6)),
  },
}))

const rain = Array.from({ length: Math.round(22 + level.value * 8) }, (_, i) => ({
  id: 'r' + i,
  style: {
    left: pct(rand(0, 100)),
    height: px(rand(16, 36)),
    opacity: rand(0.22, 0.6).toFixed(2),
    animationDuration: sec(rand(0.55, 1.15)),
    animationDelay: sec(-rand(0, 2)),
  },
}))

const snow = Array.from({ length: Math.round(18 + level.value * 8) }, (_, i) => ({
  id: 'f' + i,
  style: {
    left: pct(rand(0, 100)),
    width: px(rand(4, 10)),
    height: px(rand(4, 10)),
    opacity: rand(0.45, 0.95).toFixed(2),
    animationDuration: sec(rand(7, 15)),
    animationDelay: sec(-rand(0, 15)),
  },
}))

const fogBands = Array.from({ length: 4 }, (_, i) => ({
  id: 'g' + i,
  style: {
    top: pct(rand(8, 74)),
    height: px(rand(90, 200)),
    opacity: rand(0.25, 0.6).toFixed(2),
    animationDuration: sec(rand(26, 52)),
    animationDelay: sec(-rand(0, 30)),
  },
}))
</script>

<style scoped>
.ms {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  overflow: hidden;
  pointer-events: none;
  contain: layout style paint;
}

/* ===== 天空底色 ===== */
.ms-sky {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  transition: background 1.2s ease;
}
.ms-cond-clear .ms-sky {
  background: linear-gradient(180deg, #4a9fe0 0%, #8ec9f2 42%, #d9f0ff 100%);
}
/* 阴天不是蓝天:整片灰蓝幕,越靠天顶越沉 */
.ms-cond-cloud .ms-sky {
  background: linear-gradient(180deg, #6e8398 0%, #8fa2b3 46%, #c2cfda 100%);
}
.ms-cond-rain .ms-sky {
  background: linear-gradient(180deg, #3d4d60 0%, #5f7288 52%, #8b9caa 100%);
}
.ms-cond-snow .ms-sky {
  background: linear-gradient(180deg, #7b90a5 0%, #b6c7d7 52%, #edf5fb 100%);
}
.ms-cond-fog .ms-sky {
  background: linear-gradient(180deg, #8a98a4 0%, #bdc8d0 58%, #e4eaee 100%);
}
.ms-cond-thunder .ms-sky {
  background: linear-gradient(180deg, #1d2531 0%, #374657 55%, #586879 100%);
}

/* 夜间:统一压成深色天空,晴天偏蓝紫 */
.ms-night .ms-sky {
  background: linear-gradient(180deg, #0a1226 0%, #162642 55%, #2b3d5c 100%);
}
.ms-night.ms-cond-clear .ms-sky {
  background: linear-gradient(180deg, #081124 0%, #10203c 48%, #24395c 100%);
}
.ms-night.ms-cond-cloud .ms-sky {
  background: linear-gradient(180deg, #0b1420 0%, #172330 48%, #2b3947 100%);
}
.ms-night.ms-cond-rain .ms-sky,
.ms-night.ms-cond-thunder .ms-sky {
  background: linear-gradient(180deg, #0d1420 0%, #1d2a3a 55%, #33414f 100%);
}
.ms-night.ms-cond-snow .ms-sky,
.ms-night.ms-cond-fog .ms-sky {
  background: linear-gradient(180deg, #18222f 0%, #2c3b4d 55%, #46566a 100%);
}

/* ===== 太阳 / 月亮 / 光柱 ===== */
.ms-sun {
  position: absolute;
  right: 11%;
  top: 7%;
  width: clamp(150px, 21vw, 250px);
  aspect-ratio: 1;
  border-radius: 50%;
  /* 明确的日盘边缘 + 一圈不扩散的柔光,避免变成一团糊在中景的大光斑 */
  background: radial-gradient(
    circle,
    #fffdf4 0%,
    #fff3c4 42%,
    rgba(255, 232, 160, 0.85) 62%,
    rgba(255, 226, 148, 0.35) 74%,
    transparent 82%
  );
  box-shadow: 0 0 70px 18px rgba(255, 234, 170, 0.42);
  animation: ms-sun-breathe 9s ease-in-out infinite alternate;
}
.ms-night .ms-sun {
  right: 14%;
  top: 8%;
  width: clamp(100px, 14vw, 170px);
  background: radial-gradient(circle at 38% 36%, #fbfdff 0%, #dbe5f5 46%, rgba(196, 210, 234, 0.55) 66%, transparent 80%);
  box-shadow: 0 0 56px 12px rgba(190, 210, 255, 0.3);
  animation-duration: 14s;
}

.ms-rays {
  position: absolute;
  top: -60vmax;
  right: -60vmax;
  width: 160vmax;
  height: 160vmax;
  background: repeating-conic-gradient(from 0deg, rgba(255, 246, 220, 0.14) 0deg 2.5deg, transparent 2.5deg 15deg);
  /* 遮罩只留靠近太阳的一圈,光柱从日盘向外散开,不在画面中部结成一个圆盘 */
  -webkit-mask-image: radial-gradient(circle, rgba(0, 0, 0, 0.9) 0%, rgba(0, 0, 0, 0.45) 24%, transparent 52%);
  mask-image: radial-gradient(circle, rgba(0, 0, 0, 0.9) 0%, rgba(0, 0, 0, 0.45) 24%, transparent 52%);
  mix-blend-mode: screen;
  animation: ms-ray-spin 150s linear infinite;
}
.ms-cond-cloud .ms-rays {
  opacity: 0.5;
}
.ms-cond-rain .ms-rays,
.ms-cond-snow .ms-rays,
.ms-cond-fog .ms-rays,
.ms-cond-thunder .ms-rays {
  display: none;
}
.ms-night .ms-rays {
  opacity: 0.38;
  animation-duration: 220s;
}

/* ===== 星星 ===== */
.ms-stars {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
}
.ms-star {
  position: absolute;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 0 6px 1px rgba(210, 226, 255, 0.7);
  animation-name: ms-twinkle;
  animation-timing-function: ease-in-out;
  animation-iteration-count: infinite;
  animation-direction: alternate;
}

/* ===== 云 =====
   每朵云是一个元素,背景是多团随机位置/半径的柔边径向渐变(见 cloudBackground),
   整体再 blur 一次 → 边界完全化开、云团之间互相渗透,有体积感而不是一张白纸剪影。
   形状、尺寸、明暗、速度都随机,所以不会出现两朵一样的云。 */
.ms-clouds {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
}
.ms-cloud {
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
  animation-name: ms-cloud-drift;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
}

/* ===== 雨 ===== */
.ms-rain {
  --rx: -8vw;
  --tilt: 11deg;
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
}
.ms-drop {
  position: absolute;
  top: -8vh;
  width: 1.5px;
  border-radius: 2px;
  background: linear-gradient(to bottom, rgba(220, 238, 255, 0), rgba(220, 238, 255, 0.9));
  transform: rotate(var(--tilt, 11deg));
  animation-name: ms-rain-fall;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
}

/* ===== 雪 ===== */
.ms-snow {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
}
.ms-flake {
  position: absolute;
  top: -6vh;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 0 5px 1px rgba(255, 255, 255, 0.55);
  animation-name: ms-snow-fall;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
}

/* ===== 雾 ===== */
.ms-fog {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
}
.ms-fog-band {
  position: absolute;
  left: -30%;
  width: 160%;
  background: linear-gradient(90deg, transparent 0%, rgba(255, 255, 255, 0.75) 32%, rgba(255, 255, 255, 0.5) 58%, transparent 100%);
  filter: blur(22px);
  animation-name: ms-fog-drift;
  animation-timing-function: ease-in-out;
  animation-iteration-count: infinite;
  animation-direction: alternate;
}

/* ===== 雷闪 ===== */
.ms-flash {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  background: #eaf4ff;
  opacity: 0;
  animation: ms-lightning 9s linear infinite;
}

/* ===== 落叶 ===== */
.ms-leaves {
  --lx: 132vw;
  --lx-start: -14vw;
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
}
.ms-leaf {
  --ly: 0vh;
  --rot: 720deg;
  position: absolute;
  left: var(--lx-start, -14vw);
  border-radius: 0 70% 0 70%;
  background: linear-gradient(135deg, #e0b055 0%, #b97a2d 100%);
  box-shadow: 0 1px 2px rgba(70, 40, 0, 0.18);
  animation-name: ms-leaf-fly;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
}

/* ===== 可读性压暗 ===== */
.ms-scrim {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  background: linear-gradient(180deg, rgba(8, 14, 28, 0.16) 0%, rgba(8, 14, 28, 0.04) 38%, rgba(8, 14, 28, 0.46) 100%);
}

/* ===== 动画 ===== */
@keyframes ms-sun-breathe {
  0% {
    transform: scale(1);
    opacity: 0.9;
  }
  100% {
    transform: scale(1.06);
    opacity: 1;
  }
}
@keyframes ms-ray-spin {
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
}
@keyframes ms-twinkle {
  0% {
    opacity: 0.2;
  }
  100% {
    opacity: 1;
  }
}
/* 云:沿风向平移一整段(时长/距离由风力风向量算好写进 --dx/--dy),
   两端淡入淡出,循环时看不出「跳回起点」 */
@keyframes ms-cloud-drift {
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
@keyframes ms-rain-fall {
  0% {
    transform: translate3d(0, -10vh, 0) rotate(var(--tilt, 11deg));
  }
  100% {
    transform: translate3d(var(--rx, -8vw), 112vh, 0) rotate(var(--tilt, 11deg));
  }
}
@keyframes ms-snow-fall {
  0% {
    transform: translate3d(0, -8vh, 0);
  }
  45% {
    transform: translate3d(28px, 46vh, 0);
  }
  100% {
    transform: translate3d(-22px, 110vh, 0);
  }
}
@keyframes ms-fog-drift {
  0% {
    transform: translate3d(-8vw, 0, 0);
  }
  100% {
    transform: translate3d(9vw, 0, 0);
  }
}
@keyframes ms-lightning {
  0%,
  86%,
  100% {
    opacity: 0;
  }
  87% {
    opacity: 0.85;
  }
  88.5% {
    opacity: 0.12;
  }
  90% {
    opacity: 0.65;
  }
  92% {
    opacity: 0;
  }
}
@keyframes ms-leaf-fly {
  0% {
    transform: translate3d(0, 0, 0) rotate(0deg);
    opacity: 0;
  }
  8% {
    opacity: 1;
  }
  100% {
    transform: translate3d(var(--lx, 132vw), var(--ly, 0), 0) rotate(var(--rot, 720deg));
    opacity: 0;
  }
}

/* 关闭动效:保留配色与静态粒子,云停在行程中段(否则会停在屏外) */
.ms-static *,
.ms-static *::before,
.ms-static *::after {
  animation: none !important;
}
.ms-static .ms-cloud {
  opacity: var(--op, 0.8);
  transform: translate3d(calc(var(--dx, 0) * 0.42), calc(var(--dy, 0) * 0.42), 0);
}
.ms-static .ms-drop,
.ms-static .ms-flake,
.ms-static .ms-leaf {
  opacity: 0.35;
}

@media (prefers-reduced-motion: reduce) {
  .ms *,
  .ms *::before,
  .ms *::after {
    animation: none !important;
  }
  .ms .ms-cloud {
    opacity: var(--op, 0.8);
    transform: translate3d(calc(var(--dx, 0) * 0.42), calc(var(--dy, 0) * 0.42), 0);
  }
}
</style>
