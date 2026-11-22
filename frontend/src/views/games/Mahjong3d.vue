<!-- 立体麻将:单机 vs 三位电脑,three.js 真 3D 牌桌(透视主视角,autotable 风格)。
     规则内核与平面麻将共用 utils/games/mahjong.js(判胡/听牌/计番/AI/骰子);
     本页负责单机对局循环与 3D 场景驱动,布局数学在 utils/games/mahjong3dTable.js。 -->
<template>
  <GameShell
    ref="shellEl"
    class="m3-shell"
    :title="$t('games.mahjong3d.title')"
    body-align="stretch"
    readable
    default-wide
    :new-game="phase === 'playing'"
    :new-game-confirm="true"
    @back="goBack"
    @new-game="onNewGame"
  >
    <template #rules>
      <p>{{ $t('games.mahjong.coverHint') }}</p>
    </template>

    <!-- 新游戏:外壳确认后选规则(封面规则区之外,进行中也能换) -->
    <template #overlay>
      <GameSetupDialog v-model="setupOpen" :fields="setupFields" :append-to-body="!isFullscreen" @confirm="onSetupConfirm" />
    </template>

    <div ref="cardEl" class="mahjong3d-card" @contextmenu.prevent @dblclick.prevent>
      <!-- 3D 舞台 -->
      <div ref="stageEl" class="m3-stage" />

      <!-- HUD:余牌 / 规则 / 回合 + 视角切换(对局中显示) -->
      <div v-if="phase !== 'idle'" class="m3-hud">
        <span class="m3-chip">{{ $t('games.mahjong.wallLeft', { n: wallLeft }) }}</span>
        <span class="m3-chip">{{ $t(`games.mahjong.presets.${rulesLabel}`) }}</span>
        <span class="m3-chip turn">{{ turnText }}</span>
        <button class="m3-view" type="button" @click="toggleView">
          {{ view === 'main' ? $t('games.mahjong3d.viewTop') : $t('games.mahjong3d.viewMain') }}
        </button>
      </div>

      <!-- 玩家名录:四席名字 + 庄标记 -->
      <div v-if="phase !== 'idle'" class="m3-players">
        <span v-for="s in seatList" :key="s.seat" class="m3-player" :class="{ active: turn === s.seat && phase === 'playing' }">
          <span class="m3-player-wind">{{ windName(s.seat) }}</span>
          {{ seatName(s.seat) }}
          <span v-if="s.seat === 0" class="m3-dealer">{{ $t('games.mahjong.dealer') }}</span>
        </span>
      </div>

      <!-- 吃/碰/杠/胡询问浮层(只有我会被问;无超时,「过」放弃) -->
      <div v-if="claimPrompt" class="m3-claim" role="alert">
        <el-button type="primary" size="large" round @click="answerClaim(true)">
          {{
            claimPrompt === 'win'
              ? $t('games.mahjong.claimWin')
              : claimPrompt === 'gang'
                ? $t('games.mahjong.mingGang')
                : claimPrompt === 'chi'
                  ? $t('games.mahjong.chi')
                  : $t('games.mahjong.pong')
          }}
        </el-button>
        <el-button v-for="(pair, i) in chiOptions" :key="'chi' + i" type="primary" size="large" round @click="answerClaim(true, pair)">
          {{ $t('games.mahjong.chi') }}
          <span class="tile mini" :class="tileClass(pair[0])"
            ><img class="tile-img" :src="tileSrc(pair[0])" alt="" draggable="false"
          /></span>
          <span class="tile mini" :class="tileClass(pair[1])"
            ><img class="tile-img" :src="tileSrc(pair[1])" alt="" draggable="false"
          /></span>
        </el-button>
        <el-button size="large" round @click="answerClaim(false)">{{ $t('games.mahjong.pass') }}</el-button>
      </div>

      <!-- 我方回合动作:自摸 / 暗杠 / 补杠 -->
      <div v-if="phase === 'playing' && turn === 0 && (canSelfWin || selfGangKinds.length)" class="m3-actions">
        <el-button v-if="canSelfWin" type="primary" size="default" round @click="declareSelfWin">
          {{ $t('games.mahjong.selfWin') }}
        </el-button>
        <el-button
          v-for="opt in selfGangKinds"
          :key="opt.kind + '-' + opt.type"
          type="warning"
          size="default"
          round
          @click="declareSelfGang(opt)"
        >
          {{ $t(opt.type === 'angang' ? 'games.mahjong.anGang' : 'games.mahjong.buGang') }}
        </el-button>
      </div>

      <!-- 封面:规则选择(与平面麻将同一套预设);游戏名与玩法说明在统一卡头 -->
      <div v-if="phase === 'idle'" class="m3-cover">
        <div class="rules-panel">
          <div class="rules-title">{{ $t('games.mahjong.rulesTitle') }}</div>
          <div class="rules-list" role="radiogroup" :aria-label="$t('games.mahjong.rulesTitle')">
            <button
              v-for="(p, key) in MAHJONG_RULE_PRESETS"
              :key="key"
              type="button"
              class="rules-option"
              role="radio"
              :aria-checked="rulesDraftKey === key"
              :class="{ active: rulesDraftKey === key }"
              @click="rulesDraftKey = key"
            >
              <span class="rules-option-name">{{ $t(`games.mahjong.presets.${key}`) }}</span>
              <span class="rules-option-desc">{{ $t(`games.mahjong.presetDescs.${key}`) }}</span>
            </button>
          </div>
        </div>
        <button class="m3-start" type="button" @click="startGame">{{ $t('games.mahjong.start') }}</button>
        <div v-if="rounds > 0" class="m3-cover-stats">{{ $t('games.mahjong.roundStats', { win: playerWins, lose: aiWins }) }}</div>
      </div>

      <!-- 结算浮层 -->
      <Transition name="game-overlay">
        <div v-if="phase === 'ended'" class="overlay game-overlay">
          <div class="overlay-card game-overlay-card">
            <div class="overlay-icon">{{ result?.winner === 0 ? '🎉' : result?.draw ? '🀄' : '😌' }}</div>
            <div class="overlay-title">
              <template v-if="!result"></template>
              <template v-else-if="result.draw">{{ $t('games.mahjong.drawGame') }}</template>
              <template v-else-if="result.winner === 0">{{ $t('games.mahjong.youWin') }}</template>
              <template v-else>{{ $t('games.mahjong.seatWins', { name: result.winnerName }) }}</template>
            </div>
            <div v-if="result && !result.draw && !result.selfDrawn && result.discardedBy" class="overlay-sub">
              {{ $t('games.mahjong.discardedBy', { name: result.discardedBy }) }}
            </div>
            <div v-if="result && !result.draw" class="overlay-score">
              {{ $t('games.mahjong.scoreLine', { score: result.score }) }}
            </div>
            <div v-if="result && !result.draw && result.fanKeys.length" class="overlay-fans">
              <el-tag v-for="f in result.fanKeys" :key="f" size="small" effect="plain">{{ $t(`games.mahjong.fans.${f}`) }}</el-tag>
            </div>
            <div v-if="rewardMsg" class="overlay-reward">{{ rewardMsg }}</div>
            <el-button type="primary" size="small" round @click="startGame">{{ $t('games.mahjong.again') }}</el-button>
          </div>
        </div>
      </Transition>
    </div>
  </GameShell>
