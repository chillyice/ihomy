<template>
  <div class="pot-shade">
    <section class="pot-card" aria-labelledby="pot-name" @keydown.esc="escapeSun">
      <header>
        <div>
          <small v-if="place" class="pot-place">{{ place }}</small>
          <h2 id="pot-name">{{ title }}</h2>
        </div>
        <button ref="back" class="pot-back" @click="emit('back')">
          {{ t(fromTask ? 'garden.hand.toTask' : place ? 'garden.book.back' : 'gardenEntry.sill') }}
        </button>
      </header>
      <CatgrassStudy v-if="observing" :stage="growth" :cut="cut" :dry="thirsty" :label="stageLabel" />
      <div v-if="enteredBench" v-show="!observing" class="pot-stage">
        <iframe v-if="status !== 'error'" ref="frame" :key="attempt" :src="gameUrl.href" :title="title" tabindex="-1" />
        <div v-if="status !== 'ready'" class="pot-status" role="status">
          <span>{{ t(status === 'error' ? 'gardenEntry.sceneUnavailable' : 'gardenEntry.sceneLoading') }}</span>
          <button v-if="status === 'error'" @click="loadScene">{{ t('common.retry') }}</button>
          <button v-if="status === 'error' && studyAvailable" @click="openStudy">{{ t('garden.look.inspect') }}</button>
        </div>
      </div>
      <footer v-if="observing || status === 'ready'">
        <p aria-live="polite" aria-atomic="true">
          <strong v-if="step >= 2 && step <= 4 && !readOnly" class="seed-count">{{ t('garden.hand.seeds', { n: step - 2 }) }}</strong>
          <strong v-if="step === 1 && !readOnly" class="seed-count">{{ t('garden.hand.preparing', { n: Math.min(4, prep + 2) }) }}</strong>
          <small v-if="observing && previousStage != null && previousStage < growth && !cut" class="last-seen">
            {{ t('garden.look.lastSeen', { stage: t('garden.look.names.' + previousStage) }) }}
          </small>
          {{ observing ? observation : instruction }} {{ costHint }}
          <span v-if="!observing && !sunMode && !readOnly && !inField && step === 7 && sunRest">{{ t('garden.sun.rest') }}</span>
        </p>
        <p v-if="!observing && !readOnly && (canSun || sunMode)" class="daylight">
          {{
            t(weather ? 'garden.hand.daylight' : 'garden.hand.baseLight', {
              sky: weather?.text,
              factor: (weather?.sunTotalBp ?? 100) / 100,
            })
          }}
        </p>
        <button
          v-if="observing && !readOnly && (harvestable || (canWater && !retryWater && (thirsty || step === 6)))"
          ref="workButton"
          class="pot-action"
          :disabled="busy"
          @click="openBench(false)"
        >
          {{ t(harvestable ? 'garden.look.cutGrass' : 'garden.look.water') }}
        </button>
        <button v-if="!observing && studyAvailable && !sunMode" class="pot-back" :disabled="busy" @click="openStudy">
          {{ t('garden.look.inspect') }}
        </button>
        <button v-if="sunMode" class="pot-back" :disabled="busy" @click="sunMode = false">{{ t('common.cancel') }}</button>
        <button v-if="!sunMode && !readOnly && canWater && retryWater" class="pot-action" :disabled="busy" @click="emit('water')">
          {{ t('garden.confirmWater') }}
        </button>
        <button v-if="!readOnly && step === 8 && seedAvailable" class="pot-action" :disabled="busy" @click="emit('advance', 8)">
          {{ t(inField ? 'garden.book.sowBed' : 'garden.resow') }}
        </button>
        <button
          v-if="!observing && !readOnly && (step < 6 || harvestable || (canWater && !retryWater) || sunMode)"
          ref="action"
          class="pot-back"
          :disabled="busy"
          @click="useKeys"
        >
          {{ t('garden.hand.keyboard') }}
        </button>
        <small v-if="!observing && keyboard && !readOnly" class="keys">{{ t('garden.hand.keys') }}</small>
        <button
          v-if="!sunMode && canSun && !readOnly"
          ref="sunButton"
          class="pot-back"
          :disabled="busy"
          @click="retrySun ? emit('sun') : openBench(true)"
        >
          {{ t(retrySun ? 'garden.sun.confirm' : 'garden.sun.start') }}
        </button>
        <button v-if="!fromTask && !sunMode && !readOnly && !inField && sunRest && step === 7" class="pot-back" @click="emit('tasks')">
          {{ t('garden.hand.toTask') }}
        </button>
        <details v-if="preview && step >= 7 && !sunMode" class="pot-review">
          <summary>{{ t('garden.review') }}</summary>
          <div class="pot-samples">
            <template v-if="step === 7"
              ><button v-for="n in [0, 1, 2, 3, 4, 5]" :key="n" :aria-pressed="!thirsty && growth === n" @click="emit('sample', n)">
                {{ t('garden.look.names.' + n) }}
              </button>
              <button :aria-pressed="thirsty" @click="emit('sample', 'dry')">{{ t('garden.drySample') }}</button></template
            >
            <button @click="emit('replay')">{{ t('gardenEntry.replay') }}</button>
          </div>
        </details>
      </footer>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import CatgrassStudy from './CatgrassStudy.vue'

