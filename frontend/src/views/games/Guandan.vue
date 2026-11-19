<!-- 掼蛋:单机四人两联邦(玩家 + 三位电脑,对家是搭子)。牌型/逢人配/炸弹梯度/进贡还贡/升级
     都在 utils/games/guandan.js(纯函数,单测覆盖);本页只做发牌、回合驱动与结算展示。
     我方拿下这一局走统一 H5 奖励通道(按天限次)。 -->
<template>
  <GameShell
    ref="shellEl"
    class="gu-shell"
    :title="$t('games.guandan.title')"
    body-align="stretch"
    readable
    :new-game="phase === 'playing'"
    :new-game-confirm="true"
    @back="goBack"
    @new-game="startMatch"
  >
    <template #rules>
      <ul>
        <li>{{ $t('games.guandan.rules1') }}</li>
        <li>{{ $t('games.guandan.rules2') }}</li>
        <li>{{ $t('games.guandan.rules3') }}</li>
        <li>{{ $t('games.guandan.rules4') }}</li>
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
        :is-wild="isWildCard"
        :my-name="$t('games.fight.me')"
        :my-role-label="$t('games.guandan.teamUs')"
        my-tone="royal"
        :my-note="myNote"
        :sort-value="sortValue"
        @select="toggleCard"
        @extend-select="extendSelect"
        @auto-select="markAuto"
      >
        <template #actions>
          <template v-if="phase === 'playing'">
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
          <div v-if="tributeText && phase === 'playing'" class="fight-note">{{ tributeText }}</div>
        </template>

        <template #overlay>
          <Transition name="game-overlay">
            <div v-if="phase === 'idle' || phase === 'ended'" class="fight-overlay">
              <div class="fight-overlay-card game-overlay-card">
                <div v-if="phase === 'ended'" class="fight-overlay-title">
                  {{ matchWin ? $t('games.guandan.matchWin') : result === 'win' ? $t('games.fight.win') : $t('games.fight.lose') }}
                </div>
                <div v-if="phase === 'ended'" class="fight-overlay-meta">{{ resultMeta }}</div>
                <div v-if="rewardMsg" class="fight-overlay-reward">{{ rewardMsg }}</div>
                <div class="fight-overlay-actions">
                  <el-button type="primary" size="small" round @click="nextRound">
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
// 回合驱动与斗地主同构:玩家固定 0 号座,电脑按座位顺序行动,pass 累计到「只剩一家没表态」时清桌。
// 一局打完先结算升级(打几 → 下一局级牌),下一局发牌前自动进贡还贡。
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import FightTable from '@/components/games/FightTable.vue'
import GameShell from '@/components/games/GameShell.vue'
import { gameApi } from '@/api'
import {
  GD_LEVELS,
  gdAdvance,
  gdAiChoose,
  gdBeats,
  gdComboOf,
  gdCombos,
  gdDeal,
  gdIsWild,
  gdLevelText,
  gdTribute,
  gdValue,
} from '@/utils/games/guandan'

const SEATS = 4
const AI_DELAY = 720

const router = useRouter()
const { t } = useI18n()

const shellEl = ref(null)
const goBack = () => router.push('/games')

const phase = ref('idle') // idle | playing | ended
const level = ref(2)
const hands = ref([[], [], [], []])
const turn = ref(0)
const last = ref(null) // { seat, combo }
const passCount = ref(0)
const selected = ref([])
const finishOrder = ref([])
const roundNo = ref(0)
const teamWins = ref([0, 0])
const tributeText = ref('')
const result = ref('')
const matchWin = ref(false)
const rewardMsg = ref('')
const roundInfo = ref({ partnerRank: 0, step: 0 })
// 电脑连续「让牌」次数:联邦出的牌不抢,但连让两次之后要自己推牌,否则一家可能整局很少出牌
const passStreak = ref([0, 0, 0, 0])

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
const selection = computed(() => (selected.value.length ? gdComboOf(selectedCards.value, level.value) : null))
const canPlay = computed(() => myTurn.value && !!selection.value && gdBeats(selection.value, lastCombo.value))
const activeCount = computed(() => hands.value.filter((hand) => hand.length).length)

