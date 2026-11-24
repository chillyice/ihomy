<template>
  <GameShell
    :title="$t('games.eightBall.title')"
    :new-game="!online"
    :new-game-confirm="game.shots > 0 && phase !== 'won'"
    :room-mode="online"
    :default-wide="online"
    @new-game="newGame"
    @back="goBack"
  >
    <template #rules>
      <p>{{ $t('games.eightBall.rules') }}</p>
      <p>{{ $t('games.eightBall.controls') }}</p>
    </template>
    <template v-if="!room?.watching" #settings>
      <label class="pool-power">
        <span>{{ $t('games.eightBall.power') }}</span>
        <input
          v-model.number="power"
          type="range"
          min="5"
          max="100"
          step="1"
          :aria-label="$t('games.eightBall.power')"
          :aria-valuetext="power + '%'"
          :disabled="!canAct"
          @input="redraw"
        />
        <b>{{ power }}%</b>
      </label>
    </template>
    <template v-if="!room?.watching" #actions>
      <el-button v-if="!online" :loading="openingRoom" @click="$emit('onlinePlay')">{{ $t('games.eightBall.onlinePlay') }}</el-button>
      <el-button v-if="canAct && game.ballInHand" :aria-pressed="placing" @click="togglePlace">
        {{ $t(placing ? 'games.eightBall.shootMode' : 'games.eightBall.placeMode') }}
      </el-button>
      <el-button type="primary" :disabled="!canAct || placing" @click="shoot">{{ $t('games.eightBall.shoot') }}</el-button>
    </template>
    <template #stats>
      <span
        v-for="(label, seat) in names"
        :key="seat"
        class="gh-chip pool-seat"
        :class="{ 'is-current': game.player === seat && phase !== 'won' }"
      >
        <span>{{ label }}</span>
        <b :title="$t('games.eightBall.remaining')"
          ><i :class="game.groups[seat]" aria-hidden="true"></i>{{ $t('games.eightBall.' + (game.groups[seat] || 'open'))
          }}<template v-if="game.groups[seat]"> · {{ remaining[seat] }}</template></b
        >
      </span>
    </template>

    <div class="pool-stage">
      <div ref="table" class="pool-table">
        <canvas ref="cloth" class="pool-cloth" aria-hidden="true"></canvas>
        <canvas
          ref="surface"
          class="pool-surface"
          :class="{ 'can-aim': canAct }"
          role="application"
          tabindex="0"
          :aria-label="$t('games.eightBall.aimLabel')"
          :aria-description="$t('games.eightBall.controls')"
          @pointerdown="beginAim"
          @pointermove="moveAim"
          @pointerup="endAim"
          @pointercancel="endAim"
          @lostpointercapture="endAim"
          @keydown="onKey"
          @contextmenu.prevent
        ></canvas>
        <div v-if="phase === 'won' || roomEnded" class="pool-result game-overlay">
          <div class="game-overlay-card">
            <strong class="game-overlay-title">{{ resultTitle }}</strong>
            <div v-if="game.winner != null" class="game-overlay-meta">{{ $t('games.eightBall.' + game.lastResult) }}</div>
            <el-button v-if="online" type="primary" round @click="goBack">{{ $t('games.room.backRoom') }}</el-button>
            <el-button v-else type="primary" round @click="newGame">{{ $t('games.newGame') }}</el-button>
          </div>
        </div>
      </div>
      <div class="pool-status" role="status" aria-live="polite">
        <strong v-if="(!room || room.ready) && !roomEnded && phase !== 'won'"
          >{{ $t('games.eightBall.turn', { player: names[game.player] }) }} ·
        </strong>
        {{ caption }}
      </div>
    </div>
  </GameShell>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, triggerRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import GameShell from '@/components/games/GameShell.vue'
import { useEightBallRoom } from '@/composables/useEightBallRoom'
import {
  createEightBallGame,
  restoreEightBallGame,
  shootEightBall,
  stepEightBall,
  canPlaceEightBallCue,
  placeEightBallCue,
} from '@/utils/games/eightBall'
import { drawPlay, drawTable, prepareCanvas, tablePoint } from './poolCanvas'

