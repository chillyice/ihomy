<template>
  <Teleport to="body">
    <div v-if="placements.length" class="window-desk" data-desk-layer :inert="modalOpen" :aria-hidden="modalOpen || undefined">
      <router-link
        v-for="{ item, x, y, size, cropWidth, angle, edge } in placements"
        :key="item.key"
        :to="item.to"
        :aria-label="t(item.labelKey, item.labelParams || {})"
        :tabindex="modalOpen ? -1 : undefined"
        class="desk-object"
        :class="{ 'round-object': item.kind === 'cup' && !cropWidth, 'edge-crop': cropWidth }"
        :data-edge="edge"
        :style="{
          left: x + 'px',
          top: y + 'px',
          width: (cropWidth || size) + 'px',
          height: size + 'px',
          '--desk-size': size + 'px',
          '--desk-angle': angle + 'deg',
          '--desk-ink': cropWidth ? paintHalfExtent(item, angle, 'width') + '%' : undefined,
          '--desk-cast': litShadows.get(item.key),
        }"
      >
        <span class="desk-crop" aria-hidden="true">
          <span class="desk-illustration">
            <span v-if="item.kind === 'photo'" class="instant-print">
              <img :src="item.src" alt="" draggable="false" decoding="async" @error="hideMissingImage(item)" />
            </span>
            <span v-else-if="item.kind === 'note'" class="memo-paper" :style="paperStyle(item)">
              <img v-bind="illustrationSource(item, size)" alt="" draggable="false" @error="hideMissingImage(item)" />
              <img v-if="item.ageDays > 0" class="memo-fold" v-bind="illustrationSource(item, size)" alt="" draggable="false" />
            </span>
            <template v-else>
              <img v-bind="illustrationSource(item, size)" alt="" draggable="false" @error="hideMissingImage(item)" />
              <span v-if="item.kind === 'cup'" class="desk-steam"><i></i><i></i></span>
            </template>
          </span>
        </span>
        <span class="desk-hint" aria-hidden="true">{{ t(item.labelKey, item.labelParams || {}) }}</span>
      </router-link>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, inject, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { useDeskObjects } from '@/composables/useDeskObjects'
import { useDeskSpace } from '@/composables/useDeskSpace'
import { apertureCoverage, castShadow, projectionQuad } from '@/utils/deskLight'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'

const props = defineProps({
  destinations: { type: Object, default: () => ({}) },
  arrangement: { type: Number, default: 0 },
})
const { t } = useI18n()
const artFiles = import.meta.glob('../assets/windowdesk/*.webp', { eager: true, import: 'default', query: '?url&no-inline' })
const imageOf = (item) => (item.kind === 'photo' ? item.src : artFiles[`../assets/windowdesk/${item.art}.webp`])
const hdArtFiles = import.meta.glob('../assets/windowdesk/hd/*.webp', { eager: true, import: 'default', query: '?url&no-inline' })
function illustrationSource(item, size) {
  const src = imageOf(item)
  const hd = hdArtFiles[`../assets/windowdesk/hd/${item.art}.webp`]
  const width = item.kind === 'leaf' ? 168 : 268
  return { src, srcset: `${src} ${width}w, ${hd} ${width * 3}w`, sizes: `${size}px` }
}
const missingImages = shallowRef(new Set())
const hideMissingImage = (item) => {
  missingImages.value = new Set(missingImages.value).add(imageOf(item))
}
const arrangement = () => props.arrangement
const { candidates } = useDeskObjects(true, () => props.destinations, arrangement)
const drawable = computed(() => candidates.value.filter((item) => imageOf(item) && !missingImages.value.has(imageOf(item))))
const { placements, modalOpen } = useDeskSpace(drawable, arrangement)

const sunLight = inject(SUN_LIGHT_KEY, null)
const viewport = ref({ width: window.innerWidth || 0, height: window.innerHeight || 0 })
const syncViewport = () => {
  viewport.value = { width: window.innerWidth || 0, height: window.innerHeight || 0 }
}
onMounted(() => window.addEventListener('resize', syncViewport))
onBeforeUnmount(() => window.removeEventListener('resize', syncViewport))

