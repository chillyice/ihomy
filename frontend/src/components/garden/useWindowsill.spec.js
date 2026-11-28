import { createRenderer, ref } from 'vue'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { gardenApi } from '@/api'
import { useWindowsill } from './useWindowsill'

vi.mock('@/api', () => ({ gardenApi: { panorama: vi.fn(), entry: vi.fn(), water: vi.fn(), harvest: vi.fn(), sow: vi.fn() } }))
const renderer = createRenderer({ createComment: () => ({}), insert() {}, remove() {}, parentNode: () => null, nextSibling: () => null })
let app
let plot
function snapshot() {
  return {
    plots: [{ ...plot, mine: false, id: 33 }, { ...plot }],
    letters: [],
    unlockedCrops: [{ code: 'CATGRASS', name: 'Cat grass', packCount: 1 }],
  }
}
function mount(preview = false) {
  let garden
  app = renderer.createApp({
    setup() {
      garden = useWindowsill({ preview, member: 1, family: 2 }, ref(true))
      return () => null
    },
  })
  app.mount({})
  return garden
}
beforeEach(() => {
  vi.resetAllMocks()
  vi.stubGlobal('window', new EventTarget())
  vi.stubGlobal('document', new EventTarget())
  const values = new Map()
  vi.stubGlobal('localStorage', {
    getItem: (k) => values.get(k) ?? null,
    setItem: (k, v) => values.set(k, v),
    removeItem: (k) => values.delete(k),
  })
  plot = { id: 7, mine: true, venue: 'WINDOWSILL', slotNo: 0, cropCode: 'CATGRASS', plantedAt: '2026-11-28', lastWateredAt: null, stage: 0 }
  gardenApi.panorama.mockImplementation(async () => snapshot())
})
afterEach(() => {
  app.unmount()
  vi.unstubAllGlobals()
})

it('keeps the art review independent of the member garden', async () => {
  const garden = mount(true)
  await garden.refresh()
  expect(gardenApi.panorama).not.toHaveBeenCalled()
  expect(gardenApi.entry).not.toHaveBeenCalled()
})
it('reconciles an accepted watering after the reply was lost', async () => {
  const garden = mount()
  await garden.refresh()
  gardenApi.water.mockImplementation(async () => {
    plot.lastWateredAt = '2026-11-28T15:00:00'
    throw Error('offline')
  })
  await garden.advance(6)
  expect(garden.step.value).toBe(7)
  expect(garden.failed.value).toBe(false)
  await garden.advance(6)
  expect(gardenApi.water).toHaveBeenCalledTimes(1)
  expect(gardenApi.water.mock.calls[0][0]).toBe(7)
})
it('retries an unconfirmed action with the same key and ignores an older panorama', async () => {
  const garden = mount()
  await garden.refresh()
  let finish
  const stale = snapshot()
  gardenApi.panorama.mockImplementationOnce(
    () =>
      new Promise((resolve) => {
        finish = resolve
      }),
  )
  const read = garden.refresh()
  gardenApi.water.mockRejectedValueOnce(Error('offline')).mockImplementationOnce(async () => {
    plot.lastWateredAt = '2026-11-28T15:00:00'
    return { plot: { ...plot } }
  })
  await garden.advance(6)
  expect(garden.failed.value).toBe(true)
  await garden.advance(6)
  finish(stale)
  await read
  expect(garden.step.value).toBe(7)
  expect(gardenApi.water.mock.calls[1]).toEqual(gardenApi.water.mock.calls[0])
})

function emptyPot() {
  Object.assign(plot, { prepared: true, cropCode: null, plantedAt: null, stage: null, harvestable: false, lastWateredAt: null })
}

it('opens a family windowsill for observation and never routes an action to the owner’s pot', async () => {
  Object.assign(plot, { prepared: true, stage: 5, harvestable: true, thirsty: true })
  const garden = mount()
  await garden.refresh()
  expect(garden.selectPlot(33)).toBe(true)
  expect(garden.plot.value.id).toBe(33)
  expect(garden.canWater.value).toBe(false)
  await garden.advance(7)
  await garden.harvest()
  await garden.water()
  expect(gardenApi.harvest).not.toHaveBeenCalled()
  expect(gardenApi.water).not.toHaveBeenCalled()
  gardenApi.panorama.mockResolvedValueOnce({ plots: [{ ...plot }] })
  await garden.refresh()
  expect(garden.plot.value).toBeUndefined()
  await garden.harvest()
  expect(gardenApi.harvest).not.toHaveBeenCalled()
  garden.selectPlot(null)
  expect(garden.plot.value.id).toBe(7)
})

it('sows into the selected shared bed and restores unfinished seeds only for that bed', async () => {
  const bed = { ...plot, id: 47, venue: 'BACKYARD', ownerUid: '', slotNo: 3, cropCode: null, plantedAt: null, prepared: false }
  gardenApi.panorama.mockImplementation(async () => ({ ...snapshot(), plots: [{ ...plot }, { ...bed }] }))
  const garden = mount()
  await garden.refresh()
  garden.selectPlot(47)
  expect(garden.step.value).toBe(8)
  await garden.advance(8)
  await garden.advance(2)
  garden.selectPlot(null)
  expect(garden.step.value).toBe(6)
  garden.selectPlot(47)
  expect(garden.step.value).toBe(3)
  await garden.advance(3)
  await garden.advance(4)
  gardenApi.sow.mockImplementationOnce(async () => {
    Object.assign(bed, { cropCode: 'CATGRASS', plantedAt: '2026-11-29', stage: 0 })
    return { plot: { ...bed } }
  })
  await garden.advance(5)
  expect(gardenApi.sow).toHaveBeenCalledWith(3, 'CATGRASS', expect.any(String), 'BACKYARD')
  expect(garden.plot.value.id).toBe(47)
  expect(garden.step.value).toBe(6)
})

