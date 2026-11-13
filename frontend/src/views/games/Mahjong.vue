<!-- 麻将(推倒胡简化版):单机为玩家 vs 三位 AI,「一起玩」房间为 4 席真人(空位 AI 补)。
     规则内核在 utils/games/mahjong.js(判胡/听牌/碰/计番/AI 选牌都是纯函数);
     本页做发牌/回合驱动/交互与结算展示,单机赢一局走统一 H5 奖励通道(每日限次)。
     房间局:房主权威跑牌局,公共盘面广播、每个人只收到自己的手牌,其余三家只看剩牌数。 -->
<template>
  <div ref="pageEl" class="page game-page">
    <PageToolbar root-class="game-topbar" :holder-margin="0" always>
      <GameTopActions
        :is-fullscreen="isFullscreen"
        :wide="isWide"
        :fullscreen-label="$t('games.mahjong.fullscreen')"
        :exit-label="$t('games.mahjong.exitFullscreen')"
        @back="goBack"
        @fullscreen="toggleFullscreen"
        @wide="toggleWide"
      />
    </PageToolbar>

    <div ref="gameCardEl" class="game-card game-surface mahjong-card" @contextmenu.prevent @dblclick.prevent>
      <GameRoomBar
        v-if="play.active"
        :room="play.room"
        :is-host="play.isHost"
        :score="myScore"
        :reported="play.reported"
        :ended="play.ended"
        :result="play.spec?.result"
        :my-id="play.myId"
        exit-ends-room
        @submit="submitRoomScore"
        @leave="play.leave"
      />

      <!-- 封面(仅单机;房间局由房主直接开牌) -->
      <div v-if="phase === 'idle' && !play.active" class="cover">
        <h1 class="cover-title">{{ $t('games.mahjong.title') }}</h1>
        <div class="cover-desc">{{ $t('games.mahjong.desc') }}</div>
        <button class="start-btn" type="button" @click="startGame">{{ $t('games.mahjong.start') }}</button>
        <div v-if="rounds > 0" class="cover-stats">{{ $t('games.mahjong.roundStats', { win: playerWins, lose: aiWins }) }}</div>
        <div class="cover-hint">{{ $t('games.mahjong.coverHint') }}</div>
      </div>

      <!-- 牌桌 -->
      <template v-else>
        <div v-if="waitingHost" class="table-waiting">{{ $t('games.mahjong.waitingHost') }}</div>
        <template v-else>
          <!-- 对手:名字 + 剩牌 + 碰出的面子 + 弃牌河 -->
          <div class="opponents">
            <div v-for="s in opponents" :key="s.seat" class="opponent" :class="{ active: turn === s.seat && phase === 'playing' }">
              <div class="opponent-head">
                <span class="opponent-name">{{ seatName(s.seat) }}</span>
                <span class="opponent-count">{{ seatTileCount(s.seat) }}</span>
              </div>
              <div v-if="melds[s.seat]?.length" class="melds-row">
                <span v-for="(m, i) in melds[s.seat]" :key="'m' + i" class="meld-group" :title="$t('games.mahjong.pong')">
                  <span v-for="n in 3" :key="n" class="tile mini" :class="tileClass(m.kind)"><TileFace :kind="m.kind" /></span>
                </span>
              </div>
              <div class="opponent-river">
                <span v-for="(kind, i) in rivers[s.seat]" :key="i" class="tile mini" :class="tileClass(kind)">
                  <TileFace :kind="kind" />
                </span>
              </div>
            </div>
          </div>

          <!-- 中央信息:牌墙 / 最近一张弃牌 -->
          <div class="table-center">
            <span class="wall-count">{{ $t('games.mahjong.wallLeft', { n: wallLeft }) }}</span>
            <span class="turn-label">{{ turnLabel }}</span>
            <span v-if="lastDiscard" class="last-discard tile" :class="tileClass(lastDiscard.kind)">
              <TileFace :kind="lastDiscard.kind" />
            </span>
          </div>

          <!-- 自己的手牌(观战只看看公共盘面) -->
          <div v-if="!play.isSpectator" class="my-side" :class="{ active: myTurn }">
            <div v-if="myMelds.length" class="melds-row my-melds">
              <span v-for="(m, i) in myMelds" :key="'mm' + i" class="meld-group" :title="$t('games.mahjong.pong')">
                <span v-for="n in 3" :key="n" class="tile mini" :class="tileClass(m.kind)"><TileFace :kind="m.kind" /></span>
              </span>
            </div>
            <div class="my-river">
              <span v-for="(kind, i) in myRiver" :key="i" class="tile mini" :class="tileClass(kind)">
                <TileFace :kind="kind" />
              </span>
            </div>
            <div class="hand-row">
              <button
                v-for="tile in myHandView"
                :key="tile.id"
                type="button"
                class="tile hand"
                :class="[tileClass(tile.kind), { drawn: tile.justDrawn, dim: phase !== 'playing' }]"
                :disabled="!canDiscardNow"
                @click="onTileClick(tile)"
              >
                <TileFace :kind="tile.kind" />
              </button>
            </div>
            <div class="my-actions">
              <el-button v-if="canSelfWin" type="primary" size="default" round @click="declareSelfWin">
                {{ $t('games.mahjong.selfWin') }}
              </el-button>
              <template v-if="claimPrompt">
                <el-button type="primary" size="default" round @click="answerClaim(true)">
                  {{ claimPrompt === 'win' ? $t('games.mahjong.claimWin') : $t('games.mahjong.pong') }}
                </el-button>
                <el-button size="default" round @click="answerClaim(false)">{{ $t('games.mahjong.pass') }}</el-button>
              </template>
              <span class="my-hint">{{ actionHint }}</span>
            </div>
            <!-- 听牌提示:摸牌后建议打哪张、弃后听哪几张;听牌时显示当前听口 -->
            <div v-if="advice" class="advice-row">
              <template v-if="advice.discard != null">
                <span class="advice-label">{{ $t('games.mahjong.adviceDiscard') }}</span>
                <span class="tile mini" :class="tileClass(advice.discard)"><TileFace :kind="advice.discard" /></span>
              </template>
              <span class="advice-label">{{ $t('games.mahjong.listening') }}</span>
              <span v-for="k in advice.wins" :key="k" class="tile mini" :class="tileClass(k)"><TileFace :kind="k" /></span>
            </div>
          </div>
        </template>

        <!-- 结算浮层 -->
        <Transition name="game-overlay">
          <div v-if="phase === 'ended'" class="overlay game-overlay">
            <div class="overlay-card game-overlay-card">
              <div class="overlay-icon">{{ result?.winner === mySeat ? '🎉' : result?.draw ? '🀄' : '😌' }}</div>
              <div class="overlay-title">
                <template v-if="!result"></template>
                <template v-else-if="result.draw">{{ $t('games.mahjong.drawGame') }}</template>
                <template v-else-if="result.winner === mySeat">{{ $t('games.mahjong.youWin') }}</template>
                <template v-else>{{ $t('games.mahjong.seatWins', { name: result.winnerName }) }}</template>
              </div>
              <div v-if="result && !result.draw" class="overlay-score">
                {{ $t('games.mahjong.scoreLine', { score: result.score }) }}
              </div>
              <div v-if="result && !result.draw && result.fanKeys.length" class="overlay-fans">
                <el-tag v-for="f in result.fanKeys" :key="f" size="small" effect="plain">{{ $t(`games.mahjong.fans.${f}`) }}</el-tag>
              </div>
              <div v-if="result && result.winner === mySeat && result.handTiles?.length" class="overlay-hand">
                <span v-for="(kind, i) in result.handTiles" :key="i" class="tile mini" :class="tileClass(kind)">
                  <TileFace :kind="kind" />
                </span>
              </div>
              <div v-if="rewardMsg" class="overlay-reward">{{ rewardMsg }}</div>
              <div v-if="play.active && play.status === 'PLAYING'" class="overlay-wait">{{ $t('games.mahjong.waitSettle') }}</div>
              <el-button type="primary" size="small" round @click="onResultAction">
                {{ play.active ? $t('games.room.backRoom') : $t('games.mahjong.again') }}
              </el-button>
            </div>
          </div>
        </Transition>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, h, inject, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { gameApi, roomApi } from '@/api'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import PageToolbar from '@/components/PageToolbar.vue'
