<!-- 自研小游戏统一外壳:一个页面只填插槽,公共能力全在这里。
     一段头部行(返回 | 标题 | …… | 全屏 宽屏 规则)+ 一行控件(设置 | 数据芯片 | 按钮)
     + 游戏区(--gs-w/--gs-h 实测写入)。页面本体透明,直接透出内容页底板,不套圆角外框。
     规范见 docs/UI设计提示词.md §43;样式在 main.css 的「统一游戏外壳」段
     (插槽内容跨作用域,scoped 管不到)。 -->
<template>
  <div ref="pageEl" class="page game-page game-shell" :class="{ 'game-page-readable': readable }">
    <!-- 头/规则/控件/房间条打包成「信息栏」:竖屏是页面顶部的一块,
         手机横屏时整块挪到左侧(游戏区独占右侧),见 main.css 的横屏档 -->
    <div class="gs-rail">
      <header class="gs-head">
        <el-button
          v-if="showBack"
          class="gs-back"
          size="small"
          round
          :title="backLabel || $t('games.back')"
          :aria-label="backLabel || $t('games.back')"
          @click="$emit('back')"
        >
          <el-icon><ArrowLeft /></el-icon>
          <span class="gs-back-label">{{ backLabel || $t('games.back') }}</span>
        </el-button>
        <span class="gs-title">{{ title }}</span>
        <!-- 标题后缀(数独的模式/难度等):跟在游戏名后面,手机档自动收窄 -->
        <div v-if="$slots.titleExtra" class="gs-title-extra"><slot name="titleExtra" /></div>
        <div class="gs-end">
          <slot name="head" />
          <el-tag v-if="gamepadConnected" size="small" type="success" effect="plain" class="gamepad-tag">
            {{ $t('games.gamepadReady') }}
          </el-tag>
          <!-- 声音总开关:只给有声音的游戏出键(页面传 sound)。一份状态压住音效、iframe 游戏与悬浮 BGM,
               见 utils/games/gameSound.js;静音态用主色键表示「已开启」 -->
          <el-button
            v-if="sound"
            class="gs-icon-btn gs-mute"
            size="small"
            round
            :type="muted ? 'primary' : 'default'"
            :title="muted ? $t('games.unmute') : $t('games.mute')"
            :aria-label="muted ? $t('games.unmute') : $t('games.mute')"
            :aria-pressed="muted"
            @click="toggleGameMuted"
          >
            <el-icon>
              <svg
                viewBox="0 0 24 24"
                width="16"
                height="16"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <path d="M4 9.5h3l4.5-3.5v12L7 14.5H4z" />
                <template v-if="muted">
                  <path d="M16.5 9.5l5 5M21.5 9.5l-5 5" />
                </template>
                <template v-else>
                  <path d="M15.5 8.6a5 5 0 0 1 0 6.8" />
                  <path d="M18.4 6.2a8.6 8.6 0 0 1 0 11.6" />
                </template>
              </svg>
            </el-icon>
          </el-button>
          <!-- 新游戏:主按钮配色(每局的主操作,和全屏/宽屏/规则那排玻璃键区分开),固定在规则键左侧 -->
          <el-button v-if="newGame" class="gs-new-game" type="primary" size="small" round :disabled="newGameDisabled" @click="onNewGame">
            {{ $t('games.newGame') }}
          </el-button>
          <el-button
            v-if="!roomMode"
            class="gs-icon-btn gs-fullscreen"
            size="small"
            round
            :type="isFullscreen ? 'primary' : 'default'"
            :title="isFullscreen ? $t('games.exitFullscreen') : $t('games.fullscreen')"
            :aria-label="isFullscreen ? $t('games.exitFullscreen') : $t('games.fullscreen')"
            @click="toggleFullscreen"
          >
            <el-icon><FullScreen /></el-icon>
          </el-button>
          <el-button
            class="gs-icon-btn gs-wide"
            size="small"
            round
            :type="isWide ? 'primary' : 'default'"
            :title="isWide ? $t('games.exitWide') : $t('games.wide')"
            :aria-label="isWide ? $t('games.exitWide') : $t('games.wide')"
            @click="toggleWide"
          >
            <el-icon><Monitor /></el-icon>
          </el-button>
          <el-button
            class="gs-rules"
            size="small"
            round
            :type="rulesOpen ? 'primary' : 'default'"
            :title="$t('games.rulesLabel')"
            :aria-label="$t('games.rulesLabel')"
            aria-haspopup="dialog"
            :aria-expanded="rulesOpen"
            @click="openRules"
          >
            <el-icon><Reading /></el-icon>
            <span class="gs-rules-label">{{ $t('games.rulesLabel') }}</span>
          </el-button>
        </div>
      </header>

      <div v-if="$slots.settings || $slots.actions" class="gs-controls">
        <!-- 空插槽不渲染:否则空 div 也参与 flex gap,按钮会莫名偏离右边 -->
        <div v-if="$slots.settings" class="gs-settings"><slot name="settings" /></div>
        <div v-if="$slots.actions" class="gs-actions"><slot name="actions" /></div>
      </div>

      <div v-if="$slots.extras" class="gs-extras"><slot name="extras" /></div>
      <!-- 页面级浮层(分享弹窗等):挂在信息栏里,游戏区是尺寸容器会把 fixed 定位困住 -->
      <slot name="overlay" />

      <!-- 规则弹窗:规则搬进模态框后不再挤占棋盘高度,页数多时顶部出分页标签。
           与分享弹窗同一处理:不 teleport 进 body,全屏时挂在整页里才看得见(§43) -->
      <el-dialog
        v-model="rulesOpen"
        class="gs-rules-dialog"
        width="min(560px, 92vw)"
        :title="rulesTitle"
        :append-to-body="!isFullscreen"
        :lock-scroll="false"
      >
        <div v-if="rulesPageLabels.length > 1" class="gs-rules-tabs" role="tablist">
          <button
            v-for="(label, i) in rulesPageLabels"
            :key="label + i"
            type="button"
            class="gs-rules-tab"
            :class="{ 'is-active': i === rulesPage }"
            role="tab"
            :aria-selected="i === rulesPage"
            @click="rulesPage = i"
          >
            {{ label }}
          </button>
        </div>
        <div class="gs-rules-body">
          <slot name="rules" :page="rulesPage" />
        </div>
        <template #footer>
          <el-button type="primary" @click="rulesOpen = false">{{ $t('games.rulesAck') }}</el-button>
        </template>
      </el-dialog>
    </div>

    <!-- 数据/积分栏:竖屏是头部下面独立一行(靠左),手机横屏贴到左栏最下面一行,
         页面只放真正放不进棋盘的数据芯片,见 main.css 的横屏档 -->
    <div v-if="$slots.stats" class="gs-stats"><slot name="stats" /></div>

    <div ref="bodyEl" class="gs-body game-surface" :class="[`is-${bodyAlign}`, bodyClass]">
      <slot />
    </div>

    <div v-if="$slots.footer" class="gs-footer"><slot name="footer" /></div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessageBox } from 'element-plus'
