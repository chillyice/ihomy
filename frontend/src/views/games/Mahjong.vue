<!-- 麻将(推倒胡简化版):单机为玩家 vs 三位 AI,「一起玩」房间为 4 席真人(空位 AI 补)。
     规则内核在 utils/games/mahjong.js(判胡/听牌/碰/计番/AI 选牌都是纯函数);
     本页做发牌/回合驱动/交互与结算展示,单机赢一局走统一 H5 奖励通道(每日限次)。
     房间局:房主权威跑牌局,公共盘面广播、每个人只收到自己的手牌,其余三家只看剩牌数。 -->
<template>
  <div ref="pageEl" class="page game-page game-page-readable">
    <PageToolbar root-class="game-topbar" :holder-margin="0" always>
      <GameTopActions
        :is-fullscreen="isFullscreen"
        :wide="isWide"
        :fullscreen-label="$t('games.mahjong.fullscreen')"
        :exit-label="$t('games.mahjong.exitFullscreen')"
        @back="goBack"
        @fullscreen="toggleFullscreen"
        @wide="toggleWide"
      />
    </PageToolbar>

    <div ref="gameCardEl" class="game-surface mahjong-card" @contextmenu.prevent @dblclick.prevent>
      <GameTopActions v-if="isFullscreen" :show-back="false" :show-wide="false" :is-fullscreen="true" @fullscreen="toggleFullscreen" />
      <GameRoomBar
        v-if="play.active"
        :room="play.room"
        :is-host="play.isHost"
        :score="myScore"
        :reported="play.reported"
        :ended="play.ended"
        :result="play.spec?.result"
        :my-id="play.myId"
        exit-ends-room
        @submit="submitRoomScore"
        @leave="play.leave"
      />

      <!-- 封面(仅单机;房间局由房主选规则后开牌) -->
      <div v-if="phase === 'idle' && !play.active" class="cover">
        <div class="cover-title">{{ $t('games.mahjong.title') }}</div>
        <div class="cover-desc">{{ $t('games.mahjong.desc') }}</div>

        <!-- 开局规则选择:按规则名单选(每条规则自带胡牌口径) -->
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
          <div class="rules-hint">{{ $t('games.mahjong.rulesHint') }}</div>
        </div>

        <button class="start-btn" type="button" @click="startGame">{{ $t('games.mahjong.start') }}</button>
        <div v-if="rounds > 0" class="cover-stats">{{ $t('games.mahjong.roundStats', { win: playerWins, lose: aiWins }) }}</div>
        <div class="cover-hint">{{ $t('games.mahjong.coverHint') }}</div>
      </div>

      <!-- 牌桌 -->
      <template v-else>
        <!-- 房间局:房主开牌前先定规则(访客等待盘面) -->
        <div v-if="rulesPickerOpen && play.isHost" class="rules-overlay">
          <div class="rules-panel rules-panel-float">
            <div class="rules-title">{{ $t('games.mahjong.rulesPickTitle') }}</div>
            <div class="rules-list" role="radiogroup" :aria-label="$t('games.mahjong.rulesPickTitle')">
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
            <el-button type="primary" round @click="startGame">{{ $t('games.mahjong.rulesConfirm') }}</el-button>
          </div>
        </div>

        <div v-if="waitingHost" class="table-waiting">{{ $t('games.mahjong.waitingHost') }}</div>
        <template v-else>
          <!-- 立体牌桌:对家在上 / 上家在左 / 下家在右 / 我在下;
               吃碰杠副露贴在各家名牌旁,散牌弃张全部汇进中央牌池;
               桌面按固定宽高比居中,整桌带半俯视透视 -->
          <div class="mj-stage">
            <div class="mj-table">
              <!-- 对家(上) -->
              <div v-if="topSeat" class="mj-edge mj-top" :class="{ active: turn === topSeat.seat && phase === 'playing' }">
                <span class="mj-seat-name" :title="seatName(topSeat.seat)">
                  {{ seatName(topSeat.seat) }}<span v-if="topSeat.seat === 0" class="dealer-badge">{{ $t('games.mahjong.dealer') }}</span>
                </span>
                <!-- 对家视角的右手侧在屏幕左,故副露排到手牌牌背前面 -->
                <div v-if="meldGroups(topSeat.seat).length" class="mj-meld-corner">
                  <span v-for="(group, i) in meldGroups(topSeat.seat)" :key="i" class="meld-group">
                    <template v-if="group.dark">
                      <span v-for="n in 4" :key="n" class="tile mini back" aria-hidden="true" />
                    </template>
                    <template v-else>
                      <span v-for="(kind, j) in group.kinds" :key="j" class="tile mini" :class="tileClass(kind)" :title="tileLabel(kind)">
                        <TileFace :kind="kind" />
                      </span>
                    </template>
                  </span>
                </div>
                <span class="mj-backs">
                  <span v-for="n in seatTileCount(topSeat.seat)" :key="n" class="tile back sm" aria-hidden="true" />
                </span>
              </div>

              <!-- 上家(左) -->
              <div v-if="leftSeat" class="mj-edge mj-left" :class="{ active: turn === leftSeat.seat && phase === 'playing' }">
                <span class="mj-seat-name" :title="seatName(leftSeat.seat)">
                  {{ seatName(leftSeat.seat) }}<span v-if="leftSeat.seat === 0" class="dealer-badge">{{ $t('games.mahjong.dealer') }}</span>
                </span>
                <span class="mj-backs-col">
                  <span v-for="n in seatTileCount(leftSeat.seat)" :key="n" class="tile back sm" aria-hidden="true" />
                </span>
                <div v-if="meldGroups(leftSeat.seat).length" class="mj-meld-corner">
                  <span v-for="(group, i) in meldGroups(leftSeat.seat)" :key="i" class="meld-group">
                    <template v-if="group.dark">
                      <span v-for="n in 4" :key="n" class="tile mini back" aria-hidden="true" />
                    </template>
                    <template v-else>
                      <span v-for="(kind, j) in group.kinds" :key="j" class="tile mini" :class="tileClass(kind)" :title="tileLabel(kind)">
                        <TileFace :kind="kind" />
                      </span>
                    </template>
                  </span>
                </div>
              </div>

              <!-- 中央弃牌池:四方牌河围着牌桌信息 -->
              <div class="mj-pool">
                <div
                  v-for="river in poolRivers"
                  :key="river.pos"
                  :ref="(el) => setRiverEl(river.seat, el)"
                  class="mj-river"
                  :class="'river-' + river.pos"
                  role="region"
                  :aria-label="$t('games.mahjong.discards', { name: river.seat >= 0 ? seatName(river.seat) : '', n: river.kinds.length })"
                >
                  <span
                    v-for="(kind, i) in river.kinds"
                    :key="i"
                    class="tile mini"
                    :class="[tileClass(kind), { latest: lastDiscard?.from === river.seat && i === river.kinds.length - 1 }]"
                    :title="tileLabel(kind)"
                    :aria-label="tileLabel(kind)"
                    role="img"
                  >
                    <TileFace :kind="kind" />
                  </span>
                </div>
                <div class="mj-hub">
                  <span class="wall-count">{{ $t('games.mahjong.wallLeft', { n: wallLeft }) }}</span>
                  <span class="rules-chip">{{ $t(`games.mahjong.presets.${rulesLabel}`) }}</span>
                  <span class="turn-label">{{ turnLabel }}</span>
                </div>
              </div>

              <!-- 下家(右) -->
              <div v-if="rightSeat" class="mj-edge mj-right" :class="{ active: turn === rightSeat.seat && phase === 'playing' }">
                <span class="mj-seat-name" :title="seatName(rightSeat.seat)">
                  {{ seatName(rightSeat.seat)
                  }}<span v-if="rightSeat.seat === 0" class="dealer-badge">{{ $t('games.mahjong.dealer') }}</span>
                </span>
                <!-- 下家视角的右手侧在屏幕上,副露排到手牌牌背前面 -->
                <div v-if="meldGroups(rightSeat.seat).length" class="mj-meld-corner">
                  <span v-for="(group, i) in meldGroups(rightSeat.seat)" :key="i" class="meld-group">
                    <template v-if="group.dark">
                      <span v-for="n in 4" :key="n" class="tile mini back" aria-hidden="true" />
                    </template>
                    <template v-else>
                      <span v-for="(kind, j) in group.kinds" :key="j" class="tile mini" :class="tileClass(kind)" :title="tileLabel(kind)">
                        <TileFace :kind="kind" />
                      </span>
                    </template>
                  </span>
                </div>
                <span class="mj-backs-col">
                  <span v-for="n in seatTileCount(rightSeat.seat)" :key="n" class="tile back sm" aria-hidden="true" />
                </span>
              </div>

              <!-- 我方(下):碰/杠/吃/胡询问浮层 + 副露 + 立牌手牌 + 操作 -->
              <div class="mj-edge mj-bottom" :class="{ active: myTurn }">
                <!-- 询问浮层:悬在手牌上方,出现即须表态,「过」=放弃全部 -->
                <div v-if="claimPrompt" class="claim-float" role="alert">
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
                  <el-button
                    v-for="(pair, i) in activeChiOptions"
                    :key="'chi' + i"
                    type="primary"
                    size="large"
                    round
                    @click="answerClaim(true, pair)"
                  >
                    {{ $t('games.mahjong.chi') }}
                    <span class="tile mini" :class="tileClass(pair[0])"><TileFace :kind="pair[0]" /></span>
                    <span class="tile mini" :class="tileClass(pair[1])"><TileFace :kind="pair[1]" /></span>
                  </el-button>
                  <el-button size="large" round @click="answerClaim(false)">{{ $t('games.mahjong.pass') }}</el-button>
                </div>
                <div v-if="meldGroups(mySeat).length" class="mj-meld-corner">
                  <span v-for="(group, i) in meldGroups(mySeat)" :key="i" class="meld-group">
                    <template v-if="group.dark">
                      <span v-for="n in 4" :key="n" class="tile mini back" aria-hidden="true" />
                    </template>
                    <template v-else>
                      <span v-for="(kind, j) in group.kinds" :key="j" class="tile mini" :class="tileClass(kind)" :title="tileLabel(kind)">
                        <TileFace :kind="kind" />
                      </span>
                    </template>
                  </span>
                </div>
                <div v-if="!play.isSpectator" class="hand-row">
                  <button
                    v-for="tile in myHandView"
                    :key="tile.id"
                    type="button"
                    class="tile hand"
                    :class="[tileClass(tile.kind), { drawn: tile.justDrawn, dim: phase !== 'playing' }]"
                    :disabled="!canDiscardNow"
                    :title="tileLabel(tile.kind)"
                    :aria-label="tileLabel(tile.kind)"
                    @click="onTileClick(tile)"
                  >
                    <TileFace :kind="tile.kind" />
                  </button>
                  <template v-if="!play.isSpectator">
                    <span v-for="n in handBackFill" :key="'ph' + n" class="tile back hand" aria-hidden="true" />
                  </template>
                </div>
                <div v-if="!play.isSpectator" class="my-actions">
                  <el-button v-if="canSelfWin" type="primary" size="default" round @click="declareSelfWin">
                    {{ $t('games.mahjong.selfWin') }}
                  </el-button>
                  <el-button
                    v-for="opt in myGangOptions"
                    :key="opt.kind + '-' + opt.type"
                    type="warning"
                    size="default"
                    round
                    @click="declareSelfGang(opt)"
                  >
                    {{ $t(opt.type === 'angang' ? 'games.mahjong.anGang' : 'games.mahjong.buGang') }}
                  </el-button>
                  <span class="my-hint">{{ actionHint }}</span>
                </div>
                <!-- 听牌提示:摸牌后建议打哪张、弃后听哪几张;听牌时显示当前听口 -->
                <div v-if="advice" class="advice-row">
                  <template v-if="advice.discard != null">
                    <span class="advice-label">{{ $t('games.mahjong.adviceDiscard') }}</span>
                    <span class="tile mini" :class="tileClass(advice.discard)"><TileFace :kind="advice.discard" /></span>
                  </template>
                  <span class="advice-label">{{ $t('games.mahjong.listening') }}</span>
                  <span v-for="k in advice.wins" :key="k" class="tile mini" :class="tileClass(k)"><TileFace :kind="k" /></span>
                </div>
              </div>
            </div>
          </div>
        </template>

        <!-- 结算浮层 -->
        <Transition name="game-overlay">
          <div v-if="phase === 'ended'" class="overlay game-overlay">
            <div class="overlay-card game-overlay-card">
              <div class="overlay-icon">{{ result?.winner === mySeat ? '🎉' : result?.draw ? '🀄' : '😌' }}</div>
              <div class="overlay-title">
                <template v-if="!result"></template>
                <template v-else-if="result.draw">{{ $t('games.mahjong.drawGame') }}</template>
                <template v-else-if="result.winner === mySeat">{{ $t('games.mahjong.youWin') }}</template>
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
              <div v-if="result && !result.draw && result.handTiles?.length" class="overlay-hand">
                <span v-for="(g, i) in result.melds || []" :key="'m' + i" class="meld-group">
                  <template v-if="g.dark">
                    <span v-for="n in 4" :key="n" class="tile mini back" aria-hidden="true" />
                  </template>
                  <template v-else>
                    <span v-for="(kind, j) in g.kinds" :key="j" class="tile mini" :class="tileClass(kind)" :title="tileLabel(kind)">
                      <TileFace :kind="kind" />
                    </span>
                  </template>
                </span>
                <span v-for="(kind, i) in result.handTiles" :key="i" class="tile mini" :class="tileClass(kind)" :title="tileLabel(kind)">
                  <TileFace :kind="kind" />
                </span>
              </div>
              <div v-if="rewardMsg" class="overlay-reward">{{ rewardMsg }}</div>
              <div v-if="play.active && play.status === 'PLAYING'" class="overlay-wait">{{ $t('games.mahjong.waitSettle') }}</div>
              <el-button type="primary" size="small" round @click="onResultAction">
                {{ play.active ? $t('games.room.backRoom') : $t('games.mahjong.again') }}
              </el-button>
            </div>
          </div>
        </Transition>
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, h, inject, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { gameApi, roomApi } from '@/api'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import PageToolbar from '@/components/PageToolbar.vue'
import GameTopActions from '@/components/games/GameTopActions.vue'
import GameRoomBar from '@/components/games/GameRoomBar.vue'
import { useGameView } from '@/composables/useGameView'
import { useGameRoomPlay } from '@/composables/useGameRoomPlay'
import { isSpectatorRole } from '@/utils/gameRoom'
import { useChatStore } from '@/stores/chat'
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
  mahjongCounts,
  mahjongHandScore,
  mahjongKind,
  mahjongTenpai,
  normalizeMahjongRules,
  sortMahjongTiles,
} from '@/utils/games/mahjong'
import { mahjongTileSrc } from '@/utils/games/mahjongTiles'

