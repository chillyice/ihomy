<!-- 全局光影层:体积光 + 窗框阴影 + 台灯 + 灰尘 + 背景色块 + 闪电 + 雾霭 -->
<!-- z-index 分层:bg-blobs(1)→glass-bg(2)→内容(10)→侧栏(60)→音乐(62)→页脚(70)→沉浸层(150)→弹窗(200–260)=UI 栈;bright-spot(270)→window-shadow(272)→reflection(274)→light-layer(276)→fog(278)=背景光影;vignette(300)→dust(302)→snow/rain(304)→lightning(306)→lamp-light(308)=氛围层。**两组光影都盖在弹窗之上**(用户口径:弹窗永远低于光影) -->
<template>
  <div class="sun-light-layer" aria-hidden="true">
    <!-- 背景色块:5 个 blur 色块随机飘动(可开关) -->
    <div v-if="blobsEnabled" class="bg-blobs">
      <div class="blob blob-1"></div>
      <div class="blob blob-2"></div>
      <div class="blob blob-3"></div>
      <div class="blob blob-4"></div>
      <div class="blob blob-5"></div>
    </div>

    <!-- 毛玻璃背景层:全屏固定,半透明米色覆盖色块(不用 backdrop-filter 避免与面板叠加) -->
    <div class="glass-bg"></div>

    <!-- 亮斑图层:multiply 染色,台灯 mask 挖洞(阴影开关关闭时不渲染) -->
    <div
      v-if="shadowEnabled && (brightSpotStyle?.opacity ?? 0) > 0.01"
      class="bright-spot"
      :style="{ ...brightSpotStyle, '--lamp-mask': lampMask }"
    ></div>

    <!-- 窗户阴影:6 条 bar + 天气覆盖阴影,multiply,台灯 mask 挖洞(阴影开关可关闭;深度为 0 时整层不渲染) -->
    <div
      v-if="shadowEnabled && (shadowAlpha ?? 0) > 0.01"
      class="window-shadow"
      :style="{
        '--rot': (sunScene.shadowVRotation || 0) + 'deg',
        '--htop': (sunScene.shadowHTop || 50) + '%',
        '--shadow-alpha': shadowAlpha ?? 0.5,
        '--shadow-color': sunScene.shadowColor || 'rgb(0,0,0)',
        '--bar-transition': sunScene.isNight ? '0s' : '3s ease',
        '--frame-top-offset': (sunScene.frameTopOffset ?? 0) + 'vh',
        '--lamp-mask': lampMask,
      }"
    >
      <div class="shadow-bar frame-h-top"></div>
      <div class="shadow-bar frame-h-bottom"></div>
      <div class="shadow-bar shadow-v"></div>
      <div class="shadow-bar shadow-h"></div>
      <div class="shadow-bar frame-v-left"></div>
      <div class="shadow-bar frame-v-right"></div>
      <!-- 天气覆盖阴影:固定不移动,雨雪常显;多云太阳遮挡时显示 -->
      <div class="shadow-bar weather-shadow" :style="{ opacity: weatherShadowOpacity }"></div>
    </div>

    <!-- 反光层:soft-light 高光(opacity 为 0 时完全移除,跳过 blend 计算) -->
    <div v-if="(reflectionStyle?.opacity ?? 0) > 0.01" class="reflection-layer" :style="reflectionStyle"></div>

    <!-- 柔和暗角:边缘微压暗,台灯 mask 挖洞(阴影开关可关闭) -->
    <div v-if="shadowEnabled" class="vignette" :style="{ '--lamp-mask': lampMask }"></div>

    <!-- 体积光:丁达尔效应,screen,光源在页面外上方(天气系数实时应用,opacity 为 0 时完全移除) -->
    <div
      v-if="(lightLayerOpacity ?? 0) > 0.01"
      class="light-layer"
      :style="{ opacity: lightLayerOpacity ?? 0, transition: sunScene.isNight ? 'none' : 'opacity 3s ease' }"
    >
      <div class="light-bloom" :style="bloomStyle"></div>
      <div class="light-source" :style="sourceStyle">
        <div v-for="(rs, i) in rayStyles" :key="i" class="light-ray" :style="rs"></div>
      </div>
    </div>

    <!-- 台灯光源:左上黄金分割点 + 钟摆运动,最顶层(opacity 为 0 时移除) -->
    <div
      v-if="(lampDivOpacity ?? 0) > 0.01"
      class="lamp-light lamp-light-pendulum"
      :style="{
        opacity: lampDivOpacity,
        top: '38.2%',
        width: lampRadius * 2 + 'vw',
        height: lampRadius * 2 + 'vw',
        background:
          'radial-gradient(circle, rgba(' +
          lampColor +
          ',0.6) 0%, rgba(' +
          lampColor +
          ',0.45) 15%, rgba(' +
          lampColor +
          ',0.3) 35%, rgba(' +
          lampColor +
          ',0.18) 55%, rgba(' +
          lampColor +
          ',0.08) 75%, transparent 95%)',
      }"
    ></div>

    <!-- 灰尘粒子:光路中的飘浮微粒(天气系数实时应用,opacity 为 0 时移除) -->
    <div v-if="(lightLayerOpacity ?? 0) > 0.01" class="dust-layer" :style="{ opacity: lightLayerOpacity ?? 0 }">
      <div
        v-for="d in dustParticles"
        :key="d.id"
        class="dust"
        :style="{
          left: d.left,
          top: d.top,
          width: d.size + 'px',
          height: d.size + 'px',
          animationDuration: d.duration + 's',
          animationDelay: d.delay + 's',
          '--drift': d.drift + 'px',
        }"
      ></div>
    </div>

    <!-- 雪花粒子:从页面最顶端飘落到最底端,数量=降水等级×10,六瓣雪花样式 -->
    <div v-if="snowParticles.length" class="snow-layer">
      <div
        v-for="s in snowParticles"
        :key="s.id"
        class="snowflake"
        :style="{
          left: s.left,
          fontSize: s.size + 'px',
          opacity: s.opacity,
          animationDuration: s.duration + 's',
          animationDelay: s.delay + 's',
          '--drift': s.drift + 'px',
        }"
      >
        ❄
      </div>
    </div>

    <!-- 雷雨闪电:瞬间增亮全屏,screen 混合(闪电时显示) -->
    <div v-if="shadowEnabled && (lightningFlash ?? 0) > 0.01" class="lightning-flash" :style="{ opacity: lightningFlash }"></div>

    <!-- 雨滴粒子:快速下落,数量=降水等级×10 -->
    <div v-if="rainParticles.length" class="rain-layer">
      <div
        v-for="r in rainParticles"
        :key="r.id"
        class="raindrop"
        :style="{
          left: r.left,
          height: r.height + 'px',
          opacity: r.opacity,
          animationDuration: r.duration + 's',
          animationDelay: r.delay + 's',
        }"
      ></div>
    </div>

    <!-- 雾霭层:高斯噪声雾(进页后几秒内浮现再淡出消散,不常驻;雾/阴的昏暗压光与 3 秒后台灯补光不受影响) -->
    <Transition name="fog">
      <div v-if="weatherEffectEnabled && fogVisible" class="fog-layer" aria-hidden="true">
        <div v-for="b in FOG_LAYERS" :key="b.id" class="fog-noise" :style="fogLayerStyle(b)"></div>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { inject } from 'vue'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'