const litShadows = computed(() => {
  const shadows = new Map()
  const scene = sunLight?.sunScene?.value
  const strength = (sunLight?.lightLayerOpacity?.value ?? 0) * (sunLight?.shadowAlpha?.value ?? 0)
  if (!scene?.hasDirectLight || strength <= 0 || !placements.value.length) return shadows
  const realWeight = sunLight.projectionStandard.value === 'real' ? 1 : 0
  const quad = projectionQuad(viewport.value.width, viewport.value.height, scene, realWeight)
  for (const seat of placements.value) {
    const coverage = apertureCoverage(quad, visiblePaintRect(seat))
    if (coverage <= 0) continue
    const shadow = castShadow(scene, seat.size, strength * coverage, seat.item.kind)
    if (shadow) {
      shadows.set(
        seat.item.key,
        `drop-shadow(${shadow.x.toFixed(1)}px ${shadow.y.toFixed(1)}px ${shadow.blur.toFixed(1)}px ${shadow.color})`,
      )
    }
  }
  return shadows
})

// Visible ink extents as percentages of the square sprite: half width at 0°/30°, then half height at 0°/30°.
const paintHalfExtents = {
  bookmark: [12, 30, 45, 42],
  coffee: [41, 41, 42, 42],
  tea: [37, 37, 37, 37],
  envelope: [40, 46, 24, 40],
  fruit: [34, 36, 34, 34],
  'fruit-peach': [36, 41, 36, 39],
  'fruit-pear': [32, 33, 33, 34],
  journal: [27, 43, 43, 48],
  'leaf-autumn': [42, 44, 42, 41],
  'leaf-spring': [23, 31, 42, 37],
  'leaf-summer': [37, 39, 41, 38],
  'leaf-winter': [28, 35, 39, 41],
  note: [37, 51, 39, 52],
  phone: [21, 35, 41, 43],
  photo: [42, 61, 46.5, 61],
  recipe: [38, 46, 33, 43],
  record: [36, 51, 43, 54],
}
function paintHalfExtent(item, angle, dimension) {
  const extents = paintHalfExtents[item.art ?? item.kind] ?? [40, 50, 40, 50]
  const i = dimension === 'width' ? 0 : 2
  return extents[i] + (extents[i + 1] - extents[i]) * Math.min(1, Math.abs(angle) / 30)
}

function visiblePaintRect({ item, size, angle, x, y, cropWidth, edge }) {
  const halfWidth = (size * paintHalfExtent(item, angle, 'width')) / 100
  const halfHeight = (size * paintHalfExtent(item, angle, 'height')) / 100
  let left = x - halfWidth
  let right = x + halfWidth
  if (cropWidth) {
    // The ink is flush with the inner edge of the crop, as positioned by --desk-ink.
    const width = Math.min(2 * halfWidth, cropWidth)
    left = edge === 'left' ? x + cropWidth / 2 - width : x - cropWidth / 2
    right = left + width
  }
  return { left, right, top: y - halfHeight, bottom: y + halfHeight }
}

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
  z-index: var(--z-desk);
  overflow: clip;
  pointer-events: none;
  contain: layout style;
  --desk-shadow: rgba(57, 42, 24, 0.19);
  --desk-cast: none;
}
html.dark .window-desk {
  --desk-shadow: rgba(8, 6, 4, 0.34);
}
.desk-object {
  --desk-angle: 0deg;
  --desk-size: 100%;
  --desk-ink: 30%;
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
.desk-crop {
  position: absolute;
  inset: -25% 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  align-items: center;
  pointer-events: none;
  /* Cast the clipped silhouette in screen coordinates, after the sprite has been rotated. */
  filter: var(--desk-cast);
  transition: filter 600ms ease;
}
.edge-crop .desk-crop {
  overflow: clip;
}
.edge-crop[data-edge='left'] .desk-illustration {
  justify-self: end;
  translate: calc(50% - var(--desk-ink)) 0;
}
.edge-crop[data-edge='right'] .desk-illustration {
  justify-self: start;
  translate: calc(var(--desk-ink) - 50%) 0;
}
.window-desk[aria-hidden='true'] .desk-object {
  pointer-events: none;
}
.desk-object:focus-visible {
  outline: 2px solid var(--leaf-accent);
  outline-offset: -2px;
}
.desk-illustration {
  position: relative;
  display: grid;
  place-items: center;
  width: var(--desk-size);
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
.desk-object[data-edge] .desk-hint {
  bottom: auto;
  top: 50%;
  transform: translateY(-50%);
  width: max-content;
  max-width: 10em;
}
.desk-object[data-edge='left'] .desk-hint {
  left: 100%;
}
.desk-object[data-edge='right'] .desk-hint {
  right: 100%;
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
@media (prefers-reduced-motion: reduce) {
  .desk-steam {
    display: none;
  }
  .desk-illustration,
  .desk-crop,
  .desk-hint {
    transition: none;
  }
  .desk-object:is(:hover, :focus-visible) .desk-illustration {
    transform: rotate(var(--desk-angle));
  }
}
</style>
