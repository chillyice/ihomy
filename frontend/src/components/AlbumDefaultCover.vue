<!-- 相册默认封面:无照片也无自定义封面时显示(暖棕文件夹插画,替代 emoji 占位) -->
<template>
  <div class="default-cover" :style="{ fontSize: size + 'px' }">
    <svg viewBox="0 0 64 48" :width="size" :height="size * 0.75" aria-hidden="true">
      <path d="M4 10 Q4 6 8 6 H24 L30 12 H56 Q60 12 60 16 V40 Q60 44 56 44 H8 Q4 44 4 40 Z"
        fill="#e8dcc8" stroke="#c4a884" stroke-width="2" />
      <path d="M4 18 H60 V40 Q60 44 56 44 H8 Q4 44 4 40 Z" fill="#f3eee6" />
      <circle cx="32" cy="30" r="7" fill="none" stroke="#b88c6e" stroke-width="2.5" />
      <circle cx="32" cy="30" r="2.4" fill="#b88c6e" />
      <path d="M32 23 V18 M35.5 24.5 L39 21 M37 30 H42 M35.5 35.5 L39 39 M32 37 V42 M28.5 35.5 L25 39 M27 30 H22 M28.5 24.5 L25 21"
        stroke="#c4a884" stroke-width="1.6" stroke-linecap="round" />
    </svg>
  </div>
</template>

<script setup>
defineProps({ size: { type: Number, default: 64 } })
</script>

<style scoped>
.default-cover {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #f6f1e7, #ece2d0);
}
html.dark .default-cover { background: linear-gradient(135deg, #2a3550, #1E2A48); }
</style>
