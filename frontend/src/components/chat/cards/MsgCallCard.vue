<!-- 通话记录卡:1:1 语音/视频通话的结果记录(接通/拒绝/未接/取消),P3 通话流程落库 -->
<template>
  <div class="call-card">
    <el-icon class="call-icon" :class="{ missed: isMissed }" :size="18">
      <VideoCamera v-if="media === 'VIDEO'" />
      <Phone v-else />
    </el-icon>
    <div class="call-main">
      <div class="call-title">{{ $t(`chat.card.call.${(payload.result || 'MISSED').toLowerCase()}`) }}</div>
      <div class="call-sub">
        {{ mediaText }}<template v-if="durationText"> · {{ durationText }}</template>
      </div>
    </div>
  </div>
</template>

<script setup>
// 通话记录(CALL):payload {media:AUDIO|VIDEO, result:ANSWERED|REJECTED|MISSED|CANCELLED, duration?:秒}
import { computed } from 'vue'
import { Phone, VideoCamera } from '@element-plus/icons-vue'
import { parsePayload } from '@/utils/chatMsg'
import { useI18n } from 'vue-i18n'

const props = defineProps({
  msg: { type: Object, required: true },
})

const { t } = useI18n()
const payload = computed(() => parsePayload(props.msg.payload) || {})
const media = computed(() => (payload.value.media === 'VIDEO' ? 'VIDEO' : 'AUDIO'))
const mediaText = computed(() => t(`chat.card.call.${media.value.toLowerCase()}`))
const isMissed = computed(() => ['MISSED', 'REJECTED'].includes(payload.value.result))
const durationText = computed(() => {
  const sec = Number(payload.value.duration)
  if (!Number.isFinite(sec) || sec <= 0) return ''
  const m = Math.floor(sec / 60)
  const s = Math.round(sec % 60)
  return m > 0 ? `${m}:${String(s).padStart(2, '0')}` : `0:${String(s).padStart(2, '0')}`
})
</script>

<style scoped>
.call-card {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 2px 0;
}
.call-icon {
  flex-shrink: 0;
  color: var(--color-brand-text);
}
.call-icon.missed {
  color: var(--color-danger, #f56c6c);
}
.call-title {
  font-size: 14px;
  line-height: 1.4;
}
.call-sub {
  margin-top: 1px;
  font-size: 12px;
  color: var(--color-text-secondary, #888);
}
</style>