const seatName = (seat) => (seat === 0 ? t('games.fight.me') : t('games.fight.aiName', { n: seat }))
const isMyTeam = (seat) => seat % 2 === 0
const sortValue = (card) => gdValue(card, level.value)
const isWildCard = (card) => gdIsWild(card, level.value)

const rankLabel = (rank) => t(`games.guandan.rank${rank}`)
const teamRole = (seat) => (isMyTeam(seat) ? t('games.guandan.teamUs') : t('games.guandan.teamThem'))
const teamTone = (seat) => (isMyTeam(seat) ? 'royal' : 'rebel')

const seatViews = computed(() =>
  hands.value.map((hand, seat) => {
    const order = finishOrder.value.indexOf(seat)
    return {
      name: seatName(seat),
      count: hand.length,
      active: phase.value === 'playing' && turn.value === seat,
      finished: order >= 0,
      roleLabel: teamRole(seat),
      tone: teamTone(seat),
      finishedLabel: order >= 0 ? rankLabel(order + 1) : '',
    }
  }),
)

const comboLabel = (combo) => {
  if (!combo) return ''
  return t(`games.guandan.type.${combo.type}`, { n: combo.len })
}

const lastPlayView = computed(() => {
  if (!last.value) return null
  return {
    seat: last.value.seat,
    name: seatName(last.value.seat),
    cards: last.value.combo.cards,
    label: comboLabel(last.value.combo),
  }
})

const infoChips = computed(() => [
  { key: 'level', label: t('games.guandan.level'), value: gdLevelText(level.value) },
  { key: 'round', label: t('games.guandan.round'), value: String(roundNo.value) },
  {
    key: 'score',
    label: t('games.guandan.wins'),
    value: `${teamWins.value[0]} : ${teamWins.value[1]}`,
  },
])

const statusText = computed(() => {
  if (phase.value === 'idle') return ''
  if (phase.value === 'ended') return ''
  if (myTurn.value) return lastCombo.value ? t('games.fight.yourTurn') : t('games.guandan.yourLead')
  return t('games.fight.thinking', { name: seatName(turn.value) })
})

const myNote = computed(() => {
  if (phase.value !== 'playing' || !myTurn.value || !selected.value.length) return ''
  if (!selection.value) return t('games.fight.badCards')
  if (!gdBeats(selection.value, lastCombo.value)) return t('games.fight.notBigEnough')
  return comboLabel(selection.value)
})

const resultMeta = computed(() => {
  if (matchWin.value) return t('games.guandan.matchWinHint')
  const parts = [
    t('games.guandan.partnerRank', { n: roundInfo.value.partnerRank }),
    t('games.guandan.upgrade', { n: roundInfo.value.step }),
    t('games.guandan.levelNow', { level: gdLevelText(level.value) }),
  ]
  return parts.join(' · ')
})

const clearTimer = () => {
  clearTimeout(aiTimer)
  aiTimer = null
}

function startMatch() {
  level.value = 2
  teamWins.value = [0, 0]
  finishOrder.value = []
  roundNo.value = 0
  dealRound(false)
}

function nextRound() {
  if (phase.value === 'idle' || matchWin.value) {
    startMatch()
    return
  }
  dealRound(true)
}

function dealRound(withTribute) {
  session += 1
  clearTimer()
  const playLevel = level.value
  const prevOrder = [...finishOrder.value]
  const dealt = gdDeal()
  let nextHands = dealt
  let leader = 0
  tributeText.value = ''
  if (withTribute && prevOrder.length === SEATS) {
    const res = gdTribute(dealt, playLevel, prevOrder)
    nextHands = res.hands
    leader = res.leader
    if (res.anti) tributeText.value = t('games.guandan.antiTribute')
    else if (res.tribute) {
      tributeText.value = t('games.guandan.tributeMsg', {
        from: seatName(res.tribute.from),
        to: seatName(res.tribute.to),
      })
    }
  }
  hands.value = nextHands
  turn.value = leader
  last.value = null
  passCount.value = 0
  selected.value = []
  pendingAuto.value = []
  finishOrder.value = []
  result.value = ''
  matchWin.value = false
  rewardMsg.value = ''
  roundInfo.value = { partnerRank: 0, step: 0 }
  passStreak.value = [0, 0, 0, 0]
  roundNo.value += 1
  phase.value = 'playing'
  scheduleTick()
}