import { ArrowLeft, FullScreen, Monitor, Reading } from '@element-plus/icons-vue'
import { useGameView } from '@/composables/useGameView'
import { useGameExit } from '@/composables/useGameExit'
import { gameMuted, toggleGameMuted } from '@/utils/games/gameSound'

const props = defineProps({
  /** 游戏名(页面按对局状态拼好后传入,如「2048 · 一起玩」) */
  title: { type: String, required: true },
  /** 返回按钮文案,缺省「返回」 */
  backLabel: { type: String, default: '' },
  showBack: { type: Boolean, default: true },
  /** 是否把页面锁成一屏高(牌桌类自己算高度的页面传 false) */
  fit: { type: Boolean, default: true },
  /** 页面自身管高度与滚动(与 .game-page-readable 同义) */
  readable: { type: Boolean, default: false },
  /** 游戏区对齐:center(棋盘居中)/stretch(画板吃满)/start(顶部对齐) */
  bodyAlign: { type: String, default: 'center' },
  /** 追加到游戏区的类(个别游戏需要自己的定位上下文) */
  bodyClass: { type: [String, Array, Object], default: '' },
  /** 规则弹窗的分页标签,长度 >1 时顶部出分页;每页内容由 #rules 插槽按 page 渲染 */
  rulesPages: { type: Array, default: () => [] },
  /** 头部是否出文字「新游戏」键(固定在规则键左侧);房间对局里由页面置 false */
  newGame: { type: Boolean, default: false },
  /** 「新游戏」键是否禁用(例如撤销栈为空、对局进行中) */
  newGameDisabled: { type: Boolean, default: false },
  /** 点「新游戏」前先确认(对局进行中会丢进度);页面按自己的进行中状态传 */
  newGameConfirm: { type: Boolean, default: false },
  /** 进页默认开宽屏(隐藏侧栏、放开内容宽度);用户仍可用头部「宽屏」键切回 */
  defaultWide: { type: Boolean, default: false },
  /** 「一起玩」房间局:不出浏览器全屏键(全屏会把 App 层的聊天浮窗一起隐藏),撑满走「宽屏」 */
  roomMode: { type: Boolean, default: false },
  /** 这款游戏有声音:头部出静音键(音效、iframe 音频与悬浮 BGM 共用一份状态) */
  sound: { type: Boolean, default: false },
})
const emit = defineEmits(['back', 'newGame'])
useGameExit(() => emit('back'))

