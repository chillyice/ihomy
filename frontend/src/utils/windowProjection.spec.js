import { describe, expect, it, vi } from 'vitest'
import { drawWindowProjection, sashApertures, windowTransform } from './windowProjection'
import { CASEMENTS, chooseWindowSashes, weatherClosesWindow } from './windowSash'
import { getSunScene } from './windowLight'
import { sunPosition } from './solarPosition'

function openWindow(layout, ...angles) {
  const samples = [layout, ...angles]
  return chooseWindowSashes(false, () => samples.shift())
}

describe('window sash', () => {
  it.each([
    [null, false],
    [{ condition: 'rain', precipLevel: 2 }, false],
    [{ condition: 'rain', precipLevel: '3' }, true],
    [{ condition: 'snow', precipLevel: 2 }, false],
    [{ condition: 'snow', precipLevel: 3 }, true],
    [{ condition: 'rain', iconCode: '302', precipLevel: 1 }, true],
    [{ condition: 'rain', precipLevel: 1, text: '冻雨' }, true],
    [{ condition: 'rain', precipLevel: 1, nowFull: { text: '强阵雨' } }, true],
    [{ condition: 'clear', nowFull: { windScale: '4-5', windSpeed: '38' } }, false],
    [{ condition: 'cloud', nowFull: { windScale: '5-6' } }, true],
    [{ condition: 'clear', windScale: 6 }, true],
    [{ condition: 'clear', nowFull: { windSpeed: '39' } }, true],
  ])('applies the storm closure rule to %j', (weather, closed) => {
    expect(weatherClosesWindow(weather)).toBe(closed)
  })

  it('only allows a shut pose after an open one, even when the random draw repeats', () => {
    let wasOpen = false
    const poses = Array.from({ length: 5 }, () => {
      wasOpen = chooseWindowSashes(wasOpen, () => 0.2).some((sash) => sash.depth > 0)
      return wasOpen
    })
    expect(poses).toEqual([true, false, true, false, true])
  })

  it('lets the two meeting stiles own the whole centre seam', () => {
    const [left, right] = CASEMENTS
    expect(left.latch).toBe(right.latch)
    expect(left.latch - left.hinge).toBeCloseTo(right.hinge - right.latch)
  })

  it.each([0, 0.5, 0.999])('mirrors the open gap around the meeting stiles at sample %f', (sample) => {
    const [left, shutRight] = openWindow(0.1, sample)
    const [shutLeft, right] = openWindow(0.5, sample)
    expect([shutLeft, shutRight]).toEqual(CASEMENTS)
    expect(left.hinge).toBeLessThan(left.tipX)
    expect(left.tipX).toBeLessThan(left.latch)
    expect(left.tipX + right.tipX).toBeCloseTo(1)
    expect(left.depth).toBeCloseTo(right.depth)
    expect(Math.hypot(left.tipX - left.hinge, left.depth)).toBeCloseTo(left.latch - left.hinge)
    expect(left.depth).toBeCloseTo((left.latch - left.hinge) * Math.sin(((30 + 45 * sample) * Math.PI) / 180))
  })

  it('draws an independent opening angle for each sash when both are open', () => {
    const [left, right] = openWindow(0.9, 0.1, 0.85)
    expect(left).toEqual(openWindow(0.1, 0.1)[0])
    expect(right).toEqual(openWindow(0.5, 0.85)[1])
    expect(left.depth).toBeLessThan(right.depth)
  })
})

