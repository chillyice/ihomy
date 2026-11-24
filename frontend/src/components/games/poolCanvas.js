import { BALL_RADIUS, CUSHIONS, POCKETS, TABLE_HEIGHT, TABLE_WIDTH, tracePoolBoundary } from '@/utils/games/poolGeometry'

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

// Work across the throat and out toward the back of the pocket. The same cut
// rotates into each corner; the two middle pockets have a shorter, narrower well.
function drawPocket(ctx, { x, mouth, throat, outward }) {
  const [a, b] = throat
  const width = Math.hypot(b.x - a.x, b.y - a.y)
  const half = width / 2
  const centerX = (a.x + b.x) / 2
  const centerY = (a.y + b.y) / 2
  const depth = x === W / 2 ? 12.5 : 20
  const shelf = ctx.createLinearGradient((mouth[0].x + mouth[1].x) / 2, (mouth[0].y + mouth[1].y) / 2, centerX, centerY)
  shelf.addColorStop(0, '#286861')
  shelf.addColorStop(0.65, '#20584f')
  shelf.addColorStop(1, '#153e38')
  polygon(
    ctx,
    [mouth[0], a, b, mouth[1]].map((p) => [p.x, p.y]),
    shelf,
  )

  ctx.save()
  ctx.transform((b.x - a.x) / width, (b.y - a.y) / width, outward.x, outward.y, centerX, centerY)
  // An open leather liner follows only the back of the cutout. The front is
  // unobstructed cloth between the two physical cushion noses.
  const liner = ctx.createLinearGradient(0, -1, 0, depth + 5)
  liner.addColorStop(0, '#805d3c')
  liner.addColorStop(0.55, '#b99564')
  liner.addColorStop(1, '#7b563d')
  ctx.beginPath()
  ctx.moveTo(-half - 4, 0)
  ctx.bezierCurveTo(-half - 5, depth + 2, -half * 0.62, depth + 5, 0, depth + 5)
  ctx.bezierCurveTo(half * 0.62, depth + 5, half + 5, depth + 2, half + 4, 0)
  ctx.closePath()
  ctx.fillStyle = liner
  ctx.fill()

  const well = ctx.createLinearGradient(0, -0.5, 0, depth + 1)
  well.addColorStop(0, '#303831')
  well.addColorStop(0.24, '#172522')
  well.addColorStop(0.65, '#0b1718')
  well.addColorStop(1, '#122021')
  ctx.beginPath()
  ctx.moveTo(-half, 0)
  ctx.quadraticCurveTo(0, -1.8, half, 0)
  ctx.bezierCurveTo(half, depth * 0.84, half * 0.67, depth + 0.7, 0, depth + 0.7)
  ctx.bezierCurveTo(-half * 0.67, depth + 0.7, -half, depth * 0.84, -half, 0)
  ctx.fillStyle = well
  ctx.fill()
  ctx.restore()
}

function drawCushion(ctx, [backA, noseA, noseB, backB]) {
  const midX = (noseA.x + noseB.x) / 2
  const midY = (noseA.y + noseB.y) / 2
  const horizontal = noseA.y === noseB.y
  const backX = horizontal ? midX : (backA.x + backB.x) / 2
  const backY = horizontal ? (backA.y + backB.y) / 2 : midY
  const bevel = ctx.createLinearGradient(backX, backY, midX, midY)
  bevel.addColorStop(0, '#194f48')
  bevel.addColorStop(0.28, '#347b6e')
  bevel.addColorStop(0.79, '#347c70')
  bevel.addColorStop(1, '#215d53')
  polygon(
    ctx,
    [backA, noseA, noseB, backB].map((p) => [p.x, p.y]),
    bevel,
  )
  // A continuous narrow edge picks out the pointed noses and both jaw faces.
  ctx.beginPath()
  ctx.moveTo(backA.x, backA.y)
  ctx.lineTo(noseA.x, noseA.y)
  ctx.lineTo(noseB.x, noseB.y)
  ctx.lineTo(backB.x, backB.y)
  ctx.lineWidth = 1.65
  ctx.strokeStyle = '#184b43'
  ctx.stroke()
  ctx.beginPath()
  ctx.moveTo(backA.x, backA.y)
  ctx.lineTo(noseA.x, noseA.y)
  ctx.moveTo(noseB.x, noseB.y)
  ctx.lineTo(backB.x, backB.y)
  ctx.strokeStyle = '#7aa697'
  ctx.lineWidth = 1.35
  ctx.stroke()
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

  for (const pocket of POCKETS) drawPocket(ctx, pocket)
  for (const cushion of CUSHIONS) drawCushion(ctx, cushion)

  for (const flip of [false, true]) {
    ctx.save()
    if (flip) {
      ctx.translate(W, H)
      ctx.rotate(Math.PI)
    }
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
  const boundary = tracePoolBoundary(cue.x, cue.y, dx, dy)
  let distance = boundary?.distance ?? 0
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
    } else if (boundary?.type === 'pocket') {
      disk(ctx, distance, 0, 2.4, '#efe2b6')
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

export function drawPlay(ctx, game, angle, power, showAim = true, placement = null) {
  ctx.clearRect(-PAD, -PAD, W + 2 * PAD, H + 2 * PAD)
  if (game.phase === 'ready' && showAim) {
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
  if (placement) {
    ctx.save()
    ctx.beginPath()
    ctx.arc(placement.x, placement.y, R + 3, 0, TURN)
    ctx.strokeStyle = placement.valid === false ? '#ffae99' : '#f5e6b8'
    ctx.lineWidth = 2
    ctx.setLineDash([4, 3])
    ctx.stroke()
    ctx.restore()
  }
}
