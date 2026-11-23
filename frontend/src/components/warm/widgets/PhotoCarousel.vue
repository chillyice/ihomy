<!-- 暖居相册的三种布局共享切换方向、手势与自动播放。 -->
<template>
  <h3 class="gc-card-h3">
    {{ $t('warm.widget.photos') }}<button class="gc-more" @click.stop="openAlbum">{{ $t('warm.albumArrow') }}</button>
  </h3>
  <div
    v-if="current"
    class="pcs"
    :class="[`pcs-${variant}`, { 'is-editing': editMode }]"
    @pointerenter="hovered = $event.pointerType === 'mouse'"
    @pointerleave="hovered = false"
    @focusin="focused = $event.target.matches(':focus-visible')"
    @focusout="focused = false"
    @keydown.stop
  >
    <button
      type="button"
      class="pcs-surface"
      :aria-label="variant === 'carousel' ? $t('warm.albumArrow') : $t('warm.nextPhoto')"
      :disabled="editMode || (variant !== 'carousel' && slides.length < 2)"
      @click.stop="activate"
      @pointerdown="startSwipe"
      @pointermove="moveSwipe"
      @pointerup="endSwipe"
      @pointercancel="cancelSwipe"
      @lostpointercapture.self="cancelSwipe"
      @pointerleave="!$event.currentTarget.hasPointerCapture($event.pointerId) && cancelSwipe($event)"
      @keydown.left.prevent="prev"
      @keydown.right.prevent="next"
    >
      <Transition
        :name="direction === 1 ? 'pcs-forward' : 'pcs-back'"
        :css="!editMode && !reduced"
        @before-enter="setReleaseOffset"
        @before-leave="setReleaseOffset"
      >
        <span :key="current.id ?? current.url" class="pcs-viewport">
          <span class="pcs-strip">
            <span
              v-for="frame in frames"
              :key="frame.offset"
              class="pcs-frame"
              :style="{ left: `${frame.offset * 100}%` }"
              :aria-hidden="frame.offset !== 0"
            >
              <span v-for="(photo, i) in frame.photos" :key="photo.id ?? photo.url" class="pcs-print" :style="stackStyle(i)">
                <img :src="photo.url" :alt="photo.description || ''" draggable="false" />
              </span>
            </span>
          </span>
        </span>
      </Transition>
    </button>
    <div class="pcs-meta">
      <span class="pcs-cap">{{ caption }}</span>
      <span class="pcs-count">{{ index + 1 }} / {{ slides.length }}</span>
    </div>
    <template v-if="slides.length > 1 && !editMode">
      <button class="pcs-nav prev" :aria-label="$t('warm.prevPhoto')" @click.stop="prev">
        <svg viewBox="0 0 24 24"><path d="M15 5l-7 7 7 7" /></svg>
      </button>
      <button class="pcs-nav next" :aria-label="$t('warm.nextPhoto')" @click.stop="next">
        <svg viewBox="0 0 24 24"><path d="M9 5l7 7-7 7" /></svg>
      </button>
      <div v-if="variant === 'carousel' && slides.length <= 12" class="pcs-dots">
        <button
          v-for="(p, i) in slides"
          :key="p.id ?? p.url"
          class="pcs-dot"
          :class="{ on: i === index }"
          :aria-label="String(i + 1)"
          :aria-current="i === index"
          @click.stop="go(i)"
        ></button>
      </div>
    </template>
  </div>
  <div v-else class="gc-empty gc-empty-link" v-a11y-click @click="openAlbum">
    {{ $t('warm.addPhoto') }}
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useSlideShow } from '@/composables/useSlideShow'
import { formatDate } from '@/utils/datetime'

const props = defineProps({
  photos: { type: Array, default: () => [] },
  editMode: { type: Boolean, default: false },
  variant: { type: String, default: 'carousel' },
})

const router = useRouter()
const { t } = useI18n()
const openAlbum = () => {
  if (!props.editMode) router.push('/album')
}
const slides = computed(() => {
  const photos = props.photos || []
  return props.variant === 'carousel' ? photos.slice(0, 30) : photos
})
const focused = ref(false)
const interacting = ref(false)
const reducedQuery = window.matchMedia?.('(prefers-reduced-motion: reduce)')
const reduced = ref(Boolean(reducedQuery?.matches))
const visible = ref(!document.hidden)
const syncReduced = () => {
  reduced.value = reducedQuery.matches
}
const syncVisible = () => {
  visible.value = !document.hidden
}

