<!-- 保皇:五人局(皇帝 + 侍卫对抗三名革命党)。皇帝牌/侍卫牌、同点套牌与王挂靠的比较、电脑选牌都在
     utils/games/baohuang.js(纯函数,单测覆盖);本页只做发牌、身份公布、回合驱动与结算展示。
     「一起玩」房间:房主是唯一裁判(公共盘面 bh:public、各家手牌点对点 bh:hand、访客意图 bh:act);
     侍卫身份不能随公共盘面外播,只跟着私牌一起定向发给本人,明保后房主才广播侍卫席位。
     我方(保皇派或革命党)赢下对局走统一 H5 奖励通道(按天限次);房间局不发单机奖励。 -->
<template>
  <GameShell
    ref="shellEl"
    class="bh-shell"
    :title="$t('games.baohuang.title')"
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
        <li>{{ $t('games.baohuang.rules1') }}</li>
        <li>{{ $t('games.baohuang.rules2') }}</li>
        <li>{{ $t('games.baohuang.rules3') }}</li>
        <li>{{ $t('games.baohuang.rules4') }}</li>
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
        :sort-value="bhValue"
        @select="toggleCard"
        @extend-select="extendSelect"
        @auto-select="markAuto"
      >
        <template #actions>
          <template v-if="phase === 'playing'">
            <el-button v-if="canReveal" size="small" round @click="revealGuard">
              {{ $t('games.baohuang.revealGuard') }}
            </el-button>
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
// 身份:皇帝(公开)/ 侍卫(默认暗保,可明保)/ 革命党。暗保时电脑革命党把侍卫当自己人,
// 只有明保或自己就是侍卫时才认得出来,这样隐身护主的味道才在。
// 房间接法与斗地主同构:裁判端(单机 = 本机,房间 = 房主)推牌局、判身份,访客只发意图。
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
import { createWakeTimers } from '@/utils/games/wakeTimers'
import { seededRng } from '@/utils/games/seededRng'
import { bhAiChoose, bhBeats, bhCombo, bhDeal, bhPlays, bhRankText, bhRoles, bhStrength, bhValue } from '@/utils/games/baohuang'

const SEATS = 5
const AI_DELAY = 720

const router = useRouter()
const { t } = useI18n()
const chatStore = useChatStore()
const roomChat = useRoomChatStore()

const shellEl = ref(null)
const goBack = () => play.exitToLobby({ aiGame: true })

const phase = ref('idle') // idle | playing | ended
const hands = ref([[], [], [], [], []])
const roles = ref({ emperor: -1, guard: -1, solo: false, royal: [] })
const guardRevealed = ref(false)
/** 本人的身份(皇帝/侍卫/革命党):侍卫不能随公共盘面外播,只跟着私牌一起定向下发 */
const myRoleName = ref('')
const turn = ref(0)
const last = ref(null) // { seat, combo }
const passCount = ref(0)
const selected = ref([])
const finishOrder = ref([])
const result = ref('')
const rewardMsg = ref('')
/** 本局保皇派是否赢下(结束后随公共盘面下发,各端据此算自己的胜负与交卷分) */
const royalWin = ref(null)
// 电脑连续「让牌」次数:队友的颜色牌不抢,但一直让会让某个革命党整局一张不出、看着别人打完,
// 所以连让两次之后就当自己也得推牌(见 pickAiPlay 的 lastIsAlly 判定)。
const passStreak = ref([0, 0, 0, 0, 0])

let session = 0
let aiTimer = null
const wakeTimers = createWakeTimers() // 电脑停顿登记:后台标签页节流 setTimeout,回前台时到期的立即补跑

// ---------- 「一起玩」房间(?room=<id>) ----------

