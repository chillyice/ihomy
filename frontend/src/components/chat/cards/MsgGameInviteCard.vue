<!-- 游戏邀请卡:点「一起玩」直达对应游戏页;P4 升级为可加入的对局 -->
<template>
  <div class="invite-card">
    <el-icon class="invite-icon" :size="22"><Trophy /></el-icon>
    <div class="invite-main">
      <div class="invite-title">{{ gameName }}</div>
      <div class="invite-hint">{{ $t('chat.card.inviteHint', { name: msg.senderName || '' }) }}</div>
    </div>
    <el-button v-if="matchId" type="primary" size="small" round @click="router.push(`/games/match/${matchId}`)">
      {{ $t('chat.card.openMatch') }}
    </el-button>
    <el-button v-else-if="game" type="primary" size="small" round @click="router.push(game.route)">
      {{ $t('chat.card.play') }}
    </el-button>
  </div>
</template>

<script setup>
// 游戏邀请(GAME_INVITE):payload {gameCode};content=游戏名快照
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { Trophy } from '@element-plus/icons-vue'
import { gameByCode, parsePayload } from '@/utils/chatMsg'
import { useI18n } from 'vue-i18n'

const props = defineProps({
  msg: { type: Object, required: true },
})

const router = useRouter()
const { t } = useI18n()
const payload = computed(() => parsePayload(props.msg.payload) || {})
const game = computed(() => gameByCode(payload.value.gameCode))
const matchId = computed(() => {
  const n = Number(payload.value.matchId)
  return Number.isFinite(n) && n > 0 ? n : 0
})
const gameName = computed(() => {
  if (game.value) return t(game.value.nameKey)
  return props.msg.content || payload.value.gameCode || ''
})
</script>

<style scoped>
.invite-card {
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: min(300px, 100%);
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(var(--color-brand-rgb), 0.08);
  border: 1px solid rgba(var(--color-brand-rgb), 0.22);
}
.invite-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(var(--color-brand-rgb), 0.14);
  color: var(--color-brand-text);
}
.invite-main {
  min-width: 0;
  flex: 1;
}
.invite-title {
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.invite-hint {
  margin-top: 2px;
  font-size: 12px;
  color: var(--color-text-secondary, #888);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
