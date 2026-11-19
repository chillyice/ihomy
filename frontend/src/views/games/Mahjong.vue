<!-- 麻将(推倒胡简化版):单机为玩家 vs 三位 AI,「一起玩」房间为 4 席真人(空位 AI 补)。
     规则内核在 utils/games/mahjong.js(判胡/听牌/碰/计番/AI 选牌都是纯函数);
     本页做发牌/回合驱动/交互与结算展示,单机赢一局走统一 H5 奖励通道(每日限次)。
     房间局:房主权威跑牌局,公共盘面广播、每个人只收到自己的手牌,其余三家只看剩牌数。 -->
<template>
  <GameShell
    ref="shellEl"
    class="mj-shell"
    :class="{ 'mj-room': play.active }"
    :title="$t('games.mahjong.title')"
    body-align="stretch"
    readable
    :new-game="!play.active && phase === 'playing'"
    @back="goBack"
    @new-game="startGame"
  >
    <template #rules>
      <p>{{ $t('games.mahjong.coverHint') }}</p>
    </template>
    <template #extras>
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
        @auto="onAutoToggle"
      />
    </template>

    <div class="mahjong-card" @contextmenu.prevent @dblclick.prevent>
      <!-- 封面(仅单机;房间局由房主选规则后开牌):开局规则选择 + 开始按钮,游戏名与玩法说明在统一卡头 -->
      <div v-if="phase === 'idle' && !play.active" class="cover">
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
      </div>

      <!-- 牌桌 -->
      <template v-else>
        <!-- 房间局:房主开牌前先定规则(访客等待盘面) -->
        <div v-if="rulesPickerOpen && play.isHost" class="rules-setup">
          <div class="rules-panel">
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

        <div v-else-if="waitingHost" class="table-waiting">{{ $t('games.mahjong.waitingHost') }}</div>
        <template v-else>
          <!-- 平面牌桌(窗口不透视):对家在上 / 上家在左 / 下家在右 / 我在下;
               手牌、副露、牌河和操作各有独立位置,整块牌桌再按可用区等比缩放;
               中央 = 四方牌河(弃牌朝向出牌人) + 东南西北风位 + 双骰 + 余牌数 -->
          <div ref="stageEl" class="mj-stage">
            <div ref="tableEl" class="mj-table" :style="{ '--mj-scale': tableScale, '--mj-w': tableW + 'px', '--mj-h': tableH + 'px' }">
              <!-- 对家(上):牌背横排 + 副露横排 -->
              <div v-if="topSeat" class="mj-edge mj-top" :class="{ active: turn === topSeat.seat && phase === 'playing' }">
                <div class="seat-tag">
                  <span class="seat-wind">{{ windName(topSeat.seat) }}</span>
                  <span class="mj-seat-name" :title="seatName(topSeat.seat)">{{ seatName(topSeat.seat) }}</span>
                  <span v-if="topSeat.seat === 0" class="dealer-badge">{{ $t('games.mahjong.dealer') }}</span>
                  <span v-if="topSeat.auto" class="auto-badge">{{ $t('games.mahjong.autoBadge') }}</span>
                </div>
                <div class="side-row">
                  <div v-if="meldGroups(topSeat.seat).length" class="meld-row">
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
              </div>

              <!-- 上家(左):侧向手牌是自上而下的一条,副露横排在手的上方(同一窄栏,不并排) -->
              <div v-if="leftSeat" class="mj-edge mj-left" :class="{ active: turn === leftSeat.seat && phase === 'playing' }">
                <div class="seat-tag">
                  <span class="seat-wind">{{ windName(leftSeat.seat) }}</span>
                  <span class="mj-seat-name" :title="seatName(leftSeat.seat)">{{ seatName(leftSeat.seat) }}</span>
                  <span v-if="leftSeat.seat === 0" class="dealer-badge">{{ $t('games.mahjong.dealer') }}</span>
                  <span v-if="leftSeat.auto" class="auto-badge">{{ $t('games.mahjong.autoBadge') }}</span>
                </div>
                <div class="side-hand">
                  <div v-if="meldGroups(leftSeat.seat).length" class="meld-row meld-block">
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
                  <span class="mj-backs-col">
                    <span v-for="n in seatTileCount(leftSeat.seat)" :key="n" class="tile back side" aria-hidden="true" />
                  </span>
                </div>
              </div>

              <!-- 中央:四方牌河围着中央信息(余牌数只在中央信息里读,桌上不再摆牌墙) -->
              <div class="mj-center">
                <div class="mj-pool">
                  <div
                    v-for="river in poolRivers"
                    :key="river.pos"
                    :ref="(el) => setRiverEl(river.pos, el)"
                    class="mj-river"
                    :class="'river-' + river.pos"
                    role="region"
                    :tabindex="river.kinds.length > (isSideRiver(river.pos) ? 24 : 18) ? 0 : undefined"
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
                  <!-- 中央信息有独立格,不会压住任何一家牌河 -->
                  <div class="mj-hub">
                    <div class="hub-compass" aria-hidden="true">
                      <span class="compass-wind pos-top">{{ windOfRel(2) }}</span>
                      <span class="compass-wind pos-right">{{ windOfRel(1) }}</span>
                      <span class="compass-wind pos-bottom">{{ windOfRel(0) }}</span>
                      <span class="compass-wind pos-left">{{ windOfRel(3) }}</span>
                    </div>
                    <div v-if="dice" class="hub-dice">
                      <span class="die"><i v-for="(p, i) in diePips(dice[0])" :key="'a' + i" class="die-pip" :style="pipStyle(p)" /></span>
                      <span class="die"><i v-for="(p, i) in diePips(dice[1])" :key="'b' + i" class="die-pip" :style="pipStyle(p)" /></span>
                      <span class="die-sum">{{ dice[0] + dice[1] }}</span>
                    </div>
                    <div class="hub-meta">
                      <span class="wall-count">{{ $t('games.mahjong.wallLeft', { n: wallLeft }) }}</span>
                      <span class="rules-chip">{{ $t(`games.mahjong.presets.${rulesLabel}`) }}</span>
                      <span class="turn-label" :title="turnLabel">{{ turnLabel }}</span>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 下家(右):侧向手牌是自上而下的一条,副露横排在手的上方(同一窄栏,不并排) -->
              <div v-if="rightSeat" class="mj-edge mj-right" :class="{ active: turn === rightSeat.seat && phase === 'playing' }">
                <div class="seat-tag">
                  <span class="seat-wind">{{ windName(rightSeat.seat) }}</span>
                  <span class="mj-seat-name" :title="seatName(rightSeat.seat)">{{ seatName(rightSeat.seat) }}</span>
                  <span v-if="rightSeat.seat === 0" class="dealer-badge">{{ $t('games.mahjong.dealer') }}</span>
                  <span v-if="rightSeat.auto" class="auto-badge">{{ $t('games.mahjong.autoBadge') }}</span>
                </div>
                <div class="side-hand">
                  <div v-if="meldGroups(rightSeat.seat).length" class="meld-row meld-block">
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
                    <span v-for="n in seatTileCount(rightSeat.seat)" :key="n" class="tile back side" aria-hidden="true" />
                  </span>
                </div>
              </div>

              <!-- 我方(下):副露与手牌同排;吃碰杠胡操作条浮在手牌上方,不占布局高度 -->
              <div class="mj-edge mj-bottom" :class="{ active: myTurn }">
                <div v-if="!play.isSpectator" class="my-row">
                  <div v-if="meldGroups(mySeat).length" class="meld-row my-melds">
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
                  <div class="hand-row">
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
                    <span v-for="n in handBackFill" :key="'ph' + n" class="tile back hand" aria-hidden="true" />
                  </div>
                </div>
                <div v-if="!play.isSpectator && (claimPrompt || canSelfWin || myGangOptions.length)" class="my-actions" aria-live="polite">
                  <template v-if="claimPrompt">
                    <el-button
                      v-for="(pair, i) in activeChiOptions"
                      :key="i"
                      type="primary"
                      size="large"
                      round
                      @click="answerClaim(true, pair)"
                    >
                      {{ $t('games.mahjong.chi') }}
                      <span class="tile mini" :class="tileClass(pair[0])"><TileFace :kind="pair[0]" /></span>
                      <span class="tile mini" :class="tileClass(pair[1])"><TileFace :kind="pair[1]" /></span>
                    </el-button>
                    <el-button v-if="!activeChiOptions.length" type="primary" size="large" round @click="answerClaim(true)">
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
                    <el-button size="large" round @click="answerClaim(false)">{{ $t('games.mahjong.pass') }}</el-button>
                  </template>
                  <template v-else>
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
                  </template>
                </div>
              </div>
            </div>
          </div>
        </template>

        <!-- 断线浮层:牌局信令全走聊天 WebSocket,断开期间点击发不出去,明示「正在重连」 -->
        <Transition name="game-overlay">
          <div v-if="linkDown" class="overlay link-overlay" role="status" aria-live="polite">
            <div class="overlay-card link-overlay-card">
              <div class="overlay-title">{{ $t('games.mahjong.reconnecting') }}</div>
              <div class="overlay-sub">{{ $t('games.mahjong.reconnectingHint') }}</div>
            </div>
          </div>
        </Transition>

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
  </GameShell>
