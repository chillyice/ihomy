import { describe, expect, it } from 'vitest'
import {
  BED_MAX,
  LANE,
  LANE_ROWS,
  axisMax,
  bandPaths,
  bars,
  bedtimeDots,
  bedtimeOffset,
  bedtimeTicks,
  linePaths,
  sleepAxis,
  sleepAxisTicks,
  sleepLaneBars,
  sleepLaneRows,
  stackBar,
  timelineSegments,
  xTicks,
  yAt,
} from './healthChart'

// 健康页图表几何守门:坐标算错不会报错,只会画出错位/糊成一片的图,所以纯函数全部钉住。

describe('healthChart 坐标系', () => {
  it('axisMax 取 1/2/5 整刻度,至少装得下最大值', () => {
    expect(axisMax([0], 1)).toBe(1)
    expect(axisMax([7])).toBe(10)
    expect(axisMax([23])).toBe(50)
    expect(axisMax([8123])).toBe(10000)
    expect(axisMax([])).toBe(1)
  })

  it('yAt 把 0 压到底边、max 抬到上边,超界锁边', () => {
    const bottom = 150 - 24
    expect(yAt(0, 100)).toBe(bottom)
    expect(yAt(100, 100)).toBe(12)
    expect(yAt(-5, 100)).toBe(bottom)
    expect(yAt(200, 100)).toBe(12)
  })

  it('xTicks 恒含首末,标签过多时只采样', () => {
    const labels = Array.from({ length: 14 }, (_, i) => `d${i}`)
    const ticks = xTicks(labels, 7)
    expect(ticks.length).toBeLessThanOrEqual(8)
    expect(ticks[0].label).toBe('d0')
    expect(ticks[ticks.length - 1].label).toBe('d13')
    expect(xTicks([], 7)).toEqual([])
  })

  it('bars 跳过 null(缺数据不画,不伪造 0)', () => {
    const list = bars([10, null, 30], 40)
    expect(list.map((b) => b.i)).toEqual([0, 2])
    expect(list.every((b) => b.h >= 0)).toBe(true)
  })

  it('linePaths 在缺数据处断开,不连假直线', () => {
    expect(linePaths([1, 2, null, 3, 4], 10)).toHaveLength(2)
    expect(linePaths([1, null, 3], 10)).toEqual([])
    expect(linePaths([5], 10)).toEqual([])
  })

  it('bandPaths 只在 min/max 都有的连续点成段', () => {
    const paths = bandPaths(
      [
        { min: 60, max: 90 },
        { min: 62, max: 95 },
        { min: null, max: 70 },
        { min: 65, max: 88 },
      ],
      120,
    )
    expect(paths).toHaveLength(1)
    expect(paths[0].startsWith('M ')).toBe(true)
    expect(paths[0].endsWith(' Z')).toBe(true)
  })
})

describe('入睡时刻散点', () => {
  it('晚上算当晚、凌晨算次日凌晨,都落在 18:00 起算的分钟上', () => {
    expect(bedtimeOffset('2026-11-17T23:30:00')).toBe(330)
    expect(bedtimeOffset('2026-11-18T18:00:00')).toBe(0)
    expect(bedtimeOffset('2026-11-18T01:10:00')).toBe(430)
  })

  it('白天小睡不进散点,窗口两端对得上,解析失败返回 null', () => {
    expect(bedtimeOffset('2026-11-18T12:00:00')).toBeNull()
    expect(bedtimeOffset('2026-11-18T06:00:00')).toBe(BED_MAX)
    expect(bedtimeOffset('2026-11-18T00:00:00')).toBe(360)
    expect(bedtimeOffset('')).toBeNull()
    expect(bedtimeOffset(undefined)).toBeNull()
  })

  it('纵轴刻度从 18:00 走到 06:00,每 2 小时一格', () => {
    const ticks = bedtimeTicks()
    expect(ticks[0]).toEqual({ v: 0, label: '18:00' })
    expect(ticks[ticks.length - 1]).toEqual({ v: BED_MAX, label: '06:00' })
    expect(ticks.map((t) => t.label)).toContain('00:00')
  })

  it('bedtimeDots 丢掉没有入睡时间的夜', () => {
    const dots = bedtimeDots([{ sleepDate: '2026-11-16', startTime: '2026-11-16T23:00:00' }, { sleepDate: '2026-11-17' }])
    expect(dots).toHaveLength(1)
    expect(dots[0].label).toBe('2026-11-16')
  })
})

describe('睡眠堆叠与时间轴', () => {
  it('stackBar 按分钟折算百分比,和为 100', () => {
    const segs = stackBar([
      { code: 'DEEP', minutes: 100 },
      { code: 'LIGHT', minutes: 200 },
      { code: 'REM', minutes: 0 },
    ])
    expect(segs.map((s) => s.code)).toEqual(['DEEP', 'LIGHT'])
    expect(segs.reduce((a, s) => a + s.pct, 0)).toBeCloseTo(100, 6)
    expect(stackBar([{ code: 'DEEP', minutes: 0 }])).toEqual([])
  })

  it('timelineSegments 按真实起止铺 0~100,缺口不补', () => {
    const segs = timelineSegments(
      [
        { stage: 'LIGHT', startTime: '2026-11-17T23:00:00', endTime: '2026-11-17T23:40:00', durationMin: 40 },
        { stage: 'DEEP', startTime: '2026-11-18T00:20:00', endTime: '2026-11-18T01:00:00', durationMin: 40 },
      ],
      '2026-11-17T23:00:00',
      '2026-11-18T01:00:00',
    )
    expect(segs).toHaveLength(2)
    expect(segs[0].left).toBe(0)
    expect(segs[0].width).toBeCloseTo(100 / 3, 4)
    expect(segs[1].left).toBeCloseTo(200 / 3, 4)
    expect(segs[1].left + segs[1].width).toBeCloseTo(100, 6)
  })

  it('timelineSegments 没有分段时返回空数组(页面显示「没有分期」)', () => {
    expect(timelineSegments([], '2026-11-17T23:00:00', '2026-11-18T07:00:00')).toEqual([])
    expect(timelineSegments(null)).toEqual([])
  })
})

