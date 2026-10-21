import { describe, expect, it } from 'vitest'
import { moonLitPath, projectBody } from './celestialStage'

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
