<template>
  <GameShell
    ref="shellEl"
    class="sd-shell"
    :title="$t('games.sudoku.title')"
    :rules-pages="[rulesPagePlay, rulesPageControls, rulesPageVariants]"
    body-align="stretch"
    @back="goBack"
  >
    <template #settings>
      <el-select v-model="variant" class="sd-variant" :teleported="!isFullscreen" @change="changeVariant">
        <el-option v-for="key in VARIANT_KEYS" :key="key" :value="key" :label="$t('games.sudoku.variant.' + key)" />
      </el-select>
      <el-select v-model="difficulty" class="sd-difficulty" :teleported="!isFullscreen" @change="startNew">
        <el-option v-for="key in DIFFICULTY_KEYS" :key="key" :value="key" :label="$t('games.sudoku.difficulty.' + key)" />
      </el-select>
      <el-button class="sd-teach-btn" round @click="teachOpen = true">{{ $t('games.sudoku.teach.button') }}</el-button>
    </template>
    <template #stats>
      <span class="gh-chip"
        ><span>{{ $t('games.sudoku.time') }}</span
        ><b>{{ timeText }}</b></span
      >
      <span class="gh-chip"
        ><span>{{ $t('games.sudoku.blanks') }}</span
        ><b>{{ blanksLeft }}</b></span
      >
      <span class="gh-chip" :class="{ 'is-warn': conflictCount > 0 }">
        <span>{{ $t('games.sudoku.conflicts') }}</span
        ><b>{{ conflictCount }}</b>
      </span>
      <span v-if="hintsUsed" class="gh-chip"
        ><span>{{ $t('games.sudoku.hints') }}</span
        ><b>{{ hintsUsed }}</b></span
      >
    </template>
    <template #actions>
      <el-button :disabled="!history.length" @click="undo">{{ $t('games.sudoku.undo') }}</el-button>
      <el-button @click="startNew">{{ $t('games.sudoku.newGame') }}</el-button>
    </template>
    <template #rules="{ page }">
      <template v-if="page === 0">
        <p>{{ $t('games.sudoku.tip') }}</p>
        <ul>
          <li>{{ $t('games.sudoku.rules.play1') }}</li>
          <li>{{ $t('games.sudoku.rules.play2') }}</li>
          <li>{{ $t('games.sudoku.rules.play3') }}</li>
        </ul>
      </template>
      <ul v-else-if="page === 1">
        <li v-for="n in 5" :key="n">{{ $t('games.sudoku.rules.ctrl' + n) }}</li>
      </ul>
      <template v-else>
        <p>{{ $t('games.sudoku.rules.variantsIntro') }}</p>
        <ul>
          <li v-for="key in VARIANT_TIPS" :key="key">
            <b>{{ $t('games.sudoku.variant.' + key) }}</b> · {{ $t('games.sudoku.variantTip.' + key) }}
          </li>
        </ul>
      </template>
    </template>
    <template #extras>
      <GameRoomBar
        :room="play.room"
        :is-host="play.isHost"
        :score="elapsed"
        :reported="play.reported"
        :ended="play.ended"
        :result="play.spec?.result"
        :my-id="play.myId"
        @submit="submitRoomScore"
        @leave="play.leave"
      />
    </template>

    <!-- 技巧教学:与提示引擎同一套技巧阶梯;页面级弹窗挂 #overlay,全屏时留在整页里 -->
    <template #overlay>
      <el-dialog v-model="teachOpen" class="dialog-md" :append-to-body="!isFullscreen" :title="$t('games.sudoku.teach.title')">
        <p class="sd-teach-intro">{{ $t('games.sudoku.teach.intro') }}</p>
        <ol class="sd-teach-list">
          <li v-for="item in TECH_LIST" :key="item" class="sd-teach-item">
            <b>{{ $t('games.sudoku.tech.' + item + '.name') }}</b>
            <p>{{ $t('games.sudoku.tech.' + item + '.desc') }}</p>
          </li>
        </ol>
        <p class="sd-teach-foot">{{ $t('games.sudoku.teach.foot') }}</p>
      </el-dialog>
    </template>

    <div ref="layoutEl" v-loading="generating" class="sd-layout" :class="{ 'has-hint': !!hint }" @contextmenu.prevent @dblclick.prevent>
      <div class="sd-frame" :class="{ 'with-clues': variant === 'SKYSCRAPER' }">
        <template v-if="variant === 'SKYSCRAPER'">
          <template v-for="(value, col) in skyTop" :key="'sky-t' + col">
            <span v-if="value" class="sd-sky sd-sky-top" :style="{ gridColumnStart: String(col + 2), gridRowStart: '1' }">{{ value }}</span>
          </template>
          <template v-for="(value, col) in skyBottom" :key="'sky-b' + col">
            <span v-if="value" class="sd-sky sd-sky-bottom" :style="{ gridColumnStart: String(col + 2), gridRowStart: '11' }">{{
              value
            }}</span>
          </template>
          <template v-for="(value, row) in skyLeft" :key="'sky-l' + row">
            <span v-if="value" class="sd-sky sd-sky-left" :style="{ gridColumnStart: '1', gridRowStart: String(row + 2) }">{{
              value
            }}</span>
          </template>
          <template v-for="(value, row) in skyRight" :key="'sky-r' + row">
            <span v-if="value" class="sd-sky sd-sky-right" :style="{ gridColumnStart: '11', gridRowStart: String(row + 2) }">{{
              value
            }}</span>
          </template>
        </template>

        <div class="sd-board" :class="{ 'is-frozen': won, 'is-quick': quickFill && activeNumber > 0 }">
          <button
            v-for="(value, index) in board"
            :key="index"
            type="button"
            class="sd-cell"
            :class="cellClass(index)"
            :aria-label="cellLabel(index, value)"
            @click="select(index)"
            @pointerdown="onCellPressStart(index, $event)"
            @pointerup="onCellPressEnd"
            @pointerleave="onCellPressEnd"
            @pointercancel="onCellPressEnd"
          >
            <template v-if="variant === 'KILLER'">
              <i v-if="cageEdge(index, 't')" class="sd-cage sd-cage-t" aria-hidden="true"></i>
              <i v-if="cageEdge(index, 'b')" class="sd-cage sd-cage-b" aria-hidden="true"></i>
              <i v-if="cageEdge(index, 'l')" class="sd-cage sd-cage-l" aria-hidden="true"></i>
              <i v-if="cageEdge(index, 'r')" class="sd-cage sd-cage-r" aria-hidden="true"></i>
              <b v-if="cageHeadSum(index) !== ''" class="sd-cage-sum">{{ cageHeadSum(index) }}</b>
            </template>
            <template v-if="variant === 'WINDOW'">
              <i v-if="windowSide(index, 't')" class="sd-win sd-win-t" aria-hidden="true"></i>
              <i v-if="windowSide(index, 'b')" class="sd-win sd-win-b" aria-hidden="true"></i>
              <i v-if="windowSide(index, 'l')" class="sd-win sd-win-l" aria-hidden="true"></i>
              <i v-if="windowSide(index, 'r')" class="sd-win sd-win-r" aria-hidden="true"></i>
            </template>
            <template v-if="variant === 'DRAGON'">
              <i
                v-for="dir in dragonDirs(index)"
                :key="dir"
                class="sd-thermo-seg sd-dragon-seg"
                :class="'sd-thermo-to-' + dir"
                aria-hidden="true"
              ></i>
            </template>
            <template v-if="variant === 'THERMO' && thermoInfo[index]">
              <i
                v-if="thermoInfo[index].prev"
                class="sd-thermo-seg"
                :class="'sd-thermo-to-' + thermoInfo[index].prev"
                aria-hidden="true"
              ></i>
              <i
                v-if="thermoInfo[index].next"
                class="sd-thermo-seg"
                :class="'sd-thermo-to-' + thermoInfo[index].next"
                aria-hidden="true"
              ></i>
              <i v-if="thermoInfo[index].bulb" class="sd-thermo-bulb" aria-hidden="true"></i>
            </template>
            <template v-if="variant === 'PALINDROME' && palInfo[index]">
              <i
                v-if="palInfo[index].prev"
                class="sd-thermo-seg sd-pal-seg"
                :class="'sd-thermo-to-' + palInfo[index].prev"
                aria-hidden="true"
              ></i>
              <i
                v-if="palInfo[index].next"
                class="sd-thermo-seg sd-pal-seg"
                :class="'sd-thermo-to-' + palInfo[index].next"
                aria-hidden="true"
              ></i>
              <i v-if="palInfo[index].end" class="sd-pal-dot" aria-hidden="true"></i>
            </template>
            <i v-if="variant === 'ARITHMETIC' && arithInfo[index]" :class="arithCellClass(index)" aria-hidden="true"></i>
            <span v-if="ineqMark(index, 'r')" class="sd-ineq sd-ineq-r" aria-hidden="true">{{ ineqMark(index, 'r') }}</span>
            <span v-if="ineqMark(index, 'b')" class="sd-ineq sd-ineq-b" aria-hidden="true">{{ ineqMark(index, 'b') }}</span>
            <span v-if="consecutiveMark(index, 'r')" class="sd-consec sd-consec-r" aria-hidden="true"></span>
            <span v-if="consecutiveMark(index, 'b')" class="sd-consec sd-consec-b" aria-hidden="true"></span>
            <span v-if="xvMark(index, 'r')" class="sd-xv sd-xv-r" aria-hidden="true">{{ xvMark(index, 'r') }}</span>
            <span v-if="xvMark(index, 'b')" class="sd-xv sd-xv-b" aria-hidden="true">{{ xvMark(index, 'b') }}</span>
            <Transition name="sd-pop" mode="out-in">
              <span v-if="value" :key="'v' + value" class="sd-value">{{ value }}</span>
              <span v-else-if="notes[index]" :key="'n' + index" class="sd-notes">
                <span v-for="n in notesList(index)" :key="n" class="sd-note">{{ n }}</span>
              </span>
            </Transition>
          </button>
        </div>
      </div>
      <div class="sd-panel">
        <div class="sd-numpad">
          <button
            v-for="n in 9"
            :key="n"
            type="button"
            class="sd-num"
            :class="{ done: remaining[n] <= 0, active: quickFill ? activeNumber === n : focusDigit === n }"
            :disabled="remaining[n] <= 0"
            @click="pickNumber(n)"
          >
            <b>{{ n }}</b>
            <span class="sd-num-left">{{ remaining[n] > 0 ? remaining[n] : '' }}</span>
          </button>
        </div>
        <div class="sd-tools">
          <button type="button" class="sd-tool" :class="{ active: notesMode }" :aria-pressed="notesMode" @click="notesMode = !notesMode">
            {{ $t('games.sudoku.notes') }}
          </button>
          <button type="button" class="sd-tool" :class="{ active: quickFill }" :aria-pressed="quickFill" @click="toggleQuickFill">
            {{ $t('games.sudoku.quickFill') }}
          </button>
          <button type="button" class="sd-tool" @click="erase">{{ $t('games.sudoku.erase') }}</button>
          <button type="button" class="sd-tool" :disabled="!canSnap" :title="$t('games.sudoku.snapshot.saveTip')" @click="takeSnapshot">
            {{ $t('games.sudoku.snapshot.save') }}
          </button>
          <button
            type="button"
            class="sd-tool"
            :disabled="!snapAt || play.active || won"
            :title="snapAt ? $t('games.sudoku.snapshot.revertTip', { time: snapTime }) : $t('games.sudoku.snapshot.none')"
            @click="restoreSnapshot"
          >
            {{ $t('games.sudoku.snapshot.revert') }}
          </button>
          <button type="button" class="sd-tool" :disabled="won" @click="useHint">{{ $t('games.sudoku.hint') }}</button>
        </div>
        <div v-if="hint" class="sd-hint-note" role="status" aria-live="polite">
          <div class="sd-hint-head">
            <b v-if="hintTechName" class="sd-hint-tech">{{ hintTechName }}</b>
            <button type="button" class="sd-hint-close" :aria-label="$t('games.sudoku.hintDismiss')" @click="hint = null">×</button>
          </div>
          <p class="sd-hint-text">{{ hintText }}</p>
          <div v-if="hint.via && hint.via.length" class="sd-hint-via">
            <span class="sd-hint-via-label">{{ $t('games.sudoku.hintResult.viaLabel') }}</span>
            <span v-for="(key, i) in hint.via" :key="key + i" class="sd-hint-chip">{{ $t('games.sudoku.tech.' + key + '.name') }}</span>
          </div>
        </div>
        <div v-if="variant !== 'CLASSIC'" class="sd-hint sd-variant-tip">
          {{ $t('games.sudoku.variantTip.' + variant) }}
        </div>
      </div>

      <Transition name="game-overlay">
        <div v-if="won" class="sd-overlay game-overlay">
          <div class="sd-overlay-card game-overlay-card">
            <div class="sd-overlay-icon">🎉</div>
            <div class="sd-overlay-title">{{ $t('games.sudoku.win') }}</div>
            <div class="sd-overlay-meta">{{ $t('games.sudoku.winMeta', { time: timeText, hints: hintsUsed }) }}</div>
            <div v-if="rewardMsg" class="sd-overlay-reward">{{ rewardMsg }}</div>
            <el-button type="primary" size="small" round @click="startNew">{{ $t('games.sudoku.playAgain') }}</el-button>
          </div>
        </div>
      </Transition>
    </div>
  </GameShell>
