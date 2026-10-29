import { describe, it, expect } from 'vitest'
import { buildNavItems, buildNavGroups } from './navModules'

// 导航虚拟入口(设置/帮助/运维)不进 sys_home_module,靠 buildNavItems 无条件追加
describe('buildNavItems 虚拟入口', () => {
  it('没有 DB 行也有「设置」和「帮助」,且帮助只出现一次', () => {
    const items = buildNavItems([])
    const codes = items.map((i) => i.code)
    expect(codes).toContain('settings')
    expect(codes).toContain('help')
    expect(codes.filter((c) => c === 'help')).toHaveLength(1)
    expect(items.find((i) => i.code === 'help').path).toBe('/help')
  })

  it('运维管理仅在有运维权限时出现,且排在帮助之后', () => {
    expect(buildNavItems([]).map((i) => i.code)).not.toContain('ops')
    const codes = buildNavItems([], { hasOps: true }).map((i) => i.code)
    expect(codes).toContain('ops')
    expect(codes.indexOf('help')).toBeLessThan(codes.indexOf('ops'))
  })

  it('DB 模块与虚拟入口一起进「系统」组', () => {
    const groups = buildNavGroups([{ code: 'blog', title: '博客', path: '/blog', category: 'content', sortOrder: 1, enabled: 1 }])
    const system = groups.find((g) => g.category === 'system')
    expect(system.items.map((i) => i.code)).toEqual(['settings', 'help'])
  })
})

// 天气页不入 sys_home_module:桌面走侧栏/首页卡片,移动端无入口 = 页面存在却到不了(V10.26 修)
describe('buildNavItems 天气入口', () => {
  it('无 DB 行也有「天气」,且落在「生活」组', () => {
    const groups = buildNavGroups([])
    const life = groups.find((g) => g.category === 'life')
    expect(life.items.map((i) => i.code)).toContain('weather')
    expect(buildNavItems([]).find((i) => i.code === 'weather').path).toBe('/weather')
  })

  it('生活组内按 sortOrder 排,天气插在 vault 与 chat 之间', () => {
    const life = buildNavGroups([
      { code: 'vault', title: '保险箱', path: '/vault', category: 'life', sortOrder: 14, enabled: 1 },
      { code: 'chat', title: '聊天室', path: '/chat', category: 'life', sortOrder: 16, enabled: 1 },
    ]).find((g) => g.category === 'life')
    expect(life.items.map((i) => i.code)).toEqual(['vault', 'weather', 'chat'])
  })
})

// 「家庭」组(V10.85):物品定位/家庭公告/智能家居/工具箱 从生活组拆出,排在生活之后
describe('buildNavGroups 家庭组', () => {
  const modules = [
    { code: 'item', title: '物品定位', path: '/item', category: 'family', sortOrder: 4, enabled: 1 },
    { code: 'tools', title: '工具箱', path: '/tools', category: 'family', sortOrder: 17, enabled: 1 },
    { code: 'iot', title: '智能家居', path: '/iot', category: 'family', sortOrder: 16, enabled: 1 },
    { code: 'announcement', title: '家庭公告', path: '/announcement', category: 'family', sortOrder: 15, enabled: 1 },
    { code: 'reminder', title: '今日提醒', path: '/reminder', category: 'life', sortOrder: 6, enabled: 1 },
    { code: 'blog', title: '博客', path: '/blog', category: 'content', sortOrder: 1, enabled: 1 },
    { code: 'member', title: '家庭成员', path: '/member', category: 'social', sortOrder: 1, enabled: 1 },
  ]

  it('家庭组固定排在生活之后、成员之前,组内按 sortOrder', () => {
    const groups = buildNavGroups(modules)
    expect(groups.map((g) => g.category)).toEqual(['content', 'life', 'family', 'social', 'system'])
    const family = groups.find((g) => g.category === 'family')
    expect(family.label).toBe('家庭')
    expect(family.items.map((i) => i.code)).toEqual(['item', 'announcement', 'iot', 'tools'])
  })

  it('家庭组模块不会再出现在生活组', () => {
    const life = buildNavGroups(modules).find((g) => g.category === 'life')
    expect(life.items.map((i) => i.code)).toEqual(['reminder', 'weather'])
  })
})
