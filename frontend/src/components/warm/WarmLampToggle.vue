<!--
  暖居顶栏的台灯开关,紧贴在晨暮开关左侧。
  圆形灯泡从晨暮开关的盘面后面探出:开灯露 75% 并散发羽化光晕,关灯只露 25% 且不发光;
  悬停(关灯态)探出 50%,按下向右推进盘面,松手弹回并向左探出。
  亮度/开关状态与全局光影系统同一份(utils/useSunLight):auto 模式夜间亮、白天灭,人走灯灭也在内。
-->
<template>
  <button
    type="button"
    class="warm-lamp"
    :class="lit ? 'is-on' : 'is-off'"
    role="switch"
    :aria-checked="lit"
    :aria-label="$t('theme.lamp')"
    :title="lit ? $t('theme.lampOff') : $t('theme.lampOn')"
    @click="toggle"
  >
    <span class="warm-lamp-viewport" aria-hidden="true">
      <span class="warm-lamp-stage">
        <span class="warm-lamp-halo"></span>
        <span class="warm-lamp-bulb"></span>
      </span>
    </span>
  </button>
</template>

<script setup>
import { computed, inject } from 'vue'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'

const sunLight = inject(SUN_LIGHT_KEY, null)

// 判定口径取「灯实际有多亮」而不是 lampMode:auto 模式白天本就该是灭的,按模式摆姿态会自相矛盾
const lit = computed(() => Number(sunLight?.lampStrength?.value ?? 0) > 0.05)

const toggle = () => {
  if (!sunLight?.lampMode) return
  sunLight.lampMode.value = lit.value ? 'off' : 'on'
}
</script>

<style>
/* 只服务暖居顶栏,规则统一挂 html.theme-warm,不与光尘/窗边的按钮混色 */
html.theme-warm .warm-lamp {
  --lamp-bulb: 24px;
  --lamp-peek: 0.25; /* 灯泡露出比例:随开关状态覆写 */
  --lamp-press: 0px; /* 按住时的右移量(推进盘面) */
  position: relative;
  flex: none;
  width: 48px;
  height: 50px;
  padding: 0;
  border: 0;
  background: transparent;
  font: inherit;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
html.theme-warm .warm-lamp:focus-visible {
  border-radius: 12px;
  outline: 2px solid var(--color-brand);
  outline-offset: 2px;
}
/* 裁切口必须钉在按钮坐标系里(不随灯泡移动),所以单开一层不动的视口:
   右缘就是晨暮盘面的左缘,只裁右侧(等同被盘面挡住),其余三向放开给光晕。
   暮色晨暮开关的正面盘面在开关盒里右移 8px(WarmModeToggle 的 .warm-mode-dusk margin-left),
   视口跟着右移同一距离,裁切口才始终贴着盘面左缘 */
html.theme-warm .warm-lamp-viewport {
  position: absolute;
  inset: 0;
  clip-path: inset(-40px 0 -40px -40px);
  pointer-events: none;
}
html.theme-warm.dark .warm-lamp-viewport {
  right: -8px;
}
/* 灯泡舞台在视口里左右滑动:露出比例由 --lamp-peek 控制,按住时再向右推进盘面。
   回弹用带回弹的缓动曲线,松手后向左探出时会轻微过冲再落位 */
html.theme-warm .warm-lamp-stage {
  position: absolute;
  top: 50%;
  right: 0;
  width: var(--lamp-bulb);
  height: var(--lamp-bulb);
  transform: translate(calc((1 - var(--lamp-peek)) * var(--lamp-bulb) + var(--lamp-press)), -50%);
  transition: transform 320ms cubic-bezier(0.34, 1.56, 0.64, 1);
}
html.theme-warm .warm-lamp.is-on {
  --lamp-peek: 0.75;
}
html.theme-warm .warm-lamp.is-off {
  --lamp-peek: 0.25;
}
html.theme-warm .warm-lamp.is-off:hover,
html.theme-warm .warm-lamp.is-off:focus-visible {
  --lamp-peek: 0.5;
}
html.theme-warm .warm-lamp:active {
  --lamp-press: 7px;
}
/* 按下要跟手:短促直出;松手回到 --lamp-press:0 后由 320ms 回弹曲线接管 */
html.theme-warm .warm-lamp:active .warm-lamp-stage {
  transition-duration: 90ms;
  transition-timing-function: ease-out;
}

/* 羽化光晕:中心亮、外圈断崖式渐隐,叠一层呼吸 */
html.theme-warm .warm-lamp-halo {
  position: absolute;
  inset: -24px;
  border-radius: 50%;
  background: radial-gradient(
    circle,
    rgba(255, 236, 196, 0.5) 0%,
    rgba(255, 216, 156, 0.28) 30%,
    rgba(255, 199, 128, 0.12) 48%,
    rgba(255, 190, 120, 0.04) 62%,
    transparent 74%
  );
  opacity: 0;
  transform: scale(0.72);
  transition:
    opacity 460ms ease,
    transform 460ms cubic-bezier(0.34, 1.4, 0.64, 1);
}
html.theme-warm .warm-lamp.is-on .warm-lamp-halo {
  opacity: 1;
  transform: scale(1);
  animation: warmLampGlow 6s ease-in-out infinite;
}
@keyframes warmLampGlow {
  0%,
  100% {
    transform: scale(1);
    opacity: 1;
  }
  50% {
    transform: scale(1.06);
    opacity: 0.82;
  }
}

/* 灯泡本体:磨砂玻璃球,左上高光 + 右下暖反光,熄灭时是一颗没通电的素色球 */
html.theme-warm .warm-lamp-bulb {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background:
    radial-gradient(circle at 34% 26%, #fffdf7 0%, rgba(255, 248, 230, 0.92) 14%, rgba(255, 234, 194, 0.42) 32%, transparent 50%),
    radial-gradient(circle at 66% 78%, rgba(255, 213, 148, 0.85), transparent 58%),
    radial-gradient(circle at 50% 50%, #ffe4b0 0%, #f8ca83 58%, #dda260 100%);
  box-shadow:
    0 0 12px rgba(255, 198, 118, 0.6),
    0 2px 5px rgba(96, 62, 24, 0.3),
    inset 0 -2px 5px rgba(148, 96, 36, 0.28);
  transition:
    filter 420ms ease,
    box-shadow 420ms ease;
}
html.theme-warm .warm-lamp.is-off .warm-lamp-bulb {
  background:
    radial-gradient(circle at 34% 26%, #ece3d5 0%, rgba(226, 216, 200, 0.86) 16%, transparent 44%),
    radial-gradient(circle at 50% 50%, #bdb09c 0%, #a0937e 60%, #837663 100%);
  box-shadow:
    0 1px 3px rgba(46, 32, 23, 0.32),
    inset 0 1px 2px rgba(255, 255, 255, 0.22),
    inset 0 -2px 5px rgba(46, 32, 23, 0.34);
  filter: saturate(0.55);
}
/* 暮色底更深,没通电的球压成深木色才不抢眼 */
html.theme-warm.dark .warm-lamp.is-off .warm-lamp-bulb {
  background:
    radial-gradient(circle at 34% 26%, #6b5a46 0%, rgba(74, 60, 45, 0.85) 16%, transparent 44%),
    radial-gradient(circle at 50% 50%, #5c4b3a 0%, #4a3b2d 60%, #3a2e23 100%);
}

@media (prefers-reduced-motion: reduce) {
  html.theme-warm .warm-lamp-stage {
    transition-duration: 1ms;
  }
  html.theme-warm .warm-lamp.is-on .warm-lamp-halo {
    animation: none;
  }
}
</style>