</template>

<script setup>
// 单机对局循环:发牌→庄家掷骰定首家→摸/打/吃碰杠询问(胡>杠>碰>吃,无超时,AI 延迟应答)→结算。
// 3D 渲染交给 createMahjong3dTable;每次状态变化后 renderTable() 全量同步。
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { gameApi } from '@/api'
import GameShell from '@/components/games/GameShell.vue'
import GameSetupDialog from '@/components/games/GameSetupDialog.vue'
import { mahjongTileSrc } from '@/utils/games/mahjongTiles'
import { createMahjong3dTable } from '@/utils/games/mahjong3dTable'
import { createWakeTimers } from '@/utils/games/wakeTimers'
import {
  MAHJONG_RULE_PRESETS,
  canMahjongBuGang,
  canMahjongMingGang,
  canMahjongWin,
  createMahjongWall,
  mahjongAiChooseChi,
  mahjongAiChooseDiscard,
  mahjongAnGangKinds,
  mahjongChiOptions,
  mahjongChiSeat,
  mahjongCounts,
  mahjongDiceFirstSeat,
  mahjongHandScore,
  mahjongKind,
  mahjongRollDice,
  normalizeMahjongRules,
  sortMahjongTiles,
} from '@/utils/games/mahjong'

const router = useRouter()
const { t } = useI18n()

const HONOR_KEYS = ['east', 'south', 'west', 'north', 'zhong', 'fa', 'bai']
const WIND_KEYS = ['east', 'south', 'west', 'north']
const tileSrc = (kind) => mahjongTileSrc(kind)
const tileClass = (kind) => (kind >= 27 ? `honor-${HONOR_KEYS[kind - 27]}` : `suit-${Math.floor(kind / 9)}`)
const windName = (seat) => t(`games.mahjong.honor.${WIND_KEYS[((seat % 4) + 4) % 4]}`)

const shellEl = ref(null)
const stageEl = ref(null)
const cardEl = ref(null)
/** 全屏/宽屏由外壳持有;设置弹窗按它决定是否 teleport */
const isFullscreen = computed(() => !!shellEl.value?.isFullscreen)