import GameTopActions from '@/components/games/GameTopActions.vue'
import GameRoomBar from '@/components/games/GameRoomBar.vue'
import { useGameView } from '@/composables/useGameView'
import { useGameRoomPlay } from '@/composables/useGameRoomPlay'
import { isSpectatorRole } from '@/utils/gameRoom'
import { useChatStore } from '@/stores/chat'
import {
  canMahjongWin,
  createMahjongWall,
  mahjongAiChooseDiscard,
  mahjongCounts,
  mahjongHandScore,
  mahjongKind,
  mahjongTenpai,
  sortMahjongTiles,
} from '@/utils/games/mahjong'

const router = useRouter()
const { t } = useI18n()
const sunLight = inject(SUN_LIGHT_KEY, null)
const chatStore = useChatStore()

// 牌面组件:数字 + 花色(内联函数式组件,免一个单文件)
const TileFace = (props) => {
  const kind = props.kind
  if (kind >= 27) {
    const honorKeys = ['east', 'south', 'west', 'north', 'zhong', 'fa', 'bai']
    const zhongRed = kind === 31
    return h(
      'span',
      { class: ['tile-face', 'honor', { red: zhongRed, green: kind === 32, blue: kind === 33 }] },
      t(`games.mahjong.honor.${honorKeys[kind - 27]}`),
    )
  }
  const num = (kind % 9) + 1
  const suitKey = ['w', 't', 's'][Math.floor(kind / 9)]
  return h('span', { class: ['tile-face'] }, [
    h('span', { class: 'tile-num' }, String(num)),
    h('span', { class: 'tile-suit' }, t(`games.mahjong.suit.${suitKey}`)),
  ])
}
TileFace.props = { kind: { type: Number, required: true } }

const pageEl = ref(null)
const gameCardEl = ref(null)
const { isFullscreen, isWide, toggleFullscreen, toggleWide } = useGameView({ page: pageEl, stage: gameCardEl })

// ---------- 「一起玩」房间(?room=<id>) ----------
// 房主权威:房主跑完整牌局,公共状态广播给全房,手牌按人定向下发(其余人只看剩牌数),
// 其他人的出牌/碰/胡都作为操作意图发给房主执行。空位由 AI 补满 4 席。
const play = useGameRoomPlay({ onSignal: onRoomSignal, onRoom: onRoomChange })
const isGuest = computed(() => play.active && !play.isHost)

