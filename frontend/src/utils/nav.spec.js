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
    expect(system.items.map((i) => i.code)).toEqual(['settings', 'help', 'chillyice'])
  })
})

// chillyice 个人主页:独立页不入 sys_home_module,与设置/帮助一样是虚拟入口(V10.71 两个主题侧栏都加了入口)
describe('buildNavItems chillyice 入口', () => {
  it('没有 DB 行也有 chillyice,指向 /chillyice 且落在「系统」组', () => {
    const items = buildNavItems([])
    const item = items.find((i) => i.code === 'chillyice')
    expect(item).toBeTruthy()
    expect(item.path).toBe('/chillyice')
    expect(item.category).toBe('system')
    const system = buildNavGroups([]).find((g) => g.category === 'system')
    expect(system.items.map((i) => i.code)).toContain('chillyice')
  })

  it('排在帮助之后、运维之前(系统组内顺序)', () => {
    const codes = buildNavItems([], { hasOps: true }).map((i) => i.code)
    expect(codes.indexOf('help')).toBeLessThan(codes.indexOf('chillyice'))
    expect(codes.indexOf('chillyice')).toBeLessThan(codes.indexOf('ops'))
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

  it('不破「工具箱殿后」的排序口径(工具箱仍在生活组末位)', () => {
    const life = buildNavGroups([
      { code: 'vault', title: '保险箱', path: '/vault', category: 'life', sortOrder: 14, enabled: 1 },
      { code: 'chat', title: '聊天室', path: '/chat', category: 'life', sortOrder: 16, enabled: 1 },
      { code: 'tools', title: '工具箱', path: '/tools', category: 'life', sortOrder: 17, enabled: 1 },
    ]).find((g) => g.category === 'life')
    expect(life.items.map((i) => i.code)).toEqual(['vault', 'weather', 'chat', 'tools'])
  })
})
