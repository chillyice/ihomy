import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { gardenApi } from '@/api'
import { ulid } from '@/utils/tools/generatorTool'

// The server owns the pot. Only unfinished hand movements and pending request keys live on this device.
export function useWindowsill(props, inside) {
  const entry = ref(null)
  const world = ref(null)
  const letters = ref([])
  const busy = ref(false)
  const failed = ref(false)
  const receipt = ref(null)
  const harvestError = ref('')
  const reconciling = ref(false)
  const retryWater = ref(false)
  const replayedWater = ref(null)
  const plot = computed(() => world.value?.plots?.find((p) => p.mine && p.venue === 'WINDOWSILL' && p.slotNo === 0))
  const canWater = computed(() => {
    const p = plot.value
    return (
      !!p?.plantedAt && !!p.cropCode && !p.harvestable && (p.waterCooldownSec === 0 || (!p.lastWateredAt && p.waterCooldownSec == null))
    )
  })
  const mail = computed(() => world.value?.letters?.some((l) => l.claimable))
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
    try {
      return localStorage.getItem(prefix + key)
    } catch {
      return sessionValues.get(key) ?? null
    }
  }
  function put(key, value) {
    if (value === null) sessionValues.delete(key)
    else sessionValues.set(key, value)
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
    if (mail.value && !p.prepared && !draft.value) return -1
    if (p.prepared && !draft.value) return 8
    return p.prepared ? Math.max(2, Math.min(5, draft.value)) : Math.min(1, draft.value)
  })
  function saveDraft(n) {
    startedWith.value = n ? crop.value?.code : null
    put('seed:' + plot.value.id, startedWith.value || null)
    draft.value = n
    put('draft:' + plot.value.id, n ? String(n) : null)
  }
  let epoch = 0
  function waterAction(p) {
    // Keep the existing key for the first pour. Each later pour starts a new cooldown.
    return 'water:' + p.id + ':' + p.plantedAt + (p.lastWateredAt ? ':' + p.lastWateredAt : '')
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
  async function refresh() {
    if (props.preview || busy.value || !alive || !inside.value) return
    const ticket = ++epoch
    try {
      const data = await gardenApi.panorama()
      if (!alive || ticket !== epoch) return
      world.value = data
      retryWater.value = canWater.value && !!get('action:' + waterAction(plot.value))
      if (plot.value) {
        const n = Number(get('draft:' + plot.value.id))
        startedWith.value = plot.value.cropCode ? null : get('seed:' + plot.value.id)
        draft.value = Number.isInteger(n) && n >= 0 && n <= 5 && !plot.value.cropCode ? n : 0
        const p = plot.value
        if (p.prepared) put('action:prepare:' + p.id, null)
        if (p.cropCode) {
          receipt.value = null
          saveDraft(0)
          put('action:sow:' + p.id + ':' + p.cropCode, null)
          if (p.lastWateredAt) put('action:water:' + p.id + ':' + p.plantedAt, null)
        }
      }
      failed.value = false
    } catch {
      if (alive && ticket === epoch) failed.value = true
    }
  }
  async function write(action, send, onError) {
    if (props.preview || !alive || busy.value) return null
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
    if (!p || busy.value || reconciling.value || step.value !== previous) return
    if (previous === 6) return water()
    if (previous === 8 && crop.value) {
      receipt.value = null
      saveDraft(2)
      return
    }
    let result
    if (previous === 1) result = await write('prepare:' + p.id, (key) => gardenApi.prepare(p.id, key))
    else if (previous === 5 && crop.value) {
      const code = crop.value.code
      result = await write('sow:' + p.id + ':' + code, (key) => gardenApi.sow(p.slotNo, code, key))
    } else if (previous >= 0 && previous < 5) {
      saveDraft(previous + 1)
      return
    }
    if (result?.plot && alive) {
      Object.assign(p, result.plot)
      saveDraft(previous === 1 ? 2 : 0)
    }
    await refresh()
    if (!result && previous === 1 && plot.value?.prepared && !plot.value.cropCode && draft.value < 2) saveDraft(2)
    if (!result && step.value === previous && alive) failed.value = true
  }
  async function water() {
    if (props.preview || !canWater.value || busy.value || reconciling.value) return
    const p = plot.value
    const { id, plantedAt, lastWateredAt } = p
    const action = waterAction(p)
    replayedWater.value = null
    reconciling.value = true
    try {
      const result = await write(action, (key) => gardenApi.water(id, key))
      if (!alive) return
      retryWater.value = !result
      if (result?.plot?.id === id) {
        world.value.plots = world.value.plots.map((item) => (item.id === id ? result.plot : item))
        if (result.alreadyDone && result.plot.plantedAt === plantedAt) replayedWater.value = waterAction(result.plot)
      }
      await refresh()
      if (!alive || result) return
      const current = plot.value
      if (current && (current.id !== id || current.plantedAt !== plantedAt || current.lastWateredAt !== lastWateredAt))
        put('action:' + action, null)
      else failed.value = true
    } finally {
      reconciling.value = false
    }
  }
  async function harvest() {
    const p = plot.value
    if (props.preview || !p?.cropCode || !p.harvestable || !p.plantedAt || busy.value || reconciling.value) return
    const plantedAt = p.plantedAt
    const name = p.cropName || crop.value?.name
    const action = 'harvest:' + p.id + ':' + plantedAt
    reconciling.value = true
    harvestError.value = ''
    try {
      const result = await write(
        action,
        (key) => gardenApi.harvest(p.id, key),
        (error) => {
          harvestError.value = error.response?.data?.message || (Number.isInteger(error.code) ? error.message : '')
        },
      )
      if (!alive) return
      if (result?.plot?.id === p.id) {
        world.value.plots = world.value.plots.map((item) => (item.id === p.id ? result.plot : item))
        saveDraft(0)
        receipt.value = { name, quantity: result.yieldQty, coins: result.coinsGranted }
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
    step,
    mail,
    letters,
    busy: computed(() => busy.value || reconciling.value),
    canWater,
    retryWater,
    alreadyWatered: computed(() => !!plot.value?.cropCode && !canWater.value && replayedWater.value === waterAction(plot.value)),
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