// ---------- 对局状态 ----------
const phase = ref('idle') // idle | playing | ended
const turn = ref(0) // 0..3 座次
const seatList = ref([]) // [{ seat, userId, name, ai }]
const hands = ref([[], [], [], []]) // 暗手牌实例 id;访客只有自己那格
const guestHand = ref([]) // 访客收到的本人手牌
const melds = ref([[], [], [], []]) // 碰出的面子 [{ kind, from }]
const rivers = ref([[], [], [], []]) // 弃牌 kind
const wall = ref([])
const wallCount = ref(0)
const handCounts = ref([0, 0, 0, 0])
const lastDiscard = ref(null) // { kind, from }
const drawnTileId = ref(null)
const canSelfWin = ref(false)
const localClaim = ref(null) // 房主/单机本座收到的碰胡询问:'win' | 'pong' | null
const guestClaim = ref(null) // 访客收到的碰胡询问
const busyAi = ref(false)
const result = ref(null)
const rewardMsg = ref('')
const playerWins = ref(0)
const aiWins = ref(0)
const rounds = ref(0)

let session = 0 // 会话令牌:重开/离开时旧循环自行退出
const turnWaiters = new Map() // seat -> resolve(action)
const claimWaiters = new Map() // seat -> resolve('claim' | 'pass')

const myId = computed(() => play.myId)
const mySeat = computed(() => {
  const idx = seatList.value.findIndex((s) => !s.ai && String(s.userId) === String(myId.value))
  if (idx >= 0) return idx
  // 观战没有席位:不占座、不显示自己的手牌,只看公共盘面
  return play.isSpectator ? -1 : 0
})
const myTurn = computed(() => phase.value === 'playing' && turn.value === mySeat.value)
const myHandTiles = computed(() => (isGuest.value ? guestHand.value : hands.value[mySeat.value] || []))
const myMelds = computed(() => melds.value[mySeat.value] || [])
const myRiver = computed(() => rivers.value[mySeat.value] || [])
const claimPrompt = computed(() => guestClaim.value || localClaim.value)
const waitingHost = computed(() => isGuest.value && phase.value === 'playing' && !result.value && handCounts.value.every((n) => !n))
const wallLeft = computed(() => (isGuest.value ? wallCount.value : wall.value.length))
const seatTileCount = (seat) => (isGuest.value ? handCounts.value[seat] || 0 : hands.value[seat]?.length || 0)
const myScore = computed(() => {
  if (!result.value || result.value.draw) return 0
  return result.value.winner === mySeat.value ? result.value.score : 0
})
const canDiscardNow = computed(
  () => phase.value === 'playing' && myTurn.value && !claimPrompt.value && !busyAi.value && myHandTiles.value.length % 3 === 2,
)

const seatName = (seat) => {
  const s = seatList.value.find((x) => x.seat === seat)
  if (!s) return ''
  if (!s.ai) return s.name || t('games.room.me')
  const aiSeats = seatList.value.filter((x) => x.ai).map((x) => x.seat)
  const ordinal = aiSeats.indexOf(seat)
  return t(`games.mahjong.ai${Math.min(ordinal + 1, 3)}`)
}
const opponents = computed(() => seatList.value.filter((s) => s.seat !== mySeat.value))
const myHandView = computed(() =>
  sortMahjongTiles(myHandTiles.value).map((id) => ({ id, kind: mahjongKind(id), justDrawn: id === drawnTileId.value })),
)

const turnLabel = computed(() => {
  if (phase.value !== 'playing') return ''
  if (turn.value === mySeat.value) return t('games.mahjong.yourTurn')
  const name = seatName(turn.value)
  const seat = seatList.value.find((s) => s.seat === turn.value)
  return t(seat?.ai ? 'games.mahjong.aiTurn' : 'games.mahjong.playerTurn', { name })
})

const actionHint = computed(() => {
  if (phase.value !== 'playing') return ''
  if (canSelfWin.value) return t('games.mahjong.selfWinHint')
  if (claimPrompt.value === 'win') return t('games.mahjong.claimHint')
  if (claimPrompt.value === 'pong') return t('games.mahjong.pongHint')
  if (myTurn.value) return t('games.mahjong.discardHint')
  if (turn.value === mySeat.value) return ''
  return t('games.mahjong.waitOthers')
})

/**
 * 听牌提示(纯函数 computed):轮到自己且暗手为 14-3×碰 张时,
 * 枚举每张可弃牌算「弃后听几张」,取听口最宽的;13-3×碰 张时显示当前听口。
 */
const advice = computed(() => {
  if (phase.value !== 'playing' || turn.value !== mySeat.value || busyAi.value) return null
  if (canSelfWin.value || claimPrompt.value) return null
  const hand = myHandTiles.value
  const open = myMelds.value.length
  const counts = mahjongCounts(hand)
  if (hand.length === 14 - open * 3) {
    let best = null
    for (let k = 0; k < 34; k++) {
      if (!counts[k]) continue
      const trial = [...counts]
      trial[k] -= 1
      const wins = mahjongTenpai(trial, open)
      if (!best || wins.length > best.wins.length) best = { discard: k, wins }
    }
    return best
  }
  if (hand.length === 13 - open * 3) {
    return { discard: null, wins: mahjongTenpai(counts, open) }
  }
  return null
})

