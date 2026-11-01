import { describe, it, expect } from 'vitest'
import { CELLS, WIN_VALUE, createGame, gridOf, hasWon, isGameOver, isValidTiles, move, spawnTile, tilesFromGrid } from './game2048'

describe('2048 移动与合并', () => {
  it('同一行同值合并一次并计分', () => {
    const tiles = tilesFromGrid([
      [2, 2, 2, 2],
      [0, 0, 0, 0],
      [0, 0, 0, 0],
      [0, 0, 0, 0],
    ])
    const res = move(tiles, 'left')
    expect(gridOf(res.tiles)[0]).toEqual([4, 4, 0, 0])
    expect(res.gained).toBe(8)
    expect(res.moved).toBe(true)
    expect(res.mergedIds).toHaveLength(2)
  })

  it('一次移动里新合并的方块不再参与合并', () => {
    const tiles = tilesFromGrid([
      [4, 4, 8, 8],
      [0, 0, 0, 0],
      [0, 0, 0, 0],
      [0, 0, 0, 0],
    ])
    const res = move(tiles, 'left')
    expect(gridOf(res.tiles)[0]).toEqual([8, 16, 0, 0])
    expect(res.gained).toBe(24)
  })

  it('向右/向上/向下都按对应边紧凑', () => {
    const row = tilesFromGrid([
      [2, 0, 2, 0],
      [0, 0, 0, 0],
      [0, 0, 0, 0],
      [0, 0, 0, 0],
    ])
    expect(gridOf(move(row, 'right').tiles)[0]).toEqual([0, 0, 0, 4])

    const column = tilesFromGrid([
      [2, 0, 0, 0],
      [2, 0, 0, 0],
      [4, 0, 0, 0],
      [4, 0, 0, 0],
    ])
    expect(gridOf(move(column, 'up').tiles).map((r) => r[0])).toEqual([4, 8, 0, 0])
    expect(gridOf(move(column, 'down').tiles).map((r) => r[0])).toEqual([0, 0, 4, 8])
  })

  it('无可移动时 moved=false 且盘面不变', () => {
    const tiles = tilesFromGrid([
      [2, 4, 2, 4],
      [4, 2, 4, 2],
      [2, 4, 2, 4],
      [4, 2, 4, 2],
    ])
    for (const dir of ['left', 'right', 'up', 'down']) {
      const res = move(tiles, dir)
      expect(res.moved).toBe(false)
      expect(res.gained).toBe(0)
      expect(gridOf(res.tiles)).toEqual(gridOf(tiles))
    }
  })

  it('合并不改变未参与移动方块的 id', () => {
    const tiles = tilesFromGrid([
      [2, 2, 4, 0],
      [0, 0, 0, 0],
      [0, 0, 0, 0],
      [0, 0, 0, 0],
    ])
    const res = move(tiles, 'left')
    const ids = res.tiles.map((t) => t.id)
    expect(new Set(ids).size).toBe(ids.length)
    // 合并出的方块沿用靠前那个的 id,未合并的 4 保留原 id
    expect(ids.sort()).toEqual([1, 3])
  })
})

describe('2048 落子与胜负', () => {
  it('新方块只落在空格,数值只有 2/4', () => {
    const tiles = tilesFromGrid([
      [2, 0, 0, 0],
      [0, 0, 0, 0],
      [0, 0, 0, 0],
      [0, 0, 0, 0],
    ])
    const rolls = [0, 0]
    const tile = spawnTile(tiles, () => rolls.shift() ?? 0)
    expect(tile.value).toBe(2)
    expect(tile.row * 4 + tile.col).not.toBe(0)
    const four = spawnTile(
      tiles,
      (() => {
        const seq = [0, 0.95]
        return () => seq.shift()
      })(),
    )
    expect(four.value).toBe(4)
  })

  it('满盘时不再落子', () => {
    const full = tilesFromGrid([
      [2, 4, 2, 4],
      [4, 2, 4, 2],
      [2, 4, 2, 4],
      [4, 2, 4, 2],
    ])
    expect(spawnTile(full)).toBeNull()
    expect(isGameOver(full)).toBe(true)
  })

  it('还有空格或可合并就不算结束', () => {
    const withSpace = tilesFromGrid([
      [2, 4, 2, 4],
      [4, 2, 4, 2],
      [2, 4, 2, 4],
      [4, 2, 4, 0],
    ])
    expect(isGameOver(withSpace)).toBe(false)
    const mergeable = tilesFromGrid([
      [2, 2, 4, 8],
      [4, 8, 16, 32],
      [8, 16, 32, 64],
      [16, 32, 64, 128],
    ])
    expect(isGameOver(mergeable)).toBe(false)
  })

  it('达到 2048 算通关', () => {
    expect(
      hasWon(
        tilesFromGrid([
          [1024, 1024, 0, 0],
          [0, 0, 0, 0],
          [0, 0, 0, 0],
          [0, 0, 0, 0],
        ]),
      ),
    ).toBe(false)
    const res = move(
      tilesFromGrid([
        [1024, 1024, 0, 0],
        [0, 0, 0, 0],
        [0, 0, 0, 0],
        [0, 0, 0, 0],
      ]),
      'left',
    )
    expect(hasWon(res.tiles)).toBe(true)
    expect(gridOf(res.tiles)[0][0]).toBe(WIN_VALUE)
  })

  it('开局两个方块且都在盘内', () => {
    const tiles = createGame(() => 0)
    expect(tiles).toHaveLength(2)
    expect(isValidTiles(tiles)).toBe(true)
    expect(new Set(tiles.map((t) => t.row * 4 + t.col)).size).toBe(2)
  })

  it('读档校验挡住越界/重叠/非 2 的幂', () => {
    expect(isValidTiles([{ id: 1, value: 2, row: 0, col: 0 }])).toBe(true)
    expect(isValidTiles([{ id: 1, value: 2, row: 4, col: 0 }])).toBe(false)
    expect(
      isValidTiles([
        { id: 1, value: 2, row: 0, col: 0 },
        { id: 2, value: 4, row: 0, col: 0 },
      ]),
    ).toBe(false)
    expect(isValidTiles([{ id: 1, value: 3, row: 0, col: 0 }])).toBe(false)
    expect(isValidTiles(new Array(CELLS + 1).fill({ id: 1, value: 2, row: 0, col: 0 }))).toBe(false)
  })
})
