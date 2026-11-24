import { DESK_OBJECT_PROFILES } from './deskObjectProfiles'
import { floorShadowDirection } from './windowLight'

export function deskShadowVector(scene, spriteSize, kind) {
  const altitude = scene?.altitude
  const heightRatio = DESK_OBJECT_PROFILES[kind]?.heightRatio
  if (!scene?.hasDirectLight || !Number.isFinite(altitude) || altitude <= 0 || altitude >= 90 || !Number.isFinite(scene.azimuth))
    return null
  if (!(heightRatio > 0) || !Number.isFinite(spriteSize) || spriteSize <= 0) return null
  const height = spriteSize * heightRatio
  const length = height / Math.tan((altitude * Math.PI) / 180)
  const direction = floorShadowDirection(scene.azimuth)
  return { x: direction.x * length, y: direction.y * length, blur: 1 + height * 0.12 + length * 0.1 }
}
