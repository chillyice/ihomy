<template>
  <button
    v-if="preview || entry?.enabled"
    ref="note"
    class="garden-note"
    :class="{ 'has-letter': preview ? progress < 0 : entry?.note === 'MAIL' || entry?.note === 'CARE' }"
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
        <button v-show="!tending" ref="back" class="garden-back" @click="leave">
          <el-icon><Back /></el-icon>{{ t('gardenEntry.back') }}
        </button>
        <div class="garden-frame">
          <div class="garden-view" aria-hidden="true"></div>
          <div v-if="planted || progress === 8" class="sill-soil" aria-hidden="true">
            <i
              v-if="growth > 0 && sketchCrop"
              class="sill-shoot"
              :class="{ leafy: growth >= 2, mature: harvestable, broad: cropCode === 'MINT' }"
            >
              <i></i><i v-if="growth >= 2"></i><i v-if="growth >= 2"></i>
              <template v-if="harvestable"><i></i><i></i><i></i><i></i></template>
            </i>
          </div>
          <button
            v-if="!reading && !tending && (preview ? progress < 0 : mail)"
            ref="envelope"
            class="garden-envelope"
            :aria-label="t('gardenEntry.envelope')"
            :disabled="busy"
            @click="openLetter"
          >
            <img :src="envelopeArt" alt="" />
            <span>{{ t('gardenEntry.envelope') }}</span>
          </button>
          <button
            v-if="!reading && !tending && progress >= 0 && (preview || plot) && (planted || !mail || progress > 0)"
            ref="packet"
            class="garden-envelope garden-packet"
            :class="{ 'at-pot': progress > 0 }"
            @click="openPot"
          >
            <i v-if="progress === 0" class="seed-sachet" aria-hidden="true"><i></i></i>
            <span>{{ potLabel }}</span>
          </button>
        </div>
        <div v-show="!tending" class="garden-title">
          <h1 id="garden-name">{{ t('gardenEntry.name') }}</h1>
          <p v-if="preview">{{ t('gardenEntry.preview') }}</p>
          <p v-else-if="!world && !failed" role="status">{{ t('common.loading') }}</p>
          <p v-else-if="world && !plot && !failed">{{ t('garden.noPot') }}</p>
          <button v-if="!reading && (preview ? progress >= 0 : world && !mail)" class="garden-reread" :disabled="busy" @click="openLetter">
            {{ t('gardenEntry.reread') }}
          </button>
        </div>
        <Transition name="garden-unfold">
          <article v-if="reading" class="garden-letter" aria-labelledby="garden-letter-title">
            <h2 id="garden-letter-title">{{ t('gardenEntry.salutation') }}</h2>
            <template v-if="preview">
              <p>{{ t('gardenEntry.letter') }}</p>
              <p class="letter-gift">{{ t('gardenEntry.gift') }}</p>
              <p class="garden-signature">{{ t('gardenEntry.signature') }}</p>
            </template>
            <template v-else>
              <div v-for="letter in letters" :key="letter.date">
                <p>{{ letter.text }}</p>
                <p class="garden-signature">{{ letter.signature }}</p>
              </div>
            </template>
            <button ref="fold" @click="closeLetter">{{ t('gardenEntry.fold') }}</button>
          </article>
        </Transition>
        <FirstPot
          v-if="tending && progress >= 0"
          :step="progress"
          :growth="growth"
          :crop-code="cropCode"
          :crop-name="cropName"
          :preview="preview"
          :busy="busy"
          :cost-hint="costHint"
          :first-shoot="firstShoot"
          :harvestable="harvestable"
          :cycle="String(preview ? 'preview' : plot?.plantedAt || '')"
          :receipt="receipt"
          :harvest-error="harvestError"
          :seed-available="preview || !!crop"
          @advance="advance"
          @harvest="harvest"
          @back="closePot"
          @replay="replay"
          @sample="setSample"
          @observed="rememberGrowth"
        />
        <div v-if="!preview && failed" class="garden-sync" role="status">
          {{ t('garden.syncFailed') }} <button @click="refresh">{{ t('common.retry') }}</button>
        </div>
      </section>
    </Transition>
  </Teleport>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Back } from '@element-plus/icons-vue'
import { useAppBack } from '@/composables/useAppBack'
import envelopeArt from '@/assets/windowdesk/envelope.webp'
import FirstPot from './FirstPot.vue'
import { useWindowsill } from './useWindowsill'

