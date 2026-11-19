// 立体麻将的 three.js 牌桌场景:主视角透视相机 + 圆角立体牌 + 光照阴影。
// 分层:布局数学(座位坐标系/手牌/牌河/牌墙)全是纯函数(可单测,不依赖 DOM);
// createMahjong3dTable(container) 才创建 renderer/材质/网格。规则内核在 utils/games/mahjong.js。
import * as THREE from 'three'
import { RoundedBoxGeometry } from 'three/addons/geometries/RoundedBoxGeometry.js'
import { mahjongTileSrc } from './mahjongTiles'
import { MAHJONG_TILE_COUNT } from './mahjong'

// ---------- 尺寸常量(世界单位;牌立放:宽 X、高 Y、厚 Z) ----------
// 桌面按「最坏局面也放得下」反推:手牌 13+摸牌+四组副露(暗杠 4 张)同排、
// 牌河风车形(远行收列,四家互不相交)都对齐 15.5 的方桌;相机再按视口取景,见 fitCameraDistance。
export const TILE = { W: 0.78, H: 1.07, D: 0.52, PITCH: 0.85, LIE_PITCH: 0.66 }
export const TABLE = { W: 15.5, D: 15.5 }
/** 派生布局:手牌行贴桌沿,墙圈在其内侧,中央区只留风位牌与骰子 */
export const LAYOUT = { edgeGap: 0.95, wallV: 0.85, centerR: 0.72 }
/** 整行(手牌+摸牌+副露)排布参数:EDGE=行端到桌沿留白,其余为各类间隙 */
export const ROW = { EDGE: 0.4, DRAWN_GAP: 0.4, MELD_GAP: 0.5, MELD_GROUP_GAP: 0.22, GAP_MIN: 0.08 }

/** 座位坐标系:seat0=我(南,+Z 屏幕下方)、1=下家(东 +X 屏幕右)、2=对家(北)、3=上家(西)。
 *  row = 手牌行方向,face = 该座位朝外方向(牌面朝牌主);anchor = 手牌行中心。 */
export function seatFrame(seat) {
  const yaw = [0, Math.PI / 2, Math.PI, -Math.PI / 2][((seat % 4) + 4) % 4]
  return {
    yaw,
    anchor: { x: Math.sin(yaw) * (TABLE.W / 2 - LAYOUT.edgeGap), z: Math.cos(yaw) * (TABLE.D / 2 - LAYOUT.edgeGap) },
    row: { x: Math.cos(yaw), z: -Math.sin(yaw) },
    face: { x: Math.sin(yaw), z: Math.cos(yaw) },
  }
}

/** 局部 (u=沿行, v=向中心) → 世界坐标 */
function toWorld(seat, u, v) {
  const f = seatFrame(seat)
  return { x: f.anchor.x + f.row.x * u - f.face.x * v, z: f.anchor.z + f.row.z * u - f.face.z * v }
}

/** 手牌第 i 张(共 count)的行内偏移:按持有数居中 */
export function handTileU(i, count) {
  return (i - (count - 1) / 2) * TILE.PITCH
}

/** 手牌行右端(含牌宽) */
function handEndU(count) {
  return ((count - 1) / 2) * TILE.PITCH + TILE.W / 2
}

/** 摸牌行内偏移:手牌右端外留一个明显间隙 */
export function drawnTileU(count) {
  return handEndU(count) + ROW.DRAWN_GAP + TILE.W / 2
}

/**
 * 整行(手牌+摸牌+副露)排布计划。手牌居中,摸牌/副露向右排;
 * 超预算先压组间 gap,还不够就整行居中并等比压牌距(下限 = 牌宽+0.01,牌永远不相交)。
 * 副露越多手牌越少,常规局手牌行总宽守恒,不会触发压缩。
 */