</template>

<script setup>
// 数独:纯前端 H5 小游戏。生成器在 utils/games/sudoku.js(挖空时逐步校验唯一解;
// 变体含标准/对角线/窗口/不规则/不等式/奇偶/杀手),本页只做棋盘渲染、笔记/提示/撤销/
// 计时与进度存档;快填模式先选数字再点格子直填;通关按后端积分规则发奖励。
import { computed, inject, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { gameApi } from '@/api'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import GameShell from '@/components/games/GameShell.vue'
import GameRoomBar from '@/components/games/GameRoomBar.vue'
import { useGameRoomPlay } from '@/composables/useGameRoomPlay'
import { seededRng } from '@/utils/games/seededRng'
import {
  CELLS,
  DIFFICULTY_KEYS,
  EDGE_REL,
  HINT_TECHNIQUE,
  VARIANT_KEYS,
  buildPeers,
  cageIndexOf,
  clearPeerNotes,
  digitCounts,
  extraGroupsFor,
  findConflicts,
  findNextHint,
  generatePuzzle,
  isSolved,
  isValidArithPaths,
  isValidBoard,
  isValidCages,
  isValidEdges,
  isValidInequalities,
  isValidPalindromePaths,
  isValidParity,
  isValidRegions,
  isValidSkyscrapers,
  isValidThermos,
  notesOf,
  regionIndexOf,
} from '@/utils/games/sudoku'

const { t } = useI18n()
const router = useRouter()
const sunLight = inject(SUN_LIGHT_KEY, null)

const STORAGE_KEY = 'ihomy:games:sudoku:v1'
const SNAP_KEY = 'ihomy:games:sudoku:snapshot:v1'
const QUICK_FILL_KEY = 'ihomy:games:sudoku:quickfill:v1'
const MAX_HISTORY = 120
const LONG_PRESS_MS = 500

/** 技巧教学弹窗按难度阶梯列出的条目(提示引擎用到的同一套技巧) */
const TECH_LIST = [
  HINT_TECHNIQUE.NAKED_SINGLE,
  HINT_TECHNIQUE.HIDDEN_SINGLE,
  HINT_TECHNIQUE.LOCKED_CANDIDATES,
  HINT_TECHNIQUE.SUBSET,
  HINT_TECHNIQUE.X_WING,
  HINT_TECHNIQUE.GUESS,
]

const shellEl = ref(null)
const layoutEl = ref(null)
// 全屏/宽屏由外壳持有(全屏目标是整页,头部与控件在全屏里仍可点);弹窗按它决定是否 teleport
const isFullscreen = computed(() => !!shellEl.value?.isFullscreen)

const rulesPagePlay = computed(() => t('games.sudoku.rules.pagePlay'))
const rulesPageControls = computed(() => t('games.sudoku.rules.pageControls'))
const rulesPageVariants = computed(() => t('games.sudoku.rules.pageVariants'))
/** 规则第三页只列有额外规则的变体(标准玩法已在第一页讲了) */
const VARIANT_TIPS = VARIANT_KEYS.filter((key) => key !== 'CLASSIC')

// 竖排时数字盘在棋盘下方:实测它的高度写成变量,棋盘才能吃到真正的剩余高度。
// 观察器 + 窗口 resize 双保险:视口/字号变化时面板可能是在观察回调之外变高的
let panelObserver = null
let panelHeight = -1
const syncPanelHeight = () => {
  const layout = layoutEl.value
  if (!layout) return
  const panel = layout.querySelector('.sd-panel')
  const next = panel ? Math.round(panel.offsetHeight) : 0
  if (next === panelHeight) return
  panelHeight = next
  layout.style.setProperty('--sd-panel-h', `${next}px`)
}

const variant = ref('CLASSIC')
const difficulty = ref('MEDIUM')
const puzzle = ref(new Array(CELLS).fill(0))
const solution = ref(new Array(CELLS).fill(0))
const cages = ref(null)
const regions = ref(null)
const inequalities = ref(null)
const parity = ref(null)
const edges = ref(null)
const thermos = ref(null)
const skyscrapers = ref(null)
const arith = ref(null)
const palindromes = ref(null)
const board = ref(new Array(CELLS).fill(0))
const notes = ref(new Array(CELLS).fill(0))
const selected = ref(-1)
const notesMode = ref(false)
const quickFill = ref(false)
const activeNumber = ref(0)
// 焦点数字(数字盘选中或点中的棋盘数字),只用来点亮盘面上的同号格
const focusDigit = ref(0)
const generating = ref(false)
const history = ref([])
const hintsUsed = ref(0)
const elapsed = ref(0)
const won = ref(false)
const rewardMsg = ref('')
const hintFlash = ref(-1)
// 最近一次提示:技巧推导结果(格子、数字、用了哪几招);CONFLICT/DEAD 只用于面板文案
const hint = ref(null)
// 快照:一键存下当前盘面,走投无路时一键回退(与技巧教学弹窗各自独立)
const snapAt = ref(0)
const teachOpen = ref(false)

const snapTime = computed(() => {
  if (!snapAt.value) return ''
  const d = new Date(snapAt.value)
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
})

// ---------- 「一起玩」房间(数独只允许合作) ----------
// 房主生成题面并权威落子;其他成员的操作(落子/擦除/提示/撤销/长按直填/换题)都发给房主执行,
// 房主把整盘(题面/答案/笔记/线索/用时/提示次数)广播回来,所有人看到同一盘。
const play = useGameRoomPlay({ onSignal: onRoomSignal, onRoom: onRoomChange })
const coopGuest = computed(() => play.active && play.coop && !play.isHost)
/** 房间局返回房间大厅;单机回小游戏列表 */
const goBack = () => {
  if (play.active && play.room?.id) router.push(`/games/room/${play.room.id}`)
  else router.push('/games')
}
const submitRoomScore = () => play.report(elapsed.value)

function snapshotRoomState() {
  return {
    variant: variant.value,
    difficulty: difficulty.value,
    puzzle: puzzle.value,
    solution: solution.value,
    board: board.value,
    notes: notes.value,
    cages: cages.value,
    regions: regions.value,
    inequalities: inequalities.value,
    parity: parity.value,
    edges: edges.value,
    thermos: thermos.value,
    skyscrapers: skyscrapers.value,
    arith: arith.value,
    palindromes: palindromes.value,
    elapsed: elapsed.value,
    hintsUsed: hintsUsed.value,
    won: won.value,
  }
}

function applyRoomState(state) {
  if (!state || !Array.isArray(state.board) || state.board.length !== CELLS) return
  variant.value = VARIANT_KEYS.includes(state.variant) ? state.variant : 'CLASSIC'
  difficulty.value = DIFFICULTY_KEYS.includes(state.difficulty) ? state.difficulty : 'MEDIUM'
  puzzle.value = state.puzzle
  solution.value = state.solution
  board.value = state.board
  notes.value = Array.isArray(state.notes) && state.notes.length === CELLS ? state.notes : new Array(CELLS).fill(0)
  cages.value = state.cages || null
  regions.value = state.regions || null
  inequalities.value = state.inequalities || null
  parity.value = state.parity || null
  edges.value = state.edges || null
  thermos.value = state.thermos || null
  skyscrapers.value = state.skyscrapers || null
  arith.value = state.arith || null
  palindromes.value = state.palindromes || null
  elapsed.value = Math.max(0, Number(state.elapsed) || 0)
  hintsUsed.value = Math.max(0, Number(state.hintsUsed) || 0)
  won.value = !!state.won
  history.value = []
  hint.value = null
  if (won.value) stopTimer()
}

let roomTimer = null
function broadcastRoomState(immediate = false) {
  if (!play.active || !play.isHost) return
  if (roomTimer) {
    clearTimeout(roomTimer)
    roomTimer = null
  }
  const emit = () => {
    roomTimer = null
    play.send('state', { state: snapshotRoomState() })
  }
  if (immediate) emit()
  else roomTimer = setTimeout(emit, 80)
}

function onRoomSignal(data) {
  if (!data) return
  if (data.event === 'state' && coopGuest.value) {
    applyRoomState(data.state)
    return
  }
  if (data.event === 'sync' && play.isHost) {
    broadcastRoomState(true)
    return
  }
  if (data.event !== 'op' || !play.isHost) return
  const index = Number(data.index)
  const at = Number.isInteger(index) && index >= 0 && index < CELLS ? index : -1
  if (data.kind === 'input' && Number.isInteger(Number(data.n))) applyInput(Number(data.n), at, !!data.notes)
  else if (data.kind === 'erase') applyErase(at)
  else if (data.kind === 'hint') applyHint()
  else if (data.kind === 'undo') applyUndo()
  else if (data.kind === 'promote') applyPromote(at)
  else if (data.kind === 'newGame') startNewWith({ difficulty: data.difficulty, variant: data.variant })
}

function onRoomChange(next) {
  if (!next) return
  if (next.status === 'FINISHED' || next.status === 'CANCELLED') stopTimer()
}

/** 规则约束(生成/判定/存档校验共用同一份口径) */
const constraintsOf = (key, data) => {
  const extraGroups = key === 'KILLER' ? (data.cages || []).map((cage) => cage.cells) : extraGroupsFor(key)
  return {
    extraGroups: extraGroups.length ? extraGroups : undefined,
    regions: key === 'JIGSAW' ? data.regions || undefined : undefined,
    cages: key === 'KILLER' ? data.cages || undefined : undefined,
    // 温度计的递增线索复用不等式口径,求解/胜负判定不必再开一套约束
    inequalities: key === 'GREATER' || key === 'THERMO' ? data.inequalities || undefined : undefined,
    parity: key === 'ODD_EVEN' ? data.parity || undefined : undefined,
    edges: key === 'CONSECUTIVE' || key === 'XV' || key === 'NON_CONSECUTIVE' ? data.edges || undefined : undefined,
    skyscrapers: key === 'SKYSCRAPER' ? data.skyscrapers || undefined : undefined,
    arith: key === 'ARITHMETIC' ? data.arith || undefined : undefined,
    palindromes: key === 'PALINDROME' ? data.palindromes || undefined : undefined,
  }
}
const currentConstraints = computed(() =>
  constraintsOf(variant.value, {
    regions: regions.value,
    cages: cages.value,
    inequalities: inequalities.value,
    parity: parity.value,
    edges: edges.value,
    skyscrapers: skyscrapers.value,
    arith: arith.value,
    palindromes: palindromes.value,
  }),
)

// 当前盘面换「宫」为不规则区时的单元映射
const unitIdx = computed(() => (variant.value === 'JIGSAW' && regions.value ? regionIndexOf(regions.value) : null))
const unitAt = (index) => {
  if (unitIdx.value) return unitIdx.value[index]
  return Math.floor(Math.floor(index / 9) / 3) * 3 + Math.floor((index % 9) / 3)
}

const solvedNow = () => isSolved(board.value, currentConstraints.value)

// 冲突分组:对角线/窗口/反马步/反王步加附加组;杀手笼内不重复;不规则区替代九宫;
// 温度计沿线递增、连续/罗马的相邻边关系也算冲突依据
const conflicts = computed(() => {
  const extraGroups = variant.value === 'KILLER' ? (cages.value || []).map((cage) => cage.cells) : extraGroupsFor(variant.value)
  return findConflicts(board.value, {
    extraGroups: extraGroups.length ? extraGroups : null,
    regions: variant.value === 'JIGSAW' ? regions.value : null,
    inequalities: variant.value === 'GREATER' || variant.value === 'THERMO' ? inequalities.value : null,
    edges: variant.value === 'CONSECUTIVE' || variant.value === 'XV' || variant.value === 'NON_CONSECUTIVE' ? edges.value : null,
    skyscrapers: variant.value === 'SKYSCRAPER' ? skyscrapers.value : null,
    arith: variant.value === 'ARITHMETIC' ? arith.value : null,
    palindromes: variant.value === 'PALINDROME' ? palindromes.value : null,
  })
})
const conflictCount = computed(() => conflicts.value.size)
const blanksLeft = computed(() => board.value.reduce((n, v) => n + (v ? 0 : 1), 0))
const counts = computed(() => digitCounts(board.value))
const remaining = computed(() => {
  const left = [0]
  for (let n = 1; n <= 9; n++) left[n] = Math.max(0, 9 - counts.value[n])
  return left
})
const formatTime = (sec) => {
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}
const timeText = computed(() => formatTime(elapsed.value))

// 约束邻居与高亮:笔记清理、选中格影响范围共用同一份邻居口径(不规则区替代九宫、附加组计入)
const peersAll = computed(() => buildPeers(currentConstraints.value))
const selectedPeerSet = computed(() => (selected.value >= 0 ? new Set(peersAll.value[selected.value]) : null))

const isGiven = (index) => puzzle.value[index] !== 0
const notesList = (index) => notesOf(notes.value[index])
const cellLabel = (index, value) =>
  value
    ? t('games.sudoku.cellFilled', { row: Math.floor(index / 9) + 1, col: (index % 9) + 1, value })
    : t('games.sudoku.cellEmpty', { row: Math.floor(index / 9) + 1, col: (index % 9) + 1 })

// 提示面板:技巧名单独显示,这里拼「为什么是这一格」
const hintText = computed(() => {
  const h = hint.value
  if (!h) return ''
  if (h.technique === 'CONFLICT') return t('games.sudoku.hintResult.conflict')
  if (h.technique === HINT_TECHNIQUE.DEAD) return t('games.sudoku.hintResult.dead')
  const row = Math.floor(h.index / 9) + 1
  const col = (h.index % 9) + 1
  const cell = t('games.sudoku.hintCell', { row, col })
  if (h.technique === HINT_TECHNIQUE.GUESS) return t('games.sudoku.hintResult.deep', { cell, value: h.value })
  if (h.technique === HINT_TECHNIQUE.NAKED_SINGLE) return t('games.sudoku.hintResult.nakedSingle', { row, col, value: h.value })
  const unit = t('games.sudoku.hintUnit.' + (h.unitType || 'extra'), { n: (h.unitIndex || 0) + 1 })
  return t('games.sudoku.hintResult.hiddenSingle', { unit, cell, value: h.value })
})
const hintTechName = computed(() => {
  const h = hint.value
  if (!h || h.technique === 'CONFLICT' || h.technique === HINT_TECHNIQUE.DEAD) return ''
  return t('games.sudoku.tech.' + h.technique + '.name')
})

const cellClass = (index) => {
  const row = Math.floor(index / 9)
  const col = index % 9
  const unit = unitAt(index)
  const mark = parity.value ? parity.value[index] : 0
  return {
    given: isGiven(index),
    'edge-r': col < 8 && unitAt(index + 1) !== unit,
    'edge-b': row < 8 && unitAt(index + 9) !== unit,
    diag: variant.value === 'DIAGONAL' && (row === col || row + col === 8),
    selected: index === selected.value,
    peer: selectedPeerSet.value !== null && index !== selected.value && selectedPeerSet.value.has(index),
    same: focusDigit.value > 0 && board.value[index] === focusDigit.value && index !== selected.value,
    conflict: conflicts.value.has(index),
    hint: hintFlash.value === index,
    'parity-odd': mark === 1,
    'parity-even': mark === 2,
    'region-tint': variant.value === 'JIGSAW' && unit % 2 === 1,
  }
}

// ---------- 窗口数独:四个额外 3×3 窗口描边 ----------
const windowIdx = computed(() => {
  if (variant.value !== 'WINDOW') return null
  const map = new Int8Array(CELLS).fill(-1)
  extraGroupsFor('WINDOW').forEach((cells, g) => {
    for (const cell of cells) map[cell] = g
  })
  return map
})
const windowSide = (index, side) => {
  const idx = windowIdx.value
  if (!idx) return false
  const g = idx[index]
  if (g < 0) return false
  const row = Math.floor(index / 9)
  const col = index % 9
  if (side === 't') return row > 0 && idx[index - 9] !== g
  if (side === 'b') return row < 8 && idx[index + 9] !== g
  if (side === 'l') return col > 0 && idx[index - 1] !== g
  return col < 8 && idx[index + 1] !== g
}

// ---------- 九龙数独:九条龙形额外区域(线穿过格子中心,格内四条半段线自然接成龙身) ----------
const dragonIdx = computed(() => {
  if (variant.value !== 'DRAGON') return null
  const map = new Int8Array(CELLS).fill(-1)
  extraGroupsFor('DRAGON').forEach((cells, g) => {
    for (const cell of cells) map[cell] = g
  })
  return map
})
const dragonDirs = (index) => {
  const idx = dragonIdx.value
  if (!idx) return []
  const g = idx[index]
  if (g < 0) return []
  const row = Math.floor(index / 9)
  const col = index % 9
  const dirs = []
  if (row > 0 && idx[index - 9] === g) dirs.push('t')
  if (row < 8 && idx[index + 9] === g) dirs.push('b')
  if (col > 0 && idx[index - 1] === g) dirs.push('l')
  if (col < 8 && idx[index + 1] === g) dirs.push('r')
  return dirs
}

// ---------- 不等式数独:相邻格比较符号(< > 画在右/下边,∧∨ 表示上下关系) ----------
const ineqLess = computed(() => {
  const map = new Map()
  if (variant.value !== 'GREATER' || !Array.isArray(inequalities.value)) return map
  for (const [a, b] of inequalities.value) map.set(a < b ? a * CELLS + b : b * CELLS + a, a < b)
  return map
})
const ineqMark = (index, side) => {
  if (variant.value !== 'GREATER') return ''
  const row = Math.floor(index / 9)
  const col = index % 9
  if (side === 'r') {
    if (col === 8) return ''
    const less = ineqLess.value.get(index * CELLS + index + 1)
    return less === undefined ? '' : less ? '<' : '>'
  }
  if (row === 8) return ''
  const less = ineqLess.value.get(index * CELLS + index + 9)
  return less === undefined ? '' : less ? '∧' : '∨'
}

// ---------- 连续/罗马数独:相邻边关系标记(连续画短棒,V/X 画在右/下边中点) ----------
const edgeRelMap = computed(() => {
  const map = new Map()
  if ((variant.value !== 'CONSECUTIVE' && variant.value !== 'XV') || !Array.isArray(edges.value)) return map
  for (const edge of edges.value) {
    const lo = Math.min(edge.a, edge.b)
    const hi = Math.max(edge.a, edge.b)
    map.set(lo * CELLS + hi, edge.rel)
  }
  return map
})
const relBetween = (a, b) => edgeRelMap.value.get(Math.min(a, b) * CELLS + Math.max(a, b)) || 0
const consecutiveMark = (index, side) => {
  if (variant.value !== 'CONSECUTIVE') return false
  if (side === 'r') {
    if (index % 9 === 8) return false
    return relBetween(index, index + 1) === EDGE_REL.CONSECUTIVE
  }
  return index < CELLS - 9 && relBetween(index, index + 9) === EDGE_REL.CONSECUTIVE
}
const xvMark = (index, side) => {
  if (variant.value !== 'XV') return ''
  const rel = side === 'r' ? (index % 9 === 8 ? 0 : relBetween(index, index + 1)) : index >= CELLS - 9 ? 0 : relBetween(index, index + 9)
  if (rel === EDGE_REL.SUM_FIVE) return 'V'
  if (rel === EDGE_REL.SUM_TEN) return 'X'
  return ''
}

// ---------- 温度计数独:每格画到相邻格中心的半段线,圆头在起点格 ----------
const directionTo = (from, to) => {
  const dr = Math.floor(to / 9) - Math.floor(from / 9)
  const dc = (to % 9) - (from % 9)
  if (dr === -1) return 't'
  if (dr === 1) return 'b'
  if (dc === -1) return 'l'
  if (dc === 1) return 'r'
  return ''
}
const thermoInfo = computed(() => {
  const info = new Array(CELLS).fill(null)
  if (variant.value !== 'THERMO' || !Array.isArray(thermos.value)) return info
  for (const path of thermos.value) {
    for (let i = 0; i < path.length; i++) {
      const cell = path[i]
      info[cell] = {
        prev: i > 0 ? directionTo(cell, path[i - 1]) : '',
        next: i < path.length - 1 ? directionTo(cell, path[i + 1]) : '',
        bulb: i === 0,
      }
    }
  }
  return info
})

// ---------- 回文数独:与温度计同一套「中心 → 邻格」半段线,两端画小圆点 ----------
const palInfo = computed(() => {
  const info = new Array(CELLS).fill(null)
  if (variant.value !== 'PALINDROME' || !Array.isArray(palindromes.value)) return info
  for (const path of palindromes.value) {
    for (let i = 0; i < path.length; i++) {
      const cell = path[i]
      info[cell] = {
        prev: i > 0 ? directionTo(cell, path[i - 1]) : '',
        next: i < path.length - 1 ? directionTo(cell, path[i + 1]) : '',
        end: i === 0 || i === path.length - 1,
      }
    }
  }
  return info
})

// ---------- 摩天楼数独:四边外围提示(0 = 该侧无提示) ----------
const skyTop = computed(() => (variant.value === 'SKYSCRAPER' && skyscrapers.value ? skyscrapers.value.top : []))
const skyBottom = computed(() => (variant.value === 'SKYSCRAPER' && skyscrapers.value ? skyscrapers.value.bottom : []))
const skyLeft = computed(() => (variant.value === 'SKYSCRAPER' && skyscrapers.value ? skyscrapers.value.left : []))
const skyRight = computed(() => (variant.value === 'SKYSCRAPER' && skyscrapers.value ? skyscrapers.value.right : []))

// ---------- 等差数独:每条线段画成横/竖胶囊(端点封口 + 圆角) ----------
const arithInfo = computed(() => {
  const info = new Array(CELLS).fill(null)
  if (variant.value !== 'ARITHMETIC' || !Array.isArray(arith.value)) return info
  for (const path of arith.value) {
    const horizontal = Math.floor(path[0] / 9) === Math.floor(path[1] / 9)
    for (let i = 0; i < path.length; i++) {
      info[path[i]] = { dir: horizontal ? 'h' : 'v', first: i === 0, last: i === path.length - 1 }
    }
  }
  return info
})
const arithCellClass = (index) => {
  const info = arithInfo.value[index]
  if (!info) return ''
  return ['sd-arith', info.dir === 'h' ? 'sd-arith-h' : 'sd-arith-v', info.first ? 'sd-arith-start' : '', info.last ? 'sd-arith-end' : '']
}

// ---------- 杀手笼渲染(虚线边框 + 笼和标注,标在笼内最左上格) ----------
const cageIdx = computed(() => (variant.value === 'KILLER' && cages.value ? cageIndexOf(cages.value) : null))
const cageEdge = (index, side) => {
  const idx = cageIdx.value
  if (!idx) return false
  const g = idx[index]
  const row = Math.floor(index / 9)
  const col = index % 9
  if (side === 't') return row > 0 && idx[index - 9] !== g
  if (side === 'b') return row < 8 && idx[index + 9] !== g
  if (side === 'l') return col > 0 && idx[index - 1] !== g
  return col < 8 && idx[index + 1] !== g
}
const cageHeadSum = (index) => {
  const list = cages.value
  const idx = cageIdx.value
  if (!list || !idx) return ''
  const g = idx[index]
  if (g < 0 || list[g].cells[0] !== index) return ''
  return list[g].sum
}

// ---------- 计时 ----------
let timer = null
const stopTimer = () => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}
const startTimer = () => {
  stopTimer()
  // 合作模式以房主用时为准,访客跟随快照
  if (coopGuest.value) return
  timer = setInterval(() => {
    if (won.value || document.visibilityState === 'hidden') return
    elapsed.value += 1
  }, 1000)
}

