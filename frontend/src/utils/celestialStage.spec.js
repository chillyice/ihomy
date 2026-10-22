import { describe, expect, it } from 'vitest'
import { moonLitPath, projectBody, projectTrack, skyStyle, stageLight } from './celestialStage'

describe('projectBody', () => {
  it('hides bodies at or below the horizon', () => {
    expect(projectBody(0, 180).visible).toBe(false)
    expect(projectBody(-3, 180).visible).toBe(false)
    expect(projectBody(null, 180).visible).toBe(false)
  })

  it('keeps a hidden body at its own side of the horizon', () => {
    expect(projectBody(-5, 90).x).toBeLessThan(10)
    expect(projectBody(-5, 270).x).toBeGreaterThan(90)
    expect(projectBody(-5, 90).y).toBe(100)
  })

  it('puts the zenith at the top and the horizon at the bottom', () => {
    expect(projectBody(90, 180).y).toBeCloseTo(0, 5)
    expect(projectBody(30, 180).y).toBeCloseTo(50, 5)
    expect(projectBody(0.1, 180).y).toBeCloseTo(99.83, 1)
  })

  it('maps east to the left and west to the right of south', () => {
    expect(projectBody(0.1, 90).x).toBeLessThan(10)
    expect(projectBody(0.1, 180).x).toBeCloseTo(50, 5)
    expect(projectBody(0.1, 270).x).toBeGreaterThan(90)
  })

  it('uses depth as a small scale cue for north vs south', () => {
    expect(projectBody(30, 180).depth).toBeGreaterThan(0.8)
    expect(projectBody(30, 0).depth).toBeLessThan(-0.8)
    expect(projectBody(30, 180).scale).toBeGreaterThan(1)
  })
})

describe('moonLitPath', () => {
  it('draws a straight terminator at the quarters', () => {
    expect(moonLitPath(0.25, 50)).toContain('A 0 50')
    expect(moonLitPath(0.75, 50)).toContain('A 0 50')
  })

  it('closes the disc at full moon', () => {
    const d = moonLitPath(0.5, 50)
    expect(d).toContain('A 50 50 0 0 0 0 50')
    expect(d).toContain('A 50 50 0 0 0 0 -50')
  })

  it('bulges the terminator by the phase angle', () => {
    expect(moonLitPath(0.125, 50)).toContain('A 35.36 50')
    expect(moonLitPath(0.875, 50)).toContain('A 35.36 50')
    expect(moonLitPath(0.375, 50)).toContain('A 35.36 50')
  })

  it('wraps negative and out-of-range phases', () => {
    expect(moonLitPath(-0.75, 50)).toBe(moonLitPath(0.25, 50))
    expect(moonLitPath(1.25, 50)).toBe(moonLitPath(0.25, 50))
  })
})

describe('stageLight', () => {
  const px = (v) => parseFloat(v)

  it('throws shadows to the opposite side of the light', () => {
    expect(px(stageLight(10, 90)['--cy-sh-x'])).toBeGreaterThan(0)
    expect(px(stageLight(10, 270)['--cy-sh-x'])).toBeLessThan(0)
  })

  it('lengthens shadows as the sun drops', () => {
    const high = Math.abs(px(stageLight(26, 120)['--cy-sh-x']))
    const low = Math.abs(px(stageLight(4, 120)['--cy-sh-x']))
    expect(low).toBeGreaterThan(high)
  })

  it('switches the scene to night below the horizon', () => {
    const night = stageLight(-12, 270)
    expect(night['--cy-night']).toBe('0.53')
    expect(night['--cy-sh-a']).toBe('0.10')
    expect(night['--cy-warm-a']).toBe('0.00')
  })

  it('keeps moonlight softer and cooler than sunlight', () => {
    const sun = stageLight(40, 180)
    const moon = stageLight(40, 180, { moon: true })
    expect(Number(moon['--cy-sh-a'])).toBeLessThan(Number(sun['--cy-sh-a']))
    expect(Number(moon['--cy-warm-a'])).toBeLessThan(Number(sun['--cy-warm-a']))
  })
})

describe('projectTrack', () => {
  const point = (t, alt, az) => ({ t, alt, az })

  it('connects the above-horizon samples into one path', () => {
    const paths = projectTrack([point(0, 10, 90), point(10, 30, 120), point(20, 50, 180)])
    expect(paths).toHaveLength(1)
    expect(paths[0].startsWith('M')).toBe(true)
    expect(paths[0].match(/L/g)).toHaveLength(2)
  })

  it('splits the path when the body dips below the horizon', () => {
    const paths = projectTrack([point(0, -5, 90), point(10, 20, 120), point(20, -2, 240), point(30, 15, 270)])
    expect(paths).toHaveLength(2)
  })

  it('returns nothing for empty or malformed input', () => {
    expect(projectTrack([])).toEqual([])
    expect(projectTrack(null)).toEqual([])
    expect(projectTrack([point(0, null, 90)])).toEqual([])
  })

  it('keeps every coordinate inside the 0-100 stage', () => {
    const d = projectTrack([point(0, 1, 90), point(10, 90, 180), point(20, 5, 270)])[0]
    for (const n of d.match(/-?\d+(\.\d+)?/g).map(Number)) {
      expect(n).toBeGreaterThanOrEqual(0)
      expect(n).toBeLessThanOrEqual(100)
    }
  })
})

const lum = (rgb) => {
  const [r, g, b] = rgb
    .match(/\d+/g)
    .slice(0, 3)
    .map((v) => Number(v) / 255)
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

describe('skyStyle', () => {
  it('darkens the page at night and switches text to light', () => {
    const night = skyStyle(-20)
    expect(night['--cy-bg']).toBe('rgb(10, 17, 29)')
    expect(night['--cy-ink']).toBe('#f1ece4')
    expect(night['color-scheme']).toBe('dark')
  })

  it('returns the paper palette at noon with dark text', () => {
    const noon = skyStyle(60)
    expect(noon['--cy-bg']).toBe('rgb(246, 242, 234)')
    expect(noon['--cy-ink']).toBe('#201d19')
    expect(noon['color-scheme']).toBe('light')
  })

  it('brightens monotonically from night to day', () => {
    const steps = [-30, -8, -1, 6, 20].map((a) => lum(skyStyle(a)['--cy-bg']))
    expect(steps).toEqual([...steps].sort((a, b) => a - b))
  })

  it('falls back to the day palette when altitude is missing', () => {
    expect(skyStyle(null)['--cy-bg']).toBe('rgb(246, 242, 234)')
    expect(skyStyle(undefined)['--cy-bg']).toBe('rgb(246, 242, 234)')
  })
})
