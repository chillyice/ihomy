import { BALL_RADIUS, POCKET_RADIUS, POCKETS, TABLE_HEIGHT, TABLE_WIDTH } from '@/utils/games/pool'

// Canvas coordinates use the engine's playing surface. The extra margin holds the rails.
const PAD = 32
const W = TABLE_WIDTH
const H = TABLE_HEIGHT
const R = BALL_RADIUS
const TURN = Math.PI * 2
const colors = ['#e7ab20', '#3b6ca7', '#cf4942', '#8063a1', '#ed8131', '#258777', '#904450', '#2c3437']

function disk(ctx, x, y, radius, fill) {
  ctx.beginPath()
  ctx.arc(x, y, radius, 0, TURN)
  ctx.fillStyle = fill
  ctx.fill()
}

function polygon(ctx, points, fill) {
  ctx.beginPath()
  ctx.moveTo(...points[0])
  for (const point of points.slice(1)) ctx.lineTo(...point)
  ctx.closePath()
  ctx.fillStyle = fill
  ctx.fill()
}

export function prepareCanvas(canvas, box) {
  const upright = box.height > box.width
  const dpr = Math.min(window.devicePixelRatio || 1, 3)
  canvas.width = Math.max(1, Math.round(box.width * dpr))
  canvas.height = Math.max(1, Math.round(box.height * dpr))
  const ctx = canvas.getContext('2d')
  const sx = canvas.width / ((upright ? H : W) + 2 * PAD)
  const sy = canvas.height / ((upright ? W : H) + 2 * PAD)
  if (upright) ctx.setTransform(0, -sy, sx, 0, PAD * sx, (W + PAD) * sy)
  else ctx.setTransform(sx, 0, 0, sy, PAD * sx, PAD * sy)
  return ctx
}

export function tablePoint(canvas, event) {
  const box = canvas.getBoundingClientRect()
  const fx = (event.clientX - box.left) / box.width
  const fy = (event.clientY - box.top) / box.height
  if (box.height > box.width) return { x: (1 - fy) * (W + 2 * PAD) - PAD, y: fx * (H + 2 * PAD) - PAD }
  return { x: fx * (W + 2 * PAD) - PAD, y: fy * (H + 2 * PAD) - PAD }
}

export function drawTable(ctx) {
  const wood = ctx.createLinearGradient(0, -PAD, 0, H + PAD)
  wood.addColorStop(0, '#805338')
  wood.addColorStop(0.06, '#51362d')
  wood.addColorStop(0.52, '#735039')
  wood.addColorStop(0.94, '#5d3d2c')
  wood.addColorStop(1, '#352820')
  ctx.fillStyle = wood
  ctx.beginPath()
  ctx.roundRect(-PAD + 1, -PAD + 1, W + 2 * PAD - 2, H + 2 * PAD - 2, 24)
  ctx.fill()
  ctx.strokeStyle = '#b3905d'
  ctx.lineWidth = 0.9
  ctx.beginPath()
  ctx.roundRect(-PAD + 5, -PAD + 5, W + 2 * PAD - 10, H + 2 * PAD - 10, 21)
  ctx.stroke()

  const cloth = ctx.createRadialGradient(W * 0.43, H * 0.43, 16, W / 2, H / 2, W * 0.65)
  cloth.addColorStop(0, '#337b77')
  cloth.addColorStop(0.65, '#256962')
  cloth.addColorStop(1, '#17534f')
  ctx.fillStyle = cloth
  ctx.fillRect(0, 0, W, H)
  ctx.strokeStyle = 'rgba(230, 235, 208, .23)'
  ctx.lineWidth = 0.9
  ctx.beginPath()
  ctx.moveTo(W / 4, 0)
  ctx.lineTo(W / 4, H)
  ctx.moveTo(W / 4, H / 2 + 54)
  ctx.arc(W / 4, H / 2, 54, Math.PI / 2, Math.PI * 1.5)
  ctx.stroke()
  disk(ctx, (3 * W) / 4, H / 2, 1.8, 'rgba(245,242,212,.5)')

  // Bevels stop at each pocket mouth so the visible nose matches the rebound line.
  for (const flip of [false, true]) {
    ctx.save()
    if (flip) {
      ctx.translate(W, H)
      ctx.rotate(Math.PI)
    }
    for (const [a, b] of [
      [17, W / 2 - 17],
      [W / 2 + 17, W - 17],
    ]) {
      polygon(
        ctx,
        [
          [a - 8, -14],
          [b + 8, -14],
          [b, 0],
          [a, 0],
        ],
        flip ? '#195954' : '#2c756b',
      )
      polygon(
        ctx,
        [
          [a, -2],
          [b, -2],
          [b, 0],
          [a, 0],
        ],
        flip ? '#4a8d7f' : '#194f49',
      )
    }
    polygon(
      ctx,
      [
        [-14, 9],
        [0, 17],
        [0, H - 17],
        [-14, H - 9],
      ],
      flip ? '#317b70' : '#22655b',
    )
    for (let i = 1; i < 8; i++) {
      if (i === 4) continue
      const x = (W * i) / 8
      polygon(
        ctx,
        [
          [x, -24],
          [x + 3.6, -21.4],
          [x, -18.8],
          [x - 3.6, -21.4],
        ],
        '#e5c992',
      )
    }
    for (let i = 1; i < 4; i++) {
      const y = (H * i) / 4
      polygon(
        ctx,
        [
          [-24, y],
          [-21.4, y - 3.6],
          [-18.8, y],
          [-21.4, y + 3.6],
        ],
        '#e5c992',
      )
    }
    ctx.restore()
  }
  for (const p of POCKETS) {
    const brass = ctx.createLinearGradient(p.x, p.y - 23, p.x + 8, p.y + 23)
    brass.addColorStop(0, '#e1c790')
    brass.addColorStop(0.48, '#977b4c')
    brass.addColorStop(1, '#785c3c')
    disk(ctx, p.x, p.y, POCKET_RADIUS + 4, brass)
    disk(ctx, p.x, p.y, POCKET_RADIUS + 1.7, '#573f2e')
    const well = ctx.createRadialGradient(p.x, p.y - 4, 2, p.x, p.y, POCKET_RADIUS)
    well.addColorStop(0, '#11191a')
    well.addColorStop(0.75, '#172021')
    well.addColorStop(1, '#333638')
    disk(ctx, p.x, p.y, POCKET_RADIUS, well)
  }
}