const props = defineProps({
  step: { type: Number, required: true },
  growth: { type: Number, default: 0 },
  cropCode: String,
  cropName: String,
  preview: Boolean,
  busy: Boolean,
  costHint: String,
  firstShoot: Boolean,
  previousStage: Number,
  harvestable: Boolean,
  thirsty: Boolean,
  canWater: Boolean,
  canSun: Boolean,
  retrySun: Boolean,
  sunRest: Boolean,
  retryWater: Boolean,
  alreadyWatered: Boolean,
  harvestError: String,
  seedAvailable: Boolean,
  receipt: Object,
  cycle: String,
  readOnly: Boolean,
  inField: Boolean,
  place: String,
  cropForm: String,
  fromTask: Boolean,
  prepKey: { type: String, required: true },
  weather: Object,
})
const emit = defineEmits(['advance', 'harvest', 'water', 'sun', 'back', 'tasks', 'replay', 'sample', 'observed'])
const sunMode = ref(false)
watch(
  () => [props.canSun, props.retrySun, props.cycle],
  ([available, retry, cycle], previous) => {
    if (!available || retry || cycle !== previous[2]) sunMode.value = false
  },
)
const { t } = useI18n()
const cut = computed(() => props.step === 8 && props.receipt?.cropCode === 'CATGRASS')
const studyAvailable = computed(() => !props.inField && (cut.value || (props.cropCode === 'CATGRASS' && [6, 7].includes(props.step))))
const observing = ref(studyAvailable.value && !props.fromTask)
const enteredBench = ref(!observing.value)
const workButton = ref(null)
const stageLabel = computed(() => t(cut.value ? 'garden.look.clipped' : 'garden.look.names.' + props.growth))
const observation = computed(() => {
  if (cut.value) {
    const r = props.receipt
    return [
      t('garden.look.stubble'),
      r.quantity > 0 ? t('garden.stored', { crop: r.name, qty: r.quantity }) : '',
      r.coins > 0 ? t('garden.earned', { coins: r.coins }) : '',
    ]
      .filter(Boolean)
      .join(' ')
  }
  if (props.harvestable) return t(props.readOnly ? 'garden.look.ripeVisitor' : 'garden.look.ripe')
  if (props.thirsty) return t('garden.look.dry')
  if (props.step === 6) return t('garden.look.firstWater')
  return t('garden.look.notes.' + props.growth)
})
function openStudy() {
  observing.value = true
  cue.value = ''
  nextTick(() => (workButton.value || back.value)?.focus({ preventScroll: true }))
}
function openBench(sun) {
  observing.value = false
  sunMode.value = sun
  if (!enteredBench.value) {
    enteredBench.value = true
    loadScene()
  }
  nextTick(() => (action.value || back.value)?.focus({ preventScroll: true }))
}
watch(studyAvailable, (available) => {
  if (!available && observing.value) openBench(false)
})
watch(cut, (confirmed) => {
  if (confirmed) openStudy()
})
watch([observing, () => props.growth], ([on]) => {
  if (on) emit('observed')
})
const title = computed(() => {
  if (cut.value) return t('garden.look.cutPot')
  if (props.place) {
    if (props.step === 8 || (props.readOnly && props.step === 0)) return t(props.inField ? 'garden.book.emptyBed' : 'garden.book.emptyPot')
    return props.cropName || t('garden.book.catgrass')
  }
  return props.step === 8 ? t('garden.soilPot') : props.preview ? t('gardenEntry.pot') : t('garden.potTitle', { crop: props.cropName })
})
const hint = computed(() => {
  if (props.readOnly) {
    const status =
      props.step === 0 || props.step === 8
        ? ''
        : props.harvestable
          ? t('garden.book.ripe')
          : props.thirsty
            ? t('garden.book.dry')
            : t('garden.book.stageNames.' + props.growth)
    return [status, t('garden.book.visiting')].filter(Boolean).join(' · ')
  }
  if (props.step === 8) {
    const r = props.receipt
    const parts = []
    if (r?.alreadyDone) parts.push(t('garden.book.harvestConfirmed'))
    if (r?.name && r.quantity > 0) parts.push(t('garden.stored', { crop: r.name, qty: r.quantity }))
    if (r?.coins > 0) parts.push(t('garden.earned', { coins: r.coins }))
    parts.push(t(props.inField ? 'garden.book.readyBed' : 'garden.readySoil'))
    return parts.join(' ')
  }
  if (props.harvestable) return props.harvestError || t('garden.harvestHint')
  if (props.canWater && props.retryWater) return t('garden.pendingWater')
  if (props.thirsty && !props.canWater) return t('garden.waterRest')
  if (props.alreadyWatered) return t('garden.alreadyWatered')
  if (props.canWater && (props.step === 6 || props.thirsty)) return t('garden.waterHint')
  return props.step === 7 ? t('garden.stages.' + props.growth) : t('gardenEntry.potHints.' + props.step)
})
const frame = ref(null)
const back = ref(null)
const action = ref(null)
const sunButton = ref(null)
const cue = ref('')
const keyboard = ref(false)
function useKeys() {
  keyboard.value = true
  post('controls')
}
const prep = ref(0)
function savePrep(value) {
  prep.value = value
  try {
    if (value) localStorage.setItem(props.prepKey, String(value))
    else localStorage.removeItem(props.prepKey)
  } catch {
    /* The gesture remains playable for this visit. */
  }
}
watch(
  () => props.step,
  (step) => {
    if (props.readOnly) return
    if (step !== 1) return savePrep(0)
    try {
      const saved = Number(localStorage.getItem(props.prepKey))
      prep.value = [1, 2, 3].includes(saved) ? saved : 0
    } catch {
      prep.value = 0
    }
  },
  { immediate: true },
)
const instruction = computed(() => {
  const base = sunMode.value ? t('garden.sun.drag') : props.step === 1 && !props.readOnly ? t('garden.hand.prep.' + prep.value) : hint.value
  if (props.readOnly || !cue.value || props.busy) return base
  if (cue.value === 'miss') return t('garden.hand.miss') + ' ' + base
  if (props.step === 1 && prep.value !== 1) return t('garden.hand.' + (prep.value === 0 ? 'liner' : 'rake') + '.' + cue.value)
  const mode = sunMode.value
    ? 'light'
    : props.harvestable
      ? 'cut'
      : props.canWater
        ? 'water'
        : props.step === 5
          ? 'brush'
          : props.step < 2
            ? 'bag'
            : 'seed'
  return t('garden.hand.' + mode + '.' + cue.value)
})
function cancelSun() {
  if (!sunMode.value || props.busy) return false
  sunMode.value = false
  return true
}
function escapeSun(event) {
  if (cancelSun()) {
    event.stopPropagation()
    event.preventDefault()
  }
}
defineExpose({ cancelSun })
watch(
  () => [props.step, props.cycle, props.canWater, props.harvestable, sunMode.value, props.busy],
  () => {
    cue.value = ''
  },
)
watch(sunMode, async (on) => {
  await nextTick()
  const target = on ? action.value : sunButton.value || back.value
  target?.focus({ preventScroll: true })
})
const attempt = ref(0)
const status = ref('loading')
const channel = 'ihomy-first-pot-v1'
const gameUrl = new URL('/index.html', window.location.origin)
gameUrl.port = '5191'
let timer

