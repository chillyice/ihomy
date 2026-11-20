<template>
  <img v-if="src" :key="src" :src="src" :alt="alt" loading="lazy" decoding="async" draggable="false" @error="onError" />
  <AlbumDefaultCover v-else :size="size" />
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import AlbumDefaultCover from './AlbumDefaultCover.vue'
import { albumThumbnail, originalPhotoUrl } from '@/utils/albumImages'

const props = defineProps({ url: String, alt: { type: String, default: '' }, size: { type: Number, default: 36 } })
const src = ref('')
const original = computed(() => originalPhotoUrl(props.url))
watch(
  () => props.url,
  (url) => {
    src.value = albumThumbnail(url)
  },
  { immediate: true },
)
function onError(event) {
  if (event.currentTarget.getAttribute('src') !== src.value) return
  src.value = src.value === original.value ? '' : original.value
}
</script>

<style scoped>
img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
</style>
