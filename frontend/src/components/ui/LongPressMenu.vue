<!-- 长按上下文菜单:Teleport 到 body、fixed 跟随指针,长按与行尾「更多」共用同一份 items。
     键盘可 Tab/Enter,Esc / 点空白关闭;层级走 main.css 的 --z-toast(高于弹窗遮罩)。 -->
<template>
  <Teleport to="body">
    <Transition name="lpm-fade">
      <div
        v-if="visible"
        ref="menuRef"
        class="long-press-menu"
        role="menu"
        :style="{ left: pos.x + 'px', top: pos.y + 'px' }"
        @pointerdown.stop
      >
        <button v-for="it in items" :key="it.key" type="button" class="lpm-item" :class="it.type" role="menuitem" @click="pick(it)">
          {{ it.label }}
        </button>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  x: { type: Number, default: 0 },
  y: { type: Number, default: 0 },
  items: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:visible', 'select'])

const menuRef = ref(null)
const pos = ref({ x: 0, y: 0 })

const close = () => emit('update:visible', false)

const pick = (it) => {
  close()
  emit('select', it)
}

const place = () => {
  const el = menuRef.value
  if (!el) return
  const w = el.offsetWidth || 180
  const h = el.offsetHeight || 44
  const maxX = Math.max(8, window.innerWidth - w - 8)
  const maxY = Math.max(8, window.innerHeight - h - 8)
  pos.value = { x: Math.max(8, Math.min(props.x, maxX)), y: Math.max(8, Math.min(props.y, maxY)) }
}

const onKeydown = (e) => {
  if (e.key === 'Escape') {
    e.stopPropagation()
    close()
  }
}

const onDocPointerDown = (e) => {
  if (menuRef.value && menuRef.value.contains(e.target)) return
  close()
}

const onScroll = () => close()

const bind = () => {
  document.addEventListener('keydown', onKeydown, true)
  document.addEventListener('pointerdown', onDocPointerDown, true)
  window.addEventListener('scroll', onScroll, true)
}

const unbind = () => {
  document.removeEventListener('keydown', onKeydown, true)
  document.removeEventListener('pointerdown', onDocPointerDown, true)
  window.removeEventListener('scroll', onScroll, true)
}

watch(
  () => props.visible,
  (v) => {
    unbind()
    if (!v) return
    // 先按锚点落位,避免第一帧从 (0,0) 闪到指针处;渲染后再按视口边界夹紧
    pos.value = { x: props.x, y: props.y }
    nextTick(() => {
      place()
      menuRef.value?.querySelector('.lpm-item')?.focus()
      bind()
    })
  },
)

onBeforeUnmount(unbind)
</script>

<style scoped>
.long-press-menu {
  position: fixed;
  z-index: var(--z-toast);
  min-width: 168px;
  max-width: 240px;
  padding: 6px;
  border-radius: 12px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.18);
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.lpm-item {
  display: block;
  width: 100%;
  padding: 9px 12px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text);
  font-size: 13.5px;
  text-align: left;
  cursor: pointer;
}
.lpm-item:hover,
.lpm-item:focus-visible {
  background: rgba(var(--color-brand-rgb), 0.12);
  outline: none;
}
.lpm-item.danger {
  color: var(--color-danger, #b96058);
}
.lpm-item.danger:hover,
.lpm-item.danger:focus-visible {
  background: rgba(185, 96, 88, 0.12);
}
.lpm-fade-enter-active,
.lpm-fade-leave-active {
  transition:
    opacity 0.14s ease,
    transform 0.14s ease;
}
.lpm-fade-enter-from,
.lpm-fade-leave-to {
  opacity: 0;
  transform: scale(0.96);
}
</style>