const motion = window.matchMedia('(prefers-reduced-motion: reduce)')
function post(type, reveal = false) {
  frame.value?.contentWindow?.postMessage(
    {
      channel,
      type,
      step: props.step,
      prep: prep.value,
      reduced: motion.matches,
      growth: props.growth,
      crop: props.cropCode,
      reveal,
      ripe: !props.readOnly && props.harvestable,
      field: props.inField,
      readOnly: props.readOnly || observing.value,
      form: props.cropForm,
      dry: props.thirsty,
      canWater: !sunMode.value && props.canWater && !props.retryWater,
      moveToSun: sunMode.value && props.canSun,
      busy: props.busy,
      cycle: props.cycle,
    },
    gameUrl.origin,
  )
}
function restore(opening) {
  if (status.value === 'ready') {
    post('restore', opening === true && props.firstShoot)
    emit('observed')
  }
}

function timeout(ms) {
  clearTimeout(timer)
  timer = setTimeout(() => {
    status.value = 'error'
  }, ms)
}

function loadScene() {
  status.value = 'loading'
  attempt.value++
  if (enteredBench.value) timeout(10000)
}

function receive(event) {
  if (event.origin !== gameUrl.origin || !frame.value || event.source !== frame.value.contentWindow || event.data?.channel !== channel)
    return
  if (observing.value && !['loading', 'ready', 'error'].includes(event.data.type)) return
  switch (event.data.type) {
    case 'prep':
      if (
        status.value === 'ready' &&
        !props.readOnly &&
        !props.busy &&
        props.step === 1 &&
        event.data.cycle === props.cycle &&
        event.data.prep === prep.value &&
        prep.value < 3
      )
        savePrep(prep.value + 1)
      break
    case 'guide':
      if (
        status.value === 'ready' &&
        !props.readOnly &&
        !props.busy &&
        event.data.step === props.step &&
        event.data.cycle === props.cycle &&
        event.data.sun === sunMode.value &&
        ['', 'held', 'ready', 'miss'].includes(event.data.cue)
      )
        cue.value = event.data.cue
      break
    case 'loading':
      timeout(60000)
      break
    case 'ready':
      clearTimeout(timer)
      status.value = 'ready'
      restore(true)
      break
    case 'advance':
      if (
        status.value === 'ready' &&
        !props.readOnly &&
        !props.busy &&
        event.data.step === props.step &&
        props.step < 7 &&
        (props.step !== 1 || prep.value === 3)
      )
        emit('advance', props.step)
      else post('restore')
      break
    case 'harvest':
      if (status.value === 'ready' && !props.readOnly && !props.busy && props.harvestable && event.data.cycle === props.cycle)
        emit('harvest')
      break
    case 'water':
      if (
        status.value === 'ready' &&
        !props.readOnly &&
        !props.busy &&
        props.canWater &&
        !sunMode.value &&
        event.data.cycle === props.cycle &&
        event.data.step === props.step
      )
        emit('water')
      else post('restore')
      break
    case 'sun':
      if (status.value === 'ready' && sunMode.value && props.canSun && !props.readOnly && !props.busy && event.data.cycle === props.cycle)
        emit('sun')
      else post('restore')
      break
    case 'back':
      if (!cancelSun()) emit('back')
      break
    case 'focus':
      ;(action.value || back.value)?.focus()
      break
    case 'error':
      clearTimeout(timer)
      status.value = 'error'
      break
  }
}

