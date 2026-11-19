<!-- 飞行棋:单机为 1 真人 + AI 补位(2/3/4 人),「一起玩」房间为 4 席真人对局。
     规则内核全部在 utils/games/ludo.js(盘面几何/走子/撞机/飞跃/AI 都是纯函数);
     本页只做盘面绘制、逐格动画、骰子与回合驱动。房间局由房主权威跑引擎并把 state 广播给全房,
     其他人只发操作意图(掷骰/选子),所以两端画面永远一致。 -->
<template>
  <GameShell
    ref="shellEl"
    class="ld-shell"
    :title="$t('games.ludo.title')"
    body-align="stretch"
    readable
    :new-game="!play.active && state?.phase === 'playing'"
    @back="goBack"
    @new-game="restart"
  >
    <template #extras>
      <GameRoomBar
        v-if="play.active"
        :room="play.room"
        :is-host="play.isHost"
        :score="myFinished"
        :reported="play.reported"
        :ended="gameOver"
        :result="play.spec?.result"
        :my-id="play.myId"
        exit-ends-room
        @submit="submitRoomScore"
        @leave="play.leave"
        @auto="onAutoToggle"
      />
    </template>

    <div class="ludo-card" @contextmenu.prevent>
      <div class="ludo-layout">
        <div class="ludo-board-wrap">
          <div class="ludo-board-stage" :class="{ live: isMyTurn && !gameOver }">
            <svg class="ludo-board" viewBox="0 0 15 15" role="img" :aria-label="$t('games.ludo.boardLabel')">
              <rect class="bd-paper" x="0.12" y="0.12" width="14.76" height="14.76" rx="1.15" />

              <!-- 四色机场 -->
              <g v-for="h in hangarViews" :key="`h${h.color}`" class="bd-hangar" :class="{ dim: !h.active }">
                <rect
                  :x="h.x + 0.3"
                  :y="h.y + 0.3"
                  width="5.4"
                  height="5.4"
                  rx="0.98"
                  class="bd-hangar-plate"
                  :style="{ fill: colorTint(h.color, 0.11), stroke: colorTint(h.color, 0.36) }"
                />
                <circle
                  v-for="pad in h.pads"
                  :key="pad.pad"
                  :cx="pad.cx"
                  :cy="pad.cy"
                  r="0.54"
                  class="bd-pad"
                  :style="{ stroke: colorTint(h.color, 0.42) }"
                />
              </g>

              <!-- 主环 52 格 -->
              <g class="bd-ring">
                <g v-for="c in ringViews" :key="`r${c.index}`">
                  <rect
                    :x="c.x + 0.07"
                    :y="c.y + 0.07"
                    width="0.86"
                    height="0.86"
                    rx="0.21"
                    class="cell-fill"
                    :class="{ start: c.owner >= 0, plane: c.planeOf >= 0 }"
                    :style="cellStyle(c)"
                  />
                  <path
                    v-if="c.owner >= 0"
                    :d="PLANE_PATH"
                    class="bd-plane-icon"
                    :transform="`translate(${c.x + 0.5} ${c.y + 0.5}) scale(0.031) translate(-8 -8)`"
                  />
                  <path
                    v-else-if="c.planeOf >= 0"
                    :d="PLANE_PATH"
                    class="bd-plane-mark"
                    :style="{ fill: colorSolid(c.planeOf) }"
                    :transform="`translate(${c.x + 0.5} ${c.y + 0.5}) scale(0.026) translate(-8 -8)`"
                  />
                </g>
              </g>

              <!-- 中心停机坪 + 四色跑道 -->
              <rect class="bd-center" x="6.02" y="6.02" width="2.96" height="2.96" rx="0.86" />
              <g class="bd-lanes">
                <rect
                  v-for="l in laneViews"
                  :key="`l${l.color}-${l.i}`"
                  :x="l.x + 0.08"
                  :y="l.y + 0.08"
                  width="0.84"
                  height="0.84"
                  rx="0.2"
                  class="bd-lane"
                  :style="{ fill: colorTint(l.color, 0.26 + l.i * 0.1), stroke: colorTint(l.color, 0.5) }"
                />
              </g>
              <path class="bd-emblem" :d="PLANE_PATH" transform="translate(6.28 6.28) scale(0.152) translate(-8 -8)" />

              <!-- 航线虚线(只画参战颜色) -->
              <path v-for="a in planeArcs" :key="`a${a.color}`" class="bd-arc" :d="a.d" :style="{ stroke: colorTint(a.color, 0.55) }" />

              <!-- 四架飞机 -->
              <g
                v-for="p in pieceViews"
                :key="p.key"
                class="ludo-piece"
                :class="{ movable: p.movable, leaping: p.key === movingKey, parked: p.step === 0 }"
                :style="{ transform: `translate(${p.x}px, ${p.y}px)` }"
              >
                <circle class="piece-halo" r="0.47" />
                <circle class="piece-shadow" cy="0.065" r="0.315" />
                <circle class="piece-body" r="0.315" :style="{ fill: colorSolid(p.color) }" />
                <ellipse class="piece-glint" cx="-0.075" cy="-0.115" rx="0.13" ry="0.085" transform="rotate(-28)" />
                <path class="piece-glyph" :d="PLANE_PATH" transform="scale(0.0255) translate(-8 -8)" />
                <!-- 同格棋子完全重叠叠成一摞:最上面那架挂数量角标,不然多架叠起来看着只剩一架 -->
                <g v-if="p.stackTop" class="piece-stack-badge">
                  <circle class="piece-stack-bg" cx="0.25" cy="-0.24" r="0.17" />
                  <text class="piece-stack-text" x="0.25" y="-0.16">{{ p.stackSize }}</text>
                </g>
              </g>
            </svg>

            <!-- 透明按钮层:键盘/触屏都用原生 button 取点位,飞行棋的点选才对无障碍友好 -->
            <div class="ludo-hits">
              <button
                v-for="p in pieceViews"
                :key="`hit${p.key}`"
                type="button"
                class="ludo-hit"
                :class="{ movable: p.movable }"
                :style="{ left: `${(p.x / 15) * 100}%`, top: `${(p.y / 15) * 100}%` }"
                :disabled="!p.movable"
                :aria-label="`${seatNameByColor(p.color)} ${p.piece + 1}`"
                @click="onPieceClick(p)"
              ></button>
            </div>
          </div>
        </div>

        <!-- 骰子 / 玩家 / 规则 -->
        <aside class="ludo-side">
          <div class="ludo-status" :class="{ live: isMyTurn && !gameOver }">
            <span v-if="isMyTurn && !gameOver" class="ludo-status-dot"></span>
            <span class="ludo-status-text">{{ statusText }}</span>
          </div>

          <div class="ludo-dice-box">
            <div class="ludo-dice" :class="{ rolling, live: canRoll }" role="img" :aria-label="`${diceFace}`">
              <span v-for="i in 9" :key="i" class="ludo-pip" :class="{ on: pipsOn.has(i - 1) }"></span>
            </div>
            <el-button class="ludo-roll-btn" type="primary" round :disabled="!canRoll" @click="onRollClick">
              {{ $t('games.ludo.roll') }}
            </el-button>
            <div class="ludo-roll-hint">{{ $t('games.ludo.rollHint') }}</div>
          </div>

          <div class="ludo-note" :class="{ show: !!noteText }">{{ noteText || '\u00a0' }}</div>

          <div class="ludo-players">
            <div
              v-for="s in seatViews"
              :key="`p${s.color}`"
              class="ludo-player"
              :class="{ active: s.active, me: s.me }"
              :style="{ '--pc': colorSolid(s.color), '--pc-soft': colorTint(s.color, 0.16) }"
            >
              <span class="lp-dot"></span>
              <span class="lp-name">{{ s.name }}</span>
              <span class="lp-fin">{{ $t('games.ludo.finished', { n: s.finished }) }}</span>
              <span v-if="s.active" class="lp-live"></span>
            </div>
          </div>

          <details class="ludo-rules">
            <summary>{{ $t('games.ludo.rulesTitle') }}</summary>
            <ul>
              <li>{{ $t('games.ludo.ruleRoll') }}</li>
              <li>{{ $t('games.ludo.ruleColor') }}</li>
              <li>{{ $t('games.ludo.rulePlane') }}</li>
              <li>{{ $t('games.ludo.ruleCapture') }}</li>
              <li>{{ $t('games.ludo.ruleHome') }}</li>
            </ul>
          </details>
        </aside>
      </div>

      <!-- 单机封面:选人数开局 -->
      <Transition name="game-overlay">
        <div v-if="!play.active && !state" class="ludo-overlay game-overlay">
          <div class="ludo-cover game-overlay-card">
            <div class="cover-eyebrow">{{ $t('games.ludo.eyebrow') }}</div>
            <div class="cover-title">{{ $t('games.ludo.title') }}</div>
            <div class="cover-desc">{{ $t('games.ludo.desc') }}</div>
            <div class="cover-counts" role="radiogroup" :aria-label="$t('games.ludo.playersLabel')">
              <button
                v-for="n in [2, 3, 4]"
                :key="n"
                type="button"
                class="count-btn"
                role="radio"
                :aria-checked="seatCount === n"
                :class="{ active: seatCount === n }"
                @click="seatCount = n"
              >
                {{ $t('games.ludo.playersCount', { n }) }}
              </button>
            </div>
            <el-button type="primary" round class="cover-start" @click="startSolo">{{ $t('games.ludo.start') }}</el-button>
            <div class="cover-hint">{{ $t('games.ludo.coverHint') }}</div>
          </div>
        </div>
      </Transition>

      <!-- 房间局:等房主发盘面(或房间已结束) -->
      <Transition name="game-overlay">
        <div v-if="play.active && !state" class="ludo-overlay game-overlay">
          <div class="ludo-cover game-overlay-card">
            <div class="cover-title">{{ $t('games.ludo.title') }}</div>
            <div class="cover-hint">{{ play.ended ? $t('games.ludo.roomEnded') : $t('games.ludo.waitHost') }}</div>
            <el-button v-if="play.ended" round type="primary" @click="goRoom">{{ $t('games.room.backRoom') }}</el-button>
          </div>
        </div>
      </Transition>

      <!-- 结算 -->
      <Transition name="game-overlay">
        <div v-if="result" class="ludo-overlay game-overlay">
          <div class="ludo-cover game-overlay-card">
            <div class="result-plane" :style="{ color: colorSolid(result.color) }">
              <svg viewBox="0 0 16 16" aria-hidden="true"><path :d="PLANE_PATH" fill="currentColor" /></svg>
            </div>
            <div class="cover-title">{{ result.mine ? $t('games.ludo.youWin') : $t('games.ludo.nameWins', { name: result.name }) }}</div>
            <div class="cover-hint">{{ $t('games.ludo.resultHint') }}</div>
            <div class="result-actions">
              <el-button v-if="play.active" round type="primary" @click="goRoom">{{ $t('games.room.backRoom') }}</el-button>
              <template v-else>
                <el-button round @click="restart">{{ $t('games.ludo.again') }}</el-button>
                <el-button round type="primary" @click="goBack">{{ $t('games.back') }}</el-button>
              </template>
            </div>
          </div>
        </div>
      </Transition>
    </div>
  </GameShell>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { roomApi } from '@/api'
