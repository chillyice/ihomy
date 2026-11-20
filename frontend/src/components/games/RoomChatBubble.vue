<!-- 房间聊天头像气泡:贴在玩家头像的四个方向(对家下 / 上家右 / 下家左 / 自己上)。
     --rcb-k 用于反缩放:麻将牌桌整体走 transform: scale(--mj-scale),牌桌内把 --rcb-k 设为 1/scale,
     气泡的字号/内距/阴影一起还原成屏幕尺寸,缩小的牌桌里也看得清。 -->
<template>
  <Transition name="rcb-fade">
    <span v-if="text" class="rcb" :class="`rcb-${dir}`" role="status">{{ text }}</span>
  </Transition>
</template>

<script setup>
defineProps({
  text: { type: String, default: '' },
  /** above|below|left|right:气泡位于头像的哪一侧(箭头指向头像) */
  dir: { type: String, default: 'above' },
})
</script>

<style scoped>
.rcb {
  position: absolute;
  z-index: 3;
  width: max-content;
  max-width: calc(150px * var(--rcb-k, 1));
  padding: calc(3px * var(--rcb-k, 1)) calc(8px * var(--rcb-k, 1));
  border-radius: calc(10px * var(--rcb-k, 1));
  border: 1px solid var(--color-border);
  background: var(--color-card);
  color: var(--color-text);
  box-shadow: 0 calc(4px * var(--rcb-k, 1)) calc(12px * var(--rcb-k, 1)) rgba(0, 0, 0, 0.18);
  font-size: calc(12px * var(--rcb-k, 1));
  line-height: 1.35;
  font-weight: 400;
  letter-spacing: 0;
  text-align: left;
  white-space: normal;
  word-break: break-word;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  pointer-events: none;
}
.rcb::after {
  content: '';
  position: absolute;
  border: calc(5px * var(--rcb-k, 1)) solid transparent;
}
.rcb-above {
  bottom: calc(100% + calc(5px * var(--rcb-k, 1)));
  left: 50%;
  transform: translateX(-50%);
}
.rcb-above::after {
  top: 100%;
  left: 50%;
  margin-left: calc(-5px * var(--rcb-k, 1));
  border-top-color: var(--color-card);
}
.rcb-below {
  top: calc(100% + calc(5px * var(--rcb-k, 1)));
  left: 50%;
  transform: translateX(-50%);
}
.rcb-below::after {
  bottom: 100%;
  left: 50%;
  margin-left: calc(-5px * var(--rcb-k, 1));
  border-bottom-color: var(--color-card);
}
.rcb-right {
  left: calc(100% + calc(5px * var(--rcb-k, 1)));
  top: 50%;
  transform: translateY(-50%);
}
.rcb-right::after {
  right: 100%;
  top: 50%;
  margin-top: calc(-5px * var(--rcb-k, 1));
  border-right-color: var(--color-card);
}
.rcb-left {
  right: calc(100% + calc(5px * var(--rcb-k, 1)));
  top: 50%;
  transform: translateY(-50%);
}
.rcb-left::after {
  left: 100%;
  top: 50%;
  margin-top: calc(-5px * var(--rcb-k, 1));
  border-left-color: var(--color-card);
}
.rcb-fade-enter-active,
.rcb-fade-leave-active {
  transition: opacity 0.18s ease;
}
.rcb-fade-enter-from,
.rcb-fade-leave-to {
  opacity: 0;
}
</style>