const router = useRouter()
const { t } = useI18n()
const sunLight = inject(SUN_LIGHT_KEY, null)
const chatStore = useChatStore()

const HONOR_KEYS = ['east', 'south', 'west', 'north', 'zhong', 'fa', 'bai']

/** 牌的读屏名字:字牌取字,数牌「点数 + 花色」 */
const tileLabel = (kind) =>
  kind >= 27
    ? t(`games.mahjong.honor.${HONOR_KEYS[kind - 27]}`)
    : `${(kind % 9) + 1}${t(`games.mahjong.suit.${['w', 't', 's'][Math.floor(kind / 9)]}`)}`

// 牌面组件:开源 SVG 牌面(assets/games/mahjong,见同目录 LICENSE.md),
// 牌身与尺寸交给 `.tile` 的 CSS;白板(33)就是空白牌,素材缺失时退回「数字 + 花色」文本
const TileFace = (props) => {
  const kind = props.kind
  const src = mahjongTileSrc(kind)
  if (src) return h('img', { class: 'tile-img', src, alt: '', draggable: false })
  if (kind >= 27) {
    return h(
      'span',
      {
        class: ['tile-face', 'honor', { red: kind === 31, green: kind === 32, blue: kind === 33, blank: kind === 33 }],
      },
      kind === 33 ? '' : t(`games.mahjong.honor.${HONOR_KEYS[kind - 27]}`),
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
const { isFullscreen, isWide, toggleFullscreen, toggleWide } = useGameView({ page: pageEl, stage: gameCardEl, fit: false })

// ---------- 「一起玩」房间(?room=<id>) ----------
// 房主权威:房主跑完整牌局,公共状态广播给全房,手牌按人定向下发(其余人只看剩牌数),
// 其他人的出牌/碰/胡都作为操作意图发给房主执行。空位由 AI 补满 4 席。
const play = useGameRoomPlay({ onSignal: onRoomSignal, onRoom: onRoomChange })
const isGuest = computed(() => play.active && !play.isHost)

// ---------- 对局状态 ----------
const phase = ref('idle') // idle | playing | ended
const turn = ref(0) // 0..3 座次
const seatList = ref([]) // [{ seat, userId, name, ai }]
const hands = ref([[], [], [], []]) // 暗手牌实例 id;访客只有自己那格
const guestHand = ref([]) // 访客收到的本人手牌
const melds = ref([[], [], [], []]) // 副露面子 [{ kind, from, type: 'pong'|'gang'|'angang'|'chi', pair? }]
const rivers = ref([[], [], [], []]) // 弃牌 kind
const wall = ref([])
const wallCount = ref(0)
const handCounts = ref([0, 0, 0, 0])
const lastDiscard = ref(null) // { kind, from }
const drawnTileId = ref(null)
const canSelfWin = ref(false)
const localClaim = ref(null) // 房主/单机本座收到的询问:'win' | 'pong' | 'chi' | 'gang' | null
const guestClaim = ref(null) // 访客收到的询问
const guestChiOptions = ref([]) // 访客收到的吃组合候选
const guestGangKinds = ref([]) // 访客回合内可杠(来自 mj:hand)
const busyAi = ref(false)
const result = ref(null)
const rewardMsg = ref('')
const playerWins = ref(0)
const aiWins = ref(0)
const rounds = ref(0)

// ---------- 规则(按规则名单选,随房间广播) ----------
const rules = ref(normalizeMahjongRules(null))
const rulesDraftKey = ref('tuiDaoHu') // 开局草稿:所选规则名
const rulesPickerOpen = ref(false) // 房间局:房主开牌前先选规则
const claimChiOptions = ref([]) // 本机收到的吃组合候选
const selfGangKinds = ref([]) // 本机回合内可杠(暗杠/补杠)[{ kind, type }]

const rulesLabel = computed(() => {
  const r = rules.value
  for (const key of Object.keys(MAHJONG_RULE_PRESETS)) {
    const p = MAHJONG_RULE_PRESETS[key]
    if (p.allowChi === r.allowChi && p.allowGang === r.allowGang && p.pairConstraint === r.pairConstraint) return key
  }
  return 'custom'
})

let session = 0 // 会话令牌:重开/离开时旧循环自行退出
const turnWaiters = new Map() // seat -> resolve(action)
const claimWaiters = new Map() // seat -> resolve('claim' | 'pass')

const myId = computed(() => play.myId)
const mySeat = computed(() => {
  const idx = seatList.value.findIndex((s) => !s.ai && String(s.userId) === String(myId.value))
  if (idx >= 0) return idx
  // 观战没有席位:不占座、不显示自己的手牌,只看公共盘面
  return play.isSpectator ? -1 : 0
})
const myTurn = computed(() => phase.value === 'playing' && turn.value === mySeat.value)
const myHandTiles = computed(() => (isGuest.value ? guestHand.value : hands.value[mySeat.value] || []))
const myMelds = computed(() => melds.value[mySeat.value] || [])
const claimPrompt = computed(() => guestClaim.value || localClaim.value)
// 吃询问时给本机的组合候选(访客来自信令,房主/单机来自询问下发)
const activeChiOptions = computed(() =>
  claimPrompt.value === 'chi' ? (isGuest.value ? guestChiOptions.value : claimChiOptions.value) : [],
)
// 本机回合内可杠(暗杠/补杠):房主/单机在摸牌后由主循环写;访客在 mj:hand 里收 canGangKinds
const myGangOptions = computed(() => (isGuest.value ? guestGangKinds.value : selfGangKinds.value))
const waitingHost = computed(() => isGuest.value && phase.value === 'playing' && !result.value && handCounts.value.every((n) => !n))
const wallLeft = computed(() => (isGuest.value ? wallCount.value : wall.value.length))
const seatTileCount = (seat) => (isGuest.value ? handCounts.value[seat] || 0 : hands.value[seat]?.length || 0)
const myScore = computed(() => {
  if (!result.value || result.value.draw) return 0
  return result.value.winner === mySeat.value ? result.value.score : 0
})
const canDiscardNow = computed(
  () => phase.value === 'playing' && myTurn.value && !claimPrompt.value && !busyAi.value && myHandTiles.value.length % 3 === 2,
)

const seatName = (seat) => {
  const s = seatList.value.find((x) => x.seat === seat)
  if (!s) return ''
  if (!s.ai) return s.name || t('games.room.me')
  const aiSeats = seatList.value.filter((x) => x.ai).map((x) => x.seat)
  const ordinal = aiSeats.indexOf(seat)
  return t(`games.mahjong.ai${Math.min(ordinal + 1, 3)}`)
}
const myHandView = computed(() =>
  sortMahjongTiles(myHandTiles.value).map((id) => ({ id, kind: mahjongKind(id), justDrawn: id === drawnTileId.value })),
)

// 方位式牌桌:以本机为基准换算相对方位(观战固定 0 号视角),
// 对家在上、上家在左、下家在右、我在下。
const viewSeat = computed(() => (mySeat.value >= 0 ? mySeat.value : 0))
const relPos = (seat) => (seat - viewSeat.value + 4) % 4
const topSeat = computed(() => seatList.value.find((s) => relPos(s.seat) === 2))
const leftSeat = computed(() => seatList.value.find((s) => relPos(s.seat) === 3))
const rightSeat = computed(() => seatList.value.find((s) => relPos(s.seat) === 1))

const riverEls = new Map()
function setRiverEl(seat, el) {
  if (el) riverEls.set(seat, el)
  else riverEls.delete(seat)
}

watch(
  () => rivers.value.map((river) => river.length),
  async (lengths, previous) => {
    const follow = lengths.map((length, seat) => {
      const el = riverEls.get(seat)
      if (!el || length <= previous[seat]) return null
      // 纵向牌河(对家/我方)跟随垂直滚动,横向牌河(上/下家)跟随水平滚动
      const vertical = el.scrollHeight > el.clientHeight
      const nearEnd = vertical ? el.scrollHeight - el.scrollTop - el.clientHeight < 4 : el.scrollWidth - el.scrollLeft - el.clientWidth < 4
      return nearEnd ? (vertical ? { el, axis: 'y' } : { el, axis: 'x' }) : null
    })
    await nextTick()
    follow.forEach((f) => {
      if (!f) return
      if (f.axis === 'y') f.el.scrollTop = f.el.scrollHeight
      else f.el.scrollLeft = f.el.scrollWidth
    })
  },
)

const handBackFill = computed(() => (play.isSpectator ? 0 : Math.max(0, 13 - myHandTiles.value.length)))

/** 副露面子的渲染视图:暗杠整排牌背,吃按顺子序,明杠/补杠四张 */
const meldView = (m) => {
  const type = m.type || 'pong'
  if (type === 'chi') return { dark: false, kinds: [m.pair[0], m.kind, m.pair[1]].sort((a, b) => a - b) }
  if (type === 'angang') return { dark: true, kinds: [] }
  if (type === 'gang' || type === 'bugang') return { dark: false, kinds: [m.kind, m.kind, m.kind, m.kind] }
  return { dark: false, kinds: [m.kind, m.kind, m.kind] }
}

// 中央牌池:四方牌河(对家上、上家左、下家右、我在下),只有位置不同,渲染一致
const poolRivers = computed(() =>
  [
    { pos: 'top', seat: topSeat.value?.seat },
    { pos: 'left', seat: leftSeat.value?.seat },
    { pos: 'right', seat: rightSeat.value?.seat },
    { pos: 'bottom', seat: mySeat.value },
  ].map(({ pos, seat }) => ({
    pos,
    seat: seat ?? -1,
    kinds: seat != null && seat >= 0 ? rivers.value[seat] || [] : [],
  })),
)

// 副露:贴在各家名牌旁展示,不挤占中央牌池
const meldGroups = (seat) => (seat != null && seat >= 0 ? (melds.value[seat] || []).map(meldView) : [])

const turnLabel = computed(() => {
  if (phase.value !== 'playing') return ''
  if (turn.value === mySeat.value) return t('games.mahjong.yourTurn')
  const name = seatName(turn.value)
  const seat = seatList.value.find((s) => s.seat === turn.value)
  return t(seat?.ai ? 'games.mahjong.aiTurn' : 'games.mahjong.playerTurn', { name })
})

const actionHint = computed(() => {
  if (phase.value !== 'playing') return ''
  if (canSelfWin.value) return t('games.mahjong.selfWinHint')
  if (claimPrompt.value === 'win') return t('games.mahjong.claimHint')
  if (claimPrompt.value === 'pong') return t('games.mahjong.pongHint')
  if (claimPrompt.value === 'chi') return t('games.mahjong.chiHint')
  if (claimPrompt.value === 'gang') return t('games.mahjong.gangHint')
  if (myTurn.value) return t('games.mahjong.discardHint')
  if (turn.value === mySeat.value) return ''
  return t('games.mahjong.waitOthers')
})

/**
 * 听牌提示(纯函数 computed):轮到自己且暗手为 14-3×碰 张时,
 * 枚举每张可弃牌算「弃后听几张」,取听口最宽的;13-3×碰 张时显示当前听口。
 */
const advice = computed(() => {
  if (phase.value !== 'playing' || turn.value !== mySeat.value || busyAi.value) return null
  if (canSelfWin.value || claimPrompt.value) return null
  const hand = myHandTiles.value
  const open = myMelds.value.length
  const counts = mahjongCounts(hand)
  if (hand.length === 14 - open * 3) {
    let best = null
    for (let k = 0; k < 34; k++) {
      if (!counts[k]) continue
      const trial = [...counts]
      trial[k] -= 1
      const wins = mahjongTenpai(trial, open, rules.value)
      if (!best || wins.length > best.wins.length) best = { discard: k, wins }
    }
    return best
  }
  if (hand.length === 13 - open * 3) {
    return { discard: null, wins: mahjongTenpai(counts, open, rules.value) }
  }
  return null
})

const tileClass = (kind) => {
  if (kind >= 27) return `honor-${['east', 'south', 'west', 'north', 'zhong', 'fa', 'bai'][kind - 27]}`
  return `suit-${Math.floor(kind / 9)}`
}

const goBack = async () => {
  if (!play.active || !play.room?.id) {
    router.push('/games')
    return
  }
  const roomId = play.room.id
  // 实时权威牌局:房主退出即结束(否则其他人等不到出牌),访客退出则离房把人位交给 AI
  if (play.room.status === 'PLAYING') {
    try {
      await (play.isHost ? roomApi.end(roomId) : roomApi.leave(roomId))
    } catch {
      // 房间已结束时忽略
    }
  }
  router.push(play.isHost ? `/games/room/${roomId}` : '/games')
}

function sleep(ms) {
  return new Promise((res) => setTimeout(res, ms))
}

// ---------- 座次 ----------

function buildSoloSeats() {
  const seats = [{ seat: 0, userId: myId.value, name: '', ai: false }]
  for (let i = 1; i < 4; i++) seats.push({ seat: i, userId: null, name: '', ai: true })
  return seats
}

/** 房间座次:房主 0 号位,其余玩家按加入顺序入座,空位 AI 补满 */
function buildRoomSeats() {
  const hostId = play.room?.hostId
  const players = (play.room?.members || [])
    .filter((m) => !isSpectatorRole(m.role))
    .sort((a, b) => {
      if (String(a.userId) === String(hostId)) return -1
      if (String(b.userId) === String(hostId)) return 1
      return 0
    })
  const seats = []
  for (const m of players.slice(0, 4)) {
    seats.push({ seat: seats.length, userId: m.userId, name: m.name, ai: false })
  }
  while (seats.length < 4) seats.push({ seat: seats.length, userId: null, name: '', ai: true })
  return seats
}

// ---------- 发牌 / 主循环 ----------

function drawOne() {
  const id = wall.value.pop()
  return id == null ? null : id
}

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

/** 吃:仅上家可吃,规则开关 + 手里能凑出顺子组合 */
function canSeatChi(seat, kind) {
  if (!rules.value.allowChi) return []
  if (seat !== (lastDiscardSeat() + 3) % 4) return []
  if ((melds.value[seat]?.length || 0) >= 3) return []
  return mahjongChiOptions(mahjongCounts(hands.value[seat]), kind)
}

/** 上家(刚弃牌那位的上一位)座次 */
function lastDiscardSeat() {
  return lastDiscard.value?.from ?? -1
}

/** 明杠:规则开关 + 暗手有三张同牌 + 面子数未满 */
function canSeatMingGang(seat, kind) {
  if (!rules.value.allowGang) return false
  if ((melds.value[seat]?.length || 0) >= 4) return false
  return canMahjongMingGang(mahjongCounts(hands.value[seat]), kind)
}

/** 本回合内可杠(暗杠 / 补杠):摸牌后自杠用 */
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

/** AI 碰牌倾向:字牌/幺九必碰,数牌中张在已有多对时碰,避免拆掉成顺子 */
function aiShouldPong(seat, kind) {
  const c = mahjongCounts(hands.value[seat])
  if (c[kind] < 2) return false
  if ((melds.value[seat]?.length || 0) >= 3) return false
  if (kind >= 27 || kind % 9 === 0 || kind % 9 === 8) return true
  return c.filter((n) => n === 2).length >= 2
}

/** AI 吃牌倾向:吃后潜力不降才吃(mahjongAiChooseChi 已内置比较) */
function aiChooseChi(seat, kind) {
  return mahjongAiChooseChi(mahjongCounts(hands.value[seat]), kind)
}

async function startGame() {
  if (play.active && !play.isHost) return
  session += 1
  const mySession = session
  turnWaiters.clear()
  claimWaiters.clear()
  phase.value = 'playing'
  result.value = null
  guestClaim.value = null
  localClaim.value = null
  claimChiOptions.value = []
  selfGangKinds.value = []
  rulesPickerOpen.value = false
  rewardMsg.value = ''
  lastDiscard.value = null
  drawnTileId.value = null
  canSelfWin.value = false
  busyAi.value = false
  rules.value = normalizeMahjongRules(MAHJONG_RULE_PRESETS[rulesDraftKey.value])
  rivers.value = [[], [], [], []]
  melds.value = [[], [], [], []]
  guestHand.value = []
  seatList.value = play.active ? buildRoomSeats() : buildSoloSeats()
  wall.value = createMahjongWall()
  const deal = [[], [], [], []]
  for (let round = 0; round < 13; round++) {
    for (let seat = 0; seat < 4; seat++) deal[seat].push(wall.value.pop())
  }
  hands.value = deal
  handCounts.value = deal.map((h) => h.length)
  turn.value = 0
  rounds.value += 1
  publishAll()
  runGameLoop(mySession)
}

/** 主循环:回合制驱动;轮到人时挂起等操作(房间局超时由 AI 代打),碰/杠/吃后不摸牌直接出牌 */
async function runGameLoop(mySession) {
  let current = turn.value
  let skipDraw = false
  let justGangDrawn = false // 明杠/暗杠/补杠已在 apply 里摸了岭上牌,循环里保留 drawn 态
  while (phase.value === 'playing' && mySession === session) {
    const seat = current
    turn.value = seat
    const ai = !!seatList.value.find((s) => s.seat === seat)?.ai
    if (!skipDraw) {
      const id = drawOne()
      if (id == null) return endDraw(mySession)
      hands.value[seat] = [...hands.value[seat], id]
      drawnTileId.value = id
      canSelfWin.value = !ai && seat === mySeat.value && canSeatWin(seat)
      selfGangKinds.value = !ai && seat === mySeat.value ? seatGangOptions(seat) : []
      publishAll()
      if (ai && canSeatWin(seat)) return finishHand(seat, true, mySession)
    } else if (justGangDrawn) {
      // 杠后岭上牌已在 apply 时摸好:保留 drawn 与自摸/杠状态,不重复摸
      justGangDrawn = false
    } else {
      drawnTileId.value = null
      canSelfWin.value = false
      selfGangKinds.value = []
      publishAll()
    }
    skipDraw = false
    let action
    if (ai) {
      busyAi.value = true
      await sleep(520)
      if (mySession !== session || phase.value !== 'playing') {
        busyAi.value = false
        return
      }
      busyAi.value = false
      // AI 摸牌后有杠先杠(暗杠/补杠),否则选一张弃
      const gangOpts = seatGangOptions(seat)
      action = gangOpts.length
        ? { type: 'gang', kind: gangOpts[0].kind, gangType: gangOpts[0].type }
        : { type: 'discard', kind: mahjongAiChooseDiscard(mahjongCounts(hands.value[seat])) }
    } else {
      // 房间局 35s 不出牌由 AI 代打,避免一个人挂机把整桌拖死;单机不限时
      action = await waitSeat(seat, play.active ? 35000 : 0)
      if (mySession !== session) return
      if (action.type === 'timeout' || action.type === 'abort') {
        action = { type: 'discard', kind: mahjongAiChooseDiscard(mahjongCounts(hands.value[seat])) }
      }
    }
    if (action.type === 'win') return finishHand(seat, true, mySession)
    if (action.type === 'gang') {
      applySeatGang(seat, action)
      publishAll()
      justGangDrawn = true
      skipDraw = true
      continue
    }
    let kind = action.kind != null ? action.kind : mahjongKind(action.tileId)
    if (kind == null || !hands.value[seat].some((tile) => mahjongKind(tile) === kind)) {
      // 非法/过期操作:兜底让 AI 选一张,避免死循环
      kind = mahjongAiChooseDiscard(mahjongCounts(hands.value[seat]))
      if (kind < 0) return
    }
    discardTile(seat, kind)
    publishAll()
    const claim = await resolveClaims(seat, kind, mySession)
    if (mySession !== session || phase.value !== 'playing') return
    if (claim && claim.win != null) return finishHand(claim.win, false, mySession, kind, seat)
    if (claim && claim.gang != null) {
      applyMingGang(claim.gang, seat, kind)
      publishAll()
      current = claim.gang
      justGangDrawn = true
      skipDraw = true
      continue
    }
    if (claim && claim.pong != null) {
      applyPong(claim.pong, seat, kind)
      publishAll()
      current = claim.pong
      skipDraw = true
      continue
    }
    if (claim && claim.chi != null) {
      applyChi(claim.chi.seat, seat, kind, claim.chi.pair)
      publishAll()
      current = claim.chi.seat
      skipDraw = true
      continue
    }
    current = (seat + 1) % 4
  }
}

function waitSeat(seat, timeoutMs) {
  return new Promise((resolve) => {
    let timer = null
    const done = (action) => {
      if (timer) {
        clearTimeout(timer)
        timer = null
      }
      if (turnWaiters.get(seat) === done) turnWaiters.delete(seat)
      resolve(action)
    }
    if (timeoutMs > 0) {
      timer = setTimeout(() => {
        if (turnWaiters.get(seat) === done) {
          turnWaiters.delete(seat)
          resolve({ type: 'timeout' })
        }
      }, timeoutMs)
    }
    turnWaiters.set(seat, done)
  })
}

function resolveSeatAction(seat, action) {
  const done = turnWaiters.get(seat)
  if (!done) return false
  turnWaiters.delete(seat)
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
  handCounts.value = hands.value.map((h) => h.length)
}

function applyPong(seat, fromSeat, kind) {
  const ids = hands.value[seat].filter((id) => mahjongKind(id) === kind).slice(0, 2)
  if (ids.length < 2) return
  hands.value[seat] = hands.value[seat].filter((id) => !ids.includes(id))
  melds.value[seat] = [...melds.value[seat], { kind, from: fromSeat, type: 'pong' }]
  // 被碰走的牌从弃牌河移除(算进碰出的面子)
  rivers.value[fromSeat] = rivers.value[fromSeat].slice(0, -1)
  lastDiscard.value = null
  drawnTileId.value = null
  canSelfWin.value = false
  handCounts.value = hands.value.map((h) => h.length)
  turn.value = seat
}

/** 吃:暗手两张 + 弃牌组成顺子(仅上家),不摸牌直接出牌 */
function applyChi(seat, fromSeat, kind, pair) {
  const ids = hands.value[seat].filter((id) => pair.includes(mahjongKind(id))).slice(0, 2)
  if (ids.length < 2) return
  hands.value[seat] = hands.value[seat].filter((id) => !ids.includes(id))
  melds.value[seat] = [...melds.value[seat], { kind, from: fromSeat, type: 'chi', pair: [...pair] }]
  rivers.value[fromSeat] = rivers.value[fromSeat].slice(0, -1)
  lastDiscard.value = null
  drawnTileId.value = null
  canSelfWin.value = false
  handCounts.value = hands.value.map((h) => h.length)
  turn.value = seat
}

/** 明杠(别家点杠):暗手三张 + 弃牌入副露,摸岭上牌补位 */
function applyMingGang(seat, fromSeat, kind) {
  const ids = hands.value[seat].filter((id) => mahjongKind(id) === kind).slice(0, 3)
  if (ids.length < 3) return
  hands.value[seat] = hands.value[seat].filter((id) => !ids.includes(id))
  melds.value[seat] = [...melds.value[seat], { kind, from: fromSeat, type: 'gang' }]
  rivers.value[fromSeat] = rivers.value[fromSeat].slice(0, -1)
  lastDiscard.value = null
  drawnTileId.value = null
  canSelfWin.value = false
  drawRinshan(seat)
}

/** 自己回合内的杠(暗杠:四张全亮;补杠:碰出后再摸到一张),摸岭上牌补位 */
function applySeatGang(seat, { kind, type }) {
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

/** 岭上牌:杠后从牌墙尾摸一张补位,并重算自摸与可杠 */
function drawRinshan(seat) {
  const id = drawOne()
  if (id != null) {
    hands.value[seat] = [...hands.value[seat], id]
    drawnTileId.value = id
  }
  const ai = !!seatList.value.find((s) => s.seat === seat)?.ai
  canSelfWin.value = !ai && seat === mySeat.value && canSeatWin(seat)
  selfGangKinds.value = !ai && seat === mySeat.value && id != null ? seatGangOptions(seat) : []
  handCounts.value = hands.value.map((h) => h.length)
}

/** 碰/杠/吃/胡询问:胡 > 杠 > 碰 > 吃;本机玩家用按钮,远端玩家发定向信令;到点没人应即继续 */
function resolveClaims(fromSeat, kind, mySession) {
  return new Promise((resolve) => {
    const order = [1, 2, 3].map((i) => (fromSeat + i) % 4)
    const winSeats = order.filter((seat) => canSeatClaimWin(seat, kind))
    const gangSeats = order.filter((seat) => !winSeats.includes(seat) && canSeatMingGang(seat, kind))
    const pongSeats = order.filter((seat) => !winSeats.includes(seat) && !gangSeats.includes(seat) && canSeatPong(seat, kind))
    // 吃只有上家((fromSeat+3)%4)能吃
    const chiSeat = order[2]
    const chiOptions = chiSeat != null && !winSeats.includes(chiSeat) && !gangSeats.includes(chiSeat) ? canSeatChi(chiSeat, kind) : []
    const isAiSeat = (seat) => !!seatList.value.find((s) => s.seat === seat)?.ai
    const humanWin = winSeats.filter((seat) => !isAiSeat(seat))
    const aiWin = winSeats.find(isAiSeat)
    const humanGang = gangSeats.filter((seat) => !isAiSeat(seat))
    const aiGang = gangSeats.find(isAiSeat)
    const humanPong = pongSeats.filter((seat) => !isAiSeat(seat))
    const aiPong = pongSeats.find((seat) => isAiSeat(seat) && aiShouldPong(seat, kind))
    const aiChiPair = !isAiSeat(chiSeat) || !chiOptions.length ? null : aiChooseChi(chiSeat, kind)

    const ask = (seats, claimKind, timeoutMs, options = null) => {
      if (!seats.length) return Promise.resolve(null)
      return new Promise((done) => {
        let settled = false
        let timer = null
        const pending = new Set(seats)
        const cleanup = () => {
          if (timer) clearTimeout(timer)
          for (const seat of seats) {
            claimWaiters.delete(seat)
            if (seat === mySeat.value) {
              localClaim.value = null
              claimChiOptions.value = []
              continue
            }
            const st = seatList.value.find((s) => s.seat === seat)
            if (st && !st.ai) play.send('mj:claim', { to: st.userId, seat, kind: null })
          }
        }
        const finish = (seat, extra = null) => {
          if (settled) return
          settled = true
          cleanup()
          done({ seat, extra })
        }
        // 所有被问的人都选了「过」就不用干等到超时
        const declined = (seat) => {
          if (!pending.delete(seat)) return
          if (!pending.size) finish(null)
        }
        timer = setTimeout(() => finish(null), timeoutMs)
        for (const seat of seats) {
          if (seat === mySeat.value) {
            localClaim.value = claimKind
            claimChiOptions.value = options || []
            claimWaiters.set(seat, (ans, extra) => {
              if (ans === 'claim') finish(seat, extra)
              else {
                claimWaiters.delete(seat)
                localClaim.value = null
                claimChiOptions.value = []
                declined(seat)
              }
            })
          } else {
            const st = seatList.value.find((s) => s.seat === seat)
            if (!st || st.ai) {
              pending.delete(seat)
              continue
            }
            claimWaiters.set(seat, (ans, extra) => {
              if (ans === 'claim') finish(seat, extra)
              else {
                claimWaiters.delete(seat)
                declined(seat)
              }
            })
            play.send('mj:claim', { to: st.userId, seat, kind: claimKind, options })
          }
        }
        if (!pending.size) finish(null)
      })
    }

    ;(async () => {
      if (mySession !== session) return resolve(null)
      if (humanWin.length) {
        const hit = await ask(humanWin, 'win', 8000)
        if (mySession !== session) return resolve(null)
        if (hit && hit.seat != null) return resolve({ win: hit.seat })
      }
      if (aiWin != null) return resolve({ win: aiWin })
      if (humanGang.length) {
        const hit = await ask(humanGang, 'gang', 6000)
        if (mySession !== session) return resolve(null)
        if (hit && hit.seat != null) return resolve({ gang: hit.seat })
      }
      if (aiGang != null) return resolve({ gang: aiGang })
      if (humanPong.length) {
        const hit = await ask(humanPong, 'pong', 6000)
        if (mySession !== session) return resolve(null)
        if (hit && hit.seat != null) return resolve({ pong: hit.seat })
      }
      if (aiPong != null) return resolve({ pong: aiPong })
      if (chiOptions.length && aiChiPair != null && isAiSeat(chiSeat)) return resolve({ chi: { seat: chiSeat, pair: aiChiPair } })
      if (chiOptions.length && !isAiSeat(chiSeat)) {
        const hit = await ask([chiSeat], 'chi', 6000, chiOptions)
        if (mySession !== session) return resolve(null)
        if (hit && hit.seat != null && hit.extra) return resolve({ chi: { seat: chiSeat, pair: hit.extra } })
      }
      resolve(null)
    })()
  })
}

function resolveClaim(seat, answer, extra = null) {
  const done = claimWaiters.get(seat)
  if (!done) return false
  claimWaiters.delete(seat)
  done(answer, extra)
  return true
}

/** 房主收到的远端操作(只接受该座位本人发来的操作) */
function handleGuestAction(data) {
  if (!play.isHost) return
  const seat = Number(data.seat)
  const st = seatList.value.find((s) => s.seat === seat)
  if (!st || st.ai || String(st.userId) !== String(data.from)) return
  const action = data.action || {}
  if (claimWaiters.has(seat)) {
    resolveClaim(seat, action.type === 'pass' ? 'pass' : 'claim', action.chi || null)
    return
  }
  resolveSeatAction(seat, action)
}

function sendToHost(action) {
  if (!play.active || !play.room?.hostId) return false
  return play.send('mj:act', { to: play.room.hostId, seat: mySeat.value, action })
}

// ---------- 本机交互 ----------

function onTileClick(tile) {
  if (!canDiscardNow.value) return
  const action = { type: 'discard', tileId: tile.id }
  if (isGuest.value) sendToHost(action)
  else resolveSeatAction(mySeat.value, action)
}

function declareSelfWin() {
  if (!canSelfWin.value) return
  if (isGuest.value) sendToHost({ type: 'win' })
  else resolveSeatAction(mySeat.value, { type: 'win' })
}

/** 本机回合内杠(暗杠/补杠):访客发意图,房主/单机直接进主循环 */
function declareSelfGang(opt) {
  if (!myTurn.value || !opt) return
  if (isGuest.value) sendToHost({ type: 'gang', kind: opt.kind, gangType: opt.type })
  else resolveSeatAction(mySeat.value, { type: 'gang', kind: opt.kind, gangType: opt.type })
}

/** 应答碰/杠/吃/胡询问:take=true 吃时 extra 为选中的两张组合 */
function answerClaim(take, extra = null) {
  if (!claimPrompt.value) return
  const kind = claimPrompt.value
  if (isGuest.value) {
    guestClaim.value = null
    guestChiOptions.value = []
    sendToHost({ type: take ? kind : 'pass', chi: extra })
    return
  }
  resolveClaim(mySeat.value, take ? 'claim' : 'pass', take && kind === 'chi' ? extra : null)
}

// ---------- 房间同步 ----------

function publicSnapshot() {
  return {
    seats: seatList.value,
    phase: phase.value,
    turn: turn.value,
    rules: rules.value,
    wallCount: wall.value.length,
    handCounts: hands.value.map((h) => h.length),
    rivers: rivers.value,
    melds: melds.value,
    lastDiscard: lastDiscard.value,
    result: result.value,
  }
}

/** 房主广播公共盘面 + 按人下发各自手牌(其余人只看张数) */
function publishAll() {
  if (!play.active || !play.isHost) return
  wallCount.value = wall.value.length
  handCounts.value = hands.value.map((h) => h.length)
  play.send('mj:public', { state: publicSnapshot() })
  for (const s of seatList.value) {
    if (s.ai || !s.userId || String(s.userId) === String(play.myId)) continue
    const mine = s.seat === turn.value && phase.value === 'playing'
    play.send('mj:hand', {
      to: s.userId,
      seat: s.seat,
      tiles: hands.value[s.seat],
      drawnId: mine ? drawnTileId.value : null,
      canWin: mine && canSeatWin(s.seat),
      canGangKinds: mine ? seatGangOptions(s.seat) : [],
      myTurn: mine,
    })
  }
}

function applyPublic(state) {
  if (!state) return
  if (Array.isArray(state.seats) && state.seats.length === 4) seatList.value = state.seats
  phase.value = state.phase === 'ended' ? 'ended' : state.phase === 'playing' ? 'playing' : phase.value
  turn.value = Number(state.turn) || 0
  if (state.rules) rules.value = normalizeMahjongRules(state.rules)
  wallCount.value = Math.max(0, Number(state.wallCount) || 0)
  if (Array.isArray(state.handCounts) && state.handCounts.length === 4) {
    handCounts.value = state.handCounts.map((n) => Math.max(0, Number(n) || 0))
  }
  if (Array.isArray(state.rivers) && state.rivers.length === 4) rivers.value = state.rivers
  if (Array.isArray(state.melds) && state.melds.length === 4) melds.value = state.melds
  lastDiscard.value = state.lastDiscard || null
  result.value = state.result || null
  if (phase.value === 'ended') {
    canSelfWin.value = false
    guestClaim.value = null
    autoReport()
  }
}

function onRoomSignal(data) {
  if (!data) return
  // 房主权威:公共盘面/手牌/碰胡询问只认房主发来的,别人伪造的同名信令直接丢弃
  const fromHost = String(data.from) === String(play.room?.hostId)
  if (data.event === 'mj:public' && isGuest.value) {
    if (!fromHost) return
    applyPublic(data.state)
    return
  }
  if (data.event === 'mj:hand' && isGuest.value) {
    if (!fromHost) return
    if (Number(data.seat) !== mySeat.value) return
    guestHand.value = Array.isArray(data.tiles) ? data.tiles : []
    drawnTileId.value = data.drawnId == null ? null : Number(data.drawnId)
    canSelfWin.value = !!data.canWin
    guestGangKinds.value = Array.isArray(data.canGangKinds) ? data.canGangKinds : []
    return
  }
  if (data.event === 'mj:claim' && isGuest.value) {
    if (!fromHost) return
    if (Number(data.seat) !== mySeat.value) return
    guestClaim.value = data.kind || null
    guestChiOptions.value = Array.isArray(data.options) ? data.options : []
    return
  }
  if (data.event === 'mj:act' && play.isHost) {
    handleGuestAction(data)
    return
  }
  if (data.event === 'sync' && play.isHost) publishAll()
}

function onRoomChange(next) {
  if (!next || !play.active) return
  if ((next.status === 'FINISHED' || next.status === 'CANCELLED') && phase.value !== 'ended') {
    // 房间先结束了:停掉旧循环,盘面留在当前状态,按钮回大厅
    session += 1
    abortWaiters()
    return
  }
  // 牌局中途有人退出房间:把他的人位交给 AI,别让整桌每回合干等超时
  if (!play.isHost || phase.value !== 'playing') return
  const players = (next.members || []).filter((m) => !isSpectatorRole(m.role))
  let changed = false
  for (const seat of seatList.value) {
    if (seat.ai || !seat.userId) continue
    if (players.some((m) => String(m.userId) === String(seat.userId))) continue
    seat.ai = true
    changed = true
    if (claimWaiters.has(seat.seat)) resolveClaim(seat.seat, 'pass')
    if (turnWaiters.has(seat.seat)) resolveSeatAction(seat.seat, { type: 'timeout' })
  }
  if (changed) publishAll()
}

/** 房间状态:开赛后房主先选规则再开牌,访客进入等待并主动要一次全量同步 */
watch(
  () => [play.active, play.status, play.isHost],
  () => {
    if (!play.active) return
    if (play.status !== 'PLAYING' || phase.value !== 'idle') return
    if (play.isHost) {
      if (!rulesPickerOpen.value) rulesPickerOpen.value = true
      return
    }
    seatList.value = buildRoomSeats()
    phase.value = 'playing'
    play.requestSync()
  },
  { immediate: true },
)

/** WS 断线重连后补一次全量同步:否则访客的手牌/盘面停在断线前,直到下一回合 */
watch(
  () => chatStore.socketStatus,
  (status) => {
    if (status !== 'open') return
    if (!play.active || play.isHost || phase.value !== 'playing') return
    play.requestSync()
  },
)

// ---------- 结算 ----------

function endDraw(mySession) {
  if (mySession !== session) return
  result.value = { draw: true, winner: -1, winnerName: '', fans: [], fanKeys: [], score: 0 }
  phase.value = 'ended'
  publishAll()
  autoReport()
}

async function finishHand(winnerSeat, selfDrawn, mySession, claimKind = null, discarderSeat = null) {
  if (mySession !== session || phase.value === 'ended') return
  const counts = mahjongCounts(hands.value[winnerSeat])
  if (!selfDrawn && claimKind != null) counts[claimKind] += 1
  const handTiles = [...hands.value[winnerSeat].map(mahjongKind)]
  if (!selfDrawn && claimKind != null) handTiles.push(claimKind)
  const { fans, score } = mahjongHandScore(counts, { selfDrawn, melds: melds.value[winnerSeat] })
  const winner = seatList.value.find((s) => s.seat === winnerSeat)
  const iWon = winner && !winner.ai && String(winner.userId) === String(play.myId)
  if (iWon) playerWins.value += 1
  else if (winner?.ai) aiWins.value += 1
  result.value = {
    winner: winnerSeat,
    winnerName: seatName(winnerSeat),
    winnerAi: !!winner?.ai,
    selfDrawn,
    discardedBy: discarderSeat != null ? seatName(discarderSeat) : '',
    melds: (melds.value[winnerSeat] || []).map(meldView),
    fans,
    fanKeys: fans.map((f) => f.key),
    score,
    handTiles,
    draw: false,
  }
  phase.value = 'ended'
  publishAll()
  if (!play.active) {
    if (iWon) await grantReward()
  } else {
    autoReport()
  }
}

/** 房间局:牌局结束各自自动交卷(赢家报番分,其余报 0),房主按高分结算 */
function autoReport() {
  if (!play.active || play.reported || !result.value) return
  play.report(myScore.value)
}

const submitRoomScore = () => play.report(myScore.value)

const onResultAction = () => {
  if (play.active && play.room?.id) router.push(`/games/room/${play.room.id}`)
  else startGame()
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

function abortWaiters() {
  for (const [seat, done] of turnWaiters) {
    turnWaiters.delete(seat)
    done({ type: 'abort' })
  }
  for (const [seat, done] of claimWaiters) {
    claimWaiters.delete(seat)
    done('pass')
  }
  turnWaiters.clear()
  claimWaiters.clear()
  localClaim.value = null
  guestClaim.value = null
  busyAi.value = false
}

onMounted(() => {
  sunLight?.suspendEffects()
})

onBeforeUnmount(() => {
  session += 1
  abortWaiters()
  sunLight?.restoreEffects()
})
</script>

<style scoped>
.mahjong-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.mahjong-card:fullscreen {
  padding: clamp(8px, 2vw, 20px);
  overflow: auto;
  background: var(--color-bg);
}
/* 全屏没有顶栏/页脚,扣全屏态卡内边距 + 卡内全屏动作行 + 组间距,横幅实测高度由变量如实上报 */
.mahjong-card:fullscreen .mj-table {
  width: min(100%, calc((100dvh - 88px - var(--room-bar-h, 0px)) * 1.6));
}

/* 封面 */
.cover {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: clamp(14px, 6vh, 48px) 0;
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
.table-waiting {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-secondary);
  font-size: 14px;
  padding: 60px 0;
}

/* 牌面:开源 SVG 贴进白底牌身;白板留白,资源缺失时退回「数字 + 花色」文本。
   立体感 = 底边厚度侧影(0 3px 0 实体边)+ 落影,顶面保留高光内阴影 */
.tile {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 60px;
  border-radius: 6px;
  background: linear-gradient(180deg, #fdfcf7 0%, #f1eddf 88%, #ddd6c2 100%);
  border: 1px solid rgba(0, 0, 0, 0.18);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.85),
    0 3px 0 #c9c1ab,
    0 4px 5px rgba(0, 0, 0, 0.22);
  color: #1b2b33;
  cursor: pointer;
  transition:
    transform 0.12s ease,
    box-shadow 0.12s ease;
  flex: none;
}
.tile .tile-img {
  display: block;
  width: 100%;
  height: 100%;
  padding: 7%;
  box-sizing: border-box;
  object-fit: contain;
  pointer-events: none;
  user-select: none;
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

/* 小牌(河里/副露) */
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

/* 牌背:深色底 + 中央回形纹(纯 CSS,不引素材);叠放省空间 */
.tile.back {
  background:
    radial-gradient(circle at 50% 50%, rgba(255, 255, 255, 0.16) 0 8%, transparent 9%),
    linear-gradient(160deg, #2e6e5e 0%, #245a4e 60%, #1d4a40 100%);
  border-color: rgba(0, 0, 0, 0.35);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.22),
    inset 0 0 0 3px rgba(255, 255, 255, 0.1),
    0 2px 0 #143530,
    0 3px 4px rgba(0, 0, 0, 0.3);
  cursor: default;
}

/* 牌桌舞台:桌面按恒定宽高比居中,透视做在桌面上 */
.mj-stage {
  flex: 1;
  min-height: 0;
  display: grid;
  place-items: center;
}
/* ===== 立体牌桌:对家上 / 上家左 / 下家右 / 我在下,中央弃牌池,副露贴各家名牌 ===== */
.mj-table {
  container: mahjong / inline-size;
  position: relative;
  aspect-ratio: 16 / 10;
  /* 恒定宽高比:宽取「撑满卡片」与「视口高扣除顶栏页脚后按比例折算」较小者,
     窗口怎么变桌面形状都不变;216px ≈ 顶栏 + 页脚 + 页面/卡片内边距 + 组间距;
     「一起玩」横幅(含换行增高)由 --room-bar-h 实测变量扣减,保一屏不滚动 */
  width: min(100%, calc((100dvh - 216px - var(--room-bar-h, 0px)) * 1.6));
  /* 半俯视:顶边向后倒,像坐在桌边看 */
  transform: perspective(1700px) rotateX(12deg);
  display: grid;
  grid-template-areas:
    'top top top'
    'left pool right'
    'bottom bottom bottom';
  grid-template-columns: minmax(0, 1fr) minmax(0, 3.2fr) minmax(0, 1fr);
  grid-template-rows: auto minmax(0, 1fr) auto;
  gap: 4px 8px;
  padding: clamp(10px, 2.1vw, 20px);
  border-radius: 22px;
  background:
    radial-gradient(ellipse at 50% 32%, rgba(255, 255, 255, 0.16) 0%, transparent 58%),
    linear-gradient(158deg, var(--mj-felt-a) 0%, var(--mj-felt-b) 100%);
  box-shadow:
    inset 0 0 0 2px rgba(255, 246, 224, 0.14),
    inset 0 0 0 6px #8a5a30,
    inset 0 0 0 9px #5a3a1f,
    inset 0 0 46px rgba(0, 0, 0, 0.3),
    0 12px 26px rgba(0, 0, 0, 0.22);
  color: #f4f4e9;
  --mj-felt-a: #3f7d5c;
  --mj-felt-b: #28553f;
  --mj-river-w: clamp(15px, calc((100cqi - 176px) / 24), 30px);
  --mj-river-h: calc(var(--mj-river-w) * 1.38);
}
html.dark .mj-table {
  --mj-felt-a: #294f3f;
  --mj-felt-b: #16302a;
}
/* 桌沿:上缘高光 + 下缘阴影,让毯面看起来有厚度 */
.mj-table::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  box-shadow:
    inset 0 2px 1px rgba(255, 255, 255, 0.2),
    inset 0 -4px 8px rgba(0, 0, 0, 0.32);
  pointer-events: none;
}

/* 三面玩家:名牌 + 手牌牌背(叠放省空间) */
.mj-edge {
  min-width: 0;
  border-radius: 12px;
  padding: 4px 8px;
  opacity: 0.88;
  transition:
    opacity 0.16s ease,
    box-shadow 0.16s ease;
}
.mj-edge.active {
  opacity: 1;
  background: rgba(var(--color-brand-rgb), 0.1);
  box-shadow: inset 0 0 0 2px rgba(var(--color-brand-rgb), 0.8);
}
.mj-top {
  grid-area: top;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}
.mj-left {
  grid-area: left;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  justify-self: start;
}
.mj-right {
  grid-area: right;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  justify-self: end;
}
.mj-bottom {
  grid-area: bottom;
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.mj-seat-name {
  display: block;
  max-width: 9em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.92);
}
/* 庄家角标:金色呼应最新弃牌的高亮描边 */
.dealer-badge {
  display: inline-block;
  margin-left: 6px;
  padding: 0 7px;
  border-radius: 999px;
  background: #efca83;
  color: #463414;
  font-size: 11px;
  font-weight: 800;
  line-height: 1.6;
  vertical-align: 1px;
}
/* 对家:横排牌背平铺(不叠,像摆在桌上) */
.mj-backs {
  display: inline-flex;
  gap: 2px;
  min-width: 0;
  max-width: 340px;
  overflow: hidden;
}
.mj-backs .tile.back.sm {
  width: 21px;
  height: 29px;
  flex: none;
  border-radius: 4px;
}
/* 上下家:竖排牌背平铺(横躺的牌一张挨一张) */
.mj-backs-col {
  display: inline-flex;
  flex-direction: column;
  gap: 1px;
  max-height: 320px;
  overflow: hidden;
}
.mj-backs-col .tile.back.sm {
  width: 26px;
  height: 18px;
  flex: none;
  border-radius: 4px;
}

/* 中央弃牌池:四方牌河 + 中央信息 */
.mj-pool {
  grid-area: pool;
  justify-self: center;
  align-self: center;
  width: 100%;
  max-width: 560px;
  display: grid;
  grid-template-areas:
    'rt rt rt'
    'rl hub rr'
    'rb rb rb';
  grid-template-columns: auto minmax(88px, 1fr) auto;
  grid-template-rows: auto minmax(0, 1fr) auto;
  gap: 4px;
  padding: 6px;
  border-radius: 16px;
  background: rgba(0, 0, 0, 0.1);
  box-shadow: inset 0 0 24px rgba(0, 0, 0, 0.2);
}
.mj-river {
  display: grid;
  align-content: start;
  justify-content: start;
  gap: 2px 1px;
  overflow: hidden;
  scrollbar-width: none;
}
.mj-river::-webkit-scrollbar {
  display: none;
}
.river-top,
.river-bottom {
  grid-template-columns: repeat(6, var(--mj-river-w));
  grid-auto-rows: var(--mj-river-h);
  max-height: calc(3 * var(--mj-river-h) + 6px);
  justify-self: center;
}
.river-top {
  grid-area: rt;
}
.river-bottom {
  grid-area: rb;
}
.river-left,
.river-right {
  grid-template-rows: repeat(6, var(--mj-river-h));
  grid-auto-flow: column;
  grid-auto-columns: var(--mj-river-w);
  max-width: calc(3 * var(--mj-river-w) + var(--mj-river-h) + 6px);
  align-self: center;
  /* 旋转后的牌横向占位比格宽,留出内边距避免被裁 */
  padding-inline: calc((var(--mj-river-h) - var(--mj-river-w)) / 2 + 1px);
}
.river-left {
  grid-area: rl;
}
.river-right {
  grid-area: rr;
}
/* 牌河小牌:留出底边厚度,读数清晰又像立在桌上 */
.mj-river .tile.mini {
  width: 100%;
  height: 100%;
  border-radius: 3px;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.9),
    0 2px 0 #c9c1ab,
    0 3px 3px rgba(0, 0, 0, 0.2);
}
.mj-river .tile.latest {
  outline: 2px solid #efca83;
  outline-offset: -2px;
  background: #fff4d7;
}
.mj-hub {
  grid-area: hub;
  justify-self: center;
  align-self: center;
  max-width: 190px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 2px 6px;
  text-align: center;
}
.wall-count {
  font-size: 12px;
  color: #d7e7dd;
  font-variant-numeric: tabular-nums;
}
.rules-chip {
  padding: 2px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.16);
  color: #f1f7f2;
  font-size: 11px;
  white-space: nowrap;
}
html.dark .rules-chip {
  background: rgba(0, 0, 0, 0.32);
}
.turn-label {
  font-size: 13px;
  font-weight: 700;
  line-height: 1.35;
  color: #fff;
  overflow-wrap: anywhere;
}

/* 副露区:贴在各家名牌/手牌旁,不挤占中央牌池;牌河里只留散牌弃张 */
.mj-meld-corner {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 3px;
  max-width: 100%;
  margin-top: 4px;
  padding: 3px 4px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.14);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.1);
}
.mj-meld-corner .meld-group,
.meld-group {
  display: inline-flex;
  gap: 1px;
  padding: 1px;
  border-radius: 5px;
  background: rgba(255, 255, 255, 0.2);
}
.mj-meld-corner .tile.mini {
  width: 20px;
  height: 28px;
  border-radius: 3px;
  box-shadow: 0 1px 0 #a29c8b;
}
/* 桌面几何:他家的牌一律按该家视角摆放——牌面字头朝牌主,副露贴其右手侧 */
.mj-top .tile.mini {
  transform: rotate(180deg);
}
.mj-left .tile.mini {
  transform: rotate(-90deg);
}
.mj-right .tile.mini {
  transform: rotate(90deg);
}
/* 左右家的副露:面子与手牌平行排成纵向条,组间横向排开 */
.mj-left .meld-group,
.mj-right .meld-group {
  flex-direction: column;
}
.mj-left .mj-meld-corner,
.mj-right .mj-meld-corner {
  padding-inline: 6px;
}
.river-top .tile {
  transform: rotate(180deg);
}
.river-left .tile {
  transform: rotate(-90deg);
}
.river-right .tile {
  transform: rotate(90deg);
}
@container mahjong (max-width: 620px) {
  .mj-top {
    flex-wrap: wrap;
  }
  .mj-meld-corner {
    padding: 2px;
  }
  .mj-meld-corner .tile.mini {
    width: 13px;
    height: 18px;
  }
}
.hand-row {
  display: flex;
  flex-wrap: nowrap;
  gap: 4px;
  justify-content: center;
  align-items: flex-end;
  max-width: 100%;
}
/* 手牌永远一排:宽度随容器自动缩放,不换行不出滚动条 */
.tile.hand {
  width: min(52px, calc((100cqi - 58px) / 14));
  height: auto;
  aspect-ratio: 11 / 15;
  padding: 0;
}
.tile.hand.drawn {
  transform: translateY(-8px);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.85),
    0 3px 0 #c9c1ab,
    0 8px 12px rgba(var(--color-brand-rgb), 0.5);
}
.tile.hand:not(:disabled):hover {
  transform: translateY(-5px);
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
  color: #d4e3d9;
  text-align: center;
}
/* 碰/杠/吃/胡询问浮层:悬在手牌上方,深色托底保证压在牌河上也读得清 */
.claim-float {
  position: absolute;
  bottom: calc(100% + 10px);
  left: 50%;
  transform: translateX(-50%);
  z-index: 6;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  align-items: center;
  gap: 10px;
  max-width: min(92cqi, 640px);
  padding: 10px 14px;
  border-radius: 14px;
  background: rgba(17, 30, 24, 0.78);
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.14),
    0 8px 20px rgba(0, 0, 0, 0.35);
}
/* 可胡/可碰的牌就在眼前:主按钮轻微脉冲提示紧迫感(尊重减弱动态偏好) */
.claim-float .el-button--primary {
  animation: claim-pulse 1.6s ease-in-out infinite;
}
@keyframes claim-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(var(--color-brand-rgb), 0.45);
  }
  55% {
    box-shadow: 0 0 0 8px rgba(var(--color-brand-rgb), 0);
  }
}
@media (prefers-reduced-motion: reduce) {
  .claim-float .el-button--primary {
    animation: none;
  }
}
/* 吃按钮里内嵌的两张候选小牌 */
.claim-float .tile.mini {
  width: 22px;
  height: 30px;
  margin-left: 4px;
  box-shadow: none;
  border-radius: 4px;
}
@container mahjong (max-width: 620px) {
  .claim-float {
    gap: 8px;
    padding: 8px 10px;
  }
}

