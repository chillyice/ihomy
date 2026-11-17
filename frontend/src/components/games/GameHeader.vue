<!-- 自研小游戏统一卡头:所有游戏页共用同一套头部语言——
     游戏名在左,设置项(变体/难度等)与名称并列,数据芯片随后,右侧是主操作 + 规则;
     规则按钮展开头下方的说明面板(默认收起,替代各页原先常驻的 tip 条);
     浏览器全屏时页面顶栏不在全屏元素内,这里补一个退出全屏按钮。
     样式统一放在 main.css 的「统一游戏卡头」段(插槽内容跨作用域,scoped 管不到)。 -->
<template>
  <div class="game-header">
    <div class="gh-row">
      <span class="gh-name">{{ title }}</span>
      <div v-if="$slots.settings" class="gh-settings"><slot name="settings" /></div>
      <div v-if="$slots.stats" class="gh-stats"><slot name="stats" /></div>
      <div class="gh-right">
        <slot name="actions" />
        <el-button
          size="small"
          round
          class="gh-rules-btn"
          :type="rulesOpen ? 'primary' : 'default'"
          :aria-expanded="rulesOpen"
          @click="rulesOpen = !rulesOpen"
        >
          <el-icon><Reading /></el-icon>
          <span class="gh-btn-label">{{ $t('games.rulesLabel') }}</span>
        </el-button>
        <el-button
          v-if="fullscreen"
          size="small"
          round
          class="gh-exit-fullscreen"
          :title="$t('games.exitFullscreen')"
          :aria-label="$t('games.exitFullscreen')"
          @click="$emit('exitFullscreen')"
        >
          <el-icon><FullScreen /></el-icon>
        </el-button>
      </div>
    </div>
    <div v-show="rulesOpen" class="gh-rules-panel">
      <slot name="rules" />
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { FullScreen, Reading } from '@element-plus/icons-vue'

defineProps({
  /** 游戏名(页面按对局状态拼好后传入,如「2048 · 一起玩」) */
  title: { type: String, required: true },
  /** 当前是否处于浏览器全屏(全屏时页面顶栏不可见,这里显示退出全屏) */
  fullscreen: { type: Boolean, default: false },
})
defineEmits(['exitFullscreen'])

const rulesOpen = ref(false)
</script>
