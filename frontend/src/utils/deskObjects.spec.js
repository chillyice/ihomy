import { describe, expect, it } from 'vitest'
import { buildDeskObjects, DESK_DESTINATIONS, DESK_DEFAULT_PATHS } from './deskObjects'
import { shareId } from './shareId'
import { cardPaper, journalArt } from './warmMaterials'

const now = new Date(2026, 10, 22, 12, 30)
const modules = ['album', 'plan', 'chat', 'kitchen', 'music', 'library', 'diary'].map((code) => ({ code, enabled: 1 }))
const origin = 'https://family.test'
const desk = (extra = {}) => buildDeskObjects({ now, origin, modules, seed: 272, ...extra })
const kind = (items, type) => items.find((item) => item.kind === type)
const photo = (id, createdAt, url = '/files/pictures/' + id + '.jpg') => ({ id, albumId: 7, createdAt, url })

describe('window desk objects', () => {
  it('links only distinct accessible photos from the current and preceding six calendar days', () => {
    const photos = [
      photo(1, '2026-11-15T23:59:59'),
      photo(2, '2026-11-16T00:00:00'),
      photo(3, '2026-11-22 12:30:00'),
      photo(4, '2026-11-22T12:31:00'),
      photo(5, '2026-11-20', 'https://elsewhere.test/files/private'),
      photo(3, '2026-11-22', '/files/replaced.jpg'),
      photo(6, '2026-11-21', '/files/pictures/3.jpg'),
      photo(7, '2026-11-21', '/api/anything'),
      photo(8, 'invalid'),
    ]
    const frames = desk({ photos }).filter((i) => i.kind === 'photo')
    expect(frames.map((i) => i.key).sort()).toEqual(['photo:2', 'photo:3'])
    expect(
      desk({
        photos: [
          { ...photos[1], familyId: 7 },
          { ...photos[2], familyId: 9 },
        ],
        familyId: 7,
      })
        .filter((i) => i.kind === 'photo')
        .map((i) => i.key),
    ).toEqual(['photo:2'])
    expect(frames.find((i) => i.key === 'photo:2')).toMatchObject({ src: origin + '/files/pictures/2.jpg', to: '/album/7?p=' + shareId(2) })
    expect(
      desk({ photos: Array.from({ length: 10 }, (_, i) => photo(i + 1, '2026-11-20')) }).filter((i) => i.kind === 'photo'),
    ).toHaveLength(3)
  })

  it('preserves placement order for surviving objects and leaves time, season and redraw effective', () => {
    const first = desk()
    const keys = (rows) => rows.map((r) => r.key)
    expect(keys(desk({ notices: 1, messages: 2 }).filter((i) => i.kind !== 'phone' && i.kind !== 'envelope'))).toEqual(keys(first))
    expect(keys(desk({ seed: 273 }))).not.toEqual(keys(first))
    expect(kind(first, 'leaf')).toMatchObject({ art: 'leaf-autumn', to: '/plan?date=2026-11-22' })
    expect(kind(first, 'cup').art).toBe('tea')
    const spring = desk({ now: new Date(2026, 2, 1, 8) })
    expect(kind(spring, 'cup').art).toBe('coffee')
    expect(kind(spring, 'fruit').art).toBe('fruit-pear')
    expect(kind(desk({ now: new Date(2026, 6, 1) }), 'fruit').art).toBe('fruit-peach')
    expect(kind(desk({ now: new Date(2026, 11, 1) }), 'leaf').art).toBe('leaf-winter')
  })

  it('draws all stationery finishes across visits and keeps a task or book recognizable during refreshes', () => {
    const agenda = { plans: [{ id: 8, overdueDays: 2 }] }
    const notes = new Set()
    const journals = new Set()
    const covers = new Set()
    const papers = new Set()
    for (let seed = 0; seed < 48; seed++) {
      const items = desk({ seed, agenda })
      const note = kind(items, 'note')
      const journal = kind(items, 'journal')
      expect(note.to).toBe('/plan?tab=plan&focus=8')
      expect(journal.to).toBe('/diary')
      const refreshed = desk({ seed, agenda, messages: 1 })
      expect(kind(refreshed, 'note').art).toBe(note.art)
      expect(kind(refreshed, 'journal').art).toBe(journal.art)
      notes.add(note.art)
      journals.add(journal.art)
      covers.add(journalArt(42, seed))
      papers.add(cardPaper('feed', seed))
    }
    expect([...notes].sort()).toEqual(['note', 'note-grid', 'note-sage'])
    expect([...journals].sort()).toEqual(['journal', 'journal-floral', 'journal-linen'])
    expect(covers).toEqual(journals)
    expect([...papers].sort()).toEqual(['laid', 'plain', 'pressed'])
  })

  it('validates rebinding, module access and child-mode destinations including the item tooltip', () => {
    expect(DESK_DESTINATIONS.map((d) => d.path)).toEqual(expect.arrayContaining(Object.values(DESK_DEFAULT_PATHS)))
    expect(kind(desk({ destinations: { cup: '/library' } }), 'cup')).toMatchObject({ to: '/library', destinationLabel: 'library.title' })
    expect(kind(desk({ destinations: { cup: 'https://elsewhere.test/' } }), 'cup').to).toBe('/weather')
    const childDesk = desk({ child: true, notices: 4, messages: 2, agenda: { plans: [{ id: 8 }] }, destinations: { cup: '/plan' } })
    expect(kind(childDesk, 'cup').to).toBe('/weather')
    for (const blocked of ['phone', 'envelope', 'note', 'recipe', 'leaf']) expect(kind(childDesk, blocked)).toBeUndefined()
    expect(desk({ modules: [{ code: 'library', enabled: 0 }] }).map((item) => item.kind)).toEqual(['cup'])
  })

  it('binds one real pending task and derives its paper age without revealing titles or content', () => {
    const note = (agenda) => kind(desk({ agenda }), 'note')
    expect(note({ plans: [{ id: 81, overdueDays: 12, title: 'secret' }] })).toMatchObject({
      to: '/plan?tab=plan&focus=81',
      ageDays: 12,
      labelKey: 'windowDesk.noteOverdue',
    })
    expect(note({ plans: [{ id: 82, overdueDays: 0 }] }).labelKey).toBe('windowDesk.noteDue')
    const task = note({ undated: [{ kind: 'PLAN_TASK', id: 123, planId: 45, createdAt: '2026-11-19', title: 'secret' }] })
    expect(task).toMatchObject({ to: '/plan?tab=plan&focus=45', ageDays: 3, labelKey: 'windowDesk.noteUndated' })
    expect(JSON.stringify(task)).not.toContain('secret')
    expect(note({ reminders: [{ id: 68, fromPlan: true, planId: 23 }] }).to).toBe('/plan?tab=plan&focus=23')
    expect(note({ reminders: [{ id: 68 }] }).to).toBe('/plan?tab=today')
    expect(note({ undated: [{ id: 42, kind: 'PLAN', done: true }], upcomingEvents: [{ id: 7 }] })).toBeUndefined()
    expect(kind(desk({ notices: 8 }), 'envelope').labelParams).toEqual({ count: 8 })
    expect(kind(desk({ messages: 4 }), 'phone').labelParams).toEqual({ count: 4 })
    expect(kind(desk({ notices: 0 }), 'envelope')).toBeUndefined()
  })
})
