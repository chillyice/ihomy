<template>
  <GameShell
    :title="$t('games.pool.title')"
    :new-game="true"
    :new-game-confirm="shots > 0 && phase !== 'won'"
    @new-game="newGame"
    @back="router.push('/games')"
  >
    <template #rules>
      <p>{{ $t('games.pool.rules') }}</p>
      <p>{{ $t('games.pool.controls') }}</p>
    </template>
    <template #settings>
      <label class="pool-power">
        <span>{{ $t('games.pool.power') }}</span>
        <input
          v-model.number="power"
          type="range"
          min="5"
          max="100"
          step="1"
          :aria-label="$t('games.pool.power')"
          :aria-valuetext="power + '%'"
          :disabled="phase !== 'ready'"
          @input="redraw"
        />
        <b>{{ power }}%</b>
      </label>
    </template>
    <template #actions>
      <el-button type="primary" :disabled="phase !== 'ready'" @click="shoot">{{ $t('games.pool.shoot') }}</el-button>
    </template>
    <template #stats>
      <span class="gh-chip"
        ><span>{{ $t('games.pool.shots') }}</span
        ><b>{{ shots }}</b></span
      >
      <span class="gh-chip"
        ><span>{{ $t('games.pool.cleared') }}</span
        ><b>{{ cleared }} / 15</b></span
      >
      <span class="gh-chip"
        ><span>{{ $t('games.pool.best') }}</span
        ><b>{{ best || '—' }}</b></span
      >
    </template>

    <div class="pool-stage">
      <div ref="table" class="pool-table">
        <canvas ref="cloth" class="pool-cloth" aria-hidden="true"></canvas>
        <canvas
          ref="surface"
          class="pool-surface"
          :class="{ 'can-aim': phase === 'ready' }"
          role="application"
          tabindex="0"
          :aria-label="$t('games.pool.aimLabel')"
          :aria-description="$t('games.pool.controls')"
          @pointerdown="beginAim"
          @pointermove="moveAim"
          @pointerup="endAim"
          @pointercancel="endAim"
          @lostpointercapture="endAim"
          @keydown="onKey"
          @contextmenu.prevent
        ></canvas>
        <div v-if="phase === 'won'" class="pool-result game-overlay">
          <div class="game-overlay-card">
            <strong class="game-overlay-title">{{ $t('games.pool.won') }}</strong>
            <div class="game-overlay-meta">{{ $t('games.pool.shots') }} · {{ shots }}</div>
            <el-button type="primary" round @click="newGame">{{ $t('games.newGame') }}</el-button>
          </div>
        </div>
      </div>
      <div class="pool-status" role="status" aria-live="polite">{{ $t('games.pool.' + status) }}</div>
    </div>
  </GameShell>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import GameShell from '@/components/games/GameShell.vue'
import { createPoolGame, restorePoolGame, shootPool, stepPool } from '@/utils/games/pool'
import { drawPlay, drawTable, prepareCanvas, tablePoint } from './poolCanvas'

const STATE_KEY = 'ihomy:games:pool:state:v1'
const BEST_KEY = 'ihomy:games:pool:best:v1'
const router = useRouter()
const table = ref(null)
const cloth = ref(null)
const surface = ref(null)
const power = ref(80)
const phase = ref('ready')
const status = ref('ready')
const shots = ref(0)
const cleared = ref(0)
const best = ref(0)

// Keep moving balls outside Vue's render loop. The UI only observes scoring and turn changes.
let game = createPoolGame()
let angle = 0
let drawing = null
let resizeObserver = null
let frame = 0
let lastTime = 0
let aimPointer = null
let penaltiesAtShot = 0

function syncStats() {
  phase.value = game.phase
  shots.value = game.shots + game.penalties
  cleared.value = game.balls.filter((ball) => ball.id && ball.pocketed).length
}

function saveTable() {
  try {
    localStorage.setItem(STATE_KEY, JSON.stringify(game))
    if (game.phase === 'won') {
      const previous = readBest()
      if (previous > 0) best.value = Math.min(previous, best.value)
      localStorage.setItem(BEST_KEY, String(best.value))
    }
  } catch {
    // Play can continue with storage disabled.
  }
}

function readBest() {
  const score = Number(localStorage.getItem(BEST_KEY))
  return Number.isSafeInteger(score) && score > 0 ? score : 0
}

function restoreTable() {
  try {
    best.value = readBest()
    const saved = restorePoolGame(JSON.parse(localStorage.getItem(STATE_KEY)))
    if (saved) game = saved
  } catch {
    // An unreadable snapshot starts a fresh table.
  }
  syncStats()
  if (game.phase === 'won') best.value = best.value ? Math.min(best.value, shots.value) : shots.value
  status.value = game.phase
}

function redraw() {
  if (drawing) drawPlay(drawing, game, angle, power.value)
}