function scheduleTick() {
  clearTimer()
  if (phase.value !== 'playing' || turn.value === 0) return
  const mySession = session
  const seat = turn.value
  aiTimer = setTimeout(() => {
    if (mySession !== session || phase.value !== 'playing' || turn.value !== seat) return
    const pick = gdAiChoose({
      hand: hands.value[seat],
      last: lastCombo.value,
      level: level.value,
      seat,
      handCounts: handCounts.value,
      lastSeat: last.value && passStreak.value[seat] < 2 ? last.value.seat : null,
    })
    if (pick) commitPlay(seat, pick)
    else passTurn(seat)
  }, AI_DELAY)
}

function commitPlay(seat, combo) {
  const ids = new Set(combo.cards.map((card) => card.id))
  hands.value = hands.value.map((hand, i) => (i === seat ? hand.filter((card) => !ids.has(card.id)) : hand))
  last.value = { seat, combo }
  passCount.value = 0
  passStreak.value = passStreak.value.map((n, i) => (i === seat ? 0 : n))
  selected.value = []
  pendingAuto.value = []
  if (!hands.value[seat].length) {
    finishOrder.value = [...finishOrder.value, seat]
    if (finishOrder.value.length >= SEATS - 1) {
      const rest = Array.from({ length: SEATS }, (_, i) => i).filter((s) => !finishOrder.value.includes(s))
      finishOrder.value = [...finishOrder.value, ...rest]
      endRound(finishOrder.value[0])
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
  passCount.value += 1
  passStreak.value = passStreak.value.map((n, i) => (i === seat ? n + 1 : n))
  if (passCount.value >= activeCount.value - 1) {
    last.value = null
    passCount.value = 0
  }
  advanceTurn()
}

function endRound(winnerSeat) {
  phase.value = 'ended'
  clearTimer()
  const partnerRank = finishOrder.value.indexOf((winnerSeat + 2) % SEATS) + 1
  const step = gdAdvance(partnerRank)
  const winnerTeam = winnerSeat % 2
  const wonAtAce = level.value === 14
  if (wonAtAce) {
    matchWin.value = true
  } else {
    const idx = GD_LEVELS.indexOf(level.value)
    level.value = GD_LEVELS[Math.min(GD_LEVELS.length - 1, idx + step)]
  }
  teamWins.value = teamWins.value.map((n, i) => (i === winnerTeam ? n + 1 : n))
  roundInfo.value = { partnerRank, step }
  const myWin = winnerTeam === 0
  result.value = myWin ? 'win' : 'lose'
  if (myWin) grantReward()
}

async function grantReward() {
  try {
    const res = await gameApi.guandanReward()
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
  // 拖选途中若电脑换手重排,扫过的旧 id 会失效,过滤到当前手牌防残留点亮别的牌
  const handIds = new Set(myHand.value.map((card) => card.id))
  selected.value = [...new Set([...selected.value, ...ids])].filter((id) => handIds.has(id))
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
  const combos = gdCombos(myHand.value, level.value)
  const plays = lastCombo.value ? combos.filter((combo) => gdBeats(combo, lastCombo.value)) : combos
  if (!plays.length) {
    selected.value = []
    pendingAuto.value = []
    return
  }
  const sorted = [...plays].sort((a, b) => Number(a.bomb) - Number(b.bomb) || a.rank - b.rank || b.len - a.len)
  selected.value = sorted[0].cards.map((card) => card.id)
}

onBeforeUnmount(() => {
  session += 1
  clearTimer()
})
</script>