// 房间层要挂在 useGameRoomPlay 的信令回调上,先声明再赋值(回调只在挂载后才会到)
let room = null
const play = useGameRoomPlay({ onSignal: handleSignal })
room = useCardRoomPlay(play, {
  seatCount: SEATS,
  prefix: 'bh',
  getHand: (seat) => hands.value[seat] || [],
  snapshot,
  applyPublic,
  handPayload: (seat) => ({ tiles: hands.value[seat] || [], role: roleNameOf(seat), solo: !!roles.value.solo }),
  applyHand,
})

/** 我的座位:单机固定 0 号位,房间里按成员加入顺序;观战为 -1 */
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
const selection = computed(() => (selected.value.length ? bhCombo(selectedCards.value) : null))
const canPlay = computed(() => myTurn.value && !!selection.value && bhBeats(selection.value, lastCombo.value))
const activeCount = computed(() => handCounts.value.filter((n) => n > 0).length)
const isRoyalSeat = (seat) => seat === roles.value.emperor || seat === roles.value.guard
const canReveal = computed(() => myRoleName.value === 'guard' && !roles.value.solo && !guardRevealed.value)

/** 某席位身份的真实值(只有房主知道侍卫;单机本机就是房主) */
function roleNameOf(seat) {
  if (seat === roles.value.emperor) return 'emperor'
  if (seat === roles.value.guard) return 'guard'
  return 'rebel'
}

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

/** 能看到的身份:皇帝公开;自己的身份自己知道;侍卫要明保之后大家才知道 */
const roleOf = (seat) => {
  if (seat === roles.value.emperor) return t('games.baohuang.emperor')
  if (seat === mySeat.value) {
    if (myRoleName.value === 'guard') return t('games.baohuang.guard')
    if (myRoleName.value === 'rebel') return t('games.baohuang.rebel')
    return ''
  }
  if (guardRevealed.value && seat === roles.value.guard) return t('games.baohuang.guard')
  return ''
}
const toneOf = (seat) => {
  if (seat === roles.value.emperor) return 'emperor'
  if ((guardRevealed.value && seat === roles.value.guard) || (seat === mySeat.value && myRoleName.value === 'guard')) return 'royal'
  return 'rebel'
}
const myRoleLabel = computed(() => (roles.value.emperor < 0 ? '' : roleOf(mySeat.value)))
const myTone = computed(() => (roles.value.emperor < 0 ? '' : toneOf(mySeat.value)))
/** 席位分数:房间局结束后保皇派记 10 分、革命党 0(与上报交卷同口径) */
const seatScore = (seat) =>
  play.active && phase.value === 'ended' && royalWin.value != null ? (isRoyalSeat(seat) === !!royalWin.value ? 10 : 0) : 0

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
      roleLabel: roleOf(seat),
      tone: toneOf(seat),
      finishedLabel: finishOrder.value.includes(seat) ? t('games.fight.out') : '',
      bubble: roomBubble(seat),
    }
  }),
)

const comboLabel = (combo) => {
  if (!combo) return ''
  if (!combo.baseRank) return t('games.baohuang.jokerOnly', { n: combo.len })
  return t('games.baohuang.sameRank', { rank: bhRankText(combo.baseRank), n: combo.len })
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
  const chips = []
  if (roles.value.emperor >= 0) {
    chips.push({
      key: 'emperor',
      label: t('games.baohuang.emperor'),
      value: seatName(roles.value.emperor),
    })
  }
  if (roles.value.solo) chips.push({ key: 'solo', label: t('games.baohuang.solo'), value: '' })
  if (guardRevealed.value && !roles.value.solo && roles.value.guard >= 0) {
    chips.push({ key: 'guard', label: t('games.baohuang.guard'), value: seatName(roles.value.guard) })
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
    return (lastCombo.value ? t('games.fight.yourTurn') : t('games.baohuang.yourLead')) + countdown
  }
  return t('games.fight.thinking', { name: seatName(turn.value) }) + countdown
})

const myNote = computed(() => {
  if (phase.value !== 'playing' || !myTurn.value || !selected.value.length) return ''
  if (!selection.value) return t('games.fight.badCards')
  if (!bhBeats(selection.value, lastCombo.value)) return t('games.fight.notBigEnough')
  return comboLabel(selection.value)
})

