import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { gardenApi } from '@/api'
import { ulid } from '@/utils/tools/generatorTool'

// The server owns the pot. Only unfinished hand movements and pending request keys live on this device.
export function useWindowsill(props, inside, review = null) {
  const api = review || gardenApi
  const entry = ref(null)
  const world = ref(review?.panorama() || null)
  const letters = ref([])
  const busy = ref(false)
  const failed = ref(false)
  const receipt = ref(null)
  const harvestError = ref('')
  const reconciling = ref(false)
  const retryWater = ref(false)
  const retrySun = ref(false)
  const replayedWater = ref(null)
  const selectedId = ref(null)
  const homePlot = computed(() => world.value?.plots?.find((p) => p.mine === true && p.venue === 'WINDOWSILL' && p.slotNo === 0))
  const plot = computed(() => (selectedId.value == null ? homePlot.value : world.value?.plots?.find((p) => p.id === selectedId.value)))
  const canWater = computed(() => {
    const p = plot.value
    return (
      p?.mine === true &&
      !!p.plantedAt &&
      !!p.cropCode &&
      !p.harvestable &&
      (p.waterCooldownSec === 0 || (!p.lastWateredAt && p.waterCooldownSec == null))
    )
  })
  const mail = computed(() => world.value?.letters?.some((l) => l.claimable))
  const canSun = computed(() => {
    const p = plot.value
    return (
      p?.mine === true &&
      p.venue === 'WINDOWSILL' &&
      !!p.cropCode &&
      !!p.plantedAt &&
      !!p.lastWateredAt &&
      !p.harvestable &&
      p.sunCooldownSec === 0
    )
  })
  const startedWith = ref(null)
  const crop = computed(() => {
    const data = world.value
    const seasonal = { SUMMER: 'MINT', WINTER: 'SNAKE_PLANT' }[data?.world?.season] || 'CATGRASS'
    const firstSeed = letters.value.find((l) => l.type === 'FIRST')?.seed?.cropCode
    const crops = data?.unlockedCrops || []
    const target = plot.value?.cropCode || startedWith.value || firstSeed
    if (target) return crops.find((c) => c.code === target)
    const inSeason = crops.find((c) => c.code === seasonal)
    return inSeason?.packCount ? inSeason : crops.find((c) => c.firstPot && c.packCount > 0) || inSeason
  })
  const prefix = 'ihomy:garden:windowsill:v1:' + props.member + ':' + props.family + ':'
  const sessionValues = new Map()
  function get(key) {
    if (props.preview) return sessionValues.get(key) ?? null
    try {
      return localStorage.getItem(prefix + key)
    } catch {
      return sessionValues.get(key) ?? null
    }
  }
  function put(key, value) {
    if (value === null) sessionValues.delete(key)
    else sessionValues.set(key, value)
    if (props.preview) return
    try {
      if (value === null) localStorage.removeItem(prefix + key)
      else localStorage.setItem(prefix + key, value)
    } catch {
      /* The current session can still use the server. */
    }
  }
  const draft = ref(0)
  const step = computed(() => {
    const p = plot.value
    if (!p) return -1
    if (p.cropCode) return p.harvestable || p.lastWateredAt ? 7 : 6
    if (p.mine !== true) return p.prepared || p.venue === 'BACKYARD' ? 8 : 0
    if (p.id === homePlot.value?.id && mail.value && !p.prepared && !draft.value) return -1
    if (p.prepared || p.venue === 'BACKYARD') return draft.value ? Math.max(2, Math.min(5, draft.value)) : 8
    return Math.min(1, draft.value)
  })
  function saveDraft(n) {
    startedWith.value = n ? crop.value?.code : null
    put('seed:' + plot.value.id, startedWith.value || null)
    draft.value = n
    put('draft:' + plot.value.id, n ? String(n) : null)
  }
  let epoch = 0
  function careAction(type, p) {
    const last = type === 'water' ? p.lastWateredAt : p.lastSunAt
    return type + ':' + p.id + ':' + p.plantedAt + (last ? ':' + last : '')
  }
  function restoreDraft() {
    const p = plot.value
    retryWater.value = canWater.value && !!get('action:' + careAction('water', p))
    retrySun.value = canSun.value && !!get('action:' + careAction('sun', p))
    startedWith.value = p?.mine && !p.cropCode ? get('seed:' + p.id) : null
    const n = p?.mine && !p.cropCode ? Number(get('draft:' + p.id)) : 0
    draft.value = Number.isInteger(n) && n >= 0 && n <= 5 ? n : 0
    if (!p?.mine) return
    if (p.prepared) put('action:prepare:' + p.id, null)
    if (p.cropCode) {
      receipt.value = null
      saveDraft(0)
      put('action:sow:' + p.id + ':' + p.cropCode, null)
      if (p.lastWateredAt) put('action:water:' + p.id + ':' + p.plantedAt, null)
    }
  }
  function selectPlot(id) {
    if (id != null && !world.value?.plots?.some((p) => p.id === id)) return false
    if (id === selectedId.value) return true
    selectedId.value = id
    receipt.value = null
    harvestError.value = ''
    replayedWater.value = null
    restoreDraft()
    return true
  }
  let alive = true
  let timer
  async function refreshEntry() {
    if (props.preview || !props.member) return
    try {
      const data = await gardenApi.entry()
      if (alive) entry.value = data
    } catch {
      /* A background read can be retried on focus. */
    }
  }
  async function refresh(allowRetry = true) {
    if ((props.preview && !review) || busy.value || !alive || !inside.value) return
    const ticket = ++epoch
    try {
      const data = await api.panorama()
      if (!alive || ticket !== epoch) return
      world.value = data
      restoreDraft()
      failed.value = false
    } catch (error) {
      if (!alive || ticket !== epoch) return
      // Concurrent visits can briefly contend while the server prepares a family's plots.
      if (allowRetry !== false && (error?.code === 500 || error?.response?.status === 500)) {
        await new Promise((resolve) => setTimeout(resolve, 350))
        if (alive && ticket === epoch) await refresh(false)
      } else failed.value = true
    }
  }
  async function write(action, send, onError) {
    if ((props.preview && !review) || !alive || busy.value) return null
    const name = 'action:' + action
    const key = get(name) || ulid()
    put(name, key)
    busy.value = true
    epoch++
    try {
      const data = await send(key)
      if (!alive) return null
      put(name, null)
      failed.value = false
      return data
    } catch (error) {
      if (alive) {
        failed.value = true
        onError?.(error)
      }
      return null
    } finally {
      busy.value = false
    }
  }
  async function openMail() {
    if (!mail.value && letters.value.length) return true
    const result = await write('mail:' + (world.value?.letters?.at(-1)?.date || 'today'), gardenApi.openMail)
    if (result) letters.value = result.letters || []
    await refresh()
    if (!result && mail.value && alive) failed.value = true
    return !!result
  }
  async function advance(previous) {
    const p = plot.value
    if (p?.mine !== true || busy.value || reconciling.value || step.value !== previous) return
    if (previous === 6) return water()
    if (previous === 8 && crop.value) {
      receipt.value = null
      saveDraft(2)
      return
    }
    let result
    if (previous === 1) result = await write('prepare:' + p.id, (key) => api.prepare(p.id, key))
    else if (previous === 5 && crop.value) {
      const code = crop.value.code
      result = await write('sow:' + p.id + ':' + code, (key) => api.sow(p.slotNo, code, key, p.venue))
    } else if (previous >= 0 && previous < 5) {
      saveDraft(previous + 1)
      return
    }
    if (result?.plot && alive && plot.value?.id === p.id) {
      Object.assign(p, result.plot)
      saveDraft(previous === 1 ? 2 : 0)
    }
    await refresh()
    if (!result && plot.value?.id === p.id && previous === 1 && plot.value.prepared && !plot.value.cropCode && draft.value < 2) saveDraft(2)
    if (!result && plot.value?.id === p.id && step.value === previous && alive) failed.value = true
  }
  async function care(type) {
    const watering = type === 'water'
    if (!(watering ? canWater.value : canSun.value) || busy.value || reconciling.value) return
    const p = plot.value
    const { id, plantedAt } = p
    const action = careAction(type, p)
    const retry = watering ? retryWater : retrySun
    if (watering) replayedWater.value = null
    reconciling.value = true
    try {
      const result = await write(action, (key) => api[type](id, key))
      if (!alive) return
      if (plot.value?.id === id) retry.value = !result
      if (result?.plot?.id === id) {
        world.value.plots = world.value.plots.map((item) => (item.id === id ? result.plot : item))
        if (watering && result.alreadyDone && result.plot.plantedAt === plantedAt) replayedWater.value = careAction(type, result.plot)
      }
      await refresh()
      if (!alive || result) return
      const current = world.value?.plots?.find((item) => item.id === id)
      if (current && careAction(type, current) !== action) put('action:' + action, null)
      else failed.value = true
    } finally {
      reconciling.value = false
    }
  }
  function water() {
    return care('water')
  }
  async function harvest() {
    const p = plot.value
    if (p?.mine !== true || !p.cropCode || !p.harvestable || !p.plantedAt || busy.value || reconciling.value) return
    const plantedAt = p.plantedAt
    const name = p.cropName || crop.value?.name
    const action = 'harvest:' + p.id + ':' + plantedAt
    reconciling.value = true
    harvestError.value = ''
    try {
      const result = await write(
        action,
        (key) => api.harvest(p.id, key),
        (error) => {
          if (plot.value?.id === p.id)
            harvestError.value = error.response?.data?.message || (Number.isInteger(error.code) ? error.message : '')
        },
      )
      if (!alive) return
      if (result?.plot?.id === p.id) {
        world.value.plots = world.value.plots.map((item) => (item.id === p.id ? result.plot : item))
        if (plot.value?.id === p.id) {
          saveDraft(0)
          receipt.value = result.alreadyDone ? { alreadyDone: true } : { name, quantity: result.yieldQty, coins: result.coinsGranted }
        }
      }
      await refresh()
      if (!alive) return
      if (plot.value?.id === p.id && plot.value.plantedAt !== plantedAt) {
        put('action:' + action, null)
        harvestError.value = ''
      } else if (!result && !harvestError.value) failed.value = true
    } finally {
      reconciling.value = false
    }
  }
  function onVisible() {
    if (document.hidden || props.preview) return
    if (inside.value) refresh()
    else refreshEntry()
  }
  watch(
    inside,
    (active) => {
      if (props.preview) return
      clearInterval(timer)
      if (active) {
        refresh()
        timer = setInterval(onVisible, 60000)
      } else {
        epoch++
        refreshEntry()
      }
    },
    { immediate: true },
  )
  onMounted(() => {
    if (!props.preview) {
      window.addEventListener('focus', onVisible)
      document.addEventListener('visibilitychange', onVisible)
    }
  })
  onBeforeUnmount(() => {
    alive = false
    clearInterval(timer)
    window.removeEventListener('focus', onVisible)
    document.removeEventListener('visibilitychange', onVisible)
  })
  return {
    entry,
    world,
    plot,
    homePlot,
    selectPlot,
    step,
    mail,
    letters,
    busy: computed(() => busy.value || reconciling.value),
    canWater,
    canSun,
    retrySun,
    sun: () => care('sun'),
    retryWater,
    alreadyWatered: computed(() => !!plot.value?.cropCode && !canWater.value && replayedWater.value === careAction('water', plot.value)),
    water,
    failed,
    crop,
    receipt,
    harvestError,
    openMail,
    advance,
    harvest,
    refresh,
  }
}
