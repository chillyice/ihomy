<!-- 斗地主:三人局(玩家 + 两位电脑,「一起玩」房间里换成真人/AI 混坐)。
     叫分抢地主、常见牌型判定与电脑选牌都在 utils/games/doudizhu.js(纯函数,单测覆盖);
     本页只做发牌、叫分、回合驱动与结算展示。房间局房主是唯一裁判:
     公共盘面走 dz:public、各家手牌点对点走 dz:hand、访客意图走 dz:act(见 composables/useCardRoomPlay)。
     赢下对局走统一 H5 奖励通道(按天限次);房间局不发单机奖励,按席位成绩结算。 -->
<template>
  <GameShell
    ref="shellEl"
    class="dz-shell"
    :title="$t('games.doudizhu.title')"
    body-align="stretch"
    readable
    :room-mode="play.active"
    :default-wide="play.active"
    :new-game="!play.active && (phase === 'playing' || phase === 'bid')"
    :new-game-confirm="true"
    @back="goBack"
    @new-game="startGame"
  >
    <template #rules>
      <ul>
        <li>{{ $t('games.doudizhu.rules1') }}</li>
        <li>{{ $t('games.doudizhu.rules2') }}</li>
        <li>{{ $t('games.doudizhu.rules3') }}</li>
        <li>{{ $t('games.doudizhu.rules4') }}</li>
        <li>{{ $t('games.fight.gestures') }}</li>
      </ul>
    </template>

    <div class="fight-card">
      <FightTable
        :seats="seatViews"
        :my-seat="tableSeat"
        :spectator="room.watching"
        :hand="myHand"
        :selected="selected"
        :last-play="lastPlayView"
        :status-text="statusText"
        :info-chips="infoChips"
        :my-name="seatName(mySeat)"
        :my-role-label="myRoleLabel"
        :my-tone="myTone"
        :my-note="myNote"
        :sort-value="dzValue"
        @select="toggleCard"
        @extend-select="extendSelect"
        @auto-select="markAuto"
      >
        <template #actions>
          <template v-if="phase === 'bid' && isMyBidTurn">
            <el-button size="small" round @click="bidAction(0)">{{ $t('games.doudizhu.bidPass') }}</el-button>
            <el-button
              v-for="v in [1, 2, 3]"
              :key="v"
              size="small"
              round
              :type="v === 3 ? 'primary' : 'default'"
              :disabled="v <= highestBid"
              @click="bidAction(v)"
            >
              {{ $t('games.doudizhu.bidN', { n: v }) }}
            </el-button>
          </template>
          <template v-else-if="phase === 'playing'">
            <el-button size="small" round :disabled="!myTurn || !lastCombo" @click="passAction">
              {{ $t('games.fight.pass') }}
            </el-button>
            <el-button size="small" round :disabled="!myTurn" @click="hint">{{ $t('games.fight.hint') }}</el-button>
            <el-button type="primary" size="small" round :disabled="!canPlay" @click="playSelected">
              {{ $t('games.fight.play') }}
            </el-button>
          </template>
          <el-button v-if="play.active && room.seated" size="small" round :type="room.autoOn ? 'primary' : 'default'" @click="toggleAuto">
            {{ room.autoOn ? $t('games.room.autoOff') : $t('games.room.autoOn') }}
          </el-button>
        </template>

        <template #center-extra>
          <div v-if="phase === 'bid' && bidLog.length" class="fight-bid-log">
            <span v-for="(entry, i) in bidLog" :key="i" class="fight-bid-item">{{ bidText(entry) }}</span>
          </div>
        </template>

        <template #overlay>
          <Transition name="game-overlay">
            <div v-if="phase === 'idle' || phase === 'ended'" class="fight-overlay">
              <div class="fight-overlay-card game-overlay-card">
                <div v-if="phase === 'ended'" class="fight-overlay-title">
                  {{ result === 'win' ? $t('games.fight.win') : $t('games.fight.lose') }}
                </div>
                <div v-if="phase === 'ended'" class="fight-overlay-meta">{{ resultMeta }}</div>
                <div v-if="rewardMsg" class="fight-overlay-reward">{{ rewardMsg }}</div>
                <div class="fight-overlay-actions">
                  <template v-if="play.active">
                    <el-button type="primary" size="small" round @click="onResultAction">
                      {{ $t('games.room.backRoom') }}
                    </el-button>
                  </template>
                  <template v-else>
                    <el-button type="primary" size="small" round @click="startGame">
                      {{ phase === 'idle' ? $t('games.fight.start') : $t('games.fight.again') }}
                    </el-button>
                    <el-button v-if="phase === 'ended'" size="small" round @click="goBack">
                      {{ $t('games.back') }}
                    </el-button>
                  </template>
                </div>
              </div>
            </div>
          </Transition>
        </template>
      </FightTable>
    </div>
  </GameShell>