// ---------- 存档 ----------
const saveState = () => {
  if (play.active) return
  if (won.value || !solution.value.length) return
  try {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        difficulty: difficulty.value,
        variant: variant.value,
        puzzle: puzzle.value,
        solution: solution.value,
        board: board.value,
        notes: notes.value,
        elapsed: elapsed.value,
        hintsUsed: hintsUsed.value,
        cages: cages.value,
        regions: regions.value,
        inequalities: inequalities.value,
        parity: parity.value,
        edges: edges.value,
        thermos: thermos.value,
        skyscrapers: skyscrapers.value,
        arith: arith.value,
        palindromes: palindromes.value,
      }),
    )
  } catch (e) {
    // 隐私模式/配额满时放弃存档,不影响游戏
  }
}
const clearState = () => {
  if (play.active) return
  try {
    localStorage.removeItem(STORAGE_KEY)
  } catch (e) {
    // 忽略
  }
}
/** 校验并归一化一份进度(自动存档与快照共用同一份口径,脏数据返回 null) */
const normalizeSave = (raw) => {
  if (!raw || typeof raw !== 'object') return null
  // 旧版存档没有 variant 字段,按标准数独处理;已下线变体的存档直接丢弃
  if (raw.variant && !VARIANT_KEYS.includes(raw.variant)) return null
  const savedVariant = raw.variant || 'CLASSIC'
  if (!DIFFICULTY_KEYS.includes(raw.difficulty)) return null
  if (savedVariant === 'KILLER' && !isValidCages(raw.cages)) return null
  if (savedVariant === 'JIGSAW' && !isValidRegions(raw.regions)) return null
  if (savedVariant === 'GREATER' && !isValidInequalities(raw.inequalities)) return null
  if (savedVariant === 'ODD_EVEN' && !isValidParity(raw.parity)) return null
  if ((savedVariant === 'CONSECUTIVE' || savedVariant === 'XV' || savedVariant === 'NON_CONSECUTIVE') && !isValidEdges(raw.edges)) {
    return null
  }
  if (savedVariant === 'THERMO' && (!isValidThermos(raw.thermos) || !isValidInequalities(raw.inequalities))) return null
  if (savedVariant === 'SKYSCRAPER' && !isValidSkyscrapers(raw.skyscrapers)) return null
  if (savedVariant === 'ARITHMETIC' && !isValidArithPaths(raw.arith)) return null
  if (savedVariant === 'PALINDROME' && !isValidPalindromePaths(raw.palindromes)) return null
  if (!isValidBoard(raw.puzzle) || !isValidBoard(raw.board) || !isValidBoard(raw.solution, { allowZero: false })) {
    return null
  }
  if (!Array.isArray(raw.notes) || raw.notes.length !== CELLS || raw.notes.some((m) => !Number.isInteger(m))) {
    return null
  }
  for (let i = 0; i < CELLS; i++) {
    if (raw.puzzle[i] && raw.board[i] !== raw.puzzle[i]) return null
  }
  // 终盘必须真的满足该变体约束,挡掉变体/约束对不上的旧档
  const opts = constraintsOf(savedVariant, raw)
  if (!isSolved(raw.solution, opts)) return null
  return { ...raw, variant: savedVariant }
}

