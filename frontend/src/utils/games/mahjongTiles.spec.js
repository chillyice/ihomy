import { describe, expect, it } from 'vitest'
import { MAHJONG_TILE_NAMES, mahjongTileSrc } from './mahjongTiles'

describe('mahjongTiles', () => {
  it('34 种牌与 kind 一一对应,白板留空', () => {
    expect(MAHJONG_TILE_NAMES).toHaveLength(34)
    for (let kind = 0; kind < 33; kind++) {
      // 小体积 SVG 会被 Vite 内联成 data URL,大体积才是 .svg 资源地址
      expect(mahjongTileSrc(kind), `kind ${kind}`).toMatch(/(\.svg$|^data:image\/svg\+xml)/)
    }
    expect(mahjongTileSrc(33)).toBe('')
  })

  it('每个牌种的图不重复', () => {
    const urls = Array.from({ length: 33 }, (_, kind) => mahjongTileSrc(kind))
    expect(new Set(urls).size).toBe(33)
  })

  it('越界 kind 不会抛错', () => {
    expect(mahjongTileSrc(-1)).toBe('')
    expect(mahjongTileSrc(34)).toBe('')
    expect(mahjongTileSrc(undefined)).toBe('')
  })
})