// 从 App.vue 注入同一 useSunLight 实例(与 AppSidebar 控制台灯共享状态)
const light = inject(SUN_LIGHT_KEY)
if (!light) {
  console.warn('[SunLightLayer] 未注入光影状态,确保 App.vue 调用了 provide(SUN_LIGHT_KEY, useSunLight())')
}
const {
  sunScene,
  lampMask,
  lampDivOpacity,
  lampRadius,
  lampColor,
  shadowEnabled,
  shadowAlpha,
  weatherShadowOpacity,
  lightLayerOpacity,
  blobsEnabled,
  weatherEffectEnabled,
  dustParticles,
  snowParticles,
  rainParticles,
  rayStyles,
  sourceStyle,
  bloomStyle,
  brightSpotStyle,
  reflectionStyle,
  lightningFlash,
  fogVisible,
} = light || {}

// 雾层用「高斯噪声」而不是几个径向渐变雾团:内联 SVG 的 feTurbulence(fractalNoise)
// 生成一张可无缝平铺(stitchTiles)的云絮噪声图,经 feGaussianBlur 软化(高斯模糊后的噪声 =
// 细颗粒的雾),再用 feColorMatrix 把颜色定成雾的冷灰、透明度取噪声本身。
// 浏览器只栅格化这一次,之后当普通背景图缓存;动画只走 transform/opacity(纯合成器),
// 不像 filter:blur 那样随动画周期性重栅格化(与 .bg-blobs 的 ponytail 注记同因)。
// 三层不同颗粒尺寸(260/420/680px)与漂移速度叠在一起,避免平铺接缝被看出来。
const FOG_NOISE_SVG = `<svg xmlns='http://www.w3.org/2000/svg' width='320' height='320'>
  <filter id='fog' x='0' y='0' width='100%' height='100%'>
    <feTurbulence type='fractalNoise' baseFrequency='0.012 0.018' numOctaves='4' seed='7' stitchTiles='stitch'/>
    <feGaussianBlur stdDeviation='3'/>
    <feColorMatrix type='matrix' values='0 0 0 0 0.83 0 0 0 0 0.86 0 0 0 0 0.9 0.3 0.3 0.3 0 0'/>
  </filter>
  <rect width='320' height='320' filter='url(#fog)'/>
</svg>`
const FOG_NOISE = `url("data:image/svg+xml,${encodeURIComponent(FOG_NOISE_SVG)}")`
// 每层的颗粒尺寸 / 不透明度 / 漂移幅度与周期(负 delay 让动画起始就在中途)
const FOG_LAYERS = [
  { id: 1, size: 260, o: 0.28, dx: '4vw', dy: '2vh', dur: 26, delay: -4 },
  { id: 2, size: 420, o: 0.2, dx: '-3vw', dy: '-2vh', dur: 34, delay: -11 },
  { id: 3, size: 680, o: 0.14, dx: '2vw', dy: '-3vh', dur: 46, delay: -18 },
]
const fogLayerStyle = (b) => ({
  backgroundImage: FOG_NOISE,
  backgroundSize: b.size + 'px ' + b.size + 'px',
  opacity: b.o,
  animationDuration: b.dur + 's',
  animationDelay: b.delay + 's',
  '--dx': b.dx,
  '--dy': b.dy,
})
</script>