export function rowPlan(handCount, drawnPresent, meldSizes = []) {
  const handHalf = (Math.max(0, handCount - 1) * TILE.PITCH + TILE.W) / 2
  const drawnEdge = drawnPresent ? handHalf + ROW.DRAWN_GAP + TILE.W : handHalf
  const meldStart = meldSizes.length ? drawnEdge + ROW.MELD_GAP : drawnEdge
  const tiles = meldSizes.reduce((sum, n) => sum + n, 0)
  let gap = meldSizes.length > 1 ? ROW.MELD_GROUP_GAP : 0
  let pitch = TILE.PITCH
  let meldEnd = meldStart + tiles * pitch + Math.max(0, meldSizes.length - 1) * gap
  const budget = TABLE.W / 2 - ROW.EDGE
  if (meldEnd > budget) {
    const groups = Math.max(1, meldSizes.length - 1)
    const gapRoom = groups * (gap - ROW.GAP_MIN)
    if (gapRoom > 0) {
      const use = Math.min(gapRoom, meldEnd - budget)
      gap -= use / groups
      meldEnd -= use
    }
  }
  if (meldEnd > budget) {
    // 极端局(如四杠):整行必须压牌距才放得下
    const fixed =
      (drawnPresent ? ROW.DRAWN_GAP : 0) + (meldSizes.length ? ROW.MELD_GAP : 0) + Math.max(0, meldSizes.length - 1) * ROW.GAP_MIN
    const n = handCount + (drawnPresent ? 1 : 0) + tiles
    pitch = Math.max(TILE.W + 0.01, (TABLE.W - 0.3 - fixed) / Math.max(1, n))
    gap = meldSizes.length > 1 ? ROW.GAP_MIN : 0
    meldEnd = meldStart + tiles * pitch + Math.max(0, meldSizes.length - 1) * gap
  }
  // 常规局不位移;超预算时整行居中,两端贴桌沿(仍在呢面内)
  const shift = meldEnd <= budget ? 0 : -(meldEnd - handHalf) / 2
  return { shift, pitch, handHalf, drawnU: drawnTileU(handCount), meldStart, meldGap: gap }
}

export function handTileTransform(seat, i, count, plan = null) {
  const scale = plan ? plan.pitch / TILE.PITCH : 1
  const u = handTileU(i, count) * scale + (plan?.shift || 0)
  return { ...toWorld(seat, u, 0), yaw: seatFrame(seat).yaw }
}

export function drawnTileTransform(seat, count, plan = null) {
  const u = drawnTileU(count) + (plan?.shift || 0)
  return { ...toWorld(seat, u, 0), yaw: seatFrame(seat).yaw }
}

/** 牌河:行 0 紧贴本方墙内沿、向中心逐行生长;远端行收列,四家牌河互不相交(风车形) */
export const RIVER_COLS = 6
export const RIVER_ROWS = 5
export function riverRowCols(row) {
  return [6, 6, 6, 4, 2][row] || 2
}
export function riverTileTransform(seat, index) {
  const row = Math.min(Math.floor(index / RIVER_COLS), RIVER_ROWS - 1)
  const cols = riverRowCols(row)
  const col = Math.min(index % RIVER_COLS, cols - 1)
  const v = LAYOUT.wallV + TILE.D + 0.12 + row * TILE.LIE_PITCH
  const u = (col - (cols - 1) / 2) * TILE.PITCH
  return { ...toWorld(seat, u, v), yaw: seatFrame(seat).yaw }
}

/** 副露平铺:groupStartU 为该组首张的行内偏移(由 rowPlan 的 meldStart 累计),v 与手牌同行 */
export function meldTileTransform(seat, groupStartU, tileIndex, pitch = TILE.PITCH) {
  const u = groupStartU + tileIndex * pitch
  return { ...toWorld(seat, u, 0), yaw: seatFrame(seat).yaw }
}

/** 牌墙:按剩余张数均分四边(余数从 seat0 起),每边两层堆叠 */
export function wallStackCounts(wallLeft) {
  const base = Math.floor(Math.max(0, wallLeft) / 4)
  const rem = Math.max(0, wallLeft) % 4
  return [0, 1, 2, 3].map((seat) => {
    const count = base + (seat < rem ? 1 : 0)
    const stacks = Math.ceil(count / 2)
    return { seat, count, stacks, tops: Array.from({ length: stacks }, (_, s) => s * 2 + 1 < count) }
  })
}

/** 牌墙第 stackIndex 摞的位置(两层摞);maxStacks 给定时按固定原点排布,消耗不滑动 */
export function wallStackTransform(seat, stackIndex, stackCount, maxStacks = stackCount) {
  const u = (stackIndex - (maxStacks - 1) / 2) * (TILE.W + 0.06)
  return { ...toWorld(seat, u, LAYOUT.wallV), yaw: seatFrame(seat).yaw }
}