</template>

<script setup>
// 回合驱动:裁判端(单机 = 本机,房间 = 房主)推牌局;电脑/托管席位按 AI 延迟出牌,
// 真人席位等点击(房间局 35s 不来由电脑代打)。每次重开/离开把 session +1,
// 旧定时器/旧等待醒来发现令牌变了就自行退出,避免切页后还在改状态。
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import FightTable from '@/components/games/FightTable.vue'
import GameShell from '@/components/games/GameShell.vue'
import { gameApi } from '@/api'
import { useChatStore } from '@/stores/chat'
import { useRoomChatStore } from '@/stores/roomChat'
import { useGameRoomPlay } from '@/composables/useGameRoomPlay'
import { CARD_ACT_TIMEOUT, useCardRoomPlay } from '@/composables/useCardRoomPlay'
import { createWakeTimers } from '@/utils/games/wakeTimers'
import { cardRankText } from '@/utils/games/cardCore'
import { DDZ_COMBO, dzAiBid, dzAiChoose, dzBeats, dzBidStrength, dzCombo, dzDeal, dzPlays, dzValue } from '@/utils/games/doudizhu'
import { seededRng } from '@/utils/games/seededRng'

const SEATS = 3
const AI_DELAY = 760
const BID_DELAY = 620

const router = useRouter()
const { t } = useI18n()
const chatStore = useChatStore()
const roomChat = useRoomChatStore()

const shellEl = ref(null)
const goBack = () => play.exitToLobby({ aiGame: true })

const phase = ref('idle') // idle | bid | playing | ended
const hands = ref([[], [], []])
const bottom = ref([])
const landlord = ref(-1)
const turn = ref(0)
const last = ref(null) // { seat, combo }
const passCount = ref(0)
const selected = ref([])
const multiplier = ref(1)
const bidTurn = ref(0)
const highestBid = ref(0)
const highestSeat = ref(-1)
const bidsLeft = ref(0)
const bidLog = ref([])
const finishOrder = ref([])
const result = ref('')
/** 房间局:地主方是否赢(公共事实,各端据此算自己的胜负与交卷分;result 是各人视角) */
const winnerIsLandlord = ref(false)
const rewardMsg = ref('')
const springLabel = ref('')
const playCounts = ref([0, 0, 0])
const bubbles = ref(['', '', ''])

let session = 0
let aiTimer = null
const wakeTimers = createWakeTimers() // 电脑停顿登记:后台标签页节流 setTimeout,回前台时到期的立即补跑

// ---------- 「一起玩」房间(?room=<id>) ----------

// 房间层要挂在 useGameRoomPlay 的信令回调上,先声明再赋值(回调只在挂载后才会到)
let room = null
const play = useGameRoomPlay({ onSignal: (data) => room && room.onSignal(data) })
room = useCardRoomPlay(play, {
  seatCount: SEATS,
  prefix: 'dz',
  getHand: (seat) => hands.value[seat] || [],
  snapshot,
  applyPublic,
  handPayload: (seat) => ({ tiles: hands.value[seat] || [] }),
  applyHand,
})

/** 我的座位:单机固定 0 号位,房间里按成员加入顺序 */
const mySeat = computed(() => (play.active ? room.mySeat : 0))
const tableSeat = computed(() => Math.max(0, mySeat.value))
/** 这一席位由电脑代打(空位 / 托管):AI 延迟出牌,不等点击 */
const isAiSeat = (seat) => (play.active ? room.isAiSeat(seat) : seat !== mySeat.value)

