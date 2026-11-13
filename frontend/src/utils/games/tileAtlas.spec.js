import { describe, expect, it } from 'vitest'
import atlas from '@/assets/games/tiles-cards-atlas.json'
import {
  ATLAS_COLUMNS,
  ATLAS_ROWS,
  MAHJONG_FACE_COUNT,
  MAHJONG_FACE_START,
  atlasCell,
  atlasStyle,
  mahjongFaceStyle,
  pokerFaceStyle,
} from './tileAtlas'

const STYLE_KEYS = ['backgroundImage', 'backgroundPosition', 'backgroundRepeat', 'backgroundSize']

describe('tiles-cards 图集口径', () => {
  it('格数装得下麻将牌面 + 人头牌,且麻将起始格为 0', () => {
    expect(MAHJONG_FACE_START).toBe(0)
    expect(MAHJONG_FACE_COUNT).toBe(34)
    expect(ATLAS_COLUMNS * ATLAS_ROWS).toBeGreaterThanOrEqual(MAHJONG_FACE_START + MAHJONG_FACE_COUNT + atlas.poker.length)
  })

  it('牌面名字与花色/点数一一对应(含两张王)', () => {
    expect(atlas.poker).toHaveLength(14)
    for (const suit of ['spades', 'hearts', 'clubs', 'diamonds']) {
      for (const rank of ['J', 'Q', 'K']) expect(atlas.poker).toContain(`${suit}-${rank}`)
    }
    expect(atlas.poker).toEqual(expect.arrayContaining(['joker-small', 'joker-big']))
  })

  it('格号换算行列,越界一律为 null', () => {
    expect(atlasCell(0)).toEqual({ col: 0, row: 0 })
    expect(atlasCell(7)).toEqual({ col: 7, row: 0 })
    expect(atlasCell(8)).toEqual({ col: 0, row: 1 })
    expect(atlasCell(ATLAS_COLUMNS * ATLAS_ROWS - 1)).toEqual({ col: ATLAS_COLUMNS - 1, row: ATLAS_ROWS - 1 })
    expect(atlasCell(-1)).toBeNull()
    expect(atlasCell(ATLAS_COLUMNS * ATLAS_ROWS)).toBeNull()
    expect(atlasCell(1.5)).toBeNull()
  })

  it('背景样式把整图当 sprite 定位到目标格', () => {
    const first = atlasStyle(0)
    expect(Object.keys(first).sort()).toEqual(STYLE_KEYS)
    expect(first.backgroundSize).toMatch(/^\d+(\.\d+)?% \d+(\.\d+)?%$/)
    expect(first.backgroundRepeat).toBe('no-repeat')
    expect(first.backgroundImage).toMatch(/tiles-cards-atlas.*\.png/)
    // 每格放大到超出牌框(裁掉格间留白):8 列 → 宽必须大于 800%,6 行 → 高大于 600%
    const [sizeX, sizeY] = first.backgroundSize.split(' ').map((v) => parseFloat(v))
    expect(sizeX).toBeGreaterThan(800)
    expect(sizeY).toBeGreaterThan(600)
    // 首格贴左上、末格贴右下:定位百分比从接近 0 递增到接近 100
    const last = atlasStyle(ATLAS_COLUMNS * ATLAS_ROWS - 1)
    const pos = (style) => style.backgroundPosition.split(' ').map((v) => parseFloat(v))
    const [x0, y0] = pos(first)
    const [x1, y1] = pos(last)
    expect(x0).toBeGreaterThanOrEqual(0)
    expect(y0).toBeGreaterThanOrEqual(0)
    expect(x1).toBeLessThanOrEqual(100)
    expect(y1).toBeLessThanOrEqual(100)
    expect(x1).toBeGreaterThan(x0)
    expect(y1).toBeGreaterThan(y0)
    expect(atlasStyle(ATLAS_COLUMNS * ATLAS_ROWS)).toBeNull()
  })

  it('34 种麻将牌都有牌面,越界返回 null', () => {
    for (let kind = 0; kind < MAHJONG_FACE_COUNT; kind++) {
      expect(mahjongFaceStyle(kind), `kind=${kind}`).toBeTruthy()
    }
    expect(mahjongFaceStyle(-1)).toBeNull()
    expect(mahjongFaceStyle(MAHJONG_FACE_COUNT)).toBeNull()
    expect(mahjongFaceStyle(undefined)).toBeNull()
    // kind 与图集格同序:第 34 格(白板)紧跟字牌之后
    expect(mahjongFaceStyle(MAHJONG_FACE_COUNT - 1)).toEqual(atlasStyle(MAHJONG_FACE_START + MAHJONG_FACE_COUNT - 1))
  })

  it('所有人头牌都有牌面,A~10 留给 CSS 铺点', () => {
    for (const suit of ['S', 'H', 'C', 'D']) {
      for (const rank of [11, 12, 13]) expect(pokerFaceStyle(suit, rank), `${suit}${rank}`).toBeTruthy()
      for (let rank = 1; rank <= 13; rank++) expect(pokerFaceStyle(suit, rank)).toBeTruthy()
    }
    expect(pokerFaceStyle('X', 11)).toBeNull()
    expect(pokerFaceStyle('JOKER', 14)).toBeTruthy()
    expect(pokerFaceStyle('JOKER', 15)).toBeTruthy()
  })
})