import GameShell from '@/components/games/GameShell.vue'
import GameRoomBar from '@/components/games/GameRoomBar.vue'
import { useGameExit } from '@/composables/useGameExit'
import { useGameRoomPlay } from '@/composables/useGameRoomPlay'
import { isSpectatorRole } from '@/utils/gameRoom'
import { useChatStore } from '@/stores/chat'
import {
  LUDO_HANGARS,
  LUDO_LANES,
  LUDO_PIECE_COUNT,
  LUDO_RING,
  LUDO_START,
  createLudoState,
  ludoAiPick,
  ludoApplyMove,
  ludoCellColor,
  ludoFinishedCount,
  ludoHangarPad,
  ludoPlaneIndex,
  ludoRoll,
  ludoSeatColors,
  ludoStepPoint,
} from '@/utils/games/ludo'

// 纸飞机图标(Bootstrap send-fill 路径,16×16):盘面徽标 / 起飞格 / 棋子共用一条 path
const PLANE_PATH =
  'M15.964.686a.5.5 0 0 0-.65-.65L.767 5.855H.766l-.452.18a.5.5 0 0 0-.082.887l.41.26.001.002 4.995 3.178 3.178 4.995.002.002.26.41a.5.5 0 0 0 .886-.083l6-15Zm-1.833 1.89L6.637 10.07l-.215-.338a.5.5 0 0 0-.154-.154l-.338-.215 7.494-7.494 1.178-.471-.47 1.178Z'

