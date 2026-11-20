<!-- Based on the Chromium offline page easter egg (BSD-3-Clause, see LICENSE), localized as a Vue component.
     小恐龙快跑:引擎(跳跃/下蹲/障碍/夜间模式/音效)逐字保留在 utils/games/trexRunner.js,
     本组件只负责外壳、画布容器、按外壳游戏区等比放大,以及卸载时收干净定时器与监听。
     规范见 docs/UI设计提示词.md §43;精灵图与音效走 src/assets ESM 导入(不再读 public/)。 -->
<template>
  <GameShell
    ref="shellEl"
    class="tx-shell"
    :title="$t('games.embedded.trex.title')"
    body-align="center"
    sound
    @back="router.push('/games')"
  >
    <template #rules>
      <p>{{ $t('games.embedded.trex.tip') }}</p>
    </template>

    <div class="tx-wrap" @contextmenu.prevent>
      <!-- 引擎把 .runner-container + canvas 追加进这个节点;缩放只做 transform,不影响它量宽 -->
      <div ref="areaEl" class="tx-area">
        <div ref="stageEl" class="tx-stage"></div>
      </div>
      <p class="tx-hint">{{ $t('games.embedded.trex.hint') }}</p>
    </div>
  </GameShell>
</template>

<script setup>
import { inject, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import GameShell from '@/components/games/GameShell.vue'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import { createTrexRunner, setTrexSounds, setTrexMuted } from '@/utils/games/trexRunner'
import { gameMuted } from '@/utils/games/gameSound'
import { TREX_SOUNDS } from '@/assets/games/trex/sounds'
import sprite1x from '@/assets/games/trex/100-offline-sprite.png'
import sprite2x from '@/assets/games/trex/200-offline-sprite.png'

const router = useRouter()
const sunLight = inject(SUN_LIGHT_KEY, null)
const shellEl = ref(null)

const areaEl = ref(null)
const stageEl = ref(null)

// 外壳静音键(与小恐龙音效、悬浮 BGM 共用一份状态):引擎在 playSound 里读这个开关,
// 解码好的样本留着,取消静音下一声就能响
watch(gameMuted, (value) => setTrexMuted(value), { immediate: true })

/** 原版画布逻辑尺寸(引擎内 DEFAULT_WIDTH / HEIGHT,这里只用于算放大倍数) */
const BASE_W = 600
const BASE_H = 150

let runner = null
let observer = null

/** 精灵图交给引擎前先塞进页面隐藏节点:引擎按 id 取 2x/1x 那张(与原版同一读法) */
function mountSprites(stage) {
  const make = (id, src) => {
    const img = document.createElement('img')
    img.id = id
    img.src = src
    img.alt = ''
    img.style.display = 'none'
    stage.appendChild(img)
    return img
  }
  const hd = make('offline-resources-2x', sprite2x)
  make('offline-resources-1x', sprite1x)
  return hd
}

/** 游戏条是 4:1 的固定画布:能放多大就等比放大(pixelated 保持像素质感) */
function fitScale() {
  const area = areaEl.value
  const stage = stageEl.value
  if (!area || !stage) return
  const stageW = stage.offsetWidth || BASE_W
  const k = Math.min(area.clientWidth / stageW, area.clientHeight / BASE_H)
  const scale = Math.max(0.6, Math.min(3.4, k))
  stage.style.transform = scale > 1.001 ? `scale(${scale})` : ''
}

/** 引擎按容器 offsetWidth 建画布:外壳尺寸是挂载后实测写进来的,首测为 0,尺寸定下来要再量一次 */
function syncEngine() {
  fitScale()
  runner?.adjustDimensions()
}

onMounted(() => {
  sunLight?.suspendEffects()
  const stage = stageEl.value
  if (!stage) return

  const sprite = mountSprites(stage)
  setTrexSounds(TREX_SOUNDS)
  // 精灵图未解码完时引擎会等 load 再 init,这里不用自己等
  runner = createTrexRunner(stage)
  // 冒烟钩子:与立体麻将 __mahjong3dTable 同口径,便于在页面里核对画布尺寸与运行状态
  window.__trexRunner = runner

  const start = () => {
    syncEngine()
    observer = new ResizeObserver(syncEngine)
    observer.observe(areaEl.value)
  }
  if (sprite.complete) start()
  else sprite.addEventListener('load', start, { once: true })
})

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
  runner?.destroy()
  runner = null
  delete window.__trexRunner
  sunLight?.restoreEffects()
})
</script>

<style scoped>
/* 原版浅灰底,夜间模式由引擎自己反色;深色主题下同口径 */
.tx-shell {
  --gs-bg: #f7f7f7;
}
html.dark .tx-shell {
  --gs-bg: #1f1f1f;
}

.tx-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
}
/* 可放大区域:量的是这块的可用宽高(不含底部提示) */
.tx-area {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1 1 auto;
  width: 100%;
  min-height: 0;
  overflow: visible;
}
/* 画布条本体:宽度交给引擎(它按这个宽度算跑动距离),高度固定 150 */
.tx-stage {
  position: relative;
  width: min(100%, 600px);
  height: 150px;
  transform-origin: center center;
  transition: transform 0.18s ease;
}
.tx-stage :deep(.runner-container) {
  position: relative;
  top: 0;
  left: 0;
  height: 150px;
  max-width: 600px;
  overflow: hidden;
}
.tx-stage :deep(.runner-canvas) {
  display: block;
  image-rendering: pixelated;
}
/* 触屏操作层:原版是整页大小,站内收到游戏条范围内,别挡住外壳按钮 */
.tx-stage :deep(.controller) {
  position: absolute;
  inset: -8px 0;
  width: auto;
  height: auto;
  z-index: 1;
  background: transparent;
  touch-action: manipulation;
}
.tx-hint {
  margin: 0;
  font-size: 12.5px;
  color: var(--color-text-secondary, #6b7280);
  text-align: center;
}
html.dark .tx-hint {
  color: #9aa3b2;
}
</style>
