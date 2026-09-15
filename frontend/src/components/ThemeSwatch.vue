<!-- 主题切换圆形色块:每个主题一个圆,底色在晨/暮两色间 2 秒交叉渐变;hover 放大并展示主题特色动效 -->
<template>
  <div class="ts-row">
    <button
      v-for="s in swatches"
      :key="s.id"
      type="button"
      class="ts-item"
      :class="{ 'is-active': themeStore.theme === s.id }"
      :style="{ '--sw-a': s.dawn, '--sw-b': s.dusk }"
      :title="s.label"
      @click="themeStore.setTheme(s.id)"
    >
      <span class="ts-swatch">
        <span class="ts-dot" :class="`ts-${s.id}`">
          <!-- 光尘:迷你丁达尔体积光(光晕+自右上射向左下的光柱)+ 主题同款浮尘 -->
          <template v-if="s.id === 'guangchen'">
            <span class="ts-bloom"></span>
            <span class="ts-ray"></span>
            <span v-for="n in 5" :key="n" class="ts-dust" :class="`d${n}`"></span>
          </template>
          <!-- 暖居:呼吸暖光 + 会呼吸的窗 + 金色尘粒 -->
          <template v-else>
            <span class="ts-glow"></span>
            <span class="ts-window"></span>
            <span v-for="n in 3" :key="n" class="ts-ember" :class="`e${n}`"></span>
          </template>
        </span>
        <span v-if="themeStore.theme === s.id" class="ts-check">✓</span>
      </span>
      <span class="ts-label">{{ s.label }}</span>
    </button>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useThemeStore } from '@/stores/theme'
import { useI18n } from 'vue-i18n'

const themeStore = useThemeStore()
const { t } = useI18n()

// 两个主题的圆形色块:晨/暮底色取自 theme/index.js 的 meta,由 --sw-a/--sw-b 变量驱动 CSS 渐变
const swatches = computed(() =>
  themeStore.themes.map((th) => ({
    id: th.id,
    label: t(`theme.${th.id}`),
    dawn: th.meta.dawn,
    dusk: th.meta.dusk,
  }))
)
</script>

<style scoped>
.ts-row { display: flex; gap: 22px; }

/* 色块 + 选中对勾的定位容器:对勾脱出 overflow:hidden 的 .ts-dot,叠加在圆上层不被裁切 */
.ts-swatch { position: relative; display: inline-flex; }

.ts-item {
  display: inline-flex; flex-direction: column; align-items: center; gap: 9px;
  border: none; background: none; padding: 0; cursor: pointer;
}
.ts-item:focus-visible { outline: 2px solid var(--color-brand); outline-offset: 3px; border-radius: 14px; }

/* 圆色块:底色 2 秒在晨/暮两色间交叉渐变,悬停弹性放大 */
.ts-dot {
  position: relative;
  width: 46px; height: 46px; border-radius: 50%;
  background-color: var(--sw-a);
  animation: ts-bg-swap 6s ease-in-out infinite;
  box-shadow: inset 0 0 0 1px rgba(127, 110, 90, 0.18);
  transition: transform 0.32s cubic-bezier(0.34, 1.56, 0.64, 1), box-shadow 0.3s ease;
  overflow: hidden;
  flex-shrink: 0;
}
@keyframes ts-bg-swap {
  0%, 33.333% { background-color: var(--sw-a); }
  50%, 83.333% { background-color: var(--sw-b); }
  100% { background-color: var(--sw-a); }
}
.ts-item:hover .ts-dot {
  transform: scale(1.5);
  z-index: 3;
  box-shadow: inset 0 0 0 1px rgba(127, 110, 90, 0.18), 0 10px 26px rgba(0, 0, 0, 0.22);
}
.ts-item.is-active .ts-dot {
  box-shadow: inset 0 0 0 1px rgba(127, 110, 90, 0.18), 0 0 0 2px var(--color-brand);
}
.ts-item.is-active:hover .ts-dot {
  box-shadow: inset 0 0 0 1px rgba(127, 110, 90, 0.18), 0 0 0 2px var(--color-brand), 0 10px 26px rgba(0, 0, 0, 0.22);
}

.ts-label { font-size: 13px; font-weight: 500; color: var(--color-text); transition: color 0.2s; }
.ts-item.is-active .ts-label { color: var(--color-brand); font-weight: 650; }

/* 选中对勾角标 */
.ts-check {
  position: absolute; right: -2px; bottom: -2px;
  width: 16px; height: 16px; border-radius: 50%;
  background: var(--color-brand); color: var(--color-card);
  font-size: 10px; line-height: 16px; text-align: center; font-weight: 700;
  z-index: 6;
}

/* ===== 光尘:迷你丁达尔体积光(光晕+自右上射向左下的光柱,screen)+ 主题同款浮尘 ===== */
/* 光晕:光源处暖金 bloom(对应全局 .light-bloom),坐落在右上光源点 */
.ts-bloom {
  position: absolute; left: 72%; top: 8%;
  width: 24px; height: 24px; margin-left: -12px; margin-top: -12px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 214, 150, 0.95) 0%, rgba(255, 188, 110, 0.4) 45%, transparent 72%);
  filter: blur(3px);
  mix-blend-mode: screen;
  opacity: 0;
}
/* 光柱:单条羽毛状光束,自右上斜射向左下(对应全局 .light-ray) */
.ts-ray {
  position: absolute; left: 72%; top: 8%;
  width: 14px; height: 128%;
  margin-left: -7px;
  transform-origin: top center;
  transform: rotate(31deg);
  background: linear-gradient(to bottom, rgba(255, 218, 158, 0.95) 0%, rgba(255, 188, 108, 0.5) 40%, transparent 78%);
  filter: blur(2.5px);
  mix-blend-mode: screen;
  opacity: 0;
}

