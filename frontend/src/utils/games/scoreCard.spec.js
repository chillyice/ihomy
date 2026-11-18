import { describe, expect, it } from 'vitest'
import { CARD_HEIGHT, CARD_WIDTH, canvasFont, computeScoreCardLayout, formatStat, withAlpha } from './scoreCard'

describe('scoreCard 分享图版式', () => {
  it('默认 4 列 2 块数据:棋盘是正方形且完全落在卡片内', () => {
    const l = computeScoreCardLayout({ gridSize: 4, statsCount: 2 })
    expect(l.width).toBe(CARD_WIDTH)
    expect(l.height).toBe(CARD_HEIGHT)
    expect(l.board.size).toBeGreaterThan(0)
    expect(l.board.x).toBeGreaterThanOrEqual(l.pad)
    expect(l.board.x * 2 + l.board.size).toBeCloseTo(l.width, 5)
    expect(l.board.y).toBeGreaterThan(l.subtitle.y + l.subtitle.height)
    expect(l.board.y + l.board.size).toBeLessThanOrEqual(CARD_HEIGHT)
  })

  it('棋盘不压数据块,数据块不压页脚', () => {
    for (const gridSize of [4, 5, 6, 7, 8]) {
      for (const statsCount of [1, 2, 3]) {
        const l = computeScoreCardLayout({ gridSize, statsCount })
        const boardBottom = l.board.y + l.board.size
        for (const stat of l.stats) {
          expect(stat.y).toBeGreaterThan(boardBottom)
          expect(stat.y + stat.h).toBeLessThan(l.footer.y)
          expect(stat.x).toBeGreaterThanOrEqual(l.pad)
          expect(stat.x + stat.w).toBeLessThanOrEqual(l.width - l.pad + 0.001)
        }
        expect(l.footer.y + l.footer.height).toBeLessThanOrEqual(l.height)
      }
    }
  })

  it('数据块横向等分且首尾贴齐内容宽', () => {
    const l = computeScoreCardLayout({ statsCount: 3 })
    expect(l.stats).toHaveLength(3)
    const widths = new Set(l.stats.map((s) => Math.round(s.w)))
    expect(widths.size).toBe(1)
    expect(l.stats[0].x).toBeCloseTo(l.pad, 5)
    expect(l.stats[2].x + l.stats[2].w).toBeCloseTo(l.width - l.pad, 5)
  })

  it('格子尺寸随列数变化但恒为正,间距不挤掉格子', () => {
    const cells = [4, 6, 8].map((n) => {
      const { board } = computeScoreCardLayout({ gridSize: n })
      expect(board.cell).toBeGreaterThan(0)
      // n 个格子 + (n+1) 个间距 = 棋盘边长
      expect(board.cell * n + board.gap * (n + 1)).toBeCloseTo(board.size, 5)
      return board.cell
    })
    expect(cells[0]).toBeGreaterThan(cells[1])
    expect(cells[1]).toBeGreaterThan(cells[2])
  })

  it('列数/块数越界时收敛到合法范围', () => {
    const low = computeScoreCardLayout({ gridSize: 1, statsCount: 0 })
    expect(low.board.gridSize).toBe(2)
    expect(low.stats).toHaveLength(1)
    const high = computeScoreCardLayout({ gridSize: 99, statsCount: 9 })
    expect(high.board.gridSize).toBe(12)
    expect(high.stats).toHaveLength(4)
  })
})

describe('scoreCard 颜色与文案', () => {
  it('hex 加透明度,rgb() 原样透传', () => {
    expect(withAlpha('#866448', 0.5)).toBe('rgba(134, 100, 72, 0.5)')
    expect(withAlpha('#fff', 0.2)).toBe('rgba(255, 255, 255, 0.2)')
    expect(withAlpha('rgb(1, 2, 3)', 0.4)).toBe('rgb(1, 2, 3)')
    expect(withAlpha('', 0.4)).toBe('transparent')
  })

  it('分数带千分位,非数字原样显示', () => {
    expect(formatStat(12345)).toBe('12,345')
    expect(formatStat('9876543')).toBe('9,876,543')
    expect(formatStat('12:34')).toBe('12:34')
  })

  it('font 串带单位与字体栈', () => {
    expect(canvasFont(48, 800)).toMatch(/^800 48px /)
    expect(canvasFont(26)).toMatch(/^700 26px /)
  })
})
