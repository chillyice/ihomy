<!-- 页面统一工具栏包装:暖居下滚动后胞吐进顶栏(.gc-pin)实现「膜泡与细胞膜融合,组件扩散进顶栏」;光尘/非暖居原样渲染 -->
<template>
  <Teleport to=".gc-pin" :disabled="!(always || pinned)">
    <div :class="rootClasses" ref="toolbarEl">
      <slot />
    </div>
  </Teleport>
  <!-- 占位:正向瞬时占位+渐隐(内容平滑上移),反向渐显(内容平滑下移),toolbar 回位后瞬时消失;always 时不占位 -->
  <div ref="holderEl" class="toolbar-holder" :style="{ height: holderH + 'px' }"></div>
</template>

<script setup>
import { inject, ref, watch, computed, nextTick } from 'vue'

const props = defineProps({
  card: { type: Boolean, default: true },
  rootClass: { type: String, default: '' },
  holderMargin: { type: Number, default: 12 },
  always: { type: Boolean, default: false },
})

const scrolled = inject('gc-scrolled', ref(false))
const pinned = inject('gc-pinned', ref(false))
const toolbarEl = ref(null)
const holderEl = ref(null)
const holderH = ref(0)

// 根元素 class:rootClass 指定时用它(如 fp-topbar),否则 page-toolbar(+card)
const rootClasses = computed(() => {
  if (props.rootClass) return [props.rootClass]
  return props.card ? ['page-toolbar', 'card'] : ['page-toolbar']
})

const toolbarH = () => (toolbarEl.value?.offsetHeight ?? 0) + props.holderMargin

// 正向:瞬时占位(高度=工具栏高+下边距)抵消内容上移,下一帧渐隐到 0,内容平滑上移
// 反向:渐显到工具栏高度,内容平滑下移(给 toolbar 让位)
watch(scrolled, async (val) => {
  if (props.always) return
  if (val) {
    if (holderEl.value) holderEl.value.style.transition = 'none'
    holderH.value = toolbarH()
    await nextTick()
    requestAnimationFrame(() => {
      if (holderEl.value) holderEl.value.style.transition = 'height .45s cubic-bezier(.4,0,.2,1)'
      holderH.value = 0
    })
  } else {
    if (holderEl.value) holderEl.value.style.transition = 'none'
    holderH.value = 0
    await nextTick()
    requestAnimationFrame(() => {
      if (holderEl.value) holderEl.value.style.transition = 'height .45s cubic-bezier(.4,0,.2,1)'
      holderH.value = toolbarH()
    })
  }
})

// toolbar 回位时(pinned=false),holder 瞬时消失,抵消 toolbar 回来带来的内容下移
watch(pinned, (val) => {
  if (!val && !props.always) {
    if (holderEl.value) holderEl.value.style.transition = 'none'
    holderH.value = 0
  }
})
</script>

<style scoped>
.toolbar-holder { overflow: hidden; }
/* 物品定位顶栏容器样式(原 Item.vue scoped,Teleport 后元素带 PageToolbar data-v,需在此定义) */
.fp-topbar { display: flex; align-items: center; gap: 12px; padding: 10px 16px; }
/* 小游戏页顶栏容器样式(返回+全屏,与物品定位 .fp-topbar 同构) */
.game-topbar { display: flex; align-items: center; gap: 12px; padding: 10px 16px; }
</style>