const tileClass = (kind) => {
  if (kind >= 27) return `honor-${['east', 'south', 'west', 'north', 'zhong', 'fa', 'bai'][kind - 27]}`
  return `suit-${Math.floor(kind / 9)}`
}

const goBack = async () => {
  if (!play.active || !play.room?.id) {
    router.push('/games')
    return
  }
  const roomId = play.room.id
  // 实时权威牌局:房主退出即结束(否则其他人等不到出牌),访客退出则离房把人位交给 AI
  if (play.room.status === 'PLAYING') {
    try {
      await (play.isHost ? roomApi.end(roomId) : roomApi.leave(roomId))
    } catch {
      // 房间已结束时忽略
    }
  }
  router.push(play.isHost ? `/games/room/${roomId}` : '/games')
}

function sleep(ms) {
  return new Promise((res) => setTimeout(res, ms))
}

// ---------- 座次 ----------

function buildSoloSeats() {
  const seats = [{ seat: 0, userId: myId.value, name: '', ai: false }]
  for (let i = 1; i < 4; i++) seats.push({ seat: i, userId: null, name: '', ai: true })
  return seats
}

/** 房间座次:房主 0 号位,其余玩家按加入顺序入座,空位 AI 补满 */
function buildRoomSeats() {
  const hostId = play.room?.hostId
  const players = (play.room?.members || [])
    .filter((m) => !isSpectatorRole(m.role))
    .sort((a, b) => {
      if (String(a.userId) === String(hostId)) return -1
      if (String(b.userId) === String(hostId)) return 1
      return 0
    })
  const seats = []
  for (const m of players.slice(0, 4)) {
    seats.push({ seat: seats.length, userId: m.userId, name: m.name, ai: false })
  }
  while (seats.length < 4) seats.push({ seat: seats.length, userId: null, name: '', ai: true })
  return seats
}

// ---------- 发牌 / 主循环 ----------

function drawOne() {
  const id = wall.value.pop()
  return id == null ? null : id
}

function canSeatWin(seat) {
  return canMahjongWin(mahjongCounts(hands.value[seat]), melds.value[seat]?.length || 0)
}

function canSeatClaimWin(seat, kind) {
  const c = mahjongCounts(hands.value[seat])
  if (c[kind] >= 4) return false
  c[kind] += 1
  return canMahjongWin(c, melds.value[seat]?.length || 0)
}

function canSeatPong(seat, kind) {
  return mahjongCounts(hands.value[seat])[kind] >= 2 && (melds.value[seat]?.length || 0) < 3
}

/** AI 碰牌倾向:字牌/幺九必碰,数牌中张在已有多对时碰,避免拆掉成顺子 */
function aiShouldPong(seat, kind) {
  const c = mahjongCounts(hands.value[seat])
  if (c[kind] < 2) return false
  if ((melds.value[seat]?.length || 0) >= 3) return false
  if (kind >= 27 || kind % 9 === 0 || kind % 9 === 8) return true
  return c.filter((n) => n === 2).length >= 2
}

async function startGame() {
  if (play.active && !play.isHost) return
  session += 1
  const mySession = session
  turnWaiters.clear()
  claimWaiters.clear()
  phase.value = 'playing'
  result.value = null
  guestClaim.value = null
  localClaim.value = null
  rewardMsg.value = ''
  lastDiscard.value = null
  drawnTileId.value = null
  canSelfWin.value = false
  busyAi.value = false
  rivers.value = [[], [], [], []]
  melds.value = [[], [], [], []]
  guestHand.value = []
  seatList.value = play.active ? buildRoomSeats() : buildSoloSeats()
  wall.value = createMahjongWall()
  const deal = [[], [], [], []]
  for (let round = 0; round < 13; round++) {
    for (let seat = 0; seat < 4; seat++) deal[seat].push(wall.value.pop())
  }
  hands.value = deal
  handCounts.value = deal.map((h) => h.length)
  turn.value = 0
  rounds.value += 1
  publishAll()
  runGameLoop(mySession)
}

/** 主循环:回合制驱动;轮到人时挂起等操作(房间局超时由 AI 代打),碰牌后不摸牌直接出牌 */
async function runGameLoop(mySession) {
  let current = turn.value
  let skipDraw = false
  while (phase.value === 'playing' && mySession === session) {
    const seat = current
    turn.value = seat
    const ai = !!seatList.value.find((s) => s.seat === seat)?.ai
    if (!skipDraw) {
      const id = drawOne()
      if (id == null) return endDraw(mySession)
      hands.value[seat] = [...hands.value[seat], id]
      drawnTileId.value = id
      canSelfWin.value = !ai && seat === mySeat.value && canSeatWin(seat)
      publishAll()
      if (ai && canSeatWin(seat)) return finishHand(seat, true, mySession)
    } else {
      drawnTileId.value = null
      canSelfWin.value = false
      publishAll()
    }
    skipDraw = false
    let action
    if (ai) {
      busyAi.value = true
      await sleep(520)
      if (mySession !== session || phase.value !== 'playing') {
        busyAi.value = false
        return
      }
      busyAi.value = false
      action = { type: 'discard', kind: mahjongAiChooseDiscard(mahjongCounts(hands.value[seat])) }
    } else {
      // 房间局 35s 不出牌由 AI 代打,避免一个人挂机把整桌拖死;单机不限时
      action = await waitSeat(seat, play.active ? 35000 : 0)
      if (mySession !== session) return
      if (action.type === 'timeout' || action.type === 'abort') {
        action = { type: 'discard', kind: mahjongAiChooseDiscard(mahjongCounts(hands.value[seat])) }
      }
    }
    if (action.type === 'win') return finishHand(seat, true, mySession)
    let kind = action.kind != null ? action.kind : mahjongKind(action.tileId)
    if (kind == null || !hands.value[seat].some((tile) => mahjongKind(tile) === kind)) {
      // 非法/过期操作:兜底让 AI 选一张,避免死循环
      kind = mahjongAiChooseDiscard(mahjongCounts(hands.value[seat]))
      if (kind < 0) return
    }
    discardTile(seat, kind)
    publishAll()
    const claim = await resolveClaims(seat, kind, mySession)
    if (mySession !== session || phase.value !== 'playing') return
    if (claim && claim.win != null) return finishHand(claim.win, false, mySession, kind)
    if (claim && claim.pong != null) {
      applyPong(claim.pong, seat, kind)
      publishAll()
      current = claim.pong
      skipDraw = true
      continue
    }
    current = (seat + 1) % 4
  }
}

