<template>
  <Teleport to="body">
    <div v-if="placements.length || pawTrail.length" class="window-desk" data-desk-layer>
      <router-link
        v-for="{ item, x, y, size, angle } in placements"
        :key="item.key"
        :to="item.to"
        :aria-label="t(item.labelKey, item.labelParams || {})"
        class="desk-object"
        :class="{ 'round-object': item.kind === 'cup' }"
        :style="{ left: x + 'px', top: y + 'px', width: size + 'px', '--desk-angle': angle + 'deg' }"
      >
        <span class="desk-illustration" aria-hidden="true">
          <span v-if="item.kind === 'photo'" class="instant-print">
            <img :src="item.src" alt="" draggable="false" decoding="async" @error="hideMissingImage(item)" />
          </span>
          <span v-else-if="item.kind === 'note'" class="memo-paper" :style="paperStyle(item)">
            <img :src="imageOf(item)" alt="" draggable="false" @error="hideMissingImage(item)" />
            <img v-if="item.ageDays > 0" class="memo-fold" :src="imageOf(item)" alt="" draggable="false" />
          </span>
          <template v-else>
            <img :src="imageOf(item)" alt="" draggable="false" @error="hideMissingImage(item)" />
            <span v-if="item.kind === 'cup'" class="desk-steam"><i></i><i></i></span>
          </template>
        </span>
        <span class="desk-hint" aria-hidden="true">{{ t(item.labelKey, item.labelParams || {}) }}</span>
      </router-link>
      <span
        v-for="(step, index) in pawTrail"
        :key="index"
        class="desk-paw"
        aria-hidden="true"
        :style="{ left: step.x + 'px', top: step.y + 'px', rotate: step.angle + 'deg', animationDelay: step.delay + 's' }"
      >
        <i></i><i></i><i></i><i></i>
      </span>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, shallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { useDeskObjects } from '@/composables/useDeskObjects'
import { useDeskSpace } from '@/composables/useDeskSpace'

const props = defineProps({
  destinations: { type: Object, default: () => ({}) },
  arrangement: { type: Number, default: 0 },
  tracksOnly: { type: Boolean, default: false },
})
const { t } = useI18n()
const artFiles = import.meta.glob('../assets/windowdesk/*.webp', { eager: true, import: 'default', query: '?url&no-inline' })
const imageOf = (item) => (item.kind === 'photo' ? item.src : artFiles[`../assets/windowdesk/${item.art}.webp`])
const missingImages = shallowRef(new Set())
const hideMissingImage = (item) => {
  missingImages.value = new Set(missingImages.value).add(imageOf(item))
}
const arrangement = () => props.arrangement
const { candidates } = useDeskObjects(
  () => !props.tracksOnly,
  () => props.destinations,
  arrangement,
)
const drawable = computed(() =>
  props.tracksOnly ? [] : candidates.value.filter((item) => imageOf(item) && !missingImages.value.has(imageOf(item))),
)
const { placements, pawTrail } = useDeskSpace(drawable, true, arrangement)

function paperStyle(item) {
  const wear = Math.min(1, (item.ageDays || 0) / 10)
  const hue = [0, 27, -24][item.paperTone] || 0
  return {
    filter: `hue-rotate(${hue}deg) sepia(${wear * 0.4}) brightness(${1 - wear * 0.12})`,
    '--desk-fold-scale': 1 + wear * 0.55,
  }
}
</script>

