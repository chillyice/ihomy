import { DESK_OBJECT_PROFILES } from './deskObjectProfiles'
import { floorShadowDirection, groundShadowPerHeight } from './windowLight'

// A 1.5 m aperture has a height of two viewports. Preserve its true reach up
// to 0.8 viewports, then approach 1.8 viewports smoothly. Every caster uses
// this same scale so small desk objects stay in proportion with the frame.
export function displayedReachPerHeight(altitude, realWeight) {
  const physical = groundShadowPerHeight(altitude)
  if (physical <= 0.4 || realWeight === 1) return physical
  const excess = physical - 0.4
  const fitted = 0.4 + excess / (1 + 2 * excess)
  return fitted + realWeight * (physical - fitted)
}

export function deskShadowVector(scene, spriteSize, kind, realWeight = 1) {
  const altitude = scene?.altitude
  const heightRatio = DESK_OBJECT_PROFILES[kind]?.heightRatio
  if (!scene?.hasDirectLight || !Number.isFinite(altitude) || altitude <= 0 || altitude >= 90 || !Number.isFinite(scene.azimuth))
    return null
  if (!(heightRatio > 0) || !Number.isFinite(spriteSize) || spriteSize <= 0) return null
  const height = spriteSize * heightRatio
  const length = height * displayedReachPerHeight(altitude, realWeight)
  const direction = floorShadowDirection(scene.azimuth)
  return { x: direction.x * length, y: direction.y * length, height }
}