const { t } = useI18n()
// 静音状态是全局单例,页面自己也可以 import 同一份(如引擎侧需要读当前值)
const muted = gameMuted

const rulesOpen = ref(false)
const rulesPage = ref(0)
const rulesPageLabels = computed(() => (Array.isArray(props.rulesPages) ? props.rulesPages.map(String).filter(Boolean) : []))
// 弹窗标题带上游戏名,避免只写「规则」看不出是哪个游戏
const rulesTitle = computed(() => (props.title ? `${props.title} · ${t('games.rulesLabel')}` : t('games.rulesLabel')))
const openRules = () => {
  rulesPage.value = 0
  rulesOpen.value = true
}

// 新游戏:对局进行中先确认(取消则原局不动),确认后才交给页面
const onNewGame = async () => {
  if (props.newGameConfirm) {
    try {
      await ElMessageBox.confirm(t('games.newGameConfirm'), t('games.newGame'), {
        type: 'warning',
        closeOnClickModal: true,
      })
    } catch (e) {
      return
    }
  }
  emit('newGame')
}
const pageEl = ref(null)
const bodyEl = ref(null)

// 全屏目标是整页:头部与控件在全屏里仍然可用(否则「新局/暂停」按不到)
// gamepadConnected 由 useGameView 内部接手柄后返回,头部直接用它做提示
const { isFullscreen, isWide, toggleFullscreen, toggleWide, refit, gamepadConnected } = useGameView({
  page: pageEl,
  stage: pageEl,
  fit: props.fit,
  scale: false,
})

// 游戏区实测尺寸写成 CSS 变量,棋盘/牌桌按 --gs-h 推导尺寸,
// 各页不用再写 calc(100dvh - 442px) 这种按外壳猜的魔法数。
let bodyObserver = null
const syncBodySize = () => {
  const box = bodyEl.value
  const root = pageEl.value
  if (!box || !root) return
  root.style.setProperty('--gs-w', `${Math.round(box.clientWidth)}px`)
  root.style.setProperty('--gs-h', `${Math.round(box.clientHeight)}px`)
}

const onWindowResize = () => syncBodySize()

onMounted(() => {
  // 让移动端的浮动返回键退场:本外壳自己提供返回
  document.documentElement.classList.add('game-shell')
  if (props.defaultWide && !isWide.value) toggleWide()
  if (typeof ResizeObserver !== 'undefined' && bodyEl.value) {
    bodyObserver = new ResizeObserver(syncBodySize)
    bodyObserver.observe(bodyEl.value)
  }
  window.addEventListener('resize', onWindowResize, { passive: true })
  syncBodySize()
})

onBeforeUnmount(() => {
  document.documentElement.classList.remove('game-shell')
  bodyObserver?.disconnect()
  bodyObserver = null
  window.removeEventListener('resize', onWindowResize)
})

defineExpose({ pageEl, bodyEl, isFullscreen, isWide, toggleFullscreen, toggleWide, refit })
</script>
