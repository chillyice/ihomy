<template>
  <GameShell
    :title="$t('games.chineseCheckers.title')"
    :new-game="true"
    :new-game-confirm="(game.turns > 0 || chaining) && game.winner == null"
    @new-game="newGame"
    @back="router.push('/games')"
  >
    <template #rules>
      <p>{{ $t('games.chineseCheckers.rules') }}</p>
      <p>{{ $t('games.chineseCheckers.controls') }}</p>
    </template>
    <template #settings>
      <el-radio-group
        :model-value="mode"
        size="small"
        :aria-label="$t('games.chineseCheckers.mode')"
        :disabled="chaining"
        @update:model-value="switchMode"
      >
        <el-radio-button value="cpu">{{ $t('games.chineseCheckers.cpu') }}</el-radio-button>
        <el-radio-button value="local">{{ $t('games.chineseCheckers.local') }}</el-radio-button>
      </el-radio-group>
    </template>
    <template v-if="chaining" #actions>
      <el-button type="primary" @click="finishJump">{{ $t('games.chineseCheckers.endJump') }}</el-button>
    </template>
    <template #stats>
      <span v-for="side in [1, 0]" :key="side" class="gh-chip cc-player" :class="{ active: game.player === side }">
        <span><i :class="side === 1 ? 'jade' : 'amber'"></i>{{ playerName(side) }}</span>
        <b :title="$t('games.chineseCheckers.homeCount')">{{ homeCounts[side] }} / 10</b>
      </span>
      <span class="gh-chip"
        ><span>{{ $t('games.chineseCheckers.turns') }}</span
        ><b>{{ game.turns }}</b></span
      >
    </template>
    <div class="cc-stage">
      <div class="cc-board" role="group" :aria-label="$t('games.chineseCheckers.board')" @keydown="onBoardKey">
        <svg class="cc-drawing" viewBox="-7.1 -8.13 14.2 16.26" aria-hidden="true">
          <defs>
            <linearGradient id="cc-frame" x2="0.85" y2="1">
              <stop stop-color="#76543d" />
              <stop offset=".4" stop-color="#5a4034" />
              <stop offset="1" stop-color="#382d29" />
            </linearGradient>
            <linearGradient id="cc-maple" x1="0" x2="1" y2=".82">
              <stop stop-color="#e6c997" />
              <stop offset=".5" stop-color="#f0d7a8" />
              <stop offset="1" stop-color="#d8b987" />
            </linearGradient>
            <linearGradient id="cc-socket" x1="0" x2=".6" y2="1">
              <stop stop-color="#74593f" />
              <stop offset=".48" stop-color="#9f805a" />
              <stop offset="1" stop-color="#c5a77c" />
            </linearGradient>
            <radialGradient id="cc-jade" cx=".35" cy=".24" r=".8">
              <stop stop-color="#d4f1d8" />
              <stop offset=".18" stop-color="#8ec4ad" />
              <stop offset=".52" stop-color="#378877" />
              <stop offset=".86" stop-color="#1b5b54" />
              <stop offset="1" stop-color="#16453f" />
            </radialGradient>
            <radialGradient id="cc-amber" cx=".35" cy=".24" r=".8">
              <stop stop-color="#fff3c8" />
              <stop offset=".18" stop-color="#efc071" />
              <stop offset=".52" stop-color="#c38639" />
              <stop offset=".86" stop-color="#93572b" />
              <stop offset="1" stop-color="#734229" />
            </radialGradient>
          </defs>
          <polygon :points="rim" fill="url(#cc-frame)" stroke="#43342d" stroke-width=".13" stroke-linejoin="round" />
          <polygon :points="rim" transform="scale(.97)" fill="none" stroke="#bea17c" stroke-width=".035" />
          <polygon :points="star" fill="url(#cc-maple)" stroke="#b8996d" stroke-width=".14" stroke-linejoin="round" />
          <g v-for="(tint, i) in campTints" :key="i" :transform="`rotate(${i * 60})`">
            <path d="M 0 -7.46 L 2.03 -3.88 H -2.03 Z" :fill="tint" />
            <path d="M -2 -3.88 H 2" fill="none" stroke="#936e4e" opacity=".55" stroke-width=".035" />
          </g>
          <path :d="lattice" fill="none" stroke="#a7865f" stroke-width=".032" opacity=".66" />
          <g v-for="hole in CHECKER_HOLES" :key="hole.id">
            <circle :cx="hole.x" :cy="hole.y + 0.025" r=".248" fill="#f3ddb5" />
            <circle :cx="hole.x" :cy="hole.y" r=".23" fill="url(#cc-socket)" />
          </g>
          <polyline
            v-if="trail.length > 1"
            :points="trailPoints"
            fill="none"
            :stroke="chaining ? '#195d55' : '#775a45'"
            :opacity="chaining ? 0.92 : 0.66"
            stroke-width=".076"
            stroke-linejoin="round"
            stroke-linecap="round"
            :stroke-dasharray="chaining ? undefined : '.08 .14'"
          />
          <g v-for="hole in CHECKER_HOLES" :key="hole.id">
            <template v-if="preview[hole.id] != null">
              <circle :cx="hole.x + 0.028" :cy="hole.y + 0.064" r=".375" fill="#674c35" opacity=".58" />
              <circle :cx="hole.x" :cy="hole.y" r=".373" :fill="preview[hole.id] === 1 ? 'url(#cc-jade)' : 'url(#cc-amber)'" />
            </template>
            <circle v-if="selected === hole.id" :cx="hole.x" :cy="hole.y" r=".456" fill="none" stroke="#164f49" stroke-width=".068" />
            <g v-if="steps.has(hole.id)">
              <circle :cx="hole.x" :cy="hole.y" r=".295" fill="#f9edca" stroke="#28675c" stroke-width=".07" />
              <circle :cx="hole.x" :cy="hole.y" r=".095" fill="#28675c" />
            </g>
          </g>
        </svg>
        <button
          v-for="hole in CHECKER_HOLES"
          :key="hole.id"
          :ref="
            (el) => {
              if (el) cellEls[hole.id] = el
            }
          "
          type="button"
          class="cc-cell"
          :style="cellPositions[hole.id]"
          :tabindex="focused === hole.id ? 0 : -1"
          :aria-label="cellLabel(hole)"
          :aria-pressed="selected === hole.id"
          @focus="focused = hole.id"
          @click="chooseHole(hole.id)"
        ></button>
        <div v-if="game.winner != null" class="cc-result">
          <strong>{{ $t('games.chineseCheckers.winner', { player: playerName(game.winner) }) }}</strong>
          <el-button type="primary" round @click="newGame">{{ $t('games.newGame') }}</el-button>
        </div>
      </div>
      <div class="cc-status" role="status" aria-live="polite">
        <strong>{{ headline }}</strong
        ><span v-if="hint"> · {{ hint }}</span>
      </div>
    </div>
  </GameShell>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import GameShell from '@/components/games/GameShell.vue'
