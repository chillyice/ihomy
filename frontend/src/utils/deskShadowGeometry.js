import { DESK_OBJECT_PROFILES } from './deskObjectProfiles'
import { floorShadowDirection, groundShadowPerHeight } from './windowLight'

export function deskShadowVector(scene, spriteSize, kind, realWeight = 1) {
  const altitude = scene?.altitude
  const heightRatio = DESK_OBJECT_PROFILES[kind]?.heightRatio
  if (!scene?.hasDirectLight || !Number.isFinite(altitude) || altitude <= 0 || altitude >= 90 || !Number.isFinite(scene.azimuth))
    return null
  if (!(heightRatio > 0) || !Number.isFinite(spriteSize) || spriteSize <= 0) return null
  const height = spriteSize * heightRatio
  const length = height * groundShadowPerHeight(altitude)
  const direction = floorShadowDirection(scene.azimuth, realWeight)
  return { x: direction.x * length, y: direction.y * length, height }
}