const myHand = computed(() => hands.value[mySeat.value] || [])
const localCounts = computed(() => hands.value.map((hand) => hand.length))
const handCounts = computed(() => (play.active ? room.counts : localCounts.value))
const myTurn = computed(() => phase.value === 'playing' && turn.value === mySeat.value && !room.autoOn && !play.isSpectator)
const isMyBidTurn = computed(() => phase.value === 'bid' && bidTurn.value === mySeat.value && !room.autoOn && !play.isSpectator)
const lastCombo = computed(() => last.value?.combo || null)
const selectedCards = computed(() => {
  const ids = new Set(selected.value)
  return myHand.value.filter((card) => ids.has(card.id))
})
const selection = computed(() => (selected.value.length ? dzCombo(selectedCards.value) : null))
const canPlay = computed(() => myTurn.value && !!selection.value && dzBeats(selection.value, lastCombo.value))
const isLandlord = computed(() => landlord.value === mySeat.value)
const myRoleLabel = computed(() => roleOf(mySeat.value))
const myTone = computed(() => (landlord.value < 0 ? '' : toneOf(mySeat.value)))

const seatName = (seat) => {
  const st = play.active ? room.seatAt(seat) : null
  if (st?.userId != null && st.name) return st.name
  if (seat === mySeat.value) return t('games.fight.me')
  return t('games.fight.aiName', { n: seat })
}
const roomBubble = (seat) => {
  if (!play.active) return bubbles.value[seat] || ''
  const st = room.seatAt(seat)
  return st?.userId != null ? roomChat.bubbleOf(play.room?.id, st.userId) : ''
}
const toneOf = (seat) => (seat === landlord.value ? 'landlord' : 'farmer')
const roleOf = (seat) => {
  if (landlord.value < 0) return ''
  return seat === landlord.value ? t('games.doudizhu.landlord') : t('games.doudizhu.farmer')
}
const comboLabel = (combo) => (combo ? t(`games.doudizhu.combo.${combo.type}`) : '')
/** 席位分数只在牌局结束后有意义:胜方记倍数分,负方 0(与上报交卷同口径) */
const seatScore = (seat) => {
  if (!play.active || phase.value !== 'ended') return 0
  return (seat === landlord.value) === winnerIsLandlord.value ? multiplier.value * 10 : 0
}

const seatViews = computed(() =>
  Array.from({ length: SEATS }, (_, seat) => {
    const st = play.active ? room.seatAt(seat) : null
    return {
      name: seatName(seat),
      avatar: st?.avatar || '',
      count: handCounts.value[seat] || 0,
      score: seatScore(seat),
      auto: !!(st && !st.ai && st.auto),
      active: phase.value === 'bid' ? bidTurn.value === seat : phase.value === 'playing' && turn.value === seat,
      finished: finishOrder.value.includes(seat),
      roleLabel: roleOf(seat),
      tone: toneOf(seat),
      finishedLabel: finishOrder.value.includes(seat) ? t('games.fight.out') : '',
      bubble: roomBubble(seat),
    }
  }),
)

const lastPlayView = computed(() => {
  if (!last.value) return null
  return {
    seat: last.value.seat,
    name: seatName(last.value.seat),
    cards: last.value.combo.cards,
    label: comboLabel(last.value.combo),
  }
})

const infoChips = computed(() => {
  const chips = []
  if (multiplier.value > 1) {
    chips.push({ key: 'mult', label: t('games.fight.multiplier'), value: `×${multiplier.value}` })
  }
  if (bottom.value.length && landlord.value >= 0) {
    chips.push({
      key: 'bottom',
      label: t('games.doudizhu.bottom'),
      value: bottom.value.map((card) => cardRankText(card)).join(' '),
    })
  }
  return chips
})

const statusText = computed(() => {
  if (phase.value === 'idle') return ''
  if (room.watching) return t('games.room.spectatorHint')
  // 房间局回合倒计时(裁判端起步、各端本地滴答;单机不限时不显示)
  const seconds = Number(room.actSecondsLeft) || 0
  const countdown = seconds > 0 ? ` ${t('games.fight.turnCountdown', { n: seconds })}` : ''
  if (phase.value === 'bid') {
    const base =
      bidTurn.value === mySeat.value ? t('games.doudizhu.bidHint') : t('games.doudizhu.bidWait', { name: seatName(bidTurn.value) })
    return base + countdown
  }
  if (phase.value === 'ended') return ''
  if (room.autoOn) return t('games.room.botState')
  if (myTurn.value) {
    return (lastCombo.value ? t('games.fight.yourTurn') : t('games.doudizhu.yourLead')) + countdown
  }
  return t('games.fight.thinking', { name: seatName(turn.value) }) + countdown
})

