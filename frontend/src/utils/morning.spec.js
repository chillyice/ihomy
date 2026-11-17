import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import {
  DEFAULT_PREFS,
  MORNING_CARDS,
  clearPreviewScene,
  loadLastShown,
  loadPrefs,
  markShown,
  normalizePrefs,
  previewScene,
  savePrefs,
  setPreviewScene,
  shouldShowMorning,
  todayKey,
} from './morning'

// 早安页口径:偏好归一化、每天只展示一次、浮层/独立页/儿童模式让位。
describe('normalizePrefs', () => {
  it('空输入回落默认值', () => {
    expect(normalizePrefs(null)).toEqual(DEFAULT_PREFS)
    expect(normalizePrefs('x')).toEqual(DEFAULT_PREFS)
  })

  it('卡片只认白名单,缺省补默认', () => {
    const p = normalizePrefs({ cards: { todo: false, hacked: false } })
    expect(p.cards.todo).toBe(false)
    expect('hacked' in p.cards).toBe(false)
    MORNING_CARDS.forEach((k) => {
      if (k !== 'todo') expect(p.cards[k]).toBe(true)
    })
  })

  it('问候语限长,非字符串回落空串', () => {
    expect(normalizePrefs({ greeting: '早安' }).greeting).toBe('早安')
    expect(normalizePrefs({ greeting: 123 }).greeting).toBe('')
    expect(normalizePrefs({ greeting: 'x'.repeat(80) }).greeting).toHaveLength(60)
  })

  it('云层样式只认两种,未知回落到图片云', () => {
    expect(DEFAULT_PREFS.cloudStyle).toBe('photo')
    expect(normalizePrefs({}).cloudStyle).toBe('photo')
    expect(normalizePrefs({ cloudStyle: 'random' }).cloudStyle).toBe('random')
    expect(normalizePrefs({ cloudStyle: 'photo' }).cloudStyle).toBe('photo')
    expect(normalizePrefs({ cloudStyle: 'video' }).cloudStyle).toBe('photo')
    expect(normalizePrefs({ cloudStyle: null }).cloudStyle).toBe('photo')
  })
})

describe('loadPrefs / savePrefs / 每日标记', () => {
  beforeEach(() => {
    delete globalThis.localStorage
  })
  afterEach(() => {
    delete globalThis.localStorage
  })

  it('无存储时回落默认值', () => {
    expect(loadPrefs()).toEqual(DEFAULT_PREFS)
    expect(loadLastShown()).toBe('')
  })

  it('savePrefs 合并卡片并广播', () => {
    const store = new Map()
    globalThis.localStorage = {
      getItem: (k) => store.get(k) ?? null,
      setItem: (k, v) => store.set(k, v),
    }
    const next = savePrefs({ enabled: false, cards: { weather: false } })
    expect(next.enabled).toBe(false)
    expect(next.cards.weather).toBe(false)
    expect(next.cards.todo).toBe(true)
    expect(loadPrefs()).toEqual(next)
  })

  it('markShown 按本地日期记录', () => {
    const store = new Map()
    globalThis.localStorage = {
      getItem: (k) => store.get(k) ?? null,
      setItem: (k, v) => store.set(k, v),
    }
    markShown('2026-11-16')
    expect(loadLastShown()).toBe('2026-11-16')
    expect(todayKey(new Date(2026, 10, 16))).toBe('2026-11-16')
  })
})

// 设置页的「预览场景」只影响紧接着的一次预览:认白名单、不改真实天气、可清空
describe('previewScene', () => {
  it('只接受已知天气码,未知回落跟随实况', () => {
    setPreviewScene('rain', true)
    expect(previewScene.value).toEqual({ condition: 'rain', night: true })
    setPreviewScene('nope', 'x')
    expect(previewScene.value).toEqual({ condition: '', night: null })
  })

  it.each(['cloud', 'overcast'])('可单独预览 %s', (condition) => {
    setPreviewScene(condition, false)
    expect(previewScene.value).toEqual({ condition, night: false })
  })

  it('clearPreviewScene 回到跟随实况', () => {
    setPreviewScene('snow', false)
    clearPreviewScene()
    expect(previewScene.value).toEqual({ condition: '', night: null })
  })
})

describe('shouldShowMorning', () => {
  const base = {
    enabled: true,
    isLoggedIn: true,
    lastShown: '2026-11-15',
    today: '2026-11-16',
  }

  it('登录且今天还没看过时展示', () => {
    expect(shouldShowMorning(base)).toBe(true)
    expect(shouldShowMorning({ ...base, lastShown: '' })).toBe(true)
    // 今天已经看过 → 当天不再展示
    expect(shouldShowMorning({ ...base, lastShown: '2026-11-16' })).toBe(false)
  })

  it('关开关/未登录/纯 OPS/强制改密不展示', () => {
    expect(shouldShowMorning({ ...base, lastShown: '', enabled: false })).toBe(false)
    expect(shouldShowMorning({ ...base, lastShown: '', isLoggedIn: false })).toBe(false)
    expect(shouldShowMorning({ ...base, lastShown: '', isPureOps: true })).toBe(false)
    expect(shouldShowMorning({ ...base, lastShown: '', mustChangePassword: true })).toBe(false)
  })

  it('儿童模式/通话/后台/浮层/独立页/沉浸页让位', () => {
    expect(shouldShowMorning({ ...base, lastShown: '', isChild: true })).toBe(false)
    expect(shouldShowMorning({ ...base, lastShown: '', inCall: true })).toBe(false)
    expect(shouldShowMorning({ ...base, lastShown: '', hidden: true })).toBe(false)
    expect(shouldShowMorning({ ...base, lastShown: '', busy: true })).toBe(false)
    expect(shouldShowMorning({ ...base, lastShown: '', routeMeta: { standalone: true } })).toBe(false)
    expect(shouldShowMorning({ ...base, lastShown: '', routeMeta: { immersive: true } })).toBe(false)
  })
})