defineProps({ openingRoom: Boolean })
defineEmits(['onlinePlay'])
const { t } = useI18n()
const route = useRoute()
const online = Number(route.query.room) > 0
const STATE_KEY = 'ihomy:games:eight-ball:state:v1'
const router = useRouter()
const table = ref(null)
const cloth = ref(null)
const surface = ref(null)
const power = ref(80)
const placing = ref(false)
const invalidPlace = ref(false)

// Keep moving balls outside Vue's render loop. The UI only observes scoring and turn changes.
const game = shallowRef(createEightBallGame())
let angle = 0
let drawing = null
let resizeObserver = null
let frame = 0
let lastTime = 0
let aimPointer = null
let candidate = null

const room = online ? useEightBallRoom({ getGame: () => game.value, applyState, applyShot, applyPlacement }) : null
const phase = computed(() => game.value.phase)
const names = computed(() => [0, 1].map((seat) => room?.names[seat] || t('games.eightBall.player' + (seat + 1))))
const remaining = computed(() =>
  game.value.groups.map(
    (group) => game.value.balls.filter((b) => !b.pocketed && (group === 'solid' ? b.id > 0 && b.id < 8 : b.id > 8)).length,
  ),
)
const roomEnded = computed(() => !!(room?.play.ended || room?.play.gone))
const canAct = computed(() => phase.value === 'ready' && (!room || (room.ready && room.canAct && !roomEnded.value)))
const resultTitle = computed(() => {
  const winnerId = room?.play.room?.winnerId
  if (winnerId != null) {
    const member = room.play.members.find((m) => String(m.userId) === String(winnerId))
    if (member?.name) return t('games.eightBall.winner', { player: member.name })
  }
  if (game.value.winner != null) return t('games.eightBall.winner', { player: names.value[game.value.winner] })
  return t('games.room.status.finished')
})
const caption = computed(() => {
  if (roomEnded.value || phase.value === 'won') return resultTitle.value
  if (room && !room.ready) return t('games.eightBall.waiting')
  let key = game.value.lastResult || 'breakShot'
  if (phase.value === 'rolling') return t('games.eightBall.rolling')
  if (invalidPlace.value) key = 'placementInvalid'
  const message = t('games.eightBall.' + key)
  return room?.watching ? t('games.eightBall.watching') + ' · ' + message : message
})

function goBack() {
  if (room) room.back()
  else router.push('/games')
}

function saveTable() {
  if (online) return
  try {
    localStorage.setItem(STATE_KEY, JSON.stringify(game.value))
  } catch {
    // Play can continue with storage disabled.
  }
}

function restoreTable() {
  if (online) return
  try {
    const saved = restoreEightBallGame(JSON.parse(localStorage.getItem(STATE_KEY)))
    if (saved) applyState(saved)
  } catch {
    // An unreadable snapshot starts a fresh table.
  }
}

function redraw() {
  if (drawing)
    drawPlay(
      drawing,
      game.value,
      angle,
      power.value,
      canAct.value && !placing.value,
      canAct.value && placing.value ? candidate || game.value.balls[0] : null,
    )
}

function resizeTable() {
  const box = table.value.getBoundingClientRect()
  if (!box.width || !box.height) return
  drawTable(prepareCanvas(cloth.value, box))
  drawing = prepareCanvas(surface.value, box)
  redraw()
}

function newGame() {
  if (online) return
  applyState(createEightBallGame())
  saveTable()
}

function applyState(next) {
  cancelAnimationFrame(frame)
  frame = 0
  aimPointer = null
  if (!next.ballInHand) placing.value = false
  else if (!game.value.ballInHand || next.shots !== game.value.shots) placing.value = true
  if (!next.shots) angle = 0
  candidate = null
  invalidPlace.value = false
  game.value = next
  redraw()
}

function animate(now) {
  const dt = Math.min(0.06, Math.max(0, (now - lastTime) / 1000))
  lastTime = now
  stepEightBall(game.value, dt)
  redraw()
  if (game.value.phase === 'rolling') {
    frame = requestAnimationFrame(animate)
    return
  }
  frame = 0
  placing.value = !!game.value.ballInHand
  triggerRef(game)
  if (room) room.settled()
  else saveTable()
  redraw()
}