<style scoped>
.window-desk {
  position: fixed;
  inset: 0;
  z-index: var(--z-content);
  overflow: clip;
  pointer-events: none;
  contain: layout style;
  --desk-shadow: rgba(57, 42, 24, 0.19);
}
html.dark .window-desk {
  --desk-shadow: rgba(8, 6, 4, 0.34);
}
.desk-object {
  --desk-angle: 0deg;
  position: absolute;
  aspect-ratio: 1;
  transform: translate(-50%, -50%);
  display: grid;
  place-items: center;
  border-radius: 12%;
  color: var(--color-text);
  text-decoration: none;
  pointer-events: auto;
  touch-action: manipulation;
}
.desk-object.round-object {
  border-radius: 50%;
}
.desk-object:focus-visible {
  outline: 2px solid var(--leaf-accent);
  outline-offset: -2px;
}
.desk-illustration {
  position: relative;
  display: grid;
  place-items: center;
  width: 100%;
  aspect-ratio: 1;
  transform: rotate(var(--desk-angle));
  filter: drop-shadow(1px 2px 2px var(--desk-shadow));
  pointer-events: none;
  user-select: none;
  transition: transform 160ms ease;
}
.desk-object:is(:hover, :focus-visible) .desk-illustration {
  transform: translateY(-2px) rotate(var(--desk-angle));
}
html.dark .desk-illustration {
  filter: brightness(0.92) saturate(0.92) drop-shadow(1px 2px 2px var(--desk-shadow));
}
.desk-illustration img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.instant-print {
  display: block;
  width: 84%;
  padding: 5% 5% 14%;
  box-sizing: border-box;
  background: #f0e9da;
  border-radius: 1.5%;
  box-shadow: inset 0 0 0 1px rgba(109, 87, 47, 0.16);
}
.instant-print img {
  aspect-ratio: 1;
  height: auto;
  object-fit: cover;
  background: #d3c9b4;
}
.memo-paper {
  --desk-fold-scale: 1;
  position: relative;
  width: 100%;
  height: 100%;
}
/* This section of the original painting contains the turned paper corner. */
.memo-fold {
  position: absolute;
  inset: 0;
  clip-path: polygon(63% 88%, 74% 77%, 80% 75%, 86.8% 65%, 86.8% 88%);
  transform-origin: 86.8% 88%;
  transform: scale(var(--desk-fold-scale));
}
.desk-hint {
  position: absolute;
  bottom: 1%;
  max-width: 100%;
  box-sizing: border-box;
  padding: 3px 6px;
  border: 1px solid var(--color-border);
  border-radius: 5px;
  background: var(--color-card);
  color: var(--color-text);
  font-size: 11px;
  line-height: 1.4;
  text-align: center;
  overflow-wrap: anywhere;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  opacity: 0;
  pointer-events: none;
  transition: opacity 120ms;
}
.desk-object:is(:hover, :focus-visible) .desk-hint {
  opacity: 1;
}
.desk-steam {
  position: absolute;
  top: 31%;
  left: 45%;
  width: 13%;
  height: 19%;
}
.desk-steam i {
  position: absolute;
  left: 10%;
  bottom: 0;
  width: 64%;
  height: 88%;
  border-left: 1.5px solid rgba(255, 246, 221, 0.76);
  border-radius: 70% 30%;
  opacity: 0;
  animation: lift-steam 5.2s ease-out infinite;
}
.desk-steam i + i {
  left: 48%;
  height: 70%;
  animation-delay: -2.2s;
}
@keyframes lift-steam {
  0% {
    opacity: 0;
    transform: translate(0, 22%) scaleY(0.65);
  }
  24% {
    opacity: 0.65;
  }
  72%,
  100% {
    opacity: 0;
    transform: translate(22%, -58%) scaleY(1.1);
  }
}
.desk-paw {
  position: absolute;
  width: 16px;
  height: 17px;
  translate: -50% -50%;
  color: var(--color-text-secondary);
  opacity: 0;
  animation: paw-step 3.8s ease-in-out both;
}
html:is(.kada-page, .bitpouch-page) .desk-paw {
  color: light-dark(#6d604d, #c2b9a2);
}
.desk-paw::after,
.desk-paw i {
  position: absolute;
  background: currentColor;
}
.desk-paw::after {
  content: '';
  left: 21%;
  bottom: 2%;
  width: 61%;
  height: 48%;
  border-radius: 58% 58% 41% 41%;
}
.desk-paw i {
  width: 21%;
  height: 31%;
  border-radius: 50%;
}
.desk-paw i:nth-child(1) {
  left: 0;
  top: 25%;
  rotate: -26deg;
}
.desk-paw i:nth-child(2) {
  left: 24%;
  top: 3%;
  rotate: -10deg;
}
.desk-paw i:nth-child(3) {
  right: 24%;
  top: 3%;
  rotate: 10deg;
}
.desk-paw i:nth-child(4) {
  right: 0;
  top: 25%;
  rotate: 26deg;
}
@keyframes paw-step {
  0%,
  100% {
    opacity: 0;
  }
  16%,
  51% {
    opacity: 0.48;
  }
}
@media (prefers-reduced-motion: reduce) {
  .desk-steam,
  .desk-paw {
    display: none;
  }
  .desk-illustration,
  .desk-hint {
    transition: none;
  }
  .desk-object:is(:hover, :focus-visible) .desk-illustration {
    transform: rotate(var(--desk-angle));
  }
}
</style>