const resultMeta = computed(() => (royalWin.value ? t('games.baohuang.royalWin') : t('games.baohuang.rebelWin')))

const clearTimer = () => {
  wakeTimers.cancel(aiTimer)
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
    emperor: roles.value.emperor,
    // 侍卫席位只在明保之后进公共盘面;一局打完要摊牌,结束时也公开
    guard: guardRevealed.value || phase.value === 'ended' ? roles.value.guard : -1,
    solo: !!roles.value.solo,
    royalWin: phase.value === 'ended' ? !!royalWin.value : null,
  }
}

function applyPublic(state) {
  if (!state) return
  if (state.phase) phase.value = state.phase
  turn.value = Number(state.turn) || 0
  last.value = state.last || null
  passCount.value = Math.max(0, Number(state.passCount) || 0)
  passStreak.value =
    Array.isArray(state.passStreak) && state.passStreak.length === SEATS ? state.passStreak.map((n) => Number(n) || 0) : [0, 0, 0, 0, 0]
  finishOrder.value = Array.isArray(state.finishOrder) ? state.finishOrder : []
  const emperor = Number.isInteger(state.emperor) ? state.emperor : -1
  const guard = Number.isInteger(state.guard) && state.guard >= 0 ? state.guard : -1
  roles.value = { emperor, guard, solo: !!state.solo, royal: emperor >= 0 && guard >= 0 ? [emperor, guard] : emperor >= 0 ? [emperor] : [] }
  guardRevealed.value = guard >= 0 && !state.solo
  if (emperor >= 0 && emperor === mySeat.value) myRoleName.value = 'emperor'
  if (state.royalWin != null) {
    royalWin.value = !!state.royalWin
    if (myRoleName.value) {
      const mine = myRoleName.value === 'emperor' || myRoleName.value === 'guard'
      result.value = mine === royalWin.value ? 'win' : 'lose'
    }
    autoReport()
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
  if (data?.role) myRoleName.value = data.role
  if (!same) {
    selected.value = []
    pendingAuto.value = []
  }
  // 补发可能先到盘面后到手牌:身份到手且已结束,这时才敢算胜负交卷
  if (phase.value === 'ended') autoReport()
}

const publishRoom = () => room.publish()

/** 房间局:一局打完各自交卷(胜方记 10 分、负方 0),房主按高分结算 */
function autoReport() {
  if (!play.active || play.reported || phase.value !== 'ended' || royalWin.value == null || !myRoleName.value) return
  const mine = myRoleName.value === 'emperor' || myRoleName.value === 'guard'
  play.report(mine === royalWin.value ? 10 : 0)
}

// ---------- 发牌 ----------

function startGame() {
  if (play.active && !play.isHost) return
  session += 1
  clearTimer()
  const dealt = bhDeal(play.active ? seededRng(play.seed) : Math.random)
  const found = bhRoles(dealt)
  hands.value = dealt
  roles.value = found
  // 电脑当侍卫时按手牌强度决定明保;真人当侍卫默认暗保,可随时点「明保」
  const guardSeat = found.guard
  guardRevealed.value = found.solo || (guardSeat >= 0 && isAiSeat(guardSeat) && bhStrength(dealt[guardSeat]) >= 15)
  myRoleName.value = roleNameOf(mySeat.value)
  turn.value = found.emperor >= 0 ? found.emperor : 0
  last.value = null
  passCount.value = 0
  selected.value = []
  pendingAuto.value = []
  finishOrder.value = []
  result.value = ''
  royalWin.value = null
  rewardMsg.value = ''
  passStreak.value = [0, 0, 0, 0, 0]
  phase.value = 'playing'
  publishRoom()
  drive(turn.value)
}

function allyOf(a, b) {
  if (a === b) return false
  if (isRoyalSeat(a)) return isRoyalSeat(b)
  if (b === roles.value.emperor) return false
  if (isRoyalSeat(b) && guardRevealed.value) return false
  return true
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
  // 引擎异常兜底:记日志后按电脑口径续跑本步,别把整局冻在「电脑思考中」
  const runStep = (fn) => {
    try {
      fn()
    } catch (e) {
      console.error(`[baohuang] 回合引擎异常 seat=${seat} phase=${phase.value},按电脑兜底续跑`, e)
      try {
        if (mySession === session && !play.ended && turn.value === seat) seatAction(seat, null)
      } catch (e2) {
        console.error('[baohuang] 兜底仍失败,本局中止', e2)
        clearTimer()
      }
    }
  }
  if (isAiSeat(seat)) {
    aiTimer = wakeTimers.schedule(() => runStep(() => apply(null)), AI_DELAY)
    return
  }
  room.waitAct(seat, play.active ? CARD_ACT_TIMEOUT : 0).then((action) => runStep(() => apply(action)))
}

/** 裁判端落一个出牌意图:电脑的(null)走 AI,真人的先按自己手里的牌复验再落盘 */
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
  const combo = cards.length ? bhCombo(cards) : null
  if (!combo || !bhBeats(combo, lastCombo.value)) {
    const pick = pickAiPlay(seat)
    if (pick) commitPlay(seat, pick)
    else passTurn(seat)
    return
  }
  commitPlay(seat, combo)
}

