import { describe, expect, it } from 'vitest'
import { resolveMorningWeather } from './morningWeather'

describe('resolveMorningWeather', () => {
  it('separates overcast from the cloud values that can have sun', () => {
    expect(resolveMorningWeather({ condition: 'cloud', iconCode: 104 })).toBe('overcast')
    expect(resolveMorningWeather({ condition: 'cloud', iconCode: '101' })).toBe('cloud')
    expect(resolveMorningWeather({ condition: 'cloud', iconCode: '103' })).toBe('cloud')
  })

  it.each(['302', '303', '304'])('uses the thunder scene for code %s even when the feed says rain', (iconCode) => {
    expect(resolveMorningWeather({ condition: 'rain', iconCode })).toBe('thunder')
  })

  it('recognizes the clear night icon and retains normal rain and legacy weather data', () => {
    expect(resolveMorningWeather({ condition: 'cloud', nowFull: { icon: '150' } })).toBe('clear')
    expect(resolveMorningWeather({ condition: 'rain', iconCode: '305' })).toBe('rain')
    expect(resolveMorningWeather({ condition: 'snow' })).toBe('snow')
    expect(resolveMorningWeather(null)).toBe('clear')
    expect(resolveMorningWeather({ condition: 'unknown' })).toBe('clear')
  })
})
