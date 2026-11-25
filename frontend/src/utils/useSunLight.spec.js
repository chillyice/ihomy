import { createRenderer, nextTick } from 'vue'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import request from '@/api/request'
import { useSunLight } from './useSunLight'
import { getSunScene } from './windowLight'
import { CASEMENTS } from './windowSash'

const { applyAuto } = vi.hoisted(() => ({ applyAuto: vi.fn() }))
vi.mock('@/api/request', () => ({ default: { get: vi.fn() } }))
vi.mock('@/stores/theme', () => ({ useThemeStore: () => ({ applyAuto }) }))
vi.mock('@/theme', () => ({ setSunContext: vi.fn() }))
vi.mock('gsap', () => ({ gsap: { to: () => ({ kill() {} }) } }))

function sunDay(date, peak = 60) {
  const slots = Array.from({ length: 288 }, (_, i) => ({
    time: [Math.floor(i / 12), (i % 12) * 5].map((n) => String(n).padStart(2, '0')).join(':'),
    altitude: peak * Math.cos(((i - 144) * Math.PI) / 144),
    azimuth: 180,
  }))
  return { date, timezone: 'Asia/Shanghai', sunrise: '06:00', sunset: '18:00', slots }
}

const renderer = createRenderer({
  createComment: () => ({}),
  insert() {},
  remove() {},
  parentNode: () => null,
  nextSibling: () => null,
})
let app
let light
async function mountLight(day) {
  request.get.mockResolvedValueOnce(day)
  app = renderer.createApp({
    setup() {
      light = useSunLight()
      return () => null
    },
  })
  app.mount({})
  await Promise.resolve()
  await nextTick()
}

beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2026-11-25T04:02:00Z'))
  request.get.mockReset()
  applyAuto.mockClear()
  vi.stubGlobal('localStorage', { getItem: () => null, setItem: vi.fn() })
  vi.stubGlobal('sessionStorage', { getItem: () => null, setItem: vi.fn() })
  vi.stubGlobal('window', new EventTarget())
  vi.stubGlobal('document', Object.assign(new EventTarget(), { hidden: false, documentElement: { classList: { toggle() {} } } }))
})
afterEach(() => {
  app?.unmount()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  vi.useRealTimers()
})

it.each([null, 8])('uses the first observation’s time zone when opened early, honoring a manual choice of %s', async (manualSlot) => {
  const pending = Promise.withResolvers()
  await mountLight(pending.promise)
  light.startLightTest()
  if (manualSlot !== null) light.setSlot(manualSlot)
  pending.resolve({ ...sunDay('2026-11-24'), timezone: 'America/Chicago' })
  await pending.promise
  await nextTick()
  expect(light.slotIdx.value).toBe(manualSlot ?? 264)
})

it('freezes the live slot on entry, evaluates both sources identically and resumes the local solar clock on exit', async () => {
  const day = sunDay('2026-11-25')
  await mountLight(day)
  expect(light.slotIdx.value).toBe(144)
  const before = light.sunScene.value
  light.startLightTest()
  await vi.advanceTimersByTimeAsync(500)
  expect(light.lightTestPaused.value).toBe(true)
  expect(light.sunScene.value).toEqual(before)
  light.pauseLightTest()
  await vi.advanceTimersByTimeAsync(208)
  expect(light.slotIdx.value).toBe(145)
  light.setSlot(8)
  await nextTick()
  expect(light.sunScene.value).toEqual(getSunScene(day, 8))
  expect(applyAuto).toHaveBeenLastCalledWith(true)

  const otherDay = sunDay('2026-06-21', 76)
  request.get.mockResolvedValueOnce(otherDay)
  expect(await light.loadSunInfoForDate(otherDay.date)).toBe(true)
  const latest = sunDay(day.date, 59)
  light.applySunInfo(latest)
  light.effectsEnabled.value = false
  await nextTick()
  light.effectsEnabled.value = true
  await nextTick()
  await vi.advanceTimersByTimeAsync(3 * 60_000)
  expect(light.sunInfo.value).toEqual(latest)
  expect(light.activeSunInfo.value.date).toBe(otherDay.date)
  expect(light.sunScene.value).toEqual(getSunScene(otherDay, 8))

  light.stopLightTest()
  await nextTick()
  expect(light.slotIdx.value).toBe(145)
  expect(light.sunScene.value).toEqual(getSunScene(latest, 145))
  expect(applyAuto).toHaveBeenLastCalledWith(false)
})