/** 四色:朱砂 / 琥珀 / 苔绿 / 靛青(与站内主色同一套暖调) */
const LUDO_RGB = ['181, 67, 59', '192, 138, 46', '111, 128, 96', '63, 108, 140']
const colorRgb = (color) => LUDO_RGB[color] || LUDO_RGB[0]
const colorSolid = (color) => `rgb(${colorRgb(color)})`
const colorTint = (color, alpha) => `rgba(${colorRgb(color)}, ${alpha})`

/** 骰子点位:点数 → 3×3 网格里亮起的格 */
const PIPS = {
  1: [[1, 1]],
  2: [
    [0, 0],
    [2, 2],
  ],
  3: [
    [0, 0],
    [1, 1],
    [2, 2],
  ],
  4: [
    [0, 0],
    [0, 2],
    [2, 0],
    [2, 2],
  ],
  5: [
    [0, 0],
    [0, 2],
    [1, 1],
    [2, 0],
    [2, 2],
  ],
  6: [
    [0, 0],
    [0, 2],
    [1, 0],
    [1, 2],
    [2, 0],
    [2, 2],
  ],
}

const router = useRouter()
const { t } = useI18n()
const chatStore = useChatStore()

const shellEl = ref(null)

/** 还没开局时给盘面一份「四色飞机都在停机坪」的预览态,封面背后就是完整盘面 */
const previewState = createLudoState([0, 1, 2, 3].map((color) => ({ color, userId: null, name: '', ai: false })))

// ---------- 对局状态 ----------
const state = ref(null) // 权威状态(引擎产物)
const seatCount = ref(4) // 单机人数
const lastFace = ref(6) // 骰子面(交棒后仍显示最近点数)
const animSteps = ref({}) // 动画中的临时刻度 `${color}:${piece}` → step
const movingKey = ref('')
const animating = ref(false)
const rolling = ref(false)
const note = ref(null)

let aiTimer = null
let rollTimer = null
let noteTimer = null
let animToken = 0
let lastSeq = -1

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

// ---------- 「一起玩」房间(?room=<id>) ----------
const play = useGameRoomPlay({ onSignal: onRoomSignal, onRoom: onRoomChange })

/** 渲染用状态:没开局时用预览盘面(棋子全在机场) */
const viewState = computed(() => state.value || previewState)

const gameOver = computed(() => state.value?.phase === 'ended')
const isMyTurn = computed(() => {
  const st = state.value
  if (!st || st.phase !== 'playing') return false
  const seat = st.seats[st.turn]
  return !!seat && !seat.ai && !seat.auto && String(seat.userId) === String(play.myId)
})
const canRoll = computed(() => isMyTurn.value && state.value?.awaiting === 'roll' && !animating.value && !rolling.value)
const diceFace = computed(() => state.value?.dice || lastFace.value || 6)
const pipsOn = computed(() => new Set((PIPS[diceFace.value] || PIPS[6]).map(([r, c]) => r * 3 + c)))

/** 电脑按出现顺序编号,统一叫「电脑 N」 */
const aiNames = computed(() => {
  const map = {}
  let n = 0
  for (const seat of viewState.value.seats) if (seat.ai) map[seat.color] = ++n
  return map
})

const seatName = (seat) => (seat.ai ? t('games.ludo.aiName', { n: aiNames.value[seat.color] || 1 }) : seat.name || t('games.room.me'))
const seatNameByColor = (color) => {
  const seat = viewState.value.seats.find((s) => s.color === color)
  return seat ? seatName(seat) : ''
}

const myFinished = computed(() => {
  const st = state.value
  if (!st) return 0
  const seat = st.seats.find((s) => !s.ai && String(s.userId) === String(play.myId))
  return seat ? ludoFinishedCount(st, seat.color) : 0
})

const result = computed(() => {
  const st = state.value
  if (!st || st.phase !== 'ended') return null
  const seat = st.seats.find((s) => s.color === st.winner)
  return {
    color: st.winner,
    name: seat ? seatName(seat) : '',
    mine: !!seat && !seat.ai && String(seat.userId) === String(play.myId),
  }
})

const statusText = computed(() => {
  const st = state.value
  if (!st) return ''
  if (st.phase === 'ended') return t('games.ludo.finishedAll')
  const seat = st.seats[st.turn]
  const name = seat ? seatName(seat) : ''
  if (animating.value) return t('games.ludo.moving', { name })
  if (st.awaiting === 'move') return isMyTurn.value ? t('games.ludo.pickPiece') : t('games.ludo.waitMove', { name })
  return isMyTurn.value ? t('games.ludo.yourRoll') : t('games.ludo.waitRoll', { name })
})

const noteText = computed(() => {
  const n = note.value
  if (!n) return ''
  const name = seatNameByColor(n.color)
  if (n.type === 'win') return t('games.ludo.noteWin', { name })
  if (n.captured > 0) return t('games.ludo.noteCapture', { name, n: n.captured })
  if (n.type === 'pass') return t('games.ludo.notePass', { name })
  if (n.type === 'launch') return t('games.ludo.noteLaunch', { name })
  if (n.type === 'color') return t('games.ludo.noteJump', { name })
  if (n.type === 'plane') return t('games.ludo.noteFlight', { name })
  if (n.type === 'bounce') return t('games.ludo.noteBounce', { name, n: n.n })
  return ''
})

const seatViews = computed(() => {
  const st = viewState.value
  return st.seats.map((seat, index) => ({
    color: seat.color,
    name: seatName(seat),
    finished: ludoFinishedCount(st, seat.color),
    active: st.phase === 'playing' && st.turn === index,
    me: !seat.ai && !!seat.userId && String(seat.userId) === String(play.myId),
  }))
})