function waitSeat(seat, timeoutMs) {
  return new Promise((resolve) => {
    let timer = null
    const done = (action) => {
      if (timer) {
        clearTimeout(timer)
        timer = null
      }
      if (turnWaiters.get(seat) === done) turnWaiters.delete(seat)
      resolve(action)
    }
    if (timeoutMs > 0) {
      timer = setTimeout(() => {
        if (turnWaiters.get(seat) === done) {
          turnWaiters.delete(seat)
          resolve({ type: 'timeout' })
        }
      }, timeoutMs)
    }
    turnWaiters.set(seat, done)
  })
}

function resolveSeatAction(seat, action) {
  const done = turnWaiters.get(seat)
  if (!done) return false
  turnWaiters.delete(seat)
  done(action)
  return true
}

function discardTile(seat, kind) {
  const tileId = hands.value[seat].find((id) => mahjongKind(id) === kind)
  if (tileId == null) return
  hands.value[seat] = hands.value[seat].filter((id) => id !== tileId)
  rivers.value[seat] = [...rivers.value[seat], kind]
  lastDiscard.value = { kind, from: seat }
  drawnTileId.value = null
  canSelfWin.value = false
  handCounts.value = hands.value.map((h) => h.length)
}

function applyPong(seat, fromSeat, kind) {
  const ids = hands.value[seat].filter((id) => mahjongKind(id) === kind).slice(0, 2)
  if (ids.length < 2) return
  hands.value[seat] = hands.value[seat].filter((id) => !ids.includes(id))
  melds.value[seat] = [...melds.value[seat], { kind, from: fromSeat, type: 'pong' }]
  // 被碰走的牌从弃牌河移除(算进碰出的面子)
  rivers.value[fromSeat] = rivers.value[fromSeat].slice(0, -1)
  lastDiscard.value = null
  drawnTileId.value = null
  canSelfWin.value = false
  handCounts.value = hands.value.map((h) => h.length)
  turn.value = seat
}

/** 碰胡询问:胡优先于碰;本机玩家用按钮,远端玩家发定向信令;到点没人应即继续 */
function resolveClaims(fromSeat, kind, mySession) {
  return new Promise((resolve) => {
    const order = [1, 2, 3].map((i) => (fromSeat + i) % 4)
    const winSeats = order.filter((seat) => canSeatClaimWin(seat, kind))
    const pongSeats = order.filter((seat) => !winSeats.includes(seat) && canSeatPong(seat, kind))
    const isAiSeat = (seat) => !!seatList.value.find((s) => s.seat === seat)?.ai
    const humanWin = winSeats.filter((seat) => !isAiSeat(seat))
    const aiWin = winSeats.find(isAiSeat)
    const humanPong = pongSeats.filter((seat) => !isAiSeat(seat))
    const aiPong = pongSeats.find((seat) => isAiSeat(seat) && aiShouldPong(seat, kind))

    const ask = (seats, claimKind, timeoutMs) => {
      if (!seats.length) return Promise.resolve(null)
      return new Promise((done) => {
        let settled = false
        let timer = null
        const pending = new Set(seats)
        const cleanup = () => {
          if (timer) clearTimeout(timer)
          for (const seat of seats) {
            claimWaiters.delete(seat)
            if (seat === mySeat.value) {
              localClaim.value = null
              continue
            }
            const st = seatList.value.find((s) => s.seat === seat)
            if (st && !st.ai) play.send('mj:claim', { to: st.userId, seat, kind: null })
          }
        }
        const finish = (seat) => {
          if (settled) return
          settled = true
          cleanup()
          done(seat)
        }
        // 所有被问的人都选了「过」就不用干等到超时
        const declined = (seat) => {
          if (!pending.delete(seat)) return
          if (!pending.size) finish(null)
        }
        timer = setTimeout(() => finish(null), timeoutMs)
        for (const seat of seats) {
          if (seat === mySeat.value) {
            localClaim.value = claimKind
            claimWaiters.set(seat, (ans) => {
              if (ans === 'claim') finish(seat)
              else {
                claimWaiters.delete(seat)
                localClaim.value = null
                declined(seat)
              }
            })
          } else {
            const st = seatList.value.find((s) => s.seat === seat)
            if (!st || st.ai) {
              pending.delete(seat)
              continue
            }
            claimWaiters.set(seat, (ans) => {
              if (ans === 'claim') finish(seat)
              else {
                claimWaiters.delete(seat)
                declined(seat)
              }
            })
            play.send('mj:claim', { to: st.userId, seat, kind: claimKind })
          }
        }
        if (!pending.size) finish(null)
      })
    }

    ;(async () => {
      if (mySession !== session) return resolve(null)
      if (humanWin.length) {
        const hit = await ask(humanWin, 'win', 8000)
        if (mySession !== session) return resolve(null)
        if (hit != null) return resolve({ win: hit })
      }
      if (aiWin != null) return resolve({ win: aiWin })
      if (humanPong.length) {
        const hit = await ask(humanPong, 'pong', 6000)
        if (mySession !== session) return resolve(null)
        if (hit != null) return resolve({ pong: hit })
      }
      if (aiPong != null) return resolve({ pong: aiPong })
      resolve(null)
    })()
  })
}