// ---------- 对局状态 ----------
const phase = ref('idle') // idle | playing | ended
const turn = ref(0)
const seatList = ref([]) // [{ seat, name, ai }]
const hands = ref([[], [], [], []])
const melds = ref([[], [], [], []])
const rivers = ref([[], [], [], []])
const wall = ref([])
const dice = ref(null)
const drawnTileId = ref(null)
const canSelfWin = ref(false)
const localClaim = ref(null) // 'win' | 'gang' | 'pong' | 'chi'
const chiOptions = ref([])
const selfGangKinds = ref([])
const result = ref(null)
const rewardMsg = ref('')
const playerWins = ref(0)
const aiWins = ref(0)
const rounds = ref(0)
const rules = ref(normalizeMahjongRules(null))
const rulesDraftKey = ref('puTong') // 默认普通规则(与平面麻将同序)
// 新游戏设置弹窗:对局进行中点「新游戏」,外壳确认后在这里选规则
const setupOpen = ref(false)
const setupFields = computed(() => [
  {
    key: 'rules',
    label: t('games.mahjong.rulesTitle'),
    value: rulesDraftKey.value,
    options: Object.keys(MAHJONG_RULE_PRESETS).map((key) => ({ value: key, label: t(`games.mahjong.presets.${key}`) })),
  },
])
const onNewGame = () => {
  setupOpen.value = true
}
const onSetupConfirm = (values) => {
  rulesDraftKey.value = values.rules
  startGame()
}
const view = ref('main')
const hoverTileId = ref(null)

const rulesLabel = computed(() => {
  const r = rules.value
  for (const key of Object.keys(MAHJONG_RULE_PRESETS)) {
    const p = MAHJONG_RULE_PRESETS[key]
    if (p.allowChi === r.allowChi && p.allowGang === r.allowGang && p.pairConstraint === r.pairConstraint) return key
  }
  return 'custom'
})

let session = 0
const wakeTimers = createWakeTimers() // 回合计时登记:后台标签页节流 setTimeout,回前台时到期的立即补跑
let table = null
let stageRo = null
let actionResolver = null
let claimResolver = null

const lastDiscard = ref(null) // { kind, from }

const claimPrompt = computed(() => localClaim.value)
const wallLeft = computed(() => wall.value.length)
const myHandIds = computed(() => sortMahjongTiles(hands.value[0] || []))
const canDiscardNow = computed(() => phase.value === 'playing' && turn.value === 0 && !localClaim.value && myHandIds.value.length % 3 === 2)
const turnText = computed(() => {
  if (phase.value !== 'playing') return ''
  if (turn.value === 0) return t('games.mahjong.yourTurn')
  return t('games.mahjong.aiTurn', { name: seatName(turn.value) })
})

function seatName(seat) {
  const s = seatList.value.find((x) => x.seat === seat)
  if (!s) return ''
  if (!s.ai) return s.name || t('games.mahjong3d.you')
  const ordinal = seatList.value.filter((x) => x.ai).findIndex((x) => x.seat === seat)
  return t(`games.mahjong.ai${Math.min(ordinal + 1, 3)}`)
}

// ---------- 3D 同步 ----------
function renderTable() {
  if (!table) return
  const drawn = drawnTileId.value
  const held = drawn != null ? myHandIds.value.filter((id) => id !== drawn) : myHandIds.value
  table.setHand(
    held.map((id) => ({ id, kind: mahjongKind(id) })),
    drawn,
    drawn != null ? mahjongKind(drawn) : null,
  )
  for (const seat of [1, 2, 3]) table.setOpponentHand(seat, hands.value[seat]?.length || 0)
  for (const seat of [0, 1, 2, 3]) {
    const latestIdx = lastDiscard.value?.from === seat ? (rivers.value[seat]?.length || 0) - 1 : -1
    table.setRiver(seat, rivers.value[seat] || [], latestIdx)
  }
  for (const seat of [0, 1, 2, 3]) table.setMelds(seat, meldViews(seat))
  table.setWall(wall.value.length)
  syncHandHeight()
}

/** 按键避让手牌:把 3D 手牌整行的投影高度写进 --m3-hand-h。
 *  手机横屏的动作键/询问条靠它抬到手牌上方(竖屏不读,仍是贴底那套);
 *  纯投影、不进渲染循环,状态每变一次顺手更新一次 */
function syncHandHeight() {
  const rect = table?.handScreenRect?.()
  cardEl.value?.style.setProperty('--m3-hand-h', `${Math.round(rect?.height || 0)}px`)
}

/** 副露渲染视图:暗杠整排牌背,吃按顺子序 */
const meldView = (m) => {
  const type = m.type || 'pong'
  if (type === 'chi') return { dark: false, kinds: [m.pair[0], m.kind, m.pair[1]].sort((a, b) => a - b) }
  if (type === 'angang') return { dark: true, kinds: [] }
  if (type === 'gang' || type === 'bugang') return { dark: false, kinds: [m.kind, m.kind, m.kind, m.kind] }
  return { dark: false, kinds: [m.kind, m.kind, m.kind] }
}
const meldViews = (seat) => (melds.value[seat] || []).map(meldView)