/** 牌墙最大摞数:开局 136 − 4×13(起手)= 84 张,每边 21 张两层摞 = 11 摞;消耗时墙面不滑动 */
const WALL_MAX_STACKS = Math.ceil((MAHJONG_TILE_COUNT - 4 * 13) / 4 / 2)

/** 牌墙全部牌位:两层摞(底层贴桌面,顶层抬高 TILE.D+缝隙),固定原点、按剩余数从行尾逐张消失 */
export function wallTiles(wallLeft, maxStacks = WALL_MAX_STACKS) {
  const out = []
  for (const side of wallStackCounts(wallLeft)) {
    const ms = Math.max(maxStacks, side.stacks)
    for (let s = 0; s < side.stacks; s++) {
      const pos = wallStackTransform(side.seat, s, side.stacks, ms)
      out.push({ ...pos, y: TILE.D / 2 })
      if (side.tops[s]) out.push({ ...pos, y: TILE.D + 0.005 })
    }
  }
  return out
}

/** 骰子与风位牌的桌面位置(中央区) */
export function diceTransform(i) {
  return { x: i === 0 ? -0.45 : 0.45, z: 0 }
}
export const WIND_KINDS = [27, 28, 29, 30] // east/south/west/north 的 kind
export function windPlateTransform(seat) {
  const f = seatFrame(seat)
  return { x: f.face.x * LAYOUT.centerR, z: f.face.z * LAYOUT.centerR, yaw: f.yaw }
}

// ---------- 场景(惰性创建;仅被 Mahjong3d.vue 懒加载引用) ----------

/** 用 <img> 把 SVG 牌面画进 canvas(显式尺寸,规避无 width/height 的 SVG 光栅化差异) */
function svgToCanvas(src, w, h, bg) {
  return new Promise((resolve) => {
    const canvas = document.createElement('canvas')
    canvas.width = w
    canvas.height = h
    const ctx = canvas.getContext('2d')
    if (bg) {
      ctx.fillStyle = bg
      ctx.fillRect(0, 0, w, h)
    }
    if (!src) {
      resolve(canvas)
      return
    }
    const img = new Image()
    img.onload = () => {
      ctx.drawImage(img, 0, 0, w, h)
      resolve(canvas)
    }
    img.onerror = () => resolve(canvas)
    img.src = src
  })
}

/** 牌背纹理:深绿底 + 白色回形纹(纯 canvas 画,不引素材) */
function backCanvas() {
  const w = 128
  const h = 176
  const canvas = document.createElement('canvas')
  canvas.width = w
  canvas.height = h
  const ctx = canvas.getContext('2d')
  ctx.fillStyle = '#2a6a58'
  ctx.fillRect(0, 0, w, h)
  ctx.strokeStyle = 'rgba(255,255,255,0.5)'
  ctx.lineWidth = 5
  ctx.strokeRect(12, 12, w - 24, h - 24)
  ctx.strokeStyle = 'rgba(255,255,255,0.28)'
  ctx.lineWidth = 3
  ctx.strokeRect(24, 24, w - 48, h - 48)
  ctx.fillStyle = 'rgba(255,255,255,0.22)'
  ctx.beginPath()
  ctx.arc(w / 2, h / 2, 16, 0, Math.PI * 2)
  ctx.fill()
  return canvas
}

/** 骰子 N 点面纹理(3×3 点阵) */
const DIE_PIPS = {
  1: [[1, 1]],
  2: [
    [0, 0],
    [2, 2],
  ],
  3: [
    [0, 0],
    [1, 1],
    [2, 2],
  ],
  4: [
    [0, 0],
    [0, 2],
    [2, 0],
    [2, 2],
  ],
  5: [
    [0, 0],
    [0, 2],
    [1, 1],
    [2, 0],
    [2, 2],
  ],
  6: [
    [0, 0],
    [0, 2],
    [1, 0],
    [1, 2],
    [2, 0],
    [2, 2],
  ],
}
function dieCanvas(n) {
  const s = 128
  const canvas = document.createElement('canvas')
  canvas.width = s
  canvas.height = s
  const ctx = canvas.getContext('2d')
  ctx.fillStyle = '#fbf7ea'
  ctx.fillRect(0, 0, s, s)
  ctx.fillStyle = '#31251a'
  for (const [r, c] of DIE_PIPS[n] || []) {
    ctx.beginPath()
    ctx.arc(s / 2 + (c - 1) * 34, s / 2 + (r - 1) * 34, 11, 0, Math.PI * 2)
    ctx.fill()
  }
  return canvas
}