const props = defineProps({ member: [Number, String], family: [Number, String], preview: Boolean })
const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const note = ref(null)
const room = ref(null)
const back = ref(null)
const envelope = ref(null)
const packet = ref(null)
const fold = ref(null)
const saveKey = 'ihomy:garden:first-pot-preview:v1:' + (props.member ?? 'guest') + ':' + (props.family ?? 'guest')
function savedStep() {
  try {
    const raw = localStorage.getItem(saveKey)
    if (raw !== null && /^[0-8]$/.test(raw)) return Number(raw)
  } catch {
    /* The preview also works in memory. */
  }
  return -1
}
const inside = computed(() => route.name === 'Home' && route.query.garden === 'window')
const {
  entry,
  plot,
  world,
  crop,
  mail,
  letters,
  busy,
  failed,
  receipt,
  harvestError,
  step,
  openMail,
  advance: advanceLive,
  harvest: harvestLive,
  refresh,
} = useWindowsill(props, inside)
const localStep = ref(props.preview ? savedStep() : -1)
const progress = computed(() => (props.preview ? localStep.value : step.value))
function savedSample() {
  try {
    const n = Number(localStorage.getItem(saveKey + ':growth'))
    return [1, 2, 5].includes(n) ? n : 0
  } catch {
    return 0
  }
}
const sample = ref(props.preview && localStep.value === 7 ? savedSample() : 0)
function setSample(n) {
  if (!props.preview || ![0, 1, 2, 5].includes(n)) return
  sample.value = n
  try {
    localStorage.setItem(saveKey + ':growth', String(n))
  } catch {
    /* Session preview. */
  }
}
const growth = computed(() => (props.preview ? (progress.value === 7 ? sample.value : 0) : (plot.value?.stage ?? 0)))
const planted = computed(() => (props.preview ? progress.value === 7 : !!plot.value?.cropCode))
const cropCode = computed(() => (props.preview ? 'CATGRASS' : plot.value?.cropCode || crop.value?.code))
const sketchCrop = computed(() => ['CATGRASS', 'MINT', 'SNAKE_PLANT'].includes(cropCode.value))
const cropName = computed(() => plot.value?.cropName || crop.value?.name || '')
const harvestable = computed(() =>
  props.preview ? progress.value === 7 && growth.value === 5 : !!plot.value?.cropCode && plot.value.harvestable === true,
)
const potLabel = computed(() => {
  if (harvestable.value) return t('garden.ripePot')
  if (progress.value === 8) return t('garden.soilPot')
  if (progress.value === 0) return props.preview ? t('gardenEntry.seeds') : t('garden.seedPacket', { crop: cropName.value })
  return t('gardenEntry.visitPot')
})
const costHint = computed(() => {
  if (props.preview || !world.value) return ''
  if (progress.value === 1 && world.value.plots.some((p) => p.prepared)) return t('garden.prepareCost')
  if (progress.value === 5 && crop.value) return t('garden.sowCost', { price: crop.value.seedPrice })
  return ''
})
const tending = ref(false)
const firstShoot = ref(false)
const seenKey = computed(() =>
  props.preview ? saveKey + ':seen' : 'ihomy:garden:seen:' + props.member + ':' + props.family + ':' + plot.value?.id,
)
function viewedStage(n) {
  return (props.preview ? 'review' : plot.value?.plantedAt) + ':' + n
}
function openPot() {
  firstShoot.value = false
  if (planted.value && growth.value === 1) {
    try {
      firstShoot.value = localStorage.getItem(seenKey.value) === viewedStage(0)
    } catch {
      /* Show the current snapshot. */
    }
  }
  tending.value = true
}
function rememberGrowth() {
  if (!planted.value) return
  firstShoot.value = false
  try {
    localStorage.setItem(seenKey.value, viewedStage(growth.value))
  } catch {
    /* Optional visit history. */
  }
}
function syncProgress(event) {
  if (!props.preview) return
  if (event.key === saveKey + ':growth') {
    sample.value = savedSample()
    return
  }
  if (event.key !== saveKey) return
  localStep.value = savedStep()
  if (progress.value < 0) tending.value = false
}
onMounted(() => window.addEventListener('storage', syncProgress))
onBeforeUnmount(() => window.removeEventListener('storage', syncProgress))
function save(step) {
  localStep.value = step
  try {
    if (step < 0) localStorage.removeItem(saveKey)
    else localStorage.setItem(saveKey, String(step))
  } catch {
    /* Retain this session's progress. */
  }
}
function advance(previous) {
  if (!props.preview) return advanceLive(previous)
  if (previous === 8 && progress.value === 8) {
    setSample(0)
    save(2)
    return
  }
  if (previous === progress.value && previous < 7) save(Math.max(previous + 1, savedStep()))
}
function harvest() {
  if (!harvestable.value) return
  if (!props.preview) return harvestLive()
  save(8)
  setSample(0)
}
async function closePot() {
  tending.value = false
  await nextTick()
  packet.value?.focus({ preventScroll: true })
}
async function replay() {
  if (!props.preview) return
  setSample(0)
  tending.value = false
  save(-1)
  await nextTick()
  envelope.value?.focus({ preventScroll: true })
}
const reading = ref(false)

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
  if (!props.preview && !(await openMail())) return
  if (!inside.value) return
  reading.value = true
  if (props.preview && progress.value < 0) save(Math.max(0, savedStep()))
  await nextTick()
  fold.value?.focus({ preventScroll: true })
}