function resizeTable() {
  const box = table.value.getBoundingClientRect()
  if (!box.width || !box.height) return
  drawTable(prepareCanvas(cloth.value, box))
  drawing = prepareCanvas(surface.value, box)
  redraw()
}

function newGame() {
  cancelAnimationFrame(frame)
  frame = 0
  aimPointer = null
  game = createPoolGame()
  angle = 0
  syncStats()
  status.value = 'ready'
  saveTable()
  redraw()
}

function animate(now) {
  const dt = Math.min(0.06, Math.max(0, (now - lastTime) / 1000))
  lastTime = now
  stepPool(game, dt)
  redraw()
  if (game.phase === 'rolling') {
    frame = requestAnimationFrame(animate)
    return
  }
  frame = 0
  syncStats()
  if (game.phase === 'won') {
    best.value = best.value ? Math.min(best.value, shots.value) : shots.value
    status.value = 'won'
  } else {
    status.value = game.penalties > penaltiesAtShot ? 'scratch' : 'ready'
  }
  saveTable()
}

function shoot() {
  if (!shootPool(game, angle, power.value / 100)) return
  aimPointer = null
  penaltiesAtShot = game.penalties
  syncStats()
  status.value = 'rolling'
  lastTime = performance.now()
  redraw()
  frame = requestAnimationFrame(animate)
}

function aimAt(event) {
  const point = tablePoint(surface.value, event)
  const cue = game.balls[0]
  if (Math.hypot(point.x - cue.x, point.y - cue.y) < 2) return
  angle = Math.atan2(point.y - cue.y, point.x - cue.x)
  redraw()
}

function beginAim(event) {
  if (game.phase !== 'ready' || !event.isPrimary || event.button !== 0) return
  surface.value.focus({ preventScroll: true })
  aimPointer = event.pointerId
  aimAt(event)
}

function moveAim(event) {
  if (event.pointerId !== aimPointer || !event.buttons || game.phase !== 'ready') return
  if (!surface.value.hasPointerCapture(event.pointerId)) surface.value.setPointerCapture(event.pointerId)
  aimAt(event)
}

function endAim(event) {
  if (event.pointerId === aimPointer) aimPointer = null
}

function onKey(event) {
  if (game.phase !== 'ready') return
  if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
    event.preventDefault()
    const increment = ((event.shiftKey ? 0.25 : 1) * Math.PI) / 180
    angle += event.key === 'ArrowRight' ? increment : -increment
    redraw()
  } else if (event.code === 'Space' || event.key === 'Enter') {
    event.preventDefault()
    if (!event.repeat) shoot()
  }
}

onMounted(() => {
  restoreTable()
  resizeObserver = new ResizeObserver(resizeTable)
  resizeObserver.observe(table.value)
  resizeTable()
})
onBeforeUnmount(() => {
  cancelAnimationFrame(frame)
  resizeObserver?.disconnect()
})
</script>

<style scoped>
.pool-power {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  max-width: 100%;
  font-size: 13px;
}
.pool-power input {
  width: 110px;
  min-width: 30px;
  height: 28px;
  margin: 0;
  accent-color: var(--el-color-primary);
}
.pool-power b {
  min-width: 3.2em;
  font-variant-numeric: tabular-nums;
}
.pool-stage {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  width: min(100cqw, calc((100cqh - 27px) * 864 / 464));
}
.pool-table {
  position: relative;
  width: 100%;
  aspect-ratio: 864 / 464;
}
.pool-cloth,
.pool-surface {
  display: block;
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}
.pool-surface {
  touch-action: none;
  border-radius: 3%;
}
.pool-surface:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: -2px;
}
.pool-surface.can-aim {
  cursor: crosshair;
}
.pool-status {
  min-height: 20px;
  font-size: 12px;
  line-height: 20px;
  color: var(--el-text-color-secondary);
}
.pool-result {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  border-radius: 3%;
  background: var(--overlay-scrim);
  backdrop-filter: blur(var(--overlay-blur));
}
.pool-result .game-overlay-card {
  display: grid;
  justify-items: center;
  gap: 12px;
  padding: 18px 26px;
  border: 1px solid var(--el-border-color);
  border-radius: 14px;
  color: var(--el-text-color-primary);
  background: var(--el-bg-color);
}
.pool-result strong {
  font-size: 19px;
}
.pool-result .game-overlay-meta {
  color: var(--el-text-color-secondary);
  font-variant-numeric: tabular-nums;
}
@container (max-aspect-ratio: 1/1) {
  .pool-stage {
    width: min(100cqw, calc((100cqh - 27px) * 464 / 864));
  }
  .pool-table {
    aspect-ratio: 464 / 864;
  }
}
@media (orientation: landscape) and (max-height: 600px) {
  .pool-power {
    display: grid;
    grid-template-columns: 1fr auto;
    width: 100%;
    gap: 2px;
  }
  .pool-power input {
    grid-column: 1 / -1;
    grid-row: 2;
    width: 100%;
  }
  .pool-power b {
    text-align: right;
  }
}
</style>