</template>

<script setup>
import { computed, h, inject, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { gameApi, roomApi } from '@/api'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import GameShell from '@/components/games/GameShell.vue'
import GameRoomBar from '@/components/games/GameRoomBar.vue'
import { useGameRoomPlay } from '@/composables/useGameRoomPlay'
import { bindHorizontalWheel } from '@/utils/horizontalWheel'
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
  mahjongChiSeat,
  mahjongCounts,
  mahjongDiceFirstSeat,
  mahjongHandScore,
  mahjongKind,
  mahjongRollDice,
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

const shellEl = ref(null)
// 盘面自管尺寸(设计画布 + 等比缩放),外壳已把页面锁成一屏高

// 只缩放盘面,顶栏和房间横幅按内容页尺寸排版。offsetWidth/Height 不受 transform 影响。
const stageEl = ref(null)
const tableEl = ref(null)
// 牌桌设计画布:高按下限取、宽跟随游戏区宽高比 —— 整块游戏区铺成一张麻将桌(手机横屏尤其明显),
// 内部全按设计尺寸画,再统一缩放到刚好铺满。桌面大屏封顶 1.2 倍,牌不至于放成巨无霸。
// 下限是内容撑出来的:14 张手牌一行(46px)+ 两侧副露栏 + 两侧牌河 + 中央信息(宽)、四家牌行 + 一条牌河(高)。
const MJ_MIN_W = 1000
const MJ_MIN_H = 500
const MJ_MAX_W = 1500
const MJ_MAX_SCALE = 1.2
const tableW = ref(MJ_MIN_W)
const tableH = ref(MJ_MIN_H)
const tableScale = ref(1)
watch(
  stageEl,
  (stage, _, onCleanup) => {
    if (!stage) return
    const fitTable = () => {
      const { width, height } = stage.getBoundingClientRect()
      if (!width || !height) return
      const aspect = width / height
      const w = Math.min(MJ_MAX_W, Math.max(MJ_MIN_W, MJ_MIN_H * aspect))
      const h = Math.max(MJ_MIN_H, w / aspect)
      tableW.value = Math.round(w)
      tableH.value = Math.round(h)
      tableScale.value = Math.min(width / w, height / h, MJ_MAX_SCALE)
    }
    fitTable()
    const observer = new ResizeObserver(fitTable)
    observer.observe(stage)
    onCleanup(() => observer.disconnect())
  },
  { flush: 'post' },
)

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
const dice = ref(null) // 开局庄家掷的两颗骰子 [d1, d2](和值定首家)
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
const rulesDraftKey = ref('puTong') // 开局草稿:所选规则名(默认普通规则)
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
// seat -> 正在等待的询问(断线重连后房主据此补发,否则询问只发过一次就丢了)
const pendingPrompts = new Map()
let lastPublishKey = '' // 上次广播内容的指纹:同一状态不重复推

const myId = computed(() => play.myId)
const mySeat = computed(() => {
  const idx = seatList.value.findIndex((s) => !s.ai && String(s.userId) === String(myId.value))
  if (idx >= 0) return idx
  // 观战没有席位:不占座、不显示自己的手牌,只看公共盘面
  return play.isSpectator ? -1 : 0
})
const myTurn = computed(() => phase.value === 'playing' && turn.value === mySeat.value)
const myHandTiles = computed(() => (isGuest.value ? guestHand.value : hands.value[mySeat.value] || []))
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

/** 该席位是否由电脑代打:空位 AI,或开了托管的真人席位 */
const isAutoSeat = (seat) => {
  const s = seatList.value.find((x) => x.seat === seat)
  return !!s && (!!s.ai || !!s.auto)
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

// 只给尚未收到的手牌补牌背,已亮出的副露不算进暗手。
const handBackFill = computed(() => Math.max(0, seatTileCount(mySeat.value) - myHandTiles.value.length))

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
    { pos: 'bottom', seat: viewSeat.value },
  ].map(({ pos, seat }) => ({
    pos,
    seat: seat ?? -1,
    kinds: seat != null && seat >= 0 ? rivers.value[seat] || [] : [],
  })),
)