const IVORY = '#faf6ea'
const CAM_FOV = 40
const CAMERA_PRESETS = {
  main: { pos: [0, 8.9, 10.6], look: [0, 0, 0.55] },
  top: { pos: [0, 16.2, 0.02], look: [0, 0, 0] },
}

/** 场景内容包围盒 8 角点(木沿外沿 + 立牌高度):相机取景以它为准 */
export function contentCorners() {
  const hx = TABLE.W / 2 + 0.45
  const hz = TABLE.D / 2 + 0.45
  const corners = []
  for (const y of [-0.4, TILE.H + 0.2]) {
    for (const x of [-hx, hx]) {
      for (const z of [-hz, hz]) corners.push([x, y, z])
    }
  }
  return corners
}

/** 给定距离下内容盒角点的最大 |NDC|(解析投影,纯函数,供二分取景与单测) */
export function maxNdc(preset, distance, aspect, corners, fov = CAM_FOV) {
  const [lx, ly, lz] = preset.look
  const dx = preset.pos[0] - lx
  const dy = preset.pos[1] - ly
  const dz = preset.pos[2] - lz
  const len = Math.hypot(dx, dy, dz) || 1
  const dir = [dx / len, dy / len, dz / len]
  const eye = [lx + dir[0] * distance, ly + dir[1] * distance, lz + dir[2] * distance]
  const fwd = [-dir[0], -dir[1], -dir[2]]
  // right = normalize(cross(fwd, up));top 视角 forward 近似 -Y,叉积近零时兜底取 X 轴
  let rx = -fwd[2]
  const ry = 0
  let rz = fwd[0]
  const rl = Math.hypot(rx, ry, rz)
  if (rl < 1e-5) {
    rx = 1
    rz = 0
  } else {
    rx /= rl
    rz /= rl
  }
  const ux = ry * fwd[2] - rz * fwd[1]
  const uy = rz * fwd[0] - rx * fwd[2]
  const uz = rx * fwd[1] - ry * fwd[0]
  const tanV = Math.tan(((fov / 2) * Math.PI) / 180)
  let max = 0
  for (const [px, py, pz] of corners) {
    const wx = px - eye[0]
    const wy = py - eye[1]
    const wz = pz - eye[2]
    const depth = wx * fwd[0] + wy * fwd[1] + wz * fwd[2]
    if (depth < 0.1) return Infinity
    const nx = (wx * rx + wy * ry + wz * rz) / (depth * tanV * aspect)
    const ny = (wx * ux + wy * uy + wz * uz) / (depth * tanV)
    max = Math.max(max, Math.abs(nx), Math.abs(ny))
  }
  return max
}

/** 相机取景:沿预设机位方向二分出「内容盒完整进入视锥」的距离(竖屏窄窗自动拉远) */
export function fitCameraDistance(preset, aspect, corners, margin = 0.94) {
  let lo = 4
  let hi = 160
  for (let i = 0; i < 24; i += 1) {
    const mid = (lo + hi) / 2
    if (maxNdc(preset, mid, aspect, corners) > margin) lo = mid
    else hi = mid
  }
  return hi
}