// ---------- 盘面静态元素 ----------
const hangarViews = computed(() =>
  [0, 1, 2, 3].map((color) => {
    const [x, y] = LUDO_HANGARS[color]
    return {
      color,
      x,
      y,
      active: viewState.value.seats.some((s) => s.color === color),
      pads: [0, 1, 2, 3].map((pad) => {
        const [cx, cy] = ludoHangarPad(color, pad)
        return { pad, cx, cy }
      }),
    }
  }),
)

const ringViews = computed(() =>
  LUDO_RING.map(([x, y], index) => {
    const owner = LUDO_START.indexOf(index)
    const plane = [0, 1, 2, 3].find((color) => ludoPlaneIndex(color) === index)
    return { index, x, y, color: ludoCellColor(index), owner, planeOf: plane == null ? -1 : plane }
  }),
)

const laneViews = computed(() => [0, 1, 2, 3].flatMap((color) => LUDO_LANES[color].map(([x, y], i) => ({ color, i, x, y }))))

/** 航线:从自家航线格沿弧线飞 12 格(弧顶朝盘心,四条拼成风车) */
const planeArcs = computed(() =>
  viewState.value.seats.map((seat) => {
    const from = LUDO_RING[ludoPlaneIndex(seat.color)]
    const to = LUDO_RING[(ludoPlaneIndex(seat.color) + 12) % LUDO_RING.length]
    const ax = from[0] + 0.5
    const ay = from[1] + 0.5
    const bx = to[0] + 0.5
    const by = to[1] + 0.5
    const cx = (ax + bx) / 2 + (7.5 - (ax + bx) / 2) * 0.34
    const cy = (ay + by) / 2 + (7.5 - (ay + by) / 2) * 0.34
    return { color: seat.color, d: `M ${ax} ${ay} Q ${cx} ${cy} ${bx} ${by}` }
  }),
)

const cellStyle = (cell) => {
  if (cell.owner >= 0) return { fill: colorTint(cell.owner, 0.62) }
  if (cell.planeOf >= 0) return { fill: colorTint(cell.planeOf, 0.3) }
  return { fill: colorTint(cell.color, 0.17) }
}

// ---------- 棋子 ----------
const stepOf = (color, piece) => {
  const cursor = animSteps.value[`${color}:${piece}`]
  if (cursor != null) return cursor
  return viewState.value.pieces?.[color]?.[piece] ?? 0
}

/** 我这一步能动的飞机(只有轮到我且等选子时才有) */
const legalByPiece = computed(() => {
  const map = {}
  if (!state.value || state.value.awaiting !== 'move' || !isMyTurn.value) return map
  for (const move of state.value.moves || []) map[move.piece] = move
  return map
})

const pieceViews = computed(() => {
  const list = []
  for (const seat of viewState.value.seats) {
    for (let piece = 0; piece < LUDO_PIECE_COUNT; piece += 1) {
      const step = stepOf(seat.color, piece)
      const point = ludoStepPoint(seat.color, step, piece)
      if (!point) continue
      list.push({
        key: `${seat.color}:${piece}`,
        color: seat.color,
        piece,
        step,
        x: point[0],
        y: point[1],
        movable: !!legalByPiece.value[piece],
        stackSize: 1,
        stackTop: false,
      })
    }
  }
  // 同格叠放:同格棋子完全重叠(叠成一摞),不再环形错开;数量由最上面那架的角标给出
  const cellOf = (item) => `${item.x.toFixed(2)}:${item.y.toFixed(2)}`
  const sizes = new Map()
  for (const item of list) sizes.set(cellOf(item), (sizes.get(cellOf(item)) || 0) + 1)
  for (const item of list) item.stackSize = sizes.get(cellOf(item)) || 1
  // 正在飞的画在最上,其次可点的
  const rank = (item) => (item.key === movingKey.value ? 2 : item.movable ? 1 : 0)
  list.sort((a, b) => rank(a) - rank(b))
  const tops = new Map()
  for (const item of list) tops.set(cellOf(item), item)
  for (const item of list) item.stackTop = item.stackSize > 1 && tops.get(cellOf(item)) === item
  return list
})

// ---------- 走子 / 动画 ----------
/** 逐格播放 lastMove.frames:CSS 过渡负责「挪过去」的插值,这两档只是每格的节拍(要快,但看得清一格一格走) */
const STEP_MS = 100
const LEAP_MS = 360

async function animateMove(next) {
  const move = next?.lastMove
  if (!move || !move.frames?.length) return
  const token = (animToken += 1)
  const key = `${move.color}:${move.piece}`
  movingKey.value = key
  animating.value = true
  for (const frame of move.frames) {
    if (token !== animToken) return
    const leap = !!frame.at
    animSteps.value = { ...animSteps.value, [key]: frame.step }
    await sleep(leap ? LEAP_MS : STEP_MS)
  }
  if (token !== animToken) return
  animSteps.value = {}
  movingKey.value = ''
  animating.value = false
}

async function animateThenApply(next) {
  await animateMove(next)
  // 动画期间可能已收到更新的盘面(房主连走数步),比当前序号旧的快照直接丢弃
  if (state.value && next.seq && next.seq < (state.value.seq || 0)) return
  state.value = next
  afterState(next)
}

/** 落子后的收尾:结算 / 继续驱动 AI */
function afterState(next) {
  if (!next) return
  if (next.phase === 'ended') {
    reportIfWinner(next)
    return
  }
  scheduleAi(520)
}

/** 房间局:胜者自己交卷(先完成者胜,首报即终局) */
function reportIfWinner(next) {
  if (!play.active || play.reported) return
  const seat = next.seats.find((s) => s.color === next.winner)
  if (!seat || seat.ai) return
  if (String(seat.userId) !== String(play.myId)) return
  play.report(1)
}

const submitRoomScore = () => play.report(myFinished.value)

/** 房主权威:把权威状态广播出去(其他人收到后按同一份 frames 播放) */
function publishState(next = state.value) {
  if (!play.active || !play.isHost || !next) return
  play.send('ld:state', { state: next })
}

