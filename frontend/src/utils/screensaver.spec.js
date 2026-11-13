import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { BOARD_CARDS, DEFAULT_PREFS, IDLE_MAX, IDLE_MIN, MODES, loadPrefs, normalizePrefs, savePrefs, shouldSuppress } from './screensaver'

// 屏保口径:偏好归一化(模式回退/时长夹取/卡片白名单)与「什么时候不许弹」
// 都收在纯函数里,这里守住边界,组件只负责渲染。
describe('normalizePrefs', () => {
  it('空输入 / 非对象回落默认值', () => {
    expect(normalizePrefs(null)).toEqual(DEFAULT_PREFS)
    expect(normalizePrefs('x')).toEqual(DEFAULT_PREFS)
    expect(normalizePrefs([])).toEqual(DEFAULT_PREFS)
  })

  it('未知模式回退背景模式', () => {
    expect(normalizePrefs({ mode: 'BOARDX' }).mode).toBe(MODES.PHOTO)
    expect(normalizePrefs({ mode: MODES.BOARD }).mode).toBe(MODES.BOARD)
  })

  it('空闲时长夹到 1–30 分钟并取整', () => {
    expect(normalizePrefs({ idleMinutes: 0 }).idleMinutes).toBe(IDLE_MIN)
    expect(normalizePrefs({ idleMinutes: -5 }).idleMinutes).toBe(IDLE_MIN)
    expect(normalizePrefs({ idleMinutes: 999 }).idleMinutes).toBe(IDLE_MAX)
    expect(normalizePrefs({ idleMinutes: 7.6 }).idleMinutes).toBe(8)
    expect(normalizePrefs({ idleMinutes: 'abc' }).idleMinutes).toBe(DEFAULT_PREFS.idleMinutes)
  })

  it('卡片只认白名单键,缺省补默认', () => {
    const p = normalizePrefs({ board: { todo: false, hacked: false } })
    expect(p.board.todo).toBe(false)
    expect('hacked' in p.board).toBe(false)
    BOARD_CARDS.forEach((k) => {
      if (k !== 'todo') expect(p.board[k]).toBe(true)
    })
  })

  it('enabled 非布尔值按默认开', () => {
    expect(normalizePrefs({ enabled: '0' }).enabled).toBe(DEFAULT_PREFS.enabled)
    expect(normalizePrefs({ enabled: false }).enabled).toBe(false)
  })
})

describe('loadPrefs / savePrefs', () => {
  beforeEach(() => {
    delete globalThis.localStorage
  })
  afterEach(() => {
    delete globalThis.localStorage
  })

  it('无存储时回落默认值', () => {
    expect(loadPrefs()).toEqual(DEFAULT_PREFS)
  })

  it('坏 JSON 回落默认值', () => {
    globalThis.localStorage = { getItem: () => '{not json', setItem: () => {} }
    expect(loadPrefs()).toEqual(DEFAULT_PREFS)
  })

  it('savePrefs 只合并传入字段,卡片逐项覆盖', () => {
    const store = new Map()
    globalThis.localStorage = {
      getItem: (k) => store.get(k) ?? null,
      setItem: (k, v) => store.set(k, v),
    }
    const next = savePrefs({ mode: MODES.BOARD, board: { photos: false } })
    expect(next.mode).toBe(MODES.BOARD)
    expect(next.board.photos).toBe(false)
    expect(next.board.todo).toBe(true)
    expect(next.idleMinutes).toBe(DEFAULT_PREFS.idleMinutes)
    expect(loadPrefs()).toEqual(next)
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
