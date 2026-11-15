// 立体麻将的 three.js 牌桌场景:主视角透视相机 + 圆角立体牌 + 光照阴影。
// 分层:布局数学(座位坐标系/手牌/牌河/牌墙)全是纯函数(可单测,不依赖 DOM);
// createMahjong3dTable(container) 才创建 renderer/材质/网格。规则内核在 utils/games/mahjong.js。
import * as THREE from 'three'
import { RoundedBoxGeometry } from 'three/addons/geometries/RoundedBoxGeometry.js'
import { mahjongTileSrc } from './mahjongTiles'

// ---------- 尺寸常量(世界单位;牌立放:宽 X、高 Y、厚 Z) ----------
export const TILE = { W: 0.92, H: 1.26, D: 0.6, PITCH: 1.0, LIE_PITCH: 1.3 }
export const TABLE = { W: 13.6, D: 9.9 }

/** 座位坐标系:seat0=我(南,+Z 屏幕下方)、1=下家(东 +X 屏幕右)、2=对家(北)、3=上家(西)。
 *  row = 手牌行方向,face = 该座位朝外方向(牌面朝牌主);anchor = 手牌行中心。 */
export function seatFrame(seat) {
  const yaw = [0, Math.PI / 2, Math.PI, -Math.PI / 2][((seat % 4) + 4) % 4]
  return {
    yaw,
    anchor: { x: Math.sin(yaw) * 4.75, z: Math.cos(yaw) * 3.75 },
    row: { x: Math.cos(yaw), z: -Math.sin(yaw) },
    face: { x: Math.sin(yaw), z: Math.cos(yaw) },
  }
}

/** 局部 (u=沿行, v=向中心) → 世界坐标 */
function toWorld(seat, u, v) {
  const f = seatFrame(seat)
  return { x: f.anchor.x + f.row.x * u - f.face.x * v, z: f.anchor.z + f.row.z * u - f.face.z * v }
}

/** 手牌第 i 张(共 count)的行内偏移:按持有数居中;摸牌不占中轴,见 drawnTileU */
export function handTileU(i, count) {
  return (i - (count - 1) / 2) * TILE.PITCH
}

/** 摸牌行内偏移:整手牌右侧留一个明显间隙 */
export function drawnTileU(count) {
  return ((count - 1) / 2 + 1.55) * TILE.PITCH
}

export function handTileTransform(seat, i, count) {
  return { ...toWorld(seat, handTileU(i, count), 0), yaw: seatFrame(seat).yaw }
}

export function drawnTileTransform(seat, count) {
  return { ...toWorld(seat, drawnTileU(count), 0), yaw: seatFrame(seat).yaw }
}

/** 牌河:每行 6 张,行 0 最靠近本方牌墙,向中心增长;返回行内序号→位置 */
export const RIVER_COLS = 6
export function riverTileTransform(seat, index) {
  const row = Math.floor(index / RIVER_COLS)
  const col = index % RIVER_COLS
  const v = 2.33 + row * TILE.LIE_PITCH
  const u = (col - (RIVER_COLS - 1) / 2) * TILE.PITCH
  return { ...toWorld(seat, u, v), yaw: seatFrame(seat).yaw }
}

