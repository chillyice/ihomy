<template>
  <GameShell
    :title="$t('games.gomoku.title')"
    :new-game="true"
    :new-game-confirm="game.moves.length > 0 && !game.result"
    @new-game="newGame"
    @back="router.push('/games')"
  >
    <template #rules>
      <p>{{ $t('games.gomoku.rules') }}</p>
      <p>{{ $t('games.gomoku.controls') }}</p>
    </template>
    <template #settings>
      <div class="gm-modes" role="group" :aria-label="$t('games.gomoku.mode')">
        <button v-for="value in MODES" :key="value" type="button" :aria-pressed="mode === value" @click="changeMode(value)">
          {{ $t('games.gomoku.' + value) }}
        </button>
      </div>
    </template>
    <template #stats>
      <span class="gh-chip" role="status" aria-live="polite">
        <i v-if="game.result !== 'draw'" class="gm-indicator" :class="side" aria-hidden="true"></i>
        <b>{{ status }}</b>
      </span>
      <span class="gh-chip"
        ><span>{{ $t('games.gomoku.moves') }}</span
        ><b>{{ game.moves.length }}</b></span
      >
    </template>
    <div class="gm-stage">
      <div class="gm-frame">
        <div ref="boardEl" class="gm-board" role="grid" :aria-label="$t('games.gomoku.title')" @keydown="navigate">
          <div v-for="(row, r) in rows" :key="r" role="row" class="gm-row">
            <button
              v-for="cell in row"
              :key="cell"
              type="button"
              role="gridcell"
              class="gm-point"
              :aria-label="
                $t('games.gomoku.point', {
                  row: r + 1,
                  col: (cell % GOMOKU_SIZE) + 1,
                  stone: $t('games.gomoku.' + (game.board[cell] || 'empty')),
                })
              "
              :aria-disabled="!!game.board[cell] || !humanTurn"
              :class="{ 'gm-star': STARS.has(cell), 'gm-last': cell === lastMove, 'gm-winning': game.line.includes(cell) }"
              :tabindex="cell === cursor ? 0 : -1"
              @focus="cursor = cell"
              @click="makeMove(cell)"
            >
              <span v-if="game.board[cell]" class="gm-stone" :class="game.board[cell]"></span>
            </button>
          </div>
        </div>
      </div>
    </div>
  </GameShell>
</template>