// ---------- 引擎判定(与平面麻将同一内核) ----------
function canSeatWin(seat) {
  return canMahjongWin(mahjongCounts(hands.value[seat]), melds.value[seat]?.length || 0, rules.value)
}
function canSeatClaimWin(seat, kind) {
  const c = mahjongCounts(hands.value[seat])
  if (c[kind] >= 4) return false
  c[kind] += 1
  return canMahjongWin(c, melds.value[seat]?.length || 0, rules.value)
}
function canSeatPong(seat, kind) {
  return mahjongCounts(hands.value[seat])[kind] >= 2 && (melds.value[seat]?.length || 0) < 3
}
function canSeatChi(seat, kind) {
  if (!rules.value.allowChi) return []
  const from = lastDiscard.value?.from
  if (from == null || seat !== mahjongChiSeat(from)) return [] // 吃仅上家:只有弃牌者的下家能吃
  if ((melds.value[seat]?.length || 0) >= 3) return []
  return mahjongChiOptions(mahjongCounts(hands.value[seat]), kind)
}
function canSeatMingGang(seat, kind) {
  if (!rules.value.allowGang) return false
  if ((melds.value[seat]?.length || 0) >= 4) return false
  return canMahjongMingGang(mahjongCounts(hands.value[seat]), kind)
}
function seatGangOptions(seat) {
  if (!rules.value.allowGang) return []
  const counts = mahjongCounts(hands.value[seat])
  const out = []
  for (const k of mahjongAnGangKinds(counts)) out.push({ kind: k, type: 'angang' })
  for (const m of melds.value[seat] || []) {
    if ((m.type || 'pong') === 'pong' && canMahjongBuGang(counts, melds.value[seat], m.kind)) out.push({ kind: m.kind, type: 'bugang' })
  }
  return out
}
function aiShouldPong(seat, kind) {
  const c = mahjongCounts(hands.value[seat])
  if (c[kind] < 2) return false
  if ((melds.value[seat]?.length || 0) >= 3) return false
  if (kind >= 27 || kind % 9 === 0 || kind % 9 === 8) return true
  return c.filter((n) => n === 2).length >= 2
}

function sleep(ms) {
  return new Promise((res) => wakeTimers.schedule(res, ms))
}
/** 电脑思考停顿(1.2~2.4s),出牌与应答都有可感延迟 */
async function aiThink(mySession) {
  await sleep(1200 + Math.floor(Math.random() * 1200))
  return mySession === session
}

// ---------- 开局 / 主循环 ----------
function drawOne() {
  return wall.value.pop() ?? null
}

async function startGame() {
  session += 1
  const mySession = session
  phase.value = 'playing'
  result.value = null
  rewardMsg.value = ''
  localClaim.value = null
  chiOptions.value = []
  selfGangKinds.value = []
  lastDiscard.value = null
  drawnTileId.value = null
  canSelfWin.value = false
  rules.value = normalizeMahjongRules(MAHJONG_RULE_PRESETS[rulesDraftKey.value])
  rivers.value = [[], [], [], []]
  melds.value = [[], [], [], []]
  seatList.value = [
    { seat: 0, name: '', ai: false },
    { seat: 1, name: '', ai: true },
    { seat: 2, name: '', ai: true },
    { seat: 3, name: '', ai: true },
  ]
  wall.value = createMahjongWall()
  const deal = [[], [], [], []]
  for (let round = 0; round < 13; round++) {
    for (let seat = 0; seat < 4; seat++) deal[seat].push(wall.value.pop())
  }
  hands.value = deal
  // 庄家(席 0)掷双骰,和值定首家
  dice.value = mahjongRollDice()
  turn.value = mahjongDiceFirstSeat(dice.value[0], dice.value[1])
  rounds.value += 1
  table?.setDice(dice.value[0], dice.value[1], true)
  table?.setWinds(0)
  renderTable()
  runGameLoop(mySession).catch((e) => {
    // 引擎异常兜底:宁可按流局收尾,也别把整局冻在「电脑思考中」(主循环本身零 catch)
    console.error('[mahjong3d] 回合引擎异常,兜底流局', e)
    if (mySession === session && phase.value === 'playing') endDraw(mySession)
  })
}

async function runGameLoop(mySession) {
  let current = turn.value
  let skipDraw = false
  while (phase.value === 'playing' && mySession === session) {
    const seat = current
    turn.value = seat
    const st = seatList.value.find((s) => s.seat === seat)
    const ai = !!st?.ai
    if (!skipDraw) {
      const id = drawOne()
      if (id == null) return endDraw(mySession)
      hands.value[seat] = [...hands.value[seat], id]
      drawnTileId.value = seat === 0 ? id : null
      canSelfWin.value = !ai && seat === 0 && canSeatWin(seat)
      selfGangKinds.value = !ai && seat === 0 ? seatGangOptions(seat) : []
      renderTable()
      if (ai && canSeatWin(seat)) return finishHand(seat, true, mySession)
    }
    skipDraw = false
    let action
    if (ai) {
      await aiThink(mySession)
      if (mySession !== session || phase.value !== 'playing') return
      const gangOpts = seatGangOptions(seat)
      action = gangOpts.length
        ? { type: 'gang', kind: gangOpts[0].kind, gangType: gangOpts[0].type }
        : { type: 'discard', kind: mahjongAiChooseDiscard(mahjongCounts(hands.value[seat])) }
    } else {
      action = await waitMyAction()
      if (mySession !== session) return
    }
    if (action.type === 'win') return finishHand(seat, true, mySession)
    if (action.type === 'gang') {
      applySeatGang(seat, action)
      renderTable()
      skipDraw = true
      continue
    }
    let kind = action.kind != null ? action.kind : mahjongKind(action.tileId)
    if (kind == null || !hands.value[seat].some((tile) => mahjongKind(tile) === kind)) {
      kind = mahjongAiChooseDiscard(mahjongCounts(hands.value[seat]))
      if (kind < 0) return
    }
    discardTile(seat, kind)
    renderTable()
    const claim = await resolveClaims(seat, kind, mySession)
    if (mySession !== session || phase.value !== 'playing') return
    if (claim && claim.win != null) return finishHand(claim.win, false, mySession, kind, seat)
    if (claim && claim.gang != null) {
      applyMingGang(claim.gang, seat, kind)
      renderTable()
      current = claim.gang
      skipDraw = true
      continue
    }
    if (claim && claim.pong != null) {
      applyPong(claim.pong, seat, kind)
      renderTable()
      current = claim.pong
      skipDraw = true
      continue
    }
    if (claim && claim.chi != null) {
      applyChi(claim.chi.seat, seat, kind, claim.chi.pair)
      renderTable()
      current = claim.chi.seat
      skipDraw = true
      continue
    }
    current = (seat + 1) % 4
  }
}

