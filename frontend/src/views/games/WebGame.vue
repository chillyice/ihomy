<!-- 网页游戏:站内 iframe 打开外部游戏页(LINK 类型),不跳出 ihomy
     触控守卫沿用 .game-surface(禁双击放大/长按选字/长按菜单);全屏=浏览器全屏 API,
     页面全屏=站内沉浸浮层(不按 F11);站点登录信息统一存家庭保险箱 -->
<template>
  <div ref="pageEl" class="page web-game-page game-page">
    <PageToolbar root-class="game-topbar" :holder-margin="0" always>
      <el-button size="small" round @click="router.push('/games')">
        <el-icon><ArrowLeft /></el-icon>
        {{ $t('games.back') }}
      </el-button>
      <span class="wg-title">{{ game?.name || $t('games.title') }}</span>
      <div class="game-top-actions">
        <el-button size="small" round :title="$t('games.web.reload')" :aria-label="$t('games.web.reload')" @click="reloadFrame">
          <el-icon><Refresh /></el-icon>
          <span class="wg-btn-label">{{ $t('games.web.reload') }}</span>
        </el-button>
        <el-button
          size="small"
          round
          :title="$t('games.web.openExternal')"
          :aria-label="$t('games.web.openExternal')"
          @click="openExternal"
        >
          <el-icon><TopRight /></el-icon>
          <span class="wg-btn-label">{{ $t('games.web.openExternal') }}</span>
        </el-button>
        <el-button v-if="canVault" size="small" round :title="$t('games.web.vault')" :aria-label="$t('games.web.vault')" @click="openVault">
          <el-icon><Lock /></el-icon>
          <span class="wg-btn-label">{{ $t('games.web.vault') }}</span>
        </el-button>
        <el-button
          size="small"
          round
          :title="pageFull ? $t('games.web.exitPageFullscreen') : $t('games.web.pageFullscreen')"
          @click="togglePageFull"
        >
          <el-icon><Fold v-if="pageFull" /><Expand v-else /></el-icon>
          <span class="wg-btn-label">{{ pageFull ? $t('games.web.exitPageFullscreen') : $t('games.web.pageFullscreen') }}</span>
        </el-button>
        <el-button size="small" round @click="toggleFullscreen">
          <el-icon><FullScreen /></el-icon>
          <span class="wg-btn-label">{{ isFullscreen ? $t('games.exitFullscreen') : $t('games.fullscreen') }}</span>
        </el-button>
      </div>
    </PageToolbar>

    <div ref="stageEl" class="wg-stage card game-surface" :class="{ 'is-page-full': pageFull }">
      <iframe
        v-if="game?.linkUrl"
        :key="frameKey"
        class="wg-frame"
        :src="game.linkUrl"
        :title="game.name"
        allow="fullscreen; autoplay; gamepad; clipboard-read; clipboard-write"
        allowfullscreen
        referrerpolicy="no-referrer"
        @load="frameLoaded = true"
      ></iframe>
      <div v-if="!frameLoaded" class="wg-cover">
        <template v-if="loadError">
          <div class="wg-cover-title">{{ $t('games.loadFailed') }}</div>
          <el-button v-if="game?.linkUrl" size="small" round type="primary" @click="openExternal">{{
            $t('games.web.openExternal')
          }}</el-button>
          <el-button v-else size="small" round type="primary" @click="router.push('/games')">{{ $t('games.back') }}</el-button>
        </template>
        <template v-else>
          <div class="wg-cover-spinner"></div>
          <div class="wg-cover-title">{{ $t('games.loading') }}</div>
        </template>
      </div>
      <div v-if="pageFull" class="wg-float">
        <el-button
          circle
          :title="$t('games.web.exitPageFullscreen')"
          :aria-label="$t('games.web.exitPageFullscreen')"
          @click="pageFull = false"
        >
          <el-icon><Close /></el-icon>
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, inject, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Close, Expand, Fold, FullScreen, Lock, Refresh, TopRight } from '@element-plus/icons-vue'
import PageToolbar from '@/components/PageToolbar.vue'
import { gameApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import { useGameView } from '@/composables/useGameView'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const sunLight = inject(SUN_LIGHT_KEY, null)

const pageEl = ref(null)
const game = ref(null)
const stageEl = ref(null)
const frameKey = ref(0)
const frameLoaded = ref(false)
const loadError = ref(false)
const pageFull = ref(false)
// iframe 本身不缩放(缩了字会糊),页面按内容区锁高、iframe 用 flex 撑满
const { isFullscreen, toggleFullscreen } = useGameView({ page: pageEl, stage: stageEl, scale: false })

/** 保险箱写权限(新增条目要 vault:manage);没有权限就不显示入口,避免点了才报错 */
const canVault = computed(() => userStore.isOwner || userStore.hasPerm('vault:manage'))

const reloadFrame = () => {
  loadError.value = false
  frameLoaded.value = false
  frameKey.value += 1
}

const openExternal = () => {
  if (game.value?.linkUrl) window.open(game.value.linkUrl, '_blank', 'noopener,noreferrer')
}

/** 站点登录信息统一存家庭保险箱:带上游戏名与网址直接打开新建弹窗 */
const openVault = () => {
  if (!game.value) return
  router.push({
    path: '/vault',
    query: { create: '1', name: game.value.name, url: game.value.linkUrl, category: 'SITE' },
  })
}

const togglePageFull = () => {
  pageFull.value = !pageFull.value
}

const onKeydown = (event) => {
  if (event.key === 'Escape' && pageFull.value) pageFull.value = false
}

/** 加载游戏详情:LINK 交给 iframe,文件类游戏转原播放器页(旧链接/手输地址不落空) */
const loadGame = async (id) => {
  loadError.value = false
  frameLoaded.value = false
  game.value = null
  try {
    const data = await gameApi.get(id)
    if (data && data.type === 'LINK' && data.linkUrl) {
      game.value = data
    } else if (data) {
      router.replace(`/games/play/${data.id}`)
    } else {
      loadError.value = true
    }
  } catch (e) {
    loadError.value = true
  }
}

watch(
  () => route.params.id,
  (id) => {
    if (id) loadGame(id)
  },
)

onMounted(() => {
  document.addEventListener('keydown', onKeydown)
  sunLight?.suspendEffects()
  loadGame(route.params.id)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  if (document.fullscreenElement) document.exitFullscreen?.()
  sunLight?.restoreEffects?.()
})
</script>

<style scoped>
.wg-title {
  font-size: 14px;
  font-weight: 600;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.wg-stage {
  position: relative;
  flex: 1 1 auto;
  height: auto;
  min-height: 320px;
  padding: 0;
  overflow: hidden;
  background: #1b1b1b;
}
.wg-frame {
  display: block;
  width: 100%;
  height: 100%;
  border: 0;
  background: var(--color-bg-2);
  touch-action: manipulation;
}
.wg-stage:fullscreen {
  height: 100%;
  border-radius: 0;
}
/* 页面全屏:站内沉浸浮层,盖住侧栏/页脚/顶栏(不触发浏览器 F11) */
.wg-stage.is-page-full {
  position: fixed;
  inset: 0;
  z-index: var(--z-immersive);
  height: auto;
  min-height: 0;
  border-radius: 0;
  border: 0;
  box-shadow: none;
}
.wg-cover {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: var(--color-card, #fff);
  color: var(--color-text-secondary, #888);
  font-size: 13px;
}
.wg-cover-spinner {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  border: 2px solid var(--color-border, rgba(0, 0, 0, 0.12));
  border-top-color: var(--color-accent, #b88c6e);
  animation: wg-spin 0.9s linear infinite;
}
@keyframes wg-spin {
  to {
    transform: rotate(360deg);
  }
}
.wg-float {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 2;
}
/* 窄屏工具栏只留图标,避免一排按钮把标题挤没 */
@media (max-width: 768px) {
  .wg-btn-label {
    display: none;
  }
}
</style>
