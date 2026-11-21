import { describe, expect, it } from 'vitest'
import { windowEntries, windowGroups, parseWindowFavorites, DEFAULT_WINDOW_FAVORITES } from './windowDirectory'
import { dayEventPath, storyPath, storyImage, remainingDayEvents } from './windowHome'
import { shareId } from './shareId'

const modules = ['chat', 'item', 'music', 'member', 'plan', 'book', 'album'].map((code) => ({
  code,
  title: code,
  category: code === 'member' ? 'family' : 'life',
}))
const make = (extra = {}) => windowEntries(modules, { loggedIn: true, allows: () => true, label: (code) => code, ...extra })
describe('window directory', () => {
  it('resolves aliases to actual actions, keeping secondary views out of default groups', () => {
    const entries = make()
    const pathFor = (q) =>
      windowGroups(entries, q)
        .flatMap((g) => g.items)
        .map((i) => i.path)
    expect(pathFor('听歌')).toEqual(['/music'])
    expect(pathFor('寻物')).toEqual(['/item?focus=search'])
    expect(pathFor(' 家 谱 ')).toEqual(['/member?tab=tree'])
    expect(pathFor('问助手')).toEqual(['/chat?target=assistant'])
    expect(pathFor('纪念日')).toEqual(['/plan?tab=anniversary'])
    expect(pathFor('')).not.toContain('/member?tab=tree')
    expect(pathFor('')).toContain('/chat?target=assistant')
    expect(pathFor('雨伞')).toEqual([])
  })
  it('creates subordinate entries only when the parent route is enabled and allowed', () => {
    const child = make({ child: true }).map((i) => i.code)
    expect(child).toContain('music')
    expect(child).not.toContain('assistant')
    expect(child).not.toContain('kiosk')
    const restricted = make({ loggedIn: false, allows: (it) => !['book', 'chat'].includes(it.code) }).map((i) => i.code)
    expect(restricted).not.toContain('book')
    expect(restricted).not.toContain('assistant')
    expect(restricted).not.toContain('kiosk')
    expect(restricted).toContain('login')
    expect(restricted).toContain('appDownload')
    expect(windowEntries([{ code: 'chat', enabled: 0 }], { allows: () => true, label: (c) => c }).map((i) => i.code)).not.toContain(
      'assistant',
    )
  })
  it('distinguishes removing every favorite from initial/malformed preferences', () => {
    expect(parseWindowFavorites('[]')).toEqual([])
    expect(parseWindowFavorites('bad')).toEqual(DEFAULT_WINDOW_FAVORITES)
    expect(parseWindowFavorites('["item","chat","item",null]')).toEqual(['item', 'chat'])
  })
})
describe('window home details', () => {
  it('opens the actual album and authorized diary entry', () => {
    expect(storyPath({ type: 'photo', id: 111 }, { id: 9, albumId: 35 })).toBe('/album/35?p=' + shareId(9))
    expect(storyPath({ type: 'diary', authorId: 3, id: 92 })).toBe('/diary/book/3?entry=92')
    expect(storyImage({ images: '["/files/sunset.jpg"]' })).toBe('/files/sunset.jpg')
    expect(storyImage({ images: 'invalid' })).toBe('')
  })
  it('drops completed and earlier events and opens a recurring reminder or parent plan with its correct type', () => {
    const it = { kind: 'TASK', id: 7, time: '11:00' }
    expect(
      remainingDayEvents([{ ...it, time: '09:51' }, { ...it, done: true }, { ...it, time: null }, it], new Date(2026, 10, 21, 10, 10)),
    ).toEqual([it, { ...it, time: null }])
    expect(dayEventPath(it, '2026-11-21')).toBe('/plan?date=2026-11-21&kind=REMINDER&id=7')
    expect(dayEventPath({ kind: 'PLAN_TASK', id: 9, planId: 8 }, '2026-11-21')).toContain('kind=PLAN&id=8')
    expect(dayEventPath({ link: '/kitchen' }, '2026-11-21')).toBe('/kitchen')
  })
})
