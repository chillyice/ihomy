// 战绩分享图:1080×1350 竖版海报的纯布局 + Canvas 2D 绘制。
// computeScoreCardLayout 不碰 DOM(单测直接跑);drawScoreCard 只吃调用方传入的文案与颜色,
// 所以各游戏共用同一张版式,配色一律由组件从主题变量读出后传入,本文件不写死任何主题色。
export const CARD_WIDTH = 1080
export const CARD_HEIGHT = 1350

const PAD = 72
const TITLE_FONT = 48
const TITLE_H = 60
const SUB_FONT = 26
const SUB_H = 34
const STATS_H = 132
const STAT_VALUE_FONT = 58
const STAT_LABEL_FONT = 25
const FOOT_FONT = 24
const FOOT_H = 32
const GAP_HEAD = 18
const GAP_BOARD = 40
const GAP_STATS = 56
const GAP_FOOT = 44
const STAT_GAP = 24

const FONT_STACK = 'system-ui, -apple-system, "PingFang SC", "Microsoft YaHei", Roboto, "Helvetica Neue", Arial, sans-serif'

/** Canvas font 串:数字与中文都走系统字体,不引外部字体文件 */
export const canvasFont = (size, weight = 700) => `${weight} ${Math.round(size)}px ${FONT_STACK}`

/**
 * 版式:标题 / 副标题 / 正方形棋盘 / 数据块(横向等分)/ 页脚。
 * 棋盘边长同时吃「内容宽」和「竖向剩余高度」,所以数据块再多也不会压到页脚。
 */
export function computeScoreCardLayout({ gridSize = 4, statsCount = 2, width = CARD_WIDTH, height = CARD_HEIGHT } = {}) {
  const n = Math.max(2, Math.min(12, Math.round(Number(gridSize) || 4)))
  const count = Math.max(1, Math.min(4, Math.round(Number(statsCount) || 1)))

  let y = PAD
  const title = { x: PAD, y, font: TITLE_FONT, height: TITLE_H }
  y += TITLE_H + GAP_HEAD
  const subtitle = { x: PAD, y, font: SUB_FONT, height: SUB_H }
  y += SUB_H + GAP_BOARD

  const statsBottom = height - PAD - FOOT_H - GAP_FOOT
  const boardMaxW = width - PAD * 2
  const boardMaxH = statsBottom - GAP_STATS - STATS_H - y
  const size = Math.max(0, Math.min(boardMaxW, boardMaxH))
  const gap = Math.max(6, Math.round(size * 0.024))
  const board = {
    x: PAD + (boardMaxW - size) / 2,
    y,
    size,
    gap,
    pad: gap,
    radius: Math.round(size * 0.03),
    cell: (size - gap * (n + 1)) / n,
    gridSize: n,
  }

  const statsY = board.y + size + GAP_STATS
  const statW = (width - PAD * 2 - STAT_GAP * (count - 1)) / count
  const stats = Array.from({ length: count }, (_, i) => ({
    x: PAD + i * (statW + STAT_GAP),
    y: statsY,
    w: statW,
    h: STATS_H,
    radius: 22,
    labelFont: STAT_LABEL_FONT,
    valueFont: STAT_VALUE_FONT,
    labelY: statsY + 24,
    valueY: statsY + 58,
  }))

  const footer = { x: width / 2, y: height - PAD - FOOT_H + 6, font: FOOT_FONT, height: FOOT_H }

  return { width, height, pad: PAD, title, subtitle, board, stats, footer }
}

/** #rgb / #rrggbb 加透明度;非 hex(如主题里回退成 rgb())原样返回,至少保证可见 */
export function withAlpha(color, alpha) {
  const hex = String(color ?? '').trim()
  const m = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(hex)
  if (!m) return hex || 'transparent'
  let body = m[1]
  if (body.length === 3)
    body = body
      .split('')
      .map((c) => c + c)
      .join('')
  const num = Number.parseInt(body, 16)
  return `rgba(${(num >> 16) & 255}, ${(num >> 8) & 255}, ${num & 255}, ${alpha})`
}

/** 分数带千分位,列表里读得快 */
export const formatStat = (value) => {
  const num = Number(value)
  return Number.isFinite(num) ? num.toLocaleString('en-US') : String(value ?? '')
}

function roundRect(ctx, x, y, w, h, r) {
  const radius = Math.max(0, Math.min(r, w / 2, h / 2))
  ctx.beginPath()
  ctx.moveTo(x + radius, y)
  ctx.lineTo(x + w - radius, y)
  ctx.arcTo(x + w, y, x + w, y + radius, radius)
  ctx.lineTo(x + w, y + h - radius)
  ctx.arcTo(x + w, y + h, x + w - radius, y + h, radius)
  ctx.lineTo(x + radius, y + h)
  ctx.arcTo(x, y + h, x, y + h - radius, radius)
  ctx.lineTo(x, y + radius)
  ctx.arcTo(x, y, x + radius, y, radius)
  ctx.closePath()
}

