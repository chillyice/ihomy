<!-- 小游戏页顶栏动作组:返回 + 全屏 + 宽屏(放在 PageToolbar root-class="game-topbar" 里) -->
<template>
  <div class="game-top-actions">
    <el-tag v-if="gamepad" size="small" type="success" effect="plain" class="gamepad-tag">
      {{ $t('games.gamepadReady') }}
    </el-tag>
    <el-button
      v-if="showBack"
      class="gt-back"
      size="small"
      round
      :title="backLabel || $t('games.back')"
      :aria-label="backLabel || $t('games.back')"
      @click="$emit('back')"
    >
      <el-icon><ArrowLeft /></el-icon>
      <span class="gt-label">{{ backLabel || $t('games.back') }}</span>
    </el-button>
    <el-button
      v-if="showFullscreen"
      class="gt-fullscreen"
      size="small"
      round
      :title="isFullscreen ? exitLabel || $t('games.exitFullscreen') : fullscreenLabel || $t('games.fullscreen')"
      :aria-label="isFullscreen ? exitLabel || $t('games.exitFullscreen') : fullscreenLabel || $t('games.fullscreen')"
      @click="$emit('fullscreen')"
    >
      <el-icon><FullScreen /></el-icon>
      <span class="gt-label">{{ isFullscreen ? exitLabel || $t('games.exitFullscreen') : fullscreenLabel || $t('games.fullscreen') }}</span>
    </el-button>
    <el-button
      v-if="showWide"
      class="gt-wide"
      size="small"
      round
      :type="wide ? 'primary' : 'default'"
      :title="wide ? exitWideLabel || $t('games.exitWide') : wideLabel || $t('games.wide')"
      :aria-label="wide ? exitWideLabel || $t('games.exitWide') : wideLabel || $t('games.wide')"
      @click="$emit('wide')"
    >
      <el-icon><Monitor /></el-icon>
      <span class="gt-label">{{ wide ? exitWideLabel || $t('games.exitWide') : wideLabel || $t('games.wide') }}</span>
    </el-button>
  </div>
</template>

<script setup>
import { inject } from 'vue'
import { ArrowLeft, FullScreen, Monitor } from '@element-plus/icons-vue'
import { GAMEPAD_KEY } from '@/composables/useGamepad'

// 页面用 useGameView 时会把「手柄已上手」注入下来;没接手柄时这个标签不出现
const gamepad = inject(GAMEPAD_KEY, null)

defineProps({
  showBack: { type: Boolean, default: true },
  showFullscreen: { type: Boolean, default: true },
  showWide: { type: Boolean, default: true },
  backLabel: { type: String, default: '' },
  fullscreenLabel: { type: String, default: '' },
  exitLabel: { type: String, default: '' },
  wideLabel: { type: String, default: '' },
  exitWideLabel: { type: String, default: '' },
  isFullscreen: { type: Boolean, default: false },
  wide: { type: Boolean, default: false },
})
defineEmits(['back', 'fullscreen', 'wide'])
</script>
