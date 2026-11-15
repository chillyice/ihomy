import { describe, expect, it } from 'vitest'
import { EMBEDDED_GAMES, embeddedGameSrc, findEmbeddedGame } from './embeddedGames'
import { GAME_CATEGORIES } from './gameCategory'

describe('embeddedGames', () => {
  it('uses a unique slug and an in-whitelist category for every entry', () => {
    const slugs = EMBEDDED_GAMES.map((g) => g.slug)
    expect(new Set(slugs).size).toBe(slugs.length)
    for (const g of EMBEDDED_GAMES) {
      expect(g.slug).toMatch(/^[a-z0-9-]+$/)
      expect(GAME_CATEGORIES).toContain(g.category)
      expect(g.titleKey).toBe(`games.embedded.${g.slug}.title`)
      expect(g.descKey).toBe(`games.embedded.${g.slug}.desc`)
    }
  })

  it('builds a same-origin static path instead of an external url', () => {
    expect(embeddedGameSrc('bubble')).toBe('/mini-games/bubble/index.html')
  })

  it('returns null for an unknown slug so a hand-typed route does not crash', () => {
    expect(findEmbeddedGame('bubble')).toMatchObject({ slug: 'bubble' })
    expect(findEmbeddedGame('nope')).toBeNull()
    expect(findEmbeddedGame(undefined)).toBeNull()
  })
})
