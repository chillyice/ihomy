// 家谱关系图:把扁平成员(父/母/配偶)归一化为夫妻单元,再按「父母在上、子女在下」排布。
// 关键点:布局完全不依赖 family_tree.generation —— 历史数据世代填错、夫妻世代不一致时,
// 也不会被拆到不同行;配偶双向补齐,只有一侧写了 spouse_id 也能连上。
// 输出纯数据(节点坐标/边路径),由组件负责渲染,便于单测。

const DEFAULT_OPTIONS = {
  cardWidth: 108,
  cardHeight: 104,
  spouseGap: 16,
  siblingGap: 30,
  levelGap: 92,
  rootGap: 68,
  padding: 28,
}

/** 关系归一化:只保留真实存在的人;配偶按双向处理;统计指向缺失成员的关联。 */
export function normalizeFamily(members) {
  const byId = new Map()
  for (const m of members || []) {
    if (m && m.id !== null && m.id !== undefined) byId.set(m.id, m)
  }

  const spouseOf = new Map()
  const parentsOf = new Map()
  const childrenOf = new Map()
  for (const id of byId.keys()) {
    spouseOf.set(id, new Set())
    parentsOf.set(id, [])
    childrenOf.set(id, [])
  }

  let orphanLinks = 0
  for (const m of byId.values()) {
    if (m.spouseId && m.spouseId !== m.id) {
      if (byId.has(m.spouseId)) {
        spouseOf.get(m.id).add(m.spouseId)
        spouseOf.get(m.spouseId).add(m.id)
      } else {
        orphanLinks += 1
      }
    }
    const seen = new Set()
    for (const [role, pid] of [
      ['father', m.fatherId],
      ['mother', m.motherId],
    ]) {
      if (!pid) continue
      if (pid === m.id || seen.has(pid)) continue
      if (!byId.has(pid)) {
        orphanLinks += 1
        continue
      }
      seen.add(pid)
      parentsOf.get(m.id).push({ id: pid, role })
      childrenOf.get(pid).push(m.id)
    }
  }

  return { byId, spouseOf, parentsOf, childrenOf, orphanLinks }
}

const genderRank = (m) => (m?.gender === 1 ? 0 : m?.gender === 2 ? 1 : 2)

/** 夫妻并为一个单元(一人最多一位配偶,仍用并集兼容脏数据里的多配偶)。 */
function buildUnits(normalized) {
  const parent = new Map()
  const find = (id) => {
    let root = id
    while (parent.get(root) !== root) root = parent.get(root)
    while (parent.get(id) !== id) {
      const next = parent.get(id)
      parent.set(id, root)
      id = next
    }
    return root
  }
  const union = (a, b) => {
    const ra = find(a)
    const rb = find(b)
    if (ra !== rb) parent.set(rb, ra)
  }

  for (const id of normalized.byId.keys()) parent.set(id, id)
  for (const [id, spouses] of normalized.spouseOf) {
    for (const sid of spouses) union(id, sid)
  }

  const groups = new Map()
  for (const id of normalized.byId.keys()) {
    const root = find(id)
    if (!groups.has(root)) groups.set(root, [])
    groups.get(root).push(id)
  }

  const units = new Map()
  const unitOfPerson = new Map()
  for (const [root, ids] of groups) {
    const memberIds = ids.slice().sort((a, b) => genderRank(normalized.byId.get(a)) - genderRank(normalized.byId.get(b)) || a - b)
    const unit = { id: `u${root}`, memberIds }
    units.set(unit.id, unit)
    for (const id of memberIds) unitOfPerson.set(id, unit.id)
  }
  return { units, unitOfPerson }
}

/** 每个夫妻单元挂到哪个父单元:优先男方父系,其次母系;其余父/母关系画成补充虚线。 */
function attachParents(normalized, units, unitOfPerson) {
  const parentUnitOf = new Map()
  const parentRefs = new Map()

  for (const unit of units.values()) {
    const groups = new Map()
    for (const memberId of unit.memberIds) {
      for (const p of normalized.parentsOf.get(memberId) || []) {
        const parentUnitId = unitOfPerson.get(p.id)
        if (!parentUnitId || parentUnitId === unit.id) continue
        if (!groups.has(parentUnitId)) groups.set(parentUnitId, new Map())
        const byMember = groups.get(parentUnitId)
        if (!byMember.has(memberId)) byMember.set(memberId, new Set())
        byMember.get(memberId).add(p.id)
      }
    }
    if (!groups.size) continue

    // 第一组(男方父系优先)作为主挂点,其余作为补充关系
    const primaryParent = groups.keys().next().value
    parentUnitOf.set(unit.id, primaryParent)
    parentRefs.set(unit.id, groups)
  }

  // 脏数据成环时断开:把环上第一个单元的挂点清空,让它成为根
  const state = new Map()
  for (const unitId of units.keys()) {
    if (state.get(unitId) === 2) continue
    const path = []
    const onPath = new Set()
    let cur = unitId
    while (cur && state.get(cur) !== 2) {
      if (onPath.has(cur)) {
        parentUnitOf.set(cur, null)
        break
      }
      onPath.add(cur)
      path.push(cur)
      cur = parentUnitOf.get(cur)
    }
    for (const id of path) state.set(id, 2)
  }

  const childUnits = new Map()
  for (const [unitId, parentId] of parentUnitOf) {
    if (!parentId) continue
    if (!childUnits.has(parentId)) childUnits.set(parentId, [])
    childUnits.get(parentId).push(unitId)
  }

  return { parentUnitOf, parentRefs, childUnits }
}

