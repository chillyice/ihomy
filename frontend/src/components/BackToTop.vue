<!-- 回到顶部按钮:滚动超过阈值时显示,点击平滑滚回页首 -->
<template>
  <transition name="pop">
    <button v-if="visible" class="back-to-top" @click="scrollTop" aria-label="回到顶部">↑</button>
  </transition>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'

const visible = ref(false)
// 光尘主题主区内部滚动(.gc-main),暖居走 window 滚动
const scroller = () => document.querySelector('.gc-main')
const onScroll = () => {
  const el = scroller()
  if (el && el.scrollHeight > el.clientHeight + 10) visible.value = el.scrollTop > 400
  else visible.value = window.scrollY > 400
}
const scrollTop = () => {
  const el = scroller()
  if (el && el.scrollHeight > el.clientHeight + 10) el.scrollTo({ top: 0, behavior: 'smooth' })
  else window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(() => window.addEventListener('scroll', onScroll, { capture: true, passive: true }))
onBeforeUnmount(() => window.removeEventListener('scroll', onScroll, { capture: true }))
</script>

<style scoped>
.back-to-top {
  position: fixed;
  right: 24px;
  bottom: 28px;
  width: 46px;
  height: 46px;
  border: none;
  border-radius: 50%;
  background: var(--color-brand);
  color: var(--color-brand-text);
  font-size: 20px;
  cursor: pointer;
  box-shadow: var(--shadow);
  z-index: 200;
  transition: transform 0.15s, background 0.15s;
}
.back-to-top:hover { background: var(--color-brand-hover); transform: translateY(-2px); }
.pop-enter-active, .pop-leave-active { transition: opacity 0.2s, transform 0.2s; }
.pop-enter-from, .pop-leave-to { opacity: 0; transform: translateY(8px); }
</style>