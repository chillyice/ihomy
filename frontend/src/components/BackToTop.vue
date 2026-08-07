<!-- 回到顶部按钮:滚动超过阈值时显示,点击平滑滚回页首 -->
<template>
  <transition name="pop">
    <button v-if="visible" class="back-to-top" @click="scrollTop" aria-label="回到顶部">↑</button>
  </transition>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'

const visible = ref(false)
// 滚动超过 400px 才显示按钮,避免页面顶部时碍事
const onScroll = () => { visible.value = window.scrollY > 400 }
// 平滑滚动到页首
const scrollTop = () => window.scrollTo({ top: 0, behavior: 'smooth' })

onMounted(() => window.addEventListener('scroll', onScroll, { passive: true }))
onBeforeUnmount(() => window.removeEventListener('scroll', onScroll))
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
  background: var(--color-primary);
  color: #fff;
  font-size: 20px;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(31, 58, 95, 0.3);
  z-index: 200;
  transition: transform 0.15s, background 0.15s;
}
.back-to-top:hover { background: var(--color-accent); transform: translateY(-2px); }
.pop-enter-active, .pop-leave-active { transition: opacity 0.2s, transform 0.2s; }
.pop-enter-from, .pop-leave-to { opacity: 0; transform: translateY(8px); }
</style>