import {
  CHECKER_HOLES,
  createCheckersGame,
  checkersNextSteps,
  playCheckersMove,
  chooseCheckersMove,
  restoreCheckersGame,
} from '@/utils/games/chineseCheckers'

const { t } = useI18n()
const router = useRouter()
const key = 'ihomy:games:chinese-checkers:'
const mode = ref('cpu')
const game = ref(createCheckersGame())
const path = ref([])
const cellEls = []
const focused = ref(CHECKER_HOLES.find((h) => h.camp === 1).id)
let cpuTimer

// Coordinates in the viewBox are measured in hole spacings, including the hit targets.
const campTints = ['#dcb27c', '#d2bd9a', '#dbc09f', '#a8c9ad', '#dac19a', '#ccb8a0']
function vertices(radii) {
  return radii
    .map((radius, i) => {
      const angle = -Math.PI / 2 + (i * Math.PI * 2) / radii.length
      return [radius * Math.cos(angle), radius * Math.sin(angle)].join(',')
    })
    .join(' ')
}
const rim = vertices(Array(6).fill(8))
const star = vertices(Array.from({ length: 12 }, (_, i) => (i % 2 ? 4.31 : 7.47)))
const cellPositions = CHECKER_HOLES.map((h) => ({ left: `${(100 * (h.x + 7.1)) / 14.2}%`, top: `${(100 * (h.y + 8.13)) / 16.26}%` }))
const rows = new Map()
const byCoord = new Map()
for (const h of CHECKER_HOLES) {
  if (!rows.has(h.r)) rows.set(h.r, [])
  rows.get(h.r).push(h)
  byCoord.set(`${h.q},${h.r}`, h)
}
for (const row of rows.values()) row.sort((a, b) => a.x - b.x)
const lattice = CHECKER_HOLES.flatMap((h) =>
  [
    [1, 0],
    [0, 1],
    [-1, 1],
  ].map(([dq, dr]) => {
    const other = byCoord.get(`${h.q + dq},${h.r + dr}`)
    return other ? `M${h.x} ${h.y}L${other.x} ${other.y}` : ''
  }),
).join('')

