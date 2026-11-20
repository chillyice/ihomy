<!-- 斗地主:单机三人局(玩家 + 两位电脑)。叫分抢地主、常见牌型判定与电脑选牌都在
     utils/games/doudizhu.js(纯函数,单测覆盖);本页只做发牌、叫分、回合驱动与结算展示。
     赢下对局走统一 H5 奖励通道(按天限次)。 -->
<template>
  <GameShell
    ref="shellEl"
    class="dz-shell"
    :title="$t('games.doudizhu.title')"
    body-align="stretch"
    readable
    :new-game="phase === 'playing' || phase === 'bid'"
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
        :hand="myHand"
        :selected="selected"
        :last-play="lastPlayView"
        :status-text="statusText"
        :info-chips="infoChips"
        :my-name="$t('games.fight.me')"
        :my-role-label="myRoleLabel"
        :my-tone="myTone"
        :my-note="myNote"
        :sort-value="dzValue"
        @select="toggleCard"
        @extend-select="extendSelect"
        @auto-select="markAuto"
      >
        <template #actions>
          <template v-if="phase === 'bid' && bidTurn === 0">
            <el-button size="small" round @click="bid(0, 0)">{{ $t('games.doudizhu.bidPass') }}</el-button>
            <el-button
              v-for="v in [1, 2, 3]"
              :key="v"
              size="small"
              round
              :type="v === 3 ? 'primary' : 'default'"
              :disabled="v <= highestBid"
              @click="bid(0, v)"
            >
              {{ $t('games.doudizhu.bidN', { n: v }) }}
            </el-button>
          </template>
          <template v-else-if="phase === 'playing'">
            <el-button size="small" round :disabled="!myTurn || !lastCombo" @click="passTurn()">
              {{ $t('games.fight.pass') }}
            </el-button>
            <el-button size="small" round :disabled="!myTurn" @click="hint">{{ $t('games.fight.hint') }}</el-button>
            <el-button type="primary" size="small" round :disabled="!canPlay" @click="playSelected">
              {{ $t('games.fight.play') }}
            </el-button>
          </template>
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
                  <el-button type="primary" size="small" round @click="startGame">
                    {{ phase === 'idle' ? $t('games.fight.start') : $t('games.fight.again') }}
                  </el-button>
                  <el-button v-if="phase === 'ended'" size="small" round @click="goBack">
                    {{ $t('games.back') }}
                  </el-button>
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
// 回合驱动:玩家固定 0 号座,两位电脑由 setTimeout 依次行动;每次重开/离开把 session +1,
// 旧定时器醒来发现令牌变了就自行退出,避免切页后还在改状态。
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import FightTable from '@/components/games/FightTable.vue'
import GameShell from '@/components/games/GameShell.vue'
import { gameApi } from '@/api'
import { cardRankText } from '@/utils/games/cardCore'
import { DDZ_COMBO, dzAiBid, dzAiChoose, dzBeats, dzBidStrength, dzCombo, dzDeal, dzPlays, dzValue } from '@/utils/games/doudizhu'

const SEATS = 3
const AI_DELAY = 760
const BID_DELAY = 620

const router = useRouter()
const { t } = useI18n()

const shellEl = ref(null)
const goBack = () => router.push('/games')

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
const rewardMsg = ref('')
const springLabel = ref('')
const playCounts = ref([0, 0, 0])
const bubbles = ref(['', '', ''])

let session = 0
let aiTimer = null

const myHand = computed(() => hands.value[0])
const handCounts = computed(() => hands.value.map((hand) => hand.length))
const myTurn = computed(() => phase.value === 'playing' && turn.value === 0)
const lastCombo = computed(() => last.value?.combo || null)
const selectedCards = computed(() => {
  const ids = new Set(selected.value)
  return myHand.value.filter((card) => ids.has(card.id))
})
const selection = computed(() => (selected.value.length ? dzCombo(selectedCards.value) : null))
const canPlay = computed(() => myTurn.value && !!selection.value && dzBeats(selection.value, lastCombo.value))
const isLandlord = computed(() => landlord.value === 0)
const myRoleLabel = computed(() => roleOf(0))
const myTone = computed(() => (landlord.value < 0 ? '' : toneOf(0)))

