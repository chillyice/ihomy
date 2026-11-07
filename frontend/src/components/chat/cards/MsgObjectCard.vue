<!-- 站内对象卡:相册/视频/图书/日记/愿望等快照 + 跳转;对象已删时目标页自有空态 -->
<template>
  <div v-a11y-click class="obj-card" role="link" :title="msg.content" @click="go">
    <img v-if="payload.cover && !coverFailed" :src="payload.cover" :alt="msg.content" class="obj-cover" @error="coverFailed = true" />
    <el-icon v-else class="obj-icon" :size="22"><component :is="iconComp" /></el-icon>
    <div class="obj-main">
      <div class="obj-title">{{ msg.content }}</div>
      <div v-if="payload.sub" class="obj-sub">{{ payload.sub }}</div>
      <div class="obj-kind">{{ kindText }}</div>
    </div>
  </div>
</template>

<script setup>
// 站内对象卡(CARD):payload {objType,objId,url,sub?,cover?};content=标题快照
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Camera, Collection, Document, Film, Reading, Star } from '@element-plus/icons-vue'
import { parsePayload } from '@/utils/chatMsg'
import { useI18n } from 'vue-i18n'

const props = defineProps({
  msg: { type: Object, required: true },
})

const router = useRouter()
const { t } = useI18n()
const coverFailed = ref(false)
const payload = computed(() => parsePayload(props.msg.payload) || {})

const ICONS = {
  ALBUM: Collection,
  PHOTO: Camera,
  VIDEO: Film,
  BLOG: Document,
  DIARY: Document,
  BOOK: Reading,
  WISH: Star,
}
const iconComp = computed(() => ICONS[payload.value.objType] || Document)
const kindText = computed(() => t(`chat.card.objType.${(payload.value.objType || '').toLowerCase()}`, payload.value.objType || ''))

const go = () => {
  const url = payload.value.url
  if (url && url.startsWith('/')) router.push(url)
}
</script>

<style scoped>
.obj-card {
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: min(300px, 100%);
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border);
  cursor: pointer;
  transition: transform var(--dur-fast) var(--ease-soft);
}
.obj-card:hover {
  transform: translateY(-1px);
}
.obj-cover {
  width: 46px;
  height: 46px;
  border-radius: 8px;
  object-fit: cover;
  flex-shrink: 0;
}
.obj-icon {
  width: 46px;
  height: 46px;
  border-radius: 8px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(var(--color-brand-rgb), 0.12);
  color: var(--color-brand-text);
}
.obj-main {
  min-width: 0;
}
.obj-title {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.obj-sub {
  margin-top: 2px;
  font-size: 12px;
  color: var(--color-text-secondary, #888);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.obj-kind {
  margin-top: 3px;
  font-size: 11px;
  color: var(--color-text-tertiary, #aaa);
}
</style>
