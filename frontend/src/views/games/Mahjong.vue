<!-- 麻将(推倒胡简化版):玩家 vs 三位 AI,136 张牌全门清,4 面子 + 对子或七对子即胡。
     规则内核在 utils/games/mahjong.js(判胡/听牌/计番/AI 选牌都是纯函数);
     本页只做发牌/回合驱动/交互与结算展示,赢一局走统一 H5 奖励通道(每日限次)。 -->
<template>
  <div ref="pageEl" class="page game-page">
    <PageToolbar root-class="game-topbar" :holder-margin="0" always>
      <GameTopActions
        :is-fullscreen="isFullscreen"
        :wide="isWide"
        :fullscreen-label="$t('games.mahjong.fullscreen')"
        :exit-label="$t('games.mahjong.exitFullscreen')"
        @back="router.push('/games')"
        @fullscreen="toggleFullscreen"
        @wide="toggleWide"
      />
    </PageToolbar>

    <div ref="gameCardEl" class="game-card game-surface mahjong-card" @contextmenu.prevent @dblclick.prevent>
      <!-- 封面 -->
      <div v-if="phase === 'idle'" class="cover">
        <h1 class="cover-title">{{ $t('games.mahjong.title') }}</h1>
        <div class="cover-desc">{{ $t('games.mahjong.desc') }}</div>
        <button class="start-btn" type="button" @click="startGame">{{ $t('games.mahjong.start') }}</button>
        <div v-if="rounds > 0" class="cover-stats">{{ $t('games.mahjong.roundStats', { win: playerWins, lose: aiWins }) }}</div>
        <div class="cover-hint">{{ $t('games.mahjong.coverHint') }}</div>
      </div>

      <!-- 牌桌 -->
      <template v-else>
        <!-- 三位对手:名字 + 剩牌 + 弃牌河 -->
        <div class="opponents">
          <div v-for="seat in 3" :key="seat" class="opponent" :class="{ active: turn === seat && phase === 'playing' }">
            <div class="opponent-head">
              <span class="opponent-name">{{ $t(`games.mahjong.ai${seat}`) }}</span>
              <span class="opponent-count">{{ hands[seat].length }}</span>
            </div>
            <div class="opponent-river">
              <span v-for="(kind, i) in rivers[seat]" :key="i" class="tile mini" :class="tileClass(kind)">
                <TileFace :kind="kind" />
              </span>
            </div>
          </div>
        </div>

        <!-- 中央信息:牌墙 / 最近一张弃牌 -->
        <div class="table-center">
          <span class="wall-count">{{ $t('games.mahjong.wallLeft', { n: wall.length }) }}</span>
          <span class="turn-label">{{ turnLabel }}</span>
          <span v-if="lastDiscard" class="last-discard tile" :class="tileClass(lastDiscard.kind)">
            <TileFace :kind="lastDiscard.kind" />
          </span>
        </div>

        <!-- 自己的手牌 -->
        <div class="my-side" :class="{ active: turn === 0 && phase === 'playing' }">
          <div class="my-river">
            <span v-for="(kind, i) in rivers[0]" :key="i" class="tile mini" :class="tileClass(kind)">
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
              :disabled="phase !== 'playing' || turn !== 0 || busyAi"
              @click="onTileClick(tile)"
            >
              <TileFace :kind="tile.kind" />
            </button>
          </div>
          <div class="my-actions">
            <el-button v-if="canSelfWin" type="primary" size="default" round @click="declareSelfWin">
              {{ $t('games.mahjong.selfWin') }}
            </el-button>
            <template v-if="claimPending">
              <el-button type="primary" size="default" round @click="declareClaimWin">
                {{ $t('games.mahjong.claimWin') }}
              </el-button>
              <el-button size="default" round @click="passClaim">{{ $t('games.mahjong.pass') }}</el-button>
            </template>
            <span class="my-hint">{{ actionHint }}</span>
          </div>
        </div>

        <!-- 结算浮层 -->
        <Transition name="game-overlay">
          <div v-if="phase === 'ended'" class="overlay game-overlay">
            <div class="overlay-card game-overlay-card">
              <div class="overlay-icon">{{ result?.winner === 0 ? '🎉' : '😌' }}</div>
              <div class="overlay-title">
                {{
                  result
                    ? result.winner === 0
                      ? $t('games.mahjong.youWin')
                      : result.draw
                        ? $t('games.mahjong.drawGame')
                        : $t('games.mahjong.aiWins', { name: $t(`games.mahjong.ai${result.winner}`) })
                    : ''
                }}
              </div>
              <div v-if="result && !result.draw" class="overlay-score">
                {{ $t('games.mahjong.scoreLine', { score: result.score }) }}
              </div>
              <div v-if="result && !result.draw && result.fanKeys.length" class="overlay-fans">
                <el-tag v-for="f in result.fanKeys" :key="f" size="small" effect="plain">{{ $t(`games.mahjong.fans.${f}`) }}</el-tag>
              </div>
              <div v-if="result && result.winner === 0 && result.handTiles?.length" class="overlay-hand">
                <span v-for="(kind, i) in result.handTiles" :key="i" class="tile mini" :class="tileClass(kind)">
                  <TileFace :kind="kind" />
                </span>
              </div>
              <div v-if="rewardMsg" class="overlay-reward">{{ rewardMsg }}</div>
              <el-button type="primary" size="small" round @click="startGame">{{ $t('games.mahjong.again') }}</el-button>
            </div>
          </div>
        </Transition>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, h, inject, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { gameApi } from '@/api'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import PageToolbar from '@/components/PageToolbar.vue'