const fmt = (n) => Math.round(n * 10) / 10

/** 把折线点串成 SVG path,转角用二次贝塞尔圆角,避免生硬的直角。 */
function polylinePath(pts, radius = 8) {
  if (!pts || pts.length < 2) return ''
  let d = `M${fmt(pts[0][0])},${fmt(pts[0][1])}`
  for (let i = 1; i < pts.length; i++) {
    const prev = pts[i - 1]
    const cur = pts[i]
    const next = pts[i + 1]
    const segLen = Math.hypot(cur[0] - prev[0], cur[1] - prev[1])
    if (!next || segLen < 1e-6) {
      d += `L${fmt(cur[0])},${fmt(cur[1])}`
      continue
    }
    const nLen = Math.hypot(next[0] - cur[0], next[1] - cur[1])
    const r = Math.min(radius, segLen / 2, nLen / 2)
    const ux = (cur[0] - prev[0]) / segLen
    const uy = (cur[1] - prev[1]) / segLen
    const vx = (next[0] - cur[0]) / nLen
    const vy = (next[1] - cur[1]) / nLen
    const before = [cur[0] - ux * r, cur[1] - uy * r]
    const after = [cur[0] + vx * r, cur[1] + vy * r]
    d += `L${fmt(before[0])},${fmt(before[1])} Q${fmt(cur[0])},${fmt(cur[1])} ${fmt(after[0])},${fmt(after[1])}`
  }
  return d
}

/** 直上直下的三折线点串(起点→总线→终点)。 */
function orthPoints(x1, y1, x2, y2, busY) {
  if (Math.abs(x1 - x2) < 0.5) return [[x1, y1], [x2, y2]]
  return [[x1, y1], [x1, busY], [x2, busY], [x2, y2]]
}

/** 线段与轴对齐矩形是否相交(矩形外扩 pad 像素,贴边也视为相交)。 */
function segIntersectsRect(x1, y1, x2, y2, rect, pad = 1.5) {
  const xmin = rect.x - pad
  const xmax = rect.x + rect.w + pad
  const ymin = rect.y - pad
  const ymax = rect.y + rect.h + pad
  const dx = x2 - x1
  const dy = y2 - y1
  const p = [-dx, dx, -dy, dy]
  const q = [x1 - xmin, xmax - x1, y1 - ymin, ymax - y1]
  let tmin = 0
  let tmax = 1
  for (let i = 0; i < 4; i++) {
    if (Math.abs(p[i]) < 1e-9) {
      if (q[i] < 0) return false
    } else {
      const t = q[i] / p[i]
      if (p[i] < 0) {
        if (t > tmax) return false
        if (t > tmin) tmin = t
      } else {
        if (t < tmin) return false
        if (t < tmax) tmax = t
      }
    }
  }
  return true
}

/** 折线是否穿过任一节点卡片(端点所在单元除外)。 */
function polylineHitsNodes(pts, nodeRects, excludeUnitIds) {
  for (let i = 1; i < pts.length; i++) {
    for (const r of nodeRects) {
      if (excludeUnitIds.has(r.unitId)) continue
      if (segIntersectsRect(pts[i - 1][0], pts[i - 1][1], pts[i][0], pts[i][1], r)) return true
    }
  }
  return false
}

/**
 * 补充关系(非主亲子)走线:先试短正交线,不穿节点就直接用;
 * 穿节点时沿画布左右留白贴边走,垂直段始终落在卡片区之外,保证不压人物卡片。
 */
