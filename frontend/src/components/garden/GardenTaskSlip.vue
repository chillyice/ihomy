<template>
  <article class="task-slip" :aria-busy="claiming">
    <p v-if="world.world?.termName" class="term">{{ world.world.termName }}</p>
    <p v-if="error" role="status">
      {{ t('garden.todo.unavailable') }} <button @click="load">{{ t('common.retry') }}</button>
    </p>
    <template v-else-if="task">
      <h3>{{ task.title }}</h3>
      <div class="progress">
        <progress :value="task.progress" :max="task.condition.value" :aria-label="task.title"></progress>
        <span>{{ Math.min(task.progress, task.condition.value) }} / {{ task.condition.value }}</span>
      </div>
      <button v-if="task.completed" class="ink" :disabled="claiming || busy" @click="claim">{{ t('garden.todo.claim') }}</button>
      <button v-else-if="destination" class="ink" :disabled="busy" @click="emit('visit', destination.id)">
        {{ t(destination.venue === 'BACKYARD' ? 'garden.todo.toYard' : 'garden.todo.toSill') }}
      </button>
    </template>
    <p v-else>{{ t(data ? 'garden.todo.clear' : 'common.loading') }}</p>
    <p v-if="notice" role="status">{{ notice }}</p>
    <details v-if="stage && stage.next > stage.current">
      <summary>{{ t('garden.todo.nextStage', { n: stage.next }) }}</summary>
      <p v-if="stage.termCount < stage.termRequired">{{ t('garden.todo.waitTerm', { date: stage.termDate }) }}</p>
      <p v-if="stage.coinsShort">{{ t('garden.todo.short', { n: stage.coinsShort }) }}</p>
      <p v-for="title in missing" :key="title">{{ title }}</p>
      <p v-if="stage.canAdvance">{{ t('garden.todo.stageReady') }}</p>
    </details>
  </article>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { gardenApi } from '@/api'
const props = defineProps({ world: { type: Object, required: true }, review: Object, busy: Boolean })
const emit = defineEmits(['visit', 'claimed'])
const { t } = useI18n()
const data = ref(null)
const error = ref(false)
const notice = ref('')
const claiming = ref(false)
const api = props.review || gardenApi
const tasks = computed(() => ['starter', 'main', 'mid', 'termSide', 'side', 'final'].flatMap((k) => data.value?.[k] || []))
const task = computed(() => {
  const pending = tasks.value.filter((item) => item.visible && !item.claimed && !item.auto)
  return pending.find((item) => data.value?.starter?.includes(item)) || pending.find((item) => item.completed) || pending[0]
})
const stage = computed(() => data.value?.stage)
const missing = computed(() =>
  (stage.value?.missingMain || []).map((code) => tasks.value.find((item) => item.code === code)?.title).filter(Boolean),
)
const destination = computed(() => {
  const plots = props.world.plots.filter((p) => p.mine && (p.venue === 'WINDOWSILL' || p.venue === 'BACKYARD'))
  if (props.review) return plots.find((p) => p.venue === 'WINDOWSILL' && p.slotNo === 0)
  switch (task.value?.condition.type) {
    case 'PLANT_N':
      return plots.find((p) => !p.cropCode)
    case 'HARVEST_N':
      return plots.find((p) => p.harvestable) || plots.find((p) => p.cropCode)
    case 'CARE_N':
      return (
        plots.find(
          (p) => p.venue === 'WINDOWSILL' && p.cropCode && !p.harvestable && (p.sunCooldownSec === 0 || p.waterCooldownSec === 0),
        ) ||
        plots.find((p) => p.cropCode && !p.harvestable) ||
        plots.find((p) => p.venue === 'WINDOWSILL')
      )
    default:
      return null
  }
})
let sequence = 0
async function load() {
  const current = ++sequence
  try {
    const snapshot = await api.tasks()
    if (current === sequence) {
      data.value = snapshot
      error.value = false
    }
  } catch {
    if (current === sequence) error.value = true
  }
}
async function claim() {
  if (!task.value?.completed || claiming.value || error.value) return
  const { code, title } = task.value
  claiming.value = true
  notice.value = ''
  try {
    await api.claimTask(code)
    notice.value = t('garden.todo.claimed', { title })
    emit('claimed')
  } catch {
    notice.value = t('garden.todo.checkClaim')
  } finally {
    await load()
    claiming.value = false
  }
}
watch(() => props.world, load, { immediate: true })
onBeforeUnmount(() => sequence++)
</script>

<style scoped>
.task-slip {
  padding: 16px 16px 10px;
  margin: 12px 2px;
  background: #f6ecd1;
  border-inline-start: 3px solid #a26b4a;
  color: #443c2a;
}
h3 {
  margin: 3px 0 8px;
  font-size: 21px;
}
p,
summary {
  font-size: 14px;
  line-height: 1.5;
  margin: 6px 0;
}
.term {
  color: #77513b;
}
.progress {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 10px 0;
  font-variant-numeric: tabular-nums;
}
progress {
  width: min(55%, 180px);
  height: 6px;
  accent-color: #657952;
}
button {
  font: inherit;
  color: inherit;
  min-height: 44px;
  cursor: pointer;
}
.ink {
  border: 0;
  border-radius: 5px;
  padding: 6px 16px;
  background: #4d634e;
  color: #fffae9;
}
details {
  border-top: 1px dashed #baad89;
  margin-top: 12px;
}
summary {
  cursor: pointer;
  padding: 8px 0;
}
</style>
