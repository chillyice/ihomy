// 贴图随机歪放的最大角度（±）。超过 45° 就成了「斜着挂」，观感不对，所以封顶在这里。
const MAX_TILT = 30

// 页面之间共用一次抽样。几何计算只读这些比例，重新测量不会重新掷骰。
export function sampleDeskPlan(random = Math.random, minimumCount = 0) {
  const roll = random()
  const count = roll < 0.15 ? 0 : roll < 0.5 ? 1 : roll < 0.87 ? 2 : 3
  const firstSide = Math.floor(random() * 2)
  return Array.from({ length: Math.max(minimumCount, count) }, (_, i) => ({
    side: (firstSide + i) % 2,
    u: 0.22 + random() * 0.56,
    v: 0.12 + random() * 0.76,
    angle: Math.round(random() * MAX_TILT * 2 - MAX_TILT),
  }))
}

// 常规内容页按正文边缘分区；自由布局的首页只在两条窄边内找位置，卡片另外参与碰撞。
export function deskLanes(width, height, navRight, center) {
  const leftEnd = center ? Math.min(width, center.left) : Math.min(width / 2, navRight + 180)
  const rightStart = center ? Math.max(navRight, center.right) : Math.max(width / 2, width - 180)
  return [
    { left: Math.max(0, navRight), right: leftEnd, top: 0, bottom: height },
    { left: rightStart, right: width, top: 0, bottom: height },
  ]
}

function bounds(x, y, radius) {
  return { left: x - radius, right: x + radius, top: y - radius, bottom: y + radius }
}

function overlaps(a, b) {
  return a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top
}

// 歪放的方图沿 x 轴的实际半宽是 (size/2)·(|cosθ|+|sinθ|)：不按角度补偿,
// 30° 的大角会捅出贴边预留的范围,在视口边缘被裁掉。
function tiltSpread(angle = 0) {
  const rad = (Math.abs(angle) * Math.PI) / 180
  return Math.abs(Math.cos(rad)) + Math.abs(Math.sin(rad))
}

function turnedRadius(size, angle = 0) {
  return Math.ceil((size / 2) * tiltSpread(angle)) + 5
}

// 桌面物件边长 = 平板底数 ×3。桌沿装不下这么大的物品,允许跨到正文边缘;
// 只保证不压导航、整件留在视口内,并避开浮窗等障碍。
const DESK_SCALE = 3
const DESK_PREFERRED = { leaf: 84, other: 134 }
const DESK_MINIMUM = { cup: 64, other: 48 }

function findWideSpace(slot, lanes, obstacles, preferred, minimum) {
  const viewWidth = lanes[1].right
  const viewHeight = lanes[1].bottom
  const size = Math.floor(Math.min(preferred, viewWidth * 0.3, (viewHeight - 12) / 1.16))
  if (size < minimum) return null
  const radius = turnedRadius(size, slot.angle)
  if (radius * 2 + 16 > viewHeight) return null
  for (const side of [slot.side, 1 - slot.side]) {
    const lane = lanes[side]
    // 外侧贴住桌沿边界:左件紧接导航右缘,右件贴视口右缘
    const x = side === 0 ? Math.round(lane.left + radius) : Math.round(lane.right - radius)
    if (x - radius < 0 || x + radius > viewWidth) continue
    for (const v of [slot.v, 0.22, 0.76, 0.48, 0.94, 0.05]) {
      const y = Math.round(radius + 8 + (viewHeight - radius * 2 - 16) * v)
      const box = bounds(x, y, radius)
      if (!obstacles.some((other) => overlaps(box, other))) return { x, y, size, box, edge: side === 0 ? 'left' : 'right' }
    }
  }
  return null
}

