import { describe, it, expect, afterEach, vi } from 'vitest'
import {
  BG,
  CARDS,
  DEFAULT_PREFS,
  IDLE_MAX,
  IDLE_MIN,
  SS_KEY,
  activeStandbyTask,
  loadPrefs,
  normalizePrefs,
  protectedStandbyRoute,
  savePrefs,
  screensaverBgLayer,
  shouldSuppress,
} from './screensaver'
import { BG_PRESETS } from './warmBackground'

// 屏保复用暖居背景的预设/校验(warmBackground → userPref → request → router),
// 纯 node 环境里打不开浏览器路由:把账号同步那层换成空实现,只验证背景算法本身。
vi.mock('./userPref', () => ({
  readPref: () => null,
  syncPref: () => Promise.resolve(null),
  writePref: () => {},
}))

// 屏保口径:偏好归一化(背景三选/时长夹取/图片校验/卡片白名单)、背景层计算、
// 「什么时候不许弹」都收在纯函数里,这里守住边界,组件只负责渲染。
describe('normalizePrefs', () => {
  it('空输入 / 非对象回落默认值', () => {
    expect(normalizePrefs(null)).toEqual(DEFAULT_PREFS)
    expect(normalizePrefs('x')).toEqual(DEFAULT_PREFS)
    expect(normalizePrefs([])).toEqual(DEFAULT_PREFS)
  })

  it('空闲时长夹到 1–30 分钟并取整', () => {
    expect(normalizePrefs({ idleMinutes: 0 }).idleMinutes).toBe(IDLE_MIN)
    expect(normalizePrefs({ idleMinutes: -5 }).idleMinutes).toBe(IDLE_MIN)
    expect(normalizePrefs({ idleMinutes: 999 }).idleMinutes).toBe(IDLE_MAX)
    expect(normalizePrefs({ idleMinutes: 7.6 }).idleMinutes).toBe(8)
    expect(normalizePrefs({ idleMinutes: 'abc' }).idleMinutes).toBe(DEFAULT_PREFS.idleMinutes)
  })

  it('背景只认三档,未知值回退天气图', () => {
    expect(normalizePrefs({ bg: BG.BLACK }).bg).toBe(BG.BLACK)
    expect(normalizePrefs({ bg: BG.IMAGE }).bg).toBe(BG.IMAGE)
    expect(normalizePrefs({ bg: BG.WEATHER }).bg).toBe(BG.WEATHER)
    expect(normalizePrefs({ bg: 'PURPLE' }).bg).toBe(DEFAULT_PREFS.bg)
  })

  it('旧版 {mode, board} 迁移成「我的图片 + 原卡片勾选」', () => {
    const p = normalizePrefs({ mode: 'BOARD', board: { photos: false, clock: false } })
    expect(p.bg).toBe(BG.IMAGE)
    expect(p.cards.photos).toBe(false)
    expect(p.cards.clock).toBe(false)
    expect(p.cards.todo).toBe(true)
    // 新版 cards 优先于旧版 board
    const q = normalizePrefs({ mode: 'PHOTO', board: { photos: false }, cards: { photos: true } })
    expect(q.bg).toBe(BG.IMAGE)
    expect(q.cards.photos).toBe(true)
  })

  it('卡片只认白名单键,缺省补默认', () => {
    const p = normalizePrefs({ cards: { todo: false, hacked: false } })
    expect(p.cards.todo).toBe(false)
    expect('hacked' in p.cards).toBe(false)
    CARDS.forEach((k) => {
      if (k !== 'todo') expect(p.cards[k]).toBe(true)
    })
  })

  it('图片只接受内置预设与站内/外链地址,浓度夹到 20–100', () => {
    const p = normalizePrefs({
      bg: BG.IMAGE,
      image: { pick: 'upload', preset: 'not-a-preset', url: 'javascript:alert(1)', opacity: 5 },
    })
    expect(p.image.preset).toBe('amber')
    expect(p.image.url).toBe('')
    expect(p.image.opacity).toBe(20)
    const q = normalizePrefs({ bg: BG.IMAGE, image: { pick: 'upload', url: '/files/x.jpg', opacity: 999 } })
    expect(q.image.pick).toBe('upload')
    expect(q.image.url).toBe('/files/x.jpg')
    expect(q.image.opacity).toBe(100)
  })

  it('enabled 非布尔值按默认开', () => {
    expect(normalizePrefs({ enabled: '0' }).enabled).toBe(DEFAULT_PREFS.enabled)
    expect(normalizePrefs({ enabled: false }).enabled).toBe(false)
  })
})