const loadState = () => {
  try {
    return normalizeSave(JSON.parse(localStorage.getItem(STORAGE_KEY) || 'null'))
  } catch (e) {
    return null
  }
}

let saveTimer = null
const scheduleSave = () => {
  if (saveTimer) return
  saveTimer = setTimeout(() => {
    saveTimer = null
    saveState()
  }, 800)
}

// ---------- 快照(一键存当前盘面,走投无路时一键回退) ----------
// 只留一份,且只对当前这道题有效:换题/换变体即作废,避免回退到别的题。
const snapFits = (saved) =>
  Array.isArray(saved.puzzle) && puzzle.value.length === CELLS && saved.puzzle.join(',') === puzzle.value.join(',')
const readSnapshot = () => {
  try {
    return normalizeSave(JSON.parse(localStorage.getItem(SNAP_KEY) || 'null'))
  } catch (e) {
    return null
  }
}
const clearSnapshot = () => {
  snapAt.value = 0
  try {
    localStorage.removeItem(SNAP_KEY)
  } catch (e) {
    // 忽略
  }
}
/** 进页面时对一下:本地那份快照还是不是当前这道题的(不是就丢掉) */
const syncSnapMeta = () => {
  const saved = readSnapshot()
  if (saved && snapFits(saved)) snapAt.value = Number(saved.savedAt) || 0
  else clearSnapshot()
}
const canSnap = computed(() => !play.active && !won.value && !generating.value && solution.value.length === CELLS)

