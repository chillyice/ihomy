<!-- 够级:单机六人局(玩家 + 五位电脑,隔座三人一联邦)。同点套牌/贴 2/挂王、够级牌限制、
     憋三与名次积分都在 utils/games/gouji.js(纯函数,单测覆盖);本页只做发牌、回合驱动与结算展示。
     我方积分领先走统一 H5 奖励通道(按天限次)。 -->
<template>
  <GameShell
    ref="shellEl"
    class="gj-shell"
    :title="$t('games.gouji.title')"
    body-align="stretch"
    readable
    :new-game="phase === 'playing'"
    :new-game-confirm="true"
    @back="goBack"
    @new-game="startGame"
  >
    <template #rules>
      <ul>
        <li>{{ $t('games.gouji.rules1') }}</li>
        <li>{{ $t('games.gouji.rules2') }}</li>
        <li>{{ $t('games.gouji.rules3') }}</li>
        <li>{{ $t('games.gouji.rules4') }}</li>
        <li>{{ $t('games.gouji.rules5') }}</li>
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
        :my-role-label="$t('games.gouji.teamUs')"
        my-tone="royal"
        :my-note="myNote"
        :sort-value="gjValue"
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
          <div v-if="phase === 'playing' && lastCombo?.gouji" class="fight-note">
            {{ $t('games.gouji.goujiOnTable') }}
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
    <!-- 出牌/叫分键的第二摆放:手机横屏进外壳左栏底边(宠物连连看同款竖排),
         竖屏与桌面用牌桌下方那颗(FightTable 的 #actions);两份按档互斥,见 main.css 对抗牌局段 -->
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
  </GameShell>
</template>

<script setup>
// 顶牌顺序:玩家固定 0 号座,每局从 0 号先出;够级限制按「场上还有几家有牌」判断,
// 人少了(四人乱缠)就放开对头限制,与规则内核同口径。
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import FightTable from '@/components/games/FightTable.vue'
import GameShell from '@/components/games/GameShell.vue'
import { gameApi } from '@/api'
import { gjAiChoose, gjCanBeat, gjCombo, gjDeal, gjLegal, gjOpposite, gjPlays, gjRankText, gjScores, gjValue } from '@/utils/games/gouji'

const SEATS = 6
const AI_DELAY = 700

const router = useRouter()
const { t } = useI18n()

const shellEl = ref(null)
const goBack = () => router.push('/games')

const phase = ref('idle') // idle | playing | ended
const hands = ref([[], [], [], [], [], []])
const turn = ref(0)
const last = ref(null) // { seat, combo }
const passCount = ref(0)
const selected = ref([])
const finishOrder = ref([])
const result = ref('')
const rewardMsg = ref('')
const scores = ref({ teamScores: [0, 0], shun: false })
// 电脑连续「让牌」次数:联邦出的牌不抢,但连让两次之后要自己推牌,否则一家可能整局很少出牌
const passStreak = ref([0, 0, 0, 0, 0, 0])

let session = 0
let aiTimer = null

const myHand = computed(() => hands.value[0])
const handCounts = computed(() => hands.value.map((hand) => hand.length))
const myTurn = computed(() => phase.value === 'playing' && turn.value === 0)
const activeCount = computed(() => hands.value.filter((hand) => hand.length).length)
const lastCombo = computed(() => (last.value ? { ...last.value.combo, seat: last.value.seat } : null))
const selectedCards = computed(() => {
  const ids = new Set(selected.value)
  return myHand.value.filter((card) => ids.has(card.id))
})
const selection = computed(() => (selected.value.length ? gjCombo(selectedCards.value) : null))
const canPlay = computed(() => {
  if (!myTurn.value || !selection.value) return false
  if (!gjLegal(selection.value, myHand.value)) return false
  return gjCanBeat({ ...selection.value, seat: 0 }, lastCombo.value, {
    seat: 0,
    activeCount: activeCount.value,
  })
})

const seatName = (seat) => (seat === 0 ? t('games.fight.me') : t('games.fight.aiName', { n: seat }))
const isMyTeam = (seat) => seat % 2 === 0
const teamRole = (seat) => {
  const base = isMyTeam(seat) ? t('games.gouji.teamUs') : t('games.gouji.teamThem')
  return seat === gjOpposite(0) ? `${base} · ${t('games.gouji.opposite')}` : base
}
const teamTone = (seat) => (isMyTeam(seat) ? 'royal' : 'rebel')
const rankLabel = (rank) => t(`games.gouji.rank${rank}`)

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
  const parts = []
  if (combo.baseRank > 0) parts.push(gjRankText(combo.baseRank))
  if (combo.twos) parts.push(t('games.gouji.attachTwo', { n: combo.twos }))
  if (combo.jokers) parts.push(t('games.gouji.attachJoker', { n: combo.jokers }))
  return parts.join(' + ')
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

const finishedCount = (team) => finishOrder.value.filter((seat) => seat % 2 === team).length

const infoChips = computed(() => [
  { key: 'us', label: t('games.gouji.teamUs'), value: `${finishedCount(0)}/${SEATS / 2}` },
  { key: 'them', label: t('games.gouji.teamThem'), value: `${finishedCount(1)}/${SEATS / 2}` },
])

const statusText = computed(() => {
  if (phase.value === 'idle') return ''
  if (phase.value === 'ended') return ''
  if (myTurn.value) return lastCombo.value ? t('games.fight.yourTurn') : t('games.gouji.yourLead')
  return t('games.fight.thinking', { name: seatName(turn.value) })
})

const myNote = computed(() => {
  if (phase.value !== 'playing' || !myTurn.value || !selected.value.length) return ''
  if (!selection.value) return t('games.fight.badCards')
  if (!gjLegal(selection.value, myHand.value)) return t('games.gouji.threeRule')
  if (!gjCanBeat({ ...selection.value, seat: 0 }, lastCombo.value, { seat: 0, activeCount: activeCount.value })) {
    const restricted = lastCombo.value?.gouji && activeCount.value > 4 && last.value.seat !== gjOpposite(0)
    return restricted ? t('games.gouji.onlyOpposite') : t('games.fight.notBigEnough')
  }
  return comboLabel(selection.value)
})

const resultMeta = computed(() => {
  const parts = [t('games.gouji.teamScore', { a: scores.value.teamScores[0], b: scores.value.teamScores[1] })]
  if (scores.value.shun) parts.push(t('games.gouji.shun'))
  return parts.join(' · ')
})

const clearTimer = () => {
  clearTimeout(aiTimer)
  aiTimer = null
}

function startGame() {
  session += 1
  clearTimer()
  hands.value = gjDeal()
  turn.value = 0
  last.value = null
  passCount.value = 0
  selected.value = []
  pendingAuto.value = []
  finishOrder.value = []
  result.value = ''
  rewardMsg.value = ''
  scores.value = { teamScores: [0, 0], shun: false }
  passStreak.value = [0, 0, 0, 0, 0, 0]
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
    const pick = gjAiChoose({
      hand: hands.value[seat],
      last: lastCombo.value,
      seat,
      activeCount: activeCount.value,
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
      endGame()
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

function endGame() {
  phase.value = 'ended'
  clearTimer()
  const points = gjScores(finishOrder.value)
  scores.value = { teamScores: points.teamScores, shun: points.shun }
  const myWin = points.teamScores[0] > points.teamScores[1]
  result.value = myWin ? 'win' : 'lose'
  if (myWin) grantReward()
}

async function grantReward() {
  try {
    const res = await gameApi.goujiReward()
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
  const plays = gjPlays(myHand.value, lastCombo.value, {
    seat: 0,
    activeCount: activeCount.value,
  })
  selected.value = plays.length ? plays[0].cards.map((card) => card.id) : []
  pendingAuto.value = []
}

onBeforeUnmount(() => {
  session += 1
  clearTimer()
})
</script>