import GameTopActions from '@/components/games/GameTopActions.vue'
import { useGameView } from '@/composables/useGameView'
import {
  canMahjongWin,
  createMahjongWall,
  mahjongAiChooseDiscard,
  mahjongCounts,
  mahjongHandScore,
  mahjongKind,
  sortMahjongTiles,
} from '@/utils/games/mahjong'

const router = useRouter()
const { t } = useI18n()
const sunLight = inject(SUN_LIGHT_KEY, null)

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

// ---------- 对局状态 ----------
const phase = ref('idle') // idle | playing | ended
const turn = ref(0) // 0=玩家 1..3=AI
const hands = ref([[], [], [], []]) // 实例 id 数组
const rivers = ref([[], [], [], []]) // 弃牌 kind(按丢弃顺序)
const wall = ref([])
const lastDiscard = ref(null) // { kind, from }
const drawnTileId = ref(null) // 玩家刚摸的牌(高亮)
const canSelfWin = ref(false)
const claimPending = ref(false)
const busyAi = ref(false)
const result = ref(null)
const rewardMsg = ref('')
const playerWins = ref(0)
const aiWins = ref(0)
const rounds = ref(0)

let session = 0 // 会话令牌:重开/离开时旧循环自行退出
let playerResolver = null

const myHandView = computed(() => {
  const sorted = sortMahjongTiles(hands.value[0])
  return sorted.map((id) => ({ id, kind: mahjongKind(id), justDrawn: id === drawnTileId.value }))
})

const turnLabel = computed(() => {
  if (phase.value !== 'playing') return ''
  return turn.value === 0 ? t('games.mahjong.yourTurn') : t('games.mahjong.aiTurn', { name: t(`games.mahjong.ai${turn.value}`) })
})

const actionHint = computed(() => {
  if (phase.value !== 'playing') return ''
  if (canSelfWin.value) return t('games.mahjong.selfWinHint')
  if (claimPending.value) return t('games.mahjong.claimHint')
  if (turn.value === 0) return t('games.mahjong.discardHint')
  return ''
})

const tileClass = (kind) => {
  if (kind >= 27) return `honor-${['east', 'south', 'west', 'north', 'zhong', 'fa', 'bai'][kind - 27]}`
  return `suit-${Math.floor(kind / 9)}`
}

function sleep(ms) {
  return new Promise((res) => setTimeout(res, ms))
}

function awaitPlayerAction() {
  return new Promise((resolve) => {
    playerResolver = resolve
  })
}

function resolvePlayerAction(action) {
  const r = playerResolver
  playerResolver = null
  r?.(action)
}

/** 牌墙摸一张(空返回 null) */
function drawOne() {
  const id = wall.value.pop()
  return id == null ? null : id
}

async function startGame() {
  session += 1
  const mySession = session
  phase.value = 'playing'
  result.value = null
  rewardMsg.value = ''
  lastDiscard.value = null
  drawnTileId.value = null
  canSelfWin.value = false
  claimPending.value = false
  busyAi.value = false
  rivers.value = [[], [], [], []]
  wall.value = createMahjongWall()
  const deal = [[], [], [], []]
  for (let round = 0; round < 13; round++) {
    for (let seat = 0; seat < 4; seat++) deal[seat].push(wall.value.pop())
  }
  hands.value = deal
  turn.value = 0
  rounds.value += 1
  runGameLoop(mySession)
}

