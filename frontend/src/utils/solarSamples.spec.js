import { describe, expect, it } from 'vitest'
import { sampleSolarDirection, solarSlotForTime, solarSlotPosition } from './solarSamples'

describe('solar samples', () => {
  it('interpolates across north at midnight and holds the last observation until the new day arrives', () => {
    const info = {
      slots: [{ epochMillis: 1000, time: '23:55', altitude: 20, azimuth: 359 }],
      nextMidnight: { epochMillis: 301000, time: '00:00', altitude: 20, azimuth: 1 },
    }
    const position = solarSlotPosition(info.slots, 151000, 287)
    expect(position).toBe(0.5)
    const sample = sampleSolarDirection(info, position)
    expect(sample.time).toBe('23:57')
    expect(sample.azimuth).toBeCloseTo(0)
    expect(sample.altitude).toBeCloseTo(20, 2)
    expect(sampleSolarDirection(info, 1)).toEqual(info.nextMidnight)
  })

  it('selects by elapsed time and keeps the desired occurrence of a repeated local time', () => {
    const slots = [{ time: '01:55', epochMillis: 1000 }, { time: '01:00' }, { time: '01:55' }, { time: '02:00' }]
    expect(solarSlotPosition(slots, 601000, 0)).toBe(2)
    expect(solarSlotForTime(slots, 115, 2)).toBe(2)
    expect(solarSlotForTime(slots, 115, 0)).toBe(0)
    expect(solarSlotForTime([{ time: '01:55' }, { time: '03:00' }], 150)).toBe(1)
  })
})
