<template>
  <div class="pot-shade">
    <section class="pot-card" aria-labelledby="pot-name">
      <header>
        <h2 id="pot-name">{{ title }}</h2>
        <button ref="back" class="pot-back" @click="emit('back')">{{ t('gardenEntry.sill') }}</button>
      </header>
      <div class="pot-stage">
        <iframe v-if="status !== 'error'" ref="frame" :key="attempt" :src="gameUrl.href" :title="title" tabindex="-1" />
        <div v-if="status !== 'ready'" class="pot-status" role="status">
          <span>{{ t(status === 'error' ? 'gardenEntry.sceneUnavailable' : 'gardenEntry.sceneLoading') }}</span>
          <button v-if="status === 'error'" @click="loadScene">{{ t('common.retry') }}</button>
        </div>
      </div>
      <footer v-if="status === 'ready'">
        <p aria-live="polite">{{ hint }} {{ costHint }}</p>
        <button v-if="harvestable" ref="action" class="pot-action" :disabled="busy" @click="emit('harvest')">
          {{ t('garden.harvest') }}
        </button>
        <button v-else-if="step === 8 && seedAvailable" ref="action" class="pot-action" :disabled="busy" @click="emit('advance', 8)">
          {{ t('garden.resow') }}
        </button>
        <button
          v-else-if="step < 7"
          ref="action"
          class="pot-action"
          :disabled="busy"
          :class="{ 'pot-shortcut': step <= 4 }"
          @click="post('act')"
        >
          {{ t('gardenEntry.potActions.' + step) }}
        </button>
        <button v-if="preview && step >= 7" class="pot-back" @click="emit('replay')">{{ t('gardenEntry.replay') }}</button>
        <div v-if="preview && step === 7" class="pot-samples" role="group" :aria-label="t('garden.review')">
          <span>{{ t('garden.review') }}</span>
          <button v-for="n in [0, 1, 2, 5]" :key="n" :aria-pressed="growth === n" @click="emit('sample', n)">
            {{ n === 5 ? t('garden.ripeSample') : t('garden.samples.' + n) }}
          </button>
        </div>
      </footer>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps({
  step: { type: Number, required: true },
  growth: { type: Number, default: 0 },
  cropCode: String,
  cropName: String,
  preview: Boolean,
  busy: Boolean,
  costHint: String,
  firstShoot: Boolean,
  harvestable: Boolean,
  harvestError: String,
  seedAvailable: Boolean,
  receipt: Object,
  cycle: String,
})
const emit = defineEmits(['advance', 'harvest', 'back', 'replay', 'sample', 'observed'])
const { t } = useI18n()
const title = computed(() =>
  props.step === 8 ? t('garden.soilPot') : props.preview ? t('gardenEntry.pot') : t('garden.potTitle', { crop: props.cropName }),
)
const hint = computed(() => {
  if (props.step === 8) {
    const r = props.receipt
    const parts = []
    if (r?.name && r.quantity > 0) parts.push(t('garden.stored', { crop: r.name, qty: r.quantity }))
    if (r?.coins > 0) parts.push(t('garden.earned', { coins: r.coins }))
    parts.push(t('garden.readySoil'))
    return parts.join(' ')
  }
  if (props.harvestable) return props.harvestError || t('garden.harvestHint')
  return props.step === 7 ? t('garden.stages.' + props.growth) : t('gardenEntry.potHints.' + props.step)
})
const frame = ref(null)
const back = ref(null)
const action = ref(null)
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
      reduced: motion.matches,
      growth: props.growth,
      crop: props.cropCode,
      reveal,
      ripe: props.harvestable,
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
  timeout(10000)
}

function receive(event) {
  if (event.origin !== gameUrl.origin || !frame.value || event.source !== frame.value.contentWindow || event.data?.channel !== channel)
    return
  switch (event.data.type) {
    case 'loading':
      timeout(60000)
      break
    case 'ready':
      clearTimeout(timer)
      status.value = 'ready'
      restore(true)
      break
    case 'advance':
      if (status.value === 'ready' && !props.busy && event.data.step === props.step && props.step < 7) emit('advance', props.step)
      else post('restore')
      break
    case 'harvest':
      if (status.value === 'ready' && !props.busy && props.harvestable && event.data.cycle === props.cycle) emit('harvest')
      break
    case 'back':
      emit('back')
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

watch(() => [props.step, props.growth, props.cropCode, props.harvestable, props.cycle], restore)
onMounted(() => {
  window.addEventListener('message', receive)
  motion.addEventListener('change', restore)
  timeout(10000)
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
footer {
  flex-wrap: wrap;
}
footer p {
  flex: 1 1 110px;
  margin: 0;
  font-size: 14px;
  line-height: 1.5;
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
