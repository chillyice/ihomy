import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { readDailyPrefs } from './dailyPrefs'

// node 环境无 localStorage:readDailyPrefs 内 try/catch 兜底为缺省值;显式用例注入桩
describe('readDailyPrefs', () => {
  beforeEach(() => {
    delete globalThis.localStorage
  })
  afterEach(() => {
    delete globalThis.localStorage
  })

  it('无存储:两开关默认开,分类默认历史+生活', () => {
    expect(readDailyPrefs()).toEqual({ imageOn: true, knowledgeOn: true, types: ['history', 'life'] })
  })

  it('非法 JSON 回落缺省值', () => {
    globalThis.localStorage = { getItem: () => '{not json' }
    expect(readDailyPrefs().types).toEqual(['history', 'life'])
  })

  it('空分类视为未选,回落缺省分类', () => {
    globalThis.localStorage = { getItem: () => JSON.stringify({ imageOn: false, knowledgeOn: false, types: [] }) }
    expect(readDailyPrefs()).toEqual({ imageOn: false, knowledgeOn: false, types: ['history', 'life'] })
  })

  it('显式偏好按原值返回', () => {
    globalThis.localStorage = { getItem: () => JSON.stringify({ imageOn: false, knowledgeOn: true, types: ['science'] }) }
    expect(readDailyPrefs()).toEqual({ imageOn: false, knowledgeOn: true, types: ['science'] })
  })
})
