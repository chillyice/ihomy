import { createRenderer, nextTick, reactive } from 'vue'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import request from '@/api/request'
import { useSunLight } from './useSunLight'
import { getSunScene } from './windowLight'
import { CASEMENTS } from './windowSash'

const applyAuto = vi.fn()
const themeStore = reactive({ applyAuto, autoMode: true })
vi.mock('@/api/request', () => ({ default: { get: vi.fn() } }))
vi.mock('@/stores/theme', () => ({ useThemeStore: () => themeStore }))
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
  themeStore.autoMode = true
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

it('applies the current daylight immediately when auto mode is selected again', async () => {
  themeStore.autoMode = false
  await mountLight(sunDay('2026-11-25'))
  applyAuto.mockClear()
  themeStore.autoMode = true
  await nextTick()
  expect(applyAuto).toHaveBeenCalledWith(false, { sweep: true })
  expect(request.get).toHaveBeenCalledTimes(1)
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
  expect(applyAuto).toHaveBeenLastCalledWith(true, { sweep: false })

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
  expect(applyAuto).toHaveBeenLastCalledWith(false, { sweep: true })
})

it('keeps the selected day and manual time through stepping, pending playback and a live refresh', async () => {
  const today = sunDay('2026-11-25')
  const summer = sunDay('2026-06-21', 76)
  await mountLight(today)
  light.startLightTest()
  const pending = Promise.withResolvers()
  request.get.mockReturnValueOnce(pending.promise)
  const selection = light.loadSunInfoForDate(summer.date)
  light.setSlot(100)
  light.stepLightTest(1)
  expect(light.slotIdx.value).toBe(101)
  light.stepLightTest(-1)
  light.pauseLightTest()
  light.applySunInfo(today)
  await vi.advanceTimersByTimeAsync(500)
  expect(light.previewDate.value).toBe(summer.date)
  expect(light.activeSunInfo.value).toBeNull()
  expect(light.slotIdx.value).toBe(100)

  pending.resolve(summer)
  expect(await selection).toBe(true)
  await vi.advanceTimersByTimeAsync(208)
  expect(light.sunScene.value).toEqual(getSunScene(summer, 101))
  light.stepLightTest(-1)
  await vi.advanceTimersByTimeAsync(500)
  expect(light.sunScene.value).toEqual(getSunScene(summer, 100))
  light.stepLightTest(1)
  expect(light.sunScene.value).toEqual(getSunScene(summer, 101))
})

it('retains a selected date and time for retry when the server returns today', async () => {
  const today = sunDay('2026-11-25')
  const summer = sunDay('2026-06-21')
  await mountLight(today)
  light.startLightTest()
  light.setSlot(115)
  request.get.mockResolvedValueOnce(today)
  expect(await light.loadSunInfoForDate(summer.date)).toBe(false)
  expect(light.previewDate.value).toBe(summer.date)
  expect(light.previewLoadFailed.value).toBe(true)
  expect(light.activeSunInfo.value).toBeNull()
  request.get.mockResolvedValueOnce(summer)
  expect(await light.loadSunInfoForDate(summer.date)).toBe(true)
  expect(light.previewLoadFailed.value).toBe(false)
  expect(light.sunScene.value).toEqual(getSunScene(summer, 115))
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

it('shows the selected slot’s lamp and shadow mask immediately when seeking or playing past sunset', async () => {
  await mountLight(sunDay('2026-11-25'))
  light.startLightTest()
  light.setSlot(144)
  await nextTick()
  expect(light.lampDivOpacity.value).toBe(0)
  light.setSlot(217)
  await nextTick()
  expect(light.lightLayerOpacity.value).toBe(0)
  expect(light.lampStrengthAnim.value).toBe(1)
  const nightMask = light.lampMask.value
  light.setTestSpeed(8)
  light.pauseLightTest()
  await vi.advanceTimersByTimeAsync(260)
  expect(light.slotIdx.value).toBe(227)
  expect(light.lampMask.value).toBe(nightMask)
  expect(light.lampDivOpacity.value).toBe(0.3)
  light.setSlot(144)
  await nextTick()
  expect(light.lampMask.value).toBe('none')
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

// Jinan /public/sun-info samples: near the solstice the sun crosses each side
// of the south window during daylight; in November it crosses the horizon first.
it.each([
  {
    date: '2026-06-21',
    sunrise: '04:53',
    sunset: '19:33',
    noon: 147,
    observations: [
      [58, -1.37, 59.09],
      [60, 0.36, 60.59],
      [103, 41.63, 89.88],
      [104, 42.63, 90.63],
      [147, 76.78, 181.73],
      [189, 42.94, 269.13],
      [190, 41.94, 269.89],
      [191, 40.93, 270.63],
      [234, -0.24, 299.93],
      [235, -1.11, 300.68],
      [236, -1.97, 301.44],
    ],
  },
  {
    date: '2026-11-25',
    sunrise: '06:59',
    sunset: '16:57',
    noon: 144,
    observations: [
      [83, -1.56, 114.86],
      [84, -0.65, 115.6],
      [85, 0.25, 116.34],
      [144, 32.61, 180.45],
      [200, 2.3, 241.82],
      [202, 0.51, 243.33],
      [203, -0.38, 244.08],
      [204, -1.29, 244.82],
      [205, -2.2, 245.55],
    ],
  },
])(
  'keeps a shaded white surface brighter towards noon and darker towards night with the lamp off on $date',
  async ({ date, sunrise, sunset, noon, observations }) => {
    const day = { ...sunDay(date), sunrise, sunset }
    for (const [slot, altitude, azimuth] of observations) Object.assign(day.slots[slot], { altitude, azimuth })
    await mountLight(day)
    light.startLightTest()
    light.lampMode.value = 'off'
    for (const weather of ['clear', 'cloud', 'overcast', 'fog', 'rain', 'snow', 'thunder']) {
      light.setWeather(weather)
      for (const depth of [0, 70, 100]) {
        light.shadowDepth.value = depth
        const levels = observations.map(([slot]) => {
          light.setSlot(slot)
          const { background, opacity } = light.skyTint.value
          const [r, g, b] = background.slice(4, -1).split(',').map(Number)
          // White paper at the transparent edge of the diffuse gradient: multiply
          // by the sky tint and composite both black shadow layers at this point.
          const sky = 255 * (1 - opacity) + opacity * (0.2126 * r + 0.7152 * g + 0.0722 * b)
          return sky * (1 - light.roomShade.value) * (1 - light.castShade.value * light.lightLayerOpacity.value)
        })
        for (let i = 1; i < levels.length; i++) {
          const slot = observations[i][0]
          const delta = (levels[i] - levels[i - 1]) * (slot <= noon ? 1 : -1)
          expect(delta, `${weather}, depth ${depth}, ${day.slots[slot].time}`).toBeGreaterThanOrEqual(-0.001)
        }
        expect(levels.at(-1)).toBeCloseTo(levels[0], 8)
        expect(levels.at(-1)).toBe(levels.at(-2))
        expect(Math.max(...levels)).toBeGreaterThan(levels[0])
      }
    }
    await nextTick()
    expect(light.lampMask.value).toBe('none')
    expect(light.lampDivOpacity.value).toBe(0)
  },
)