describe('loadPrefs / savePrefs', () => {
  afterEach(() => {
    if (globalThis.localStorage) delete globalThis.localStorage
  })

  it('无存储时回落默认值', () => {
    const original = globalThis.localStorage
    delete globalThis.localStorage
    expect(loadPrefs()).toEqual(DEFAULT_PREFS)
    if (original) globalThis.localStorage = original
  })

  it('坏 JSON 回落默认值', () => {
    globalThis.localStorage = { getItem: () => '{not json', setItem: () => {} }
    expect(loadPrefs()).toEqual(DEFAULT_PREFS)
  })

  it('savePrefs 只合并传入字段,卡片与图片逐项覆盖', () => {
    const store = new Map()
    globalThis.localStorage = {
      getItem: (k) => store.get(k) ?? null,
      setItem: (k, v) => store.set(k, v),
    }
    const next = savePrefs({ bg: BG.IMAGE, cards: { photos: false }, image: { preset: 'sage' } })
    expect(next.bg).toBe(BG.IMAGE)
    expect(next.cards.photos).toBe(false)
    expect(next.cards.todo).toBe(true)
    expect(next.image.preset).toBe('sage')
    expect(next.idleMinutes).toBe(DEFAULT_PREFS.idleMinutes)
    expect(JSON.parse(store.get(SS_KEY))).toEqual(next)
    expect(loadPrefs()).toEqual(next)
  })
})

describe('screensaverBgLayer', () => {
  it('纯黑不画图', () => {
    expect(screensaverBgLayer({ ...DEFAULT_PREFS, bg: BG.BLACK }, { weatherUrl: 'x.jpg' })).toEqual({ kind: 'black' })
  })

  it('天气图有图满浓度铺满,无图按纯黑', () => {
    expect(screensaverBgLayer({ ...DEFAULT_PREFS, bg: BG.WEATHER }, { weatherUrl: '/files/w.jpg' })).toEqual({
      kind: 'image',
      image: 'url("/files/w.jpg")',
      size: 'cover',
      opacity: 1,
    })
    expect(screensaverBgLayer({ ...DEFAULT_PREFS, bg: BG.WEATHER }, {})).toEqual({ kind: 'black' })
  })

  it('我的图片用内置氛围与用户浓度', () => {
    const pref = { ...DEFAULT_PREFS, bg: BG.IMAGE, image: { pick: 'preset', preset: 'sage', url: '', opacity: 55 } }
    const layer = screensaverBgLayer(pref, {})
    expect(layer.kind).toBe('image')
    expect(layer.image).toBe(BG_PRESETS.find((p) => p.id === 'sage').css)
    expect(layer.opacity).toBeCloseTo(0.55)
  })

  it('上传图登录可读,未登录回落到内置氛围', () => {
    const pref = { ...DEFAULT_PREFS, bg: BG.IMAGE, image: { pick: 'upload', preset: 'dusk', url: '/files/a.jpg', opacity: 80 } }
    const photo = screensaverBgLayer(pref, { isLoggedIn: true })
    expect(photo.image).toBe('url("/files/a.jpg")')
    expect(photo.photo).toBe(true)
    const guest = screensaverBgLayer(pref, { isLoggedIn: false })
    expect(guest.image).toBe(BG_PRESETS.find((p) => p.id === 'dusk').css)
    // 回落的内置氛围不是照片,不能走轻压暗层
    expect(guest.photo).toBeUndefined()
  })

  it('上传照片浓度只做轻微压暗,最暗也留 80% 亮度(不能被压成深灰)', () => {
    const at = (opacity) => {
      const pref = { ...DEFAULT_PREFS, bg: BG.IMAGE, image: { pick: 'upload', preset: 'amber', url: '/files/a.jpg', opacity } }
      return screensaverBgLayer(pref, { isLoggedIn: true }).opacity
    }
    expect(at(20)).toBeCloseTo(0.84, 3)
    expect(at(60)).toBeCloseTo(0.92, 3)
    expect(at(100)).toBeCloseTo(1, 3)
    // 浓度越高越亮(与暖居「浓度」语义同向)
    expect(at(20) < at(60)).toBe(true)
    expect(at(60) < at(100)).toBe(true)
  })
})

