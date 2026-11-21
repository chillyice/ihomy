import { afterEach, describe, expect, it, vi } from 'vitest'
import { loadPrefs, savePrefs } from './screensaver'
import {
  readSharedDisplay,
  writeSharedDisplay,
  displayScope,
  displaySchedule,
  selectedAlbumPhotos,
  protectedStandbyRoute,
} from './windowStandby'
import { shouldShowMorning } from './morning'

afterEach(() => vi.unstubAllGlobals())

function storage() {
  const data = new Map()
  vi.stubGlobal('localStorage', { getItem: (key) => data.get(key) || null, setItem: (key, value) => data.set(key, value) })
}

describe('window standby', () => {
  it('keeps existing narrow defaults and requires wide-screen opt-in', () => {
    storage()
    savePrefs({ idleMinutes: 8 })
    expect(loadPrefs()).toMatchObject({ enabled: true, idleMinutes: 8 })
    expect(loadPrefs(true)).toMatchObject({ enabled: false, idleMinutes: 5 })
    savePrefs({ enabled: true, idleMinutes: 12 }, true)
    expect(loadPrefs().idleMinutes).toBe(8)
  })

  it('does not transfer display consent to another account or family', () => {
    storage()
    const scope = displayScope({ id: 2, familyId: 3 })
    writeSharedDisplay(scope, { albumId: 4, schedule: true })
    expect(readSharedDisplay(scope)).toEqual({ albumId: 4, schedule: true })
    for (const other of ['', '2:4', '3:3']) expect(readSharedDisplay(other)).toEqual({ albumId: null, schedule: false })
  })

  it('displays photos only from the selected eligible album after checking current access', () => {
    const source = { album: { id: 2, visibility: 'FAMILY' }, photos: [{ url: '/files/test' }] }
    expect(selectedAlbumPhotos(source, 2)).toEqual(source.photos)
    expect(selectedAlbumPhotos(source, null)).toEqual([])
    expect(selectedAlbumPhotos(source, 3)).toEqual([])
    expect(selectedAlbumPhotos({ ...source, readOnly: true }, 2)).toEqual([])
    expect(selectedAlbumPhotos({ ...source, album: { id: 2, visibility: 'PRIVATE' } }, 2)).toEqual([])
  })

  it('keeps financial, medical, unknown and shared events off a shared screen', () => {
    const reminder = { kind: 'REMINDER' }
    expect(displaySchedule([reminder, { ...reminder, shared: true }, { kind: 'LOAN' }, { kind: 'HEALTH' }, {}])).toEqual([reminder])
  })

  it('defers a daily welcome for targeted entry and protects ongoing tasks', () => {
    const ctx = { enabled: true, isLoggedIn: true, today: '2026-11-21' }
    expect(shouldShowMorning({ ...ctx, targetedEntry: true })).toBe(false)
    expect(shouldShowMorning(ctx)).toBe(true)
    for (const path of ['/games/sudoku', '/tools/gba', '/blog/edit/4', '/kitchen/recipe/new', '/tools/mindmap/3', '/cinema/2'])
      expect(protectedStandbyRoute(path)).toBe(true)
    expect(protectedStandbyRoute('/music')).toBe(false)
  })
})