function doRoll(value) {
  const st = state.value
  if (!st || st.phase !== 'playing' || st.awaiting !== 'roll' || animating.value) return
  const raw = Number(value)
  const face = raw >= 1 && raw <= 6 ? Math.round(raw) : 1 + Math.floor(Math.random() * 6)
  const next = ludoRoll(st, face)
  if (next === st) return
  state.value = next
  publishState(next)
  afterState(next)
}

function commitMove(move) {
  const st = state.value
  // 动画期间 state 还停在上一帧,这时再点一次会拿同一份 moves 重复落子
  if (!st || st.awaiting !== 'move' || animating.value) return
  // 只认引擎给出的合法落点,不信任外部传进来的 to
  const legal = (st.moves || []).find((m) => m.piece === move.piece)
  if (!legal) return
  const next = ludoApplyMove(st, legal)
  if (next === st) return
  publishState(next)
  animateThenApply(next)
}

function onRollClick() {
  if (!canRoll.value) return
  if (play.active && !play.isHost) {
    play.send('ld:act', { to: play.room.hostId, action: { type: 'roll' } })
    return
  }
  doRoll()
}

function onPieceClick(piece) {
  if (!piece.movable || animating.value) return
  if (play.active && !play.isHost) {
    play.send('ld:act', { to: play.room.hostId, action: { type: 'move', piece: piece.piece } })
    return
  }
  const move = (state.value.moves || []).find((m) => m.piece === piece.piece)
  if (move) commitMove(move)
}

// ---------- AI ----------
const canHostRun = () => !play.active || play.isHost

/** 电脑步频放慢:默认带 1.1~2.3s 的可感停顿(调用方显式传短值只用于动画后的快速轮询) */
function scheduleAi(delay) {
  clearTimeout(aiTimer)
  const st = state.value
  if (!st || st.phase !== 'playing' || !canHostRun()) return
  if (!st.seats[st.turn]?.ai && !st.seats[st.turn]?.auto) return
  const wait = delay == null ? 1100 + Math.floor(Math.random() * 1200) : delay
  aiTimer = setTimeout(runAi, wait)
}

function runAi() {
  if (!canHostRun()) return
  const st = state.value
  if (!st || st.phase !== 'playing') return
  if (animating.value || rolling.value) {
    scheduleAi(240)
    return
  }
  if (st.awaiting === 'roll') {
    doRoll()
    scheduleAi()
    return
  }
  const pick = ludoAiPick(st)
  if (pick) commitMove(pick)
}

// ---------- 开局 / 重开 ----------
function resetBoard(next) {
  animToken += 1
  animSteps.value = {}
  movingKey.value = ''
  animating.value = false
  state.value = next
}

function startSolo() {
  const seats = ludoSeatColors(seatCount.value).map((color, i) => ({
    color,
    userId: i === 0 ? play.myId : null,
    name: '',
    ai: i !== 0,
  }))
  resetBoard(createLudoState(seats))
  scheduleAi(700)
}

/** 房间席位:房主占 0 号位(左下),其余按加入顺序,人数不足用 AI 补到 2~4 人 */
function startRoomGame() {
  if (!play.isHost || !play.room) return
  const hostId = play.room.hostId
  const players = (play.room.members || [])
    .filter((m) => !isSpectatorRole(m.role))
    .sort((a, b) => {
      if (String(a.userId) === String(hostId)) return -1
      if (String(b.userId) === String(hostId)) return 1
      return 0
    })
  const colors = ludoSeatColors(Math.max(2, Math.min(4, players.length || 1)))
  const seats = colors.map((color, i) => {
    const member = players[i]
    return { color, userId: member?.userId ?? null, name: member?.name || '', ai: !member }
  })
  resetBoard(createLudoState(seats))
  lastSeq = -1
  publishState()
  scheduleAi(900)
}

const restart = () => startSolo()

function goRoom() {
  if (play.room?.id) router.push(`/games/room/${play.room.id}`)
  else router.push('/games')
}

// ---------- 房间信令 ----------
function onRoomSignal(data) {
  if (!data) return
  const fromHost = String(data.from) === String(play.room?.hostId)
  if (data.event === 'ld:state' && !play.isHost) {
    if (!fromHost) return // 只认房主发来的盘面,别人伪造的同名信令直接丢弃
    receiveState(data.state)
    return
  }
  if (data.event === 'ld:act' && play.isHost) {
    handleGuestAction(data)
    return
  }
  // 托管开关:访客请求房主把自己席位交给 AI(或收回)
  if (data.event === 'auto' && play.isHost) {
    const seat = state.value?.seats.find((s) => String(s.userId) === String(data.from))
    if (seat && !seat.ai) {
      seat.auto = !!data.on
      state.value.seq = (state.value.seq || 0) + 1
      publishState()
      scheduleAi(320)
    }
    return
  }
  // 访客刚进房就可能发 sync,这时房主还没开桌:顺手把桌开了,免得一直等
  if (data.event === 'sync' && play.isHost) {
    if (!state.value && play.status === 'PLAYING') startRoomGame()
    else publishState()
  }
}

/** 本机托管开关:房主直接生效;访客发信令给房主 */
function onAutoToggle(on) {
  if (!play.active) return
  if (play.isHost) {
    const seat = state.value?.seats.find((s) => !s.ai && String(s.userId) === String(play.myId))
    if (!seat) return
    seat.auto = !!on
    state.value.seq = (state.value.seq || 0) + 1
    publishState()
    scheduleAi(320)
  } else {
    play.send('auto', { to: play.room.hostId, on: !!on })
  }
}

/** 房主收到的远端意图(只接受当前座位本人发来的;托管中由 AI 代打,忽略其操作) */
function handleGuestAction(data) {
  const st = state.value
  if (!st || st.phase !== 'playing') return
  const seat = st.seats[st.turn]
  if (!seat || seat.ai || seat.auto || String(seat.userId) !== String(data.from)) return
  const action = data.action || {}
  if (action.type === 'roll') doRoll()
  else if (action.type === 'move') {
    const move = (st.moves || []).find((m) => m.piece === Number(action.piece))
    if (move) commitMove(move)
  }
}