// 预留旋转、悬停和投影所需的边界。窄桌沿只摆仍能拥有完整点击面的物品。
function findSpace(slot, lanes, obstacles, preferred, minimum) {
  for (const lane of [lanes[slot.side], lanes[1 - slot.side]]) {
    const width = lane.right - lane.left
    const height = lane.bottom - lane.top
    const size = Math.floor(Math.min(preferred, (Math.min(width, height) - 12) / 1.16))
    if (size < minimum) continue
    const radius = Math.ceil(size * 0.58) + 5
    const x = Math.round(lane.left + radius + (width - 2 * radius) * slot.u)
    for (const v of [slot.v, 0.22, 0.76, 0.48, 0.94, 0.05]) {
      const y = Math.round(lane.top + radius + (height - 2 * radius) * v)
      const box = bounds(x, y, radius)
      if (!obstacles.some((other) => overlaps(box, other))) return { x, y, size, box }
    }
  }
  return null
}

// 平板在视口外藏起半张图，只让约一指宽的部分探入正文。触控区也由视口裁切。
function findEdgeSpace(slot, width, height, occupied, size) {
  const radius = Math.max(Math.ceil(size * 0.58) + 4, turnedRadius(size, slot.angle))
  const inset = Math.round(6 + slot.u * 6)
  const end = radius + 24
  if (height < end * 2) return null
  for (const side of [slot.side, 1 - slot.side]) {
    const x = side === 0 ? inset : width - inset
    for (const v of [slot.v, 0.18, 0.32, 0.48, 0.64, 0.82, 0.94, 0.07]) {
      const y = Math.round(end + (height - end * 2) * v)
      const box = bounds(x, y, radius)
      if (!occupied.some((obstacle) => overlaps(box, obstacle))) return { x, y, size, box, edge: side === 0 ? 'left' : 'right' }
    }
  }
  return null
}

export function placeDeskItems(items, plan, lanes, obstacles, tabletViewport) {
  const result = []
  const occupied = obstacles.slice()
  for (const item of items) {
    const slot = plan[result.length]
    if (!slot) break
    if (tabletViewport && tabletViewport.width < 600 && result.length) break
    const seat = tabletViewport
      ? findEdgeSpace(slot, tabletViewport.width, tabletViewport.height, occupied, item.kind === 'leaf' ? 74 : 84)
      : findWideSpace(
          slot,
          lanes,
          occupied,
          (item.kind === 'leaf' ? DESK_PREFERRED.leaf : DESK_PREFERRED.other) * DESK_SCALE,
          item.kind === 'cup' ? DESK_MINIMUM.cup : DESK_MINIMUM.other,
        )
    if (!seat) continue
    const { x, y, size, box } = seat
    result.push({ item, x, y, size, angle: slot.angle, edge: seat.edge })
    occupied.push(box)
  }
  return result
}

export function placePawTrail(placements, lanes, obstacles, random = Math.random) {
  if (placements.length >= 3) return []
  const occupied = obstacles.concat(placements.map(({ x, y, size }) => bounds(x, y, Math.ceil(size * 0.58) + 5)))
  const slot = { side: Math.floor(random() * 2), u: random(), v: random() }
  const seat = findSpace(slot, lanes, occupied, 86, 54)
  if (!seat) return []
  const direction = random() < 0.5 ? 1 : -1
  return Array.from({ length: 4 }, (_, i) => ({
    x: Math.round(seat.x + (i - 1.5) * seat.size * 0.1 * direction + (i % 2 ? 3 : -3)),
    y: Math.round(seat.y + (1.5 - i) * seat.size * 0.22),
    angle: direction * 19,
    delay: i * 0.28,
  }))
}

export function pawTrailFits(trail, placements, lanes, obstacles) {
  if (!trail.length || placements.length >= 3) return false
  const occupied = obstacles.concat(placements.map(({ x, y, size }) => bounds(x, y, Math.ceil(size * 0.58) + 5)))
  return trail.every(({ x, y }) => {
    const box = bounds(x, y, 13)
    return (
      lanes.some((l) => box.left >= l.left && box.right <= l.right && box.top >= l.top && box.bottom <= l.bottom) &&
      !occupied.some((other) => overlaps(box, other))
    )
  })
}
