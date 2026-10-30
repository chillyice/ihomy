// 家谱关系图:把扁平成员(父/母/配偶)归一化为夫妻单元,再按「世代分层」排布——
// 同一代的单元排在同一行,父母恒在上方、子女恒在下方。
// 三条关键约束:
// 1) 布局完全不依赖 family_tree.generation —— 历史数据世代填错、夫妻世代不一致也不会被拆到不同行;
// 2) 一个单元可以同时挂在两组父母下(男方父母 / 女方父母),两组都画实线;
//    「姻亲父母」不单独成树,而是紧贴本家父母右侧、占同一行的槽位,到子女就是相邻行的短竖线;
// 3) 连线只走「行间空隙」与「卡片正上方/正下方」,所以不会压到人物卡片。
// 输出纯数据(节点坐标/边路径),由组件负责渲染,便于单测。

const DEFAULT_OPTIONS = {
  cardWidth: 108,
  cardHeight: 104,
  spouseGap: 16,
  siblingGap: 28,
  levelGap: 96,
  rootGap: 64,
  padding: 32,
  busOffset: 20,
  busLaneGap: 9,
  busLanes: 4,
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

/**
 * 每个单元的父单元分组:一个单元最多两组(男方父母 / 女方父母)。
 * byMember 记录「本单元里哪位成员」通过这条关系挂上去,用于把线连到具体那张卡片。
 */
function collectParentGroups(normalized, units, unitOfPerson) {
  const groupsOf = new Map()
  for (const unit of units.values()) {
    const perUnit = new Map()
    for (const memberId of unit.memberIds) {
      for (const p of normalized.parentsOf.get(memberId) || []) {
        const parentUnitId = unitOfPerson.get(p.id)
        if (!parentUnitId || parentUnitId === unit.id) continue
        if (!perUnit.has(parentUnitId)) perUnit.set(parentUnitId, new Map())
        const byMember = perUnit.get(parentUnitId)
        if (!byMember.has(memberId)) byMember.set(memberId, new Set())
        byMember.get(memberId).add(p.id)
      }
    }
    if (perUnit.size) groupsOf.set(unit.id, perUnit)
  }
  return groupsOf
}

/** DFS 标记回边:脏数据成环时丢掉成环的那条关系,后续世代分层才有拓扑序。 */
function dropBackEdges(unitList, childUnitsOf) {
  const color = new Map(unitList.map((u) => [u.id, 0]))
  const dropped = new Set()
  for (const root of unitList) {
    if (color.get(root.id)) continue
    const stack = [[root.id, 0]]
    color.set(root.id, 1)
    while (stack.length) {
      const top = stack[stack.length - 1]
      const kids = childUnitsOf.get(top[0]) || []
      if (top[1] >= kids.length) {
        color.set(top[0], 2)
        stack.pop()
        continue
      }
      const kid = kids[top[1]]
      top[1] += 1
      const state = color.get(kid)
      if (state === 1) {
        dropped.add(`${kid}>${top[0]}`)
      } else if (!state) {
        color.set(kid, 1)
        stack.push([kid, 0])
      }
    }
  }
  return dropped
}

/**
 * 姻亲单元:只有这一个子女、自己又没有父母,且不是该子女的主血亲挂点。
 * 它应当紧贴子女的本家父母排在同一个行,而不是被当成独立根系摆到画布另一端。
 */
function makeSatelliteTest(childUnitsOf, parentUnitsOf, primaryParentOf) {
  return (id) => {
    const kids = childUnitsOf.get(id) || []
    if (kids.length !== 1 || (parentUnitsOf.get(id) || []).length) return false
    const primaryParentOfKid = primaryParentOf.get(kids[0])
    return primaryParentOfKid !== undefined && primaryParentOfKid !== id
  }
}

/**
 * 递归铺排:以「主血亲树」为准 —— 父母居中于子女块,子女按录入序从左到右。
 * 姻亲单元不再单独成树,而是占用宿主单元那一行的右侧槽位,
 * 于是「男方父母 / 女方父母」并排出现在子女正上方,连线都是相邻行的短竖线。
 * 世代(行号)直接取主血亲树的深度,不再依赖 family_tree.generation。
 */
function layoutForest(unitList, parentUnitsOf, primaryChildrenOf, satellitesOf, units, unitWidth, opts) {
  const slotWidthOf = (id) =>
    unitWidth(units.get(id)) +
    (primaryChildrenOf.get(id) || []).reduce(
      (sum, c) => sum + satellitesOf(c).reduce((s, sat) => s + unitWidth(units.get(sat)) + opts.siblingGap, 0),
      0,
    )

  const measureOf = new Map()
  const measure = (id, stack = new Set()) => {
    if (measureOf.has(id)) return measureOf.get(id)
    if (stack.has(id)) return { width: unitWidth(units.get(id)), height: opts.cardHeight }
    stack.add(id)
    const kids = primaryChildrenOf.get(id) || []
    const infos = kids.map((c) => measure(c, stack))
    const kidsWidth = infos.reduce((sum, info) => sum + info.width, 0) + Math.max(0, kids.length - 1) * opts.siblingGap
    const info = {
      width: Math.max(slotWidthOf(id), kidsWidth),
      height: opts.cardHeight + (kids.length ? opts.levelGap + Math.max(...infos.map((i) => i.height)) : 0),
    }
    stack.delete(id)
    measureOf.set(id, info)
    return info
  }

  const pos = new Map()
  const place = (id, left, top, level) => {
    if (pos.has(id)) return
    const info = measure(id)
    const own = unitWidth(units.get(id))
    const kids = primaryChildrenOf.get(id) || []
    const infos = kids.map((c) => measure(c))
    const kidsWidth = infos.reduce((sum, info2) => sum + info2.width, 0) + Math.max(0, kids.length - 1) * opts.siblingGap
    const slotLeft = left + (info.width - slotWidthOf(id)) / 2
    pos.set(id, { x: slotLeft, y: top, level })

    // 姻亲块:紧跟宿主,占同一行右侧
    let satCursor = slotLeft + own + opts.siblingGap
    for (const c of kids) {
      for (const sat of satellitesOf(c)) {
        if (pos.has(sat)) continue
        pos.set(sat, { x: satCursor, y: top, level })
        satCursor += unitWidth(units.get(sat)) + opts.siblingGap
      }
    }

    let cursor = left + (info.width - kidsWidth) / 2
    kids.forEach((c, index) => {
      place(c, cursor, top + opts.cardHeight + opts.levelGap, level + 1)
      cursor += infos[index].width + opts.siblingGap
    })
  }

  const satelliteIds = new Set()
  for (const unit of unitList) for (const sat of satellitesOf(unit.id)) satelliteIds.add(sat)
  const roots = unitList.filter((u) => !(parentUnitsOf.get(u.id) || []).length && !satelliteIds.has(u.id))

  let cursor = opts.padding
  let maxHeight = opts.cardHeight
  for (const root of roots) {
    const info = measure(root.id)
    place(root.id, cursor, opts.padding, 0)
    cursor += info.width + opts.rootGap
    maxHeight = Math.max(maxHeight, info.height)
  }
  // 脏数据里可能留下没挂上任何树的单元(如姻亲的宿主本身缺失),兜底各自成根
  for (const unit of unitList) {
    if (pos.has(unit.id)) continue
    const info = measure(unit.id)
    place(unit.id, cursor, opts.padding, 0)
    cursor += info.width + opts.rootGap
    maxHeight = Math.max(maxHeight, info.height)
  }

  const width = Math.max(opts.padding * 2, cursor - opts.rootGap + opts.padding)
  const height = maxHeight + opts.padding * 2
  return { pos, width, height }
}

const fmt = (n) => Math.round(n * 10) / 10

/** 把折线点串成 SVG path,转角用二次贝塞尔圆角,避免生硬的直角。 */
function polylinePath(pts, radius = 8) {
  if (!pts || pts.length < 2) return ''
  let d = `M${fmt(pts[0][0])},${fmt(pts[0][1])}`
  for (let i = 1; i < pts.length; i += 1) {
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
  if (Math.abs(x1 - x2) < 0.5)
    return [
      [x1, y1],
      [x2, y2],
    ]
  return [
    [x1, y1],
    [x1, busY],
    [x2, busY],
    [x2, y2],
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
      normalized,
      stats: { people: 0, units: 0, spousePairs: 0, parentLinks: 0, orphanLinks: normalized.orphanLinks },
    }
  }

  const unitWidth = (unit) => unit.memberIds.length * opts.cardWidth + Math.max(0, unit.memberIds.length - 1) * opts.spouseGap
  const groupsOf = collectParentGroups(normalized, units, unitOfPerson)

  // 父 → 子 的邻接表(去重),先断环再分层
  const childUnitsOf = new Map()
  for (const [childUnitId, perUnit] of groupsOf) {
    for (const parentUnitId of perUnit.keys()) {
      if (!childUnitsOf.has(parentUnitId)) childUnitsOf.set(parentUnitId, [])
      childUnitsOf.get(parentUnitId).push(childUnitId)
    }
  }
  const dropped = dropBackEdges(unitList, childUnitsOf)
  for (const [parentUnitId, kids] of childUnitsOf) {
    childUnitsOf.set(
      parentUnitId,
      kids.filter((kid) => !dropped.has(`${parentUnitId}>${kid}`)),
    )
  }
  const parentUnitsOf = new Map()
  for (const [parentUnitId, kids] of childUnitsOf) {
    for (const kid of kids) {
      if (!parentUnitsOf.has(kid)) parentUnitsOf.set(kid, [])
      parentUnitsOf.get(kid).push(parentUnitId)
    }
  }

  // 主血亲挂点:每个单元的第一组父母(本人一侧,即男方父母)。铺排以这棵树为骨架。
  const primaryParentOf = new Map()
  for (const [childUnitId, perUnit] of groupsOf) {
    const usable = [...perUnit.keys()].filter((parentUnitId) => !dropped.has(`${parentUnitId}>${childUnitId}`))
    if (usable.length) primaryParentOf.set(childUnitId, usable[0])
  }
  const primaryChildrenOf = new Map()
  for (const [kid, parentUnitId] of primaryParentOf) {
    if (!primaryChildrenOf.has(parentUnitId)) primaryChildrenOf.set(parentUnitId, [])
    primaryChildrenOf.get(parentUnitId).push(kid)
  }
  const isSatellite = makeSatelliteTest(childUnitsOf, parentUnitsOf, primaryParentOf)
  const satellitesOf = (id) => (parentUnitsOf.get(id) || []).filter(isSatellite)
  const { pos, width, height } = layoutForest(unitList, parentUnitsOf, primaryChildrenOf, satellitesOf, units, unitWidth, opts)

  // 落坐标:单元 → 卡片
  const nodes = new Map()
  const placed = []
  for (const unit of unitList) {
    const spot = pos.get(unit.id)
    if (!spot) continue
    const unitW = unitWidth(unit)
    unit.x = spot.x
    unit.y = spot.y
    unit.width = unitW
    unit.depth = spot.level
    unit.level = spot.level
    placed.push(unit)
    unit.memberIds.forEach((memberId, index) => {
      const x = spot.x + index * (opts.cardWidth + opts.spouseGap)
      nodes.set(memberId, {
        id: memberId,
        x,
        y: spot.y,
        cx: x + opts.cardWidth / 2,
        cy: spot.y + opts.cardHeight / 2,
        width: opts.cardWidth,
        height: opts.cardHeight,
        unitId: unit.id,
      })
    })
  }

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
        const x1 = leftNode.x + leftNode.width
        const x2 = rightNode.x
        const y1 = leftNode.y + leftNode.height / 2
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

  // 亲子线:从「夫妻横线中点」(单人取卡片下沿)垂到本行下方的总线,再横到子女卡片正上方落下。
  // 同层并列的父母单元分配不同总线高度,避免两条总线重叠。
  const rowBottomOf = (level) => opts.padding + level * (opts.cardHeight + opts.levelGap) + opts.cardHeight
  const junctionOf = (unit) => {
    if (unit.memberIds.length >= 2) {
      const first = nodes.get(unit.memberIds[0])
      const second = nodes.get(unit.memberIds[1])
      return { x: (first.x + first.width + second.x) / 2, y: first.y + first.height / 2 }
    }
    const only = nodes.get(unit.memberIds[0])
    return { x: only.cx, y: only.y + only.height }
  }

  const busYOf = new Map()
  const laneCount = new Map()
  for (const unit of placed) {
    const kids = childUnitsOf.get(unit.id) || []
    if (!kids.length) continue
    const lane = (laneCount.get(unit.level) || 0) % opts.busLanes
    laneCount.set(unit.level, (laneCount.get(unit.level) || 0) + 1)
    busYOf.set(unit.id, rowBottomOf(unit.level) + opts.busOffset + lane * opts.busLaneGap)
  }

  const laneX = (x1, x2) => ((x1 + x2) / 2 < width / 2 ? opts.padding * 0.45 : width - opts.padding * 0.45)
  const spanPoints = (jx, jy, cx, childTop, busY) => {
    const sideX = laneX(jx, cx)
    const enterY = childTop - Math.min(20, opts.levelGap * 0.24)
    return [
      [jx, jy],
      [jx, busY],
      [sideX, busY],
      [sideX, enterY],
      [cx, enterY],
      [cx, childTop],
    ]
  }

  for (const unit of placed) {
    const perUnit = groupsOf.get(unit.id)
    if (!perUnit) continue
    const isPrimaryGroup = new Map()
    let first = true
    for (const parentUnitId of perUnit.keys()) {
      isPrimaryGroup.set(parentUnitId, first && !dropped.has(`${parentUnitId}>${unit.id}`))
      first = false
    }
    for (const [parentUnitId, byMember] of perUnit) {
      if (dropped.has(`${parentUnitId}>${unit.id}`)) continue
      const parentUnit = units.get(parentUnitId)
      if (!parentUnit) continue
      const junction = junctionOf(parentUnit)
      const busY = busYOf.get(parentUnitId)
      for (const [memberId, parentIds] of byMember) {
        const child = nodes.get(memberId)
        if (!child) continue
        const adjacent = unit.level === parentUnit.level + 1
        const pts = adjacent
          ? orthPoints(junction.x, junction.y, child.cx, child.y, busY)
          : spanPoints(junction.x, junction.y, child.cx, child.y, busY)
        edges.push({
          key: `p-${unit.id}-${memberId}-${parentUnitId}`,
          type: 'parent',
          primary: isPrimaryGroup.get(parentUnitId),
          fromUnitId: parentUnitId,
          toUnitId: unit.id,
          memberId,
          parentIds: [...parentIds],
          x1: junction.x,
          y1: junction.y,
          x2: child.cx,
          y2: child.y,
          path: polylinePath(pts),
        })
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
    normalized,
    stats: {
      people: normalized.byId.size,
      units: placed.length,
      spousePairs,
      orphanLinks: normalized.orphanLinks,
      parentLinks: edges.filter((e) => e.type === 'parent').length,
    },
  }
}

// ---------------------------------------------------------------------------
// 画板交互的纯函数:命中测试、坐标换算、关系载荷、最短关系路径。
// 组件只负责事件与渲染,判断逻辑都在这里,便于单测。
// ---------------------------------------------------------------------------

/** 画布坐标:视口内客户端坐标 → 画板内坐标(与 translate+scale 变换互逆)。 */
export function canvasPoint(clientX, clientY, rect, offsetX, offsetY, scale) {
  const s = scale || 1
  return { x: (clientX - rect.left - offsetX) / s, y: (clientY - rect.top - offsetY) / s }
}

/** 命中的节点:点在卡片外扩 threshold 范围内即算命中,多个候选取离中心最近的。 */
export function nearestNode(nodes, canvasX, canvasY, threshold = 24) {
  let bestId = null
  let bestDistance = Infinity
  for (const node of nodes instanceof Map ? nodes.values() : nodes || []) {
    const left = node.x ?? node.cx - (node.width || 0) / 2
    const top = node.y ?? node.cy - (node.height || 0) / 2
    const right = left + (node.width || 0)
    const bottom = top + (node.height || 0)
    const dx = Math.max(left - canvasX, 0, canvasX - right)
    const dy = Math.max(top - canvasY, 0, canvasY - bottom)
    if (Math.hypot(dx, dy) > threshold) continue
    const distance = Math.hypot((left + right) / 2 - canvasX, (top + bottom) / 2 - canvasY)
    if (distance < bestDistance) {
      bestDistance = distance
      bestId = node.id
    }
  }
  return bestId
}

/**
 * 关系选择 → 接口载荷。option 是「from 是 to 的什么」:
 * FATHER/MOTHER/SPOUSE 直接给 kind;CHILD 表示「from 是 to 的子女」,
 * 需要按 to 的性别把方向反过来(性别未知时返回 needGenderOf 让用户先选)。
 */
export function relationPayload(fromId, toId, option, members) {
  if (fromId == null || toId == null || fromId === toId) return null
  switch (option) {
    case 'FATHER':
    case 'MOTHER':
    case 'SPOUSE':
      return { fromId, toId, kind: option }
    case 'CHILD': {
      const target = (members || []).find((m) => m.id === toId)
      if (target?.gender === 1) return { fromId: toId, toId: fromId, kind: 'FATHER' }
      if (target?.gender === 2) return { fromId: toId, toId: fromId, kind: 'MOTHER' }
      return { needGenderOf: toId }
    }
    default:
      return null
  }
}

const relationPairKey = (a, b) => (a < b ? `${a}|${b}` : `${b}|${a}`)

/** 关系跳 → 图上连线 key 的索引:夫妻按成员对,亲子按「父母-子女」逐对。 */
function buildHopIndex(edges) {
  const index = new Map()
  const add = (a, b, key) => {
    const k = relationPairKey(a, b)
    if (!index.has(k)) index.set(k, new Set())
    index.get(k).add(key)
  }
  for (const edge of edges || []) {
    if (edge.type === 'spouse' && edge.memberIds?.length >= 2) {
      add(edge.memberIds[0], edge.memberIds[1], edge.key)
    } else if (edge.type === 'parent' && edge.memberId != null) {
      for (const pid of edge.parentIds || []) add(pid, edge.memberId, edge.key)
    }
  }
  return index
}

/** 归一化关系 → 无向邻接表(夫妻 + 亲子),只含真实存在的人。 */
export function relationAdjacency(normalized) {
  const adjacency = new Map()
  const ensure = (id) => {
    if (!adjacency.has(id)) adjacency.set(id, new Set())
    return adjacency.get(id)
  }
  for (const id of normalized?.byId?.keys() || []) ensure(id)
  const link = (a, b) => {
    if (!normalized.byId.has(a) || !normalized.byId.has(b) || a === b) return
    ensure(a).add(b)
    ensure(b).add(a)
  }
  for (const [id, spouses] of normalized?.spouseOf || []) for (const sid of spouses) link(id, sid)
  for (const [child, parents] of normalized?.parentsOf || []) for (const p of parents) link(p.id, child)
  return adjacency
}

/**
 * 两人之间的「最近连线」= 关系跳数最少(BFS,不是屏幕距离),多解全取。
 * 返回路径上的边 key 与节点 id;无通路时 distance=-1 且集合为空。
 */
export function shortestRelationEdgeKeys(graph, fromId, toId) {
  const empty = { distance: -1, edgeKeys: new Set(), nodeIds: new Set() }
  if (!graph || fromId == null || toId == null || fromId === toId) return empty
  const normalized = graph.normalized
  if (!normalized?.byId?.has(fromId) || !normalized.byId.has(toId)) return empty

  const adjacency = relationAdjacency(normalized)
  const bfs = (start) => {
    const dist = new Map([[start, 0]])
    const queue = [start]
    for (let head = 0; head < queue.length; head += 1) {
      const cur = queue[head]
      for (const next of adjacency.get(cur) || []) {
        if (dist.has(next)) continue
        dist.set(next, dist.get(cur) + 1)
        queue.push(next)
      }
    }
    return dist
  }

  const dFrom = bfs(fromId)
  const distance = dFrom.get(toId)
  if (distance == null) return empty
  const dTo = bfs(toId)

  const hopIndex = buildHopIndex(graph.edges)
  const edgeKeys = new Set()
  const nodeIds = new Set()
  for (const [id, d] of dFrom) {
    if (d + (dTo.get(id) ?? Infinity) === distance) nodeIds.add(id)
  }
  for (const [a, d] of dFrom) {
    for (const b of adjacency.get(a) || []) {
      if (d + 1 + (dTo.get(b) ?? Infinity) !== distance) continue
      for (const key of hopIndex.get(relationPairKey(a, b)) || []) edgeKeys.add(key)
    }
  }
  return { distance, edgeKeys, nodeIds }
}
