import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { aiApi, albumApi, photoApi } from '@/api'
import { readWeatherBgConfig, saveWeatherBgConfig, weatherArtwork, weatherSceneThemes, weatherStyleIds } from '@/utils/weatherBackground'
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
    const prompts = weatherStyleIds.map(
      (style) => weatherArtwork(weather, { ...config, style, sceneTheme: 'custom', scene: '河岸' }, date).prompt,
    )
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
    expect(config).toMatchObject({ style: 'watercolor', sceneTheme: 'custom', scene: '河岸' })
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
    saveWeatherBgConfig({ ...config, style: 'oil', sceneTheme: 'custom', scene: '石桥' })
    await home.load(weather)
    expect(aiApi.image.mock.lastCall[0].prompt).toContain('石桥')
    saveWeatherBgConfig(config)
    await home.load(weather)
    expect(aiApi.image).toHaveBeenCalledTimes(3)
  })

  it('rotates through all settings on new images and keeps cached loads out of the rotation', async () => {
    const config = readWeatherBgConfig()
    const themes = weatherSceneThemes.filter((id) => !['auto', 'custom'].includes(id))
    const home = useWeatherBg()
    const chosen = []
    for (let i = 0; i <= themes.length; i++) {
      const sample = { ...weather, city: 'test-' + i }
      await home.load(sample)
      const last = localStorage.getItem('ihomy:weather-bg:last-auto-scene')
      chosen.push(last)
      await home.load(sample)
      expect(localStorage.getItem('ihomy:weather-bg:last-auto-scene')).toBe(last)
      expect(weatherArtwork(sample, config, date).key).toBe(
        [sample.city, sample.text, sample.iconCode, 'day', '秋', JSON.stringify(['rain', 'photo', '', '2048x2048', false])].join('|'),
      )
    }
    expect(chosen.slice(0, -1).sort()).toEqual(themes.sort())
    expect(chosen.at(-1)).toBe(chosen[0])
    expect(aiApi.image).toHaveBeenCalledTimes(themes.length + 1)
  })

  it('keeps the chosen location independent of style, viewpoint, and a previous custom description', async () => {
    const config = readWeatherBgConfig()
    saveWeatherBgConfig({ ...config, sceneTheme: 'cyber', scene: '旧的河岸取景' })
    const home = useWeatherBg()
    await home.load(weather)
    const first = aiApi.image.mock.lastCall[0].prompt
    expect(first).toContain('写实风景摄影')
    expect(first).toMatch(/机械臂|无人吊机|磁轨/)
    expect(first).not.toContain('旧的河岸取景')
    expect(localStorage.getItem('ihomy:weather-bg:last-auto-scene')).toBeNull()
    saveWeatherBgConfig({ ...config, sceneTheme: 'coast' })
    await home.load(weather)
    expect(aiApi.image.mock.lastCall[0].prompt).toMatch(/礁盘|防波堤|沙丘/)
    saveWeatherBgConfig({ ...config, sceneTheme: 'cyber' })
    await home.load(weather)
    expect(aiApi.image).toHaveBeenCalledTimes(2)
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
