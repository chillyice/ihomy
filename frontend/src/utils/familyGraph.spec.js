import { describe, it, expect } from 'vitest'
import { buildFamilyGraph, normalizeFamily } from './familyGraph'

// 生产 family 2 的真实家谱快照(20 人):含姻亲父母、兄弟、跨房祖辈
const FAMILY = [
  { id: 8, name: '高尚', gender: 1, spouseId: 9, fatherId: 10, motherId: 11 },
  { id: 9, name: '胡晓瑞', gender: 2, spouseId: 8, fatherId: 12, motherId: 13 },
  { id: 10, name: '高忠林', gender: 1, spouseId: 11 },
  { id: 11, name: '李艳芳', gender: 2, spouseId: 10, fatherId: 14, motherId: 15 },
  { id: 12, name: '胡彦涛', gender: 1, spouseId: 13 },
  { id: 13, name: '王晓瑞妈', gender: 2, spouseId: 12 },
  { id: 14, name: '姥爷', gender: 0, spouseId: 15, motherId: 24 },
  { id: 15, name: '姥姥', gender: 2, spouseId: 14 },
  { id: 16, name: '大舅', gender: 1, spouseId: 21, fatherId: 14, motherId: 15 },
  { id: 17, name: '二舅', gender: 1, fatherId: 14, motherId: 15 },
  { id: 18, name: '三舅', gender: 1, fatherId: 14, motherId: 15 },
  { id: 19, name: '李露', gender: 1, fatherId: 16, motherId: 21 },
  { id: 20, name: '李阳', gender: 1, fatherId: 16, motherId: 21 },
  { id: 21, name: '大舅妈', gender: 2, spouseId: 16 },
  { id: 22, name: '二姥姥', gender: 2, spouseId: 23 },
  { id: 23, name: '二姥爷', gender: 1, spouseId: 22, motherId: 24 },
  { id: 24, name: '老姥姥', gender: 0, spouseId: 25 },
  { id: 25, name: '老姥爷', gender: 1, spouseId: 24 },
  { id: 26, name: '李梅', gender: 2, fatherId: 23, motherId: 22 },
  { id: 27, name: '梅好', gender: 2, motherId: 26 },
]

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

// 卡片两两不重叠
function overlaps(graph) {
  const list = [...graph.nodes.values()]
  const hits = []
  for (let i = 0; i < list.length; i += 1) {
    for (let j = i + 1; j < list.length; j += 1) {
      const a = list[i]
      const b = list[j]
      if (a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + b.height && b.y < a.y + a.height) {
        hits.push(`${a.id}×${b.id}`)
      }
    }
  }
  return hits
}

const unitOfMember = (graph, id) => graph.units.find((unit) => unit.memberIds.includes(id))
const centerOf = (graph, id) => graph.nodes.get(id).cx

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

  it('父母未登记夫妻时,两组父母并排同排各画一条实线', () => {
    const graph = buildFamilyGraph([
      { id: 1, name: '父', gender: 1 },
      { id: 2, name: '母', gender: 2 },
      { id: 3, name: '子', gender: 1, fatherId: 1, motherId: 2 },
    ])
    const parentEdges = graph.edges.filter((e) => e.type === 'parent')
    expect(parentEdges).toHaveLength(2)
    expect(parentEdges.filter((e) => e.primary)).toHaveLength(1)
    // 母亲不是主血亲挂点,但要和父亲排在同一行、紧挨着,不能自成一根
    expect(graph.nodes.get(1).y).toBe(graph.nodes.get(2).y)
    expect(graph.nodes.get(3).y).toBeGreaterThan(graph.nodes.get(1).y)
    expect(edgeCrossings(graph)).toEqual([])
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

  it('真实 20 人家谱:全部落位、卡片不重叠、连线不压卡片', () => {
    const graph = buildFamilyGraph(FAMILY)
    expect(graph.nodes.size).toBe(FAMILY.length)
    expect(overlaps(graph)).toEqual([])
    expect(edgeCrossings(graph)).toEqual([])
  })

  it('真实 20 人家谱:姻亲父母与本家父母同排、子女紧接下一排', () => {
    const graph = buildFamilyGraph(FAMILY)
    const husbandParents = unitOfMember(graph, 10) // 高忠林 & 李艳芳
    const wifeParents = unitOfMember(graph, 12) // 胡彦涛 & 王晓瑞妈
    const couple = unitOfMember(graph, 8) // 高尚 & 胡晓瑞
    expect(wifeParents.level).toBe(husbandParents.level)
    expect(couple.level).toBe(husbandParents.level + 1)
    // 两亲家横向相邻(中间只隔一个 siblingGap 量级的空隙)
    const gap = Math.min(
      Math.abs(wifeParents.x - (husbandParents.x + husbandParents.width)),
      Math.abs(husbandParents.x - (wifeParents.x + wifeParents.width)),
    )
    expect(gap).toBeLessThan(60)
    // 两条父母线都挂在夫妻各自那张卡片上,而不是笼统挂在单元中心
    const toHusband = graph.edges.find((e) => e.type === 'parent' && e.memberId === 8)
    const toWife = graph.edges.find((e) => e.type === 'parent' && e.memberId === 9)
    expect(toHusband.primary).toBe(true)
    expect(toWife.primary).toBe(false)
    expect(toHusband.x2).toBe(centerOf(graph, 8))
    expect(toWife.x2).toBe(centerOf(graph, 9))
  })

  it('真实 20 人家谱:兄弟姐妹成块排列,孙辈挂在自己父母下方', () => {
    const graph = buildFamilyGraph(FAMILY)
    // 大舅、二舅、三舅是亲兄弟,行内顺序必须连续
    const uncles = [16, 17, 18].map((id) => unitOfMember(graph, id))
    expect(new Set(uncles.map((u) => u.level)).size).toBe(1)
    const sorted = [...uncles].sort((a, b) => a.x - b.x)
    expect(sorted.map((u) => u.memberIds[0])).toEqual([16, 17, 18])
    // 梅好(27) 应落在李梅(26) 正下方,而不是被排到别家孩子旁边
    expect(graph.nodes.get(27).y).toBeGreaterThan(graph.nodes.get(26).y)
    expect(Math.abs(centerOf(graph, 27) - centerOf(graph, 26))).toBeLessThan(1)
  })

  it('非姻亲的跨代父母走画布外侧通道,仍不压卡片', () => {
    // E(6) 有两个孩子:D(5) 挂在 A 家很深的分支里,H(7) 挂在 E 自己下面
    const graph = buildFamilyGraph([
      { id: 1, name: '曾祖', gender: 1, spouseId: 2 },
      { id: 2, name: '曾祖母', gender: 2, spouseId: 1 },
      { id: 3, name: 'A', gender: 1, fatherId: 1, motherId: 2, spouseId: 4 },
      { id: 4, name: 'B', gender: 2, spouseId: 3 },
      { id: 5, name: 'D', gender: 1, fatherId: 3, motherId: 6 },
      { id: 6, name: 'E', gender: 2 },
      { id: 7, name: 'H', gender: 1, motherId: 6 },
    ])
    const spanEdge = graph.edges.find((e) => e.type === 'parent' && e.memberId === 5 && !e.primary)
    expect(spanEdge).toBeDefined()
    // 跨两排 → 走外侧通道,点串比普通三折线更长
    expect(spanEdge.path.split(/[MLQ]/).length).toBeGreaterThan(4)
    expect(edgeCrossings(graph)).toEqual([])
    expect(overlaps(graph)).toEqual([])
  })
})
