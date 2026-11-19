<template>
  <GameShell
    ref="shellEl"
    class="cards-shell"
    :title="$t(modes[currentGame])"
    body-align="stretch"
    :new-game="!inRoom"
    @back="router.push('/games')"
    @new-game="board?.startNew?.()"
  >
    <template #rules>
      <p>{{ $t(modeRules[currentGame]) }}</p>
    </template>

    <!-- 模式筛选(翻牌数/难度)贴在游戏名后面:竖屏、桌面、手机横屏同一位置 -->
    <template #titleExtra>
      <el-select v-if="currentGame === 'klondike'" v-model="drawCount" class="cards-mode" :teleported="!isFullscreen" :disabled="inRoom">
        <el-option :value="1" :label="$t('games.solitaire.draw1')" />
        <el-option :value="3" :label="$t('games.solitaire.draw3')" />
      </el-select>
      <el-select
        v-else-if="currentGame === 'spider'"
        v-model="difficulty"
        class="cards-mode"
        :teleported="!isFullscreen"
        :disabled="inRoom"
      >
        <el-option v-for="d in DIFFICULTIES" :key="d" :value="d" :label="difficultyLabel(d)" />
      </el-select>
    </template>

    <!-- 积分/时间:手机横屏进外壳左栏底边(芯片一行);竖屏用棋盘上方那条工具条,这一份隐藏 -->
    <template #stats>
      <span v-for="item in boardStats" :key="item.label" class="gh-chip">
        <span>{{ item.label }}</span
        ><b>{{ item.value }}</b>
      </span>
    </template>

    <!-- 棋盘左侧放不下的操作按钮:手机横屏进外壳左栏第三行;竖屏用工具条 -->
    <template #actions>
      <el-button size="small" round @click="board?.showHint?.()">{{ $t(labels.hint) }}</el-button>
      <el-button v-if="!inRoom" size="small" round :disabled="!board?.canUndo" @click="board?.undo?.()">{{ $t(labels.undo) }}</el-button>
      <el-button v-if="labels.autoFinish" size="small" round @click="board?.autoFinishAll?.()">{{ $t(labels.autoFinish) }}</el-button>
    </template>

    <div class="cards-stage">
      <KlondikeBoard v-if="currentGame === 'klondike'" ref="board" v-model:draw-count="drawCount" />
      <SpiderBoard v-else-if="currentGame === 'spider'" ref="board" v-model:difficulty="difficulty" />
      <FreeCellBoard v-else ref="board" />
    </div>
  </GameShell>
</template>

<script setup>
import { computed, inject, onBeforeUnmount, onMounted, provide, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import GameShell from '@/components/games/GameShell.vue'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import { DIFFICULTIES } from '@/utils/games/spider'
import KlondikeBoard from './cards/KlondikeBoard.vue'
import SpiderBoard from './cards/SpiderBoard.vue'
import FreeCellBoard from './cards/FreeCellBoard.vue'
import './cards/cards.css'

const MODE_KEY = 'ihomy:games:cards:mode:v1'
const modes = {
  klondike: 'games.solitaire.title',
  spider: 'games.spider.title',
  freecell: 'games.freecell.title',
}

/** 各玩法的玩法说明(规则弹窗内容) */
const modeRules = {
  klondike: 'games.solitaire.tip',
  spider: 'games.spider.tip',
  freecell: 'games.freecell.tip',
}

/** 各玩法在外壳头部/控件行用的按钮文案(棋盘只交动作,文案留在页面) */
const modeLabels = {
  klondike: { hint: 'games.solitaire.hint', undo: 'games.solitaire.undo', autoFinish: 'games.solitaire.autoFinish' },
  spider: { hint: 'games.spider.hint', undo: 'games.spider.undo', autoFinish: '' },
  freecell: { hint: 'games.freecell.hint', undo: 'games.freecell.undo', autoFinish: 'games.freecell.autoFinish' },
}

function savedMode() {
  try {
    const mode = localStorage.getItem(MODE_KEY)
    if (Object.hasOwn(modes, mode)) return mode
  } catch {
    // Storage may be disabled.
  }
  return 'klondike'
}

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const sunLight = inject(SUN_LIGHT_KEY, null)
// 绑上 ref 才能取到外壳的全屏态:棋盘里的下拉/浮层按它决定挂在哪一层
const shellEl = ref(null)
/** 牌桌按这个标志切换弹窗 teleport 与快捷键(三桌 inject) */
const isFullscreen = computed(() => !!shellEl.value?.isFullscreen)
provide('gameFullscreen', isFullscreen)

/** 房间对局:牌桌模式固定由房主定,不给重开、不改模式、不给撤销 */
const inRoom = computed(() => !!route.query.room)
/** 当前挂着的牌桌(只挂一个,三桌共用一个 ref) */
const board = ref(null)
/** 翻牌数/难度由页面持有(头部有控件),棋盘用 v-model 同步 */
const drawCount = ref(1)
const difficulty = ref(1)

const initialMode = savedMode()
const currentGame = computed(() => (Object.hasOwn(modes, route.params.game) ? route.params.game : initialMode))
const labels = computed(() => modeLabels[currentGame.value])
const boardStats = computed(() => board.value?.stats || [])
const difficultyLabel = (d) =>
  ({ 1: t('games.spider.oneSuit'), 2: t('games.spider.twoSuits'), 4: t('games.spider.fourSuits') })[d] || String(d)

watch(
  currentGame,
  (mode) => {
    try {
      localStorage.setItem(MODE_KEY, mode)
    } catch {
      // Storage may be disabled.
    }
  },
  { immediate: true },
)

onMounted(() => sunLight?.suspendEffects())
onBeforeUnmount(() => sunLight?.restoreEffects())
</script>

<style scoped>
/* 模式筛选必须有定宽:el-select 不定宽时在 flex 行里会被压成只有箭头(文字区宽 0);
   128 是「四色(困难)」这类最长档位全显示所需(再窄就只剩省略号) */
.cards-mode {
  width: 128px;
}

.cards-stage {
  display: flex;
  /* 拉满外壳游戏区:牌桌内部 fitCardWidth 按 felt 实测宽高自适应 */
  flex: 1 1 320px;
  min-height: 0;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}
</style>
