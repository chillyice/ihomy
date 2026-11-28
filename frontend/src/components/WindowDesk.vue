<template>
  <Teleport to="body">
    <div
      v-if="sceneAvailable"
      ref="deskLayer"
      @load.capture="syncCasters"
      class="window-desk"
      data-desk-layer
      :inert="modalOpen"
      :aria-hidden="modalOpen || undefined"
    >
      <Transition mode="out-in" :css="false" appear @enter="slideIn" @leave="slideOut" @enter-cancelled="stopSlide">
        <div v-if="placements.length" :key="sceneKey">
          <router-link
            v-for="({ item, x, y, size, cropWidth, angle, edge }, index) in placements"
            :key="item.key"
            v-desk-seat="placements[index]"
            :to="item.to"
            draggable="false"
            @pointerdown="startMove($event, placements[index])"
            @click.capture="guardClick"
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
              '--desk-ink': cropWidth ? paintHalfWidth(item, angle) + '%' : undefined,
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
      </Transition>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, inject, onBeforeUnmount, onUpdated, ref, shallowRef, watch } from 'vue'
import { gsap } from 'gsap'
import { useI18n } from 'vue-i18n'
import { useDeskObjects } from '@/composables/useDeskObjects'
import { useDeskSpace } from '@/composables/useDeskSpace'
import { useDragResize } from '@/utils/useDragResize'
import { useRoute } from 'vue-router'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'

