<!-- 全局光影层:体积光 + 窗框阴影 + 台灯 + 灰尘 + 背景色块 -->
<!-- 所有页面共享,通过 useSunLight 组合式函数驱动 -->
<!-- z-index 分层(从底到顶):bg-blobs(1)→内容(10)→AppSidebar(60)→bright-spot(65)→window-shadow(68)→reflection(72)→vignette(74)→dust(76)→light-layer(78)→lamp-light(100) -->
<!-- 光影层在导航栏之上,覆盖全页(含导航栏),所有光影效果在所有元素之上生效 -->
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

    <!-- 亮斑图层:multiply 染色,台灯 mask 挖洞 -->
    <div v-if="(brightSpotStyle?.opacity ?? 0) > 0.01" class="bright-spot" :style="{ ...brightSpotStyle, '--lamp-mask': lampMask }"></div>

    <!-- 窗户阴影:6 条 bar + 天气覆盖阴影,multiply,台灯 mask 挖洞(阴影开关可关闭) -->
    <div v-if="shadowEnabled" class="window-shadow"
         :style="{ '--rot': (sunScene.shadowVRotation || 0) + 'deg', '--htop': (sunScene.shadowHTop || 50) + '%', '--shadow-alpha': (sunScene.shadowIntensity ?? 0.5), '--shadow-color': (sunScene.shadowColor || 'rgb(0,0,0)'), '--bar-transition': sunScene.isNight ? '0s' : '3s ease', '--frame-top-offset': (sunScene.frameTopOffset ?? 0) + 'vh', '--lamp-mask': lampMask }">
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
    <div v-if="(lightLayerOpacity ?? 0) > 0.01" class="light-layer" :style="{ opacity: lightLayerOpacity ?? 0, transition: sunScene.isNight ? 'none' : 'opacity 3s ease' }">
      <div class="light-bloom" :style="bloomStyle"></div>
      <div class="light-source" :style="sourceStyle">
        <div
          v-for="(rs, i) in rayStyles"
          :key="i"
          class="light-ray"
          :style="rs"
        ></div>
      </div>
    </div>

    <!-- 台灯光源:左上黄金分割点 + 钟摆运动,最顶层(opacity 为 0 时移除) -->
    <div v-if="(lampDivOpacity ?? 0) > 0.01" class="lamp-light lamp-light-pendulum" :style="{
      opacity: lampDivOpacity,
      top: '38.2%',
      width: (lampRadius * 2) + 'vw',
      height: (lampRadius * 2) + 'vw',
      background: 'radial-gradient(circle, rgba(' + lampColor + ',0.6) 0%, rgba(' + lampColor + ',0.45) 15%, rgba(' + lampColor + ',0.3) 35%, rgba(' + lampColor + ',0.18) 55%, rgba(' + lampColor + ',0.08) 75%, transparent 95%)'
    }"></div>

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
      >❄</div>
    </div>

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
  sunScene, lampMask, lampDivOpacity, lampRadius, lampColor, shadowEnabled, weatherShadowOpacity, lightLayerOpacity, blobsEnabled,
  dustParticles, snowParticles, rainParticles, rayStyles, sourceStyle, bloomStyle, brightSpotStyle, reflectionStyle,
} = light || {}
</script>

<style scoped>
.sun-light-layer {
  pointer-events: none;
  /* 不设 position/inset/z-index:子元素都是 fixed,各自在 root stacking context 中 */
  /* 这样 z-index 35/48/100 等与页面内容(z=10)在同一 context 比较,和以前 Home.vue 一样 */
}