<style scoped>
.sun-light-layer {
  pointer-events: none;
  /* 不设 position/inset/z-index:子元素都是 fixed,各自在 root stacking context 中 */
  /* 这样 z-index 270/300 等与页面内容(z=10)、弹窗(z=200)在同一 context 比较,和以前 Home.vue 一样 */
}

/* 毛玻璃背景层:全屏固定,半透明米色,覆盖色块使其柔和(不用 backdrop-filter 避免与面板叠加) */
.glass-bg {
  position: fixed;
  inset: 0;
  z-index: 2;
  pointer-events: none;
  background: var(--light-glass-bg, rgba(250, 246, 236, 0.25));
  transform: translateZ(0);
}

/* 背景色块:清新淡雅,随机飘动(fixed 固定不随页面滚动) */
/* ponytail: 用预模糊 radial-gradient 替代 filter:blur(80px) —— 带 filter 的层随 scale 动画
   会被 Chromium 周期性重栅格化,叠加上方 N 张 backdrop-filter 卡片即首页频闪根因;
   渐变纹理一次栅格化,translate-only 漂移纯合成器变换,视觉等效零滤波成本 */
.bg-blobs {
  position: fixed;
  inset: 0;
  z-index: 1;
  overflow: hidden;
  pointer-events: none;
  transform: translateZ(0);
}
.blob {
  position: absolute;
  border-radius: 50%;
  opacity: 0.25;
  will-change: transform;
  transition:
    opacity 1s ease,
    box-shadow 1s ease;
}
html.dark .blob {
  opacity: 0.08;
}
html.dark .blob-1 {
  box-shadow: 0 0 120px 40px rgba(var(--blob-1), 0.4);
}
html.dark .blob-2 {
  box-shadow: 0 0 120px 40px rgba(var(--blob-2), 0.4);
}
html.dark .blob-3 {
  box-shadow: 0 0 120px 40px rgba(var(--blob-3), 0.4);
}
html.dark .blob-4 {
  box-shadow: 0 0 120px 40px rgba(var(--blob-4), 0.4);
}
html.dark .blob-5 {
  box-shadow: 0 0 120px 40px rgba(var(--blob-5), 0.4);
}
.blob-1 {
  width: 480px;
  height: 480px;
  top: -120px;
  left: -100px;
  background: radial-gradient(circle, rgb(var(--blob-1)) 0%, rgba(var(--blob-1), 0.55) 45%, transparent 70%);
  animation: drift1 22s ease-in-out infinite;
}
.blob-2 {
  width: 560px;
  height: 560px;
  top: 25%;
  right: -180px;
  background: radial-gradient(circle, rgb(var(--blob-2)) 0%, rgba(var(--blob-2), 0.55) 45%, transparent 70%);
  animation: drift2 26s ease-in-out infinite;
}
.blob-3 {
  width: 420px;
  height: 420px;
  bottom: -120px;
  left: 18%;
  background: radial-gradient(circle, rgb(var(--blob-3)) 0%, rgba(var(--blob-3), 0.55) 45%, transparent 70%);
  animation: drift3 20s ease-in-out infinite;
}
.blob-4 {
  width: 380px;
  height: 380px;
  top: 35%;
  left: 32%;
  background: radial-gradient(circle, rgb(var(--blob-4)) 0%, rgba(var(--blob-4), 0.55) 45%, transparent 70%);
  animation: drift4 24s ease-in-out infinite;
}
.blob-5 {
  width: 300px;
  height: 300px;
  bottom: 20%;
  right: 22%;
  background: radial-gradient(circle, rgb(var(--blob-5)) 0%, rgba(var(--blob-5), 0.55) 45%, transparent 70%);
  animation: drift5 18s ease-in-out infinite;
}
/* ponytail: 纯 translate 漂移(无 scale)——scale 变化会触发层重栅格化,是频闪诱因之一 */
@keyframes drift1 {
  0%,
  100% {
    transform: translate(0, 0);
  }
  33% {
    transform: translate(180px, 120px);
  }
  66% {
    transform: translate(-80px, 200px);
  }
}
@keyframes drift2 {
  0%,
  100% {
    transform: translate(0, 0);
  }
  33% {
    transform: translate(-200px, 150px);
  }
  66% {
    transform: translate(120px, -120px);
  }
}
@keyframes drift3 {
  0%,
  100% {
    transform: translate(0, 0);
  }
  50% {
    transform: translate(240px, -160px);
  }
}
@keyframes drift4 {
  0%,
  100% {
    transform: translate(0, 0);
  }
  40% {
    transform: translate(-160px, -120px);
  }
  70% {
    transform: translate(200px, 80px);
  }
}
@keyframes drift5 {
  0%,
  100% {
    transform: translate(0, 0);
  }
  50% {
    transform: translate(-120px, -180px);
  }
}

