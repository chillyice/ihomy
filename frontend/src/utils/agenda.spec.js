import { describe, it, expect } from 'vitest'
import { buildAgenda } from './agenda'

const today = {
  plans: [
    { id: 2, title: '换季大扫除', remindTime: '09:30:00', overdue: false, overdueDays: 0, doneCount: 1, totalCount: 3 },
    { id: 1, title: '交水电费', remindTime: null, overdue: true, overdueDays: 2, doneCount: 0, totalCount: 0 },
  ],
  reminders: [
    { id: 9, title: '吃药', remindTime: '08:00:00', fromPlan: false },
    { id: 8, title: '接孩子', remindTime: '09:30:00', fromPlan: true },
  ],
}

describe('buildAgenda 今日列表合并', () => {
  it('按提醒时刻升序,无时刻的排最后', () => {
    expect(buildAgenda(today).map((r) => r.title)).toEqual(['吃药', '换季大扫除', '接孩子', '交水电费'])
  })

  it('同一时刻计划在前', () => {
    const rows = buildAgenda(today)
    expect(rows.findIndex((r) => r.title === '换季大扫除')).toBeLessThan(rows.findIndex((r) => r.title === '接孩子'))
  })

  it('保留逾期天数与子任务进度,时刻截到分钟', () => {
    const rows = buildAgenda(today)
    const plan = rows.find((r) => r.title === '换季大扫除')
    expect(plan).toMatchObject({ kind: 'plan', time: '09:30', doneCount: 1, totalCount: 3, overdueDays: 0 })
    const overdue = rows.find((r) => r.title === '交水电费')
    expect(overdue).toMatchObject({ overdueDays: 2, time: '' })
  })

  it('提醒标记是否来自计划', () => {
    const rows = buildAgenda(today)
    expect(rows.find((r) => r.title === '接孩子').fromPlan).toBe(true)
    expect(rows.find((r) => r.title === '吃药').fromPlan).toBe(false)
  })

  it('并入无日期待办,排在有时间的条目之后', () => {
    const rows = buildAgenda({
      ...today,
      undated: [
        { kind: 'PLAN', id: 3, title: '整理书架', done: false },
        { kind: 'PLAN_TASK', id: 4, planId: 3, planTitle: '整理书架', title: '买标签', done: false },
      ],
    })
    expect(rows.map((r) => r.title)).toEqual(['吃药', '换季大扫除', '接孩子', '交水电费', '整理书架', '买标签'])
    expect(rows.find((r) => r.title === '整理书架')).toMatchObject({ undated: true, sourceKind: 'PLAN', kind: 'plan' })
    expect(rows.find((r) => r.title === '买标签')).toMatchObject({
      undated: true,
      sourceKind: 'PLAN_TASK',
      kind: 'task',
      planId: 3,
    })
  })

  it('limit 截断;空数据返回空数组', () => {
    expect(buildAgenda(today, 2)).toHaveLength(2)
    expect(buildAgenda()).toEqual([])
    expect(buildAgenda({ plans: null, reminders: null })).toEqual([])
  })
})