function resolveClaim(seat, answer) {
  const done = claimWaiters.get(seat)
  if (!done) return false
  claimWaiters.delete(seat)
  done(answer)
  return true
}

/** 房主收到的远端操作(只接受该座位本人发来的操作) */
function handleGuestAction(data) {
  if (!play.isHost) return
  const seat = Number(data.seat)
  const st = seatList.value.find((s) => s.seat === seat)
  if (!st || st.ai || String(st.userId) !== String(data.from)) return
  const action = data.action || {}
  if (claimWaiters.has(seat)) {
    resolveClaim(seat, action.type === 'win' || action.type === 'pong' ? 'claim' : 'pass')
    return
  }
  resolveSeatAction(seat, action)
}

function sendToHost(action) {
  if (!play.active || !play.room?.hostId) return false
  return play.send('mj:act', { to: play.room.hostId, seat: mySeat.value, action })
}

// ---------- 本机交互 ----------

function onTileClick(tile) {
  if (!canDiscardNow.value) return
  const action = { type: 'discard', tileId: tile.id }
  if (isGuest.value) sendToHost(action)
  else resolveSeatAction(mySeat.value, action)
}

function declareSelfWin() {
  if (!canSelfWin.value) return
  if (isGuest.value) sendToHost({ type: 'win' })
  else resolveSeatAction(mySeat.value, { type: 'win' })
}

function answerClaim(take) {
  if (!claimPrompt.value) return
  const kind = claimPrompt.value === 'win' ? 'win' : 'pong'
  if (isGuest.value) {
    guestClaim.value = null
    sendToHost({ type: take ? kind : 'pass' })
    return
  }
  resolveClaim(mySeat.value, take ? 'claim' : 'pass')
}

// ---------- 房间同步 ----------

function publicSnapshot() {
  return {
    seats: seatList.value,
    phase: phase.value,
    turn: turn.value,
    wallCount: wall.value.length,
    handCounts: hands.value.map((h) => h.length),
    rivers: rivers.value,
    melds: melds.value,
    lastDiscard: lastDiscard.value,
    result: result.value,
  }
}

/** 房主广播公共盘面 + 按人下发各自手牌(其余人只看张数) */
function publishAll() {
  if (!play.active || !play.isHost) return
  wallCount.value = wall.value.length
  handCounts.value = hands.value.map((h) => h.length)
  play.send('mj:public', { state: publicSnapshot() })
  for (const s of seatList.value) {
    if (s.ai || !s.userId || String(s.userId) === String(play.myId)) continue
    const mine = s.seat === turn.value && phase.value === 'playing'
    play.send('mj:hand', {
      to: s.userId,
      seat: s.seat,
      tiles: hands.value[s.seat],
      drawnId: mine ? drawnTileId.value : null,
      canWin: mine && canSeatWin(s.seat),
      myTurn: mine,
    })
  }
}

function applyPublic(state) {
  if (!state) return
  if (Array.isArray(state.seats) && state.seats.length === 4) seatList.value = state.seats
  phase.value = state.phase === 'ended' ? 'ended' : state.phase === 'playing' ? 'playing' : phase.value
  turn.value = Number(state.turn) || 0
  wallCount.value = Math.max(0, Number(state.wallCount) || 0)
  if (Array.isArray(state.handCounts) && state.handCounts.length === 4) {
    handCounts.value = state.handCounts.map((n) => Math.max(0, Number(n) || 0))
  }
  if (Array.isArray(state.rivers) && state.rivers.length === 4) rivers.value = state.rivers
  if (Array.isArray(state.melds) && state.melds.length === 4) melds.value = state.melds
  lastDiscard.value = state.lastDiscard || null
  result.value = state.result || null
  if (phase.value === 'ended') {
    canSelfWin.value = false
    guestClaim.value = null
    autoReport()
  }
}

function onRoomSignal(data) {
  if (!data) return
  // 房主权威:公共盘面/手牌/碰胡询问只认房主发来的,别人伪造的同名信令直接丢弃
  const fromHost = String(data.from) === String(play.room?.hostId)
  if (data.event === 'mj:public' && isGuest.value) {
    if (!fromHost) return
    applyPublic(data.state)
    return
  }
  if (data.event === 'mj:hand' && isGuest.value) {
    if (!fromHost) return
    if (Number(data.seat) !== mySeat.value) return
    guestHand.value = Array.isArray(data.tiles) ? data.tiles : []
    drawnTileId.value = data.drawnId == null ? null : Number(data.drawnId)
    canSelfWin.value = !!data.canWin
    return
  }
  if (data.event === 'mj:claim' && isGuest.value) {
    if (!fromHost) return
    if (Number(data.seat) !== mySeat.value) return
    guestClaim.value = data.kind || null
    return
  }
  if (data.event === 'mj:act' && play.isHost) {
    handleGuestAction(data)
    return
  }
  if (data.event === 'sync' && play.isHost) publishAll()
}