function routeExtraEdge({ fromX, fromY, toX, toY, parentUnitId, childUnitId, nodes, opts, width }) {
  const nodeRects = [...nodes.values()].map((n) => ({ x: n.x, y: n.y, w: n.width, h: n.height, unitId: n.unitId }))
  const exclude = new Set([parentUnitId, childUnitId])
  const vGap = Math.min(18, opts.levelGap * 0.24)

  if (fromY < toY) {
    const busY = fromY + Math.min(26, opts.levelGap * 0.3)
    const direct = orthPoints(fromX, fromY, toX, toY, busY)
    if (!polylineHitsNodes(direct, nodeRects, exclude)) return direct
  }

  const side = (fromX + toX) / 2 < width / 2 ? 'left' : 'right'
  const rx = side === 'left' ? opts.padding * 0.5 : width - opts.padding * 0.5
  if (fromY < toY) {
    return [
      [fromX, fromY],
      [fromX, fromY + vGap],
      [rx, fromY + vGap],
      [rx, toY - vGap],
      [toX, toY - vGap],
      [toX, toY],
    ]
  }
  if (fromY > toY) {
    return [
      [fromX, fromY],
      [fromX, fromY - vGap],
      [rx, fromY - vGap],
      [rx, toY + vGap],
      [toX, toY + vGap],
      [toX, toY],
    ]
  }
  const lane = fromY + vGap
  return [
    [fromX, fromY],
    [fromX, lane],
    [toX, lane],
    [toX, toY],
  ]
}

/**
 * 生成家谱图布局。
 * @returns {{
 *   width:number, height:number,
 *   nodes: Map<number, {id:number,x:number,y:number,cx:number,cy:number,width:number,height:number,unitId:string}>,
 *   units: Array<object>, edges: Array<object>, stats: object
 * }}
 */