const props = defineProps({
  destinations: { type: Object, default: () => ({}) },
  arrangement: { type: Number, default: 0 },
  plan: { type: Array, required: true },
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
const { placements: seats, modalOpen, sceneAvailable, sceneRevision } = useDeskSpace(drawable, () => props.plan)
// Use the measured seats (before any manual drag). Background refreshes that only
// change a label can patch in place. A new illustration or layout swaps the group.
const sceneKey = computed(() =>
  JSON.stringify([
    sceneRevision.value,
    ...seats.value.map(({ item, x, y, angle, edge }) => [item.key, imageOf(item), item.paperTone, x, y, angle, edge]),
  ]),
)

const route = useRoute()
const temporaryPositions = shallowRef({})
const { pos, size: dragSize, dragging, onDragStart } = useDragResize({ w: 1, h: 1 })
let activeSeat
let ignoreClick = false
let dragRoute
let dragArrangement
watch(seats, () => {
  if (dragRoute === route.fullPath && dragArrangement === arrangement()) return
  temporaryPositions.value = {}
  activeSeat = null
})
function startMove(event, seat) {
  if (event.button !== 0 || event.isPrimary === false) return
  const w = seat.cropWidth || seat.size
  dragSize.value = { w, h: seat.size }
  pos.value = { x: seat.x - w / 2, y: seat.y - seat.size / 2 }
  activeSeat = seat
  dragRoute = route.fullPath
  dragArrangement = arrangement()
  ignoreClick = false
  onDragStart(event)
}
watch(
  pos,
  (point) => {
    if (!dragging.value || !activeSeat) return
    const { width, size, cropWidth, item } = activeSeat
    temporaryPositions.value = {
      ...temporaryPositions.value,
      [item.key]: { x: (point.x + (cropWidth || size) / 2) / width, y: point.y + size / 2 },
    }
  },
  { flush: 'sync' },
)
watch(
  dragging,
  (moving) => {
    if (moving) return
    activeSeat = null
    ignoreClick = true
  },
  { flush: 'sync' },
)
function guardClick(event) {
  if (ignoreClick && event.detail !== 0) {
    event.preventDefault()
    event.stopImmediatePropagation()
  }
  ignoreClick = false
}
const placements = computed(() =>
  seats.value.map((seat) => {
    const point = temporaryPositions.value[seat.item.key]
    if (!point) return seat
    const { size, cropWidth, width, height, navRight } = seat
    const edge = point.x * width < (width + navRight) / 2 ? 'left' : 'right'
    const half = (cropWidth || size) / 2
    const x = cropWidth
      ? edge === 'left'
        ? navRight + half
        : width - half
      : Math.max(navRight + half, Math.min(width - half, point.x * width))
    const y = Math.max(size / 2, Math.min(height - size / 2, point.y))
    return { ...seat, edge, x, y }
  }),
)
const sunLight = inject(SUN_LIGHT_KEY, null)
const deskLayer = ref(null)
const elementSeats = new WeakMap()
const vDeskSeat = (node, binding) => elementSeats.set(node, binding.value)
let slide
function stopSlide() {
  slide?.kill()
  slide = null
}
function slideGroup(group, entering, done) {
  stopSlide()
  group.inert = true
  const nodes = [...group.children]
  const finish = () => {
    group.inert = !entering
    slide = null
    done()
    syncCasters()
  }
  if (document.hidden || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    // Vue must mount the out-in placeholder before completing a zero-duration leave.
    queueMicrotask(finish)
    return
  }
  const width = document.documentElement.clientWidth
  const outside = (_, node) => {
    const { x, edge, size, cropWidth } = elementSeats.get(node)
    const margin = (cropWidth ? cropWidth / 2 : size * 0.72) + 4
    return (edge === 'left' ? -x - margin : width - x + margin) + 'px'
  }
  if (entering) gsap.set(nodes, { '--desk-shift': outside })
  slide = gsap.to(nodes, {
    '--desk-shift': entering ? '0px' : outside,
    duration: entering ? 0.48 : 0.32,
    ease: entering ? 'power3.out' : 'power2.in',
    onComplete: finish,
  })
  syncCasters()
}
const slideIn = (group, done) => slideGroup(group, true, done)
const slideOut = (group, done) => slideGroup(group, false, done)

// Share the same decoded images and placements that actually made it onto the desk.
// The projection canvas paints their shadows from its interpolated solar position.
function syncCasters() {
  if (!sunLight?.deskCasters) return
  const casters = []
  if (deskLayer.value && !modalOpen.value) {
    for (const node of deskLayer.value.querySelectorAll('.desk-object')) {
      const seat = elementSeats.get(node)
      if (!seat) continue
      const { item, angle, x, y, size, cropWidth, edge } = seat
      const image = node.querySelector('img')
      if (!image?.complete || !image.naturalWidth) continue
      const halfInk = (size * paintHalfWidth(item, angle)) / 100
      const centerX = cropWidth ? x + (edge === 'left' ? 1 : -1) * (cropWidth / 2 - halfInk) : x
      casters.push({ kind: item.kind, image, node, x: centerX, y, size, angle, cropLeft: x - cropWidth / 2, cropWidth })
    }
  }
  sunLight.deskCasters.value = casters
}
onUpdated(syncCasters)
onBeforeUnmount(() => {
  stopSlide()
  if (sunLight?.deskCasters) sunLight.deskCasters.value = []
})

// Half-width of the visible ink at 0° and 30°, in percent of the square sprite.
const paintHalfWidths = {
  bookmark: [12, 30],
  coffee: [41, 41],
  tea: [37, 37],
  envelope: [40, 46],
  fruit: [34, 36],
  'fruit-peach': [36, 41],
  'fruit-pear': [32, 33],
  journal: [27, 43],
  'journal-linen': [27, 43],
  'journal-floral': [28, 43],
  'leaf-autumn': [42, 44],
  'leaf-spring': [23, 31],
  'leaf-summer': [37, 39],
  'leaf-winter': [28, 35],
  note: [37, 51],
  'note-grid': [34, 46],
  'note-sage': [36, 50],
  phone: [21, 35],
  photo: [42, 61],
  recipe: [38, 46],
  record: [36, 51],
}
function paintHalfWidth(item, angle) {
  const [upright, tilted] = paintHalfWidths[item.art ?? item.kind] ?? [40, 50]
  return upright + (tilted - upright) * Math.min(1, Math.abs(angle) / 30)
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
}
.desk-object {
  --desk-angle: 0deg;
  --desk-size: 100%;
  --desk-ink: 30%;
  position: absolute;
  aspect-ratio: 1;
  --desk-shift: 0px;
  transform: translate(calc(-50% + var(--desk-shift)), -50%);
  display: grid;
  place-items: center;
  border-radius: 12%;
  color: var(--color-text);
  text-decoration: none;
  pointer-events: auto;
  touch-action: none;
  user-select: none;
  cursor: grab;
}
.desk-object:active {
  cursor: grabbing;
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
  pointer-events: none;
  user-select: none;
  transition: transform 160ms ease;
}
.desk-object:is(:hover, :focus-visible) .desk-illustration {
  transform: translateY(-2px) rotate(var(--desk-angle));
}
html.dark .desk-illustration {
  filter: brightness(0.92) saturate(0.92);
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
  .desk-hint {
    transition: none;
  }
  .desk-object:is(:hover, :focus-visible) .desk-illustration {
    transform: rotate(var(--desk-angle));
  }
}
</style>