function drawBall(ctx, ball, upright) {
  const { x, y, id } = ball
  const hue = id ? colors[(id - 1) % 8] : '#f6f1db'
  ctx.save()
  ctx.translate(x, y)
  if (upright) ctx.rotate(Math.PI / 2)
  disk(ctx, 1.3, 2.2, R + 0.2, 'rgba(2, 25, 28, .4)')
  disk(ctx, 0, 0, R, id > 8 ? '#f5efdd' : hue)
  ctx.clip()
  if (id > 8) {
    ctx.fillStyle = hue
    ctx.fillRect(-R, -R * 0.59, 2 * R, R * 1.18)
  }
  if (id) {
    disk(ctx, 0, 0, 4.9, '#fcf5e1')
    ctx.fillStyle = '#263036'
    ctx.font = 'bold 7.6px sans-serif'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(String(id), 0, 0.5)
  }
  const gloss = ctx.createRadialGradient(-3.3, -4.4, 0.8, -1.7, -2.2, R + 2.6)
  gloss.addColorStop(0, 'rgba(255,255,255,.62)')
  gloss.addColorStop(0.32, 'rgba(255,255,255,.04)')
  gloss.addColorStop(0.72, 'rgba(0,0,0,.02)')
  gloss.addColorStop(1, 'rgba(15,26,29,.48)')
  disk(ctx, 0, 0, R, gloss)
  ctx.restore()
}

function drawAim(ctx, cue, balls, angle, power) {
  const dx = Math.cos(angle)
  const dy = Math.sin(angle)
  const wallX = Math.abs(dx) < 1e-8 ? Infinity : ((dx > 0 ? W - R : R) - cue.x) / dx
  const wallY = Math.abs(dy) < 1e-8 ? Infinity : ((dy > 0 ? H - R : R) - cue.y) / dy
  let distance = Math.min(wallX, wallY)
  let target
  for (const ball of balls) {
    if (!ball.id || ball.pocketed) continue
    const bx = ball.x - cue.x
    const by = ball.y - cue.y
    const projection = bx * dx + by * dy
    const offset = bx * dy - by * dx
    if (projection <= 0 || Math.abs(offset) > 2 * R) continue
    const contact = Math.max(0, projection - Math.sqrt((2 * R) ** 2 - offset ** 2))
    if (contact < distance) {
      distance = contact
      target = ball
    }
  }
  ctx.save()
  ctx.translate(cue.x, cue.y)
  ctx.rotate(angle)
  if (distance > R) {
    ctx.lineWidth = 1.4
    ctx.strokeStyle = 'rgba(246,246,218,.65)'
    ctx.setLineDash([3, 6])
    ctx.beginPath()
    ctx.moveTo(R + 2, 0)
    ctx.lineTo(distance, 0)
    ctx.stroke()
    ctx.setLineDash([])
    if (target) {
      ctx.beginPath()
      ctx.arc(distance, 0, R, 0, TURN)
      ctx.stroke()
    }
  }
  ctx.translate(-R - 4 - power * 0.12, 0)
  polygon(
    ctx,
    [
      [0, -1.8],
      [0, 1.8],
      [-172, 4.4],
      [-172, -4.4],
    ],
    '#d7b480',
  )
  polygon(
    ctx,
    [
      [-116, -3.4],
      [-116, 3.4],
      [-172, 4.4],
      [-172, -4.4],
    ],
    '#593a2c',
  )
  ctx.fillStyle = '#f6e5bb'
  ctx.fillRect(-6, -1.9, 5, 3.8)
  ctx.fillStyle = '#6595aa'
  ctx.fillRect(-1.5, -1.8, 2.5, 3.6)
  ctx.restore()
}

export function drawPlay(ctx, game, angle, power) {
  ctx.clearRect(-PAD, -PAD, W + 2 * PAD, H + 2 * PAD)
  if (game.phase === 'ready') {
    ctx.save()
    ctx.beginPath()
    ctx.roundRect(-PAD + 1, -PAD + 1, W + 2 * PAD - 2, H + 2 * PAD - 2, 24)
    ctx.clip()
    drawAim(ctx, game.balls[0], game.balls, angle, power)
    ctx.restore()
  }
  const upright = ctx.canvas.height > ctx.canvas.width
  for (const ball of game.balls) {
    if (!ball.pocketed) drawBall(ctx, ball, upright)
  }
}