onMounted(() => {
  reducedQuery?.addEventListener('change', syncReduced)
  document.addEventListener('visibilitychange', syncVisible)
})
onBeforeUnmount(() => {
  reducedQuery?.removeEventListener('change', syncReduced)
  document.removeEventListener('visibilitychange', syncVisible)
})

const { index, direction, next, prev, go, hovered } = useSlideShow({
  count: () => slides.value.length,
  active: () => !props.editMode && !reduced.value && visible.value && !focused.value && !interacting.value,
  interval: props.variant === 'carousel' ? 4000 : 5000,
})
const current = computed(() => slides.value[index.value])
const caption = computed(() => {
  const p = current.value
  return p?.description || formatDate(p?.takenAt || p?.createdAt) || t('warm.familyPhoto')
})
const frameSize = computed(() => (props.variant === 'stack' ? Math.min(slides.value.length, 4) : 1))
const frames = computed(() => {
  const count = slides.value.length
  return (count > 1 ? [-1, 0, 1] : [0]).map((offset) => ({
    offset,
    photos: Array.from({ length: frameSize.value }, (_, i) => slides.value[(index.value + offset + i + count) % count]),
  }))
})
const stackStyle = (i) => {
  if (props.variant !== 'stack') return
  const spread = i - (frameSize.value - 1) / 2
  return {
    '--dx': `${spread * 12}px`,
    '--dy': `${Math.abs(spread) * 8}px`,
    '--rot': `${spread * 5}deg`,
    '--z': 10 - i,
  }
}

let gesture = null
let suppressClick = false
let releasePan = 0
function resetSwipe(surface, settle) {
  surface.classList.toggle('is-settling', settle)
  surface.style.removeProperty('--pan')
  gesture = null
  interacting.value = false
}
function startSwipe(e) {
  if (!e.isPrimary) {
    if (gesture) {
      suppressClick = true
      resetSwipe(e.currentTarget, true)
    }
    return
  }
  suppressClick = false
  if (e.button !== 0 || props.editMode || slides.value.length < 2) return
  e.currentTarget.classList.remove('is-settling')
  gesture = { id: e.pointerId, x: e.clientX, y: e.clientY, dx: 0, horizontal: false }
  interacting.value = true
}
function moveSwipe(e) {
  if (gesture?.id !== e.pointerId) return
  const dx = e.clientX - gesture.x
  const dy = e.clientY - gesture.y
  if (!gesture.horizontal) {
    if (Math.max(Math.abs(dx), Math.abs(dy)) < 8) return
    if (Math.abs(dx) <= Math.abs(dy)) {
      suppressClick = true
      cancelSwipe(e)
      return
    }
    gesture.horizontal = true
    suppressClick = true
    e.currentTarget.setPointerCapture(e.pointerId)
  }
  const width = e.currentTarget.clientWidth
  gesture.dx = Math.max(-width, Math.min(dx, width))
  if (!reduced.value) e.currentTarget.style.setProperty('--pan', `${gesture.dx}px`)
}
function cancelSwipe(e) {
  if (gesture?.id !== e.pointerId) return
  resetSwipe(e.currentTarget, true)
}
function endSwipe(e) {
  if (gesture?.id !== e.pointerId) return
  const { dx, horizontal } = gesture
  const advance = !props.editMode && horizontal && Math.abs(dx) >= Math.min(42, e.currentTarget.clientWidth / 4)
  resetSwipe(e.currentTarget, !advance)
  if (!advance) return
  releasePan = reduced.value ? 0 : dx
  if (dx < 0) next()
  else prev()
  nextTick(() => {
    releasePan = 0
  })
}
function setReleaseOffset(el) {
  el.style.setProperty('--release-pan', `${releasePan}px`)
}
function activate(e) {
  if (props.editMode || (suppressClick && e.detail !== 0)) return
  if (props.variant === 'carousel') openAlbum()
  else next()
}
</script>