it('discards superseded date selections and preview results arriving after exit and re-entry', async () => {
  const day = sunDay('2026-11-25')
  await mountLight(day)
  const receivedAt = light.sunInfoAt.value
  light.startLightTest()
  const earlier = Promise.withResolvers()
  const later = sunDay('2026-06-21')
  request.get.mockReturnValueOnce(earlier.promise).mockResolvedValueOnce(later)
  const old = light.loadSunInfoForDate('2026-03-20')
  expect(await light.loadSunInfoForDate(later.date)).toBe(true)
  earlier.resolve(sunDay('2026-03-20'))
  expect(await old).toBe(false)
  expect(light.activeSunInfo.value.date).toBe(later.date)
  expect(light.sunInfoAt.value).toBe(receivedAt)

  const tail = Promise.withResolvers()
  request.get.mockReturnValueOnce(tail.promise)
  const abandoned = light.loadSunInfoForDate('2026-12-22')
  light.stopLightTest()
  light.startLightTest()
  tail.resolve(sunDay('2026-12-22'))
  expect(await abandoned).toBe(false)
  expect(light.activeSunInfo.value.date).toBe(day.date)
})

it('refreshes the live day once at local midnight without changing a paused preview', async () => {
  vi.setSystemTime(new Date('2026-11-25T15:59:50Z'))
  const day = sunDay('2026-11-25')
  await mountLight(day)
  light.startLightTest()
  const midnight = Promise.withResolvers()
  request.get.mockReturnValueOnce(midnight.promise)
  await vi.advanceTimersByTimeAsync(10_000)
  document.dispatchEvent(new Event('visibilitychange'))
  expect(request.get).toHaveBeenCalledTimes(2)
  midnight.resolve(sunDay('2026-11-26'))
  await Promise.resolve()
  expect(light.activeSunInfo.value.date).toBe(day.date)
  expect(light.slotIdx.value).toBe(287)
  light.stopLightTest()
  expect(light.activeSunInfo.value.date).toBe('2026-11-26')
  expect(light.slotIdx.value).toBe(0)
  expect(request.get).toHaveBeenCalledTimes(2)
})

it('uses the same weather gate and scene rotation and keeps simulated storms out of persisted window state', async () => {
  vi.spyOn(Math, 'random').mockReturnValue(0.9)
  await mountLight(sunDay('2026-11-25'))
  light.weather.value = { condition: 'rain', precipLevel: 2 }
  await nextTick()
  const liveRain = light.rainParticles.value.length
  const initialSashes = light.windowSashes.value
  light.startLightTest()
  light.setWeather('rain', 2)
  await nextTick()
  expect(light.rainParticles.value).toHaveLength(liveRain)
  const dust = light.dustParticles.value
  light.setWeather('thunder', 4)
  await nextTick()
  light.rearrangeScene()
  expect(light.dustParticles.value).not.toBe(dust)
  expect(light.windowSashes.value).toBe(CASEMENTS)
  expect(sessionStorage.setItem).toHaveBeenLastCalledWith('ihomy:window:last-pose', 'open')
  light.weatherEffectEnabled.value = false
  expect(light.weatherMode.value).toBe('clear')
  light.stopLightTest()
  expect(light.weatherMode.value).toBe('clear')
  expect(light.windowSashes.value).not.toBe(initialSashes)
  light.weatherEffectEnabled.value = true
  expect(light.weatherMode.value).toBe('rain')
  expect(light.precipLevel.value).toBe(2)
})