// 夜视图进度轴:横轴是钟点(入睡前后各留半小时再扩到整点),纵轴是四行阶段
describe('睡眠进度轴几何', () => {
  const hms = (ms) => {
    const d = new Date(ms)
    return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  }

  it('两端各留半小时后再向外扩到整点', () => {
    const axis = sleepAxis('2026-11-17T22:35:00', '2026-11-18T07:05:00')
    expect(hms(axis.from)).toBe('22:00')
    expect(hms(axis.to)).toBe('08:00')
    expect(axis.span).toBe(10 * 60 * 60 * 1000)
    // 22:30 入睡减去半小时正好是整点:不多扩一格
    const tight = sleepAxis('2026-11-17T22:30:00', '2026-11-18T07:00:00')
    expect(hms(tight.from)).toBe('22:00')
    expect(hms(tight.to)).toBe('08:00')
  })

  it('没传起止时用分期区间兜底,数据不可用返回 null', () => {
    const axis = sleepAxis(null, null, [
      { stage: 'LIGHT', startTime: '2026-11-17T22:40:00', endTime: '2026-11-17T23:00:00' },
      { stage: 'DEEP', startTime: '2026-11-18T06:00:00', endTime: '2026-11-18T06:20:00' },
    ])
    expect(hms(axis.from)).toBe('22:00')
    expect(hms(axis.to)).toBe('07:00')
    expect(sleepAxis(null, null, [])).toBeNull()
    expect(sleepAxis('2026-11-18T07:00:00', '2026-11-17T23:00:00')).toBeNull()
  })

  it('整点刻度:常规跨度 1 小时一格、超 12 小时 2 小时一格', () => {
    const ticks = sleepAxisTicks(sleepAxis('2026-11-17T22:35:00', '2026-11-18T07:05:00'))
    expect(ticks[0].label).toBe('22:00')
    expect(ticks[ticks.length - 1].label).toBe('08:00')
    expect(ticks).toHaveLength(11)
    const wide = sleepAxisTicks(sleepAxis('2026-11-17T15:00:00', '2026-11-18T09:00:00'))
    expect(wide.map((t) => t.label)).toEqual([
      '14:00',
      '16:00',
      '18:00',
      '20:00',
      '22:00',
      '00:00',
      '02:00',
      '04:00',
      '06:00',
      '08:00',
      '10:00',
    ])
    expect(ticks[0].x).toBeCloseTo(LANE.padL, 6)
  })

  it('四行自下而上是深睡→核心→快速眼动→清醒', () => {
    const rows = sleepLaneRows()
    expect(rows.map((r) => r.code)).toEqual(['AWAKE', 'REM', 'LIGHT', 'DEEP'])
    expect(rows[0].y).toBe(LANE.padT)
    expect(rows[3].y).toBeGreaterThan(rows[0].y)
  })

  it('分期区间按钟点铺进对应行,在床/未分期不占行', () => {
    const axis = sleepAxis('2026-11-17T23:00:00', '2026-11-18T07:00:00')
    const bars = sleepLaneBars(
      [
        { stage: 'DEEP', startTime: '2026-11-17T23:00:00', endTime: '2026-11-18T00:00:00' },
        { stage: 'INBED', startTime: '2026-11-18T00:00:00', endTime: '2026-11-18T06:00:00' },
        { stage: 'AWAKE', startTime: '2026-11-18T06:00:00', endTime: '2026-11-18T07:00:00' },
        { stage: 'LIGHT', startTime: '2026-11-18T07:00:00', endTime: '2026-11-18T07:00:00' },
      ],
      axis,
    )
    expect(bars.map((b) => b.code)).toEqual(['DEEP', 'AWAKE'])
    expect(bars[0].y).toBe(LANE.padT + LANE_ROWS.indexOf('DEEP') * (LANE.rowH + LANE.rowGap))
    // 23:00 入睡 → 轴从 22:00 起共 10 小时,深睡条距左内边 1/10
    const inner = LANE.w - LANE.padL - LANE.padR
    expect(bars[0].x - LANE.padL).toBeCloseTo(inner / 10, 6)
    expect(bars[0].w).toBeCloseTo(inner / 10, 6)
  })

  it('越界区间被轴两端裁掉,没有轴时不画', () => {
    const axis = sleepAxis('2026-11-17T23:00:00', '2026-11-18T07:00:00')
    const bars = sleepLaneBars([{ stage: 'LIGHT', startTime: '2026-11-17T21:00:00', endTime: '2026-11-18T09:00:00' }], axis)
    expect(bars[0].x).toBeCloseTo(LANE.padL, 6)
    expect(bars[0].x + bars[0].w).toBeCloseTo(LANE.w - LANE.padR, 6)
    expect(sleepLaneBars([], null)).toEqual([])
  })
})