const takeSnapshot = () => {
  if (!canSnap.value) return
  const saved = { savedAt: Date.now(), ...snapshotRoomState() }
  try {
    localStorage.setItem(SNAP_KEY, JSON.stringify(saved))
  } catch (e) {
    // 隐私模式/配额满:说一声就好,不影响继续玩
    ElMessage.error(t('games.sudoku.snapshot.saveFailed'))
    return
  }
  snapAt.value = saved.savedAt
  ElMessage.success(t('games.sudoku.snapshot.saved'))
}

const restoreSnapshot = () => {
  if (play.active || won.value) return
  if (!snapAt.value) {
    ElMessage.info(t('games.sudoku.snapshot.none'))
    return
  }
  const saved = readSnapshot()
  if (!saved || !snapFits(saved)) {
    clearSnapshot()
    ElMessage.warning(t('games.sudoku.snapshot.invalid'))
    return
  }
  // 不做二次确认:回退本身进撤销栈,按错了「撤销」就能找回刚才的盘面
  const before = { board: board.value.slice(), notes: notes.value.slice() }
  applySavedState(saved)
  history.value = [before]
  ElMessage.success(t('games.sudoku.snapshot.restored'))
}

/** 把一份校验过的进度铺到棋盘上(自动恢复与手动载入共用) */
const applySavedState = (saved) => {
  variant.value = saved.variant
  difficulty.value = saved.difficulty
  puzzle.value = saved.puzzle
  solution.value = saved.solution
  cages.value = saved.variant === 'KILLER' ? saved.cages : null
  regions.value = saved.variant === 'JIGSAW' ? saved.regions : null
  inequalities.value = saved.variant === 'GREATER' || saved.variant === 'THERMO' ? saved.inequalities : null
  parity.value = saved.variant === 'ODD_EVEN' ? saved.parity : null
  edges.value = saved.variant === 'CONSECUTIVE' || saved.variant === 'XV' || saved.variant === 'NON_CONSECUTIVE' ? saved.edges : null
  thermos.value = saved.variant === 'THERMO' ? saved.thermos : null
  skyscrapers.value = saved.variant === 'SKYSCRAPER' ? saved.skyscrapers : null
  arith.value = saved.variant === 'ARITHMETIC' ? saved.arith : null
  palindromes.value = saved.variant === 'PALINDROME' ? saved.palindromes : null
  board.value = saved.board
  notes.value = saved.notes
  elapsed.value = Number.isInteger(saved.elapsed) ? saved.elapsed : 0
  hintsUsed.value = Number.isInteger(saved.hintsUsed) ? saved.hintsUsed : 0
  won.value = false
  selected.value = -1
  focusDigit.value = 0
  hint.value = null
  history.value = []
  startTimer()
}
// ---------- 游戏流程 ----------
let generateSeq = 0
// 合作模式首局按房间 seed 生成(房主刷新重进仍是同一道题);之后每次换题本地推进一轮,
// 否则同 seed + 同难度/变体永远生成一模一样的题,「换题」看起来像没反应。
// 只在房主侧累加,访客始终以房主广播的题面为准。
let roomRound = 0
const startNew = async () => {
  if (coopGuest.value) {
    play.send('op', { kind: 'newGame', difficulty: difficulty.value, variant: variant.value })
    return
  }
  const seq = ++generateSeq
  generating.value = true
  // 先让 loading 遮罩画出来再算题:杀手数独生成要几百毫秒,直接同步算会卡死一拍
  await nextTick()
  // 房间内按 seed 生成题面:即使房主重启,同一 seed 也还是同一道题
  const rand = play.active ? seededRng((Number(play.seed) || 1) + roomRound * 7919) : Math.random
  const res = generatePuzzle(difficulty.value, rand, { variant: variant.value })
  if (seq !== generateSeq) return
  if (play.active) roomRound += 1
  variant.value = res.variant
  puzzle.value = res.puzzle
  solution.value = res.solution
  cages.value = res.cages
  regions.value = res.regions
  inequalities.value = res.inequalities
  parity.value = res.parity
  edges.value = res.edges || null
  thermos.value = res.thermos || null
  skyscrapers.value = res.skyscrapers || null
  arith.value = res.arith || null
  palindromes.value = res.palindromes || null
  board.value = res.puzzle.slice()
  notes.value = new Array(CELLS).fill(0)
  selected.value = -1
  notesMode.value = false
  activeNumber.value = 0
  focusDigit.value = 0
  skipClick = false
  history.value = []
  hintsUsed.value = 0
  elapsed.value = 0
  won.value = false
  rewardMsg.value = ''
  hintFlash.value = -1
  hint.value = null
  clearState()
  // 新题面 = 旧快照作废,否则「回退」会把人送回上一道题
  clearSnapshot()
  generating.value = false
  startTimer()
}

// 房主按其他成员换题的请求重建题面(先套用对方选的难度/变体)
const startNewWith = ({ difficulty: nextDifficulty, variant: nextVariant } = {}) => {
  if (DIFFICULTY_KEYS.includes(nextDifficulty)) difficulty.value = nextDifficulty
  if (VARIANT_KEYS.includes(nextVariant)) variant.value = nextVariant
  return startNew()
}

// 切变体:先清掉旧盘面的存档与线索再开新局,避免旧盘面被写进新变体的档里
const changeVariant = () => {
  if (coopGuest.value) {
    play.send('op', { kind: 'newGame', difficulty: difficulty.value, variant: variant.value })
    return
  }
  cages.value = null
  regions.value = null
  inequalities.value = null
  parity.value = null
  edges.value = null
  thermos.value = null
  skyscrapers.value = null
  arith.value = null
  palindromes.value = null
  clearState()
  startNew()
}

const select = (index) => {
  if (won.value || generating.value) return
  // 长按直填会紧随一次 click,别让这次点击再落一遍数字
  if (skipClick) {
    skipClick = false
    return
  }
  selected.value = index
  // 快填模式:先点数字再点格子,已有数字可覆盖(给定格不可改);同数字再点不擦除
  if (quickFill.value && activeNumber.value > 0 && !isGiven(index)) {
    focusDigit.value = activeNumber.value
    if (board.value[index] !== activeNumber.value) input(activeNumber.value)
    return
  }
  // 点棋盘上的数字:点亮盘面上所有同号格
  focusDigit.value = board.value[index] || 0
}

// 快填开关:开启后数字盘只负责选中数字,不再直接落子
const toggleQuickFill = () => {
  quickFill.value = !quickFill.value
  activeNumber.value = 0
  focusDigit.value = 0
  try {
    localStorage.setItem(QUICK_FILL_KEY, quickFill.value ? '1' : '0')
  } catch (e) {
    // 忽略
  }
}

// 数字盘:快填模式选中数字(再点同数字取消),常规模式直接落子;两种模式都点亮该数字的落点
const pickNumber = (n) => {
  if (won.value || generating.value) return
  if (quickFill.value) {
    activeNumber.value = activeNumber.value === n ? 0 : n
    focusDigit.value = activeNumber.value
    return
  }
  focusDigit.value = n
  input(n)
}

const snapshot = () => {
  history.value.push({ board: board.value.slice(), notes: notes.value.slice() })
  if (history.value.length > MAX_HISTORY) history.value.shift()
}

// 落数后按当前变体口径清邻居笔记(不规则区替代九宫,对角线/窗口/杀手笼一并计入)
const clearNotesAround = (index, value) => clearPeerNotes(notes.value, index, value, currentConstraints.value)

const input = (n, at = selected.value) => {
  if (coopGuest.value) {
    play.send('op', { kind: 'input', index: at, n, notes: notesMode.value })
    return
  }
  applyInput(n, at, notesMode.value)
}