describe('shouldSuppress', () => {
  const base = { isLoggedIn: true }

  it('登录且普通页面可触发', () => {
    expect(shouldSuppress(base)).toBe(false)
  })

  it('未登录 / 儿童模式 / 通话中 / 后台 / 有浮层都禁止', () => {
    expect(shouldSuppress({ isLoggedIn: false })).toBe(true)
    expect(shouldSuppress({ ...base, isChild: true })).toBe(true)
    expect(shouldSuppress({ ...base, inCall: true })).toBe(true)
    expect(shouldSuppress({ ...base, hidden: true })).toBe(true)
    expect(shouldSuppress({ ...base, busy: true })).toBe(true)
  })

  it('独立页与沉浸页不弹', () => {
    expect(shouldSuppress({ ...base, routeMeta: { standalone: true } })).toBe(true)
    expect(shouldSuppress({ ...base, routeMeta: { immersive: true } })).toBe(true)
    expect(shouldSuppress({ ...base, routeMeta: {} })).toBe(false)
  })
})

describe('protectedStandbyRoute', () => {
  it('游戏 / 只读器 / 编辑页 / 放映厅受保护', () => {
    expect(protectedStandbyRoute('/games/mahjong')).toBe(true)
    expect(protectedStandbyRoute('/tools/gba')).toBe(true)
    expect(protectedStandbyRoute('/blog/new')).toBe(true)
    expect(protectedStandbyRoute('/cinema/1')).toBe(true)
    expect(protectedStandbyRoute('/home')).toBe(false)
    expect(protectedStandbyRoute('/blog/12')).toBe(false)
  })
})

describe('activeStandbyTask', () => {
  const doc = ({ fullscreen = null, active = null, nodes = {} } = {}) => ({
    fullscreenElement: fullscreen,
    activeElement: active,
    querySelectorAll: (sel) => nodes[sel] || [],
  })

  it('无文档 / 无进行中的事都不拦', () => {
    expect(activeStandbyTask(null)).toBe(false)
    expect(activeStandbyTask(doc())).toBe(false)
  })

  it('全屏 / 输入框聚焦 / 已有草稿 / 正在播放视频都会拦', () => {
    expect(activeStandbyTask(doc({ fullscreen: {} }))).toBe(true)
    expect(activeStandbyTask(doc({ active: { closest: (sel) => (sel.includes('input') ? {} : null) } }))).toBe(true)
    expect(
      activeStandbyTask(
        doc({
          nodes: {
            'textarea, .chat-composer .msg-input input, [contenteditable="true"]': [{ getClientRects: () => [1], value: '写作中' }],
          },
        }),
      ),
    ).toBe(true)
    expect(activeStandbyTask(doc({ nodes: { video: [{ paused: false, ended: false, getClientRects: () => [1] }] } }))).toBe(true)
    expect(activeStandbyTask(doc({ nodes: { video: [{ paused: true, ended: false, getClientRects: () => [1] }] } }))).toBe(false)
  })
})