describe('window-plane projection', () => {
  it.each([0, 1])('releases both rail strips and the stiles on open side %i while keeping the other panes intact', (side) => {
    const shut = CASEMENTS.map(sashApertures)
    const open = openWindow(side === 0 ? 0.1 : 0.5, 0.65).map(sashApertures)
    expect(open[1 - side]).toEqual(shut[1 - side])
    expect(open[side]).toHaveLength(1)
    const [[jamb, sill, openingWidth, openingHeight]] = open[side]
    const [[left, glassSill, width, lowerHeight], [, upperStart, , upperHeight]] = shut[side]
    expect(width).toBeCloseTo(0.452)
    expect(lowerHeight).toBeCloseTo(0.44175)
    expect(upperHeight).toBeCloseTo(0.47575)
    expect(glassSill - sill).toBeCloseTo(0.036)
    expect(sill + openingHeight - (upperStart + upperHeight)).toBeCloseTo(0.036)
    expect(jamb).toBeLessThan(left)
    expect(jamb + openingWidth).toBeGreaterThan(left + width)
  })

  it.each([
    [1200, 800],
    [390, 844],
  ])('mirrors the footprint with solar bearing in a %i × %i viewport', (width, height) => {
    const [paneWidth, , morningShear, morningDepth, morningX, morningY] = windowTransform(width, height, 25, 115)
    const [, , eveningShear, eveningDepth, eveningX, eveningY] = windowTransform(width, height, 25, 245)
    expect(morningShear).toBeGreaterThan(0)
    expect(eveningShear).toBeCloseTo(-morningShear)
    expect(morningDepth).toBeCloseTo(eveningDepth)
    expect(morningY).toBeCloseTo(eveningY)
    expect(morningX + paneWidth + eveningX).toBeCloseTo(width)
  })

  it.each([
    [1200, 800],
    [390, 844],
  ])('matches the Jinan noon landmarks at %i by %i', (width, height) => {
    const noon = (altitude) => windowTransform(width, height, altitude, 180, 0, altitude)
    const summer = noon(76.79)
    const equinox = noon(53.35)
    const winter = noon(29.91)
    expect(summer[5] / height).toBeCloseTo(0.01, 8)
    expect(equinox[5] / height).toBeCloseTo(0.05, 8)
    expect((equinox[5] + equinox[3]) / height).toBeCloseTo(0.95, 8)
    expect(winter[5] / height).toBeCloseTo(0.09, 8)
    expect(winter[3] / summer[3]).toBeCloseTo(1.15, 8)
    expect((summer[3] + winter[3]) / (2 * height)).toBeCloseTo(0.9, 8)
  })

  it('keeps the far frame edge in its summer framing while date and projection standard change', () => {
    const atMidday = (date) => {
      const slots = Array.from({ length: 288 }, (_, i) => sunPosition(36.65, 117.12, date, i * 5))
      return getSunScene({ slots, sunrise: '06:00', sunset: '18:00' }, 144)
    }
    const june = atMidday('2026-06-21')
    const november = atMidday('2026-11-25')
    const transform = (scene, weight) => windowTransform(1200, 800, scene.altitude, scene.azimuth, weight, scene.noonAltitude)
    const [[, bottom, , frameHeight]] = sashApertures(openWindow(0.1, 0.5)[0])
    const farEdge = (scene) => {
      const [, , , depth, , originY] = transform(scene, 0)
      return (originY + (bottom + frameHeight) * depth) / 800
    }
    expect(farEdge(june)).toBeGreaterThan(0.86)
    expect(farEdge(june)).toBeLessThan(0.92)
    expect(farEdge(november)).toBeGreaterThan(farEdge(june))
    expect(june.lightOpacity).toBeGreaterThan(november.lightOpacity)
    const realLength = (scene) => {
      const [, , dx, dy] = transform(scene, 1)
      return Math.hypot(dx, dy)
    }
    const tanAltitude = (scene) => Math.tan((scene.altitude * Math.PI) / 180)
    expect(realLength(november) / realLength(june)).toBeCloseTo(tanAltitude(june) / tanAltitude(november), 8)
  })

  it('aligns both projection standards and their transition with direct sunlight throughout the year', () => {
    for (let day = 1; day <= 365; day++) {
      const date = new Date(Date.UTC(2026, 0, day)).toISOString().slice(0, 10)
      const slots = Array.from({ length: 288 }, (_, i) => sunPosition(36.65, 117.12, date, i * 5))
      const peak = Math.max(...slots.map((s) => s.altitude))
      let maxAngleError = 0
      for (const { altitude, azimuth } of slots) {
        if (altitude <= 0 || azimuth <= 90 || azimuth >= 270) continue
        for (const weight of [0, 0.5, 1]) {
          const [, , dx, dy] = windowTransform(1200, 800, altitude, azimuth, weight, peak)
          const tilt = (Math.atan2(dx, dy) * 180) / Math.PI
          maxAngleError = Math.max(maxAngleError, Math.abs(tilt - (180 - azimuth)))
        }
      }
      expect(maxAngleError, date).toBeLessThan(1e-8)
    }
  })

  it.each([
    [0.01, 90.00001],
    [89.999, 180],
    [0.01, 269.99999],
  ])('preserves the bearing at altitude %f and azimuth %f', (altitude, azimuth) => {
    const [, , dx, dy] = windowTransform(1200, 800, altitude, azimuth, 0, 89.999)
    expect(Number.isFinite(dx) && Number.isFinite(dy)).toBe(true)
    expect((Math.atan2(dx, dy) * 180) / Math.PI).toBeCloseTo(180 - azimuth, 8)
  })

  it.each([30, 65])('shortens the corrected shadow towards a daily peak of %i° and lengthens it again before sunset', (peak) => {
    const slots = [
      { time: '07:05', altitude: 2, azimuth: 115 },
      { time: '08:30', altitude: 15, azimuth: 130 },
      { time: '12:20', altitude: peak, azimuth: 180 },
      { time: '16:10', altitude: 15, azimuth: 230 },
      { time: '17:35', altitude: 2, azimuth: 245 },
    ]
    const day = { sunrise: '07:00', sunset: '17:40', slots }
    const lengths = slots.map((_, index) => {
      const scene = getSunScene(day, index)
      expect(scene.hasDirectLight).toBe(true)
      expect(scene.noonAltitude).toBe(peak)
      return windowTransform(1200, 800, scene.altitude, scene.azimuth, 0, scene.noonAltitude)[3]
    })
    const [morning, midMorning, noon, afternoon, evening] = lengths
    expect(morning).toBeGreaterThan(midMorning)
    expect(midMorning).toBeGreaterThan(noon)
    expect(morning / noon).toBeCloseTo(Math.tan((peak * Math.PI) / 180) / Math.tan((2 * Math.PI) / 180), 8)
    expect(afternoon).toBeCloseTo(midMorning)
    expect(evening).toBeCloseTo(morning)
  })

  it.each([0, 1])('keeps extending past the viewport as the sun approaches the horizon (real weight %i)', (realWeight) => {
    const reach = (altitude) => {
      const [, , dx, dy] = windowTransform(390, 844, altitude, 115, realWeight, 40)
      return Math.hypot(dx, dy)
    }
    for (const altitude of [1, 0.1, 0.01]) {
      expect(reach(altitude / 10) / reach(altitude)).toBeCloseTo(10, 2)
    }
  })

  it.each([
    [0, 76.79],
    [0, 29.91],
    [0.5, 53.35],
    [1, 53.35],
  ])('casts the sill, transom and head from their heights above one fixed wall foot (weight %f, noon %f°)', (weight, peak) => {
    const width = 1200
    const at = (altitude, azimuth) => windowTransform(width, 800, altitude, azimuth, weight, peak)
    const [paneWidth, , , noonRise, , noonSillY] = at(peak, 180)
    const wallX = (width - paneWidth) / 2
    const wallY = noonSillY - 0.15 * noonRise
    const [[, frameBottom, , frameHeight]] = sashApertures(openWindow(0.1, 0.5)[0])
    const [[, glassBottom, , glassHeight], [, upperGlassBottom]] = sashApertures(CASEMENTS[0])
    const transom = (glassBottom + glassHeight + upperGlassBottom) / 2
    for (const v of [frameBottom, glassBottom, transom, frameBottom + frameHeight]) {
      const noonDistance = noonSillY + v * noonRise - wallY
      expect(noonDistance).toBeGreaterThan(0)
      for (const [altitude, azimuth] of [
        [1, 110],
        [15, 145],
        [15, 215],
        [1, 250],
      ]) {
        const [, , dx, dy, x, y] = at(altitude, azimuth)
        const offsetX = x + v * dx - wallX
        const offsetY = y + v * dy - wallY
        const bearing = ((180 - azimuth) * Math.PI) / 180
        const distance = (noonDistance * Math.tan((peak * Math.PI) / 180)) / Math.tan((altitude * Math.PI) / 180)
        expect(Math.atan2(offsetX, offsetY)).toBeCloseTo(bearing, 8)
        expect(offsetY).toBeCloseTo(distance * (1 - weight + weight * Math.cos(bearing)), 7)
      }
    }
  })

  it.each(['2026-06-21', '2026-11-25'])('moves all three horizontal rails towards the wall until noon and out afterwards on %s', (date) => {
    const slots = Array.from({ length: 288 }, (_, i) => sunPosition(36.65, 117.12, date, i * 5))
    const peak = Math.max(...slots.map((s) => s.altitude))
    const noon = slots.findIndex((s) => s.altitude === peak)
    const direct = slots.map((s, i) => ({ ...s, i })).filter((s) => s.altitude > 0 && s.azimuth > 90 && s.azimuth < 270)
    expect(direct[0].i).toBeLessThan(noon)
    expect(direct.at(-1).i).toBeGreaterThan(noon)
    for (const rail of [-0.02075, 0.483, 1.02075]) {
      let previous
      for (const { i, altitude, azimuth } of direct) {
        const [, , , depth, , y] = windowTransform(1280, 902, altitude, azimuth, 0, peak)
        const edge = y + rail * depth
        if (previous !== undefined) {
          if (i <= noon) expect(edge).toBeLessThan(previous)
          else expect(edge).toBeGreaterThan(previous)
        }
        previous = edge
      }
    }
  })

  it('projects a finite window height on days when the solar noon reaches the zenith', () => {
    const reach = (altitude) => windowTransform(1200, 800, altitude, 180, 0, 90)[3]
    expect(reach(1)).toBeGreaterThan(reach(45))
    expect(reach(45)).toBeLessThan(8000)
    expect(reach(89.999)).toBeLessThan(1)
    expect(reach(90)).toBe(0)
  })

  it('projects a south-facing vertical window onto the floor using both solar angles', () => {
    const [, , dx, dy, , top] = windowTransform(1200, 1000, 30, 120, 1, 65)
    expect(Math.hypot(dx, dy)).toBeCloseTo(2000 * Math.sqrt(3))
    expect(dy).toBeCloseTo((2000 * Math.sqrt(3)) / 2)
    expect(dx).toBeCloseTo(3000)
    expect(top).toBeCloseTo(dy * 0.15)
    expect(windowTransform(1200, 1000, 45, 180, 1)[3]).toBeCloseTo(2000)
  })

  it.each([
    ['clear', false],
    ['rain', true],
    ['snow', true],
    ['thunder', true],
  ])('clears the previous footprint for weather %s, direct sun %s', (weather, hasDirectLight) => {
    const clearRect = vi.fn()
    const canvas = { width: 512, height: 256, getContext: () => ({ clearRect }) }
    drawWindowProjection(canvas, { width: 4096, height: 2048, scene: { hasDirectLight }, weather })
    expect(clearRect).toHaveBeenCalledWith(0, 0, 512, 256)
  })

  it.each([0, 90])('clears the canvas at the solar boundary of %i degrees', (altitude) => {
    const clearRect = vi.fn()
    const canvas = { width: 100, height: 100, getContext: () => ({ clearRect }) }
    drawWindowProjection(canvas, { width: 200, height: 200, scene: { altitude, azimuth: 180, hasDirectLight: true } })
    expect(clearRect).toHaveBeenCalledWith(0, 0, 100, 100)
  })
})