/* ===== 开局规则面板(封面内联 + 房间局浮层) ===== */
.rules-panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 14px 18px;
  border: 1px solid var(--color-border);
  border-radius: 14px;
  min-width: 300px;
}
.cover .rules-panel {
  border: 0;
}
.rules-panel-float {
  background: var(--color-bg);
  box-shadow: var(--shadow-hover);
}
.rules-title {
  font-size: 14px;
  font-weight: 700;
}
.rules-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}
.rules-option {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: 9px 14px;
  border: 1.5px solid var(--color-border);
  border-radius: 10px;
  background: transparent;
  color: var(--color-text);
  cursor: pointer;
  text-align: left;
  transition:
    border-color 0.15s,
    background 0.15s;
}
.rules-option.active {
  border-color: var(--color-brand);
  background: rgba(var(--color-brand-rgb), 0.1);
}
.rules-option-name {
  font-size: 13px;
  font-weight: 600;
}
.rules-option.active .rules-option-name {
  color: var(--color-brand);
}
.rules-option-desc {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.rules-hint {
  font-size: 12px;
  color: var(--color-text-secondary);
  text-align: center;
}
.rules-overlay {
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

/* 听牌提示行:小牌列表 + 文案 */
.advice-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: center;
  margin-top: 8px;
  min-height: 40px;
}
.advice-label {
  font-size: 12px;
  color: #d4e3d9;
  margin-right: 4px;
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
.overlay-sub {
  font-size: 13px;
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
/* 浅色结算卡上的副露分组底,压住白色牌身轮廓 */
.overlay-hand .meld-group {
  background: rgba(0, 0, 0, 0.08);
}
.overlay-reward {
  font-size: 13px;
  color: var(--color-accent);
}
.overlay-wait {
  font-size: 12px;
  color: var(--color-text-secondary);
}

@media (max-width: 560px) {
  .rules-panel {
    min-width: 0;
    width: min(100%, 420px);
  }
  .turn-label {
    flex-basis: 100%;
  }
}
</style>