const myNote = computed(() => {
  if (phase.value !== 'playing' || !myTurn.value || !selected.value.length) return ''
  if (!selection.value) return t('games.fight.badCards')
  if (!dzBeats(selection.value, lastCombo.value)) return t('games.fight.notBigEnough')
  return comboLabel(selection.value)
})

const resultMeta = computed(() => {
  const parts = [t('games.doudizhu.score', { n: multiplier.value })]
  if (springLabel.value) parts.push(springLabel.value)
  return parts.join(' · ')
})

const clearTimer = () => {
  wakeTimers.cancel(aiTimer)
  aiTimer = null
}

function bidText(entry) {
  return entry.value > 0
    ? t('games.doudizhu.bidLog', { name: seatName(entry.seat), n: entry.value })
    : t('games.doudizhu.bidLogPass', { name: seatName(entry.seat) })
}

// ---------- 房间快照 / 落盘 ----------

/** 房主公共快照:不含任何人的手牌(底牌叫完地主后才公开,叫分阶段只报张数与分数) */
function snapshot() {
  return {
    phase: phase.value,
    turn: turn.value,
    bidTurn: bidTurn.value,
    highestBid: highestBid.value,
    highestSeat: highestSeat.value,
    bidsLeft: bidsLeft.value,
    bidLog: bidLog.value,
    landlord: landlord.value,
    multiplier: multiplier.value,
    last: last.value,
    passCount: passCount.value,
    counts: localCounts.value,
    finishOrder: finishOrder.value,
    winnerIsLandlord: phase.value === 'ended' ? winnerIsLandlord.value : null,
    spring: springLabel.value,
    bottom: landlord.value >= 0 ? bottom.value : [],
  }
}

function applyPublic(state) {
  if (!state) return
  if (state.phase) phase.value = state.phase
  turn.value = Number(state.turn) || 0
  bidTurn.value = Number(state.bidTurn) || 0
  highestBid.value = Number(state.highestBid) || 0
  highestSeat.value = Number.isFinite(Number(state.highestSeat)) ? Number(state.highestSeat) : -1
  bidsLeft.value = Math.max(0, Number(state.bidsLeft) || 0)
  bidLog.value = Array.isArray(state.bidLog) ? state.bidLog : []
  landlord.value = Number.isFinite(Number(state.landlord)) ? Number(state.landlord) : -1
  multiplier.value = Math.max(1, Number(state.multiplier) || 1)
  last.value = state.last || null
  passCount.value = Math.max(0, Number(state.passCount) || 0)
  finishOrder.value = Array.isArray(state.finishOrder) ? state.finishOrder : []
  springLabel.value = state.spring || ''
  bottom.value = Array.isArray(state.bottom) ? state.bottom : []
  if (state.winnerIsLandlord != null) {
    winnerIsLandlord.value = !!state.winnerIsLandlord
    result.value = winnerIsLandlord.value === isLandlord.value ? 'win' : 'lose'
  }
  // 别人的回合不再替自己留选中:风向一变,留着高亮容易误触
  if (turn.value !== mySeat.value || phase.value !== 'playing') {
    selected.value = []
    pendingAuto.value = []
  }
  if (phase.value === 'ended') autoReport()
}

function applyHand(data) {
  const tiles = Array.isArray(data?.tiles) ? data.tiles : []
  const prev = hands.value[mySeat.value] || []
  // 每次发布都会带一份手牌,内容没变就别清选中(否则别人出牌一次就把我正在选的牌清空)
  const same = prev.length === tiles.length && prev.every((card, i) => card.id === tiles[i].id)
  hands.value = hands.value.map((hand, i) => (i === mySeat.value ? tiles : hand))
  if (same) return
  // 手牌真变了(开局/重连补发):旧 id 已经不在手里,选中一起清掉
  selected.value = []
  pendingAuto.value = []
}

const publishRoom = () => room.publish()

// ---------- 发牌 / 叫分 ----------