async function receiveState(next) {
  if (!next || typeof next !== 'object' || !Array.isArray(next.seats)) return
  const seq = Number(next.seq) || 0
  if (seq <= lastSeq) return
  lastSeq = seq
  if (next.lastMove?.frames?.length && state.value) await animateMove(next)
  state.value = next
  afterState(next)
}

function onRoomChange(next) {
  if (!next || !play.active) return
  if (!play.isHost || !state.value || state.value.phase !== 'playing') return
  // 席位交接:退出/转观战的人位交给 AI;新参战者(如让位接手)优先接管刚空出的席
  const players = (next.members || []).filter((m) => !isSpectatorRole(m.role))
  let changed = false
  const freed = []
  for (const seat of state.value.seats) {
    if (seat.ai || !seat.userId) continue
    if (players.some((m) => String(m.userId) === String(seat.userId))) continue
    seat.ai = true
    seat.auto = false
    seat.name = ''
    changed = true
    freed.push(seat)
  }
  const seated = new Set(state.value.seats.filter((s) => !s.ai).map((s) => String(s.userId)))
  for (const m of players) {
    if (seated.has(String(m.userId))) continue
    const free = freed.shift() || state.value.seats.find((s) => s.ai)
    if (!free) break
    free.ai = false
    free.auto = false
    free.userId = m.userId
    free.name = m.name
    changed = true
    seated.add(String(m.userId))
  }
  if (changed) {
    // 人选变了但盘面本身没走子,序号要自己抬一格,否则访客按 seq 去重会收不到
    state.value.seq = (state.value.seq || 0) + 1
    publishState()
    scheduleAi(320)
  }
}

/** 房间进入对局:房主开桌并广播,其他人先占位再主动要一次全量同步 */
watch(
  () => [play.active, play.status, play.isHost],
  () => {
    if (!play.active || play.status !== 'PLAYING') return
    if (play.isHost) {
      if (!state.value || state.value.phase === 'ended') startRoomGame()
      return
    }
    // 新一局(房主重开后 seq 归零)要清掉上一局状态,否则会按旧 seq 丢掉新盘面
    if (!state.value || state.value.phase === 'ended') {
      resetBoard(null)
      lastSeq = -1
      play.requestSync()
    }
  },
  { immediate: true },
)

/** WS 重连后补一次同步,否则盘面停在断线前 */
watch(
  () => chatStore.socketStatus,
  (status) => {
    if (status !== 'open' || !play.active || play.isHost) return
    lastSeq = -1
    play.requestSync()
  },
)

/** 骰子翻滚:任何一端的点数变化都触发同一段动画 */
watch(
  () => state.value?.rollSeq,
  (value) => {
    if (!value) return
    rolling.value = true
    clearTimeout(rollTimer)
    rollTimer = setTimeout(() => {
      rolling.value = false
    }, 480)
  },
)

watch(
  () => state.value?.dice,
  (value) => {
    if (value) lastFace.value = value
  },
)

watch(
  () => state.value?.note,
  (value) => {
    if (!value) return
    note.value = value
    clearTimeout(noteTimer)
    noteTimer = setTimeout(() => {
      note.value = null
    }, 2600)
  },
)

async function goBack() {
  if (!play.active || !play.room?.id) {
    // 单机:固定回上一级(小游戏列表),不按浏览历史回到上一页
    // (手机沉浸态的浮动返回键也走这里)
    router.push('/games')
    return
  }
  const roomId = play.room.id
  // 实时权威对局:房主退出即结束(否则其他人等不到掷骰),访客退出则离房把人位交给 AI
  if (play.room.status === 'PLAYING') {
    try {
      await (play.isHost ? roomApi.end(roomId) : roomApi.leave(roomId))
    } catch {
      // 房间已结束时忽略
    }
  }
  router.push(play.isHost ? `/games/room/${roomId}` : '/games')
}

// 手机沉浸态:左上角浮动返回键优先走本页退出逻辑,房间局才会先退房/结束房间
useGameExit(goBack)

onBeforeUnmount(() => {
  animToken += 1
  clearTimeout(aiTimer)
  clearTimeout(rollTimer)
  clearTimeout(noteTimer)
})
</script>

<style scoped>
.ludo-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 14px clamp(10px, 2vw, 22px) 18px;
  --ludo-paper: #f8f0dd;
  --ludo-cell-line: rgba(58, 46, 34, 0.07);
  /* 棋盘 1:1 恒定方形:宽取「撑满卡片」与「视口高扣除顶栏页脚后折算」较小者;
     230px ≈ 顶栏 + 页脚 + 页面/卡片内边距 + 组间距 + 页底留白(方形盘比 16:10 桌多扣);
     「一起玩」横幅实测高度走 --room-bar-h */
  --ludo-board-max: min(620px, calc(100cqh - 150px - var(--room-bar-h, 0px)));
}

html.dark .ludo-card {
  --ludo-paper: #1b2536;
  --ludo-cell-line: rgba(255, 255, 255, 0.06);
}

.ludo-layout {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  gap: clamp(12px, 2vw, 24px);
}

.ludo-board-wrap {
  display: flex;
  justify-content: center;
  min-width: 0;
  flex: 1 1 auto;
}

.ludo-board-stage {
  position: relative;
  width: min(100%, var(--ludo-board-max));
  aspect-ratio: 1 / 1;
  border-radius: 20px;
}

.ludo-board {
  display: block;
  width: 100%;
  height: 100%;
  border-radius: 20px;
  filter: drop-shadow(0 10px 26px rgba(58, 46, 34, 0.16));
  transition: filter 0.3s var(--ease-soft);
}

.ludo-board-stage.live .ludo-board {
  filter: drop-shadow(0 10px 26px rgba(58, 46, 34, 0.18)) drop-shadow(0 0 18px rgba(var(--color-brand-rgb), 0.28));
}