const chaining = computed(() => path.value.length > 1)
const selected = computed(() => path.value.at(-1))
const steps = computed(() => new Map(checkersNextSteps(game.value, path.value).map((s) => [s.to, s])))
const preview = computed(() => {
  if (!chaining.value) return game.value.board
  const board = [...game.value.board]
  board[path.value[0]] = null
  board[selected.value] = game.value.player
  return board
})
const trail = computed(() => (chaining.value ? path.value : game.value.lastMove?.path || []))
const trailPoints = computed(() => trail.value.map((id) => `${CHECKER_HOLES[id].x},${CHECKER_HOLES[id].y}`).join(' '))
const cpuTurn = computed(() => mode.value === 'cpu' && game.value.player === 0 && game.value.winner == null)
const homeCounts = computed(() =>
  [0, 1].map((side) => CHECKER_HOLES.filter((h) => h.camp === 1 - side && game.value.board[h.id] === side).length),
)
const headline = computed(() => {
  if (game.value.winner != null) return t('games.chineseCheckers.winner', { player: playerName(game.value.winner) })
  if (chaining.value) return t('games.chineseCheckers.jumpCount', { n: path.value.length - 1 })
  return t('games.chineseCheckers.turn', { player: playerName(game.value.player) })
})
const hint = computed(() => {
  if (game.value.winner != null) return ''
  if (cpuTurn.value) return t('games.chineseCheckers.cpuThinking')
  if (chaining.value) return t('games.chineseCheckers.continueJump')
  if (path.value.length && !steps.value.size) return t('games.chineseCheckers.blockedPiece')
  return t(path.value.length ? 'games.chineseCheckers.chooseLanding' : 'games.chineseCheckers.choosePiece')
})

function playerName(side) {
  if (mode.value === 'cpu') return t(side === 1 ? 'games.chineseCheckers.you' : 'games.chineseCheckers.computer')
  return t(side === 1 ? 'games.chineseCheckers.bottom' : 'games.chineseCheckers.top')
}

function cellLabel(hole) {
  const pos = t('games.chineseCheckers.cell', { row: hole.r + 9, col: rows.get(hole.r).indexOf(hole) + 1 })
  const piece = preview.value[hole.id]
  if (piece != null) return pos + ' · ' + playerName(piece)
  return steps.value.has(hole.id) ? pos + ' · ' + t('games.chineseCheckers.chooseLanding') : pos
}

function save() {
  try {
    localStorage.setItem(key + mode.value + ':v1', JSON.stringify(game.value))
  } catch {
    /* Storage is optional for local play. */
  }
}

function resetSelection() {
  path.value = []
  focused.value = game.value.board.findIndex((side) => side === game.value.player)
}

function loadMode(next) {
  clearTimeout(cpuTimer)
  mode.value = next
  let saved
  try {
    saved = restoreCheckersGame(JSON.parse(localStorage.getItem(key + next + ':v1')))
  } catch {
    /* An unreadable snapshot starts a fresh game. */
  }
  game.value = saved || createCheckersGame()
  resetSelection()
  scheduleCpu()
}

