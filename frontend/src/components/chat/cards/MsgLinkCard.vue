<!-- 站外链接卡:标题/描述/域名 + 缩略图(浏览器直连 + no-referrer,拍板 #6);新窗口打开 -->
<template>
  <a v-a11y-click class="link-card" :href="url" target="_blank" rel="noopener noreferrer" @click.stop>
    <div class="link-main">
      <div class="link-title">{{ payload.title || url }}</div>
      <div v-if="payload.description" class="link-desc">{{ payload.description }}</div>
      <div class="link-host">{{ host }}</div>
    </div>
    <img
      v-if="payload.image && !thumbFailed"
      :src="payload.image"
      :alt="$t('chat.card.linkThumbAlt')"
      class="link-thumb"
      loading="lazy"
      referrerpolicy="no-referrer"
      @error="thumbFailed = true"
    />
  </a>
</template>

<script setup>
// 站外链接(LINK):payload {title?,description?,image?,siteName?};content=URL
import { computed, ref } from 'vue'
import { parsePayload } from '@/utils/chatMsg'

const props = defineProps({
  msg: { type: Object, required: true },
})

const thumbFailed = ref(false)
const url = computed(() => props.msg.content || '')
const payload = computed(() => parsePayload(props.msg.payload) || {})
const host = computed(() => {
  try {
    return payload.value.siteName || new URL(url.value).hostname
  } catch {
    return url.value
  }
})
</script>

<style scoped>
.link-card {
  display: flex;
  gap: 10px;
  max-width: min(320px, 100%);
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--color-bg-muted, rgba(128, 128, 128, 0.08));
  border: 1px solid var(--color-border-light, rgba(128, 128, 128, 0.18));
  text-decoration: none;
  color: inherit;
  cursor: pointer;
  transition: transform var(--dur-fast) var(--ease-soft);
}
.link-card:hover {
  transform: translateY(-1px);
}
.link-main {
  min-width: 0;
  flex: 1;
}
.link-title {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}
.link-desc {
  margin-top: 3px;
  font-size: 12px;
  color: var(--color-text-secondary, #888);
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}
.link-host {
  margin-top: 5px;
  font-size: 11px;
  color: var(--color-text-tertiary, #aaa);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.link-thumb {
  width: 72px;
  height: 72px;
  flex-shrink: 0;
  border-radius: 8px;
  object-fit: cover;
  align-self: center;
}
</style>
