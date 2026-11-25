// Coordinates across the fixed jambs and central mullion of the window.
export const WINDOW_WIDTH_RATIO = 0.88
export const CASEMENTS = [
  { hinge: 0.012, latch: 0.5 - 0.036 },
  { hinge: 1 - 0.012, latch: 0.5 + 0.036 },
]

export function chooseWindowSash(random = Math.random) {
  if (random() < 0.45) return null
  const { hinge, latch } = CASEMENTS[random() < 0.5 ? 0 : 1]
  const angle = ((30 + random() * 45) * Math.PI) / 180
  const span = latch - hinge
  return {
    hinge,
    latch,
    tipX: hinge + span * Math.cos(angle),
    depth: Math.abs(span) * Math.sin(angle),
  }
}
