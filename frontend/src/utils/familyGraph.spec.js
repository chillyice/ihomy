import { describe, it, expect } from 'vitest'
import { buildFamilyGraph, normalizeFamily } from './familyGraph'
// 采样每条关系线,确认不穿过任何非端点的人物卡片矩形
function edgeCrossings(graph) {
  const rects = [...graph.nodes.values()].map((n) => ({
    id: n.id,
    unitId: n.unitId,
    x: n.x,
    y: n.y,
    w: n.width,
    h: n.height,
  }))
  const issues = []
  const steps = 64
  for (const e of graph.edges) {
    const d = e.path || `M${e.x1},${e.y1} L${e.x2},${e.y2}`
    const pts = []
    const re = /([MLQC])([-d.]+),([-d.]+)/g
    let m
    while ((m = re.exec(d))) pts.push([parseFloat(m[2]), parseFloat(m[3])])
    const endpoints = new Set([...(e.memberIds || []), ...(e.parentIds || [])])
    for (const r of rects) {
      if (endpoints.has(r.id)) continue
      let hit = false
      for (let i = 1; i < pts.length && !hit; i++) {
        const [ax, ay] = pts[i - 1]
        const [bx, by] = pts[i]
        for (let s = 0; s <= steps; s++) {
          const t = s / steps
          const px = ax + (bx - ax) * t
          const py = ay + (by - ay) * t
          if (px > r.x + 1 && px < r.x + r.w - 1 && py > r.y + 1 && py < r.y + r.h - 1) {
            issues.push(`${e.key}→${r.id}`)
            hit = true
            break
          }
        }
      }
    }
  }
  return issues
}

describe('normalizeFamily', () => {
  it('配偶只写一侧也按双向处理', () => {
    const g = normalizeFamily([
      { id: 1, gender: 1, spouseId: 2 },
      { id: 2, gender: 2 },
    ])
    expect(g.spouseOf.get(1).has(2)).toBe(true)
    expect(g.spouseOf.get(2).has(1)).toBe(true)
  })

  it('指向不存在成员的父/母/配偶计入 orphanLinks', () => {
    const g = normalizeFamily([{ id: 1, fatherId: 99, spouseId: 98 }])
    expect(g.orphanLinks).toBe(2)
    expect(g.parentsOf.get(1)).toEqual([])
  })
})

describe('buildFamilyGraph', () => {
  it('忽略 generation:夫妻永远同一行并连上配偶线', () => {
    const graph = buildFamilyGraph([
      { id: 1, name: '胡', gender: 1, spouseId: 2, generation: 3 },
      { id: 2, name: '高', gender: 2, generation: 0 },
    ])
    const hu = graph.nodes.get(1)
    const gao = graph.nodes.get(2)
    expect(hu.y).toBe(gao.y)
    expect(graph.edges.filter((e) => e.type === 'spouse')).toHaveLength(1)
    expect(graph.stats.spousePairs).toBe(1)
  })

  it('孩子挂在父母单元下方,只有一条主亲子线', () => {
    const graph = buildFamilyGraph([
      { id: 1, name: '胡', gender: 1, spouseId: 2, generation: 5 },
      { id: 2, name: '高', gender: 2, generation: 1 },
      { id: 3, name: '小明', gender: 1, fatherId: 1, motherId: 2, generation: 9 },
    ])
    expect(graph.nodes.get(3).y).toBeGreaterThan(graph.nodes.get(1).y)
    const parentEdges = graph.edges.filter((e) => e.type === 'parent')
    expect(parentEdges).toHaveLength(1)
    expect(parentEdges[0].primary).toBe(true)
    expect(parentEdges[0].memberId).toBe(3)
  })

  it('父母不在同一单元时,第二个父/母画成补充虚线', () => {
    const graph = buildFamilyGraph([
      { id: 1, name: '父', gender: 1 },
      { id: 2, name: '母', gender: 2 },
      { id: 3, name: '子', gender: 1, fatherId: 1, motherId: 2 },
    ])
    const parentEdges = graph.edges.filter((e) => e.type === 'parent')
    expect(parentEdges).toHaveLength(2)
    expect(parentEdges.filter((e) => e.primary)).toHaveLength(1)
    expect(parentEdges.filter((e) => !e.primary)).toHaveLength(1)
  })

  it('父子关系成环也能布局,不会死循环', () => {
    const graph = buildFamilyGraph([
      { id: 1, gender: 1, fatherId: 2 },
      { id: 2, gender: 1, fatherId: 1 },
    ])
    expect(graph.nodes.size).toBe(2)
    expect(graph.height).toBeGreaterThan(0)
  })

  it('空数据返回空图', () => {
    const graph = buildFamilyGraph([])
    expect(graph.nodes.size).toBe(0)
    expect(graph.width).toBe(0)
  })

  it('补充父母跨代时连线不穿过中间人物节点', () => {
    const graph = buildFamilyGraph([
      { id: 1, name: '父甲', gender: 1 },
      { id: 2, name: '继母', gender: 2 },
      { id: 3, name: '子丙', gender: 1, fatherId: 1 },
      { id: 7, name: '孙庚', gender: 1, fatherId: 3, motherId: 2 },
    ])
    expect(edgeCrossings(graph)).toEqual([])
  })

  it('多家庭交叉引用时所有关系线都不穿过人物卡片', () => {
    const graph = buildFamilyGraph([
      { id: 1, name: '父甲', gender: 1 },
      { id: 2, name: '母乙', gender: 2 },
      { id: 3, name: '子丙', gender: 1, fatherId: 1, motherId: 2 },
      { id: 4, name: '父丁', gender: 1 },
      { id: 5, name: '母戊', gender: 2 },
      { id: 6, name: '女己', gender: 2, fatherId: 4, motherId: 5 },
      { id: 7, name: '孙庚', gender: 1, fatherId: 3, motherId: 6 },
      { id: 8, name: '孙辛', gender: 2, fatherId: 3, motherId: 6 },
    ])
    expect(edgeCrossings(graph)).toEqual([])
  })
})