function switchMode(next) {
  if (chaining.value || next === mode.value || !['local', 'cpu'].includes(next)) return
  loadMode(next)
  try {
    localStorage.setItem(key + 'mode:v1', next)
  } catch {
    /* Switching still works when preferences cannot be stored. */
  }
}

function newGame() {
  clearTimeout(cpuTimer)
  game.value = createCheckersGame()
  resetSelection()
  save()
  scheduleCpu()
}

function commit(move) {
  if (!playCheckersMove(game.value, move[0], move.at(-1), move)) return false
  path.value = []
  save()
  scheduleCpu()
  return true
}

function scheduleCpu() {
  clearTimeout(cpuTimer)
  if (!cpuTurn.value) return
  cpuTimer = setTimeout(() => {
    if (!cpuTurn.value) return
    const move = chooseCheckersMove(game.value)
    if (move) commit(move.path)
  }, 480)
}

function finishJump() {
  if (chaining.value && !cpuTurn.value) commit(path.value)
}

function chooseHole(id) {
  if (game.value.winner != null || cpuTurn.value) return
  const step = steps.value.get(id)
  if (step) {
    const move = [...path.value, id]
    if (step.jump && checkersNextSteps(game.value, move).length) path.value = move
    else commit(move)
  } else if (chaining.value) {
    if (id === selected.value) finishJump()
  } else if (game.value.board[id] === game.value.player) {
    path.value = id === selected.value ? [] : [id]
  }
}

function onBoardKey(event) {
  if (!event.target.classList.contains('cc-cell')) return
  if (event.key === 'Escape' && !chaining.value) {
    path.value = []
    return
  }
  if (!['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(event.key)) return
  event.preventDefault()
  const current = CHECKER_HOLES[focused.value]
  const row = rows.get(current.r)
  let next
  if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
    next = row[row.indexOf(current) + (event.key === 'ArrowLeft' ? -1 : 1)]
  } else {
    const adjacent = rows.get(current.r + (event.key === 'ArrowUp' ? -1 : 1))
    next = adjacent?.reduce((best, h) => (Math.abs(h.x - current.x) < Math.abs(best.x - current.x) ? h : best))
  }
  if (next) cellEls[next.id]?.focus({ preventScroll: true })
}

onMounted(() => {
  let preferred
  try {
    preferred = localStorage.getItem(key + 'mode:v1')
  } catch {
    /* Use the default mode. */
  }
  loadMode(preferred === 'local' ? 'local' : 'cpu')
})
onBeforeUnmount(() => clearTimeout(cpuTimer))
</script>

<style scoped>
.cc-stage {
  width: min(100cqw, calc((100cqh - 48px) * 14.2 / 16.26));
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
}
.cc-board {
  position: relative;
  width: 100%;
  aspect-ratio: 14.2 / 16.26;
  touch-action: manipulation;
}
.cc-drawing {
  display: block;
  width: 100%;
  height: 100%;
}
.cc-cell {
  position: absolute;
  transform: translate(-50%, -50%);
  width: calc(100% / 14.2);
  height: calc(115.47% / 16.26);
  padding: 0;
  border: none;
  background: transparent;
  clip-path: polygon(50% 0, 100% 25%, 100% 75%, 50% 100%, 0 75%, 0 25%);
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.cc-cell:focus-visible::after {
  content: '';
  position: absolute;
  inset: 12% 6%;
  border-radius: 50%;
  outline: 2px solid #264263;
}
.cc-player > span,
.cc-player i {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.cc-player i {
  width: 10px;
  height: 10px;
  flex: none;
  border-radius: 50%;
}
.cc-player .jade {
  background: #347e70;
}
.cc-player .amber {
  background: #b27832;
}
.cc-player.active > span {
  font-weight: 700;
}
.cc-status {
  min-height: 36px;
  text-align: center;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
}
.cc-status strong {
  color: var(--el-text-color-primary);
}
.cc-result {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  display: grid;
  justify-items: center;
  gap: 12px;
  border: 1px solid var(--el-border-color);
  padding: 14px 22px;
  border-radius: 14px;
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
}
</style>