// 落子/记笔记的唯一实现(房主本地操作与远端请求都走这里)
const applyInput = (n, at, asNotes) => {
  const index = at
  if (won.value || generating.value || index < 0 || isGiven(index)) return
  hint.value = null
  if (asNotes) {
    if (board.value[index]) return
    snapshot()
    notes.value[index] ^= 1 << (n - 1)
    return
  }
  snapshot()
  if (board.value[index] === n) {
    board.value[index] = 0
    return
  }
  board.value[index] = n
  notes.value[index] = 0
  clearNotesAround(index, n)
  if (solvedNow()) finish()
}

const erase = () => {
  if (coopGuest.value) {
    play.send('op', { kind: 'erase', index: selected.value })
    return
  }
  applyErase(selected.value)
}

const applyErase = (index) => {
  if (won.value || index < 0 || isGiven(index)) return
  if (!board.value[index] && !notes.value[index]) return
  hint.value = null
  snapshot()
  board.value[index] = 0
  notes.value[index] = 0
  // 快填时保持已选数字的落点点亮,否则清掉高亮
  focusDigit.value = quickFill.value ? activeNumber.value : 0
}

const flashCell = (index) => {
  hintFlash.value = index
  setTimeout(() => {
    if (hintFlash.value === index) hintFlash.value = -1
  }, 900)
}

// 长按只有一个候选的格子:把该笔记直接转成正式数字(可撤销,不计提示次数)
const promoteSingleNote = (index) => {
  if (coopGuest.value) {
    play.send('op', { kind: 'promote', index })
    return true
  }
  return applyPromote(index)
}

const applyPromote = (index) => {
  if (won.value || generating.value || index < 0) return false
  if (isGiven(index) || board.value[index]) return false
  const candidates = notesOf(notes.value[index])
  if (candidates.length !== 1) return false
  const n = candidates[0]
  snapshot()
  board.value[index] = n
  notes.value[index] = 0
  clearNotesAround(index, n)
  selected.value = index
  focusDigit.value = n
  flashCell(index)
  if (solvedNow()) finish()
  return true
}

let pressTimer = null
let skipClick = false
const cancelCellPress = () => {
  if (pressTimer) {
    clearTimeout(pressTimer)
    pressTimer = null
  }
}

const onCellPressStart = (index, event) => {
  // 只认主键/触摸,避免右键长按也触发
  if (event.pointerType === 'mouse' && event.button !== 0) return
  cancelCellPress()
  pressTimer = setTimeout(() => {
    pressTimer = null
    // 长按直填后浏览器还会补一次 click,标掉它免得快填模式再覆盖一次
    if (promoteSingleNote(index)) skipClick = true
  }, LONG_PRESS_MS)
}

const onCellPressEnd = () => cancelCellPress()

const undo = () => {
  if (coopGuest.value) {
    play.send('op', { kind: 'undo' })
    return
  }
  if (won.value || !history.value.length) return
  applyUndo()
}

const applyUndo = () => {
  if (won.value || !history.value.length) return
  hint.value = null
  const last = history.value.pop()
  board.value = last.board
  notes.value = last.notes
}

const useHint = () => {
  if (won.value || generating.value) return
  if (coopGuest.value) {
    play.send('op', { kind: 'hint' })
    return
  }
  applyHint()
}

// 提示 = 按人解技巧推出「下一步该填的格子」,不再跟着当前选中的格子走;
// 冲突或走不通时只给说明、不落子,也不计提示次数。
const applyHint = () => {
  if (won.value || generating.value) return
  if (conflictCount.value > 0) {
    hint.value = { technique: 'CONFLICT' }
    return
  }
  const result = findNextHint(board.value, {
    ...currentConstraints.value,
    solution: solution.value,
    variant: variant.value,
  })
  if (!result) return
  hint.value = result
  if (result.technique === HINT_TECHNIQUE.DEAD) return
  snapshot()
  board.value[result.index] = result.value
  notes.value[result.index] = 0
  clearNotesAround(result.index, result.value)
  hintsUsed.value += 1
  selected.value = result.index
  focusDigit.value = result.value
  flashCell(result.index)
  if (solvedNow()) finish()
}

const finish = () => {
  if (won.value) return
  won.value = true
  stopTimer()
  selected.value = -1
  hint.value = null
  clearState()
  if (play.active) {
    if (play.isHost) play.report(elapsed.value)
    return
  }
  grantReward()
}

const grantReward = async () => {
  if (play.active) return
  try {
    const res = await gameApi.sudokuReward()
    rewardMsg.value =
      res && res.awarded > 0
        ? `${t('games.rewarded', { n: res.awarded })}（${t('points.points')} ${res.balance}）`
        : t('games.rewardCapped')
  } catch (e) {
    rewardMsg.value = t('games.rewardFailed')
  }
}

// ---------- 键盘 ----------
const ARROW_DELTA = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -9, ArrowDown: 9 }

const onKeydown = (event) => {
  const tag = event.target?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return
  if (won.value || generating.value) return
  if (/^[1-9]$/.test(event.key)) {
    const n = Number(event.key)
    if (quickFill.value) {
      // 快填模式下键盘也只选数字,配合点击格子落子
      activeNumber.value = activeNumber.value === n ? 0 : n
      focusDigit.value = activeNumber.value
    } else {
      focusDigit.value = n
      input(n)
    }
    event.preventDefault()
    return
  }
  if (event.key === 'Backspace' || event.key === 'Delete' || event.key === '0') {
    erase()
    event.preventDefault()
    return
  }
  if (event.key === 'n' || event.key === 'N') {
    notesMode.value = !notesMode.value
    return
  }
  const delta = ARROW_DELTA[event.key]
  if (delta) {
    const from = selected.value >= 0 ? selected.value : 0
    const next = Math.min(CELLS - 1, Math.max(0, from + delta))
    selected.value = next
    event.preventDefault()
  }
}

// 棋面/笔记改动立即落盘(撤销后立刻刷新也不会回退),计时每秒变化走防抖,避免每秒写 localStorage
watch([board, notes, difficulty, hintsUsed], saveState, { deep: true })
watch(elapsed, scheduleSave)
// 合作模式:房主把整盘(题面/答案/笔记/线索/用时/提示)同步给其他成员
watch(
  [board, notes, elapsed, hintsUsed, won, variant, difficulty, puzzle],
  () => {
    if (play.active && play.isHost) broadcastRoomState()
  },
  { deep: true },
)

onMounted(async () => {
  document.addEventListener('keydown', onKeydown)
  window.addEventListener('resize', syncPanelHeight, { passive: true })
  sunLight?.suspendEffects()

  await nextTick()
  syncPanelHeight()
  const panel = layoutEl.value?.querySelector('.sd-panel')
  if (panel && typeof ResizeObserver !== 'undefined') {
    panelObserver = new ResizeObserver(syncPanelHeight)
    panelObserver.observe(panel)
  }

  try {
    quickFill.value = localStorage.getItem(QUICK_FILL_KEY) === '1'
  } catch (e) {
    quickFill.value = false
  }
  if (play.active) {
    // 房间:跳过本地存档,房主出题并广播,访客等房主同步
    await play.load()
    if (coopGuest.value) play.requestSync()
    else startNew()
    return
  }

  const saved = loadState()
  if (saved) applySavedState(saved)
  else await startNew()
  // 刷新后本地那份快照若仍属于这道题就继续可用,否则丢掉
  syncSnapMeta()
})

onBeforeUnmount(() => {
  stopTimer()
  cancelCellPress()
  panelObserver?.disconnect()
  panelObserver = null
  window.removeEventListener('resize', syncPanelHeight)
  if (saveTimer) clearTimeout(saveTimer)
  if (roomTimer) clearTimeout(roomTimer)
  saveState()
  document.removeEventListener('keydown', onKeydown)
  sunLight?.restoreEffects()
})
</script>

<style scoped>
/* 页面本体透明:棋盘与操作面板直接铺在内容页底板上(UI §43),不再套圆角外框。
   风格色只用 --sd-* 一组,深浅色各一份 */