<script setup>
import { computed, nextTick, onScopeDispose, ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import GameShell from '@/components/games/GameShell.vue'
import { GOMOKU_SIZE, chooseGomokuMove, createGomoku, playGomoku, restoreGomoku } from '@/utils/games/gomoku'

const PREFIX = 'ihomy:games:gomoku:'
const MODES = ['computer', 'local']
const CENTER = Math.floor(GOMOKU_SIZE ** 2 / 2)
const STARS = new Set([48, 56, CENTER, 168, 176])
const rows = Array.from({ length: GOMOKU_SIZE }, (_, r) => Array.from({ length: GOMOKU_SIZE }, (_, c) => r * GOMOKU_SIZE + c))
const { t } = useI18n()
const router = useRouter()
const boardEl = ref(null)

function read(key) {
  try {
    return JSON.parse(localStorage.getItem(PREFIX + key))
  } catch {
    return null
  }
}
function write(key, value) {
  try {
    localStorage.setItem(PREFIX + key, JSON.stringify(value))
  } catch {}
}
const mode = ref(read('mode:v1') === 'local' ? 'local' : 'computer')
const load = () => restoreGomoku(read(mode.value + ':v1')) || createGomoku()
const game = shallowRef(load())
const lastMove = computed(() => game.value.moves.at(-1))
const cursor = ref(lastMove.value ?? CENTER)
const aiTurn = computed(() => !game.value.result && mode.value === 'computer' && game.value.turn === 'white')
const humanTurn = computed(() => !game.value.result && !aiTurn.value)
const side = computed(() => game.value.result || game.value.turn)
const status = computed(() => {
  if (game.value.result === 'draw') return t('games.gomoku.draw')
  if (aiTurn.value) return t('games.gomoku.thinking')
  return t('games.gomoku.' + (game.value.result ? 'won' : 'turn'), { side: t('games.gomoku.' + side.value) })
})

function update(next) {
  if (!next) return
  game.value = next
  write(mode.value + ':v1', next.moves)
}
function changeMode(value) {
  if (mode.value === value) return
  mode.value = value
  write('mode:v1', value)
  game.value = load()
  cursor.value = lastMove.value ?? CENTER
}
function newGame() {
  update(createGomoku())
  cursor.value = CENTER
}
function makeMove(cell) {
  if (humanTurn.value) update(playGomoku(game.value, cell))
}

let timer
watch(
  [game, mode],
  () => {
    clearTimeout(timer)
    if (!aiTurn.value) return
    const position = game.value
    timer = setTimeout(() => {
      if (game.value === position && aiTurn.value) update(playGomoku(position, chooseGomokuMove(position)))
    }, 320)
  },
  { immediate: true },
)
onScopeDispose(() => clearTimeout(timer))

const directions = { ArrowUp: [-1, 0], ArrowDown: [1, 0], ArrowLeft: [0, -1], ArrowRight: [0, 1] }
function navigate(event) {
  const dir = directions[event.key]
  if (!dir) return
  event.preventDefault()
  const clamp = (n) => Math.max(0, Math.min(GOMOKU_SIZE - 1, n))
  const row = clamp(Math.floor(cursor.value / GOMOKU_SIZE) + dir[0])
  const col = clamp((cursor.value % GOMOKU_SIZE) + dir[1])
  cursor.value = row * GOMOKU_SIZE + col
  nextTick(() => boardEl.value?.querySelector('[tabindex="0"]')?.focus({ preventScroll: true }))
}
</script>

<style scoped>
.gm-stage {
  display: grid;
  width: 100%;
  height: 100%;
  place-items: center;
}
.gm-modes {
  display: inline-flex;
  border: 1px solid var(--el-border-color);
  border-radius: 12px;
  padding: 2px;
  gap: 2px;
}
.gm-modes button {
  border: 0;
  border-radius: 9px;
  padding: 5px 10px;
  background: transparent;
  color: var(--el-text-color-regular);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.gm-modes button[aria-pressed='true'] {
  background: var(--el-fill-color);
  color: var(--el-text-color-primary);
  font-weight: 650;
}
.gm-frame {
  width: min(100cqw, 100cqh, 720px);
  aspect-ratio: 1;
  box-sizing: border-box;
  padding: clamp(5px, 1.5cqw, 14px);
  border: 1px solid #8a5c32;
  border-radius: 9px;
  background:
    repeating-linear-gradient(3deg, transparent 0 6px, #97612610 7px, transparent 8px 18px),
    linear-gradient(125deg, #edcd93, #d7ad71 70%, #e0b778);
  box-shadow:
    inset 0 1px 2px #fff4d27a,
    inset 0 -3px 4px #78492454;
}
.gm-board {
  position: relative;
  display: grid;
  width: 100%;
  height: 100%;
  grid-template-rows: repeat(15, 1fr);
}
.gm-board::before {
  position: absolute;
  content: '';
  inset: calc(100% / 30 - 0.5px);
  pointer-events: none;
  border: 1px solid #694922;
  background: linear-gradient(to right, #765326 1px, transparent 1px), linear-gradient(to bottom, #765326 1px, transparent 1px);
  background-size: calc(100% / 14) calc(100% / 14);
}
.gm-row {
  display: grid;
  grid-template-columns: repeat(15, 1fr);
  min-height: 0;
}
.gm-point {
  position: relative;
  display: grid;
  place-items: center;
  padding: 0;
  border: 0;
  background: transparent;
  touch-action: manipulation;
}
.gm-point[aria-disabled='false'] {
  cursor: pointer;
}
.gm-point:focus-visible {
  outline: 2px solid #99391b;
  outline-offset: -2px;
  border-radius: 25%;
}
.gm-star::before {
  position: absolute;
  content: '';
  width: 18%;
  aspect-ratio: 1;
  border-radius: 50%;
  background: #593e20;
}
.gm-stone,
.gm-indicator {
  border-radius: 50%;
}
.gm-stone {
  position: relative;
  width: 86%;
  aspect-ratio: 1;
  box-shadow:
    0 1px 2px #492c197d,
    inset 0 -1px 2px #20160f47;
}
.gm-indicator {
  width: 0.85em;
  height: 0.85em;
  border: 1px solid #8e8477;
}
.black {
  background: radial-gradient(circle at 34% 26%, #656463, #292b2a 40%, #131715 85%);
}
.white {
  background: radial-gradient(circle at 34% 26%, #fffef9, #ece9dc 57%, #beb8ab);
}
.gm-last .gm-stone::after {
  content: '';
  position: absolute;
  inset: 36%;
  border-radius: 50%;
  background: #c56d42;
}
.gm-winning .gm-stone {
  outline: 2px solid #ab502c;
  outline-offset: -2px;
}
@media (orientation: landscape) and (max-height: 520px) {
  .gm-stage {
    justify-items: start;
  }
  .gm-modes {
    box-sizing: border-box;
    width: 100%;
    flex-direction: column;
  }
  .gm-modes button {
    text-align: start;
  }
}
</style>