/* 透明点选层:棋子位置的圆按钮(原生 button,键盘可直接 Tab + Enter) */
.ludo-hits {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.ludo-hit {
  position: absolute;
  width: 6.8%;
  height: 6.8%;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  transform: translate(-50%, -50%);
  pointer-events: none;
}

.ludo-hit.movable {
  pointer-events: auto;
  cursor: pointer;
}

.ludo-hit:focus-visible {
  outline: 2px solid rgba(var(--color-brand-rgb), 0.85);
  outline-offset: 1px;
}

/* ---------- 盘面 ---------- */
.bd-paper {
  fill: var(--ludo-paper);
  stroke: rgba(58, 46, 34, 0.1);
  stroke-width: 0.03;
}

.bd-hangar-plate {
  stroke-width: 0.035;
}

.bd-hangar.dim {
  opacity: 0.45;
}

.bd-pad {
  fill: var(--ludo-paper);
  stroke-width: 0.045;
  stroke-dasharray: 0.14 0.12;
}

.cell-fill {
  stroke: var(--ludo-cell-line);
  stroke-width: 0.025;
}

.cell-fill.start {
  stroke-width: 0.05;
  stroke: rgba(255, 255, 255, 0.55);
}

.cell-fill.plane {
  stroke-width: 0.05;
  stroke-dasharray: 0.16 0.12;
}

.bd-plane-icon {
  fill: rgba(255, 255, 255, 0.92);
  pointer-events: none;
}

.bd-plane-mark {
  opacity: 0.85;
  pointer-events: none;
}

.bd-center {
  fill: var(--ludo-paper);
  stroke: rgba(58, 46, 34, 0.12);
  stroke-width: 0.035;
}

.bd-lane {
  stroke-width: 0.03;
}

.bd-emblem {
  fill: rgba(var(--color-brand-rgb), 0.9);
  pointer-events: none;
}

.bd-arc {
  fill: none;
  stroke-width: 0.055;
  stroke-dasharray: 0.2 0.16;
  stroke-linecap: round;
  pointer-events: none;
}

/* ---------- 棋子 ---------- */
/* 位移用 CSS transform(不是 SVG transform 属性,属性变化不会触发过渡),逐格过渡才有「一格一格挪」的观感 */
.ludo-piece {
  transition: transform 0.1s linear;
  pointer-events: none;
}

.ludo-piece.leaping {
  transition-duration: 0.34s;
  transition-timing-function: cubic-bezier(0.3, 0.9, 0.4, 1);
}

.piece-shadow {
  fill: rgba(40, 30, 22, 0.22);
}

.piece-body {
  stroke: rgba(255, 255, 255, 0.75);
  stroke-width: 0.055;
}

.piece-glint {
  fill: rgba(255, 255, 255, 0.5);
}

.piece-glyph {
  fill: rgba(255, 255, 255, 0.94);
}

.piece-halo {
  fill: none;
  stroke: rgba(var(--color-brand-rgb), 0.9);
  stroke-width: 0.05;
  opacity: 0;
}

/* 叠起来的同格棋子:右上角数量角标(只挂一摞里最上面那架) */
.piece-stack-bg {
  fill: rgba(32, 26, 20, 0.62);
  stroke: rgba(255, 255, 255, 0.8);
  stroke-width: 0.028;
}

.piece-stack-text {
  fill: #fff;
  font-size: 0.24;
  font-weight: 700;
  text-anchor: middle;
}

.ludo-piece.parked .piece-body {
  opacity: 0.86;
}

.ludo-piece.movable .piece-halo {
  opacity: 1;
  animation: ludo-halo 1.5s ease-in-out infinite;
}

.ludo-piece.leaping .piece-halo {
  opacity: 0.7;
  animation: none;
}

@keyframes ludo-halo {
  0%,
  100% {
    opacity: 0.35;
  }
  50% {
    opacity: 1;
  }
}

/* ---------- 侧栏 ---------- */
.ludo-side {
  flex: 0 0 244px;
  width: 244px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  /* 左右布局时侧栏随棋盘同高封顶:矮视口(手机横屏/矮窗)下状态+骰子仍在首屏,
     玩家明细在侧栏内部滚动,页面不破一屏;纵向堆叠分支放开 */
  max-height: var(--ludo-board-max, 620px);
  overflow-y: auto;
}

.ludo-status {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 42px;
  padding: 10px 14px;
  border-radius: 12px;
  background: rgba(var(--color-brand-rgb), 0.09);
  border: 1px solid var(--color-border);
  font-size: 13px;
  color: var(--color-text-secondary);
}

.ludo-status.live {
  color: var(--color-accent);
  border-color: rgba(var(--color-brand-rgb), 0.35);
  background: rgba(var(--color-brand-rgb), 0.14);
}

.ludo-status-text {
  line-height: 1.35;
}

.ludo-status-dot {
  flex: 0 0 auto;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-accent);
  box-shadow: 0 0 0 4px rgba(var(--color-brand-rgb), 0.18);
  animation: ludo-halo 1.6s ease-in-out infinite;
}

.ludo-dice-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 16px 12px 12px;
  border-radius: 14px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow);
}