const isSideRiver = (pos) => pos === 'left' || pos === 'right'
const riverEls = new Map()
function setRiverEl(pos, el) {
  const previous = riverEls.get(pos)
  if (previous?.el === el) return
  previous?.unbind?.()
  if (!el) {
    riverEls.delete(pos)
    return
  }
  riverEls.set(pos, { el, unbind: isSideRiver(pos) ? bindHorizontalWheel(el) : null })
  // 中途进入房间时,已有的弃牌也从最新处看起。
  nextTick(() => scrollRiverToEnd(pos))
}

function scrollRiverToEnd(pos) {
  const el = riverEls.get(pos)?.el
  if (!el) return
  if (isSideRiver(pos)) el.scrollLeft = el.scrollWidth
  else el.scrollTop = el.scrollHeight
}

watch(
  () => poolRivers.value.map(({ kinds }) => kinds.length),
  async (lengths, previous) => {
    // 用户正在看旧牌时不拉回末尾;第一次超过显示区时也要跟随新牌。
    const follow = poolRivers.value.filter(({ pos }, i) => {
      if (lengths[i] <= previous[i]) return false
      const el = riverEls.get(pos)?.el
      if (!el) return false
      return isSideRiver(pos) ? el.scrollWidth - el.clientWidth - el.scrollLeft < 4 : el.scrollHeight - el.clientHeight - el.scrollTop < 4
    })
    await nextTick()
    for (const { pos } of follow) scrollRiverToEnd(pos)
  },
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

const tileClass = (kind) => {
  if (kind >= 27) return `honor-${['east', 'south', 'west', 'north', 'zhong', 'fa', 'bai'][kind - 27]}`
  return `suit-${Math.floor(kind / 9)}`
}

// ---------- 风位 / 牌墙 / 骰子(纯展示派生) ----------

/** 座位风位:庄家(席 0)= 东,逆时针依次南西北;风名复用字牌文案 */
const WIND_KEYS = ['east', 'south', 'west', 'north']
const windName = (seat) => t(`games.mahjong.honor.${WIND_KEYS[((seat % 4) + 4) % 4]}`)

/** 罗盘上各方位的风:relPos(0=我/下,1=右,2=对家/上,3=左)→ 该座位的风 */
const windOfRel = (rel) => windName((viewSeat.value + rel) % 4)

/** 骰子点阵(3×3 布局的坐标,r/c ∈ 0..2) */
const DIE_PIPS = {
  1: [[1, 1]],
  2: [
    [0, 0],
    [2, 2],
  ],
  3: [
    [0, 0],
    [1, 1],
    [2, 2],
  ],
  4: [
    [0, 0],
    [0, 2],
    [2, 0],
    [2, 2],
  ],
  5: [
    [0, 0],
    [0, 2],
    [1, 1],
    [2, 0],
    [2, 2],
  ],
  6: [
    [0, 0],
    [0, 2],
    [1, 0],
    [1, 2],
    [2, 0],
    [2, 2],
  ],
}
const diePips = (n) => DIE_PIPS[Math.max(1, Math.min(6, Number(n) || 1))] || []
const pipStyle = ([r, c]) => ({ gridArea: `${r + 1} / ${c + 1}` })

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

/** 电脑应答(吃碰杠胡)前的思考停顿:0.7~1.6s,操作有可感延迟不显机器手速 */
async function aiThink(mySession) {
  await sleep(700 + Math.floor(Math.random() * 900))
  return mySession === session
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

/** 吃:仅上家(上一个出牌的玩家)可吃,规则开关 + 手里能凑出顺子组合 */
function canSeatChi(seat, kind) {
  if (!rules.value.allowChi) return []
  if (seat !== mahjongChiSeat(lastDiscardSeat())) return []
  if ((melds.value[seat]?.length || 0) >= 3) return []
  return mahjongChiOptions(mahjongCounts(hands.value[seat]), kind)
}

/** 弃牌者座次 */
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
  pendingPrompts.clear()
  lastPublishKey = ''
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
  // 庄家(席 0)掷两颗骰子,和值定首家(从庄家数起,和值 % 4)
  dice.value = mahjongRollDice()
  turn.value = mahjongDiceFirstSeat(dice.value[0], dice.value[1])
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
    const ai = isAutoSeat(seat)
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
      // 电脑出牌放慢:保留可感的思考停顿(1.2~2.4s),别快得像开了倍速
      await sleep(1200 + Math.floor(Math.random() * 1200))
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
  const ai = isAutoSeat(seat)
  canSelfWin.value = !ai && seat === mySeat.value && canSeatWin(seat)
  selfGangKinds.value = !ai && seat === mySeat.value && id != null ? seatGangOptions(seat) : []
  handCounts.value = hands.value.map((h) => h.length)
}

/** 碰/杠/吃/胡询问:胡 > 杠 > 碰 > 吃;本机玩家用按钮,远端玩家发定向信令;吃碰杠不限时,没人应即视为都过 */
function resolveClaims(fromSeat, kind, mySession) {
  return new Promise((resolve) => {
    const order = [1, 2, 3].map((i) => (fromSeat + i) % 4)
    const winSeats = order.filter((seat) => canSeatClaimWin(seat, kind))
    const gangSeats = order.filter((seat) => !winSeats.includes(seat) && canSeatMingGang(seat, kind))
    const pongSeats = order.filter((seat) => !winSeats.includes(seat) && !gangSeats.includes(seat) && canSeatPong(seat, kind))
    // 吃只有上家(上一个出牌的玩家,即弃牌者的下家)能吃
    const chiSeat = mahjongChiSeat(fromSeat)
    const chiOptions = chiSeat >= 0 && !winSeats.includes(chiSeat) && !gangSeats.includes(chiSeat) ? canSeatChi(chiSeat, kind) : []
    const isAiSeat = (seat) => isAutoSeat(seat)
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
            pendingPrompts.delete(seat)
            if (seat === mySeat.value) {
              localClaim.value = null
              claimChiOptions.value = []
              continue
            }
            const st = seatList.value.find((s) => s.seat === seat)
            if (st && !st.ai && !st.auto) play.send('mj:claim', { to: st.userId, seat, kind: null })
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
        // timeoutMs>0 才计时(胡 8s 保留;吃碰杠按需求不限时,挂机靠托管/让位/离席转 AI 兜底)
        if (timeoutMs > 0) {
          timer = setTimeout(() => finish(null), timeoutMs)
        }
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
            if (!st || st.ai || st.auto) {
              // 空位 AI / 托管席位:不发询问,由 AI 决断
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
            pendingPrompts.set(seat, { kind: claimKind, options })
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
      if (aiWin != null) {
        await aiThink(mySession)
        if (mySession !== session) return resolve(null)
        return resolve({ win: aiWin })
      }
      if (humanGang.length) {
        const hit = await ask(humanGang, 'gang', 0)
        if (mySession !== session) return resolve(null)
        if (hit && hit.seat != null) return resolve({ gang: hit.seat })
      }
      if (aiGang != null) {
        await aiThink(mySession)
        if (mySession !== session) return resolve(null)
        return resolve({ gang: aiGang })
      }
      if (humanPong.length) {
        const hit = await ask(humanPong, 'pong', 0)
        if (mySession !== session) return resolve(null)
        if (hit && hit.seat != null) return resolve({ pong: hit.seat })
      }
      if (aiPong != null) {
        await aiThink(mySession)
        if (mySession !== session) return resolve(null)
        return resolve({ pong: aiPong })
      }
      if (chiOptions.length && aiChiPair != null && isAiSeat(chiSeat)) {
        await aiThink(mySession)
        if (mySession !== session) return resolve(null)
        return resolve({ chi: { seat: chiSeat, pair: aiChiPair } })
      }
      if (chiOptions.length && !isAiSeat(chiSeat)) {
        const hit = await ask([chiSeat], 'chi', 0, chiOptions)
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

/** 房主收到的远端操作(只接受该座位本人发来的操作;托管中的席位由 AI 代打,忽略其操作) */
function handleGuestAction(data) {
  if (!play.isHost) return
  const seat = Number(data.seat)
  const st = seatList.value.find((s) => s.seat === seat)
  if (!st || st.ai || st.auto || String(st.userId) !== String(data.from)) return
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
  if (blockedByLink()) return
  const action = { type: 'discard', tileId: tile.id }
  if (isGuest.value) sendToHost(action)
  else resolveSeatAction(mySeat.value, action)
}

function declareSelfWin() {
  if (!canSelfWin.value) return
  if (blockedByLink()) return
  if (isGuest.value) sendToHost({ type: 'win' })
  else resolveSeatAction(mySeat.value, { type: 'win' })
}

/** 本机回合内杠(暗杠/补杠):访客发意图,房主/单机直接进主循环 */
function declareSelfGang(opt) {
  if (!myTurn.value || !opt) return
  if (blockedByLink()) return
  if (isGuest.value) sendToHost({ type: 'gang', kind: opt.kind, gangType: opt.type })
  else resolveSeatAction(mySeat.value, { type: 'gang', kind: opt.kind, gangType: opt.type })
}

/** 应答碰/杠/吃/胡询问:take=true 吃时 extra 为选中的两张组合 */
function answerClaim(take, extra = null) {
  if (!claimPrompt.value) return
  if (blockedByLink()) return
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
    dice: dice.value,
    wallCount: wall.value.length,
    handCounts: hands.value.map((h) => h.length),
    rivers: rivers.value,
    melds: melds.value,
    lastDiscard: lastDiscard.value,
    result: result.value,
  }
}

/** 房主广播公共盘面 + 按人下发各自手牌(其余人只看张数) */
function publishAll({ force = false } = {}) {
  if (!play.active || !play.isHost) return
  wallCount.value = wall.value.length
  handCounts.value = hands.value.map((h) => h.length)
  const state = publicSnapshot()
  const outs = []
  for (const s of seatList.value) {
    if (s.ai || !s.userId || String(s.userId) === String(play.myId)) continue
    const mine = s.seat === turn.value && phase.value === 'playing'
    outs.push({
      to: s.userId,
      seat: s.seat,
      tiles: hands.value[s.seat],
      drawnId: mine ? drawnTileId.value : null,
      canWin: mine && canSeatWin(s.seat),
      canGangKinds: mine ? seatGangOptions(s.seat) : [],
      myTurn: mine,
    })
  }
  // 同一动作常触发两次发布(提交后各自刷新):内容完全一致就别重复推,弱网下等于把 KB 翻倍。
  // 断线重连的补发必须真发,走 force。
  const key = JSON.stringify([state, outs])
  if (!force && key === lastPublishKey) return
  lastPublishKey = key
  play.send('mj:public', { state })
  for (const o of outs) play.send('mj:hand', o)
}

/** 重连补发:吃碰杠询问只在发起那一刻推过一次,且吃/碰/杠不限时,访客重连拿不到就双方一起卡住 */
function replayClaims() {
  if (!play.isHost) return
  for (const [seat, prompt] of pendingPrompts) {
    const st = seatList.value.find((s) => s.seat === seat)
    if (!st || st.ai || st.auto || !st.userId) continue
    play.send('mj:claim', { to: st.userId, seat, kind: prompt.kind, options: prompt.options })
  }
}

function applyPublic(state) {
  if (!state) return
  if (Array.isArray(state.seats) && state.seats.length === 4) seatList.value = state.seats
  phase.value = state.phase === 'ended' ? 'ended' : state.phase === 'playing' ? 'playing' : phase.value
  turn.value = Number(state.turn) || 0
  if (state.rules) rules.value = normalizeMahjongRules(state.rules)
  if (Array.isArray(state.dice) && state.dice.length === 2) dice.value = [Number(state.dice[0]) || 1, Number(state.dice[1]) || 1]
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
  // 托管开关:访客请求房主把自己席位交给 AI(或收回)
  if (data.event === 'auto' && play.isHost) {
    handleGuestAuto(data)
    return
  }
  if (data.event === 'sync' && play.isHost) {
    publishAll({ force: true })
    replayClaims()
  }
}

/** 房主处理托管请求:席位加 auto 标记;若正等这席位操作,立即按超时交给 AI 过渡 */
function handleGuestAuto(data) {
  const st = seatList.value.find((s) => String(s.userId) === String(data.from))
  if (!st || st.ai) return
  st.auto = !!data.on
  if (st.auto) {
    if (claimWaiters.has(st.seat)) resolveClaim(st.seat, 'pass')
    if (turnWaiters.has(st.seat)) resolveSeatAction(st.seat, { type: 'timeout' })
  }
  publishAll()
}

/** 本机托管开关:房主直接生效;访客发信令给房主 */
function onAutoToggle(on) {
  if (!play.active) return
  const st = seatList.value.find((s) => !s.ai && String(s.userId) === String(play.myId))
  if (!st) return
  st.auto = !!on
  if (play.isHost) {
    if (st.auto) {
      if (claimWaiters.has(st.seat)) resolveClaim(st.seat, 'pass')
      if (turnWaiters.has(st.seat)) resolveSeatAction(st.seat, { type: 'timeout' })
    }
    publishAll()
  } else {
    play.send('auto', { to: play.room.hostId, on: st.auto })
  }
}

function onRoomChange(next) {
  if (!next || !play.active) return
  if ((next.status === 'FINISHED' || next.status === 'CANCELLED') && phase.value !== 'ended') {
    // 房间先结束了:停掉旧循环,盘面留在当前状态,按钮回大厅
    session += 1
    abortWaiters()
    return
  }
  // 牌局中途席位交接:退出/转观战的人位交给 AI;新参战者(如让位接手)优先接管刚空出的席
  if (!play.isHost || phase.value !== 'playing') return
  const players = (next.members || []).filter((m) => !isSpectatorRole(m.role))
  let changed = false
  const freedSeats = []
  for (const seat of seatList.value) {
    if (seat.ai || !seat.userId) continue
    if (players.some((m) => String(m.userId) === String(seat.userId))) continue
    seat.ai = true
    seat.auto = false
    changed = true
    freedSeats.push(seat)
    if (claimWaiters.has(seat.seat)) resolveClaim(seat.seat, 'pass')
    if (turnWaiters.has(seat.seat)) resolveSeatAction(seat.seat, { type: 'timeout' })
  }
  // 新玩家接管:优先刚空出的席,其次任意空位 AI 席;挂起的 waiter 先让 AI 过渡一手
  const seated = new Set(seatList.value.filter((s) => !s.ai).map((s) => String(s.userId)))
  for (const m of players) {
    if (seated.has(String(m.userId))) continue
    const free = freedSeats.shift() || seatList.value.find((s) => s.ai)
    if (!free) break
    free.ai = false
    free.auto = false
    free.userId = m.userId
    free.name = m.name
    changed = true
    seated.add(String(m.userId))
    if (claimWaiters.has(free.seat)) resolveClaim(free.seat, 'pass')
    if (turnWaiters.has(free.seat)) resolveSeatAction(free.seat, { type: 'timeout' })
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

/** 连接状态明示:断线时棋盘上的点击都会静默失败,先挡一下再重连,别让它看起来像卡死 */
const linkDown = ref(false)
let linkTimer = null
watch(
  () => chatStore.socketStatus,
  (status) => {
    if (status === 'open') {
      if (linkTimer) {
        clearTimeout(linkTimer)
        linkTimer = null
      }
      linkDown.value = false
      return
    }
    if (linkTimer) return
    // 瞬断(重拨只要几百毫秒)不闪浮层
    linkTimer = setTimeout(() => {
      linkTimer = null
      linkDown.value = play.active
    }, 800)
  },
  { immediate: true },
)

/** 房间局信令发不出去时给一次提示(单机局没有连接依赖,不受影响) */
function blockedByLink() {
  if (!play.active || chatStore.socketStatus === 'open') return false
  ElMessage.warning(t('games.mahjong.linkLost'))
  return true
}

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
  pendingPrompts.clear()
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
  if (linkTimer) {
    clearTimeout(linkTimer)
    linkTimer = null
  }
  sunLight?.restoreEffects()
})
</script>

<style scoped>
.mahjong-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 8px;
  /* 外壳把游戏区锁成一屏,这里吃满剩余高度,盘面才有确定的可用区分母 */
  flex: 1;
  min-height: 0;
  min-width: 0;
}

/* 矮窗里规则/结算面板可独立滚动,牌桌本身始终按可用宽高等比缩放。 */
.cover,
.rules-setup {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.cover {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: clamp(14px, 6vh, 48px) 0;
}
.cover > * {
  flex-shrink: 0;
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
.cover-stats {
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
   牌身保持纯平面——只有描边和一层极浅投影,不做顶面条 / 侧棱的假立体 */
.tile {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 60px;
  border-radius: 5px;
  background: linear-gradient(180deg, #fdfcf7 0%, #f2eee2 100%);
  border: 1px solid rgba(0, 0, 0, 0.16);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.16);
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

/* 小牌(河里/副露):默认取牌河档,各处的副露再按自己的档位覆盖 */
.tile.mini {
  width: var(--mj-river-w, 32px);
  height: var(--mj-river-h, 44px);
  cursor: default;
}
.tile.mini .tile-num {
  font-size: 15px;
}
.tile.mini .tile-suit {
  font-size: 9px;
}
.tile.mini.honor {
  font-size: 13px;
}

/* 牌背:深色底 + 中央回形纹(纯 CSS,不引素材);平面牌背,靠描边分张 */
.tile.back {
  background:
    radial-gradient(circle at 50% 50%, rgba(255, 255, 255, 0.16) 0 8%, transparent 9%),
    linear-gradient(160deg, #2e6e5e 0%, #245a4e 60%, #1d4a40 100%);
  border-color: rgba(0, 0, 0, 0.35);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.22),
    inset 0 0 0 3px rgba(255, 255, 255, 0.1),
    0 1px 2px rgba(0, 0, 0, 0.26);
  cursor: default;
}
/* 侧向牌背(左右家):进深固定,列内高度由 .mj-backs-col 的 flex 平分(最多长到 --mj-side-back-h) */
.tile.back.side {
  width: var(--mj-side-back-w, 41px);
  height: var(--mj-side-back-h, 30px);
  border-radius: 4px;
}

/* 牌桌舞台:占满卡片剩余高度,盘面在里面按设计画布等比缩放、四向居中 */
.mj-stage {
  flex: 1;
  min-height: 0;
  position: relative;
  overflow: hidden;
}
/* 平面牌桌:用固定布局分出四家席位与中央牌池,保持牌形和相对位置。
   宽高由脚本按游戏区宽高比写进 --mj-w / --mj-h(整块游戏区铺成一张牌桌),再统一缩放。 */
.mj-table {
  position: absolute;
  left: 50%;
  top: 50%;
  width: var(--mj-w);
  height: var(--mj-h);
  transform: translate(-50%, -50%) scale(var(--mj-scale));
  transform-origin: center center;
  display: grid;
  grid-template-areas:
    'top top top'
    'left center right'
    'bottom bottom bottom';
  grid-template-columns: 148px minmax(0, 1fr) 148px;
  grid-template-rows: auto minmax(0, 1fr) auto;
  gap: 6px 8px;
  padding: 12px;
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
  --mj-scale: 1;
  /* 设计画布宽高:脚本按游戏区宽高比实时覆盖(见 MJ_MIN_W/H);这里的默认值与脚本下限同值 */
  --mj-w: 1000px;
  --mj-h: 500px;
  /* 牌面尺寸一整套:手牌最大,其余按「看得清、又不抢手牌」分档;左右家横躺的牌身/牌面互换宽高 */
  --mj-hand-w: 46px;
  --mj-hand-h: 62px;
  --mj-river-w: 32px;
  --mj-river-h: 44px;
  --mj-meld-w: 26px;
  --mj-meld-h: 36px;
  --mj-my-meld-w: 38px;
  --mj-my-meld-h: 50px;
  --mj-side-meld-w: 32px;
  --mj-side-meld-h: 22px;
  --mj-back-w: 30px;
  --mj-back-h: 41px;
  --mj-side-back-w: 41px;
  --mj-side-back-h: 30px;
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

/* 席位:名牌、手牌和副露共用该席位的格子。 */
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
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
/* 左右家:名牌在上,副露紧随其下,手牌一列在最下;整栏在中央行里纵向居中 */
.mj-left,
.mj-right {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-height: 0;
}
.mj-left {
  grid-area: left;
}
.mj-right {
  grid-area: right;
}
.mj-left .seat-tag,
.mj-right .seat-tag {
  flex-wrap: wrap;
  justify-content: center;
  gap: 3px 5px;
}
.mj-left .mj-seat-name,
.mj-right .mj-seat-name {
  max-width: calc(100% - 25px);
}
.mj-left .dealer-badge,
.mj-right .dealer-badge {
  margin-left: 0;
}
.mj-bottom {
  grid-area: bottom;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.seat-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
}
.seat-wind {
  flex: none;
  width: 20px;
  height: 20px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 12px;
  font-weight: 800;
  color: #463414;
  background: rgba(239, 202, 131, 0.9);
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
/* 托管徽标:席位被电脑接管时亮出 */
.auto-badge {
  padding: 0 7px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.6;
}
/* 对家:牌背横排 + 副露横排同一行(手牌与副露紧凑挨在一起) */
.side-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-width: 0;
  max-width: 100%;
  flex-wrap: wrap;
}
/* 左右家:副露横排在上、手牌一列在下,都收在同一个窄栏里;整块吃掉名牌下面剩下的高度 */
.side-hand {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-width: 0;
  min-height: 0;
  max-width: 100%;
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
/* 对家:横排牌背平铺,一张挨一张 */
.mj-backs {
  display: inline-flex;
  gap: 0;
}
.mj-backs .tile.back.sm {
  width: var(--mj-back-w, 30px);
  height: var(--mj-back-h, 41px);
  flex: none;
  border-radius: 4px;
}
/* 相邻牌共用一条描边,排成一条不留缝 */
.mj-backs .tile.back + .tile.back {
  margin-left: -1px;
}
/* 左右家:手牌和另外三家一样是「一条」——侧看就是自上而下的一列牌背,一张压一张共用描边。
   单张进深固定(--mj-side-back-w),列高交给 flex 平分:横屏矮屏里每张压扁到十几像素也还是
   一条整列,竖屏/iPad 里最多长到 --mj-side-back-h 就封顶居中,不会为了凑高度撑破中间行。 */
.mj-backs-col {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 0;
}
/* 单张进深固定(--mj-side-back-w),列高交给 flex 平分:横屏矮屏里每张压扁到十几像素也还是
   一条整列,竖屏/iPad 里最多长到 --mj-side-back-h 就封顶居中,不会为了凑高度撑破中间行。
   注意只能缩不能长(flex-grow 0):一旦允许长,max-height 会把多出来的空间变成牌与牌之间的
   缝,整列被拉开成一条虚线。 */
.mj-backs-col .tile.back.side {
  flex: 0 1 auto;
  min-height: 0;
  max-height: var(--mj-side-back-h, 30px);
}
.mj-backs-col .tile.back.side + .tile.back.side {
  margin-top: -1px;
}
/* 中央:牌河和中央信息分格。 */
.mj-center {
  grid-area: center;
  position: relative;
  display: grid;
  place-items: center;
  min-width: 0;
  min-height: 0;
}
/* 侧牌河占各自整列,顶/底牌河和中央信息共用中列。弃牌再多也只在自己的格子里滚动。 */
.mj-pool {
  --side-river-width: calc(4 * var(--mj-river-h) + 3px);
  position: absolute;
  inset: 10px;
  display: grid;
  grid-template-areas:
    'rl rt rr'
    'rl mid rr'
    'rl rb rr';
  grid-template-columns: var(--side-river-width) minmax(0, 1fr) var(--side-river-width);
  /* 顶/底牌河各吃一半剩余高度(能放几行就放几行,多了自己滚),中央信息按内容取高 */
  grid-template-rows: minmax(0, 1fr) auto minmax(0, 1fr);
  gap: 8px;
  place-items: center;
}
.mj-river {
  display: grid;
  align-content: start;
  justify-content: start;
  gap: 2px 1px;
  scrollbar-width: none;
}
.mj-river::-webkit-scrollbar {
  display: none;
}
.river-top,
.river-bottom {
  grid-template-columns: repeat(6, var(--mj-river-w));
  grid-auto-rows: var(--mj-river-h);
  max-height: 100%;
  overflow: hidden auto;
}
.river-top {
  grid-area: rt;
  align-self: end;
}
.river-bottom {
  grid-area: rb;
  align-self: start;
}
/* 左右两家的牌河:牌横躺,格子跟着横过来(行高 = 牌宽) */
.river-left,
.river-right {
  grid-template-rows: repeat(6, var(--mj-river-w));
  grid-auto-flow: column;
  grid-auto-columns: var(--mj-river-h);
  max-width: 100%;
  overflow: auto hidden;
}
.river-left {
  grid-area: rl;
  justify-self: end;
}
.river-right {
  grid-area: rr;
  justify-self: start;
}
/* 最新弃牌只改底色和内描边,不盖住相邻牌。 */
.mj-river .tile.mini {
  width: 100%;
  height: 100%;
  border-radius: 3px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.22);
}
/* 牌面下缘朝出牌人;左右家的方位也供副露共用。 */
.river-top :is(.tile-img, .tile-face) {
  transform: rotate(180deg);
}
.river-left .tile-img,
.river-left .tile-face,
.river-right .tile-img,
.river-right .tile-face {
  width: var(--mj-river-w);
  height: var(--mj-river-h);
}
.river-left :is(.tile-img, .tile-face),
.mj-left .meld-block :is(.tile-img, .tile-face) {
  transform: rotate(90deg);
}
.river-right :is(.tile-img, .tile-face),
.mj-right .meld-block :is(.tile-img, .tile-face) {
  transform: rotate(-90deg);
}
.mj-river .tile.latest {
  outline: 2px solid #efca83;
  outline-offset: -2px;
  background: #fff4d7;
}
/* 中央信息:风位罗盘 + 双骰 + 余牌/规则/回合 */
.mj-hub {
  grid-area: mid;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  padding: 9px 14px;
  border-radius: 14px;
  background: rgba(0, 0, 0, 0.24);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.12);
  text-align: center;
  max-width: 100%;
}
.hub-compass {
  position: relative;
  width: 62px;
  height: 54px;
}
.compass-wind {
  position: absolute;
  font-size: 12px;
  font-weight: 800;
  color: #f7f3e3;
  opacity: 0.92;
  line-height: 1;
}
.compass-wind.pos-top {
  top: 0;
  left: 50%;
  transform: translateX(-50%);
}
.compass-wind.pos-bottom {
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
}
.compass-wind.pos-left {
  left: 0;
  top: 50%;
  transform: translateY(-50%);
}
.compass-wind.pos-right {
  right: 0;
  top: 50%;
  transform: translateY(-50%);
}
.hub-dice {
  display: flex;
  align-items: center;
  gap: 5px;
}
.die {
  width: 21px;
  height: 21px;
  border-radius: 5px;
  background: linear-gradient(180deg, #fffef8 0%, #e8e2cf 100%);
  box-shadow:
    inset 0 1px 0 #fff,
    0 2px 3px rgba(0, 0, 0, 0.35);
  display: grid;
  grid-template-rows: repeat(3, 1fr);
  grid-template-columns: repeat(3, 1fr);
  padding: 2px;
}
.die-pip {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: #43301b;
  align-self: center;
  justify-self: center;
}
.die-sum {
  font-size: 13px;
  font-weight: 800;
  color: #efca83;
  font-variant-numeric: tabular-nums;
}
.hub-meta {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  max-width: 100%;
}
.wall-count {
  font-size: 11px;
  color: #d7e7dd;
  font-variant-numeric: tabular-nums;
}
.rules-chip {
  padding: 1px 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.16);
  color: #f1f7f2;
  font-size: 10px;
  white-space: nowrap;
}
html.dark .rules-chip {
  background: rgba(0, 0, 0, 0.32);
}
.turn-label {
  font-size: 12px;
  font-weight: 700;
  line-height: 1.3;
  color: #fff;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 副露区:上下家横排,左右家纵排。 */
.meld-row {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  align-items: center;
  gap: 2px;
  max-width: 100%;
  padding: 3px 4px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.14);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.1);
}
.meld-group {
  display: inline-flex;
  gap: 0;
  padding: 1px;
  border-radius: 5px;
  background: rgba(255, 255, 255, 0.2);
}
.meld-group .tile.mini + .tile.mini {
  margin-left: -1px;
}
.meld-row .tile.mini {
  width: var(--mj-meld-w, 26px);
  height: var(--mj-meld-h, 36px);
  border-radius: 3px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.18);
}
/* 两侧副露:横躺牌身一列一列并排,整块落在家手牌的上方(不吃手牌左右的空间);
   牌身横躺、牌面按长方形给尺寸再转 90° 朝向该家,旋转后正好落满牌身 */
.meld-block {
  flex-wrap: nowrap;
  gap: 2px;
  padding: 3px 4px;
}
.meld-block .meld-group {
  flex-direction: column;
  flex: none;
}
.meld-block .meld-group .tile.mini + .tile.mini {
  margin-left: 0;
  margin-top: -1px;
}
.meld-block .tile.mini {
  width: var(--mj-side-meld-w, 32px);
  height: var(--mj-side-meld-h, 22px);
}
.meld-block .tile.mini :is(.tile-img, .tile-face) {
  width: var(--mj-side-meld-h, 22px);
  height: var(--mj-side-meld-w, 32px);
}
/* 我方:副露与手牌同排,上方留出摸牌/悬停抬起的空间。 */
.my-row {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 8px;
  max-width: 100%;
  flex-wrap: nowrap;
  padding-top: 10px;
}
.my-melds {
  align-self: flex-end;
}
/* 我的副露比对家/左右家都大一号(就在自己眼皮底下,要和手牌一起读) */
.my-melds .tile.mini {
  width: var(--mj-my-meld-w, 38px);
  height: var(--mj-my-meld-h, 50px);
  border-radius: 4px;
}
.hand-row {
  display: flex;
  flex-wrap: nowrap;
  gap: 0;
  justify-content: center;
  align-items: flex-end;
  max-width: 100%;
}
/* 手牌固定设计尺寸(46×62,与牌面同比例),整桌靠统一缩放适配窗口 */
.tile.hand {
  width: var(--mj-hand-w, 46px);
  height: var(--mj-hand-h, 62px);
  padding: 0;
}
.tile.hand + .tile.hand {
  margin-left: -1px;
}
.tile.hand.drawn {
  transform: translateY(-9px);
  box-shadow: 0 6px 12px rgba(0, 0, 0, 0.35);
}
.tile.hand:not(:disabled):hover {
  transform: translateY(-6px);
}
.tile.hand.dim {
  opacity: 0.6;
}
/* 操作条浮在手牌正上方:出现时不占布局高度,手牌下面不会再留一块空地 */
.mj-bottom {
  position: relative;
}
.my-actions {
  position: absolute;
  left: 50%;
  bottom: calc(100% + 2px);
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: center;
  padding: 6px 10px;
  border-radius: 12px;
  background: rgba(10, 30, 24, 0.44);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.14);
  backdrop-filter: blur(6px);
  z-index: 2;
  max-width: 100%;
  white-space: nowrap;
}
.my-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
/* 吃按钮里内嵌的两张候选小牌 */
.my-actions .tile.mini {
  width: 24px;
  height: 32px;
  margin-left: 4px;
  box-shadow: none;
  border-radius: 4px;
}

/* ===== 开局规则面板(单机封面 / 房间准备阶段) ===== */
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
.rules-setup {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: safe center;
}
.rules-setup .rules-panel {
  flex-shrink: 0;
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
/* 断线浮层压在结算浮层之下:结算是终局,重连提示不该盖住它 */
.link-overlay {
  z-index: 4;
}
.link-overlay-card {
  padding: 18px 24px;
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
  max-height: calc(100% - 16px);
  overflow: auto;
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
}

/* ===== 手机横屏:整块游戏区就是一张麻将桌 =====
 * 矮屏横屏最缺的正是高度,而外壳默认会在左边留一栏放头部,白白从毯面身边切走 150~200px。
 * 单机局改成盘面独占整页,返回/游戏名/新游戏/规则压成一块玻璃条浮在桌面左上角
 * (左上角那块毯面本来就不摆牌)。
 * 「一起玩」房间条一出现就有六七个按钮,浮在桌面上必然压住上家的副露,所以房间局仍走
 * 外壳的两栏横屏档(左栏放头与房间条),只把牌桌按同一套设计画布铺满右栏。 */
@media (orientation: landscape) and (max-height: 520px) {
  .page.game-shell.mj-shell:not(.mj-room) {
    position: relative;
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: minmax(0, 1fr);
    column-gap: 0;
    row-gap: 0;
  }
  /* 浮层高度跟着内容走:桌面全被盘面占满,浮层只在左上角压住一小块 */
  .page.game-shell.mj-shell:not(.mj-room) :deep(.gs-rail) {
    position: absolute;
    top: 0;
    left: 0;
    z-index: var(--z-float);
    max-width: min(72vw, 520px);
    max-height: 100%;
    overflow-y: auto;
    overscroll-behavior: contain;
    padding: 4px 6px 6px;
    border-radius: 12px;
    color: #f6f2e4;
    background: rgba(10, 32, 25, 0.5);
    box-shadow:
      inset 0 0 0 1px rgba(255, 255, 255, 0.16),
      0 8px 18px rgba(0, 0, 0, 0.18);
    backdrop-filter: blur(10px) saturate(1.1);
  }
  .page.game-shell.mj-shell:not(.mj-room) :deep(.gs-head) {
    min-height: 0;
    padding-bottom: 2px;
  }
  .page.game-shell.mj-shell:not(.mj-room) :deep(.gs-title) {
    color: #f6f2e4;
  }
  /* 盘面独占整块游戏区(本页没有计分栏/页脚,一行一列就够) */
  .page.game-shell.mj-shell:not(.mj-room) :deep(.gs-body) {
    grid-row: 1;
    grid-column: 1;
  }
}
</style>
