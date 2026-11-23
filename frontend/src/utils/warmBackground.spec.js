import { describe, it, expect, vi } from 'vitest'

// 纯逻辑测试:把偏好同步依赖(api / user store)打桩,避免把 router / 浏览器环境拖进来
vi.mock('@/api', () => ({ userPrefApi: { all: async () => ({}), save: async () => {} } }))
vi.mock('@/stores/user', () => ({ useUserStore: () => ({ isLoggedIn: false }) }))

import { BG_PRESETS, DEFAULT_WARM_BG, parseWarmBg, presetById, warmBgLayer } from './warmBackground'

describe('暖居背景偏好解析', () => {
  it('空值/坏 JSON/非对象回落到默认(天气图)', () => {
    expect(parseWarmBg(null)).toEqual(DEFAULT_WARM_BG)
    expect(parseWarmBg('')).toEqual(DEFAULT_WARM_BG)
    expect(parseWarmBg('not-json')).toEqual(DEFAULT_WARM_BG)
    expect(parseWarmBg('[]')).toEqual(DEFAULT_WARM_BG)
    expect(parseWarmBg('"mine"')).toEqual(DEFAULT_WARM_BG)
  })

  it('合法对象按字段保留,未知来源与未知预设回落', () => {
    const p = parseWarmBg(JSON.stringify({ mode: 'mine', pick: 'upload', preset: 'sage', url: '/files/files/202611/bg.webp', opacity: 72 }))
    expect(p).toMatchObject({ mode: 'mine', pick: 'upload', preset: 'sage', url: '/files/files/202611/bg.webp', opacity: 72 })

    expect(parseWarmBg({ mode: 'weather', preset: 'nope' }).preset).toBe(DEFAULT_WARM_BG.preset)
    expect(parseWarmBg({ mode: 'weird', pick: 'weird' })).toMatchObject({ mode: 'weather', pick: 'preset' })
  })

  it('上传地址只收站内 /files/ 与 http(s),data: 一律丢弃', () => {
    expect(parseWarmBg({ mode: 'mine', pick: 'upload', url: 'javascript:alert(1)' }).url).toBe('')
    expect(parseWarmBg({ mode: 'mine', pick: 'upload', url: 'data:image/png;base64,xx' }).url).toBe('')
    expect(parseWarmBg({ mode: 'mine', pick: 'upload', url: 'https://cdn.example.com/a.jpg' }).url).toBe('https://cdn.example.com/a.jpg')
    expect(parseWarmBg({ mode: 'mine', pick: 'upload', url: '/files/files/202611/a.jpg' }).url).toBe('/files/files/202611/a.jpg')
  })

  it('浓度夹在 20~100 并取整', () => {
    expect(parseWarmBg({ opacity: 5 }).opacity).toBe(20)
    expect(parseWarmBg({ opacity: 300 }).opacity).toBe(100)
    expect(parseWarmBg({ opacity: 63.6 }).opacity).toBe(64)
    expect(parseWarmBg({ opacity: 'x' }).opacity).toBe(DEFAULT_WARM_BG.opacity)
  })
})

describe('暖居背景层样式', () => {
  const weather = 'https://cdn.example.com/w.jpg'

  it('天气来源:有天气图才出层,浓度固定 30%', () => {
    expect(warmBgLayer(DEFAULT_WARM_BG, { weatherUrl: '', isLoggedIn: true })).toBeNull()
    expect(warmBgLayer(DEFAULT_WARM_BG, { weatherUrl: weather, isLoggedIn: true })).toEqual({
      image: `url("${weather}")`,
      size: 'cover',
      opacity: 0.3,
    })
  })

  it('内置氛围:用预设 css 与用户浓度,游客也生效', () => {
    const pref = parseWarmBg({ mode: 'mine', pick: 'preset', preset: 'sage', opacity: 50 })
    const layer = warmBgLayer(pref, { weatherUrl: weather, isLoggedIn: false })
    expect(layer.image).toBe(presetById('sage').css)
    expect(layer.size).toBe(presetById('sage').size)
    expect(layer.opacity).toBe(0.5)
  })

  it('上传照片:登录后生效;未登录不可读 /files/,回落天气图', () => {
    const pref = parseWarmBg({ mode: 'mine', pick: 'upload', url: '/files/files/202611/a.jpg', opacity: 80 })
    expect(warmBgLayer(pref, { weatherUrl: weather, isLoggedIn: true })).toEqual({
      image: 'url("/files/files/202611/a.jpg")',
      size: 'cover',
      opacity: 0.8,
    })
    expect(warmBgLayer(pref, { weatherUrl: weather, isLoggedIn: false })).toEqual({
      image: `url("${weather}")`,
      size: 'cover',
      opacity: 0.3,
    })
    // 连天气图都没有时就不画层,不能留一块空底
    expect(warmBgLayer(pref, { weatherUrl: '', isLoggedIn: false })).toBeNull()
  })

  it('选了上传但还没传图:退回内置氛围', () => {
    const pref = parseWarmBg({ mode: 'mine', pick: 'upload', url: '', preset: 'linen', opacity: 60 })
    expect(warmBgLayer(pref, { isLoggedIn: true }).image).toBe(presetById('linen').css)
  })
})

describe('内置氛围清单', () => {
  it('id 唯一,且每一项都有文案键/样式/尺寸', () => {
    const ids = BG_PRESETS.map((p) => p.id)
    expect(new Set(ids).size).toBe(ids.length)
    for (const p of BG_PRESETS) {
      expect(p.labelKey).toMatch(/^warmBg\.preset[A-Z]/)
      expect(p.css.length).toBeGreaterThan(0)
      expect(p.size.length).toBeGreaterThan(0)
    }
  })

  it('未知 id 回落第一档', () => {
    expect(presetById('nope')).toBe(BG_PRESETS[0])
  })
})