function startGame() {
  if (play.active && !play.isHost) return
  session += 1
  clearTimer()
  const deal = dzDeal(play.active ? seededRng(play.seed) : Math.random)
  hands.value = deal.hands
  bottom.value = deal.bottom
  landlord.value = -1
  turn.value = 0
  last.value = null
  passCount.value = 0
  selected.value = []
  pendingAuto.value = []
  multiplier.value = 1
  finishOrder.value = []
  result.value = ''
  rewardMsg.value = ''
  springLabel.value = ''
  playCounts.value = [0, 0, 0]
  bubbles.value = ['', '', '']
  phase.value = 'bid'
  highestBid.value = 0
  highestSeat.value = -1
  bidsLeft.value = SEATS
  bidLog.value = []
  // 房间局房主先叫(种子 + 座位一致,几端看到的是同一副牌);单机随机起叫
  bidTurn.value = play.active ? 0 : Math.floor(Math.random() * SEATS)
  publishRoom()
  drive(bidTurn.value)
}

// ---------- 回合驱动(裁判端) ----------

function drive(seat) {
  clearTimer()
  if (!room.authority || play.ended) return
  if (phase.value !== 'bid' && phase.value !== 'playing') return
  const mySession = session
  const apply = (action) => {
    if (mySession !== session || play.ended) return
    if (phase.value === 'bid') {
      if (bidTurn.value !== seat) return
      seatAction(seat, action)
      return
    }
    if (phase.value !== 'playing' || turn.value !== seat) return
    seatAction(seat, action)
  }
  // 引擎异常兜底:记日志后按电脑口径续跑本步,别把整局冻在「电脑思考中」
  const runStep = (fn) => {
    try {
      fn()
    } catch (e) {
      console.error(`[doudizhu] 回合引擎异常 seat=${seat} phase=${phase.value},按电脑兜底续跑`, e)
      try {
        if (mySession === session && !play.ended) {
          const canAct = phase.value === 'bid' ? bidTurn.value === seat : turn.value === seat
          if (canAct) seatAction(seat, null)
        }
      } catch (e2) {
        console.error('[doudizhu] 兜底仍失败,本局中止', e2)
        clearTimer()
      }
    }
  }
  if (isAiSeat(seat)) {
    const delay = phase.value === 'bid' ? BID_DELAY : AI_DELAY
    aiTimer = wakeTimers.schedule(() => runStep(() => apply(null)), delay)
    return
  }
  // 真人:等点击 / 等房内远端的出牌意图;房间局 35s 不来交给电脑,单机不限时
  room.waitAct(seat, play.active ? CARD_ACT_TIMEOUT : 0).then((action) => runStep(() => apply(action)))
}

/** 裁判端落一个出牌意图:电脑的(null)走 AI,真人的先按自己手里的牌复验再落盘 */
function seatAction(seat, action) {
  if (phase.value === 'bid') {
    if (!action) {
      const want = dzAiBid(hands.value[seat])
      applyBid(seat, want > highestBid.value ? want : 0)
      return
    }
    const value = Math.max(0, Math.min(3, Number(action.value) || 0))
    applyBid(seat, value > highestBid.value ? value : 0)
    return
  }
  if (!action || action.type === 'pass') {
    aiFallbackOrPass(seat, action)
    return
  }
  const ids = new Set(Array.isArray(action.ids) ? action.ids : [])
  const cards = hands.value[seat].filter((card) => ids.has(card.id))
  const combo = cards.length ? dzCombo(cards) : null
  if (!combo || !dzBeats(combo, lastCombo.value)) {
    aiFallbackOrPass(seat, null)
    return
  }
  commitPlay(seat, combo)
}

/** 想出的牌不合法(或电脑该出):让 AI 按当前局面拿一手,出不了就过 */
function aiFallbackOrPass(seat, action) {
  if (action && action.type === 'pass' && last.value && last.value.seat !== seat) {
    passTurn(seat)
    return
  }
  const pick = pickAiPlay(seat)
  if (pick) commitPlay(seat, pick)
  else passTurn(seat)
}

function pickAiPlay(seat) {
  return dzAiChoose({
    hand: hands.value[seat],
    last: lastCombo.value,
    seat,
    landlord: landlord.value,
    handCounts: handCounts.value,
    lastSeat: last.value ? last.value.seat : null,
    playCounts: playCounts.value,
  })
}