/** 等我出牌/自摸/自杠(无超时:单机节奏自己掌握) */
function waitMyAction() {
  return new Promise((resolve) => {
    actionResolver = resolve
  })
}
function resolveMyAction(action) {
  if (!actionResolver) return false
  const done = actionResolver
  actionResolver = null
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
}

function applyPong(seat, fromSeat, kind) {
  const ids = hands.value[seat].filter((id) => mahjongKind(id) === kind).slice(0, 2)
  if (ids.length < 2) return
  hands.value[seat] = hands.value[seat].filter((id) => !ids.includes(id))
  melds.value[seat] = [...melds.value[seat], { kind, from: fromSeat, type: 'pong' }]
  rivers.value[fromSeat] = rivers.value[fromSeat].slice(0, -1)
  lastDiscard.value = null
}
function applyChi(seat, fromSeat, kind, pair) {
  const ids = hands.value[seat].filter((id) => pair.includes(mahjongKind(id))).slice(0, 2)
  if (ids.length < 2) return
  hands.value[seat] = hands.value[seat].filter((id) => !ids.includes(id))
  melds.value[seat] = [...melds.value[seat], { kind, from: fromSeat, type: 'chi', pair: [...pair] }]
  rivers.value[fromSeat] = rivers.value[fromSeat].slice(0, -1)
  lastDiscard.value = null
}
function applyMingGang(seat, fromSeat, kind) {
  const ids = hands.value[seat].filter((id) => mahjongKind(id) === kind).slice(0, 3)
  if (ids.length < 3) return
  hands.value[seat] = hands.value[seat].filter((id) => !ids.includes(id))
  melds.value[seat] = [...melds.value[seat], { kind, from: fromSeat, type: 'gang' }]
  rivers.value[fromSeat] = rivers.value[fromSeat].slice(0, -1)
  lastDiscard.value = null
  drawRinshan(seat)
}
/** 自己回合内的杠(暗杠:四张全亮;补杠:碰出后再摸到一张),摸岭上牌补位;杠意图里类型在 gangType(type 固定为 'gang') */
function applySeatGang(seat, { kind, gangType: type }) {
  if (type === 'bugang') {
    const ids = hands.value[seat].filter((id) => mahjongKind(id) === kind).slice(0, 1)
    if (!ids.length) return
    hands.value[seat] = hands.value[seat].filter((id) => !ids.includes(id))
    melds.value[seat] = (melds.value[seat] || []).map((m) =>
      m.kind === kind && (m.type || 'pong') === 'pong' ? { ...m, type: 'gang' } : m,
    )
  } else {
    const ids = hands.value[seat].filter((id) => mahjongKind(id) === kind).slice(0, 4)
    if (ids.length < 4) return
    hands.value[seat] = hands.value[seat].filter((id) => !ids.includes(id))
    melds.value[seat] = [...melds.value[seat], { kind, from: seat, type: 'angang' }]
  }
  drawRinshan(seat)
}
/** 杠后摸岭上牌补位 */
function drawRinshan(seat) {
  const id = drawOne()
  if (id != null) {
    hands.value[seat] = [...hands.value[seat], id]
    drawnTileId.value = seat === 0 ? id : null
  }
  canSelfWin.value = seat === 0 && id != null && canSeatWin(seat)
  selfGangKinds.value = seat === 0 && id != null ? seatGangOptions(seat) : []
}

