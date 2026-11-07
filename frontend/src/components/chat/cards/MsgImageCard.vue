<!-- 图片消息卡:展示站内上传图片(经 /files 鉴权 cookie 加载),附言可选 -->
<template>
  <div class="img-card">
    <img
      v-if="!failed"
      :src="payload.url"
      :alt="$t('chat.card.imageAlt')"
      class="img-card-media"
      loading="lazy"
      draggable="false"
      @error="failed = true"
    />
    <div v-else class="img-card-fallback">{{ $t('chat.card.imageBroken') }}</div>
    <div v-if="msg.content" class="img-card-caption">{{ msg.content }}</div>
  </div>
</template>

<script setup>
// 图片消息(IMAGE):payload {url,width?,height?};加载失败降级占位
import { computed, ref } from 'vue'
import { parsePayload } from '@/utils/chatMsg'

const props = defineProps({
  msg: { type: Object, required: true },
})

const failed = ref(false)
const payload = computed(() => parsePayload(props.msg.payload) || {})
</script>

<style scoped>
.img-card {
  max-width: min(280px, 100%);
}
.img-card-media {
  display: block;
  max-width: 100%;
  max-height: 320px;
  border-radius: 10px;
  object-fit: cover;
}
.img-card-fallback {
  padding: 18px 22px;
  border-radius: 10px;
  background: var(--color-bg-muted, rgba(128, 128, 128, 0.1));
  color: var(--color-text-secondary, #999);
  font-size: 13px;
  text-align: center;
}
.img-card-caption {
  margin-top: 4px;
  font-size: 14px;
  line-height: 1.5;
  word-break: break-word;
  white-space: pre-wrap;
}
</style>