/* 亮斑图层:multiply 染色,台灯 mask 挖洞(背景光影,盖在弹窗之上) */
.bright-spot {
  position: fixed;
  inset: 0;
  z-index: var(--z-deco);
  pointer-events: none;
  mix-blend-mode: multiply;
  -webkit-mask-image: var(--lamp-mask, none);
  mask-image: var(--lamp-mask, none);
  transition:
    background 3s ease,
    opacity 3s ease;
  transform: translateZ(0);
}

/* 反光层:soft-light 高光 */
.reflection-layer {
  position: fixed;
  inset: 0;
  z-index: calc(var(--z-deco) + 4);
  pointer-events: none;
  mix-blend-mode: soft-light;
  transition: opacity 3s ease;
  transform: translateZ(0);
}

/* 台灯光源:氛围层最高(盖在弹窗之上,像灯照在纸面) */
.lamp-light {
  position: fixed;
  border-radius: 50%;
  z-index: calc(var(--z-atmos) + 8);
  pointer-events: none;
  filter: blur(20px);
}

/* 窗户阴影:6 条 bar,multiply,台灯 mask 挖洞 */
.window-shadow {
  position: fixed;
  inset: 0;
  z-index: calc(var(--z-deco) + 2);
  pointer-events: none;
  mix-blend-mode: multiply;
  opacity: var(--shadow-alpha, 0.7);
  -webkit-mask-image: var(--lamp-mask, none);
  mask-image: var(--lamp-mask, none);
}
.shadow-bar {
  position: absolute;
  filter: blur(16px);
  background: var(--shadow-color, rgb(0, 0, 0));
}
.shadow-v {
  top: -50vh;
  left: 50%;
  width: 112px;
  margin-left: -56px;
  height: 337.5vh;
  transform-origin: 50% 60vh;
  transition: transform var(--bar-transition, 3s ease);
  transform: rotate(var(--rot, 0deg));
}
.frame-v-left {
  top: -50vh;
  left: 50%;
  width: 1400px;
  margin-left: -1400px;
  height: 337.5vh;
  transform-origin: 100% 60vh;
  transition: transform var(--bar-transition, 3s ease);
  transform: translateX(-55vw) rotate(var(--rot, 0deg));
}
.frame-v-right {
  top: -50vh;
  left: 50%;
  width: 1400px;
  height: 337.5vh;
  transform-origin: 0% 60vh;
  transition: transform var(--bar-transition, 3s ease);
  transform: translateX(55vw) rotate(var(--rot, 0deg));
}
.shadow-h {
  left: -75%;
  right: -75%;
  height: 70px;
  top: var(--htop, 50%);
  transition: top var(--bar-transition, 3s ease);
}
.frame-h-top {
  left: -75%;
  right: -75%;
  height: 280px;
  top: calc(10vh - 280px + var(--frame-top-offset, 0vh));
  transition: top var(--bar-transition, 3s ease);
}
.frame-h-bottom {
  left: -75%;
  right: -75%;
  height: 1400px;
  top: calc(var(--htop, 50%) * 2 + 70px);
  transition: top var(--bar-transition, 3s ease);
}

