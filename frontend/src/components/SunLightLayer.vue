<!-- 全局光影层:体积光 + 窗框阴影 + 台灯 + 灰尘 + 背景色块 -->
<!-- 所有页面共享,通过 useSunLight 组合式函数驱动 -->
<!-- z-index 分层(从底到顶):bg-blobs(1)→内容(10)→AppSidebar(60)→bright-spot(65)→window-shadow(68)→reflection(72)→vignette(74)→dust(76)→light-layer(78)→lamp-light(100) -->
<!-- 光影层在导航栏之上,覆盖全页(含导航栏),所有光影效果在所有元素之上生效 -->
<template>
  <div class="sun-light-layer" aria-hidden="true">
    <!-- 背景色块:5 个 blur 色块随机飘动 -->
    <div class="bg-blobs">
      <div class="blob blob-1"></div>
      <div class="blob blob-2"></div>
      <div class="blob blob-3"></div>
      <div class="blob blob-4"></div>
      <div class="blob blob-5"></div>
    </div>

    <!-- 亮斑图层:multiply 染色,台灯 mask 挖洞 -->
    <div class="bright-spot" :style="{ ...brightSpotStyle, '--lamp-mask': lampMask }"></div>

    <!-- 窗户阴影:6 条 bar,multiply,台灯 mask 挖洞 -->
    <div class="window-shadow"
         :style="{ '--rot': (sunScene.shadowVRotation || 0) + 'deg', '--htop': (sunScene.shadowHTop || 50) + '%', '--shadow-alpha': (sunScene.shadowIntensity ?? 0.5), '--shadow-color': (sunScene.shadowColor || 'rgb(0,0,0)'), '--bar-transition': sunScene.isNight ? '0s' : '3s ease', '--frame-top-offset': (sunScene.frameTopOffset ?? 0) + 'vh', '--lamp-mask': lampMask }">
      <div class="shadow-bar frame-h-top"></div>
      <div class="shadow-bar frame-h-bottom"></div>
      <div class="shadow-bar shadow-v"></div>
      <div class="shadow-bar shadow-h"></div>
      <div class="shadow-bar frame-v-left"></div>
      <div class="shadow-bar frame-v-right"></div>
    </div>

    <!-- 反光层:soft-light 高光 -->
    <div class="reflection-layer" :style="reflectionStyle"></div>

    <!-- 柔和暗角:边缘微压暗,台灯 mask 挖洞 -->
    <div class="vignette" :style="{ '--lamp-mask': lampMask }"></div>

    <!-- 体积光:丁达尔效应,screen,光源在页面外上方 -->
    <div class="light-layer" :style="{ opacity: sunScene.lightOpacity ?? 0, transition: sunScene.isNight ? 'none' : 'opacity 3s ease' }">
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

    <!-- 台灯光源:左上黄金分割点 + 钟摆运动,最顶层 -->
    <div class="lamp-light" :style="{
      opacity: lampDivOpacity,
      left: 'calc(38.2% + ' + lampPendulumX + 'vw)',
      top: '38.2%',
      transform: 'translate(-50%, -50%) scaleX(' + lampPendulumScaleX + ')',
      width: (lampRadius * 2) + 'vw',
      height: (lampRadius * 2) + 'vw',
      background: 'radial-gradient(circle, rgba(' + lampColor + ',0.6) 0%, rgba(' + lampColor + ',0.45) 15%, rgba(' + lampColor + ',0.3) 35%, rgba(' + lampColor + ',0.18) 55%, rgba(' + lampColor + ',0.08) 75%, transparent 95%)'
    }"></div>

    <!-- 灰尘粒子:光路中的飘浮微粒 -->
    <div class="dust-layer" :style="{ opacity: sunScene.lightOpacity ?? 0 }">
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
  sunScene, lampMask, lampDivOpacity, lampRadius, lampColor,
  lampPendulumX, lampPendulumScaleX,
  dustParticles, rayStyles, sourceStyle, bloomStyle, brightSpotStyle, reflectionStyle,
} = light || {}
</script>

<style scoped>
.sun-light-layer {
  pointer-events: none;
  /* 不设 position/inset/z-index:子元素都是 fixed,各自在 root stacking context 中 */
  /* 这样 z-index 35/48/100 等与页面内容(z=10)在同一 context 比较,和以前 Home.vue 一样 */
}

/* 背景色块:清新淡雅,高斯模糊,随机飘动 */
.bg-blobs {
  position: absolute;
  inset: 0;
  z-index: 1;
  overflow: hidden;
  pointer-events: none;
}
.blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.4;
  will-change: transform;
  transition: opacity 1s ease, box-shadow 1s ease;
}
html.dark .blob { opacity: 0.1; }
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
}

/* 反光层:soft-light 高光 */
.reflection-layer {
  position: fixed; inset: 0; z-index: 72; pointer-events: none;
  mix-blend-mode: soft-light;
  transition: opacity 3s ease;
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
  left: -75%; right: -75%; height: 140px;
  top: calc(10vh - 140px + var(--frame-top-offset, 0vh));
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
}

/* 灰尘粒子:screen 发光 */
.dust-layer {
  position: fixed; inset: 0; z-index: 76; pointer-events: none;
  overflow: hidden; mix-blend-mode: screen;
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
</style>
