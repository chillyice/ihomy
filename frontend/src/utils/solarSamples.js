const SLOT_MS = 5 * 60_000
const radians = Math.PI / 180

// Indices follow real elapsed time, including the second occurrence of a repeated hour.
export function solarSlotPosition(slots, epochMillis, fallbackIndex) {
  const start = slots?.[0]?.epochMillis
  if (!Number.isFinite(start)) return fallbackIndex
  return Math.max(0, Math.min(slots.length, (epochMillis - start) / SLOT_MS))
}

function direction({ altitude, azimuth }) {
  const alt = altitude * radians
  const az = azimuth * radians
  return [Math.cos(alt) * Math.sin(az), Math.cos(alt) * Math.cos(az), Math.sin(alt)]
}

export function sampleSolarDirection(info, position) {
  const index = Math.floor(position)
  const a = info?.slots?.[index]
  const b = info?.slots?.[index + 1] ?? info?.nextMidnight
  if (!a) return info?.nextMidnight
  const t = position - index
  if (!b || t === 0) return a
  const start = direction(a)
  const end = direction(b)
  const [x, y, z] = start.map((value, i) => value + t * (end[i] - value))
  const [hour, minute] = a.time.split(':')
  return {
    ...a,
    time: `${hour}:${String(Number(minute) + Math.floor(t * 5)).padStart(2, '0')}`,
    altitude: Math.atan2(z, Math.hypot(x, y)) / radians,
    azimuth: (Math.atan2(x, y) / radians + 360) % 360,
  }
}

export function solarSlotForTime(slots, minutes, currentIndex = 0) {
  if (!slots?.length) return Math.floor(minutes / 5)
  const hhmm = [Math.floor(minutes / 60), Math.floor((minutes % 60) / 5) * 5].map((n) => String(n).padStart(2, '0')).join(':')
  // Keep the later occurrence when editing within the repeated hour. In a spring gap,
  // select the first wall time following the gap.
  let nearest = -1
  for (const [index, slot] of slots.entries()) {
    if (slot.time === hhmm && (nearest === -1 || Math.abs(index - currentIndex) < Math.abs(nearest - currentIndex))) nearest = index
  }
  if (nearest >= 0) return nearest
  const next = slots.findIndex((slot) => slot.time > hhmm)
  return next < 0 ? slots.length - 1 : next
}