const seatName = (seat) => (seat === 0 ? t('games.fight.me') : t('games.fight.aiName', { n: seat }))
const toneOf = (seat) => (seat === landlord.value ? 'landlord' : 'farmer')
const roleOf = (seat) => {
  if (landlord.value < 0) return ''
  return seat === landlord.value ? t('games.doudizhu.landlord') : t('games.doudizhu.farmer')
}
const comboLabel = (combo) => (combo ? t(`games.doudizhu.combo.${combo.type}`) : '')

const seatViews = computed(() =>
  hands.value.map((hand, seat) => ({
    name: seatName(seat),
    count: hand.length,
    active: phase.value === 'bid' ? bidTurn.value === seat : phase.value === 'playing' && turn.value === seat,
    finished: finishOrder.value.includes(seat),
    roleLabel: roleOf(seat),
    tone: toneOf(seat),
    finishedLabel: finishOrder.value.includes(seat) ? t('games.fight.out') : '',
    bubble: bubbles.value[seat] || '',
  })),
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
  if (phase.value === 'bid') {
    return bidTurn.value === 0 ? t('games.doudizhu.bidHint') : t('games.doudizhu.bidWait', { name: seatName(bidTurn.value) })
  }
  if (phase.value === 'ended') return ''
  if (myTurn.value) return lastCombo.value ? t('games.fight.yourTurn') : t('games.doudizhu.yourLead')
  return t('games.fight.thinking', { name: seatName(turn.value) })
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
  clearTimeout(aiTimer)
  aiTimer = null
}

function bidText(entry) {
  return entry.value > 0
    ? t('games.doudizhu.bidLog', { name: seatName(entry.seat), n: entry.value })
    : t('games.doudizhu.bidLogPass', { name: seatName(entry.seat) })
}

function startGame() {
  session += 1
  clearTimer()
  const deal = dzDeal()
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
  bidTurn.value = Math.floor(Math.random() * SEATS)
  scheduleBid()
}

/** 叫分:0 = 不叫,1..3 必须高于当前最高分;每家各一次机会 */
function bid(seat, value) {
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
  scheduleBid()
}

function scheduleBid() {
  clearTimer()
  if (phase.value !== 'bid' || bidTurn.value === 0) return
  const mySession = session
  const seat = bidTurn.value
  aiTimer = setTimeout(() => {
    if (mySession !== session || phase.value !== 'bid' || bidTurn.value !== seat) return
    const want = dzAiBid(hands.value[seat])
    bid(seat, want > highestBid.value ? want : 0)
  }, BID_DELAY)
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
  scheduleTick()
}

function scheduleTick() {
  clearTimer()
  if (phase.value !== 'playing' || turn.value === 0) return
  const mySession = session
  const seat = turn.value
  aiTimer = setTimeout(() => {
    if (mySession !== session || phase.value !== 'playing' || turn.value !== seat) return
    const pick = dzAiChoose({
      hand: hands.value[seat],
      last: lastCombo.value,
      seat,
      landlord: landlord.value,
      handCounts: handCounts.value,
      lastSeat: last.value ? last.value.seat : null,
      playCounts: playCounts.value,
    })
    if (pick) commitPlay(seat, pick)
    else passTurn(seat)
  }, AI_DELAY)
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
  scheduleTick()
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
  result.value = myWin ? 'win' : 'lose'
  if (myWin) grantReward()
}

async function grantReward() {
  try {
    const res = await gameApi.doudizhuReward()
    rewardMsg.value = res && res.awarded > 0 ? t('games.rewarded', { n: res.awarded }) : t('games.rewardCapped')
  } catch (e) {
    rewardMsg.value = t('games.rewardFailed')
  }
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
  commitPlay(0, selection.value)
}

function hint() {
  if (!myTurn.value) return
  const plays = dzPlays(myHand.value, lastCombo.value)
  selected.value = plays.length ? plays[0].cards.map((card) => card.id) : []
  pendingAuto.value = []
}

onBeforeUnmount(() => {
  session += 1
  clearTimer()
})
</script>
