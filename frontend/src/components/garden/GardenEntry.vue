<template>
  <button
    ref="note"
    class="garden-note"
    :class="{ 'has-letter': !read }"
    :aria-label="t('gardenEntry.visit')"
    :title="t('gardenEntry.name')"
    aria-haspopup="dialog"
    :aria-expanded="inside"
    @click="visit"
  >
    <span>{{ t('gardenEntry.note') }}</span>
  </button>

  <Teleport to="body">
    <Transition name="garden-visit" @after-leave="focusNote">
      <section
        v-if="inside"
        ref="room"
        class="garden-room"
        role="dialog"
        aria-modal="true"
        aria-labelledby="garden-name"
        tabindex="-1"
        @keydown.esc.stop.prevent="goBack"
        @keydown.tab="cycleFocus"
      >
        <div class="garden-view" aria-hidden="true"></div>
        <button ref="back" class="garden-back" @click="leave">
          <el-icon><Back /></el-icon>{{ t('gardenEntry.back') }}
        </button>
        <div class="garden-title">
          <h1 id="garden-name">{{ t('gardenEntry.name') }}</h1>
          <p>{{ t('gardenEntry.preview') }}</p>
        </div>
        <button
          v-if="!reading"
          ref="envelope"
          class="garden-envelope"
          :aria-label="t(read ? 'gardenEntry.reread' : 'gardenEntry.envelope')"
          @click="openLetter"
        >
          <img :src="envelopeArt" alt="" />
          <span>{{ t(read ? 'gardenEntry.reread' : 'gardenEntry.envelope') }}</span>
        </button>
        <Transition name="garden-unfold">
          <article v-if="reading" class="garden-letter" aria-labelledby="garden-letter-title">
            <h2 id="garden-letter-title">{{ t('gardenEntry.salutation') }}</h2>
            <p>{{ t('gardenEntry.letter') }}</p>
            <p class="garden-signature">{{ t('gardenEntry.signature') }}</p>
            <button ref="fold" @click="closeLetter">{{ t('gardenEntry.fold') }}</button>
          </article>
        </Transition>
      </section>
    </Transition>
  </Teleport>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Back } from '@element-plus/icons-vue'
import { useAppBack } from '@/composables/useAppBack'
import envelopeArt from '@/assets/windowdesk/envelope.webp'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const note = ref(null)
const room = ref(null)
const back = ref(null)
const envelope = ref(null)
const fold = ref(null)
const read = ref(false)
const reading = ref(false)
const inside = computed(() => route.name === 'Home' && route.query.garden === 'window')

function visit() {
  router.push({ path: route.path, query: { ...route.query, garden: 'window' }, hash: route.hash, state: { gardenFrom: route.fullPath } })
}

function leave() {
  const query = { ...route.query }
  delete query.garden
  const home = { path: route.path, query, hash: route.hash }
  if (window.history.state?.gardenFrom === router.resolve(home).fullPath) router.back()
  else router.replace(home)
}

async function openLetter() {
  reading.value = true
  read.value = true
  await nextTick()
  fold.value?.focus({ preventScroll: true })
}

async function closeLetter() {
  reading.value = false
  await nextTick()
  envelope.value?.focus({ preventScroll: true })
}

function goBack() {
  if (!inside.value) return false
  if (reading.value) closeLetter()
  else leave()
  return true
}
useAppBack(goBack)

function cycleFocus(event) {
  const last = reading.value ? fold.value : envelope.value
  const target = event.shiftKey && event.target === back.value ? last : !event.shiftKey && event.target === last ? back.value : null
  if (target) {
    event.preventDefault()
    target.focus({ preventScroll: true })
  }
}

function focusNote() {
  if (!inside.value) note.value?.focus({ preventScroll: true })
}

// 场景在 body 上，首页保留滚动位置。离开、切号或导航中断都归还交互权。
let releaseHome
watch(
  inside,
  async (visible) => {
    releaseHome?.()
    if (!visible) {
      reading.value = false
      return
    }
    await nextTick()
    if (!inside.value || !room.value) return
    const app = document.getElementById('app')
    const wasInert = app?.inert
    back.value?.focus({ preventScroll: true })
    if (app) app.inert = true
    document.documentElement.classList.add('garden-visiting')
    releaseHome = () => {
      if (app) app.inert = wasInert
      document.documentElement.classList.remove('garden-visiting')
      releaseHome = undefined
    }
  },
  { immediate: true, flush: 'post' },
)
onBeforeUnmount(() => releaseHome?.())
</script>