.ludo-dice {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  grid-template-rows: repeat(3, 1fr);
  gap: 5px;
  width: 62px;
  height: 62px;
  padding: 9px;
  border-radius: 14px;
  background: linear-gradient(150deg, #fffdf7 0%, #f0e6d1 100%);
  border: 1px solid rgba(58, 46, 34, 0.14);
  box-shadow:
    inset 0 2px 0 rgba(255, 255, 255, 0.9),
    inset 0 -3px 6px rgba(58, 46, 34, 0.12),
    0 4px 10px rgba(58, 46, 34, 0.14);
}

.ludo-dice.live {
  box-shadow:
    inset 0 2px 0 rgba(255, 255, 255, 0.9),
    inset 0 -3px 6px rgba(58, 46, 34, 0.12),
    0 0 0 3px rgba(var(--color-brand-rgb), 0.28),
    0 6px 16px rgba(58, 46, 34, 0.2);
}

.ludo-dice.rolling {
  animation: ludo-dice-roll 0.48s cubic-bezier(0.3, 0.9, 0.4, 1);
}

@keyframes ludo-dice-roll {
  0% {
    transform: rotate(0deg) scale(1);
  }
  35% {
    transform: rotate(-16deg) scale(1.12);
  }
  70% {
    transform: rotate(10deg) scale(0.96);
  }
  100% {
    transform: rotate(0deg) scale(1);
  }
}

.ludo-pip {
  border-radius: 50%;
  background: transparent;
  transition: background 0.12s var(--ease-soft);
}

.ludo-pip.on {
  background: var(--color-accent);
  box-shadow: inset 0 -1px 1px rgba(0, 0, 0, 0.25);
}

.ludo-roll-btn {
  width: 100%;
}

.ludo-roll-hint {
  font-size: 11px;
  line-height: 1.4;
  color: var(--color-text-tertiary);
  text-align: center;
}

.ludo-note {
  min-height: 18px;
  font-size: 12px;
  line-height: 1.4;
  text-align: center;
  color: var(--color-accent);
  opacity: 0;
  transition: opacity 0.24s var(--ease-soft);
}

.ludo-note.show {
  opacity: 1;
}

.ludo-players {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ludo-player {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 12px;
  border-radius: 11px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  font-size: 13px;
  color: var(--color-text-secondary);
  transition:
    border-color 0.24s var(--ease-soft),
    background 0.24s var(--ease-soft);
}

.ludo-player.active {
  border-color: var(--pc);
  background: var(--pc-soft);
  color: var(--color-text);
}

.ludo-player.me .lp-name {
  font-weight: 600;
  color: var(--color-text);
}

.lp-dot {
  flex: 0 0 auto;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--pc);
  box-shadow: 0 0 0 3px var(--pc-soft);
}

.lp-name {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lp-fin {
  flex: 0 0 auto;
  font-size: 11px;
  color: var(--color-text-tertiary);
}

.lp-live {
  flex: 0 0 auto;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--pc);
  animation: ludo-halo 1.4s ease-in-out infinite;
}

.ludo-rules {
  padding: 10px 12px;
  border-radius: 11px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.ludo-rules summary {
  cursor: pointer;
  font-size: 12px;
  color: var(--color-text-secondary);
  outline: none;
}

.ludo-rules ul {
  margin: 8px 0 0;
  padding-left: 16px;
  display: flex;
  flex-direction: column;
  gap: 5px;
  line-height: 1.45;
}

/* ---------- 浮层 ---------- */
.ludo-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  border-radius: inherit;
  background: var(--overlay-scrim);
  backdrop-filter: blur(var(--overlay-blur));
  -webkit-backdrop-filter: blur(var(--overlay-blur));
  z-index: var(--z-content);
}

.ludo-cover {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 26px 28px;
  width: min(100%, 380px);
  border-radius: 18px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-hover);
  text-align: center;
}

.cover-eyebrow {
  font-size: 11px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: var(--color-text-tertiary);
}

.cover-title {
  font-size: 21px;
  font-weight: 700;
  color: var(--color-text);
}

.cover-desc {
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-secondary);
}

.cover-counts {
  display: flex;
  gap: 8px;
}

.count-btn {
  min-width: 62px;
  padding: 7px 12px;
  border-radius: 999px;
  border: 1px solid var(--color-border);
  background: transparent;
  font-size: 13px;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition:
    color 0.2s var(--ease-soft),
    border-color 0.2s var(--ease-soft),
    background 0.2s var(--ease-soft);
}

.count-btn:hover {
  color: var(--color-accent);
  border-color: rgba(var(--color-brand-rgb), 0.45);
}

.count-btn.active {
  background: rgba(var(--color-brand-rgb), 0.14);
  border-color: rgba(var(--color-brand-rgb), 0.55);
  color: var(--color-text);
  font-weight: 600;
}

.cover-start {
  min-width: 148px;
}

.cover-hint {
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-tertiary);
}

.result-plane {
  width: 54px;
  height: 54px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 16px;
  background: rgba(var(--color-brand-rgb), 0.12);
}

.result-plane svg {
  width: 30px;
  height: 30px;
}

.result-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
}

/* ---------- 窄屏(仅竖屏堆叠;横屏窄窗保持左右布局,靠 --ludo-board-max 高度约束保一屏) ---------- */
@media (max-width: 860px) and (orientation: portrait) {
  .ludo-layout {
    flex-direction: column;
    align-items: stretch;
  }

  /* 纵向堆叠:棋盘再扣侧栏(状态+骰子+玩家压缩态实测约 308px)与顶栏页脚约 475px,
     横幅走 --room-bar-h;堆叠态侧栏全宽展开,不封顶 */
  .ludo-card {
    --ludo-board-max: min(620px, calc(100cqh - 395px - var(--room-bar-h, 0px)));
  }

  .ludo-side {
    flex: 1 1 auto;
    width: auto;
    max-height: none;
    overflow-y: visible;
  }

  .ludo-dice-box {
    flex-direction: row;
    align-items: center;
    justify-content: center;
    gap: 14px;
    padding: 12px;
  }

  .ludo-roll-btn {
    width: auto;
    min-width: 108px;
  }

  .ludo-roll-hint {
    display: none;
  }

  .ludo-players {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .ludo-card {
    padding: 10px 8px 14px;
  }

  .lp-fin {
    display: none;
  }

  .ludo-dice {
    width: 54px;
    height: 54px;
    padding: 8px;
  }
}

/* 矮视口(手机横屏):侧栏内部滚动时骰子排到最前,掷骰动作始终在首屏可达 */
@media (max-height: 500px) {
  .ludo-dice-box {
    order: -1;
  }
}

/* 手机沉浸态:页头/底栏/游戏工具栏都不在布局里了,棋盘按真实可用高度放大 */
:global(html.game-mobile-immersive) .ludo-card {
  --ludo-board-max: min(620px, calc(100cqh - 16px - var(--room-bar-h, 0px)));
}

@media (max-width: 860px) and (orientation: portrait) {
  :global(html.game-mobile-immersive) .ludo-card {
    /* 竖屏堆叠:只扣卡片内边距 + 侧栏(状态/骰子/玩家/规则)实测高度 */
    --ludo-board-max: min(620px, calc(100cqh - 340px - var(--room-bar-h, 0px)));
  }
}
</style>
