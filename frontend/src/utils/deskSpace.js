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
export function deskLanes(width, height, navRight, center, maxMargin = 180) {
  const middle = (width + navRight) / 2
  const leftEnd = center ? Math.min(width, center.left) : Math.min(middle, navRight + maxMargin)
  const rightStart = center ? Math.max(navRight, center.right) : Math.max(middle, width - maxMargin)
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

// 914px 高时，大件的画布边长为 381px。小件保留各自的相对尺度。
const ITEM_SCALE = {
  journal: 1,
  record: 0.98,
  recipe: 0.94,
  photo: 0.88,
  cup: 0.83,
  envelope: 0.8,
  note: 0.75,
  phone: 0.72,
  fruit: 0.7,
  bookmark: 0.65,
  leaf: 0.58,
}

function findDeskSpace(item, slot, lanes, obstacles, cards) {
  const { right: width, bottom: height } = lanes[1]
  const scale = ITEM_SCALE[item.kind] ?? 0.85
  const preferred = Math.round(Math.min(480, Math.max(180, height * (381 / 914))) * scale)
  const spread = tiltSpread(slot.angle)
  const maxRadius = turnedRadius(preferred, slot.angle)
  const peek = Math.round(Math.min(44, Math.max(28, width * 0.045)))
  const near = 0.14 + slot.u * 0.1
  const bands = [near, 1 - near, 0.32, 0.68, 0.08, 0.92]
  for (const side of [slot.side, 1 - slot.side]) {
    const lane = lanes[side]
    for (const band of bands) {
      const y = Math.round(height * (slot.v < 0.5 ? band : 1 - band))
      let gutter = lane.right - lane.left
      for (const card of cards) {
        if (card.top < y + maxRadius && card.bottom > y - maxRadius) {
          gutter = Math.min(gutter, side === 0 ? card.left - lane.left : lane.right - card.right)
        }
      }
      const room = Math.max(0, Math.floor(gutter)) + peek
      // 窄边仍保留物件的画幅，压缩到首选尺寸的七成后只裁切露出的部分。
      const size = Math.min(preferred, Math.max(Math.round(preferred * 0.7), Math.floor((room - 12) / spread)))
      const radius = turnedRadius(size, slot.angle)
      if (y - radius < 6 || y + radius > height - 6) continue
      const cropped = room < 2 * radius
      const inset = cropped ? room / 2 : radius
      const x = side === 0 ? lane.left + inset : lane.right - inset
      const box = bounds(x, y, radius)
      if (cropped) {
        box.left = x - inset
        box.right = x + inset
      }
      if (!obstacles.some((other) => overlaps(box, other))) {
        return { x, y, size, cropWidth: cropped ? room : 0, box, edge: side === 0 ? 'left' : 'right' }
      }
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

export function placeDeskItems(items, plan, lanes, obstacles, cards = []) {
  const result = []
  const occupied = obstacles.slice()
  for (const item of items) {
    const slot = plan[result.length]
    if (!slot) break
    if (lanes[1].right < 600 && result.length) break
    const seat = findDeskSpace(item, slot, lanes, occupied, cards)
    if (!seat) continue
    const { box, ...layout } = seat
    result.push({ item, angle: slot.angle, ...layout })
    occupied.push(box)
  }
  return result
}

export function placePawTrail(placements, lanes, obstacles, random = Math.random) {
  if (placements.length >= 3) return []
  const occupied = obstacles.concat(placements.map(({ x, y, size, angle }) => bounds(x, y, turnedRadius(size, angle))))
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
  const occupied = obstacles.concat(placements.map(({ x, y, size, angle }) => bounds(x, y, turnedRadius(size, angle))))
  return trail.every(({ x, y }) => {
    const box = bounds(x, y, 13)
    return (
      lanes.some((l) => box.left >= l.left && box.right <= l.right && box.top >= l.top && box.bottom <= l.bottom) &&
      !occupied.some((other) => overlaps(box, other))
    )
  })
}
