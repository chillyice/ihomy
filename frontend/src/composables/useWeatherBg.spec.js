import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { aiApi, albumApi, photoApi } from '@/api'
import { readWeatherBgConfig, saveWeatherBgConfig, weatherArtwork, weatherStyleIds } from '@/utils/weatherBackground'
import { useWeatherBg } from './useWeatherBg'

vi.mock('@/api', () => ({
  aiApi: { status: vi.fn(), image: vi.fn() },
  albumApi: { list: vi.fn() },
  photoApi: { saveFromUrl: vi.fn() },
}))
vi.mock('@/stores/user', () => ({ useUserStore: () => ({ isLoggedIn: true, userInfo: { familyId: 7 } }) }))

const weather = { city: '济南', condition: 'rain', iconCode: '305', text: '小雨', precipLevel: 1 }
const date = new Date(2026, 10, 25, 16)

describe('天气窗 AI 风景', () => {
  beforeEach(() => {
    const data = new Map()
    vi.stubGlobal('localStorage', { getItem: (key) => data.get(key) ?? null, setItem: (key, value) => data.set(key, value) })
    vi.clearAllMocks()
    aiApi.status.mockResolvedValue({ weatherImage: { available: true } })
    aiApi.image.mockResolvedValue([{ b64_json: 'image' }])
    albumApi.list.mockResolvedValue([{ id: 8, name: 'AI 生图' }])
    photoApi.saveFromUrl.mockResolvedValue([{ url: '/files/weather.png' }])
  })

  afterEach(() => vi.unstubAllGlobals())

  it('uses physical cues and the same no-lettering direction in every preset', () => {
    const config = readWeatherBgConfig()
    expect(config.style).toBe('photo')
    const prompts = weatherStyleIds.map((style) => weatherArtwork(weather, { ...config, style, scene: '河岸' }, date).prompt)
    expect(new Set(prompts).size).toBe(weatherStyleIds.length)
    for (const prompt of prompts) {
      expect(prompt).toContain('涟漪')
      expect(prompt).toContain('傍晚')
      expect(prompt).toContain('不要出现文字')
      expect(prompt).not.toMatch(/济南|小雨|11月25日/)
    }
    expect(weatherArtwork({ ...weather, condition: 'cloud', iconCode: '104' }, config, date).prompt).toContain('漫射光')
  })

  it('uses saved styles, including legacy names, and restores photography for an unknown style', () => {
    localStorage.setItem('ihomy:weather-bg-config:v1', JSON.stringify({ style: '水彩手绘风', scene: ' 河岸 ' }))
    const config = readWeatherBgConfig()
    expect(config).toMatchObject({ style: 'watercolor', scene: '河岸' })
    const { prompt } = weatherArtwork(weather, config, date)
    expect(prompt).toContain('湿画法')
    expect(prompt).toContain('河岸')
    saveWeatherBgConfig({ ...config, style: 'missing' })
    expect(readWeatherBgConfig().style).toBe('photo')
  })

  it('sends one paid request and album write for simultaneous consumers, then reuses the saved photo', async () => {
    const home = useWeatherBg()
    const shell = useWeatherBg()
    await Promise.all([home.load(weather), shell.load(weather)])
    await useWeatherBg().load(weather)
    expect(home.weatherBg.value).toBe('/files/weather.png')
    expect(shell.weatherBg.value).toBe(home.weatherBg.value)
    expect(aiApi.image).toHaveBeenCalledTimes(1)
    expect(aiApi.image).toHaveBeenCalledWith(
      { prompt: expect.stringContaining('写实风景摄影'), size: '2048x2048', watermark: false },
      'WEATHER_IMAGE',
    )
    expect(photoApi.saveFromUrl).toHaveBeenCalledTimes(1)
    expect(photoApi.saveFromUrl.mock.calls[0][1].description).toBe('济南 · 小雨')

    const config = readWeatherBgConfig()
    saveWeatherBgConfig({ ...config, style: 'oil' })
    await home.load(weather)
    expect(aiApi.image.mock.lastCall[0].prompt).toContain('厚笔触')
    saveWeatherBgConfig({ ...config, style: 'oil', scene: '石桥' })
    await home.load(weather)
    expect(aiApi.image.mock.lastCall[0].prompt).toContain('石桥')
    saveWeatherBgConfig(config)
    await home.load(weather)
    expect(aiApi.image).toHaveBeenCalledTimes(3)
  })

  it('does not request images when disabled and retries a failed generation on the next load', async () => {
    const config = readWeatherBgConfig()
    const home = useWeatherBg()
    saveWeatherBgConfig({ ...config, enabled: false })
    await home.load(weather)
    expect(aiApi.image).not.toHaveBeenCalled()
    saveWeatherBgConfig(config)
    aiApi.image.mockRejectedValueOnce(new Error('unavailable'))
    await home.load(weather)
    await home.load(weather)
    expect(aiApi.image).toHaveBeenCalledTimes(2)
    expect(home.weatherBg.value).toBe('/files/weather.png')
  })
})