/** 吃/碰/杠/胡询问:胡 > 杠 > 碰 > 吃;人无超时,AI 延迟应答 */
function resolveClaims(fromSeat, kind, mySession) {
  return new Promise((resolve) => {
    const order = [1, 2, 3].map((i) => (fromSeat + i) % 4)
    const winSeats = order.filter((s) => canSeatClaimWin(s, kind))
    const gangSeats = order.filter((s) => !winSeats.includes(s) && canSeatMingGang(s, kind))
    const pongSeats = order.filter((s) => !winSeats.includes(s) && !gangSeats.includes(s) && canSeatPong(s, kind))
    const chiSeat = mahjongChiSeat(fromSeat) // 吃仅上家:弃牌者的下家才有资格
    const chiOpts = chiSeat >= 0 && !winSeats.includes(chiSeat) && !gangSeats.includes(chiSeat) ? canSeatChi(chiSeat, kind) : []
    const isAi = (s) => !!seatList.value.find((x) => x.seat === s)?.ai
    const humanWin = winSeats.filter((s) => !isAi(s))
    const aiWin = winSeats.find(isAi)
    const humanGang = gangSeats.filter((s) => !isAi(s))
    const aiGang = gangSeats.find(isAi)
    const humanPong = pongSeats.filter((s) => !isAi(s))
    const aiPong = pongSeats.find((s) => isAi(s) && aiShouldPong(s, kind))
    const aiChiPair = !isAi(chiSeat) || !chiOpts.length ? null : mahjongAiChooseChi(mahjongCounts(hands.value[chiSeat]), kind)

    /** 人(席 0)表态:无超时,等按钮 */
    const askHuman = (claimKind, options = null) =>
      new Promise((done) => {
        localClaim.value = claimKind
        chiOptions.value = options || []
        claimResolver = (take, extra) => {
          claimResolver = null
          localClaim.value = null
          chiOptions.value = []
          done(take ? { seat: 0, extra } : null)
        }
      })

    ;(async () => {
      if (mySession !== session) return resolve(null)
      if (humanWin.length) {
        const hit = await askHuman('win')
        if (mySession !== session) return resolve(null)
        if (hit) return resolve({ win: hit.seat })
      }
      if (aiWin != null) {
        await aiThink(mySession)
        if (mySession !== session) return resolve(null)
        return resolve({ win: aiWin })
      }
      if (humanGang.length) {
        const hit = await askHuman('gang')
        if (mySession !== session) return resolve(null)
        if (hit) return resolve({ gang: hit.seat })
      }
      if (aiGang != null) {
        await aiThink(mySession)
        if (mySession !== session) return resolve(null)
        return resolve({ gang: aiGang })
      }
      if (humanPong.length) {
        const hit = await askHuman('pong')
        if (mySession !== session) return resolve(null)
        if (hit) return resolve({ pong: hit.seat })
      }
      if (aiPong != null) {
        await aiThink(mySession)
        if (mySession !== session) return resolve(null)
        return resolve({ pong: aiPong })
      }
      if (chiOpts.length && aiChiPair != null && isAi(chiSeat)) {
        await aiThink(mySession)
        if (mySession !== session) return resolve(null)
        return resolve({ chi: { seat: chiSeat, pair: aiChiPair } })
      }
      if (chiOpts.length && !isAi(chiSeat)) {
        const hit = await askHuman('chi', chiOpts)
        if (mySession !== session) return resolve(null)
        if (hit && hit.extra) return resolve({ chi: { seat: chiSeat, pair: hit.extra } })
      }
      resolve(null)
    })().catch((e) => {
      // 裁决链任何一环抛异常都 resolve,别让主循环在 await resolveClaims 上永久挂起
      console.error('[mahjong3d] 应答裁决异常,按全部过处理', e)
      resolve(null)
    })
  })
}

/** 本机应答询问 */
function answerClaim(take, extra = null) {
  if (!localClaim.value || !claimResolver) return
  claimResolver(take, take && localClaim.value === 'chi' ? extra : null)
}

// ---------- 本机交互 ----------
function onStageClick(e) {
  if (!canDiscardNow.value) return
  const id = table?.pickHandTile?.(e.clientX, e.clientY)
  if (id == null) return
  resolveMyAction({ type: 'discard', tileId: id })
}
function onStageMove(e) {
  if (!table) return
  const id = canDiscardNow.value ? table.pickHandTile(e.clientX, e.clientY) : null
  if (id !== hoverTileId.value) {
    hoverTileId.value = id
    table.setHover(id)
  }
}
function declareSelfWin() {
  if (!canSelfWin.value) return
  resolveMyAction({ type: 'win' })
}
function declareSelfGang(opt) {
  if (turn.value !== 0 || !opt) return
  resolveMyAction({ type: 'gang', kind: opt.kind, gangType: opt.type })
}

// ---------- 结算 ----------
function endDraw(mySession) {
  if (mySession !== session) return
  result.value = { draw: true, winner: -1, winnerName: '', fans: [], fanKeys: [], score: 0 }
  phase.value = 'ended'
}
async function finishHand(winnerSeat, selfDrawn, mySession, claimKind = null, discarderSeat = null) {
  if (mySession !== session || phase.value === 'ended') return
  const counts = mahjongCounts(hands.value[winnerSeat])
  if (!selfDrawn && claimKind != null) counts[claimKind] += 1
  const handTiles = [...hands.value[winnerSeat].map(mahjongKind)]
  if (!selfDrawn && claimKind != null) handTiles.push(claimKind)
  const { fans, score } = mahjongHandScore(counts, { selfDrawn, melds: melds.value[winnerSeat] })
  const iWon = winnerSeat === 0
  if (iWon) playerWins.value += 1
  else aiWins.value += 1
  result.value = {
    winner: winnerSeat,
    winnerName: seatName(winnerSeat),
    selfDrawn,
    discardedBy: discarderSeat != null ? seatName(discarderSeat) : '',
    fans,
    fanKeys: fans.map((f) => f.key),
    score,
    handTiles,
    draw: false,
  }
  phase.value = 'ended'
  if (iWon) await grantReward()
}