watch(
  () => [
    props.step,
    prep.value,
    props.growth,
    observing.value,
    props.cropCode,
    props.harvestable,
    props.readOnly,
    props.inField,
    props.cropForm,
    props.cycle,
    props.thirsty,
    props.canWater,
    sunMode.value,
    props.canSun,
    props.retryWater,
    props.busy,
  ],
  restore,
)
onMounted(() => {
  window.addEventListener('message', receive)
  motion.addEventListener('change', restore)
  if (enteredBench.value) timeout(10000)
  if (observing.value) emit('observed')
  nextTick(() => back.value?.focus({ preventScroll: true }))
})
onBeforeUnmount(() => {
  clearTimeout(timer)
  window.removeEventListener('message', receive)
  motion.removeEventListener('change', restore)
})
</script>

<style scoped>
.pot-shade {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  padding: max(12px, env(safe-area-inset-top)) 12px max(12px, env(safe-area-inset-bottom));
  background: #43372475;
}
.pot-card {
  display: flex;
  flex-direction: column;
  width: min(94%, 520px);
  max-height: 100%;
  overflow: auto;
  color: #493d2d;
  background: #fcf4df;
  border: 1px solid #aa9676;
  border-radius: 12px;
  box-shadow: 0 10px 38px #3a2a1e45;
}
header,
footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 18px;
}
h2 {
  font-size: 19px;
  margin: 0;
  color: inherit;
}
.pot-place {
  color: #64644b;
  font-size: 13px;
}
footer {
  flex-wrap: wrap;
}
footer p {
  flex: 1 1 100%;
  min-height: 3em;
  margin: 0;
  font-size: 14px;
  line-height: 1.5;
}
.seed-count {
  display: block;
  margin-bottom: 2px;
  color: #556548;
  font-size: 13px;
}
.keys {
  flex-basis: 100%;
  color: #63583d;
}
.last-seen {
  display: block;
  color: #63694d;
  margin-bottom: 3px;
}
footer .daylight {
  min-height: 0;
  color: #79582e;
}
.pot-review {
  flex: 1 1 100%;
  border-top: 1px dashed #c6b694;
}
.pot-review summary {
  cursor: pointer;
  padding: 10px 0;
  color: #665840;
  font-size: 14px;
}
button {
  font: inherit;
  cursor: pointer;
  min-height: 44px;
  padding: 6px 12px;
  border-radius: 6px;
}
.pot-back,
.pot-status button {
  color: #554630;
  background: #f7ecd4;
  border: 1px solid #b7a481;
}
.pot-action {
  background: #4a6047;
  color: #fff9e8;
  border: 1px solid #42533f;
}
.pot-shortcut {
  color: #584a39;
  background: transparent;
  border-color: #b7a481;
}
.pot-stage {
  position: relative;
  height: min(55dvh, 330px);
  min-height: 120px;
  background: #efe5cc;
}
.pot-samples {
  display: flex;
  flex: 1 1 100%;
  align-items: center;
  flex-wrap: wrap;
  gap: 5px;
  font-size: 13px;
}
.pot-samples button {
  background: transparent;
  color: inherit;
  border: 1px solid #b7a481;
}
.pot-samples button[aria-pressed='true'] {
  background: #e0e6d3;
  border-color: #687957;
}
iframe,
.pot-status {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  border: 0;
}
.pot-status {
  display: flex;
  padding: 18px;
  gap: 12px;
  align-items: center;
  justify-content: center;
  background: #efe5cc;
}
@media (max-height: 460px) {
  .pot-stage {
    height: 47dvh;
  }
}
</style>
