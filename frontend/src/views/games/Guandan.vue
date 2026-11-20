<!-- 掼蛋:四人两联邦(对家是搭子,「一起玩」房间里换成真人/AI 混坐)。牌型/逢人配/炸弹梯度/进贡还贡/升级
     都在 utils/games/guandan.js(纯函数,单测覆盖);本页只做发牌、回合驱动与结算展示。
     房间局房主是唯一裁判(公共盘面 gd:public、各家手牌点对点 gd:hand、访客意图 gd:act),
     一局一结算:升级/进贡只对单机的连局有意义,房间打一局就回房间页开下一局。
     我方拿下这一局走统一 H5 奖励通道(按天限次);房间局不发单机奖励。 -->
<template>
  <GameShell
    ref="shellEl"
    class="gu-shell"
    :title="$t('games.guandan.title')"
    body-align="stretch"
    readable
    :room-mode="play.active"
    :default-wide="play.active"
    :new-game="!play.active && phase === 'playing'"
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
        :my-seat="tableSeat"
        :spectator="room.watching"
        :hand="myHand"
        :selected="selected"
        :last-play="lastPlayView"
        :status-text="statusText"
        :info-chips="infoChips"
        :is-wild="isWildCard"
        :my-name="seatName(mySeat)"
        :my-role-label="teamRole(mySeat)"
        :my-tone="teamTone(mySeat)"
        :my-note="myNote"
        :sort-value="sortValue"
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
                  <template v-if="play.active">
                    <el-button type="primary" size="small" round @click="backToRoom">
                      {{ $t('games.room.backRoom') }}
                    </el-button>
                  </template>
                  <template v-else>
                    <el-button type="primary" size="small" round @click="nextRound">
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
// 回合驱动与斗地主同构:裁判端(单机 = 本机,房间 = 房主)推牌局,pass 累计到「只剩一家没表态」时清桌。
// 单机连局:一局打完先结算升级(打几 → 下一局级牌),下一局发牌前自动进贡还贡;房间局打一局即结束。
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
const chatStore = useChatStore()
const roomChat = useRoomChatStore()

const shellEl = ref(null)
const goBack = () => play.exitToLobby({ aiGame: true })

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
/** 房间局这一局谁赢(0/1 联邦;-1 = 未结束),各端据此算自己的胜负与交卷分 */
const winnerTeam = ref(-1)

const passStreak = ref([0, 0, 0, 0])

let session = 0
let aiTimer = null

// ---------- 「一起玩」房间(?room=<id>) ----------

// 房间层要挂在 useGameRoomPlay 的信令回调上,先声明再赋值(回调只在挂载后才会到)
let room = null
const play = useGameRoomPlay({ onSignal: (data) => room && room.onSignal(data) })
room = useCardRoomPlay(play, {
  seatCount: SEATS,
  prefix: 'gd',
  getHand: (seat) => hands.value[seat] || [],
  snapshot,
  applyPublic,
  handPayload: (seat) => ({ tiles: hands.value[seat] || [] }),
  applyHand,
})

/** 我的座位:单机固定 0 号位,房间里按成员加入顺序(联邦 = 座位奇偶,所以座位不能随手换) */
const mySeat = computed(() => (play.active ? room.mySeat : 0))
const tableSeat = computed(() => Math.max(0, mySeat.value))
const isAiSeat = (seat) => (play.active ? room.isAiSeat(seat) : seat !== mySeat.value)

const myHand = computed(() => hands.value[mySeat.value] || [])
const localCounts = computed(() => hands.value.map((hand) => hand.length))
const handCounts = computed(() => (play.active ? room.counts : localCounts.value))
const myTurn = computed(() => phase.value === 'playing' && turn.value === mySeat.value && !room.autoOn && !play.isSpectator)
const lastCombo = computed(() => last.value?.combo || null)
const selectedCards = computed(() => {
  const ids = new Set(selected.value)
  return myHand.value.filter((card) => ids.has(card.id))
})
const selection = computed(() => (selected.value.length ? gdComboOf(selectedCards.value, level.value) : null))
const canPlay = computed(() => myTurn.value && !!selection.value && gdBeats(selection.value, lastCombo.value))
const activeCount = computed(() => handCounts.value.filter((n) => n > 0).length)

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
const sortValue = (card) => gdValue(card, level.value)
const isWildCard = (card) => gdIsWild(card, level.value)