async function grantReward() {
  try {
    const res = await gameApi.mahjong3dReward()
    rewardMsg.value =
      res && res.awarded > 0
        ? `${t('games.mahjong.rewarded', { n: res.awarded })}(${t('points.points')} ${res.balance})`
        : t('games.mahjong.rewardCapped')
  } catch {
    rewardMsg.value = t('games.mahjong.rewardFailed')
  }
}

// ---------- 视角 / 生命周期 ----------
function toggleView() {
  view.value = view.value === 'main' ? 'top' : 'main'
  table?.setView(view.value)
  // 视角切换是 450ms 补间:机位落定后重新量一次手牌行(切换途中量到的是中间机位)
  setTimeout(syncHandHeight, 520)
  try {
    localStorage.setItem('ihomy:mahjong3d:view', view.value)
  } catch {}
}
function goBack() {
  router.push('/games')
}

onMounted(async () => {
  try {
    view.value = localStorage.getItem('ihomy:mahjong3d:view') === 'top' ? 'top' : 'main'
  } catch {}
  table = createMahjong3dTable(stageEl.value)
  table.setView(view.value)
  stageEl.value.addEventListener('click', onStageClick)
  stageEl.value.addEventListener('pointermove', onStageMove)
  // 牌桌内部靠 ResizeObserver 跟随舞台,个别 WebView(如安卓壳)RO 不派发,补 window resize 兜底
  window.addEventListener('resize', onWinResize)
  // 舞台尺寸变了(全屏/宽屏/旋转)手牌行的屏幕位置也跟着变;下一帧再量,牌桌自己的 RO 已先 resize 过相机
  stageRo = new ResizeObserver(() => requestAnimationFrame(syncHandHeight))
  stageRo.observe(stageEl.value)
  renderTable()
})

const onWinResize = () => {
  table?.resize?.()
  syncHandHeight()
}

onBeforeUnmount(() => {
  session += 1
  wakeTimers.destroy()
  stageEl.value?.removeEventListener('click', onStageClick)
  stageEl.value?.removeEventListener('pointermove', onStageMove)
  window.removeEventListener('resize', onWinResize)
  stageRo?.disconnect()
  stageRo = null
  table?.dispose()
  table = null
})
</script>

<style scoped>
.mahjong3d-card {
  position: relative;
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
  /* 手牌整行的投影高度(JS 每帧不写,状态/尺寸变化时由 syncHandHeight 更新);
     手机横屏的动作键与询问条按它抬到手牌上方 */
  --m3-hand-h: 0px;
}
.m3-stage {
  position: relative;
  flex: 1;
  min-height: 0;
  touch-action: none;
}
/* HUD:左上信息条 */
.m3-hud {
  position: absolute;
  left: 12px;
  top: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  max-width: calc(100% - 24px);
}
.m3-chip {
  padding: 3px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  color: var(--color-text-secondary);
  box-shadow: var(--shadow);
}
.m3-chip.turn {
  color: var(--color-accent);
  font-weight: 700;
}
.m3-view {
  border: none;
  padding: 3px 12px;
  border-radius: 999px;
  font-size: 12px;
  cursor: pointer;
  background: rgba(var(--color-brand-rgb), 0.9);
  color: var(--color-brand-text);
  font-weight: 600;
}
/* 玩家名录:右上,当前行动者高亮 */
.m3-players {
  position: absolute;
  right: 12px;
  top: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: flex-end;
}
.m3-player {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  color: var(--color-text-secondary);
  opacity: 0.75;
}
.m3-player.active {
  opacity: 1;
  color: var(--color-accent);
  border-color: rgba(var(--color-brand-rgb), 0.6);
  font-weight: 600;
}
.m3-player-wind {
  display: inline-flex;
  width: 18px;
  height: 18px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: rgba(239, 202, 131, 0.85);
  color: #463414;
  font-size: 11px;
  font-weight: 800;
}
.m3-dealer {
  font-size: 11px;
  color: #b9791f;
  font-weight: 700;
}
/* 询问浮层:底部居中,不遮手牌 */
.m3-claim {
  position: absolute;
  left: 50%;
  bottom: 76px;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: center;
  padding: 10px 14px;
  border-radius: 14px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-hover);
  z-index: var(--z-action);
  max-width: calc(100% - 24px);
}
.m3-claim .tile.mini {
  width: 26px;
  height: 36px;
  border-radius: 3px;
  background: #fdfcf7;
  border: 1px solid rgba(0, 0, 0, 0.18);
  display: inline-flex;
  margin-left: 2px;
}
.m3-claim .tile-img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  padding: 2px;
  pointer-events: none;
}
.m3-actions {
  position: absolute;
  left: 12px;
  bottom: 14px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  z-index: var(--z-action);
}
/* 封面 */
.m3-cover {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 20px;
  background: color-mix(in srgb, var(--color-card) 78%, transparent);
  backdrop-filter: blur(8px);
  z-index: var(--z-overlay);
  overflow-y: auto;
}
.m3-start {
  border: none;
  cursor: pointer;
  padding: 10px 42px;
  border-radius: 999px;
  font-size: 16px;
  font-weight: 700;
  background: rgba(var(--color-brand-rgb), 0.95);
  color: var(--color-brand-text);
  transition: transform var(--dur-fast) var(--ease-soft);
}
.m3-start:hover {
  transform: translateY(-2px);
}
.m3-cover-stats {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.rules-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 18px;
  border-radius: 14px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  max-width: 520px;
}
.rules-title {
  font-size: 13px;
  font-weight: 700;
}
.rules-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.rules-option {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 12px;
  border-radius: 10px;
  border: 1px solid var(--color-border);
  background: transparent;
  cursor: pointer;
  text-align: left;
  font: inherit;
  color: inherit;
}
.rules-option.active {
  border-color: rgba(var(--color-brand-rgb), 0.7);
  background: rgba(var(--color-brand-rgb), 0.08);
}
.rules-option-name {
  font-size: 13px;
  font-weight: 700;
}
.rules-option-desc {
  font-size: 12px;
  color: var(--color-text-secondary);
}
@media (max-width: 640px) {
  .m3-players {
    display: none;
  }
}