export function buildFamilyGraph(members, options = {}) {
  const opts = { ...DEFAULT_OPTIONS, ...options }
  const normalized = normalizeFamily(members)
  const { units, unitOfPerson } = buildUnits(normalized)
  const unitList = [...units.values()]
  if (!unitList.length) {
    return {
      width: 0,
      height: 0,
      nodes: new Map(),
      units: [],
      edges: [],
      stats: { people: 0, units: 0, orphanLinks: normalized.orphanLinks },
    }
  }

  const unitWidth = (unit) => unit.memberIds.length * opts.cardWidth + Math.max(0, unit.memberIds.length - 1) * opts.spouseGap
  const { parentUnitOf, parentRefs, childUnits } = attachParents(normalized, units, unitOfPerson)

  const measure = new Map()
  const measureUnit = (unitId, stack = new Set()) => {
    if (measure.has(unitId)) return measure.get(unitId)
    if (stack.has(unitId)) return { width: unitWidth(units.get(unitId)), height: opts.cardHeight }
    stack.add(unitId)
    const unit = units.get(unitId)
    const kids = childUnits.get(unitId) || []
    const kidInfos = kids.map((id) => measureUnit(id, stack))
    const kidsWidth = kidInfos.reduce((sum, info) => sum + info.width, 0) + Math.max(0, kids.length - 1) * opts.siblingGap
    const kidsHeight = kidInfos.length ? Math.max(...kidInfos.map((i) => i.height)) + opts.levelGap : 0
    const info = {
      width: Math.max(unitWidth(unit), kidsWidth),
      height: opts.cardHeight + kidsHeight,
    }
    measure.set(unitId, info)
    stack.delete(unitId)
    return info
  }

  const nodes = new Map()
  const placed = []
  const placeUnit = (unitId, left, top, depth) => {
    const unit = units.get(unitId)
    const info = measureUnit(unitId)
    const width = unitWidth(unit)
    const unitLeft = left + (info.width - width) / 2
    unit.x = unitLeft
    unit.y = top
    unit.depth = depth
    unit.width = width
    unit.order = placed.length
    placed.push(unit)

    unit.memberIds.forEach((memberId, index) => {
      const x = unit.memberIds.length >= 2 ? unitLeft + index * (opts.cardWidth + opts.spouseGap) : unitLeft + (width - opts.cardWidth) / 2
      nodes.set(memberId, {
        id: memberId,
        x,
        y: top,
        cx: x + opts.cardWidth / 2,
        cy: top + opts.cardHeight / 2,
        width: opts.cardWidth,
        height: opts.cardHeight,
        unitId,
      })
    })

    const kids = childUnits.get(unitId) || []
    if (!kids.length) return
    const kidInfos = kids.map((id) => measureUnit(id))
    const kidsWidth = kidInfos.reduce((sum, i) => sum + i.width, 0) + Math.max(0, kids.length - 1) * opts.siblingGap
    let cursor = left + (info.width - kidsWidth) / 2
    const kidTop = top + opts.cardHeight + opts.levelGap
    kids.forEach((id, index) => {
      placeUnit(id, cursor, kidTop, depth + 1)
      cursor += kidInfos[index].width + opts.siblingGap
    })
  }

  const roots = unitList.filter((unit) => !parentUnitOf.get(unit.id))
  const rootInfos = roots.map((unit) => measureUnit(unit.id))
  const rootsWidth = rootInfos.reduce((sum, info) => sum + info.width, 0) + Math.max(0, roots.length - 1) * opts.rootGap
  let cursor = opts.padding
  roots.forEach((unit, index) => {
    placeUnit(unit.id, cursor, opts.padding, 0)
    cursor += rootInfos[index].width + opts.rootGap
  })

  const width = rootsWidth + opts.padding * 2
  const height = Math.max(...rootInfos.map((info) => info.height), opts.cardHeight) + opts.padding * 2

  const edges = []
  // 夫妻线:相邻卡片画一条横线;脏数据出现三人及以上同单元时,远距离夫妻画弧线
  for (const unit of placed) {
    if (unit.memberIds.length < 2) continue
    unit.memberIds.forEach((id, i) => {
      unit.memberIds.forEach((other, j) => {
        if (j <= i) return
        if (!normalized.spouseOf.get(id)?.has(other)) return
        const leftNode = nodes.get(id)
        const rightNode = nodes.get(other)
        const adjacent = j === i + 1
        const y1 = leftNode.y + leftNode.height / 2
        const x1 = leftNode.x + leftNode.width
        const x2 = rightNode.x
        const edge = {
          key: `s-${unit.id}-${id}-${other}`,
          type: 'spouse',
          unitId: unit.id,
          memberIds: [id, other],
          x1,
          y1,
          x2,
          y2: rightNode.y + rightNode.height / 2,
          heartX: (x1 + x2) / 2,
          heartY: y1 - 7,
        }
        if (!adjacent) {
          const top = leftNode.y - 4
          edge.path = `M${x1},${y1} C${x1 + 18},${top} ${x2 - 18},${top} ${x2},${edge.y2}`
          edge.heartY = top + 4
        }
        edges.push(edge)
      })
    })
  }

  // 亲子线:主挂点实线,同一父单元的子女共用一条总线(整齐的树形主干);
  // 补充关系(第二个父/母在不同单元)画成虚线,并走避障路径保证不穿过人物卡片。
  const busYOf = new Map()
  for (const [parentUnitId, kids] of childUnits) {
    if (!kids.length) continue
    const parentUnit = units.get(parentUnitId)
    busYOf.set(parentUnitId, parentUnit.y + opts.cardHeight + Math.min(40, opts.levelGap * 0.45))
  }

  for (const unit of placed) {
    const groups = parentRefs.get(unit.id)
    if (!groups) continue
    for (const [parentUnitId, byMember] of groups) {
      const parentUnit = units.get(parentUnitId)
      if (!parentUnit) continue
      const isPrimary = parentUnitOf.get(unit.id) === parentUnitId
      for (const [memberId, parentIds] of byMember) {
        const child = nodes.get(memberId)
        if (!child) continue
        const parentNodes = [...parentIds].map((id) => nodes.get(id)).filter(Boolean)
        if (!parentNodes.length) continue
        const both = parentNodes.length >= 2
        const parentCx = both ? parentUnit.x + parentUnit.width / 2 : parentNodes[0].cx
        const base = {
          key: `p-${unit.id}-${memberId}-${parentUnitId}`,
          type: 'parent',
          primary: isPrimary,
          fromUnitId: parentUnitId,
          toUnitId: unit.id,
          memberId,
          parentIds: [...parentIds],
        }
        if (isPrimary) {
          const fromX = parentCx
          const fromY = parentUnit.y + opts.cardHeight
          const busY = busYOf.get(parentUnitId)
          edges.push({
            ...base,
            x1: fromX,
            y1: fromY,
            x2: child.cx,
            y2: child.y,
            path: polylinePath(orthPoints(fromX, fromY, child.cx, child.y, busY)),
          })
        } else {
          const parentTop = parentUnit.y
          const parentBottom = parentUnit.y + opts.cardHeight
          const childTop = child.y
          const childBottom = child.y + opts.cardHeight
          let fromX
          let fromY
          let toX
          let toY
          if (parentBottom <= childTop) {
            fromX = parentCx
            fromY = parentBottom
            toX = child.cx
            toY = childTop
          } else if (parentTop >= childBottom) {
            fromX = parentCx
            fromY = parentTop
            toX = child.cx
            toY = childBottom
          } else {
            fromX = parentCx
            fromY = parentBottom
            toX = child.cx
            toY = childBottom
          }
          const pts = routeExtraEdge({
            fromX,
            fromY,
            toX,
            toY,
            parentUnitId,
            childUnitId: unit.id,
            nodes,
            opts,
            width,
          })
          edges.push({ ...base, x1: fromX, y1: fromY, x2: toX, y2: toY, path: polylinePath(pts) })
        }
      }
    }
  }

  const spousePairs = placed.filter((unit) => unit.memberIds.length >= 2).length
  return {
    width,
    height,
    nodes,
    units: placed,
    edges,
    stats: {
      people: normalized.byId.size,
      units: placed.length,
      spousePairs,
      orphanLinks: normalized.orphanLinks,
      parentLinks: edges.filter((e) => e.type === 'parent').length,
    },
  }
}
