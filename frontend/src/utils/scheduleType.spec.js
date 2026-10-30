import { describe, it, expect } from 'vitest'
import { classify, matchFilter, typeOfKind, TYPE_FILTERS, REPEAT, REMIND } from './scheduleType'

// 计划 §3 的七行判定表,逐行锁定
describe('classify 七行判定', () => {
  it('无日期 → 计划(PLAN)', () => {
    expect(classify({ hasDate: false, repeat: 'NONE', remind: 'NONE' })).toBe('PLAN')
  })

  it('有日期 + 不循环 + 无提醒 → 事项(ITEM)', () => {
    expect(classify({ hasDate: true, repeat: 'NONE', remind: 'NONE' })).toBe('ITEM')
  })

  it('有日期 + 不循环 + 当天提醒 → 提醒(REMINDER)', () => {
    expect(classify({ hasDate: true, repeat: 'NONE', remind: 'ON_TIME' })).toBe('REMINDER')
  })

  it('有日期 + 每年循环 + 无提醒 → 纪念日(ANNIVERSARY)', () => {
    expect(classify({ hasDate: true, repeat: REPEAT.YEARLY, remind: REMIND.NONE })).toBe('ANNIVERSARY')
  })

  it('有日期 + 每年循环 + 提前提醒 → 纪念日(ANNIVERSARY)', () => {
    expect(classify({ hasDate: true, repeat: REPEAT.YEARLY, remind: REMIND.ADVANCE })).toBe('ANNIVERSARY')
  })

  it('有日期 + 每年循环 + 当天提醒 → 每日任务(TASK)', () => {
    expect(classify({ hasDate: true, repeat: REPEAT.YEARLY, remind: REMIND.ON_TIME })).toBe('TASK')
  })

  it('有日期 + 每天循环 + 当天提醒 → 每日任务(TASK)', () => {
    expect(classify({ hasDate: true, repeat: REPEAT.DAILY, remind: REMIND.ON_TIME })).toBe('TASK')
  })
})

describe('classify 默认值', () => {
  it('不传参数按无日期计划处理', () => {
    expect(classify()).toBe('PLAN')
    expect(classify({})).toBe('PLAN')
  })

  it('循环但没给提醒方式按纪念日处理', () => {
    expect(classify({ hasDate: true, repeat: 'WEEKLY' })).toBe('ANNIVERSARY')
  })
})

describe('后端 kind → 用户类型', () => {
  it('PLAN 归事项,PLAN_TASK 归计划,未知兜底事项', () => {
    expect(typeOfKind('PLAN')).toBe('ITEM')
    expect(typeOfKind('PLAN_TASK')).toBe('PLAN')
    expect(typeOfKind('ANNIVERSARY')).toBe('ANNIVERSARY')
    expect(typeOfKind('REMINDER')).toBe('REMINDER')
    expect(typeOfKind('TASK')).toBe('TASK')
    expect(typeOfKind('WHATEVER')).toBe('ITEM')
  })

  it('筛选 ALL 全通过', () => {
    for (const kind of ['PLAN', 'PLAN_TASK', 'REMINDER', 'TASK', 'ANNIVERSARY']) {
      expect(matchFilter(kind, 'ALL')).toBe(true)
    }
  })

  it('按类型筛选只命中对应 kind', () => {
    expect(matchFilter('PLAN', 'ITEM')).toBe(true)
    expect(matchFilter('PLAN', 'PLAN')).toBe(false)
    expect(matchFilter('PLAN_TASK', 'PLAN')).toBe(true)
    expect(matchFilter('REMINDER', 'REMINDER')).toBe(true)
    expect(matchFilter('TASK', 'REMINDER')).toBe(false)
  })

  it('筛选顺序覆盖五种类型 + 全部', () => {
    expect(TYPE_FILTERS).toEqual(['ALL', 'ANNIVERSARY', 'REMINDER', 'TASK', 'ITEM', 'PLAN'])
  })
})