export function createMahjong3dTable(container, opts = {}) {
  const dprCap = opts.dprCap || (Math.min(window.innerWidth, window.innerHeight) < 820 ? 1.5 : 2)
  const renderer = new THREE.WebGLRenderer({ antialias: true })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, dprCap))
  renderer.setSize(container.clientWidth || 640, container.clientHeight || 400)
  renderer.shadowMap.enabled = true
  renderer.shadowMap.type = THREE.PCFSoftShadowMap
  container.appendChild(renderer.domElement)
  renderer.domElement.style.display = 'block'
  renderer.domElement.style.width = '100%'
  renderer.domElement.style.height = '100%'

  const scene = new THREE.Scene()
  scene.background = new THREE.Color('#22343c')
  const camera = new THREE.PerspectiveCamera(CAM_FOV, (container.clientWidth || 640) / (container.clientHeight || 400), 0.1, 300)
  let viewName = 'main'
  const lookTarget = new THREE.Vector3(...CAMERA_PRESETS.main.look)
  // 取景:沿当前预设机位方向,把内容盒完整放进视锥(竖屏/窄窗自动拉远)
  const applyCamera = () => {
    const preset = CAMERA_PRESETS[viewName] || CAMERA_PRESETS.main
    const dist = fitCameraDistance(preset, camera.aspect, contentCorners())
    const look = new THREE.Vector3(...preset.look)
    const dir = new THREE.Vector3(...preset.pos).sub(look).normalize()
    camera.position.copy(look).addScaledVector(dir, dist)
    camera.lookAt(look)
    lookTarget.copy(look)
  }
  applyCamera()

  // 光照:半球环境 + 主方向光(带柔阴影)
  scene.add(new THREE.HemisphereLight('#fff6e4', '#31473c', 1.05))
  const sun = new THREE.DirectionalLight('#fff2dc', 1.9)
  sun.position.set(4.5, 12, 5)
  sun.castShadow = true
  sun.shadow.mapSize.set(1024, 1024)
  sun.shadow.camera.left = -10
  sun.shadow.camera.right = 10
  sun.shadow.camera.top = 10
  sun.shadow.camera.bottom = -10
  sun.shadow.radius = 3
  scene.add(sun)

  // 桌面:呢面圆角板 + 木色沿
  const felt = new THREE.Mesh(
    new RoundedBoxGeometry(TABLE.W, 0.5, TABLE.D, 3, 0.32),
    new THREE.MeshStandardMaterial({ color: '#2f6e52', roughness: 0.92 }),
  )
  felt.position.y = -0.25
  felt.receiveShadow = true
  scene.add(felt)
  const rim = new THREE.Mesh(
    new RoundedBoxGeometry(TABLE.W + 0.9, 0.7, TABLE.D + 0.9, 3, 0.34),
    new THREE.MeshStandardMaterial({ color: '#6d4a2a', roughness: 0.7 }),
  )
  rim.position.y = -0.42
  rim.receiveShadow = true
  scene.add(rim)

  // 最新弃牌的金色高亮圈
  const latestRing = new THREE.Mesh(
    new THREE.RingGeometry(0.52, 0.68, 32),
    new THREE.MeshBasicMaterial({ color: '#efca83', transparent: true, opacity: 0.9, side: THREE.DoubleSide }),
  )
  latestRing.rotation.x = -Math.PI / 2
  latestRing.position.y = 0.02
  latestRing.visible = false
  scene.add(latestRing)

  // ---------- 牌材质与几何 ----------
  const tileGeo = new RoundedBoxGeometry(TILE.W, TILE.H, TILE.D, 2, 0.09)
  const ivoryMat = new THREE.MeshStandardMaterial({ color: IVORY, roughness: 0.5 })
  const backMat = new THREE.MeshStandardMaterial({ map: new THREE.CanvasTexture(backCanvas()), roughness: 0.6 })
  const faceMats = new Map() // kind -> Promise<Material>

  async function faceMaterial(kind) {
    if (faceMats.has(kind)) return faceMats.get(kind)
    const task = svgToCanvas(mahjongTileSrc(kind), 256, 352, IVORY).then((canvas) => {
      const tex = new THREE.CanvasTexture(canvas)
      tex.colorSpace = THREE.SRGBColorSpace
      tex.anisotropy = 4
      return new THREE.MeshStandardMaterial({ map: tex, roughness: 0.5 })
    })
    faceMats.set(kind, task)
    return task
  }
  /** 六面材质数组:[+X, −X, +Y, −Y, +Z(正面), −Z(背面)] */
  async function tileMaterials(kind) {
    const face = kind == null ? ivoryMat : await faceMaterial(kind)
    return [ivoryMat, ivoryMat, ivoryMat, ivoryMat, face, backMat]
  }

  // ---------- 网格管理 ----------
  const groups = {
    hand: new THREE.Group(),
    opponents: new THREE.Group(),
    river: new THREE.Group(),
    melds: new THREE.Group(),
    wall: new THREE.Group(),
    center: new THREE.Group(),
  }
  for (const g of Object.values(groups)) scene.add(g)
  const handMeshes = new Map() // tileId -> mesh(拾取用)
  const tweens = []
  let disposed = false

  function addTile(parent, kind, { x, y = 0, z, yaw }, mode /* stand | lieUp | lieDown */, tileId = null) {
    const mesh = new THREE.Mesh(tileGeo, null)
    mesh.castShadow = true
    mesh.receiveShadow = true
    mesh.position.y = mode === 'stand' ? TILE.H / 2 : TILE.D / 2
    if (mode === 'lieUp') mesh.rotation.x = -Math.PI / 2
    if (mode === 'lieDown') mesh.rotation.x = Math.PI / 2
    const holder = new THREE.Group()
    holder.rotation.y = yaw
    // y 抬升由调用方传入(牌墙顶层摞在底层之上);mesh 的 y 相对 holder 再抬半厚
    holder.position.set(x, y, z)
    holder.add(mesh)
    tileMaterials(kind).then((mats) => {
      if (!disposed) mesh.material = mats
    })
    if (tileId != null) {
      mesh.userData.tileId = tileId
      handMeshes.set(tileId, mesh)
    }
    parent.add(holder)
    return holder
  }

  function clearGroup(g) {
    for (const child of [...g.children]) {
      g.remove(child)
    }
  }

  // 每席整行缓存:手牌/摸牌/副露必须一起排(rowPlan 要知道三者才算得出整行),改动任一即整行重排
  const rowCache = [0, 1, 2, 3].map(() => ({ hand: [], drawn: null, melds: [] }))
  function relayoutRow(seat) {
    const cache = rowCache[seat]
    const plan = rowPlan(
      cache.hand.length,
      !!cache.drawn,
      cache.melds.map((m) => m.kinds.length),
    )
    if (seat === 0) {
      clearGroup(groups.hand)
      handMeshes.clear()
      cache.hand.forEach((t, i) => {
        const pos = handTileTransform(0, i, cache.hand.length, plan)
        addTile(groups.hand, t.kind, pos, 'stand', t.id)
      })
      if (cache.drawn) {
        const pos = drawnTileTransform(0, cache.hand.length, plan)
        addTile(groups.hand, cache.drawn.kind, pos, 'stand', cache.drawn.id)
      }
    } else {
      const key = 'opp' + seat
      const old = groups.opponents.getObjectByName(key)
      if (old) groups.opponents.remove(old)
      const g = new THREE.Group()
      g.name = key
      cache.hand.forEach((t, i) => {
        const pos = handTileTransform(seat, i, cache.hand.length, plan)
        addTile(g, t.kind, pos, 'stand')
      })
      groups.opponents.add(g)
    }
    // 副露与手牌同行,随 plan 一起位移/压缩
    const mkey = 'meld' + seat
    const mold = groups.melds.getObjectByName(mkey)
    if (mold) groups.melds.remove(mold)
    const mg = new THREE.Group()
    mg.name = mkey
    let startU = plan.meldStart + plan.shift
    for (const m of cache.melds) {
      const kinds = m.dark ? [null, null, null, null] : m.kinds
      kinds.forEach((kind, i) => {
        const pos = meldTileTransform(seat, startU, i, plan.pitch)
        addTile(mg, kind, pos, m.dark ? 'lieDown' : 'lieUp')
      })
      startU += kinds.length * plan.pitch + plan.meldGap
    }
    groups.melds.add(mg)
  }

  // ---------- 对外 API ----------
  const api = {
    /** 我方手牌:tiles [{id, kind}];drawnKind 摸牌单放右侧(与副露同一行,整行重排) */
    setHand(tiles, drawnId = null, drawnKind = null) {
      rowCache[0].hand = tiles.map((t) => ({ id: t.id, kind: t.kind }))
      rowCache[0].drawn = drawnKind != null ? { id: drawnId ?? 'drawn', kind: drawnKind } : null
      relayoutRow(0)
    },
    /** 他家手牌(只见牌背) */
    setOpponentHand(seat, count) {
      rowCache[seat].hand = Array.from({ length: count }, () => ({ id: null, kind: null }))
      relayoutRow(seat)
    },
    /** 牌河:kinds 数组,latestIndex 高亮抬升 */
    setRiver(seat, kinds, latestIndex = -1) {
      const key = 'river' + seat
      let g = groups.river.getObjectByName(key)
      if (g) groups.river.remove(g)
      g = new THREE.Group()
      g.name = key
      kinds.forEach((kind, i) => {
        const pos = riverTileTransform(seat, i)
        const holder = addTile(g, kind, pos, 'lieUp')
        if (i === latestIndex) {
          holder.position.y = 0.24
          latestRing.position.set(pos.x, 0.03, pos.z)
          latestRing.visible = true
        }
      })
      if (latestIndex < 0 || !kinds.length) latestRing.visible = false
      groups.river.add(g)
    },
    /** 副露:groups [{dark, kinds:[kind...]}];与手牌同行,整行重排 */
    setMelds(seat, meldGroups) {
      rowCache[seat].melds = (meldGroups || []).map((m) => ({
        dark: !!m.dark,
        kinds: m.dark ? [null, null, null, null] : [...m.kinds],
      }))
      relayoutRow(seat)
    },
    /** 牌墙:按剩余张数重建四边两层堆(固定原点,从行尾逐张消失) */
    setWall(wallLeft) {
      clearGroup(groups.wall)
      for (const t of wallTiles(wallLeft)) {
        addTile(groups.wall, null, t, 'lieDown')
      }
    },
    /** 双骰:掷骰动画(随机翻滚后停到 d1/d2) */
    setDice(d1, d2, animate = false) {
      clearGroup(groups.center)
      const dieGeo = new RoundedBoxGeometry(0.56, 0.56, 0.56, 2, 0.1)
      ;[d1, d2].forEach(async (n, i) => {
        const mats = []
        for (let f = 1; f <= 6; f++) {
          const tex = new THREE.CanvasTexture(dieCanvas(f))
          tex.colorSpace = THREE.SRGBColorSpace
          mats.push(new THREE.MeshStandardMaterial({ map: tex, roughness: 0.4 }))
        }
        // 材质顺序 [+X,−X,+Y,−Y,+Z,−Z]:让顶面(+Y)显示 n
        const order = {
          1: [2, 5, 1, 6, 3, 4],
          2: [1, 6, 2, 5, 3, 4],
          3: [1, 6, 3, 4, 2, 5],
          4: [1, 6, 4, 3, 2, 5],
          5: [1, 6, 5, 2, 3, 4],
          6: [5, 2, 6, 1, 3, 4],
        }[n] || [1, 6, 3, 4, 2, 5]
        const arranged = order.map((v) => mats[v - 1])
        const die = new THREE.Mesh(dieGeo, arranged)
        die.castShadow = true
        const pos = diceTransform(i)
        die.position.set(pos.x, 0.28, pos.z)
        groups.center.add(die)
        if (animate) {
          // 掷骰:随机翻滚两圈后停正(顶面即点数)
          const spin = { x: Math.PI * (3 + Math.random() * 2), y: Math.PI * (3 + Math.random() * 2) }
          tweens.push({
            t: 0,
            dur: 700,
            tick(p) {
              die.rotation.x = spin.x * (1 - p)
              die.rotation.y = spin.y * (1 - p)
            },
          })
        }
      })
    },
    /** 风位牌(小牌立在中央四边,庄家金圈) */
    async setWinds(dealerSeat = 0) {
      const key = 'winds'
      let g = groups.center.getObjectByName(key)
      if (g) groups.center.remove(g)
      g = new THREE.Group()
      g.name = key
      for (let seat = 0; seat < 4; seat++) {
        const kind = WIND_KINDS[seat]
        const pos = windPlateTransform(seat)
        const holder = addTile(g, kind, pos, 'stand')
        holder.scale.set(0.55, 0.55, 0.55)
        if (seat === dealerSeat) {
          const ring = new THREE.Mesh(
            new THREE.RingGeometry(0.44, 0.56, 32),
            new THREE.MeshBasicMaterial({ color: '#efca83', side: THREE.DoubleSide }),
          )
          ring.rotation.x = -Math.PI / 2
          ring.position.set(pos.x, 0.02, pos.z)
          g.add(ring)
        }
      }
      groups.center.add(g)
    },
    /** 拾取我方手牌:返回 tileId 或 null */
    pickHandTile(clientX, clientY) {
      const rect = renderer.domElement.getBoundingClientRect()
      const ndc = new THREE.Vector2(((clientX - rect.left) / rect.width) * 2 - 1, -((clientY - rect.top) / rect.height) * 2 + 1)
      const ray = new THREE.Raycaster()
      ray.setFromCamera(ndc, camera)
      const hits = ray.intersectObjects([...handMeshes.values()], false)
      return hits.length ? (hits[0].object.userData.tileId ?? null) : null
    },
    /** hover 抬起提示:返回当前指向 tileId(由页面决定是否置 hover 态) */
    hoverHandTile(clientX, clientY) {
      return api.pickHandTile(clientX, clientY)
    },
    setHover(tileId) {
      for (const [id, mesh] of handMeshes) {
        mesh.position.y = TILE.H / 2 + (id === tileId ? 0.16 : 0)
      }
    },
    setView(name) {
      viewName = name
      const preset = CAMERA_PRESETS[name] || CAMERA_PRESETS.main
      const dist = fitCameraDistance(preset, camera.aspect, contentCorners())
      const look = new THREE.Vector3(...preset.look)
      const dir = new THREE.Vector3(...preset.pos).sub(look).normalize()
      const toPos = look.clone().addScaledVector(dir, dist)
      const fromPos = camera.position.clone()
      const fromLook = lookTarget.clone()
      tweens.push({
        t: 0,
        dur: 450,
        tick(p) {
          camera.position.lerpVectors(fromPos, toPos, p)
          lookTarget.lerpVectors(fromLook, look, p)
          camera.lookAt(lookTarget)
        },
      })
    },
    getView: () => viewName,
    /** 取景调试信息(冒烟/调试用) */
    cameraInfo() {
      return {
        view: viewName,
        aspect: Number(camera.aspect.toFixed(3)),
        pos: camera.position.toArray().map((n) => Number(n.toFixed(2))),
        dist: Number(camera.position.distanceTo(lookTarget).toFixed(2)),
      }
    },
    /** 渲染统计(冒烟/调试用:draw call 与三角数来自最近一帧) */
    stats() {
      return {
        calls: renderer.info.render.calls,
        triangles: renderer.info.render.triangles,
        frames,
        loopError,
        handMeshes: handMeshes.size,
        sceneChildren: scene.children.length,
      }
    },
    resize() {
      const w = container.clientWidth || 640
      const h = container.clientHeight || 400
      renderer.setSize(w, h, false)
      camera.aspect = w / h
      camera.updateProjectionMatrix()
      applyCamera()
    },
    dispose() {
      disposed = true
      tweens.length = 0
      renderer.dispose()
      tileGeo.dispose()
      backMat.map?.dispose()
      backMat.dispose()
      ivoryMat.dispose()
      for (const task of faceMats.values()) {
        task.then((m) => {
          m.map?.dispose()
          m.dispose()
        })
      }
      renderer.domElement.remove()
    },
  }

  // 渲染循环:补间(相机切换/掷骰)+ 连续重绘。
  // rAF 为主;个别 WebView(rAF/ResizeObserver 不派发)或后台节流时由 keep-alive 兜底,
  // 兜底只在页面可见时渲染(真实后台标签页不烧 GPU)。
  let raf = 0
  let lastTs = 0
  let frames = 0
  let loopError = null
  let lastRenderAt = 0
  function tick(now) {
    if (disposed) return
    try {
      const dt = lastTs ? Math.min(64, now - lastTs) : 16
      lastTs = now
      for (let i = tweens.length - 1; i >= 0; i--) {
        const tw = tweens[i]
        tw.t += dt
        const p = Math.min(1, tw.t / tw.dur)
        tw.tick?.(p)
        if (p >= 1) tweens.splice(i, 1)
      }
      renderer.render(scene, camera)
      frames += 1
      lastRenderAt = now
    } catch (e) {
      loopError = String(e?.message || e)
      return
    }
    raf = requestAnimationFrame(tick)
  }
  raf = requestAnimationFrame(tick)
  const keepAlive = setInterval(() => {
    if (disposed || document.hidden) return
    if (performance.now() - lastRenderAt > 180) tick(performance.now())
  }, 120)

  const ro = new ResizeObserver(() => api.resize())
  ro.observe(container)
  // 冒烟/调试钩子:测试工具可从容器上拿到实例(不进任何业务路径)
  container.__mahjong3dTable = api

  api._disposeExtras = () => {
    cancelAnimationFrame(raf)
    clearInterval(keepAlive)
    ro.disconnect()
  }
  const origDispose = api.dispose
  api.dispose = () => {
    api._disposeExtras()
    origDispose()
  }
  return api
}