/* 体积光层:丁达尔效应,screen(背景光影,盖在弹窗之上) */
.light-layer {
  position: fixed;
  inset: 0;
  z-index: calc(var(--z-deco) + 6);
  pointer-events: none;
  mix-blend-mode: screen;
  overflow: hidden;
  transform: translateZ(0);
}
.light-bloom {
  position: absolute;
  width: 700px;
  height: 700px;
  margin-left: -350px;
  margin-top: -350px;
  border-radius: 50%;
  filter: blur(60px);
  transition:
    background 3s ease,
    left 3s ease,
    top 3s ease;
}
.light-source {
  position: absolute;
  width: 0;
  height: 0;
  transition:
    left 3s ease,
    top 3s ease;
}
.light-ray {
  position: absolute;
  top: 0;
  left: 50%;
  height: 200vh;
  transform-origin: top center;
  transition:
    opacity 3s ease,
    filter 3s ease,
    transform 3s ease,
    background 3s ease;
}

/* 柔和暗角:氛围层,盖在弹窗之上(压边走暗,弹窗居中不受影响) */
.vignette {
  position: fixed;
  inset: 0;
  z-index: var(--z-atmos);
  pointer-events: none;
  -webkit-mask-image: var(--lamp-mask, none);
  mask-image: var(--lamp-mask, none);
  background: radial-gradient(
    ellipse 90% 75% at 50% 42%,
    transparent 0%,
    transparent 55%,
    rgba(60, 38, 12, 0.08) 80%,
    rgba(45, 25, 8, 0.18) 100%
  );
  transform: translateZ(0);
}

/* 天气覆盖阴影:与其他 shadow-bar 同色同 blur,固定全屏不移动,随天气显隐 */
.shadow-bar.weather-shadow {
  inset: 0;
  width: 100%;
  height: 100%;
  transition: opacity 2s ease;
}

/* 灰尘粒子:screen 发光,氛围层 */
.dust-layer {
  position: fixed;
  inset: 0;
  z-index: calc(var(--z-atmos) + 2);
  pointer-events: none;
  overflow: hidden;
  mix-blend-mode: screen;
  transform: translateZ(0);
}
.dust {
  position: absolute;
  border-radius: 50%;
  background: var(--light-dust, rgba(255, 238, 185, 0.85));
  box-shadow: 0 0 8px var(--light-dust-glow, rgba(255, 225, 150, 0.7));
  animation: dust-float linear infinite;
}
@keyframes dust-float {
  0% {
    transform: translate(0, 0);
    opacity: 0;
  }
  12% {
    opacity: 0.9;
  }
  28% {
    transform: translate(calc(var(--drift, 60px) * 0.5), calc(var(--drift, 60px) * 1));
    opacity: 1;
  }
  46% {
    transform: translate(calc(var(--drift, 60px) * -0.25), calc(var(--drift, 60px) * 2.5));
    opacity: 0.9;
  }
  64% {
    transform: translate(calc(var(--drift, 60px) * 0.45), calc(var(--drift, 60px) * 4));
    opacity: 0.7;
  }
  82% {
    transform: translate(calc(var(--drift, 60px) * 0.15), calc(var(--drift, 60px) * 6.3));
    opacity: 0.4;
  }
  100% {
    transform: translate(calc(var(--drift, 60px) * -0.3), calc(var(--drift, 60px) * 8.6));
    opacity: 0;
  }
}

