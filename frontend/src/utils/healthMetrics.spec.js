import { describe, expect, it } from 'vitest'
import {
  HEALTH_METRICS,
  SLEEP_STAGES,
  TAB_METRICS,
  clockText,
  dayLabel,
  formatMetric,
  metricName,
  metricUnit,
  periodOf,
  sleepHM,
  sleepText,
  stageMeta,
  withUnit,
} from './healthMetrics'

// 指标目录守门:首页卡与 /health 页共用这里的小数位/单位口径,改错会两处一起错,所以钉住。

// 假 t:按 key 取嵌套数值即可,不引 i18n 实例
const t = (key, params) => (params ? `${key}:${Object.values(params).join('/')}` : key)

describe('healthMetrics 目录', () => {
  it('每个指标都有分组、周期、小数位与文案键', () => {
    for (const [code, def] of Object.entries(HEALTH_METRICS)) {
      expect(def.group, code).toMatch(/^(SLEEP|ACTIVITY|VITAL|BODY)$/)
      expect(def.period, code).toMatch(/^(DAY|POINT|SESSION)$/)
      expect(Number.isInteger(def.digits), code).toBe(true)
      expect(def.nameKey, code).toBe(`health.metrics.${code.toLowerCase().replace(/_([a-z])/g, (m, c) => c.toUpperCase())}`)
    }
  })

  it('tab 指标条只引用目录里存在的码,且同一指标不重复出现在两个 tab', () => {
    const seen = new Set()
    for (const [tab, codes] of Object.entries(TAB_METRICS)) {
      expect(codes.length, tab).toBeGreaterThan(0)
      for (const code of codes) {
        expect(HEALTH_METRICS[code], `${tab}/${code}`).toBeTruthy()
        expect(seen.has(code), `${code} 重复`).toBe(false)
        seen.add(code)
      }
    }
  })

  it('读值口径:缺数据给 —,小数位按目录', () => {
    expect(formatMetric('STEPS', null)).toBe('—')
    expect(formatMetric('STEPS', '')).toBe('—')
    expect(formatMetric('STEPS', 'abc')).toBe('—')
    expect(formatMetric('STEPS', 8123)).toBe('8123')
    expect(formatMetric('WEIGHT', 62.44)).toBe('62.4')
    expect(formatMetric('BASAL_TEMP', 36.567)).toBe('36.57')
    expect(withUnit('WEIGHT', '62.4')).toBe('62.4 kg')
    expect(withUnit('STEPS', '8123')).toBe('8123')
    expect(withUnit('STEPS', '—')).toBe('—')
    expect(metricUnit('UNKNOWN_CODE')).toBe('')
  })

  it('未知指标不炸,回显原码', () => {
    expect(metricName(t, 'NOPE')).toBe('NOPE')
    expect(metricName(t, 'WEIGHT')).toBe('health.metrics.weight')
    expect(periodOf('NOPE')).toBe('DAY')
  })
})

describe('睡眠文案', () => {
  it('sleepHM 四舍五入到分钟,不出 60 分', () => {
    expect(sleepHM(450)).toEqual({ h: 7, m: 30 })
    expect(sleepHM(60)).toEqual({ h: 1, m: 0 })
    expect(sleepHM(59.6)).toEqual({ h: 1, m: 0 })
    expect(sleepHM(null)).toBeNull()
  })

  it('sleepText 不足 1 小时只写分钟,整点不带 0 分', () => {
    expect(sleepText(t, 45)).toBe('health.m:45')
    expect(sleepText(t, 420)).toBe('health.hm:7/0')
    expect(sleepText(t, 450)).toBe('health.hm:7/30')
    expect(sleepText(t, null)).toBe('—')
  })

  it('分期颜色与文案键齐全,未知分期兜底不返回 null', () => {
    const codes = SLEEP_STAGES.map((s) => s.code)
    expect(codes).toEqual(['AWAKE', 'REM', 'LIGHT', 'DEEP', 'INBED', 'UNKNOWN'])
    expect(stageMeta('deep').labelKey).toBe('health.stage.deep')
    expect(stageMeta('WHATEVER')).toBeNull()
    for (const s of SLEEP_STAGES) expect(s.color.startsWith('var(--health-stage-')).toBe(true)
  })
})

describe('时间与日期显示', () => {
  it('clockText 只取字符串里的时钟,不换算时区', () => {
    expect(clockText('2026-11-17T23:41:00')).toBe('23:41')
    expect(clockText('2026-11-17T07:05')).toBe('07:05')
    expect(clockText('')).toBe('—')
  })

  it('dayLabel 出 M/D,非日期原样返回', () => {
    expect(dayLabel('2026-11-08')).toBe('11/8')
    expect(dayLabel('')).toBe('')
    expect(dayLabel('上周')).toBe('上周')
  })
})