/** 主循环:回合制驱动,玩家输入经 Promise 挂起 */
async function runGameLoop(mySession) {
  while (phase.value === 'playing' && mySession === session) {
    const seat = turn.value
    if (seat === 0) {
      // 玩家回合:摸牌 → 自摸判定 → 等弃牌或胡
      const id = drawOne()
      if (id == null) return endDraw(mySession)
      hands.value[0] = [...hands.value[0], id]
      drawnTileId.value = id
      const counts = mahjongCounts(hands.value[0])
      canSelfWin.value = canMahjongWin(counts)
      const action = await awaitPlayerAction()
      if (mySession !== session) return
      canSelfWin.value = false
      if (action.type === 'win') {
        return finishHand(0, true, mySession)
      }
      // 弃牌
      const discardId = action.tileId
      const kind = mahjongKind(discardId)
      hands.value[0] = hands.value[0].filter((t) => t !== discardId)
      rivers.value[0] = [...rivers.value[0], kind]
      lastDiscard.value = { kind, from: 0 }
      drawnTileId.value = null
      // AI 依次检查能否胡这张弃牌(点炮),能胡的直接胡(放炮)
      for (let s = 1; s < 4; s++) {
        const c = mahjongCounts(hands.value[s])
        if (c[kind] >= 4) continue
        c[kind] += 1
        if (canMahjongWin(c)) {
          return finishHand(s, false, mySession, kind)
        }
      }
      turn.value = 1
    } else {
      // AI 回合:摸牌 → 胡? → 弃 → 玩家可点炮胡?
      busyAi.value = true
      await sleep(520)
      if (mySession !== session || phase.value !== 'playing') return
      const id = drawOne()
      if (id == null) return endDraw(mySession)
      hands.value[seat] = [...hands.value[seat], id]
      const counts = mahjongCounts(hands.value[seat])
      if (canMahjongWin(counts)) {
        busyAi.value = false
        return finishHand(seat, true, mySession)
      }
      const discardKind = mahjongAiChooseDiscard(counts)
      const discardId = hands.value[seat].find((t) => mahjongKind(t) === discardKind)
      hands.value[seat] = hands.value[seat].filter((t) => t !== discardId)
      rivers.value[seat] = [...rivers.value[seat], discardKind]
      lastDiscard.value = { kind: discardKind, from: seat }
      busyAi.value = false
      // 玩家是否能胡这张(点炮)
      const myCounts = mahjongCounts(hands.value[0])
      if (myCounts[discardKind] < 4) {
        const trial = [...myCounts]
        trial[discardKind] += 1
        if (canMahjongWin(trial)) {
          claimPending.value = true
          const action = await awaitPlayerAction()
          claimPending.value = false
          if (mySession !== session) return
          if (action.type === 'claim') {
            return finishHand(0, false, mySession, discardKind)
          }
        }
      }
      turn.value = (seat + 1) % 4
    }
  }
}

/** 玩家点手牌弃牌 */
function onTileClick(tile) {
  if (phase.value !== 'playing' || turn.value !== 0 || busyAi.value) return
  if (claimPending.value) return
  resolvePlayerAction({ type: 'discard', tileId: tile.id })
}

function declareSelfWin() {
  if (!canSelfWin.value) return
  resolvePlayerAction({ type: 'win' })
}

function declareClaimWin() {
  if (!claimPending.value) return
  resolvePlayerAction({ type: 'claim' })
}

function passClaim() {
  if (!claimPending.value) return
  resolvePlayerAction({ type: 'pass' })
}

/** 流局(牌墙摸空) */
function endDraw(mySession) {
  if (mySession !== session) return
  result.value = { draw: true, winner: -1, fans: [], fanKeys: [], score: 0 }
  phase.value = 'ended'
}

/** 一局终了:算番、记账、玩家赢时走奖励(点炮时第 14 张 = 别人打出的那张) */
async function finishHand(winner, selfDrawn, mySession, claimKind = null) {
  if (mySession !== session) return
  const counts = mahjongCounts(hands.value[winner])
  if (!selfDrawn && claimKind != null) counts[claimKind] += 1
  const handTiles = [...hands.value[winner].map(mahjongKind)]
  if (!selfDrawn && claimKind != null) handTiles.push(claimKind)
  const { fans, score } = mahjongHandScore(counts, { selfDrawn })
  if (winner === 0) {
    playerWins.value += 1
  } else {
    aiWins.value += 1
  }
  result.value = { winner, selfDrawn, fans, fanKeys: fans.map((f) => f.key), score, handTiles, draw: false }
  phase.value = 'ended'
  if (winner === 0) await grantReward()
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

onMounted(() => {
  sunLight?.suspendEffects()
})

onBeforeUnmount(() => {
  session += 1
  resolvePlayerAction({ type: 'abort' })
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

/* 小牌(河里) */
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