/** 副露平铺:行内第 tileIndex 张,整组排在手牌右侧(本方视角) */
export function meldTileTransform(seat, meldStartTiles, tileIndex) {
  const u = 2.15 + (meldStartTiles + tileIndex) * TILE.PITCH
  return { ...toWorld(seat, u, 1.18), yaw: seatFrame(seat).yaw }
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

/** 牌墙第 stackIndex 摞的位置(两层摞,行内居中) */
export function wallStackTransform(seat, stackIndex, stackCount) {
  const u = (stackIndex - (stackCount - 1) / 2) * (TILE.W + 0.06)
  return { ...toWorld(seat, u, 1.03), yaw: seatFrame(seat).yaw }
}

/** 骰子与风位牌的桌面位置(中央区) */
export function diceTransform(i) {
  return { x: i === 0 ? -0.55 : 0.55, z: 0 }
}
export const WIND_KINDS = [27, 28, 29, 30] // east/south/west/north 的 kind
export function windPlateTransform(seat) {
  const f = seatFrame(seat)
  return { x: f.face.x * 1.05, z: f.face.z * 1.05, yaw: f.yaw }
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
const CAMERA_PRESETS = {
  main: { pos: [0, 8.9, 10.6], look: [0, 0, 0.55] },
  top: { pos: [0, 16.2, 0.02], look: [0, 0, 0] },
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
  const camera = new THREE.PerspectiveCamera(40, 16 / 10, 0.1, 80)
  const lookTarget = new THREE.Vector3(...CAMERA_PRESETS.main.look)
  camera.position.set(...CAMERA_PRESETS.main.pos)
  camera.lookAt(lookTarget)

  // 光照:半球环境 + 主方向光(带柔阴影)
  scene.add(new THREE.HemisphereLight('#fff6e4', '#31473c', 1.05))
  const sun = new THREE.DirectionalLight('#fff2dc', 1.9)
  sun.position.set(4.5, 12, 5)
  sun.castShadow = true
  sun.shadow.mapSize.set(1024, 1024)
  sun.shadow.camera.left = -8
  sun.shadow.camera.right = 8
  sun.shadow.camera.top = 8
  sun.shadow.camera.bottom = -8
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
  let viewName = 'main'

  function addTile(parent, kind, { x, z, yaw }, mode /* stand | lieUp | lieDown */, tileId = null) {
    const mesh = new THREE.Mesh(tileGeo, null)
    mesh.castShadow = true
    mesh.receiveShadow = true
    mesh.position.y = mode === 'stand' ? TILE.H / 2 : TILE.D / 2
    if (mode === 'lieUp') mesh.rotation.x = -Math.PI / 2
    if (mode === 'lieDown') mesh.rotation.x = Math.PI / 2
    const holder = new THREE.Group()
    holder.rotation.y = yaw
    holder.position.set(x, 0, z)
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

  // ---------- 对外 API ----------
  const api = {
    /** 我方手牌:tiles [{id, kind}];drawnKind 摸牌单放右侧 */
    setHand(tiles, drawnId = null, drawnKind = null) {
      clearGroup(groups.hand)
      handMeshes.clear()
      tiles.forEach((t, i) => {
        const pos = handTileTransform(0, i, tiles.length)
        addTile(groups.hand, t.kind, pos, 'stand', t.id)
      })
      if (drawnKind != null) {
        const pos = drawnTileTransform(0, tiles.length)
        addTile(groups.hand, drawnKind, pos, 'stand', drawnId ?? 'drawn')
      }
    },
    /** 他家手牌(只见牌背) */
    setOpponentHand(seat, count) {
      const key = 'opp' + seat
      let g = groups.opponents.getObjectByName(key)
      if (g) groups.opponents.remove(g)
      g = new THREE.Group()
      g.name = key
      for (let i = 0; i < count; i++) {
        const pos = handTileTransform(seat, i, count)
        addTile(g, null, pos, 'stand')
      }
      groups.opponents.add(g)
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
    /** 副露:groups [{dark, kinds:[kind...]}] */
    async setMelds(seat, meldGroups) {
      const key = 'meld' + seat
      let g = groups.melds.getObjectByName(key)
      if (g) groups.melds.remove(g)
      g = new THREE.Group()
      g.name = key
      let start = 0
      for (const m of meldGroups || []) {
        const kinds = m.dark ? [null, null, null, null] : m.kinds
        for (let i = 0; i < kinds.length; i++) {
          const pos = meldTileTransform(seat, start, i)
          addTile(g, kinds[i], pos, m.dark ? 'lieDown' : 'lieUp')
        }
        start += kinds.length + 0.4 // 组间留半个空隙
      }
      groups.melds.add(g)
    },
    /** 牌墙:按剩余张数重建四边两层堆 */
    setWall(wallLeft) {
      clearGroup(groups.wall)
      for (const side of wallStackCounts(wallLeft)) {
        for (let s = 0; s < side.stacks; s++) {
          const pos = wallStackTransform(side.seat, s, side.stacks)
          addTile(groups.wall, null, pos, 'lieDown')
          if (side.tops[s]) {
            addTile(groups.wall, null, { ...pos, y: TILE.D + 0.005 }, 'lieDown')
          }
        }
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
        holder.scale.set(0.62, 0.62, 0.62)
        if (seat === dealerSeat) {
          const ring = new THREE.Mesh(
            new THREE.RingGeometry(0.5, 0.64, 32),
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
      const fromPos = camera.position.clone()
      const fromLook = lookTarget.clone()
      const toPos = new THREE.Vector3(...preset.pos)
      const toLook = new THREE.Vector3(...preset.look)
      tweens.push({
        t: 0,
        dur: 450,
        tick(p) {
          camera.position.lerpVectors(fromPos, toPos, p)
          lookTarget.lerpVectors(fromLook, toLook, p)
          camera.lookAt(lookTarget)
        },
      })
    },
    getView: () => viewName,
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