it('accepts one pour per cooldown and reuses the pending key after a failed revisit', async () => {
  Object.assign(plot, { stage: 2, lastWateredAt: '2026-11-27T10:00:00', thirsty: true, waterCooldownSec: 50 })
  const garden = mount()
  await garden.refresh()
  await garden.water()
  expect(gardenApi.water).not.toHaveBeenCalled()
  plot.waterCooldownSec = 0
  await garden.refresh()
  gardenApi.water.mockRejectedValueOnce(Error('offline')).mockImplementationOnce(async () => {
    Object.assign(plot, { lastWateredAt: '2026-11-28T10:00:00', thirsty: false, waterCooldownSec: 43200 })
    throw Error('reply lost')
  })
  await garden.water()
  expect(garden.failed.value).toBe(true)
  expect(garden.plot.value.thirsty).toBe(true)
  expect(garden.retryWater.value).toBe(true)
  await Promise.all([garden.water(), garden.water()])
  expect(gardenApi.water.mock.calls[1]).toEqual(gardenApi.water.mock.calls[0])
  expect(garden.canWater.value).toBe(false)
  expect(garden.failed.value).toBe(false)
  expect(garden.plot.value.stage).toBe(2)
  expect(garden.retryWater.value).toBe(false)
  await garden.water()
  expect(gardenApi.water).toHaveBeenCalledTimes(2)
  plot.waterCooldownSec = 0
  await garden.refresh()
  gardenApi.water.mockResolvedValueOnce({ plot: { ...plot, waterCooldownSec: 43200 } })
  await garden.water()
  expect(gardenApi.water.mock.calls[2][1]).not.toBe(gardenApi.water.mock.calls[0][1])
})

it('uses the current plot from a replayed watering receipt', async () => {
  Object.assign(plot, { stage: 2, lastWateredAt: '2026-11-27', thirsty: true, waterCooldownSec: 0 })
  const garden = mount()
  await garden.refresh()
  gardenApi.water.mockImplementationOnce(async () => {
    Object.assign(plot, { lastWateredAt: '2026-11-28', thirsty: false, waterCooldownSec: 43200 })
    return { alreadyDone: true, plot: { ...plot } }
  })
  await garden.water()
  expect(garden.alreadyWatered.value).toBe(true)
  expect(garden.canWater.value).toBe(false)
  emptyPot()
  await garden.refresh()
  expect(garden.alreadyWatered.value).toBe(false)
})

it('retains the ripe plant after a full store and sows another crop in the same soil after a confirmed harvest', async () => {
  plot.stage = 5
  const garden = mount()
  await garden.refresh()
  await garden.harvest()
  expect(gardenApi.harvest).not.toHaveBeenCalled()
  plot.harvestable = true
  await garden.refresh()
  expect(garden.step.value).toBe(7)
  gardenApi.harvest.mockRejectedValueOnce({ code: 409, message: 'Store full' }).mockImplementationOnce(async () => {
    emptyPot()
    return { plot: { ...plot }, yieldQty: 3, coinsGranted: 0 }
  })
  await garden.harvest()
  expect(garden.harvestError.value).toBe('Store full')
  expect(garden.plot.value.harvestable).toBe(true)
  await Promise.all([garden.harvest(), garden.harvest()])
  expect(gardenApi.harvest.mock.calls[1]).toEqual(gardenApi.harvest.mock.calls[0])
  expect(garden.receipt.value).toMatchObject({ name: 'Cat grass', quantity: 3, coins: 0 })
  expect(garden.step.value).toBe(8)
  await garden.harvest()
  expect(gardenApi.harvest).toHaveBeenCalledTimes(2)
  await garden.advance(8)
  await garden.refresh()
  expect(garden.step.value).toBe(2)
  expect(garden.receipt.value).toBe(null)
  for (const n of [2, 3, 4]) await garden.advance(n)
  gardenApi.sow.mockImplementationOnce(async () => {
    Object.assign(plot, { cropCode: 'CATGRASS', plantedAt: '2026-11-29', stage: 0 })
    return { plot: { ...plot } }
  })
  await garden.advance(5)
  expect(gardenApi.sow).toHaveBeenCalledOnce()
  expect(garden.step.value).toBe(6)
})

it('recovers the empty soil from the panorama after a lost harvest reply without guessing the yield', async () => {
  plot.harvestable = true
  const garden = mount()
  await garden.refresh()
  gardenApi.harvest.mockImplementationOnce(async () => {
    emptyPot()
    throw Error('offline')
  })
  await garden.harvest()
  expect(garden.step.value).toBe(8)
  expect(garden.receipt.value).toBe(null)
  expect(garden.failed.value).toBe(false)
  await garden.harvest()
  expect(gardenApi.harvest).toHaveBeenCalledOnce()
})

it('keeps the next planting when another visit has already replaced the harvested crop', async () => {
  plot.harvestable = true
  const garden = mount()
  await garden.refresh()
  gardenApi.harvest.mockImplementationOnce(async () => {
    Object.assign(plot, { plantedAt: '2026-11-29', harvestable: false, stage: 0 })
    throw Error('offline')
  })
  await garden.harvest()
  expect(garden.step.value).toBe(6)
  expect(garden.receipt.value).toBe(null)
  expect(garden.failed.value).toBe(false)
})
