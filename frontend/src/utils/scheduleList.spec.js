import { describe, it, expect } from 'vitest'
import { buildList, flattenEvents, sortEvents, dayTypes } from './scheduleList'

const days = {
  '2026-10-31': [
    { kind: 'PLAN', id: 3, title: '换滤芯', time: null },
    { kind: 'REMINDER', id: 9, title: '交物业费', time: '09:00' },
  ],
  '2026-10-30': [
    { kind: 'TASK', id: 12, title: '吃药', time: '07:30' },
    { kind: 'ANNIVERSARY', id: 3, title: '妈妈生日', time: null },
    { kind: 'PLAN_TASK', id: 21, planId: 8, title: '买滤芯', time: null },
    { kind: 'TASK', id: 5, title: '浇花', time: '07:30' },
  ],
}

describe('sortEvents 排序', () => {
  it('时刻升序,全天排最后', () => {
    // 07:30 同一时刻按 id 升序(5 浇花 → 12 吃药),全天项按类型排最后
    const rows = sortEvents(days['2026-10-30'])
    expect(rows.map((r) => r.title)).toEqual(['浇花', '吃药', '妈妈生日', '买滤芯'])
  })

  it('同一时刻按类型顺序:纪念日 → 提醒 → 任务 → 事项 → 子任务', () => {
    const rows = sortEvents([
      { kind: 'PLAN_TASK', id: 1, time: null },
      { kind: 'PLAN', id: 2, time: null },
      { kind: 'ANNIVERSARY', id: 3, time: null },
      { kind: 'TASK', id: 4, time: null },
      { kind: 'REMINDER', id: 5, time: null },
    ])
    expect(rows.map((r) => r.kind)).toEqual(['ANNIVERSARY', 'REMINDER', 'TASK', 'PLAN', 'PLAN_TASK'])
  })

  it('同类型按 id 升序,且不修改入参', () => {
    const input = [
      { kind: 'TASK', id: 7, time: null },
      { kind: 'TASK', id: 3, time: null },
    ]
    expect(sortEvents(input).map((r) => r.id)).toEqual([3, 7])
    expect(input.map((r) => r.id)).toEqual([7, 3])
  })
})

describe('flattenEvents 摊平', () => {
  it('日期升序并补齐 date 字段', () => {
    const rows = flattenEvents(days)
    expect(rows[0].date).toBe('2026-10-30')
    expect(rows[rows.length - 1].date).toBe('2026-10-31')
    expect(rows.every((r) => r.date)).toBe(true)
  })

  it('空数据返回空数组', () => {
    expect(flattenEvents()).toEqual([])
    expect(flattenEvents({})).toEqual([])
  })
})

describe('buildList 分组与筛选', () => {
  it('按日期分组,组内已排序', () => {
    const groups = buildList(days)
    expect(groups.map((g) => g.date)).toEqual(['2026-10-30', '2026-10-31'])
    expect(groups[0].items.map((r) => r.title)).toEqual(['浇花', '吃药', '妈妈生日', '买滤芯'])
  })

  it('筛选后没有事项的日期不占位', () => {
    const groups = buildList(days, 'ANNIVERSARY')
    expect(groups).toHaveLength(1)
    expect(groups[0].date).toBe('2026-10-30')
    expect(groups[0].items).toHaveLength(1)
  })

  it('事项筛选命中 PLAN,计划筛选命中 PLAN_TASK', () => {
    expect(buildList(days, 'ITEM')[0].items.map((r) => r.title)).toEqual(['换滤芯'])
    expect(buildList(days, 'PLAN')[0].items.map((r) => r.title)).toEqual(['买滤芯'])
  })

  it('每日任务筛选只留循环提醒', () => {
    const groups = buildList(days, 'TASK')
    expect(groups).toHaveLength(1)
    expect(groups[0].items.map((r) => r.title)).toEqual(['浇花', '吃药'])
  })

  it('全部筛选保留所有分组', () => {
    expect(buildList(days, 'ALL')).toHaveLength(2)
  })
})

describe('dayTypes', () => {
  it('去重保序', () => {
    expect(dayTypes(days['2026-10-30'])).toEqual(['TASK', 'ANNIVERSARY', 'PLAN'])
    expect(dayTypes([])).toEqual([])
  })
})
