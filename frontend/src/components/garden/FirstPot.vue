<template>
  <div class="pot-shade">
    <section class="pot-card" aria-labelledby="pot-name">
      <header>
        <h2 id="pot-name">{{ t('gardenEntry.pot') }}</h2>
        <button ref="back" class="pot-back" @click="emit('back')">{{ t('gardenEntry.sill') }}</button>
      </header>
      <div class="pot-stage">
        <iframe v-if="status !== 'error'" ref="frame" :key="attempt" :src="gameUrl.href" :title="t('gardenEntry.pot')" tabindex="-1" />
        <div v-if="status !== 'ready'" class="pot-status" role="status">
          <span>{{ t(status === 'error' ? 'gardenEntry.sceneUnavailable' : 'gardenEntry.sceneLoading') }}</span>
          <button v-if="status === 'error'" @click="loadScene">{{ t('common.retry') }}</button>
        </div>
      </div>
      <footer v-if="status === 'ready'">
        <p aria-live="polite">{{ t('gardenEntry.potHints.' + step) }}</p>
        <button v-if="step < 7" ref="action" class="pot-action" @click="post('act')">{{ t('gardenEntry.potActions.' + step) }}</button>
        <button v-else ref="action" class="pot-back" @click="emit('replay')">{{ t('gardenEntry.replay') }}</button>
      </footer>
    </section>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps({ step: { type: Number, required: true } })
const emit = defineEmits(['advance', 'back', 'replay'])
const { t } = useI18n()
const frame = ref(null)
const back = ref(null)
const action = ref(null)
const attempt = ref(0)
const status = ref('loading')
const channel = 'ihomy-first-pot-v1'
const gameUrl = new URL('/index.html', window.location.origin)
gameUrl.port = '5191'
let timer

function post(type) {
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  frame.value?.contentWindow?.postMessage({ channel, type, step: props.step, reduced }, gameUrl.origin)
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
      post('restore')
      break
    case 'advance':
      if (status.value === 'ready' && event.data.step === props.step && props.step < 7) emit('advance', props.step)
      else post('restore')
      break
    case 'back':
      emit('back')
      break
    case 'focus':
      action.value?.focus()
      break
    case 'error':
      clearTimeout(timer)
      status.value = 'error'
      break
  }
}

watch(
  () => props.step,
  () => {
    if (status.value === 'ready') post('restore')
  },
)
onMounted(() => {
  window.addEventListener('message', receive)
  timeout(10000)
  nextTick(() => back.value?.focus({ preventScroll: true }))
})
onBeforeUnmount(() => {
  clearTimeout(timer)
  window.removeEventListener('message', receive)
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
.pot-stage {
  position: relative;
  height: min(55dvh, 330px);
  min-height: 120px;
  background: #efe5cc;
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
