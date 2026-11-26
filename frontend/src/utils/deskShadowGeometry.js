import { DESK_OBJECT_PROFILES } from './deskObjectProfiles'
import { floorShadowDirection, groundShadowPerHeight } from './windowLight'

const referenceAspect = 16 / 9

// Keep the calibrated 16:9 desktop framing. A narrower viewport has less
// room for lateral casts. An ultrawide viewport can use up to 25% more reach.
// The curve has the same slope on both sides of 16:9. Use the same budget
// all day, so the change of bearing cannot reverse a rising sun's shortening.
export function displayedReachPerHeight(altitude, realWeight, aspect = referenceAspect) {
  const physical = groundShadowPerHeight(altitude)
  if (realWeight === 1) return physical
  const relativeWidth = aspect / referenceAspect
  const extra = Math.max(0, relativeWidth - 1)
  const budget = Math.min(1, relativeWidth) + extra / (1 + 4 * extra)
  const threshold = 0.4 * budget
  if (physical <= threshold) return physical
  const excess = physical - threshold
  const fitted = threshold + excess / (1 + (2 * excess) / budget)
  return fitted + realWeight * (physical - fitted)
}

export function deskShadowVector(scene, spriteSize, kind, realWeight = 1, aspect = referenceAspect) {
  const altitude = scene?.altitude
  const heightRatio = DESK_OBJECT_PROFILES[kind]?.heightRatio
  if (!scene?.hasDirectLight || !Number.isFinite(altitude) || altitude <= 0 || altitude >= 90 || !Number.isFinite(scene.azimuth))
    return null
  if (!(heightRatio > 0) || !Number.isFinite(spriteSize) || spriteSize <= 0) return null
  const height = spriteSize * heightRatio
  const length = height * displayedReachPerHeight(altitude, realWeight, aspect)
  const direction = floorShadowDirection(scene.azimuth)
  return { x: direction.x * length, y: direction.y * length, height }
}
