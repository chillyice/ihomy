<!-- 小游戏页顶栏动作组:返回 + 全屏 + 宽屏(放在 PageToolbar root-class="game-topbar" 里) -->
<template>
  <div class="game-top-actions">
    <el-tag v-if="gamepad" size="small" type="success" effect="plain" class="gamepad-tag">
      {{ $t('games.gamepadReady') }}
    </el-tag>
    <el-button v-if="showBack" size="small" round @click="$emit('back')">
      <el-icon><ArrowLeft /></el-icon>
      {{ backLabel || $t('games.back') }}
    </el-button>
    <el-button v-if="showFullscreen" size="small" round @click="$emit('fullscreen')">
      <el-icon><FullScreen /></el-icon>
      {{ isFullscreen ? exitLabel || $t('games.exitFullscreen') : fullscreenLabel || $t('games.fullscreen') }}
    </el-button>
    <el-button v-if="showWide" size="small" round :type="wide ? 'primary' : 'default'" @click="$emit('wide')">
      <el-icon><Monitor /></el-icon>
      {{ wide ? exitWideLabel || $t('games.exitWide') : wideLabel || $t('games.wide') }}
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