/** 叫分:0 = 不叫,1..3 必须高于当前最高分;每家各一次机会 */
function applyBid(seat, value) {
  if (phase.value !== 'bid' || bidTurn.value !== seat) return
  if (value > 0 && value <= highestBid.value) return
  bidLog.value = [...bidLog.value, { seat, value }]
  if (value > highestBid.value) {
    highestBid.value = value
    highestSeat.value = seat
  }
  bidsLeft.value -= 1
  if (highestBid.value === 3 || bidsLeft.value === 0) {
    settleBid()
    return
  }
  bidTurn.value = (seat + 1) % SEATS
  publishRoom()
  drive(bidTurn.value)
}

function settleBid() {
  clearTimer()
  // 三家都不叫:手牌最强的当地主,底分按 1 分算
  if (highestBid.value === 0) {
    const strengths = hands.value.map((hand) => dzBidStrength(hand))
    let best = 0
    for (let i = 1; i < SEATS; i += 1) {
      if (strengths[i] > strengths[best]) best = i
    }
    highestSeat.value = best
    highestBid.value = 1
  }
  landlord.value = highestSeat.value
  multiplier.value = highestBid.value
  hands.value = hands.value.map((hand, seat) => (seat === landlord.value ? [...hand, ...bottom.value] : hand))
  phase.value = 'playing'
  turn.value = landlord.value
  last.value = null
  passCount.value = 0
  bubbles.value = ['', '', '']
  publishRoom()
  drive(turn.value)
}

function commitPlay(seat, combo) {
  const ids = new Set(combo.cards.map((card) => card.id))
  hands.value = hands.value.map((hand, i) => (i === seat ? hand.filter((card) => !ids.has(card.id)) : hand))
  bubbles.value = bubbles.value.map((bubble, i) => (i === seat ? '' : bubble))
  last.value = { seat, combo }
  passCount.value = 0
  playCounts.value = playCounts.value.map((n, i) => (i === seat ? n + 1 : n))
  if (combo.type === DDZ_COMBO.BOMB || combo.type === DDZ_COMBO.ROCKET) multiplier.value *= 2
  selected.value = []
  pendingAuto.value = []
  if (!hands.value[seat].length) {
    finishOrder.value = [...finishOrder.value, seat]
    const farmersDone = [0, 1, 2].filter((s) => s !== landlord.value && finishOrder.value.includes(s)).length
    if (seat === landlord.value || farmersDone === 2) {
      finishGame(seat === landlord.value)
      return
    }
  }
  advanceTurn()
}

function advanceTurn() {
  if (phase.value !== 'playing') return
  for (let i = 1; i <= SEATS; i += 1) {
    const seat = (turn.value + i) % SEATS
    if (hands.value[seat].length) {
      turn.value = seat
      break
    }
  }
  publishRoom()
  drive(turn.value)
}

function passTurn(seat = turn.value) {
  if (phase.value !== 'playing' || !last.value || turn.value !== seat) return
  if (last.value.seat === seat) return
  bubbles.value = bubbles.value.map((bubble, i) => (i === seat ? t('games.fight.pass') : bubble))
  passCount.value += 1
  const active = hands.value.filter((hand) => hand.length).length
  if (passCount.value >= active - 1) {
    last.value = null
    passCount.value = 0
    bubbles.value = ['', '', '']
  }
  advanceTurn()
}

function finishGame(landlordWin) {
  phase.value = 'ended'
  clearTimer()
  const farmerPlays = [0, 1, 2].filter((seat) => seat !== landlord.value).reduce((sum, seat) => sum + playCounts.value[seat], 0)
  if (landlordWin && farmerPlays === 0) {
    multiplier.value *= 2
    springLabel.value = t('games.doudizhu.spring')
  } else if (!landlordWin && playCounts.value[landlord.value] === 1) {
    multiplier.value *= 2
    springLabel.value = t('games.doudizhu.antiSpring')
  }
  const myWin = landlordWin === isLandlord.value
  winnerIsLandlord.value = landlordWin
  result.value = myWin ? 'win' : 'lose'
  if (play.active) {
    // 胜方按倍数记分、负方 0:房间按高分排名,同队的人一起报同一个分
    publishRoom()
    autoReport()
  } else if (myWin) {
    grantReward()
  }
}