<style scoped>
.pcs {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  border-radius: 12px;
  background: var(--color-line);
}
.pcs-surface {
  --pan: 0px;
  --release-pan: 0px;
  position: absolute;
  inset: 0;
  display: block;
  width: 100%;
  height: 100%;
  padding: 0;
  border: 0;
  color: inherit;
  background: transparent;
  cursor: pointer;
  touch-action: pan-y pinch-zoom;
  user-select: none;
}
.pcs-surface:disabled {
  cursor: default;
}
.is-editing .pcs-surface {
  pointer-events: none;
}
.is-settling .pcs-strip {
  transition: transform 180ms ease-out;
}
.pcs-surface:focus-visible {
  outline-offset: -3px;
}
.pcs-viewport,
.pcs-strip,
.pcs-frame,
.pcs-print {
  position: absolute;
  display: block;
  inset: 0;
}
.pcs-viewport {
  overflow: hidden;
}
.pcs-strip {
  transform: translateX(var(--pan, 0px));
}
.pcs-frame {
  width: 100%;
}
.pcs-forward-enter-active,
.pcs-forward-leave-active,
.pcs-back-enter-active,
.pcs-back-leave-active {
  transition: transform 450ms cubic-bezier(0.22, 1, 0.36, 1);
}
.pcs-forward-leave-from,
.pcs-back-leave-from {
  transform: translateX(var(--release-pan, 0px));
}
.pcs-forward-enter-from {
  transform: translateX(calc(100% + var(--release-pan, 0px)));
}
.pcs-back-enter-from {
  transform: translateX(calc(-100% + var(--release-pan, 0px)));
}
.pcs-forward-leave-to {
  transform: translateX(-100%);
}
.pcs-back-leave-to {
  transform: translateX(100%);
}
.pcs-featured {
  min-height: 180px;
  border-radius: 10px;
}
.pcs-stack {
  background: transparent;
}
.pcs-stack .pcs-frame {
  display: grid;
  place-items: center;
}
.pcs-stack .pcs-print {
  --dx: 0px;
  --dy: 0px;
  --rot: 0deg;
  --z: 10;
  position: absolute;
  inset: auto;
  width: 72%;
  height: 76%;
  border-radius: 14px;
  overflow: hidden;
  z-index: var(--z);
  transform: translate(var(--dx), var(--dy)) rotate(var(--rot));
  box-shadow: 0 14px 30px rgba(0, 0, 0, 0.16);
  background: var(--color-line);
  transition: transform 0.3s ease;
}
@media (hover: hover) {
  .pcs-stack .pcs-print:hover {
    z-index: 15;
    transform: scale(1.1);
  }
}
.pcs-print img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
/* 读数条常驻在底部:横向切换时说明与序号不闪烁 */
.pcs-meta {
  position: absolute;
  inset: auto 0 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 28px 14px 10px;
  background: linear-gradient(transparent, rgba(0, 0, 0, 0.62));
  pointer-events: none;
}
.pcs-cap {
  min-width: 0;
  font-size: 12.5px;
  font-weight: 600;
  color: #fff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pcs-count {
  flex-shrink: 0;
  font-size: 11px;
  color: #fff;
  opacity: 0.85;
  font-variant-numeric: tabular-nums;
}
.pcs-nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  color: #fff;
  background: rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(6px);
  transition:
    background 0.2s ease,
    transform 0.2s ease;
}
.pcs-nav:hover {
  background: rgba(0, 0, 0, 0.6);
  transform: translateY(-50%) scale(1.06);
}
.pcs-nav.prev {
  left: 8px;
}
.pcs-nav.next {
  right: 8px;
}
.pcs-nav svg {
  width: 16px;
  height: 16px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2.4;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.pcs-dots {
  position: absolute;
  left: 50%;
  bottom: 38px;
  transform: translateX(-50%);
  display: flex;
  gap: 5px;
  z-index: 2;
}
.pcs-dot {
  width: 6px;
  height: 6px;
  padding: 0;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.45);
  transition:
    background 0.2s ease,
    transform 0.2s ease;
}
.pcs-dot.on {
  background: #fff;
  transform: scale(1.25);
}
.pcs-featured .pcs-meta {
  padding: 48px 16px 14px;
}
.pcs-featured .pcs-cap {
  font-size: 15px;
  white-space: normal;
  overflow-wrap: anywhere;
}
.pcs-stack .pcs-meta {
  opacity: 0;
  transition: opacity 0.2s ease;
}
.pcs-stack:hover .pcs-meta,
.pcs-stack:focus-within .pcs-meta {
  opacity: 1;
}
@media (hover: none) {
  .pcs-stack .pcs-meta {
    opacity: 1;
  }
}
@media (prefers-reduced-motion: reduce) {
  .pcs *,
  .is-settling .pcs-strip,
  .pcs-stack .pcs-meta,
  .pcs-stack .pcs-print {
    transition: none;
  }
  .pcs-stack .pcs-print:hover {
    transform: translate(var(--dx), var(--dy)) rotate(var(--rot));
  }
}
</style>