async function closeLetter() {
  reading.value = false
  await nextTick()
  packet.value?.focus({ preventScroll: true })
}

function goBack() {
  if (!inside.value) return false
  if (tending.value) closePot()
  else if (reading.value) closeLetter()
  else leave()
  return true
}
useAppBack(goBack)

function cycleFocus(event) {
  const buttons = Array.from(room.value.querySelectorAll('button:not(:disabled)')).filter((el) => el.getClientRects().length > 0)
  const first = buttons[0]
  const last = buttons.at(-1)
  const target = event.shiftKey && event.target === first ? last : !event.shiftKey && event.target === last ? first : null
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
      tending.value = false
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
  container-type: size;
  overflow: hidden;
  background: #e6d5b8;
  color: #433b2d;
}
.garden-frame {
  position: absolute;
  left: 50%;
  top: 74%;
  translate: -50% -74%;
  width: max(100cqw, 150cqh);
  aspect-ratio: 3 / 2;
  transform-origin: 50% 77%;
}
.garden-view {
  position: absolute;
  inset: 0;
  background: #d7c4a0 url('@/assets/garden/windowsill.webp') center / cover no-repeat;
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
  z-index: 1;
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
  left: min(77%, calc(50% + 50cqw - 56px - env(safe-area-inset-right, 0px)));
  bottom: 23%;
  translate: -50% 0;
  width: clamp(82px, 9cqw, 135px);
  min-height: 44px;
  padding: 0;
  border: 0;
  background: transparent;
}
.garden-envelope img {
  width: 100%;
  display: block;
  transform: scaleY(0.66) rotate(-5deg);
  transform-origin: center bottom;
  filter: drop-shadow(2px 4px 3px #31251460);
}
.garden-envelope:hover img,
.garden-envelope:focus-visible img {
  transform: translateY(-2px) scaleY(0.68) rotate(-3deg);
}
.garden-envelope span {
  position: absolute;
  top: 100%;
  width: max-content;
  max-width: 108px;
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
.garden-reread {
  min-height: 44px;
  padding: 2px 0;
  font-size: 13px;
  text-decoration: underline;
  text-underline-offset: 3px;
  background: none;
  border: none;
}
.garden-packet {
  justify-items: center;
}
.seed-sachet {
  display: grid;
  place-items: center;
  width: 52px;
  height: 66px;
  border: 1px solid #aa8555;
  border-block: 4px double #b19266;
  background: linear-gradient(110deg, #ddbd83, #f8e6bb 30%, #e8cc93);
  color: #526946;
  transform: rotate(7deg);
  box-shadow: 1px 3px 4px #62452755;
}
.seed-sachet i,
.seed-sachet i::before,
.seed-sachet i::after {
  display: block;
  width: 5px;
  height: 29px;
  border-radius: 0 90% 0 90%;
  background: #647448;
  transform: rotate(-6deg);
}
.seed-sachet i {
  position: relative;
}
.seed-sachet i::before,
.seed-sachet i::after {
  content: '';
  position: absolute;
  bottom: 1px;
  height: 22px;
  transform-origin: bottom;
}
.seed-sachet i::before {
  transform: rotate(-23deg);
}
.seed-sachet i::after {
  transform: rotate(27deg);
}
.garden-packet.at-pot {
  left: 50%;
  bottom: 28%;
}
.garden-packet.at-pot span {
  top: auto;
  bottom: 0;
}
.sill-soil {
  position: absolute;
  left: 49.2%;
  top: 56.9%;
  width: 10.1%;
  height: 1.35%;
  transform: translate(-50%, -50%);
  border-radius: 50%;
  background: #655039;
  pointer-events: none;
}
.sill-shoot {
  position: absolute;
  left: 28%;
  translate: -50% 0;
  bottom: 45%;
  width: 12%;
  aspect-ratio: 1 / 1.8;
}
.sill-shoot i {
  position: absolute;
  bottom: 0;
  left: 50%;
  width: 45%;
  height: 100%;
  transform-origin: bottom;
  border-radius: 100% 0 90% 15%;
  background: linear-gradient(90deg, #536932 50%, #a4b86a 51%);
  transform: rotate(12deg);
}
.sill-shoot.leafy {
  width: 23%;
}
.sill-shoot.leafy i:nth-child(2) {
  transform: rotate(-35deg);
  height: 77%;
}
.sill-shoot.leafy i:nth-child(3) {
  transform: rotate(39deg);
  height: 70%;
}
.sill-shoot.broad i {
  width: 68%;
  border-radius: 70% 30%;
}
.sill-shoot.mature {
  width: 36%;
}
.sill-shoot.mature i:nth-child(4) {
  transform: rotate(-17deg);
  height: 90%;
}
.sill-shoot.mature i:nth-child(5) {
  transform: rotate(27deg);
  height: 87%;
}
.sill-shoot.mature i:nth-child(6) {
  transform: rotate(-52deg);
  height: 61%;
}
.sill-shoot.mature i:nth-child(7) {
  transform: rotate(53deg);
  height: 54%;
}
@media (prefers-reduced-motion: no-preference) {
  .sill-shoot.leafy {
    transform-origin: 50% 100%;
    animation: sill-leaves 1.75s ease-in-out infinite alternate;
  }
  @keyframes sill-leaves {
    from {
      transform: rotate(-1deg);
    }
    to {
      transform: rotate(1deg);
    }
  }
}
.garden-sync {
  position: absolute;
  right: 12px;
  top: 12px;
  background: #fff3da;
  color: #503e2b;
  padding: 8px;
  border-radius: 6px;
}
.garden-sync button {
  background: transparent;
  border: 0;
  text-decoration: underline;
}
.garden-letter .letter-gift {
  font-size: 14px;
  color: #566a47;
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
  transition: background-color 1.8s ease;
}
.garden-visit-leave-active {
  transition: background-color 1.4s ease;
}
.garden-visit-enter-active .garden-frame {
  transition:
    transform 1.8s cubic-bezier(0.16, 0.68, 0.2, 1),
    opacity 0.55s ease;
}
.garden-visit-leave-active .garden-frame {
  transition:
    transform 1.4s ease-in,
    opacity 0.5s 0.9s ease;
}
.garden-visit-enter-from,
.garden-visit-leave-to {
  background-color: #dfc9a6;
}
.garden-visit-enter-from .garden-frame,
.garden-visit-leave-to .garden-frame {
  opacity: 0;
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
}
@media (orientation: portrait) {
  .garden-frame {
    width: max(100cqw, calc(100cqh * 2 / 3));
    aspect-ratio: 2 / 3;
  }
  .garden-view {
    background-image: url('@/assets/garden/windowsill-portrait.webp');
  }
  .sill-soil {
    left: 47.4%;
    top: 59.4%;
    width: 11.5%;
    height: 0.9%;
  }
  .garden-envelope {
    bottom: 29%;
  }
}
@media (orientation: portrait), (prefers-reduced-motion: reduce) {
  .garden-visit-enter-active,
  .garden-visit-leave-active {
    transition: background-color 0.3s;
  }
  .garden-visit-enter-active .garden-frame,
  .garden-visit-leave-active .garden-frame {
    transition: opacity 0.3s;
    transform: none;
  }
  .garden-unfold-enter-active,
  .garden-unfold-leave-active {
    transition: none;
    transform: none;
  }
}
</style>
