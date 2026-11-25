import { daysBetween, toDateStr } from './date'
import { isChildAllowedPath } from './displayMode'
import { shareId } from './shareId'
import { journalArt, keyedDraw as draw, noteArt } from './warmMaterials'

export const DESK_DESTINATIONS = [
  { path: '/weather', labelKey: 'weatherPage.title' },
  { path: '/library', labelKey: 'library.title' },
  { path: '/diary', labelKey: 'diary.title' },
  { path: '/music', labelKey: 'music.title' },
  { path: '/kitchen', labelKey: 'kitchen.title' },
  { path: '/kitchen/ingredients', labelKey: 'kitchen.ingredients' },
  { path: '/plan', labelKey: 'schedule.title' },
]

export const DESK_DEFAULT_PATHS = {
  cup: '/weather',
  fruit: '/kitchen/ingredients',
  journal: '/diary',
  leaf: '/plan',
  bookmark: '/library',
  record: '/music',
  recipe: '/kitchen',
}

export function deskPathAllowed(path, modules, child) {
  if (child && !isChildAllowedPath(path)) return false
  const code = path.split(/[/?]/)[1]
  return code === 'weather' || code === 'notification' || modules.some((m) => m.code === code && m.enabled !== 0)
}

function photoItems(rows, now, origin, seed, familyId) {
  const seen = new Set()
  const first = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 6)
  const photos = []
  for (const row of rows) {
    if (!row?.url || !row.createdAt || !positiveId(row.id) || !positiveId(row.albumId)) continue
    // A photo shared individually can be visible in the feed without access to its source album.
    if (familyId != null && String(row.familyId) !== String(familyId)) continue
    const raw = String(row.createdAt)
    const time = new Date(raw.length === 10 ? raw + 'T00:00:00' : raw.replace(' ', 'T'))
    if (!(time >= first && time <= now)) continue
    let url
    try {
      url = new URL(row.url, origin)
    } catch {
      continue
    }
    if (url.origin !== origin || !/^\/(files\/|demo\/|api\/storage\/file-signed$)/.test(url.pathname)) continue
    const key = 'photo:' + row.id
    if (seen.has(key) || seen.has(url.href)) continue
    seen.add(key)
    seen.add(url.href)
    photos.push({
      key,
      kind: 'photo',
      src: url.href,
      to: '/album/' + row.albumId + '?p=' + shareId(row.id),
      labelKey: 'windowDesk.photo',
      labelParams: { date: toDateStr(time) },
    })
  }
  return photos.sort((a, b) => draw(seed, a.key) - draw(seed, b.key)).slice(0, 3)
}

const positiveId = (id) => /^\d+$/.test(String(id)) && Number.isSafeInteger(Number(id)) && Number(id) > 0

function deskNote(agenda, now, seed) {
  let choice
  function consider(key, to, ageDays, label) {
    const rank = draw(seed, key)
    if (choice && rank >= choice.rank) return
    choice = {
      rank,
      item: {
        key,
        kind: 'note',
        art: noteArt(key, seed),
        to,
        ageDays,
        paperTone: Math.floor(draw(seed, key + ':stock') * 3),
        labelKey: 'windowDesk.' + label,
        labelParams: { days: ageDays },
      },
    }
  }
  const planPath = (id) => '/plan?tab=plan&focus=' + id
  const activePlans = new Set()
  for (const p of agenda?.plans || []) {
    if (!positiveId(p.id)) continue
    activePlans.add(String(p.id))
    const age = Math.max(0, Number(p.overdueDays) || 0)
    consider('plan:' + p.id, planPath(p.id), age, age ? 'noteOverdue' : 'noteDue')
  }
  for (const p of agenda?.undated || []) {
    if (p.kind !== 'PLAN' && p.kind !== 'PLAN_TASK') continue
    const id = p.kind === 'PLAN_TASK' ? p.planId : p.id
    if (!positiveId(id) || !positiveId(p.id) || p.done) continue
    consider(p.kind + ':' + p.id, planPath(id), Math.max(0, daysBetween(p.createdAt, now)), 'noteUndated')
  }
  for (const r of agenda?.reminders || []) {
    if (!positiveId(r.id) || activePlans.has(String(r.planId))) continue
    consider('reminder:' + r.id, positiveId(r.planId) ? planPath(r.planId) : '/plan?tab=today', 0, 'noteReminder')
  }
  return choice?.item
}

export function buildDeskObjects({
  now,
  seed,
  origin,
  familyId,
  photos = [],
  modules = [],
  child = false,
  agenda,
  notices = 0,
  messages = 0,
  destinations = {},
}) {
  const allowed = (path) => deskPathAllowed(path, modules, child)
  const hour = now.getHours()
  const season = ['winter', 'spring', 'summer', 'autumn'][Math.floor(((now.getMonth() + 1) % 12) / 3)]
  const art = {
    cup: hour < 12 ? 'coffee' : 'tea',
    fruit: { spring: 'fruit-pear', summer: 'fruit-peach', autumn: 'fruit', winter: 'fruit' }[season],
    leaf: 'leaf-' + season,
    journal: journalArt('desk', seed),
  }
  const candidates = []
  for (const [kind, fallback] of Object.entries(DESK_DEFAULT_PATHS)) {
    const selected = DESK_DESTINATIONS.find((d) => d.path === destinations?.[kind] && allowed(d.path))
    const dest = selected || DESK_DESTINATIONS.find((d) => d.path === fallback)
    if (!allowed(dest.path)) continue
    const to = kind === 'leaf' && dest.path === '/plan' ? '/plan?date=' + toDateStr(now) : dest.path
    candidates.push({ key: kind, kind, art: art[kind] || kind, to, labelKey: 'windowDesk.openItem', destinationLabel: dest.labelKey })
  }
  if (allowed('/album')) candidates.push(...photoItems(photos, now, origin, seed, familyId))
  const note = allowed('/plan') && deskNote(agenda, now, seed)
  if (note) candidates.push(note)
  if (notices > 0 && allowed('/notification')) {
    candidates.push({
      key: 'envelope',
      kind: 'envelope',
      art: 'envelope',
      to: '/notification',
      labelKey: 'windowDesk.notice',
      labelParams: { count: notices },
    })
  }
  if (messages > 0 && allowed('/chat')) {
    candidates.push({
      key: 'phone',
      kind: 'phone',
      art: 'phone',
      to: '/chat',
      labelKey: 'windowDesk.phone',
      labelParams: { count: messages },
    })
  }
  const weights = {
    cup: 1.8,
    journal: hour >= 18 ? 2 : 1,
    recipe: [11, 12, 17, 18].includes(hour) ? 2 : 1,
    envelope: 2.5,
    note: 2.5,
    phone: 2.5,
  }
  const rank = (item) => -Math.log(1 - draw(seed, item.kind === 'note' ? 'note' : item.key)) / (weights[item.kind] || 1)
  return candidates.sort((a, b) => rank(a) - rank(b))
}
