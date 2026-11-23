import { describe, expect, it } from 'vitest'
import { MORNING_SCENES } from '@/utils/morning'
import { pickMorningBackdrop, weatherBackdrop } from './backdrops'

describe('weather window backdrops', () => {
  it.each(MORNING_SCENES)('has distinct day and night pictures for %s', (condition) => {
    const day = weatherBackdrop(condition, false)
    const night = weatherBackdrop(condition, true)
    expect(day).toBeTruthy()
    expect(night).toBeTruthy()
    expect(day).not.toBe(night)
  })

  it('keeps the window stable while the morning overlay alternates', () => {
    const first = pickMorningBackdrop('rain', false)
    const windowPhoto = weatherBackdrop('rain', false)
    const second = pickMorningBackdrop('rain', false)
    expect(second).not.toBe(first)
    expect([first, second]).toContain(windowPhoto)
    expect(weatherBackdrop('rain', false)).toBe(windowPhoto)
    expect(pickMorningBackdrop('rain', false)).toBe(first)
  })
})