function onRoomChange(next) {
  if (!next || !play.active) return
  if ((next.status === 'FINISHED' || next.status === 'CANCELLED') && phase.value !== 'ended') {
    // 房间先结束了:停掉旧循环,盘面留在当前状态,按钮回大厅
    session += 1
    abortWaiters()
    return
  }
  // 牌局中途有人退出房间:把他的人位交给 AI,别让整桌每回合干等超时
  if (!play.isHost || phase.value !== 'playing') return
  const players = (next.members || []).filter((m) => !isSpectatorRole(m.role))
  let changed = false
  for (const seat of seatList.value) {
    if (seat.ai || !seat.userId) continue
    if (players.some((m) => String(m.userId) === String(seat.userId))) continue
    seat.ai = true
    changed = true
    if (claimWaiters.has(seat.seat)) resolveClaim(seat.seat, 'pass')
    if (turnWaiters.has(seat.seat)) resolveSeatAction(seat.seat, { type: 'timeout' })
  }
  if (changed) publishAll()
}

/** 房间状态:开赛后房主自动开牌,访客进入等待并主动要一次全量同步 */
watch(
  () => [play.active, play.status, play.isHost],
  () => {
    if (!play.active) return
    if (play.status !== 'PLAYING' || phase.value !== 'idle') return
    if (play.isHost) {
      startGame()
      return
    }
    seatList.value = buildRoomSeats()
    phase.value = 'playing'
    play.requestSync()
  },
  { immediate: true },
)

/** WS 断线重连后补一次全量同步:否则访客的手牌/盘面停在断线前,直到下一回合 */
watch(
  () => chatStore.socketStatus,
  (status) => {
    if (status !== 'open') return
    if (!play.active || play.isHost || phase.value !== 'playing') return
    play.requestSync()
  },
)

// ---------- 结算 ----------

function endDraw(mySession) {
  if (mySession !== session) return
  result.value = { draw: true, winner: -1, winnerName: '', fans: [], fanKeys: [], score: 0 }
  phase.value = 'ended'
  publishAll()
  autoReport()
}

async function finishHand(winnerSeat, selfDrawn, mySession, claimKind = null) {
  if (mySession !== session || phase.value === 'ended') return
  const counts = mahjongCounts(hands.value[winnerSeat])
  if (!selfDrawn && claimKind != null) counts[claimKind] += 1
  const handTiles = [...hands.value[winnerSeat].map(mahjongKind)]
  if (!selfDrawn && claimKind != null) handTiles.push(claimKind)
  const { fans, score } = mahjongHandScore(counts, { selfDrawn, melds: melds.value[winnerSeat] })
  const winner = seatList.value.find((s) => s.seat === winnerSeat)
  const iWon = winner && !winner.ai && String(winner.userId) === String(play.myId)
  if (iWon) playerWins.value += 1
  else if (winner?.ai) aiWins.value += 1
  result.value = {
    winner: winnerSeat,
    winnerName: seatName(winnerSeat),
    winnerAi: !!winner?.ai,
    selfDrawn,
    fans,
    fanKeys: fans.map((f) => f.key),
    score,
    handTiles,
    draw: false,
  }
  phase.value = 'ended'
  publishAll()
  if (!play.active) {
    if (iWon) await grantReward()
  } else {
    autoReport()
  }
}

/** 房间局:牌局结束各自自动交卷(赢家报番分,其余报 0),房主按高分结算 */
function autoReport() {
  if (!play.active || play.reported || !result.value) return
  play.report(myScore.value)
}

const submitRoomScore = () => play.report(myScore.value)

const onResultAction = () => {
  if (play.active && play.room?.id) router.push(`/games/room/${play.room.id}`)
  else startGame()
}

async function grantReward() {
  try {
    const res = await gameApi.mahjongReward()
    rewardMsg.value =
      res && res.awarded > 0
        ? `${t('games.mahjong.rewarded', { n: res.awarded })}(${t('points.points')} ${res.balance})`
        : t('games.mahjong.rewardCapped')
  } catch (e) {
    rewardMsg.value = t('games.mahjong.rewardFailed')
  }
}

function abortWaiters() {
  for (const [seat, done] of turnWaiters) {
    turnWaiters.delete(seat)
    done({ type: 'abort' })
  }
  for (const [seat, done] of claimWaiters) {
    claimWaiters.delete(seat)
    done('pass')
  }
  turnWaiters.clear()
  claimWaiters.clear()
  localClaim.value = null
  guestClaim.value = null
  busyAi.value = false
}

onMounted(() => {
  sunLight?.suspendEffects()
})

onBeforeUnmount(() => {
  session += 1
  abortWaiters()
  sunLight?.restoreEffects()
})
</script>

<style scoped>
.mahjong-card {
  padding: 20px;
  min-height: 480px;
  display: flex;
  flex-direction: column;
}

