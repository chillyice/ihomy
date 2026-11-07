<!-- 消息卡分发:按 msgType 渲染对应卡片;负载解析失败降级为文本占位(不抛错不打断) -->
<template>
  <component :is="comp" v-if="comp" :msg="msg" />
  <span v-else class="card-fallback">{{ fallbackText }}</span>
</template>

<script setup>
// 分发器:TEXT 由消息列表直接插值,这里只接非文本类型
import { computed } from 'vue'
import { MSG_TYPE } from '@/utils/chatMsg'
import { useI18n } from 'vue-i18n'
import MsgImageCard from './MsgImageCard.vue'
import MsgLinkCard from './MsgLinkCard.vue'
import MsgObjectCard from './MsgObjectCard.vue'
import MsgGameInviteCard from './MsgGameInviteCard.vue'
import MsgCallCard from './MsgCallCard.vue'

const props = defineProps({
  msg: { type: Object, required: true },
})

const { t } = useI18n()

const COMP_MAP = {
  [MSG_TYPE.IMAGE]: MsgImageCard,
  [MSG_TYPE.LINK]: MsgLinkCard,
  [MSG_TYPE.CARD]: MsgObjectCard,
  [MSG_TYPE.GAME_INVITE]: MsgGameInviteCard,
  [MSG_TYPE.CALL]: MsgCallCard,
}

const comp = computed(() => COMP_MAP[props.msg.msgType] || null)
const fallbackText = computed(() => {
  // 负载坏了/类型不认识:降级展示快照文本,空则占位
  return props.msg.content || t('chat.card.unavailable')
})
</script>

<style scoped>
.card-fallback {
  font-size: 14px;
  color: var(--color-text-secondary, #888);
}
</style>
