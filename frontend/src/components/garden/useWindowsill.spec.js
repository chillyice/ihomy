import { createRenderer, ref } from 'vue'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { gardenApi } from '@/api'
import { useWindowsill } from './useWindowsill'

vi.mock('@/api', () => ({ gardenApi: { panorama: vi.fn(), entry: vi.fn(), water: vi.fn() } }))
const renderer = createRenderer({ createComment: () => ({}), insert() {}, remove() {}, parentNode: () => null, nextSibling: () => null })
let app
let plot
function snapshot() {
  return { plots: [{ ...plot, mine: false, id: 33 }, { ...plot }], letters: [], unlockedCrops: [] }
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