.sd-shell {
  color: var(--color-text-primary, #2b2b2b);
  --sd-line: rgba(0, 0, 0, 0.12);
  --sd-line-strong: rgba(0, 0, 0, 0.32);
  --sd-cell-bg: var(--color-surface, #fff);
  --sd-given: var(--color-text-primary, #2b2b2b);
  --sd-user: var(--color-accent, #b88c6e);
  --sd-peer: rgba(184, 140, 110, 0.08);
  --sd-same: rgba(184, 140, 110, 0.2);
  --sd-conflict: rgba(214, 76, 76, 0.22);
  --sd-hint: rgba(96, 176, 120, 0.3);
  --sd-diag: rgba(184, 140, 110, 0.07);
  --sd-cage: rgba(184, 140, 110, 0.6);
  --sd-region: rgba(184, 140, 110, 0.06);
  --sd-window: rgba(64, 150, 170, 0.55);
  --sd-thermo: rgba(208, 118, 72, 0.6);
  --sd-consec: rgba(122, 96, 184, 0.7);
  --sd-xv: rgba(62, 138, 156, 0.9);
  --sd-arith: rgba(52, 138, 112, 0.8);
  --sd-pal: rgba(196, 86, 146, 0.7);
  --sd-dragon: rgba(188, 116, 46, 0.62);
}

.sd-variant {
  width: 140px;
}
.sd-difficulty {
  width: 104px;
}

/* 棋盘 + 操作面板:竖屏=棋盘在上、数字盘在下;横屏改左右两栏(见下面的容器查询)。
   外壳的 .gs-body 是尺寸容器,这里直接用 100cqh 扣掉「实测面板高度」,
   不再按屏高减魔法数,也不整块 transform 缩放 */
.sd-layout {
  --sd-frame-size: min(calc(100% - 8px), calc(100cqh - var(--sd-panel-h, 0px) - 18px), 560px);
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  /* 两行都按内容走、整体垂直居中:棋盘被宽度卡住时(手机竖屏/平板竖屏)棋盘与数字盘
     贴在一起居中,而不是被 1fr 行拉开一大段空白 */
  grid-template-rows: auto auto;
  justify-items: center;
  align-items: center;
  align-content: center;
  gap: 10px;
  flex: 1 1 auto;
  width: 100%;
  min-width: 0;
  min-height: 0;
}
.sd-panel {
  display: grid;
  gap: 10px;
  width: 100%;
  min-width: 0;
  min-height: 0;
}

/* 棋盘外框:摩天楼要用四边留出提示轨道,其余变体只有中间 9×9。
   外框自身是尺寸容器,外圈提示数字按 cqi 跟随棋盘大小 */
.sd-frame {
  container-type: inline-size;
  display: grid;
  grid-template-columns: repeat(9, minmax(0, 1fr));
  grid-template-rows: repeat(9, minmax(0, 1fr));
  width: var(--sd-frame-size);
  aspect-ratio: 1;
}
.sd-frame.with-clues {
  --sd-clue-track: clamp(15px, 4.2vw, 24px);
  grid-template-columns: var(--sd-clue-track) repeat(9, minmax(0, 1fr)) var(--sd-clue-track);
  grid-template-rows: var(--sd-clue-track) repeat(9, minmax(0, 1fr)) var(--sd-clue-track);
}

.sd-board {
  grid-area: 1 / 1 / 10 / 10;
  container-type: inline-size;
  display: grid;
  grid-template-columns: repeat(9, minmax(0, 1fr));
  /* 行也必须显式等分:自动行高会跟着格内数字/笔记的内容走,填数后整盘会跳尺寸 */
  grid-template-rows: repeat(9, minmax(0, 1fr));
  width: 100%;
  height: 100%;
  border: 2px solid var(--sd-line-strong);
  border-radius: 8px;
  overflow: hidden;
  background: var(--sd-cell-bg);
  contain: layout style;
}
.sd-frame.with-clues .sd-board {
  grid-area: 2 / 2 / 11 / 11;
}

/* 摩天楼四边外围提示数字 */
.sd-sky {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: clamp(8px, 2.2cqi, 13px);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  line-height: 1;
  color: var(--color-text-secondary, #8a8a8a);
  user-select: none;
}
.sd-sky-top,
.sd-sky-bottom {
  padding: 0 1px;
}
.sd-sky-left,
.sd-sky-right {
  padding: 1px 0;
}

.sd-board.is-frozen {
  pointer-events: none;
}

.sd-cell {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  min-height: 0;
  padding: 0;
  border: 0;
  border-right: 1px solid var(--sd-line);
  border-bottom: 1px solid var(--sd-line);
  background: transparent;
  color: var(--sd-user);
  /* 字号跟棋盘走(cqi = 棋盘宽度):小屏不溢出、大屏不显小 */
  font-size: clamp(12px, 6.4cqi, 27px);
  font-weight: 600;
  cursor: pointer;
  overflow: hidden;
  transition:
    background-color 0.12s ease,
    transform 0.1s ease;
  -webkit-tap-highlight-color: transparent;
}
.sd-cell:active {
  transform: scale(0.97);
}
.sd-cell.edge-r {
  border-right-width: 2px;
  border-right-color: var(--sd-line-strong);
}
.sd-cell.edge-b {
  border-bottom-width: 2px;
  border-bottom-color: var(--sd-line-strong);
}
.sd-cell:nth-child(9n) {
  border-right: 0;
}
.sd-cell:nth-child(n + 73) {
  border-bottom: 0;
}
.sd-cell.given {
  color: var(--sd-given);
  font-weight: 700;
}
/* 对角线数独:两条对角线淡着色(优先级低于选中/冲突态) */
.sd-cell.diag {
  background: var(--sd-diag);
}
/* 不规则数独:相邻区域淡色交替,方便一眼看出异形区轮廓 */
.sd-cell.region-tint {
  background: var(--sd-region);
}
/* 奇偶数独:奇数格右上角实心圆点、偶数格右上角空心圆环(都不用底色,免得压住高亮态) */
.sd-cell.parity-odd::after,
.sd-cell.parity-even::after {
  content: '';
  position: absolute;
  top: 3px;
  right: 3px;
  box-sizing: border-box;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  pointer-events: none;
}
.sd-cell.parity-odd::after {
  background: var(--sd-user);
  opacity: 0.75;
}
.sd-cell.parity-even::after {
  border: 1.5px solid var(--sd-user);
  opacity: 0.55;
}
.sd-cell.peer {
  background: var(--sd-peer);
}
.sd-cell.same {
  background: var(--sd-same);
}
.sd-cell.selected {
  background: var(--sd-same);
  box-shadow: inset 0 0 0 2px var(--sd-user);
}
.sd-cell.conflict {
  background: var(--sd-conflict);
  color: #c23a3a;
}
.sd-cell.hint {
  background: var(--sd-hint);
}
.sd-value {
  position: relative;
  z-index: 2;
  line-height: 1;
}
/* 填数/改数/擦除:新数字轻轻弹出,旧数字先淡出,不再硬切 */
.sd-pop-enter-active {
  transition:
    opacity 0.12s ease,
    transform 0.18s var(--ease-spring);
}
.sd-pop-leave-active {
  transition: opacity 0.08s ease;
}
.sd-pop-enter-from {
  opacity: 0;
  transform: scale(0.55);
}
.sd-pop-leave-to {
  opacity: 0;
}

/* 不等式数独:比较符号贴在右/下边中点,∧∨ 表示上下两格的大小方向 */
.sd-ineq {
  position: absolute;
  z-index: 1;
  padding: 0 1px;
  font-size: clamp(7px, 2.4cqi, 12px);
  font-weight: 700;
  line-height: 1;
  color: var(--sd-line-strong);
  background: var(--sd-cell-bg);
  pointer-events: none;
}
.sd-ineq-r {
  top: 50%;
  right: 0;
  transform: translateY(-50%);
}
.sd-ineq-b {
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
}

/* 连续数独:标记边画一根短棒,横跨两格交界 */
.sd-consec {
  position: absolute;
  z-index: 1;
  border-radius: 2px;
  background: var(--sd-consec);
  pointer-events: none;
}
.sd-consec-r {
  top: 50%;
  right: 1px;
  width: 7px;
  height: 3px;
  transform: translateY(-50%);
}
.sd-consec-b {
  bottom: 1px;
  left: 50%;
  width: 3px;
  height: 7px;
  transform: translateX(-50%);
}

/* 罗马数独:V(和为 5)/ X(和为 10)贴在边中点 */
.sd-xv {
  position: absolute;
  z-index: 1;
  padding: 0 2px;
  border-radius: 3px;
  background: var(--sd-cell-bg);
  color: var(--sd-xv);
  font-size: clamp(7px, 2.2cqi, 12px);
  font-weight: 700;
  line-height: 1;
  pointer-events: none;
}
.sd-xv-r {
  top: 50%;
  right: 1px;
  transform: translateY(-50%);
}
.sd-xv-b {
  bottom: 1px;
  left: 50%;
  transform: translateX(-50%);
}

/* 温度计:每格只画「中心 → 相邻格方向」的半段线,格子之间自然接成折线 */
.sd-thermo-seg {
  position: absolute;
  z-index: 0;
  background: var(--sd-thermo);
  pointer-events: none;
}
.sd-thermo-to-t {
  top: 0;
  left: calc(50% - 1px);
  width: 2px;
  height: 50%;
}
.sd-thermo-to-b {
  bottom: 0;
  left: calc(50% - 1px);
  width: 2px;
  height: 50%;
}
.sd-thermo-to-l {
  top: calc(50% - 1px);
  left: 0;
  width: 50%;
  height: 2px;
}
.sd-thermo-to-r {
  top: calc(50% - 1px);
  right: 0;
  width: 50%;
  height: 2px;
}
.sd-thermo-bulb {
  position: absolute;
  z-index: 0;
  top: 50%;
  left: 50%;
  width: 34%;
  aspect-ratio: 1;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  background: var(--sd-thermo);
  pointer-events: none;
}

/* 回文数独:复用温度计的「中心 → 邻格」半段线,换色并在两端补小圆点 */
.sd-pal-seg {
  background: var(--sd-pal);
}
.sd-pal-dot {
  position: absolute;
  z-index: 0;
  top: 50%;
  left: 50%;
  width: 24%;
  aspect-ratio: 1;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  background: var(--sd-pal);
  pointer-events: none;
}

/* 等差数独:每条线段画成贴边界的胶囊框(中间格只画上下/左右两条边,端点封口并加圆角) */
.sd-arith {
  position: absolute;
  z-index: 0;
  border-style: solid;
  border-width: 0;
  border-color: var(--sd-arith);
  pointer-events: none;
}
.sd-arith-h {
  top: 2px;
  bottom: 2px;
  left: 0;
  right: 0;
  border-top-width: 2px;
  border-bottom-width: 2px;
}
.sd-arith-v {
  top: 0;
  bottom: 0;
  left: 2px;
  right: 2px;
  border-left-width: 2px;
  border-right-width: 2px;
}
.sd-arith-h.sd-arith-start {
  left: 2px;
  border-left-width: 2px;
  border-top-left-radius: 6px;
  border-bottom-left-radius: 6px;
}
.sd-arith-h.sd-arith-end {
  right: 2px;
  border-right-width: 2px;
  border-top-right-radius: 6px;
  border-bottom-right-radius: 6px;
}
.sd-arith-v.sd-arith-start {
  top: 2px;
  border-top-width: 2px;
  border-top-left-radius: 6px;
  border-top-right-radius: 6px;
}
.sd-arith-v.sd-arith-end {
  bottom: 2px;
  border-bottom-width: 2px;
  border-bottom-left-radius: 6px;
  border-bottom-right-radius: 6px;
}

/* 窗口数独:额外 3×3 窗用实线描边(与九宫粗线区分) */
.sd-win {
  position: absolute;
  pointer-events: none;
}
.sd-win-t {
  top: 0;
  left: 0;
  right: 0;
  border-top: 2px solid var(--sd-window);
}
.sd-win-b {
  bottom: 0;
  left: 0;
  right: 0;
  border-bottom: 2px solid var(--sd-window);
}
.sd-win-l {
  top: 0;
  bottom: 0;
  left: 0;
  border-left: 2px solid var(--sd-window);
}
.sd-win-r {
  top: 0;
  bottom: 0;
  right: 0;
  border-right: 2px solid var(--sd-window);
}

/* 九龙数独:龙身线穿过格子中心(与温度计同一套半段线),格内数字压在线上面 */
.sd-dragon-seg {
  background: var(--sd-dragon);
}

/* 杀手笼:虚线框在格边内侧描出笼界,笼和标注压在笼内最左上格的角上 */
.sd-board.is-quick .sd-cell {
  cursor: crosshair;
}
.sd-cage {
  position: absolute;
  pointer-events: none;
}
.sd-cage-t {
  top: 0;
  left: 0;
  right: 0;
  border-top: 2px dashed var(--sd-cage);
}
.sd-cage-b {
  bottom: 0;
  left: 0;
  right: 0;
  border-bottom: 2px dashed var(--sd-cage);
}
.sd-cage-l {
  top: 0;
  bottom: 0;
  left: 0;
  border-left: 2px dashed var(--sd-cage);
}
.sd-cage-r {
  top: 0;
  bottom: 0;
  right: 0;
  border-right: 2px dashed var(--sd-cage);
}
.sd-cage-sum {
  position: absolute;
  top: 0;
  left: 2px;
  padding: 0 3px;
  border-radius: 0 0 4px 0;
  background: var(--sd-cell-bg);
  font-size: clamp(6px, 1.9cqi, 10px);
  font-weight: 700;
  line-height: 1.4;
  color: var(--color-text-secondary, #8a8a8a);
}

/* 笔记是覆盖层,不参与格子尺寸计算(否则 3×3 笔记会把行撑高、整盘跳尺寸) */
.sd-notes {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  grid-template-rows: repeat(3, 1fr);
  padding: 3px;
  box-sizing: border-box;
  font-size: clamp(6px, 2.1cqi, 10px);
  font-weight: 500;
  color: var(--color-text-secondary, #8a8a8a);
}
.sd-note {
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.sd-tools {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  width: 100%;
}
.sd-tool {
  padding: 7px 14px;
  border-radius: 999px;
  border: 1px solid var(--sd-line);
  background: transparent;
  color: var(--color-text-primary, #2b2b2b);
  font-size: 13px;
  cursor: pointer;
  transition:
    background-color 0.15s ease,
    color 0.15s ease;
}
.sd-tool:hover:not(:disabled) {
  background: var(--sd-peer);
}
.sd-tool.active {
  background: var(--sd-user);
  border-color: var(--sd-user);
  color: #fff;
}
.sd-tool:disabled {
  opacity: 0.4;
  cursor: default;
}

.sd-numpad {
  display: grid;
  grid-template-columns: repeat(9, minmax(0, 1fr));
  gap: 6px;
  width: 100%;
}
.sd-num {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 10px 0;
  border-radius: 10px;
  border: 1px solid var(--sd-line);
  background: transparent;
  color: var(--sd-user);
  font-size: 17px;
  cursor: pointer;
  transition: background-color 0.15s ease;
}
.sd-num:hover:not(:disabled) {
  background: var(--sd-peer);
}
/* 当前焦点数字(点数字盘或棋盘上的该数字):与棋盘「同数字」高亮同色 */
.sd-num.active {
  background: var(--sd-same);
  border-color: var(--sd-user);
  color: var(--sd-user);
  font-weight: 700;
}
.sd-num.done {
  opacity: 0.32;
  cursor: default;
}
.sd-num-left {
  position: absolute;
  right: 4px;
  bottom: 1px;
  font-size: 9px;
  color: var(--color-text-secondary, #8a8a8a);
}

.sd-hint {
  font-size: 12px;
  color: var(--color-text-secondary, #8a8a8a);
  line-height: 1.6;
}

/* 提示说明:技巧名 + 为什么 + 用过的技巧链 */
.sd-hint-note {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
  padding: 9px 12px;
  border-radius: 12px;
  border: 1px solid color-mix(in srgb, var(--sd-user) 28%, transparent);
  background: var(--sd-peer);
  text-align: left;
}
.sd-hint-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.sd-hint-tech {
  font-size: 12px;
  color: var(--sd-user);
}
.sd-hint-close {
  padding: 0 4px;
  border: 0;
  background: transparent;
  color: var(--color-text-secondary, #8a8a8a);
  font-size: 15px;
  line-height: 1;
  cursor: pointer;
}
.sd-hint-close:hover {
  color: var(--color-text-primary, #2b2b2b);
}
.sd-hint-text {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-primary, #2b2b2b);
}
.sd-hint-via {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.sd-hint-via-label {
  font-size: 11px;
  color: var(--color-text-secondary, #8a8a8a);
}
.sd-hint-chip {
  padding: 1px 8px;
  border-radius: 999px;
  border: 1px solid var(--sd-line);
  font-size: 11px;
  color: var(--color-text-secondary, #8a8a8a);
}

/* 技巧教学 */
.sd-teach-intro,
.sd-teach-foot {
  margin: 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--color-text-secondary, #8a8a8a);
}
.sd-teach-intro {
  margin-bottom: 10px;
}
.sd-teach-foot {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--color-border, rgba(0, 0, 0, 0.12));
}
.sd-teach-list {
  display: grid;
  gap: 10px;
  margin: 0;
  padding-left: 20px;
}
.sd-teach-item {
  font-size: 13px;
  color: var(--color-text-primary, #2b2b2b);
}
.sd-teach-item b {
  font-size: 13px;
}
.sd-teach-item p {
  margin: 3px 0 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--color-text-secondary, #8a8a8a);
}

.sd-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--overlay-scrim);
  backdrop-filter: var(--overlay-blur);
  border-radius: 14px;
  z-index: 5;
}
.sd-overlay-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 26px 40px;
  border-radius: 16px;
  background: var(--color-surface, #fff);
  color: var(--color-text-primary, #2b2b2b);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.35);
}
.sd-overlay-icon {
  font-size: 38px;
}
.sd-overlay-title {
  font-size: 19px;
  font-weight: 700;
}
.sd-overlay-meta {
  font-size: 13px;
  color: var(--color-text-secondary, #8a8a8a);
}
.sd-overlay-reward {
  font-size: 14px;
  font-weight: 700;
  color: #e0933a;
}

html.dark .sd-shell {
  --sd-line: rgba(255, 255, 255, 0.13);
  --sd-line-strong: rgba(255, 255, 255, 0.34);
  --sd-cell-bg: rgba(255, 255, 255, 0.03);
  --sd-given: rgba(255, 255, 255, 0.92);
  --sd-peer: rgba(184, 140, 110, 0.14);
  --sd-same: rgba(184, 140, 110, 0.28);
  --sd-conflict: rgba(214, 76, 76, 0.32);
  --sd-hint: rgba(96, 176, 120, 0.34);
  --sd-diag: rgba(184, 140, 110, 0.13);
  --sd-cage: rgba(184, 140, 110, 0.7);
  --sd-region: rgba(184, 140, 110, 0.1);
  --sd-window: rgba(96, 190, 210, 0.6);
  --sd-thermo: rgba(232, 148, 100, 0.7);
  --sd-consec: rgba(160, 138, 224, 0.8);
  --sd-xv: rgba(112, 196, 214, 0.95);
  --sd-arith: rgba(96, 196, 164, 0.9);
  --sd-pal: rgba(228, 132, 184, 0.8);
  --sd-dragon: rgba(234, 168, 92, 0.72);
}
html.dark .sd-cell.conflict {
  color: #ff9a9a;
}
.sd-shell .gh-chip.is-warn b {
  color: #d64c4c;
}
html.dark .sd-shell .gh-chip.is-warn b {
  color: #ff9a9a;
}

/* 窄窗(手机竖):数字盘与工具键压一档,棋盘仍是通栏最大尺寸 */
@container (max-width: 549px) {
  .sd-tool {
    padding: 6px 11px;
    font-size: 12px;
  }
  .sd-numpad {
    gap: 4px;
  }
  .sd-num {
    padding: 8px 0;
    font-size: 15px;
    border-radius: 8px;
  }
  /* 提示条会占掉一两行高度:棋盘由 --sd-panel-h 自动缩,这里只把间隙收紧一档 */
  .sd-layout.has-hint {
    gap: 8px;
  }
  .sd-layout.has-hint .sd-panel {
    gap: 6px;
  }
  .sd-layout.has-hint .sd-tools {
    row-gap: 6px;
  }
  .sd-layout.has-hint .sd-hint-note {
    padding-block: 6px;
  }
}

/* 横屏(游戏区宽 > 高):棋盘按高度铺满左列,数字盘/工具收成右侧一列。
   必须放在最后:上面 .sd-numpad/.sd-tools/.sd-num/.sd-tool 的基础声明会按书写顺序覆盖同权重的容器规则 */
@container (min-aspect-ratio: 6/5) {
  .sd-layout {
    /* 右列按比例给(手机横屏时收窄让棋盘吃满高度),上限 232px 保持平板/电脑上的手感 */
    grid-template-columns: minmax(0, 1fr) clamp(148px, 30%, 232px);
    grid-template-rows: minmax(0, 1fr);
    gap: clamp(12px, 3cqi, 30px);
    --sd-frame-size: min(calc(100% - 8px), calc(100cqh - 8px), 560px);
  }
  .sd-panel {
    max-height: 100%;
    overflow-y: auto;
    overscroll-behavior: contain;
    padding-right: 2px;
  }
  .sd-numpad {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .sd-tools {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .sd-num {
    padding: 8px 0;
    font-size: 15px;
  }
  .sd-tool {
    padding: 6px 8px;
    font-size: 12px;
  }
}
</style>
