<!-- 头像右下角在线状态点:ONLINE绿/BUSY红/AWAY黄/OFFLINE灰,slot 放头像本身 -->
<template>
  <span class="presence-wrap" :title="label">
    <slot />
    <i class="presence-dot" :class="`is-${presenceKey(status)}`" :style="{ '--presence-size': `${size}px` }" />
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { dictText } from '@/utils/dict'
import { presenceKey } from '@/utils/presence'

const props = defineProps({
  /** ONLINE / BUSY / AWAY / OFFLINE */
  status: { type: String, default: 'OFFLINE' },
  /** 圆点直径(px):24px 头像用 8,40px 头像用 10 */
  size: { type: Number, default: 10 },
})

const { t } = useI18n()
const label = computed(() => dictText(t, 'user_presence', props.status))
</script>

<style scoped>
.presence-wrap {
  position: relative;
  display: inline-flex;
  flex: none;
}
.presence-dot {
  position: absolute;
  right: -1px;
  bottom: -1px;
  width: var(--presence-size);
  height: var(--presence-size);
  border-radius: 50%;
  /* 描边与承载面同色,避免圆点直接压在头像边缘上显得脏 */
  box-shadow: 0 0 0 2px var(--color-card);
}
.presence-dot.is-online {
  background: var(--presence-online);
}
.presence-dot.is-busy {
  background: var(--presence-busy);
}
.presence-dot.is-away {
  background: var(--presence-away);
}
.presence-dot.is-offline {
  background: var(--presence-offline);
}
</style>
