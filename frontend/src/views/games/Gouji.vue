<!-- 够级:六人局(隔座三人一联邦)。同点套牌/贴 2/挂王、够级牌限制、憋三与名次积分都在
     utils/games/gouji.js(纯函数,单测覆盖);本页只做发牌、回合驱动与结算展示。
     「一起玩」房间:房主是唯一裁判(公共盘面 gj:public、各家手牌点对点 gj:hand、访客意图 gj:act),
     六人坐满后多出来的成员进观战位;够级牌只有对头能上,判定一律在裁判端做。
     我方积分领先走统一 H5 奖励通道(按天限次);房间局不发单机奖励。 -->
<template>
  <GameShell
    ref="shellEl"
    class="gj-shell"
    :title="$t('games.gouji.title')"
    body-align="stretch"
    readable
    :room-mode="play.active"
    :default-wide="play.active"
    :new-game="!play.active && phase === 'playing'"
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
        :my-seat="tableSeat"
        :spectator="room.watching"
        :hand="myHand"
        :selected="selected"
        :last-play="lastPlayView"
        :status-text="statusText"
        :info-chips="infoChips"
        :my-name="seatName(mySeat)"
        :my-role-label="myTeamLabel"
        my-tone="royal"
        :my-note="myNote"
        :sort-value="gjValue"
        @select="toggleCard"
        @extend-select="extendSelect"
        @auto-select="markAuto"
      >
        <template #actions>
          <template v-if="phase === 'playing'">
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
                  <template v-if="play.active">
                    <el-button type="primary" size="small" round @click="backToRoom">
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
// 顶牌顺序:单机固定从 0 号座先出,房间里从房主席位先出;够级限制按「场上还有几家有牌」判断,
// 人少了(四人乱缠)就放开对头限制,与规则内核同口径。裁判端复验每一条访客意图后落盘。
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import FightTable from '@/components/games/FightTable.vue'
import GameShell from '@/components/games/GameShell.vue'
import { gameApi } from '@/api'
import { useChatStore } from '@/stores/chat'
import { useRoomChatStore } from '@/stores/roomChat'
import { useGameRoomPlay } from '@/composables/useGameRoomPlay'
import { CARD_ACT_TIMEOUT, useCardRoomPlay } from '@/composables/useCardRoomPlay'
import { seededRng } from '@/utils/games/seededRng'
import { gjAiChoose, gjCanBeat, gjCombo, gjDeal, gjLegal, gjOpposite, gjPlays, gjRankText, gjScores, gjValue } from '@/utils/games/gouji'

const SEATS = 6
const AI_DELAY = 700

const router = useRouter()
const { t } = useI18n()
const chatStore = useChatStore()
const roomChat = useRoomChatStore()

const shellEl = ref(null)
const goBack = () => play.exitToLobby({ aiGame: true })

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

// ---------- 「一起玩」房间(?room=<id>) ----------

// 房间层要挂在 useGameRoomPlay 的信令回调上,先声明再赋值(回调只在挂载后才会到)
let room = null
const play = useGameRoomPlay({ onSignal: (data) => room && room.onSignal(data) })
room = useCardRoomPlay(play, {
  seatCount: SEATS,
  prefix: 'gj',
  getHand: (seat) => hands.value[seat] || [],
  snapshot,
  applyPublic,
  handPayload: (seat) => ({ tiles: hands.value[seat] || [] }),
  applyHand,
})

/** 我的座位:单机固定 0 号位,房间里按成员加入顺序;观战为 -1 */
const mySeat = computed(() => (play.active ? room.mySeat : 0))
const tableSeat = computed(() => Math.max(0, mySeat.value))
const isAiSeat = (seat) => (play.active ? room.isAiSeat(seat) : seat !== mySeat.value)
const myTeam = computed(() => (mySeat.value % 2 === 0 ? 0 : 1))

const myHand = computed(() => hands.value[mySeat.value] || [])
const localCounts = computed(() => hands.value.map((hand) => hand.length))
const handCounts = computed(() => (play.active ? room.counts : localCounts.value))
const myTurn = computed(() => phase.value === 'playing' && turn.value === mySeat.value && !room.autoOn && !play.isSpectator)
const activeCount = computed(() => handCounts.value.filter((n) => n > 0).length)
const lastCombo = computed(() => (last.value ? { ...last.value.combo, seat: last.value.seat } : null))
const selectedCards = computed(() => {
  const ids = new Set(selected.value)
  return myHand.value.filter((card) => ids.has(card.id))
})
const selection = computed(() => (selected.value.length ? gjCombo(selectedCards.value) : null))
const canPlay = computed(() => {
  if (!myTurn.value || !selection.value) return false
  if (!gjLegal(selection.value, myHand.value)) return false
  return gjCanBeat({ ...selection.value, seat: mySeat.value }, lastCombo.value, {
    seat: mySeat.value,
    activeCount: activeCount.value,
  })
})