function pickAiPlay(seat) {
  const opponents = Array.from({ length: SEATS }, (_, i) => i).filter((i) => i !== seat && !allyOf(seat, i))
  const opponentMin = opponents.length ? Math.min(...opponents.map((i) => hands.value[i].length)) : 99
  return bhAiChoose({
    hand: hands.value[seat],
    last: lastCombo.value,
    // 队友刚出的牌不抢;但连让两次之后要自己推牌,否则一家会整局不出牌
    lastIsAlly: last.value ? allyOf(seat, last.value.seat) && passStreak.value[seat] < 2 : false,
    opponentMin,
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
    const rebelsDone = Array.from({ length: SEATS }, (_, i) => i).filter((i) => !isRoyalSeat(i) && finishOrder.value.includes(i)).length
    const emperorDone = finishOrder.value.includes(roles.value.emperor)
    if (emperorDone || rebelsDone >= SEATS - roles.value.royal.length) {
      finishGame()
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

function finishGame() {
  phase.value = 'ended'
  clearTimer()
  const emperorRank = finishOrder.value.indexOf(roles.value.emperor) + 1
  royalWin.value = emperorRank >= 1 && emperorRank <= 2
  const mine = myRoleName.value === 'emperor' || myRoleName.value === 'guard'
  result.value = mine === royalWin.value ? 'win' : 'lose'
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

/** 明保:房主直接把侍卫亮出来并广播,访客发一条专用信令请房主亮 */
function applyReveal() {
  if (roles.value.solo || roles.value.guard < 0 || guardRevealed.value) return
  guardRevealed.value = true
  publishRoom()
}

function revealGuard() {
  if (!canReveal.value) return
  if (!play.active) {
    guardRevealed.value = true
    return
  }
  if (play.isHost) {
    applyReveal()
    return
  }
  play.send('bh:reveal', { to: play.room?.hostId })
}

async function grantReward() {
  try {
    const res = await gameApi.baohuangReward()
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
  const plays = bhPlays(myHand.value, lastCombo.value)
  selected.value = plays.length ? plays[0].cards.map((card) => card.id) : []
  pendingAuto.value = []
}

// ---------- 信令 ----------

function handleSignal(data) {
  // 明保只有侍卫本人能喊:房主按自己的身份表验一遍再广播
  if (data?.event === 'bh:reveal') {
    if (!play.isHost) return
    const seat = room.seats.findIndex((s) => s.userId != null && String(s.userId) === String(data.from))
    if (seat >= 0 && seat === roles.value.guard) applyReveal()
    return
  }
  room.onSignal(data)
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
  wakeTimers.destroy()
})
</script>
