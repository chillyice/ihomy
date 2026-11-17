import { describe, it, expect } from 'vitest'
import { resolvePresence, presenceKey, PRESENCE_STATUS, PRESENCE_CHOICES } from './presence'

describe('resolvePresence 头像状态点取值', () => {
  it('在线且未设手动状态 → 绿', () => {
    expect(resolvePresence({ id: 1, online: true, presenceStatus: 'ONLINE' })).toBe(PRESENCE_STATUS.ONLINE)
    expect(resolvePresence({ id: 1, online: true })).toBe(PRESENCE_STATUS.ONLINE)
  })

  it('在线且手动设了忙碌/离开 → 红/黄', () => {
    expect(resolvePresence({ id: 1, online: true, presenceStatus: 'BUSY' })).toBe(PRESENCE_STATUS.BUSY)
    expect(resolvePresence({ id: 1, online: true, presenceStatus: 'AWAY' })).toBe(PRESENCE_STATUS.AWAY)
  })

  it('离线优先:离线时手动状态不显示,一律灰', () => {
    expect(resolvePresence({ id: 1, online: false, presenceStatus: 'BUSY' })).toBe(PRESENCE_STATUS.OFFLINE)
    expect(resolvePresence({ id: 1, online: false })).toBe(PRESENCE_STATUS.OFFLINE)
  })

  it('WS 实时覆盖优先于列表首拉快照', () => {
    const overrides = { 1: 'OFFLINE' }
    expect(resolvePresence({ id: 1, online: true, presenceStatus: 'ONLINE' }, overrides)).toBe(PRESENCE_STATUS.OFFLINE)
    expect(resolvePresence({ id: 2, online: false }, { 2: 'BUSY' })).toBe(PRESENCE_STATUS.BUSY)
  })

  it('覆盖值非法时回落到快照,不显示脏值', () => {
    expect(resolvePresence({ id: 1, online: true, presenceStatus: 'AWAY' }, { 1: 'WHATEVER' })).toBe(PRESENCE_STATUS.AWAY)
  })

  it('无成员/null 按离线处理', () => {
    expect(resolvePresence(null)).toBe(PRESENCE_STATUS.OFFLINE)
    expect(resolvePresence({ online: true })).toBe(PRESENCE_STATUS.ONLINE)
  })

  it('prefers id over userId, and userId works for circle members', () => {
    expect(resolvePresence({ userId: 7, online: true }, { 7: 'AWAY' })).toBe(PRESENCE_STATUS.AWAY)
  })
})

describe('presenceKey / PRESENCE_CHOICES', () => {
  it('转小写给 CSS 类与 i18n 键用', () => {
    expect(presenceKey('BUSY')).toBe('busy')
    expect(presenceKey()).toBe('offline')
  })

  it('本人可切换状态不含离线', () => {
    expect(PRESENCE_CHOICES).toEqual(['ONLINE', 'BUSY', 'AWAY'])
  })
})
