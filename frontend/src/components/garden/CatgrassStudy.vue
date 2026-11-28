<template>
  <figure class="grass-study" :style="tileStyle">
    <div
      class="specimen"
      :class="{ leafy: stage >= 2 && !cut, dry: dry && stage > 0 && !cut, wilted: dry && stage >= 4 && !cut }"
      aria-hidden="true"
    >
      <i class="soil"></i><i class="leaves"></i>
    </div>
    <figcaption>{{ label }}</figcaption>
  </figure>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({ stage: Number, cut: Boolean, dry: Boolean, label: String })
// The original study is a four-by-two sheet. The two tall leafy studies share game stage four.
const tileStyle = computed(() => {
  const i = props.cut ? 7 : ([0, 1, 2, 3, 5, 6][props.stage] ?? 0)
  return { '--tile-x': ((i % 4) * 100) / 3 + '%', '--tile-y': i < 4 ? '0%' : '100%' }
})
</script>

<style scoped>
.grass-study {
  --tile-x: 0%;
  --tile-y: 0%;
  position: relative;
  display: grid;
  place-items: end center;
  margin: 0;
  height: clamp(185px, 36dvh, 300px);
  overflow: hidden;
  background: radial-gradient(ellipse at 60% 42%, #f5f0da, #e7e3cd 68%, #d7d5bd);
  border-block: 1px solid #bebda2;
}
.specimen {
  position: relative;
  height: 100%;
  aspect-ratio: 1;
}
.specimen::before {
  content: '';
  position: absolute;
  left: 16%;
  right: 10%;
  bottom: 7%;
  height: 8%;
  background: radial-gradient(ellipse, #71644250, transparent 70%);
}
.specimen i {
  position: absolute;
  inset: 0;
  background: url('@/assets/garden/catgrass-stages.webp') var(--tile-x) var(--tile-y) / 400% 200% no-repeat;
}
.soil {
  clip-path: inset(50% 0 0);
}
.leaves {
  clip-path: inset(0 0 50%);
  transform-origin: 50% 50%;
}
.dry .leaves {
  transform: skewX(5deg) scaleY(0.86);
}
.specimen.wilted i {
  background-image: url('@/assets/garden/catgrass-conditions.webp');
  background-size: 300% 300%;
  background-position: 0 0;
  transform: none;
}
@media (prefers-reduced-motion: no-preference) {
  .leafy:not(.dry) .leaves {
    animation: grass-breeze 3.5s ease-in-out infinite alternate;
  }
  @keyframes grass-breeze {
    from {
      transform: skewX(-1.1deg);
    }
    to {
      transform: skewX(1.6deg);
    }
  }
}
figcaption {
  position: absolute;
  inset: 12px auto auto 14px;
  max-width: 50%;
  padding: 4px 12px 4px 9px;
  background: #fdf7e5;
  border-left: 3px solid #60784d;
  color: #455640;
  font-size: 13px;
  font-weight: 650;
  transform: rotate(-2deg);
}
</style>