/* 封面 */
.cover {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 56px 20px;
}
.cover-title {
  font-size: 30px;
  font-weight: 800;
  letter-spacing: 0.06em;
}
.cover-desc {
  font-size: 14px;
  color: var(--color-text-secondary);
  text-align: center;
  max-width: 480px;
  line-height: 1.6;
}
.start-btn {
  margin-top: 8px;
  padding: 10px 42px;
  border: none;
  border-radius: 999px;
  background: var(--color-brand);
  color: var(--color-brand-text, #fff);
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
}
.cover-stats,
.cover-hint {
  font-size: 13px;
  color: var(--color-text-secondary);
}
.table-waiting {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-secondary);
  font-size: 14px;
  padding: 60px 0;
}

/* 牌面:白底圆角,数字大花色小;字牌按颜色 */
.tile {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 60px;
  border-radius: 6px;
  background: #f8f6ef;
  border: 1px solid rgba(0, 0, 0, 0.18);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.8),
    0 1px 2px rgba(0, 0, 0, 0.14);
  color: #1b2b33;
  cursor: pointer;
  transition:
    transform 0.12s ease,
    box-shadow 0.12s ease;
  flex: none;
}
.tile .tile-face {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  line-height: 1.05;
}
.tile .tile-num {
  font-size: 22px;
  font-weight: 800;
}
.tile .tile-suit {
  font-size: 11px;
  font-weight: 600;
  opacity: 0.72;
}
.tile.honor {
  font-size: 18px;
  font-weight: 800;
}
.tile-face.red {
  color: #c0392b;
}
.tile-face.green {
  color: #1e8449;
}
.tile-face.blue {
  color: #2471a3;
}

/* 小牌(河里/碰出的面子) */
.tile.mini {
  width: 30px;
  height: 42px;
  cursor: default;
}
.tile.mini .tile-num {
  font-size: 14px;
}
.tile.mini .tile-suit {
  font-size: 9px;
}
.tile.mini.honor {
  font-size: 12px;
}

/* 对手区 */
.opponents {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}
.opponent {
  border: 1px solid var(--color-border);
  border-radius: 12px;
  padding: 8px 10px;
  min-width: 0;
  opacity: 0.75;
  transition:
    border-color 0.16s ease,
    opacity 0.16s ease;
}
.opponent.active {
  opacity: 1;
  border-color: var(--color-accent);
}
.opponent-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.opponent-name {
  font-size: 13px;
  font-weight: 600;
}
.opponent-count {
  font-size: 12px;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}
.opponent-river {
  display: flex;
  flex-wrap: wrap;
  gap: 3px;
  margin-top: 6px;
  min-height: 44px;
}
.melds-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}
.meld-group {
  display: inline-flex;
  gap: 1px;
  padding: 1px;
  border-radius: 5px;
  background: rgba(var(--color-brand-rgb), 0.12);
}
.my-melds {
  justify-content: center;
  margin: 0 0 6px;
}

/* 中央 */
.table-center {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 12px 0;
}
.wall-count,
.turn-label {
  font-size: 13px;
  color: var(--color-text-secondary);
}
.turn-label {
  font-weight: 600;
  color: var(--color-text);
}
.last-discard {
  cursor: default;
}

/* 自己这边 */
.my-side {
  margin-top: auto;
  border-top: 1px dashed var(--color-border);
  padding-top: 10px;
}
.my-river {
  display: flex;
  flex-wrap: wrap;
  gap: 3px;
  min-height: 44px;
  margin-bottom: 8px;
}
.hand-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: center;
}
.tile.hand.drawn {
  transform: translateY(-6px);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.8),
    0 4px 10px rgba(var(--color-brand-rgb), 0.45);
}
.tile.hand:not(:disabled):hover {
  transform: translateY(-4px);
}
.tile.hand.dim {
  opacity: 0.6;
}
.my-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  justify-content: center;
  margin-top: 10px;
  min-height: 36px;
  flex-wrap: wrap;
}
.my-hint {
  font-size: 12px;
  color: var(--color-text-secondary);
}
/* 听牌提示行:小牌列表 + 文案 */
.advice-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: center;
  margin-top: 8px;
  min-height: 40px;
}
.advice-label {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-right: 4px;
}

/* 结算浮层 */
.overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--overlay-scrim);
  backdrop-filter: var(--overlay-blur);
  border-radius: inherit;
  z-index: 5;
}
.overlay-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 28px 32px;
  border-radius: 16px;
  background: var(--color-bg);
  box-shadow: var(--shadow-hover);
  max-width: 90%;
}
.overlay-icon {
  font-size: 40px;
}
.overlay-title {
  font-size: 20px;
  font-weight: 700;
}
.overlay-score {
  font-size: 14px;
  color: var(--color-text-secondary);
}
.overlay-fans {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: center;
}
.overlay-hand {
  display: flex;
  flex-wrap: wrap;
  gap: 3px;
  justify-content: center;
}
.overlay-reward {
  font-size: 13px;
  color: var(--color-accent);
}
.overlay-wait {
  font-size: 12px;
  color: var(--color-text-secondary);
}

@media (max-width: 768px) {
  .tile {
    width: 36px;
    height: 50px;
  }
  .tile .tile-num {
    font-size: 18px;
  }
  .tile.mini {
    width: 26px;
    height: 36px;
  }
  .mahjong-card {
    padding: 12px;
  }
  .opponents {
    gap: 6px;
  }
}
</style>