function shoot() {
  if (!canAct.value || placing.value) return
  if (room) room.shoot(angle, power.value / 100)
  else applyShot(angle, power.value / 100)
}

function applyShot(direction, force) {
  if (!shootEightBall(game.value, direction, force)) return false
  angle = direction
  aimPointer = null
  candidate = null
  placing.value = false
  invalidPlace.value = false
  triggerRef(game)
  lastTime = performance.now()
  redraw()
  frame = requestAnimationFrame(animate)
  return true
}

function aimAt(event) {
  const point = tablePoint(surface.value, event)
  if (placing.value) {
    previewPlace(point)
    return
  }
  const cue = game.value.balls[0]
  if (Math.hypot(point.x - cue.x, point.y - cue.y) < 2) return
  angle = Math.atan2(point.y - cue.y, point.x - cue.x)
  redraw()
}

function togglePlace() {
  placing.value = !placing.value
  candidate = null
  invalidPlace.value = false
  redraw()
}

function previewPlace(point) {
  candidate = { ...point, valid: canPlaceEightBallCue(game.value, point.x, point.y) }
  invalidPlace.value = !candidate.valid
  redraw()
}

function confirmPlace() {
  if (!candidate?.valid || !canAct.value) return
  const { x, y } = candidate
  const placed = room ? room.place(x, y) : applyPlacement(x, y)
  if (placed) {
    placing.value = false
    candidate = null
    redraw()
  }
}

function applyPlacement(x, y) {
  if (!placeEightBallCue(game.value, x, y)) return false
  triggerRef(game)
  saveTable()
  redraw()
  return true
}

function beginAim(event) {
  if (!canAct.value || !event.isPrimary || event.button !== 0) return
  surface.value.focus({ preventScroll: true })
  aimPointer = event.pointerId
  aimAt(event)
}

function moveAim(event) {
  if (!canAct.value || event.pointerId !== aimPointer || !event.buttons) return
  if (!surface.value.hasPointerCapture(event.pointerId)) surface.value.setPointerCapture(event.pointerId)
  aimAt(event)
}

function endAim(event) {
  if (event.pointerId !== aimPointer) return
  if (placing.value && event.type === 'pointerup' && canAct.value) {
    aimAt(event)
    confirmPlace()
  }
  aimPointer = null
}

function onKey(event) {
  if (!canAct.value) return
  if (placing.value) {
    const delta = event.shiftKey ? 1 : 7
    const upright = surface.value.height > surface.value.width
    const offsets = {
      ArrowLeft: upright ? [0, -delta] : [-delta, 0],
      ArrowRight: upright ? [0, delta] : [delta, 0],
      ArrowUp: upright ? [delta, 0] : [0, -delta],
      ArrowDown: upright ? [-delta, 0] : [0, delta],
    }
    if (offsets[event.key]) {
      event.preventDefault()
      const { x, y } = candidate || game.value.balls[0]
      const [dx, dy] = offsets[event.key]
      previewPlace({ x: x + dx, y: y + dy })
    } else if (event.code === 'Space' || event.key === 'Enter') {
      event.preventDefault()
      if (!event.repeat) {
        if (!candidate) previewPlace(game.value.balls[0])
        confirmPlace()
      }
    }
    return
  }
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

watch(canAct, redraw)
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
  --caption-space: 48px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  width: min(100cqw, calc((100cqh - var(--caption-space)) * 864 / 464));
}
.pool-seat {
  min-width: 0;
}
.pool-seat > span {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.pool-seat b {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  white-space: nowrap;
}
.pool-seat.is-current > span {
  font-weight: 700;
  color: var(--el-color-primary);
}
.pool-seat b i:is(.solid, .stripe) {
  width: 10px;
  height: 10px;
  border: 1px solid #6b5431;
  border-radius: 50%;
  background: #d7a148;
}
.pool-seat b i.stripe {
  background: linear-gradient(0deg, #fff5d5 25%, #397d9f 25% 75%, #fff5d5 75%);
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
  text-align: center;
  overflow-wrap: anywhere;
}
.pool-status strong {
  color: var(--el-text-color-regular);
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
    width: min(100cqw, calc((100cqh - var(--caption-space)) * 464 / 864));
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