const seatName = (seat) => {
  const st = play.active ? room.seatAt(seat) : null
  if (st?.userId != null && st.name) return st.name
  if (seat === mySeat.value) return t('games.fight.me')
  return t('games.fight.aiName', { n: seat })
}
const roomBubble = (seat) => {
  if (!play.active) return ''
  const st = room.seatAt(seat)
  return st?.userId != null ? roomChat.bubbleOf(play.room?.id, st.userId) : ''
}
const isMyTeam = (seat) => seat % 2 === mySeat.value % 2
const teamRole = (seat) => {
  const base = isMyTeam(seat) ? t('games.gouji.teamUs') : t('games.gouji.teamThem')
  return seat === gjOpposite(mySeat.value) ? `${base} · ${t('games.gouji.opposite')}` : base
}
const teamTone = (seat) => (isMyTeam(seat) ? 'royal' : 'rebel')
const myTeamLabel = computed(() => (mySeat.value < 0 ? '' : teamRole(mySeat.value)))
const rankLabel = (rank) => t(`games.gouji.rank${rank}`)
/** 席位分数:房间局结束后联邦积分按阵营记(胜方拿自己的分,负方 0) */
const seatScore = (seat) => {
  if (!play.active || phase.value !== 'ended') return 0
  return seat % 2 === myTeam.value && scores.value.teamScores[myTeam.value] > scores.value.teamScores[1 - myTeam.value]
    ? scores.value.teamScores[myTeam.value]
    : 0
}