/* 背景色块:清新淡雅,高斯模糊,随机飘动(fixed 固定不随页面滚动,避免 backdrop-filter 元素每帧重算) */
.bg-blobs {
  position: fixed;
  inset: 0;
  z-index: 1;
  overflow: hidden;
  pointer-events: none;
  /* 隔离为独立合成层:drift 动画的 transform 变化不触发 backdrop-filter 元素重算 */
  transform: translateZ(0);
  will-change: transform;
}
.blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.25;
  will-change: transform;
  transition: opacity 1s ease, box-shadow 1s ease;
}
html.dark .blob { opacity: 0.08; }
html.dark .blob-1 { box-shadow: 0 0 120px 40px rgba(120,200,160,0.4); }
html.dark .blob-2 { box-shadow: 0 0 120px 40px rgba(200,180,100,0.4); }
html.dark .blob-3 { box-shadow: 0 0 120px 40px rgba(200,160,140,0.4); }
html.dark .blob-4 { box-shadow: 0 0 120px 40px rgba(120,160,200,0.4); }
html.dark .blob-5 { box-shadow: 0 0 120px 40px rgba(160,180,120,0.4); }
.blob-1 { width: 480px; height: 480px; top: -120px; left: -100px; background: #9CD0B5; animation: drift1 22s ease-in-out infinite; }
.blob-2 { width: 560px; height: 560px; top: 25%; right: -180px; background: #EDDB8C; animation: drift2 26s ease-in-out infinite; }
.blob-3 { width: 420px; height: 420px; bottom: -120px; left: 18%; background: #ECC0AC; animation: drift3 20s ease-in-out infinite; }
.blob-4 { width: 380px; height: 380px; top: 35%; left: 32%; background: #A8C9DE; animation: drift4 24s ease-in-out infinite; }
.blob-5 { width: 300px; height: 300px; bottom: 20%; right: 22%; background: #C0D8A8; animation: drift5 18s ease-in-out infinite; }
@keyframes drift1 { 0%,100%{transform:translate(0,0) scale(1);} 33%{transform:translate(180px,120px) scale(1.3);} 66%{transform:translate(-80px,200px) scale(0.85);} }
@keyframes drift2 { 0%,100%{transform:translate(0,0) scale(1);} 33%{transform:translate(-200px,150px) scale(1.25);} 66%{transform:translate(120px,-120px) scale(0.8);} }
@keyframes drift3 { 0%,100%{transform:translate(0,0) scale(1);} 50%{transform:translate(240px,-160px) scale(1.35);} }
@keyframes drift4 { 0%,100%{transform:translate(0,0) scale(1);} 40%{transform:translate(-160px,-120px) scale(1.28);} 70%{transform:translate(200px,80px) scale(0.82);} }
@keyframes drift5 { 0%,100%{transform:translate(0,0) scale(1);} 50%{transform:translate(-120px,-180px) scale(1.3);} }

/* 亮斑图层:multiply 染色,台灯 mask 挖洞 */
.bright-spot {
  position: fixed; inset: 0; z-index: 65; pointer-events: none;
  mix-blend-mode: multiply;
  -webkit-mask-image: var(--lamp-mask, none);
  mask-image: var(--lamp-mask, none);
  transition: background 3s ease, opacity 3s ease;
  transform: translateZ(0);
}

/* 反光层:soft-light 高光 */
.reflection-layer {
  position: fixed; inset: 0; z-index: 72; pointer-events: none;
  mix-blend-mode: soft-light;
  transition: opacity 3s ease;
  transform: translateZ(0);
}

/* 台灯光源:最顶层 */
.lamp-light {
  position: fixed; border-radius: 50%; z-index: 100; pointer-events: none;
  filter: blur(20px);
}

/* 窗户阴影:6 条 bar,multiply,台灯 mask 挖洞 */
.window-shadow {
  position: fixed; inset: 0; z-index: 68; pointer-events: none;
  mix-blend-mode: multiply;
  opacity: var(--shadow-alpha, 0.7);
  -webkit-mask-image: var(--lamp-mask, none);
  mask-image: var(--lamp-mask, none);
}
.shadow-bar {
  position: absolute; filter: blur(16px);
  background: var(--shadow-color, rgb(0,0,0));
}
.shadow-v {
  top: -50vh; left: 50%; width: 112px; margin-left: -56px;
  height: 337.5vh; transform-origin: 50% 60vh;
  transition: transform var(--bar-transition, 3s ease);
  transform: rotate(var(--rot, 0deg));
}
.frame-v-left {
  top: -50vh; left: 50%; width: 1400px; margin-left: -1400px;
  height: 337.5vh; transform-origin: 100% 60vh;
  transition: transform var(--bar-transition, 3s ease);
  transform: translateX(-55vw) rotate(var(--rot, 0deg));
}
.frame-v-right {
  top: -50vh; left: 50%; width: 1400px;
  height: 337.5vh; transform-origin: 0% 60vh;
  transition: transform var(--bar-transition, 3s ease);
  transform: translateX(55vw) rotate(var(--rot, 0deg));
}
.shadow-h {
  left: -75%; right: -75%; height: 70px;
  top: var(--htop, 50%);
  transition: top var(--bar-transition, 3s ease);
}
.frame-h-top {
  left: -75%; right: -75%; height: 280px;
  top: calc(10vh - 280px + var(--frame-top-offset, 0vh));
  transition: top var(--bar-transition, 3s ease);
}
.frame-h-bottom {
  left: -75%; right: -75%; height: 1400px;
  top: calc(var(--htop, 50%) * 2 + 70px);
  transition: top var(--bar-transition, 3s ease);
}

/* 体积光层:丁达尔效应,screen */
.light-layer {
  position: fixed; inset: 0; z-index: 78; pointer-events: none;
  mix-blend-mode: screen; overflow: hidden;
  transform: translateZ(0);
}
.light-bloom {
  position: absolute; width: 700px; height: 700px;
  margin-left: -350px; margin-top: -350px;
  border-radius: 50%; filter: blur(60px);
  transition: background 3s ease, left 3s ease, top 3s ease;
}
.light-source {
  position: absolute; width: 0; height: 0;
  transition: left 3s ease, top 3s ease;
}
.light-ray {
  position: absolute; top: 0; left: 50%; height: 200vh;
  transform-origin: top center;
  transition: opacity 3s ease, filter 3s ease, transform 3s ease, background 3s ease;
}

/* 柔和暗角 */
.vignette {
  position: fixed; inset: 0; z-index: 74; pointer-events: none;
  -webkit-mask-image: var(--lamp-mask, none);
  mask-image: var(--lamp-mask, none);
  background: radial-gradient(ellipse 90% 75% at 50% 42%, transparent 0%, transparent 55%, rgba(60,38,12,0.08) 80%, rgba(45,25,8,0.18) 100%);
  transform: translateZ(0);
}

/* 天气覆盖阴影:与其他 shadow-bar 同色同 blur,固定全屏不移动,随天气显隐 */
.shadow-bar.weather-shadow {
  inset: 0;
  width: 100%; height: 100%;
  transition: opacity 2s ease;
}

/* 灰尘粒子:screen 发光 */
.dust-layer {
  position: fixed; inset: 0; z-index: 76; pointer-events: none;
  overflow: hidden; mix-blend-mode: screen;
  transform: translateZ(0);
}
.dust {
  position: absolute; border-radius: 50%;
  background: rgba(255,238,185,0.85);
  box-shadow: 0 0 8px rgba(255,225,150,0.7);
  animation: dust-float linear infinite;
}
@keyframes dust-float {
  0% { transform: translate(0,0); opacity: 0; }
  15% { opacity: 0.9; }
  85% { opacity: 0.9; }
  100% { transform: translate(var(--drift, 60px), calc(var(--drift, 60px) * -1.5)); opacity: 0; }
}

/* 雪花层:从顶端飘到底端,六瓣雪花样式 */
.snow-layer {
  position: fixed; inset: 0; z-index: 77; pointer-events: none;
  overflow: hidden;
  transform: translateZ(0);
}
.snowflake {
  position: absolute; top: -20px;
  color: rgba(255, 255, 255, 0.9);
  text-shadow: 0 0 4px rgba(255, 255, 255, 0.6);
  line-height: 1;
  user-select: none;
  animation: snow-fall linear infinite;
}
@keyframes snow-fall {
  0% { transform: translate(0, 0) rotate(0deg); opacity: 0; }
  10% { opacity: 1; }
  90% { opacity: 1; }
  100% { transform: translate(var(--drift, 50px), 100vh) rotate(360deg); opacity: 0; }
}

/* 雨滴层:快速下落 */
.rain-layer {
  position: fixed; inset: 0; z-index: 77; pointer-events: none;
  overflow: hidden;
  transform: translateZ(0);
}
.raindrop {
  position: absolute; top: -20px; width: 1.5px;
  background: linear-gradient(to bottom, transparent, rgba(180, 200, 230, 0.7));
  animation: rain-fall linear infinite;
}
@keyframes rain-fall {
  0% { transform: translateY(0); opacity: 0; }
  10% { opacity: 1; }
  100% { transform: translateY(100vh); opacity: 0.3; }
}
</style>