function drawBoard(ctx, board, tiles, { colors, tileColor }) {
  const text = colors.text || '#2f2f2f'
  roundRect(ctx, board.x, board.y, board.size, board.size, board.radius)
  ctx.fillStyle = colors.board || withAlpha(text, 0.06)
  ctx.fill()

  const n = board.gridSize
  const step = board.cell + board.gap
  // 空格底
  ctx.fillStyle = withAlpha(text, 0.05)
  for (let r = 0; r < n; r += 1) {
    for (let c = 0; c < n; c += 1) {
      roundRect(ctx, board.x + board.pad + c * step, board.y + board.pad + r * step, board.cell, board.cell, board.cell * 0.08)
      ctx.fill()
    }
  }

  const seen = new Set()
  for (const tile of tiles || []) {
    if (!tile || !Number.isFinite(tile.row) || !Number.isFinite(tile.col)) continue
    // 同一格只画一次(合并动画期间可能存在重叠的落点)
    const key = `${tile.row}:${tile.col}`
    if (seen.has(key)) continue
    seen.add(key)
    const value = Number(tile.value) || 0
    const paint = (typeof tileColor === 'function' ? tileColor(value) : null) || {
      bg: withAlpha(colors.brand || '#6b7f6e', 0.22),
      fg: text,
    }
    const x = board.x + board.pad + tile.col * step
    const y = board.y + board.pad + tile.row * step
    roundRect(ctx, x, y, board.cell, board.cell, board.cell * 0.08)
    ctx.fillStyle = paint.bg
    ctx.fill()

    const label = String(value)
    const ratio = label.length <= 2 ? 0.46 : label.length === 3 ? 0.38 : 0.3
    ctx.fillStyle = paint.fg
    ctx.font = canvasFont(board.cell * ratio, 800)
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(label, x + board.cell / 2, y + board.cell / 2 + board.cell * 0.02)
    ctx.textAlign = 'left'
    ctx.textBaseline = 'top'
  }
}

/**
 * 画整张分享图。colors 由调用方从主题变量取:
 * { bg, card, board, text, secondary, brand }(十六进制色值最稳)。
 */
export function drawScoreCard(
  ctx,
  { layout, title = '', subtitle = '', stats = [], tiles = [], footer = '', colors = {}, tileColor = null },
) {
  const { width, height } = layout
  const text = colors.text || '#2f2f2f'
  const secondary = colors.secondary || '#8a8a8a'
  const brand = colors.brand || '#6b7f6e'

  ctx.save()
  ctx.clearRect(0, 0, width, height)
  ctx.fillStyle = colors.bg || '#f7f4ec'
  ctx.fillRect(0, 0, width, height)

  // 顶部一层品牌色渐隐:海报有光但不抢字
  const glow = ctx.createLinearGradient(0, 0, 0, height * 0.62)
  glow.addColorStop(0, withAlpha(brand, 0.14))
  glow.addColorStop(1, withAlpha(brand, 0))
  ctx.fillStyle = glow
  ctx.fillRect(0, 0, width, height * 0.62)

  ctx.textBaseline = 'top'
  ctx.textAlign = 'left'
  ctx.fillStyle = text
  ctx.font = canvasFont(layout.title.font, 800)
  ctx.fillText(String(title), layout.title.x, layout.title.y)
  ctx.fillStyle = secondary
  ctx.font = canvasFont(layout.subtitle.font, 500)
  ctx.fillText(String(subtitle), layout.subtitle.x, layout.subtitle.y)

  drawBoard(ctx, layout.board, tiles, { colors, tileColor })

  stats.slice(0, layout.stats.length).forEach((stat, i) => {
    const box = layout.stats[i]
    roundRect(ctx, box.x, box.y, box.w, box.h, box.radius)
    // 数据块用品牌色淡底 + 描边:卡片色和页底太接近时也能看清块边界
    ctx.fillStyle = withAlpha(brand, 0.1)
    ctx.fill()
    ctx.strokeStyle = withAlpha(brand, 0.22)
    ctx.lineWidth = 2
    ctx.stroke()

    ctx.fillStyle = secondary
    ctx.font = canvasFont(box.labelFont, 500)
    ctx.fillText(String(stat.label ?? ''), box.x + 28, box.labelY)
    ctx.fillStyle = brand
    ctx.font = canvasFont(box.valueFont, 800)
    ctx.fillText(formatStat(stat.value), box.x + 26, box.valueY)
  })

  if (footer) {
    ctx.fillStyle = withAlpha(text, 0.42)
    ctx.font = canvasFont(layout.footer.font, 500)
    ctx.textAlign = 'center'
    ctx.fillText(String(footer), layout.footer.x, layout.footer.y)
    ctx.textAlign = 'left'
  }
  ctx.restore()
}