/** 房间局:牌局结束各自自动交卷(胜方报倍数分,负方报 0),房主按高分结算 */
function autoReport() {
  if (!play.active || play.reported || phase.value !== 'ended') return
  const mine = isLandlord.value === winnerIsLandlord.value
  play.report(mine ? multiplier.value * 10 : 0)
}

async function grantReward() {
  try {
    const res = await gameApi.doudizhuReward()
    rewardMsg.value = res && res.awarded > 0 ? t('games.rewarded', { n: res.awarded }) : t('games.rewardCapped')
  } catch (e) {
    rewardMsg.value = t('games.rewardFailed')
  }
}

// ---------- 本机交互 ----------

function bidAction(value) {
  if (!isMyBidTurn.value) return
  room.local({ type: 'bid', value })
}

function passAction() {
  if (!myTurn.value) return
  room.local({ type: 'pass' })
}

function toggleAuto() {
  room.setAuto(!room.autoOn)
}

function onResultAction() {
  if (!play.active) return startGame()
  router.push(`/games/room/${play.room?.id}`)
}

function toggleCard(card) {
  selected.value = selected.value.includes(card.id) ? selected.value.filter((id) => id !== card.id) : [...selected.value, card.id]
}

// ---------- 扩选与自动出牌 ----------
// 左键拖选:拖过的牌并入选择(随时可选,不限于自己回合);右键拖选:标记的牌
// 在轮到自己时自动打出(能大过桌面就出),打不出则整体收回(清空选中,牌落回去)。
const pendingAuto = ref([])
function extendSelect(ids) {
  // 拖选 = 逐张翻转:扫过未选的选上、扫过已选的取消(批量取消选中);
  // 触摸点按的轻微抖动也会走上这条(阈值内不打断),翻转语义正好把「点已选牌反选」一并兜住。
  // 拖选途中若电脑换手重排,扫过的旧 id 会失效,过滤到当前手牌防残留点亮别的牌
  const handIds = new Set(myHand.value.map((card) => card.id))
  const set = new Set(selected.value)
  for (const id of ids) {
    if (!handIds.has(id)) continue
    if (set.has(id)) set.delete(id)
    else set.add(id)
  }
  selected.value = [...set]
}
function markAuto(ids) {
  const handIds = new Set(myHand.value.map((card) => card.id))
  pendingAuto.value = ids.filter((id) => handIds.has(id))
  selected.value = [...pendingAuto.value]
  tryAutoPlay()
}
function tryAutoPlay() {
  if (!pendingAuto.value.length || !myTurn.value) return
  const handIds = new Set(myHand.value.map((card) => card.id))
  pendingAuto.value = pendingAuto.value.filter((id) => handIds.has(id))
  selected.value = [...pendingAuto.value]
  if (canPlay.value) {
    playSelected()
    pendingAuto.value = []
  } else {
    pendingAuto.value = []
    selected.value = []
  }
}
watch(myTurn, (turn) => {
  if (turn) tryAutoPlay()
})

function playSelected() {
  if (!canPlay.value) return
  const ids = selection.value.cards.map((card) => card.id)
  if (!room.local({ type: 'play', ids })) return
  pendingAuto.value = []
}

function hint() {
  if (!myTurn.value) return
  const plays = dzPlays(myHand.value, lastCombo.value)
  selected.value = plays.length ? plays[0].cards.map((card) => card.id) : []
  pendingAuto.value = []
}

// ---------- 房间生命周期 ----------

/** 房间开局:房主发牌,访客等第一份公共盘面(先进入 playing 渲染,手牌由 dz:hand 补) */
watch(
  () => [play.active, play.status, play.isHost],
  () => {
    if (!play.active) return
    if (play.status !== 'PLAYING' || phase.value !== 'idle') return
    if (play.isHost) {
      startGame()
      return
    }
    room.syncSeats()
    phase.value = 'playing'
    play.requestSync()
  },
  { immediate: true },
)

/** WS 断线重连后补一次全量:否则访客手牌/盘面停在断线前,直到下一回合 */
watch(
  () => chatStore.socketStatus,
  (status) => {
    if (status !== 'open') return
    if (!play.active || play.isHost || phase.value !== 'playing') return
    play.requestSync()
  },
)

onBeforeUnmount(() => {
  session += 1
  clearTimer()
  wakeTimers.destroy()
})
</script>