const rankLabel = (rank) => t(`games.guandan.rank${rank}`)
const teamRole = (seat) => (isMyTeam(seat) ? t('games.guandan.teamUs') : t('games.guandan.teamThem'))
const teamTone = (seat) => (isMyTeam(seat) ? 'royal' : 'rebel')
/** 席位分数:房间局结束后胜方联邦记升级步数,负方 0(与上报交卷同口径) */
const seatScore = (seat) => {
  if (!play.active || phase.value !== 'ended' || winnerTeam.value < 0) return 0
  return seat % 2 === winnerTeam.value ? roundInfo.value.step * 10 : 0
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
      active: phase.value === 'playing' && turn.value === seat,
      finished: finishOrder.value.includes(seat),
      roleLabel: teamRole(seat),
      tone: teamTone(seat),
      finishedLabel: finishOrder.value.includes(seat) ? rankLabel(finishOrder.value.indexOf(seat) + 1) : '',
      bubble: roomBubble(seat),
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

const infoChips = computed(() => {
  const chips = [
    { key: 'level', label: t('games.guandan.level'), value: gdLevelText(level.value) },
    { key: 'round', label: t('games.guandan.round'), value: String(roundNo.value) },
  ]
  // 房间打一局即结束,联邦胜场没有累计意义,只留级牌与本局
  if (!play.active) {
    chips.push({ key: 'score', label: t('games.guandan.wins'), value: `${teamWins.value[0]} : ${teamWins.value[1]}` })
  }
  return chips
})

const statusText = computed(() => {
  if (phase.value === 'idle') return ''
  if (room.watching) return t('games.room.spectatorHint')
  // 房间局回合倒计时(裁判端起步、各端本地滴答;单机不限时不显示)
  const seconds = Number(room.actSecondsLeft) || 0
  const countdown = seconds > 0 ? ` ${t('games.fight.turnCountdown', { n: seconds })}` : ''
  if (phase.value === 'ended') return ''
  if (room.autoOn) return t('games.room.botState')
  if (myTurn.value) {
    return (lastCombo.value ? t('games.fight.yourTurn') : t('games.guandan.yourLead')) + countdown
  }
  return t('games.fight.thinking', { name: seatName(turn.value) }) + countdown
})

const myNote = computed(() => {
  if (phase.value !== 'playing' || !myTurn.value || !selected.value.length) return ''
  if (!selection.value) return t('games.fight.badCards')
  if (!gdBeats(selection.value, lastCombo.value)) return t('games.fight.notBigEnough')
  return comboLabel(selection.value)
})

const resultMeta = computed(() => {
  if (matchWin.value) return t('games.guandan.matchWinHint')
  const parts = [t('games.guandan.partnerRank', { n: roundInfo.value.partnerRank })]
  if (!play.active) {
    parts.push(t('games.guandan.upgrade', { n: roundInfo.value.step }), t('games.guandan.levelNow', { level: gdLevelText(level.value) }))
  }
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
    level: level.value,
    turn: turn.value,
    last: last.value,
    passCount: passCount.value,
    passStreak: passStreak.value,
    counts: localCounts.value,
    finishOrder: finishOrder.value,
    roundNo: roundNo.value,
    teamWins: teamWins.value,
    roundInfo: roundInfo.value,
    tribute: tributeText.value,
    winnerTeam: phase.value === 'ended' ? winnerTeam.value : -1,
  }
}

function applyPublic(state) {
  if (!state) return
  if (state.phase) phase.value = state.phase
  level.value = Math.max(2, Math.min(14, Number(state.level) || 2))
  turn.value = Number(state.turn) || 0
  last.value = state.last || null
  passCount.value = Math.max(0, Number(state.passCount) || 0)
  passStreak.value =
    Array.isArray(state.passStreak) && state.passStreak.length === SEATS ? state.passStreak.map((n) => Number(n) || 0) : [0, 0, 0, 0]
  finishOrder.value = Array.isArray(state.finishOrder) ? state.finishOrder : []
  roundNo.value = Math.max(0, Number(state.roundNo) || 0)
  teamWins.value = Array.isArray(state.teamWins) && state.teamWins.length === 2 ? state.teamWins.map((n) => Number(n) || 0) : [0, 0]
  roundInfo.value = state.roundInfo || { partnerRank: 0, step: 0 }
  tributeText.value = state.tribute || ''
  if (state.winnerTeam != null && state.winnerTeam >= 0) {
    winnerTeam.value = Number(state.winnerTeam)
    result.value = winnerTeam.value === mySeat.value % 2 ? 'win' : 'lose'
  }
  if (turn.value !== mySeat.value || phase.value !== 'playing') {
    selected.value = []
    pendingAuto.value = []
  }
  if (phase.value === 'ended') autoReport()
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

// ---------- 发牌 / 连局 ----------

function startMatch() {
  if (play.active && !play.isHost) return
  level.value = 2
  teamWins.value = [0, 0]
  finishOrder.value = []
  roundNo.value = 0
  winnerTeam.value = -1
  dealRound(false)
}

function nextRound() {
  if (play.active) {
    backToRoom()
    return
  }
  if (phase.value === 'idle' || matchWin.value) {
    startMatch()
    return
  }
  dealRound(true)
}

function dealRound(withTribute) {
  if (play.active && !play.isHost) return
  session += 1
  clearTimer()
  const playLevel = level.value
  const prevOrder = [...finishOrder.value]
  const dealt = gdDeal(play.active ? seededRng(play.seed) : Math.random)
  let nextHands = dealt
  let leader = 0
  tributeText.value = ''
  // 进贡还贡是连局规则:房间打一局就结束,不做进贡
  if (!play.active && withTribute && prevOrder.length === SEATS) {
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
  winnerTeam.value = -1
  matchWin.value = false
  rewardMsg.value = ''
  roundInfo.value = { partnerRank: 0, step: 0 }
  passStreak.value = [0, 0, 0, 0]
  roundNo.value += 1
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

/** 裁判端落一个出牌意图:电脑的(null)走 AI,真人的先按自己手里的牌复验再落盘 */
function seatAction(seat, action) {
  if (!action || action.type === 'pass') {
    if (action && action.type === 'pass' && last.value && last.value.seat !== seat) {
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
  const combo = cards.length ? gdComboOf(cards, level.value) : null
  if (!combo || !gdBeats(combo, lastCombo.value)) {
    const pick = pickAiPlay(seat)
    if (pick) commitPlay(seat, pick)
    else passTurn(seat)
    return
  }
  commitPlay(seat, combo)
}

function pickAiPlay(seat) {
  return gdAiChoose({
    hand: hands.value[seat],
    last: lastCombo.value,
    level: level.value,
    seat,
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

function endRound(winnerSeat) {
  phase.value = 'ended'
  clearTimer()
  winnerTeam.value = winnerSeat % 2
  const partnerRank = finishOrder.value.indexOf((winnerSeat + 2) % SEATS) + 1
  const step = gdAdvance(partnerRank)
  roundInfo.value = { partnerRank, step }
  // 升级只对单机连局有意义:房间打一局即结束,回去开下一局
  if (!play.active) {
    if (level.value === 14) {
      matchWin.value = true
    } else {
      const idx = GD_LEVELS.indexOf(level.value)
      level.value = GD_LEVELS[Math.min(GD_LEVELS.length - 1, idx + step)]
    }
    teamWins.value = teamWins.value.map((n, i) => (i === winnerTeam.value ? n + 1 : n))
  }
  const myWin = winnerTeam.value === mySeat.value % 2
  result.value = myWin ? 'win' : 'lose'
  if (play.active) {
    publishRoom()
    autoReport()
  } else if (myWin) {
    grantReward()
  }
}

/** 房间局:一局打完各自交卷(胜方联邦报升级步数,负方报 0),房主按高分结算 */
function autoReport() {
  if (!play.active || play.reported || phase.value !== 'ended' || winnerTeam.value < 0) return
  const mine = winnerTeam.value === mySeat.value % 2
  play.report(mine ? roundInfo.value.step * 10 : 0)
}

async function grantReward() {
  try {
    const res = await gameApi.guandanReward()
    rewardMsg.value = res && res.awarded > 0 ? t('games.rewarded', { n: res.awarded }) : t('games.rewardCapped')
  } catch (e) {
    rewardMsg.value = t('games.rewardFailed')
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

function toggleCard(card) {
  selected.value = selected.value.includes(card.id) ? selected.value.filter((id) => id !== card.id) : [...selected.value, card.id]
}

// ---------- 扩选与自动出牌 ----------
const pendingAuto = ref([])
function extendSelect(ids) {
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

// ---------- 房间生命周期 ----------

watch(
  () => [play.active, play.status, play.isHost],
  () => {
    if (!play.active) return
    if (play.status !== 'PLAYING' || phase.value !== 'idle') return
    if (play.isHost) {
      startMatch()
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