/* 雪花层:从顶端飘到底端,六瓣雪花样式(氛围层,雪落在弹窗上) */
.snow-layer {
  position: fixed;
  inset: 0;
  z-index: calc(var(--z-atmos) + 4);
  pointer-events: none;
  overflow: hidden;
  transform: translateZ(0);
}
.snowflake {
  position: absolute;
  top: -20px;
  color: rgba(255, 255, 255, 0.9);
  text-shadow: 0 0 4px rgba(255, 255, 255, 0.6);
  line-height: 1;
  user-select: none;
  animation: snow-fall linear infinite;
}
@keyframes snow-fall {
  0% {
    transform: translate(0, 0) rotate(0deg);
    opacity: 0;
  }
  10% {
    opacity: 1;
  }
  90% {
    opacity: 1;
  }
  100% {
    transform: translate(var(--drift, 50px), 100vh) rotate(360deg);
    opacity: 0;
  }
}

/* 雨滴层:快速下落(氛围层,雨落在弹窗上) */
.rain-layer {
  position: fixed;
  inset: 0;
  z-index: calc(var(--z-atmos) + 4);
  pointer-events: none;
  overflow: hidden;
  transform: translateZ(0);
}
.raindrop {
  position: absolute;
  top: -20px;
  width: 1.5px;
  background: linear-gradient(to bottom, transparent, rgba(180, 200, 230, 0.7));
  animation: rain-fall linear infinite;
}
@keyframes rain-fall {
  0% {
    transform: translateY(0);
    opacity: 0;
  }
  10% {
    opacity: 1;
  }
  100% {
    transform: translateY(100vh);
    opacity: 0.3;
  }
}

/* 雷雨闪电:全屏瞬间增亮,screen 混合(氛围层) */
.lightning-flash {
  position: fixed;
  inset: 0;
  z-index: calc(var(--z-atmos) + 6);
  pointer-events: none;
  mix-blend-mode: screen;
  background: rgba(255, 255, 255, 0.6);
  transform: translateZ(0);
}

/* 雾霭层(背景光影最高一处):高斯噪声雾,三层不同颗粒尺寸/漂移速度叠出云絮感(减少平铺接缝感);
   整层经 Vue Transition 淡入/淡出——雾/阴进入后只出现几秒就消散,不常驻(可见时长由 useSunLight 的 FOG_VISIBLE_MS 控制) */
.fog-layer {
  position: fixed;
  inset: 0;
  z-index: calc(var(--z-deco) + 8);
  overflow: hidden;
  pointer-events: none;
  transform: translateZ(0);
  /* 贴地雾:底部浓、向上渐淡 */
  -webkit-mask-image: linear-gradient(to top, #000 0%, rgba(0, 0, 0, 0.75) 40%, rgba(0, 0, 0, 0.3) 72%, transparent 94%);
  mask-image: linear-gradient(to top, #000 0%, rgba(0, 0, 0, 0.75) 40%, rgba(0, 0, 0, 0.3) 72%, transparent 94%);
}
.fog-noise {
  position: absolute;
  inset: -12% -8%;
  background-repeat: repeat;
  will-change: transform;
  animation: fog-drift 24s ease-in-out infinite alternate;
}
@keyframes fog-drift {
  from {
    transform: translate3d(0, 0, 0) scale(1);
  }
  to {
    transform: translate3d(var(--dx, 4vw), var(--dy, 2vh), 0) scale(1.08);
  }
}
/* 进入快速浮现,消散缓慢淡出并轻微上浮,像雾被风带走 */
.fog-enter-active {
  transition: opacity 0.6s ease;
}
.fog-leave-active {
  transition:
    opacity 2.2s ease,
    transform 2.2s ease;
}
.fog-enter-from,
.fog-leave-to {
  opacity: 0;
}
.fog-leave-to {
  transform: translateY(-2vh);
}

/* 台灯钟摆运动:CSS @keyframes 驱动,不经过 Vue 响应式 */
.lamp-light-pendulum {
  left: 38.2%;
  transform: translate(-50%, -50%);
  animation: lampSwing 8s ease-in-out infinite;
}
@keyframes lampSwing {
  0%,
  100% {
    transform: translate(calc(-50% - 1.5vw), -50%) scaleX(0.97);
  }
  50% {
    transform: translate(calc(-50% + 1.5vw), -50%) scaleX(1.03);
  }
}
</style>