/* ===== 手机横屏:3D 牌桌铺满整屏 =====
 * 与平面麻将单机局同一套口径:左栏取消,整页就是画面(拍照/录屏里只剩牌桌),
 * 头部压成贴左上角的一行浮层。舞台吃到整块游戏区后,相机沿预设机位自动拉近
 * (fitCameraDistance),牌面与手牌自然变大;动作键/询问条按手牌整行的投影高度
 * (--m3-hand-h)抬到手牌上方,不再从手牌上压过去。 */
@media (orientation: landscape) and (max-height: 520px) {
  /* 沉浸态那条 padding 带 !important(叠 --gs-pad),这里同样要 !important 才盖得回;
     只留安全区 → 画面贴边。页底取场景底色,画布与页底之间不留色差 */
  .page.game-shell.m3-shell,
  html.game-mobile-immersive .mobile-sub-content .page.game-shell.m3-shell {
    --gs-bg: #22343c;
    position: relative;
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: minmax(0, 1fr);
    column-gap: 0;
    row-gap: 0;
    padding: env(safe-area-inset-top, 0px) env(safe-area-inset-right, 0px) env(safe-area-inset-bottom, 0px) env(safe-area-inset-left, 0px) !important;
  }
  /* 头部浮在画面左上角:没有玻璃底、没有描边,只有一层暗投影把字托起来 */
  .page.game-shell.m3-shell :deep(.gs-rail) {
    position: absolute;
    top: 0;
    left: 0;
    z-index: var(--z-float);
    max-width: calc(100% - 12px);
    max-height: 100%;
    overflow: visible;
    padding: 2px 4px;
    border-radius: 0;
    color: #f6f2e4;
    background: none;
    box-shadow: none;
  }
  .page.game-shell.m3-shell :deep(.gs-head) {
    flex-wrap: nowrap;
    min-height: 0;
    padding-bottom: 0;
    filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.35));
  }
  /* 外壳横屏档把「新游戏 + 规则」掰到第二行;这里整条只有 200 出头,拉回第一行 */
  .page.game-shell.m3-shell :deep(.gs-head .gs-end) {
    flex: 0 0 auto;
    flex-wrap: nowrap;
    margin-left: 4px;
    justify-content: flex-start;
  }
  .page.game-shell.m3-shell :deep(.gs-title) {
    color: #f6f2e4;
  }
  .page.game-shell.m3-shell :deep(.gs-back-label),
  .page.game-shell.m3-shell :deep(.gs-rules-label) {
    display: inline;
  }
  /* 次级键改白玻璃(暖居那条浅米底压在深色画面上像贴了张纸);主按钮「新游戏」保持品牌色 */
  .page.game-shell.m3-shell :deep(.gs-head .el-button:not(.el-button--primary):not(.el-button--danger):not(.is-link):not(.is-text)) {
    background: rgba(255, 255, 255, 0.14) !important;
    border-color: rgba(255, 255, 255, 0.18) !important;
    color: #fff !important;
  }
  /* 画布独占整块游戏区(本页没有计分栏/页脚,一行一列就够) */
  .page.game-shell.m3-shell :deep(.gs-body) {
    grid-row: 1;
    grid-column: 1;
    margin-inline: 0;
  }
  /* 头部这一行占了左上角:信息条与名录各下移一行,四席名字不钻到标题底下 */
  .m3-hud {
    top: 32px;
    left: 8px;
    gap: 6px;
  }
  .m3-players {
    top: 32px;
    right: 8px;
  }
  /* 动作键与询问条:手牌上方(两只都读同一个投影高度,竖屏仍是贴底/76px 那套) */
  .m3-actions,
  .m3-claim {
    bottom: calc(var(--m3-hand-h, 0px) + 6px);
  }
}
</style>