const seatViews = computed(() =>
  Array.from({ length: SEATS }, (_, seat) => {
    const st = play.active ? room.seatAt(seat) : null
    const order = finishOrder.value.indexOf(seat)
    return {
      name: seatName(seat),
      avatar: st?.avatar || '',
      count: handCounts.value[seat] || 0,
      score: seatScore(seat),
      auto: !!(st && !st.ai && st.auto),
      active: phase.value === 'playing' && turn.value === seat,
      finished: order >= 0,
      roleLabel: teamRole(seat),
      tone: teamTone(seat),
      finishedLabel: order >= 0 ? rankLabel(order + 1) : '',
      bubble: roomBubble(seat),
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
  { key: 'us', label: t('games.gouji.teamUs'), value: `${finishedCount(myTeam.value)}/${SEATS / 2}` },
  { key: 'them', label: t('games.gouji.teamThem'), value: `${finishedCount(1 - myTeam.value)}/${SEATS / 2}` },
])

const statusText = computed(() => {
  if (phase.value === 'idle') return ''
  if (room.watching) return t('games.room.spectatorHint')
  if (phase.value === 'ended') return ''
  if (room.autoOn) return t('games.room.botState')
  if (myTurn.value) return lastCombo.value ? t('games.fight.yourTurn') : t('games.gouji.yourLead')
  return t('games.fight.thinking', { name: seatName(turn.value) })
})

const myNote = computed(() => {
  if (phase.value !== 'playing' || !myTurn.value || !selected.value.length) return ''
  if (!selection.value) return t('games.fight.badCards')
  if (!gjLegal(selection.value, myHand.value)) return t('games.gouji.threeRule')
  if (!gjCanBeat({ ...selection.value, seat: mySeat.value }, lastCombo.value, { seat: mySeat.value, activeCount: activeCount.value })) {
    const restricted = lastCombo.value?.gouji && activeCount.value > 4 && last.value.seat !== gjOpposite(mySeat.value)
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

// ---------- 房间快照 / 落盘 ----------

function snapshot() {
  return {
    phase: phase.value,
    turn: turn.value,
    last: last.value,
    passCount: passCount.value,
    passStreak: passStreak.value,
    counts: localCounts.value,
    finishOrder: finishOrder.value,
    scores: scores.value,
  }
}

function applyPublic(state) {
  if (!state) return
  if (state.phase) phase.value = state.phase
  turn.value = Number(state.turn) || 0
  last.value = state.last || null
  passCount.value = Math.max(0, Number(state.passCount) || 0)
  passStreak.value =
    Array.isArray(state.passStreak) && state.passStreak.length === SEATS
      ? state.passStreak.map((n) => Number(n) || 0)
      : new Array(SEATS).fill(0)
  finishOrder.value = Array.isArray(state.finishOrder) ? state.finishOrder : []
  if (state.scores) {
    const teamScores = Array.isArray(state.scores.teamScores) ? state.scores.teamScores.map((n) => Number(n) || 0) : [0, 0]
    scores.value = { teamScores, shun: !!state.scores.shun }
    if (phase.value === 'ended' && !result.value) {
      result.value = teamScores[myTeam.value] > teamScores[1 - myTeam.value] ? 'win' : 'lose'
      autoReport()
    }
  }
  if (turn.value !== mySeat.value || phase.value !== 'playing') {
    selected.value = []
    pendingAuto.value = []
  }
}

function applyHand(data) {
  const tiles = Array.isArray(data?.tiles) ? data.tiles : []
  const prev = hands.value[mySeat.value] || []
  const same = prev.length === tiles.length && prev.every((card, i) => card.id === tiles[i].id)
  hands.value = hands.value.map((hand, i) => (i === mySeat.value ? tiles : hand))
  if (same) return
  selected.value = []
  pendingAuto.value = []
}

const publishRoom = () => room.publish()

/** 房间局:一局打完各自交卷(胜方联邦报积分、负方 0),房主按高分结算 */
function autoReport() {
  if (!play.active || play.reported || phase.value !== 'ended') return
  const [us, them] = scores.value.teamScores
  play.report(us > them ? us : 0)
}

// ---------- 发牌 ----------

function startGame() {
  if (play.active && !play.isHost) return
  session += 1
  clearTimer()
  hands.value = gjDeal(play.active ? seededRng(play.seed) : Math.random)
  turn.value = 0
  last.value = null
  passCount.value = 0
  selected.value = []
  pendingAuto.value = []
  finishOrder.value = []
  result.value = ''
  rewardMsg.value = ''
  scores.value = { teamScores: [0, 0], shun: false }
  passStreak.value = new Array(SEATS).fill(0)
  phase.value = 'playing'
  publishRoom()
  drive(turn.value)
}

// ---------- 回合驱动(裁判端) ----------

function drive(seat) {
  clearTimer()
  if (!room.authority || play.ended) return
  if (phase.value !== 'playing') return
  const mySession = session
  const apply = (action) => {
    if (mySession !== session || play.ended) return
    if (phase.value !== 'playing' || turn.value !== seat) return
    seatAction(seat, action)
  }
  if (isAiSeat(seat)) {
    aiTimer = setTimeout(() => apply(null), AI_DELAY)
    return
  }
  room.waitAct(seat, play.active ? CARD_ACT_TIMEOUT : 0).then(apply)
}

/** 裁判端落一个出牌意图:电脑的(null)走 AI,真人的先按自己手里的牌复验(够级限制/憋三一起验) */
function seatAction(seat, action) {
  if (!action || action.type === 'pass') {
    // 领出时不能过:交给电脑按最优解出牌,别让一张无效的「过」把回合卡死
    if (action && last.value && last.value.seat !== seat) {
      passTurn(seat)
      return
    }
    const pick = pickAiPlay(seat)
    if (pick) commitPlay(seat, pick)
    else passTurn(seat)
    return
  }
  const ids = new Set(Array.isArray(action.ids) ? action.ids : [])
  const cards = hands.value[seat].filter((card) => ids.has(card.id))
  const combo = cards.length ? gjCombo(cards) : null
  const legal = combo && gjLegal(combo, hands.value[seat])
  const beats = legal && gjCanBeat({ ...combo, seat }, lastCombo.value, { seat, activeCount: activeCount.value })
  if (!beats) {
    const pick = pickAiPlay(seat)
    if (pick) commitPlay(seat, pick)
    else passTurn(seat)
    return
  }
  commitPlay(seat, combo)
}

function pickAiPlay(seat) {
  return gjAiChoose({
    hand: hands.value[seat],
    last: lastCombo.value,
    seat,
    activeCount: activeCount.value,
    handCounts: handCounts.value,
    lastSeat: last.value && passStreak.value[seat] < 2 ? last.value.seat : null,
  })
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
  publishRoom()
  drive(turn.value)
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
  result.value = points.teamScores[myTeam.value] > points.teamScores[1 - myTeam.value] ? 'win' : 'lose'
  if (play.active) {
    publishRoom()
    autoReport()
  } else if (result.value === 'win') {
    grantReward()
  }
}

// ---------- 本机交互 ----------

function passAction() {
  if (!myTurn.value) return
  room.local({ type: 'pass' })
}

function toggleAuto() {
  room.setAuto(!room.autoOn)
}

function backToRoom() {
  if (play.active && play.room?.id) router.push(`/games/room/${play.room.id}`)
  else router.push('/games')
}

function blockedByLink() {
  if (!play.active || play.isHost || chatStore.socketStatus === 'open') return false
  ElMessage.warning(t('games.mahjong.linkLost'))
  return true
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
  if (blockedByLink()) return
  const ids = selection.value.cards.map((card) => card.id)
  if (!room.local({ type: 'play', ids })) return
  pendingAuto.value = []
}

function hint() {
  if (!myTurn.value) return
  const plays = gjPlays(myHand.value, lastCombo.value, {
    seat: mySeat.value,
    activeCount: activeCount.value,
  })
  selected.value = plays.length ? plays[0].cards.map((card) => card.id) : []
  pendingAuto.value = []
}

// ---------- 房间生命周期 ----------

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
})
</script>