<style scoped>
.garden-note {
  position: fixed;
  z-index: var(--z-float);
  inset-inline-end: max(4px, env(safe-area-inset-right));
  top: 38%;
  width: 75px;
  min-height: 65px;
  padding: 14px 9px 10px;
  border: 0;
  border-radius: 2px 3px 12px 2px;
  background: linear-gradient(149deg, #ead7ac, #f8ebca 65%, #e8cf99 95%, #ceaf7f);
  color: #57472f;
  box-shadow: -2px 4px 7px #3a2f2338;
  transform: rotate(-5deg);
  cursor: pointer;
  font:
    500 16px/1.3 KaiTi,
    STKaiti,
    cursive;
  text-wrap: balance;
}
.garden-note::before {
  content: '';
  position: absolute;
  top: -5px;
  left: 28%;
  width: 43%;
  height: 12px;
  background: #d3c7a6bb;
  transform: rotate(7deg);
}
.garden-note.has-letter::after {
  content: '';
  position: absolute;
  right: 7px;
  bottom: 7px;
  width: 9px;
  height: 5px;
  background: #65704f;
  border-radius: 100% 0;
  transform: rotate(-22deg);
}
.garden-note:hover,
.garden-note:focus-visible {
  transform: rotate(-2deg) translateX(-3px);
}
:global(html.garden-visiting) {
  overflow: hidden;
}
.garden-room {
  position: fixed;
  inset: 0;
  z-index: var(--z-immersive);
  isolation: isolate;
  overflow: hidden;
  background: #d7c4a0;
  color: #433b2d;
}
.garden-view {
  position: absolute;
  inset: 0;
  z-index: -1;
  background: #d7c4a0 url('@/assets/garden/windowsill.webp') center / cover no-repeat;
  transform-origin: 50% 78%;
}
.garden-room button {
  font-family: inherit;
  color: inherit;
  cursor: pointer;
}
.garden-back,
.garden-letter button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  padding: 7px 17px;
  border: 1px solid #aa9676;
  border-radius: 7px;
  background: #fbf3dd;
  font-size: 15px;
}
.garden-back {
  position: absolute;
  top: max(56px, calc(env(safe-area-inset-top) + 10px));
  left: max(18px, env(safe-area-inset-left));
  box-shadow: 0 2px 6px #45382024;
}
.garden-title {
  position: absolute;
  left: max(5%, env(safe-area-inset-left));
  bottom: max(4%, calc(env(safe-area-inset-bottom) + 12px));
  max-width: 46%;
  padding: 10px 16px;
  background: #f8edcfef;
  border-inline-start: 4px solid #717a56;
  box-shadow: 1px 3px 6px #46341e30;
  transform: rotate(-1deg);
}
.garden-title h1,
.garden-letter h2 {
  margin: 0;
  color: #443b2d;
  font-size: clamp(20px, 2.4vw, 36px);
  font-weight: 600;
  line-height: 1.2;
  letter-spacing: 0.045em;
}
.garden-title p {
  margin: 5px 0 0;
  color: #645942;
  font-size: 13px;
}
.garden-envelope {
  display: grid;
  justify-items: center;
  position: absolute;
  right: max(13%, env(safe-area-inset-right));
  bottom: max(6%, calc(env(safe-area-inset-bottom) + 10px));
  width: clamp(110px, 12vw, 166px);
  padding: 0;
  border: 0;
  background: transparent;
}
.garden-envelope img {
  width: 100%;
  transform: rotate(-6deg);
  filter: drop-shadow(2px 4px 3px #31251460);
}
.garden-envelope:hover img,
.garden-envelope:focus-visible img {
  transform: translateY(-4px) rotate(-4deg);
}
.garden-envelope span {
  padding: 3px 9px;
  border-radius: 2px;
  background: #f9f0d7;
  font-size: 13px;
}
.garden-letter {
  position: absolute;
  inset: 50% auto auto 50%;
  translate: -50% -50%;
  width: min(390px, calc(100% - 44px));
  max-height: 68%;
  overflow: auto;
  box-sizing: border-box;
  padding: clamp(22px, 4vw, 42px);
  background: linear-gradient(175deg, #fff8e6 25%, #f3e7cb 50%, #faf0d9 51%, #fff8e5);
  box-shadow: 2px 8px 26px #49362345;
  transform: rotate(-1deg);
}
.garden-letter h2 {
  font-size: 21px;
}
.garden-letter p {
  margin: 20px 0;
  line-height: 1.85;
}
.garden-signature {
  text-align: end;
}
.garden-room button:focus-visible,
.garden-note:focus-visible {
  outline: 3px solid #405744;
  outline-offset: 3px;
}
.garden-visit-enter-active {
  transition: opacity 1.8s ease;
}
.garden-visit-leave-active {
  transition: opacity 1.4s ease;
}
.garden-visit-enter-active .garden-view {
  transition: transform 1.8s cubic-bezier(0.16, 0.68, 0.2, 1);
}
.garden-visit-leave-active .garden-view {
  transition: transform 1.4s ease-in;
}
.garden-visit-enter-from,
.garden-visit-leave-to {
  opacity: 0;
}
.garden-visit-enter-from .garden-view,
.garden-visit-leave-to .garden-view {
  transform: perspective(900px) rotateX(12deg) translateY(-9%) scale(1.38);
}
.garden-unfold-enter-active,
.garden-unfold-leave-active {
  transition:
    opacity 0.24s,
    transform 0.24s;
}
.garden-unfold-enter-from,
.garden-unfold-leave-to {
  opacity: 0;
  transform: translateY(12px) rotate(2deg);
}
@media (max-width: 600px) {
  .garden-note {
    width: 62px;
    min-height: 62px;
  }
  .garden-envelope {
    right: 6%;
  }
}
@media (orientation: portrait) {
  .garden-view {
    background-image: url('@/assets/garden/windowsill-portrait.webp');
  }
}
@media (orientation: portrait), (prefers-reduced-motion: reduce) {
  .garden-visit-enter-active,
  .garden-visit-leave-active {
    transition: opacity 0.3s;
  }
  .garden-visit-enter-active .garden-view,
  .garden-visit-leave-active .garden-view,
  .garden-unfold-enter-active,
  .garden-unfold-leave-active {
    transition: none;
    transform: none;
  }
}
</style>