/* 变化:与全局体积光一致——缓慢呼吸明暗 + 光柱轻微摆动(模拟太阳方位角漂移 + 10s 微闪) */
.ts-item:hover .ts-bloom { animation: ts-bloom-breathe 5s ease-in-out infinite; }
.ts-item:hover .ts-ray { animation: ts-ray-breathe 5s ease-in-out infinite; }
@keyframes ts-bloom-breathe {
  0%, 100% { opacity: 0.5; }
  50% { opacity: 0.95; }
}
@keyframes ts-ray-breathe {
  0%, 100% { opacity: 0.55; transform: rotate(34deg); }
  50% { opacity: 0.95; transform: rotate(28deg); }
}

/* 浮尘:主题同款(screen + --light-dust 发光;缓起缓落 + 横向轻摆,每颗时长/相位错开,对应全局 .dust) */
.ts-dust {
  position: absolute; border-radius: 50%;
  background: var(--light-dust, rgba(255, 238, 185, 0.85));
  box-shadow: 0 0 8px var(--light-dust-glow, rgba(255, 225, 150, 0.7));
  mix-blend-mode: screen;
  opacity: 0;
}
.ts-dust.d1 { left: 30%; top: 26%; width: 3px; height: 3px; --drift: 6px; --dur: 7s; --delay: 0s; }
.ts-dust.d2 { left: 64%; top: 20%; width: 2px; height: 2px; --drift: 8px; --dur: 5.5s; --delay: 1.2s; }
.ts-dust.d3 { left: 44%; top: 54%; width: 3px; height: 3px; --drift: 7px; --dur: 7.5s; --delay: 2.4s; }
.ts-dust.d4 { left: 72%; top: 48%; width: 2px; height: 2px; --drift: 9px; --dur: 6.5s; --delay: 3.2s; }
.ts-dust.d5 { left: 22%; top: 64%; width: 2.5px; height: 2.5px; --drift: 6px; --dur: 8s; --delay: 1.6s; }
.ts-item:hover .ts-dust { animation: ts-dust-float var(--dur, 6s) ease-in-out var(--delay, 0s) infinite; }
@keyframes ts-dust-float {
  0%   { transform: translate(0, 0); opacity: 0; }
  10%  { opacity: 0.85; }
  30%  { transform: translate(calc(var(--drift) * 0.6), calc(var(--drift) * 1.2)); opacity: 1; }
  55%  { transform: translate(calc(var(--drift) * -0.5), calc(var(--drift) * 3)); opacity: 0.8; }
  80%  { transform: translate(calc(var(--drift) * 0.4), calc(var(--drift) * 5)); opacity: 0.5; }
  100% { transform: translate(calc(var(--drift) * -0.3), calc(var(--drift) * 7.5)); opacity: 0; }
}

/* ===== 暖居:呼吸暖光 + 会呼吸的窗 + 金色尘粒 ===== */
.ts-glow {
  position: absolute; inset: -25%;
  background: radial-gradient(circle at 50% 72%, rgba(245, 185, 113, 0.95) 0%, rgba(232, 163, 75, 0.4) 40%, transparent 68%);
  opacity: 0;
}
.ts-item:hover .ts-glow { animation: ts-breathe 3s ease-in-out infinite; }
@keyframes ts-breathe {
  0%, 100% { opacity: 0.5; transform: scale(1); }
  50% { opacity: 0.95; transform: scale(1.18); }
}

.ts-window {
  position: absolute; left: 50%; top: 50%;
  width: 58%; height: 46%;
  transform: translate(-50%, -50%) rotate(-5deg);
  border: 1.5px solid rgba(120, 84, 54, 0.5);
  border-radius: 5px;
  background: linear-gradient(160deg, rgba(255, 236, 200, 0.35), rgba(255, 200, 140, 0.1));
  opacity: 0;
}
.ts-window::before {
  content: ''; position: absolute; left: 50%; top: 0; bottom: 0; width: 1.5px;
  background: rgba(120, 84, 54, 0.4); transform: translateX(-50%);
}
.ts-window::after {
  content: ''; position: absolute; left: 0; right: 0; top: 50%; height: 1.5px;
  background: rgba(120, 84, 54, 0.4); transform: translateY(-50%);
}
.ts-item:hover .ts-window { opacity: 1; animation: ts-window-breathe 3s ease-in-out infinite; }
@keyframes ts-window-breathe {
  0%, 100% { transform: translate(-50%, -50%) rotate(-5deg) scale(1); }
  50% { transform: translate(-50%, -50%) rotate(2deg) scale(1.06); }
}

.ts-ember {
  position: absolute; width: 3px; height: 3px; border-radius: 50%;
  background: #ffd9a0; box-shadow: 0 0 5px 1px rgba(255, 200, 130, 0.9);
  opacity: 0;
}
.ts-ember.e1 { left: 30%; top: 70%; animation-delay: 0s; }
.ts-ember.e2 { left: 55%; top: 78%; animation-delay: 1.1s; }
.ts-ember.e3 { left: 68%; top: 60%; animation-delay: 2.2s; }
.ts-item:hover .ts-ember { animation: ts-ember-rise 3.5s ease-in-out infinite; }
@keyframes ts-ember-rise {
  0% { transform: translate(0, 0); opacity: 0; }
  30% { opacity: 0.9; }
  100% { transform: translate(2px, -14px); opacity: 0; }
}
</style>
