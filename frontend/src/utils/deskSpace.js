import { DESK_OBJECT_PROFILES } from './deskObjectProfiles'

// 贴图随机歪放的最大角度（±）。超过 45° 就成了「斜着挂」，观感不对，所以封顶在这里。
const MAX_TILT = 30

export const DESK_FLOATS = [
  '.gc-topbar',
  '.window-top',
  '.mobile-header',
  '.mobile-tabbar',
  '.site-footer',
  '.back-to-top',
  '.install-prompt',
  '.web-update',
  '.child-lock',
  '.music-player',
  '.music-player .playlist',
  '.window-player',
  '.window-queue',
  '.chat-float-ball',
  '.chat-float-panel',
  '.chat-float-bubble',
  '.last-doing-float',
  '.call-mini',
  '.daily-add-fab',
  '.home-page .door-sign',
  '.el-popper',
].join(',')

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

function* deskSeats(item, slot, lanes, obstacles, cards) {
  const { right: width, bottom: height } = lanes[1]
  const scale = DESK_OBJECT_PROFILES[item.kind]?.sizeRatio ?? 0.85
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
        yield { box, layout: { item, angle: slot.angle, x, y, size, cropWidth: cropped ? room : 0, edge: side === 0 ? 'left' : 'right' } }
      }
    }
  }
}

// 只在实际出现两件时比较位置。优先对角，其次分居两侧；被迫共边时尽量拉开高度。
function pairSeparation(a, b, height) {
  const firstIsUpper = a.y < height / 2
  const secondIsUpper = b.y < height / 2
  return (a.edge !== b.edge ? 2 : 0) + (firstIsUpper !== secondIsUpper ? 1 : 0) + Math.abs(a.y - b.y) / height
}

function spreadDeskPair(pair, plan, lanes, obstacles, cards) {
  const height = lanes[1].bottom
  let best = pair
  let score = pairSeparation(pair[0], pair[1], height)
  if (score >= 3) return best
  const secondSeats = [...deskSeats(pair[1].item, plan[1], lanes, obstacles, cards)]
  for (const first of deskSeats(pair[0].item, plan[0], lanes, obstacles, cards)) {
    for (const second of secondSeats) {
      if (overlaps(first.box, second.box)) continue
      const next = pairSeparation(first.layout, second.layout, height)
      if (next <= score) continue
      best = [first.layout, second.layout]
      if (next >= 3) return best
      score = next
    }
  }
  return best
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
    const seat = deskSeats(item, slot, lanes, occupied, cards).next().value
    if (!seat) continue
    result.push(seat.layout)
    occupied.push(seat.box)
  }
  return result.length === 2 ? spreadDeskPair(result, plan, lanes, obstacles, cards) : result
}

// 一串爪印的步距:横向 0.2 / 纵向 0.44 个物件尺寸(间距是旧版的两倍)。
const PAW_STEP_X = 0.2
const PAW_STEP_Y = 0.44

// 步数 4–12 的偶数随机:猫狗四只脚交替落地,奇数会落成「单脚」
export function pawStepCount(random = Math.random) {
  return 4 + 2 * Math.floor(random() * 5)
}

// 两串爪印之间至少 5 分钟,再随机加 0–5 分钟,避免固定节拍
export const PAW_MIN_INTERVAL = 5 * 60_000

export function pawTrailDelay(random = Math.random) {
  return PAW_MIN_INTERVAL + random() * PAW_MIN_INTERVAL
}

export function placePawTrail(lanes, obstacles, random = Math.random) {
  const slot = { side: Math.floor(random() * 2), u: random(), v: random() }
  // 座位只按单只爪印的半径找,整串会比它长很多:两条桌沿各找一个座位,整串放不下就换下一条
  const seats = []
  for (const side of [slot.side, 1 - slot.side]) {
    const seat = findSpace({ ...slot, side }, lanes, obstacles, 86, 54)
    if (seat && !seats.some((s) => s.x === seat.x && s.y === seat.y)) seats.push(seat)
  }
  if (!seats.length) return []
  // 每串都换一个弯曲方向与幅度:中段横向鼓出去、两端收回,走成一条弧线
  const curve = (random() - 0.5) * 2 * seats[0].size * 0.16
  const turning = random() < 0.5 ? 1 : -1
  const vertical = random() < 0.5 ? 1 : -1
  const steps = pawStepCount(random)
  const middle = (steps - 1) / 2
  const build = (seat, bend, side, up) => {
    // 先算中轴上的落点,再左右交替偏移成两只脚的脚印
    const center = Array.from({ length: steps }, (_, i) => ({
      x: seat.x + Math.sin((i / middle) * (Math.PI / 2)) * bend,
      y: seat.y + (middle - i) * seat.size * PAW_STEP_Y * up,
    }))
    return center.map((point, i) => {
      const prev = center[i - 1] ?? point
      const next = center[i + 1] ?? point
      return {
        x: Math.round(point.x + (i % 2 ? 1 : -1) * seat.size * PAW_STEP_X * side),
        y: Math.round(point.y),
        // 爪尖朝向跟脚步同向:取相邻两步的切线,0° 朝上,往回走时整串翻过来
        angle: Math.round((Math.atan2(next.x - prev.x, prev.y - next.y) * 180) / Math.PI),
        delay: Math.round(i * 0.18 * 100) / 100,
      }
    })
  }
  // 弯着的放不下(窄边/桌沿被占)就退回直串;横向翻不进去再试纵向翻转
  const attempts = [
    [curve, turning, vertical],
    [-curve, turning, vertical],
    [curve, turning, -vertical],
    [-curve, turning, -vertical],
    [0, turning, vertical],
    [0, turning, -vertical],
  ]
  for (const seat of seats) {
    for (const [bend, side, up] of attempts) {
      const trail = build(seat, bend, side, up)
      if (pawTrailFits(trail, lanes, obstacles)) return trail
    }
  }
  return []
}

export function pawTrailFits(trail, lanes, obstacles) {
  return trail.every(({ x, y }) => {
    const box = bounds(x, y, 13)
    return (
      lanes.some((l) => box.left >= l.left && box.right <= l.right && box.top >= l.top && box.bottom <= l.bottom) &&
      !obstacles.some((other) => overlaps(box, other))
    )
  })
}
