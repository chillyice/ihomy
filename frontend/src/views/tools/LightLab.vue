<!-- 3D 光影实验台(临时):真实阴影贴图演示「光源 → 带窗框的墙(朝南) → 屏幕/地面」遮挡投影。
     交互:OrbitControls 左键旋转(绕场景中央)/右键平移(移动场景)/滚轮缩放;拖动发光小球移动光源。
     太阳模拟:客户端 NOAA 算法(见 utils/solarPosition.js,与后端 SolarUtil 同源),按日期+经纬度(默认济南)+
               时区 UTC+8 计算高度角/方位角,驱动平行太阳光;支持播放/暂停/加速/步进;房间朝南=窗朝方位角 180°。
     窗户:两扇日字形传统窗,绕外窗框竖轴由中间向外推开(合页在左右外梃);墙体厚度与窗框/窗棂同厚。
     手动模式:可拖拽光源 + 光源类型(平行光/聚光/点光)。约定:懒加载路由 + three 独立 chunk,不接 i18n。 -->
<template>
  <div class="light-lab">
    <div ref="canvasRef" class="light-lab-canvas"></div>

    <div class="hud">
      <button class="hud-back" @click="goBack" title="返回工具箱">← 返回</button>
      <div class="hud-title">3D 光影实验台</div>

      <div class="hud-row">
        <div class="hud-seg">
          <button :class="{ active: mode === 'sun' }" @click="setMode('sun')">太阳模拟</button>
          <button :class="{ active: mode === 'manual' }" @click="setMode('manual')">手动拖拽</button>
        </div>
      </div>

      <!-- 太阳模拟面板 -->
      <template v-if="mode === 'sun'">
        <div class="hud-row">
          <span class="hud-label">日期</span>
          <input type="date" v-model="dateStr" class="hud-input" />
          <span class="hud-time">{{ hudTime }}</span>
        </div>
        <div class="hud-row">
          <span class="hud-label">地点</span>
          <span class="hud-city">{{ locationLabel }}</span>
          <input type="number" v-model.number="lat" step="0.01" class="hud-input num" title="纬度" />
          <input type="number" v-model.number="lng" step="0.01" class="hud-input num" title="经度" />
        </div>
        <div class="hud-stats">
          <span>高度角 <b>{{ hudAlt }}°</b></span>
          <span>方位角 <b>{{ hudAz }}°</b></span>
          <span>窗角 <b>{{ hudWindowAngle }}°</b></span>
          <span v-if="isNight" class="hud-night">夜间</span>
        </div>
        <div class="hud-row">
          <div class="hud-seg">
            <button class="hud-btn" @click="stepTime(-15)" title="后退 15 分钟">⏮</button>
            <button class="hud-btn main" @click="playing = !playing">{{ playing ? '⏸' : '▶' }}</button>
            <button class="hud-btn" @click="stepTime(15)" title="前进 15 分钟">⏭</button>
            <button class="hud-btn" @click="resetToNow" title="回到当前时刻">现在</button>
          </div>
        </div>
        <div class="hud-row">
          <div class="hud-seg">
            <button v-for="sp in speeds" :key="sp" class="hud-btn" :class="{ on: speed === sp }" @click="speed = sp">{{ sp }}×</button>
          </div>
        </div>
      </template>

      <!-- 手动拖拽面板 -->
      <template v-else>
        <div class="hud-row">
          <span class="hud-label">光源</span>
          <div class="hud-seg">
            <button :class="{ active: lightType === 'directional' }" @click="lightType = 'directional'">平行光</button>
            <button :class="{ active: lightType === 'spot' }" @click="lightType = 'spot'">聚光</button>
            <button :class="{ active: lightType === 'point' }" @click="lightType = 'point'">点光</button>
          </div>
        </div>
        <div class="hud-row">
          <span class="hud-label">阴影</span>
          <button class="hud-btn" :class="{ on: shadowOn }" @click="toggleShadow">{{ shadowOn ? '开' : '关' }}</button>
          <button class="hud-btn" @click="resetManualLight">重置光源</button>
        </div>
        <div class="hud-coords">光源 {{ lightCoords || '—' }}</div>
      </template>

      <div class="hud-row">
        <span class="hud-label">开窗</span>
        <input type="range" min="0" max="90" step="1" v-model.number="windowOpen" class="hud-range" />
        <span class="hud-unit">{{ windowOpen }}°</span>
      </div>

      <div class="hud-row hud-footer">
        <button class="hud-btn" :class="{ on: lampOn }" @click="toggleLamp">台灯 {{ lampOn ? '开' : '关' }}</button>
        <button class="hud-btn" @click="resetCamera">重置视角</button>
      </div>
      <div class="hud-hint">左键旋转 · 右键平移 · 滚轮缩放 · 拖动发光小球移动光源</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import * as THREE from 'three'
import { OrbitControls } from 'three/addons/controls/OrbitControls.js'
import { sunPosition } from '@/utils/solarPosition'

const RAD = Math.PI / 180
const TZ_OFFSET_HOURS = 8
const JINAN_LAT = 36.6512
const JINAN_LNG = 117.1201
const SUN_DISTANCE = 15
const SUN_BASE_INTENSITY = 6

const canvasRef = ref(null)
const router = useRouter()
const goBack = () => router.push('/tools')
const mode = ref('sun')
const lightType = ref('directional')
const shadowOn = ref(true)
const lampOn = ref(true)
const playing = ref(true)
const speed = ref(1)
const speeds = [0.5, 1, 2, 4, 8]
const dateStr = ref('')
const lat = ref(JINAN_LAT)
const lng = ref(JINAN_LNG)
const hudTime = ref('--:--')
const hudAlt = ref('--')
const hudAz = ref('--')
const hudWindowAngle = ref('--')
const isNight = ref(false)
const lightCoords = ref('')
const windowOpen = ref(50) // 开窗角度(0° 关闭 ~ 90° 全开)
const reducedMotion = ref(false)

const locationLabel = computed(() =>
  Math.abs(lat.value - JINAN_LAT) < 0.5 && Math.abs(lng.value - JINAN_LNG) < 0.5 ? '济南' : '自定义'
)

let renderer = null
let scene = null
let camera = null
let controls = null
let sunLight = null
let manualLight = null
let manualLightTarget = null
let hemi = null
let gizmo = null
let gizmoHit = null
let raycaster = null
let dragPlane = null
let lampGroup = null
let lampLight = null
let lampShadeMat = null
let windowGroup = null
let sashL = null
let sashR = null
let compassTextures = []
let dragging = false
let rafId = null

// 模拟时间(当地时间分钟,0~1440),普通变量由 rAF 推进,不写 Vue ref
let minutesOfDay = 0
let lastTickMs = null
let lastFrameMs = null
let lastHudFlushMs = null
let currentOpen = 0
let latestSun = { time: '--:--', altitude: 0, azimuth: 180 }

const _tmpNormal = new THREE.Vector3()
const _sunColor = new THREE.Color()

// 场景常量(墙在 z=WALL_Z,窗朝南=方位角 180° 方向)
const WALL_Z = -2.5
const WALL_W = 6
const WALL_H = 6.5
const ROOM_DEPTH = 6
const WIN_W = 2.4
const WIN_H = 3
const WIN_BOTTOM = 0.8
const WIN_CX = 0
const WIN_CY = WIN_BOTTOM + WIN_H / 2
const WALL_T = 0.16      // 墙体厚度(与窗框/窗棂同厚)
const FRAME_BAR = 0.07   // 窗框/窗扇框 面内厚度
const MULLION_BAR = 0.04 // 窗棂(中横档)面内厚度
const DEFAULT_LIGHT_POS = new THREE.Vector3(1.5, 3.6, -6.5)
// 桌面台灯(可开关):置于桌面上靠左,灯罩朝下打光
const LAMP_X = -0.9
const LAMP_Z = WALL_Z + 0.35 // 桌面中心深(见 buildTable 的 TZ)
const LAMP_BASE_Y = 0.75     // 桌面顶高(见 buildTable 的 TT)
const LAMP_INTENSITY = 10
const SHADOW_BLUR_RADIUS = 6    // 阴影模糊半径(配合 VSM 软阴影消除锯齿)
const SHADOW_BLUR_SAMPLES = 16  // 模糊采样数

// 软阴影:把 LightShadow 配置为带高斯模糊的 VSM(半径/采样),柔化锯齿边缘
function softenShadow(shadow) {
  shadow.radius = SHADOW_BLUR_RADIUS
  shadow.blurSamples = SHADOW_BLUR_SAMPLES
}

function init() {
  const container = canvasRef.value
  const w = container.clientWidth || window.innerWidth
  const h = container.clientHeight || window.innerHeight

  renderer = new THREE.WebGLRenderer({ antialias: true })
  renderer.setPixelRatio(Math.min(2, window.devicePixelRatio || 1))
  renderer.setSize(w, h)
  renderer.shadowMap.enabled = shadowOn.value
  renderer.shadowMap.type = THREE.VSMShadowMap
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.12
  renderer.domElement.style.touchAction = 'none'
  container.appendChild(renderer.domElement)

  scene = new THREE.Scene()
  scene.background = new THREE.Color(0x15110d)

  camera = new THREE.PerspectiveCamera(50, w / h, 0.1, 100)
  // 默认正视窗户:相机在室内(z>WALL_Z),平视窗心,从室内向室外看
  camera.position.set(0, WIN_CY, 3.2)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.target.set(0, WIN_CY, WALL_Z)
  controls.enableDamping = !reducedMotion.value
  controls.dampingFactor = 0.08
  controls.minDistance = 3
  controls.maxDistance = 20
  controls.maxPolarAngle = Math.PI * 0.56
  controls.mouseButtons = { LEFT: THREE.MOUSE.ROTATE, MIDDLE: THREE.MOUSE.DOLLY, RIGHT: THREE.MOUSE.PAN }
  controls.saveState()

  // 环境补光(日/夜在 loop 中调节强度)
  hemi = new THREE.HemisphereLight(0xcfd8ff, 0x2e2419, 0.5)
  scene.add(hemi)

  // 太阳光(平行光,太阳模拟专用)
  sunLight = new THREE.DirectionalLight(0xfff2da, SUN_BASE_INTENSITY)
  sunLight.castShadow = shadowOn.value
  sunLight.shadow.mapSize.set(2048, 2048)
  softenShadow(sunLight.shadow)
  sunLight.shadow.camera.near = 0.5
  sunLight.shadow.camera.far = 60
  sunLight.shadow.camera.left = -12
  sunLight.shadow.camera.right = 12
  sunLight.shadow.camera.top = 12
  sunLight.shadow.camera.bottom = -12
  sunLight.shadow.bias = -0.0002
  sunLight.target.position.set(0, 0.8, -1)
  scene.add(sunLight)
  scene.add(sunLight.target)

  buildFloor()
  buildWall()
  buildSideWalls()
  buildCeiling()
  buildWindow()
  buildTable()
  buildLamp()
  buildBalcony()
  buildBoxes()
  buildCompass()
  buildGizmo()

  // 手动光源(初始平行光,默认隐藏;进入手动模式才显示)
  applyLightType()
  syncLightVisibility()

  raycaster = new THREE.Raycaster()
  dragPlane = new THREE.Plane()

  renderer.domElement.addEventListener('pointerdown', onPointerDown)
  renderer.domElement.addEventListener('pointermove', onPointerMove)
  window.addEventListener('resize', onResize)
  document.addEventListener('visibilitychange', onVisibility)

  // 初始时间 = 济南当前时刻
  const nowJinan = new Date(Date.now() + TZ_OFFSET_HOURS * 3600000)
  dateStr.value = nowJinan.toISOString().slice(0, 10)
  minutesOfDay = nowJinan.getUTCHours() * 60 + nowJinan.getUTCMinutes()

  loop()
}

function buildFloor() {
  const floor = new THREE.Mesh(
    new THREE.PlaneGeometry(12, 12),
    new THREE.MeshStandardMaterial({ color: 0x3a322a, roughness: 0.85, metalness: 0 })
  )
  floor.rotation.x = -Math.PI / 2
  floor.receiveShadow = true
  scene.add(floor)
}

function buildWall() {
  const shape = new THREE.Shape()
  shape.moveTo(-WALL_W / 2, 0)
  shape.lineTo(WALL_W / 2, 0)
  shape.lineTo(WALL_W / 2, WALL_H)
  shape.lineTo(-WALL_W / 2, WALL_H)
  shape.closePath()
  const hole = new THREE.Path()
  hole.moveTo(WIN_CX - WIN_W / 2, WIN_BOTTOM)
  hole.lineTo(WIN_CX + WIN_W / 2, WIN_BOTTOM)
  hole.lineTo(WIN_CX + WIN_W / 2, WIN_BOTTOM + WIN_H)
  hole.lineTo(WIN_CX - WIN_W / 2, WIN_BOTTOM + WIN_H)
  hole.closePath()
  shape.holes.push(hole)
  // 挤出墙体厚度(与窗框/窗棂同厚),沿 z 居中,形成真实窗洞侧壁
  const geo = new THREE.ExtrudeGeometry(shape, { depth: WALL_T, bevelEnabled: false })
  geo.translate(0, 0, -WALL_T / 2)
  const wall = new THREE.Mesh(
    geo,
    new THREE.MeshStandardMaterial({ color: 0x8d7d6a, roughness: 0.92, side: THREE.DoubleSide })
  )
  wall.position.set(0, 0, WALL_Z)
  wall.castShadow = true
  wall.receiveShadow = true
  scene.add(wall)
}

function buildSideWalls() {
  const mat = new THREE.MeshStandardMaterial({ color: 0x7d6f5d, roughness: 0.92 })
  const openZ = WALL_Z + ROOM_DEPTH
  const geo = new THREE.BoxGeometry(WALL_T, WALL_H, ROOM_DEPTH)
  const centerZ = (WALL_Z + openZ) / 2
  ;[WALL_W / 2, -WALL_W / 2].forEach((x) => {
    const wall = new THREE.Mesh(geo, mat)
    wall.position.set(x, WALL_H / 2, centerZ)
    wall.castShadow = true
    wall.receiveShadow = true
    scene.add(wall)
  })
}

function buildCeiling() {
  const mat = new THREE.MeshStandardMaterial({ color: 0x7d6f5d, roughness: 0.92 })
  const openZ = WALL_Z + ROOM_DEPTH
  const centerZ = (WALL_Z + openZ) / 2
  const ceiling = new THREE.Mesh(new THREE.BoxGeometry(WALL_W, WALL_T, ROOM_DEPTH), mat)
  ceiling.position.set(0, WALL_H - WALL_T / 2, centerZ)
  ceiling.castShadow = true
  ceiling.receiveShadow = true
  scene.add(ceiling)
}

function buildTable() {
  const mat = new THREE.MeshStandardMaterial({ color: 0x7a5a3a, roughness: 0.6 })
  const TW = 2.8 // 桌面宽(比窗 2.4 宽一点)
  const TD = 1.4 // 桌面深(向室内拉宽两倍)
  const TH = 0.05 // 桌面厚
  const TT = 0.75 // 桌面顶高(正常桌高)
  const TZ = WALL_Z + TD / 2 // 靠窗墙
  const top = new THREE.Mesh(new THREE.BoxGeometry(TW, TH, TD), mat)
  top.position.set(0, TT - TH / 2, TZ)
  top.castShadow = true
  top.receiveShadow = true
  const legGeo = new THREE.BoxGeometry(0.06, TT - TH, 0.06)
  const lx = TW / 2 - 0.08
  const lz = TD / 2 - 0.08
  const legs = [[1, 1], [1, -1], [-1, 1], [-1, -1]].map(([sx, sz]) => {
    const leg = new THREE.Mesh(legGeo, mat)
    leg.position.set(sx * lx, (TT - TH) / 2, TZ + sz * lz)
    leg.castShadow = true
    leg.receiveShadow = true
    return leg
  })
  scene.add(top, ...legs)
}

function buildLamp() {
  lampGroup = new THREE.Group()
  const metalMat = new THREE.MeshStandardMaterial({ color: 0x30343c, roughness: 0.4, metalness: 0.7 })
  lampShadeMat = new THREE.MeshStandardMaterial({ color: 0x1e4a3a, roughness: 0.5, metalness: 0.2, emissive: 0x000000, side: THREE.DoubleSide })

  // 底座
  const base = new THREE.Mesh(new THREE.CylinderGeometry(0.13, 0.15, 0.04, 24), metalMat)
  base.position.y = 0.02
  base.castShadow = true
  base.receiveShadow = true
  // 灯杆
  const arm = new THREE.Mesh(new THREE.CylinderGeometry(0.022, 0.03, 0.45, 16), metalMat)
  arm.position.y = 0.04 + 0.225
  arm.castShadow = true
  // 灯罩(锥体,尖朝上、开口朝下,双面可见)
  const shade = new THREE.Mesh(new THREE.ConeGeometry(0.17, 0.22, 24, 1, true), lampShadeMat)
  shade.position.y = 0.04 + 0.45 + 0.11
  shade.castShadow = true
  lampGroup.add(base, arm, shade)
  lampGroup.position.set(LAMP_X, LAMP_BASE_Y, LAMP_Z)
  scene.add(lampGroup)

  // 台灯光源:聚光灯朝下打向桌面
  lampLight = new THREE.SpotLight(0xffe0b0, LAMP_INTENSITY, 8, 0.7, 0.55, 2)
  lampLight.castShadow = shadowOn.value
  lampLight.shadow.mapSize.set(1024, 1024)
  softenShadow(lampLight.shadow)
  lampLight.shadow.bias = -0.0004
  lampLight.position.set(LAMP_X, LAMP_BASE_Y + 0.45, LAMP_Z)
  lampLight.target.position.set(LAMP_X, 0.72, LAMP_Z + 0.35)
  scene.add(lampLight)
  scene.add(lampLight.target)
  lampLight.visible = lampOn.value
  lampShadeMat.emissive.setHex(lampOn.value ? 0xffc87a : 0x000000)
}

function buildBalcony() {
  const mat = new THREE.MeshStandardMaterial({ color: 0x6e5c4a, roughness: 0.8 })
  const BW = 3.2 // 阳台宽
  const BD = 0.9 // 阳台深(向外)
  const BT = 0.12 // 板厚
  const TOP = WIN_BOTTOM - 0.1 // 阳台顶比窗框底稍低
  const BZ = WALL_Z - BD / 2
  const balcony = new THREE.Mesh(new THREE.BoxGeometry(BW, BT, BD), mat)
  balcony.position.set(0, TOP - BT / 2, BZ)
  balcony.castShadow = true
  balcony.receiveShadow = true
  const potMat = new THREE.MeshStandardMaterial({ color: 0xb0603a, roughness: 0.7 })
  const pot = new THREE.Mesh(new THREE.CylinderGeometry(0.28, 0.22, 0.35, 24), potMat)
  pot.position.set(0, TOP + 0.175, BZ)
  pot.castShadow = true
  pot.receiveShadow = true
  scene.add(balcony, pot)
}

function windowBar(w, h, d, cx, cy, cz, mat) {
  const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), mat)
  m.position.set(cx, cy, cz)
  m.castShadow = true
  m.receiveShadow = true
  return m
}

function buildWindow() {
  const frameMat = new THREE.MeshStandardMaterial({ color: 0x4a3824, roughness: 0.55 })
  const glassMat = new THREE.MeshStandardMaterial({
    color: 0xa8c6d8, roughness: 0.08, metalness: 0.1,
    transparent: true, opacity: 0.22, side: THREE.DoubleSide,
  })

  windowGroup = new THREE.Group()
  windowGroup.position.set(0, 0, WALL_Z)
  const D = WALL_T
  const FB = FRAME_BAR

  // 外窗框(固定):上下左右四根,填满洞口边缘
  windowGroup.add(windowBar(WIN_W, FB, D, WIN_CX, WIN_BOTTOM + WIN_H - FB / 2, 0, frameMat))
  windowGroup.add(windowBar(WIN_W, FB, D, WIN_CX, WIN_BOTTOM + FB / 2, 0, frameMat))
  windowGroup.add(windowBar(FB, WIN_H, D, WIN_CX - WIN_W / 2 + FB / 2, WIN_CY, 0, frameMat))
  windowGroup.add(windowBar(FB, WIN_H, D, WIN_CX + WIN_W / 2 - FB / 2, WIN_CY, 0, frameMat))

  // 两扇窗:左扇合页在左梃、右扇合页在右梃,由中间向外推开
  sashL = buildSash(frameMat, glassMat, D, FB)
  sashL.position.set(WIN_CX - WIN_W / 2 + FB, 0, 0)
  windowGroup.add(sashL)

  sashR = buildSash(frameMat, glassMat, D, FB)
  sashR.scale.x = -1
  sashR.position.set(WIN_CX + WIN_W / 2 - FB, 0, 0)
  windowGroup.add(sashR)

  scene.add(windowGroup)

  // 初始即按滑杆角度摆好,避免首帧从 0° 突跳
  currentOpen = windowOpen.value
  sashL.rotation.y = currentOpen * RAD
  sashR.rotation.y = -currentOpen * RAD
}

function buildSash(frameMat, glassMat, D, FB) {
  const g = new THREE.Group()
  const SW = WIN_W / 2 - FB            // 单扇宽(左框内缘到中缝)
  const MB = MULLION_BAR
  const y0 = WIN_BOTTOM
  const y1 = WIN_BOTTOM + WIN_H
  const innerTop = y1 - FB             // 上框条下缘
  const innerBot = y0 + FB             // 下框条上缘
  const sashH = innerTop - innerBot    // 扇内高
  const ym = WIN_BOTTOM + WIN_H / 2

  // 四根窗扇框(合页梃 / 中缝梃 / 上梃 / 下梃)
  g.add(windowBar(FB, sashH, D, FB / 2, ym, 0, frameMat))
  g.add(windowBar(FB, sashH, D, SW - FB / 2, ym, 0, frameMat))
  g.add(windowBar(SW, FB, D, SW / 2, innerTop - FB / 2, 0, frameMat))
  g.add(windowBar(SW, FB, D, SW / 2, innerBot + FB / 2, 0, frameMat))
  // 日字形中横棂(把窗扇分成上下两格)
  g.add(windowBar(SW, MB, D, SW / 2, ym, 0, frameMat))

  // 玻璃:上下两格,透明不投影(阳光穿过)
  const gw = SW - FB * 2
  const gh = (sashH - FB * 2 - MB) / 2
  const glassGeo = new THREE.PlaneGeometry(gw, gh)
  const top = new THREE.Mesh(glassGeo, glassMat)
  top.position.set(SW / 2, ym + MB / 2 + gh / 2, 0)
  const bot = new THREE.Mesh(glassGeo, glassMat)
  bot.position.set(SW / 2, ym - MB / 2 - gh / 2, 0)
  g.add(top, bot)
  return g
}

function buildBoxes() {
  const mat = new THREE.MeshStandardMaterial({ color: 0x705e4b, roughness: 0.8 })
  const specs = [
    { s: [0.7, 0.5, 0.5], p: [-1.6, 0.25, 1.4] },
    { s: [0.5, 0.8, 0.5], p: [1.4, 0.4, 1.0] },
    { s: [0.9, 0.3, 0.6], p: [0.4, 0.15, 2.0] },
  ]
  specs.forEach(({ s, p }) => {
    const m = new THREE.Mesh(new THREE.BoxGeometry(...s), mat)
    m.position.set(...p)
    m.castShadow = true
    m.receiveShadow = true
    scene.add(m)
  })
}

function makeTextSprite(text, color) {
  const canvas = document.createElement('canvas')
  canvas.width = 64
  canvas.height = 64
  const ctx = canvas.getContext('2d')
  ctx.font = 'bold 42px sans-serif'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillStyle = color
  ctx.fillText(text, 32, 32)
  const tex = new THREE.CanvasTexture(canvas)
  compassTextures.push(tex)
  const sprite = new THREE.Sprite(new THREE.SpriteMaterial({ map: tex, transparent: true, depthWrite: false }))
  sprite.scale.set(1.1, 1.1, 1)
  return sprite
}

function buildCompass() {
  const R = 5
  const marks = [
    { t: 'N', p: [0, 0.05, R], c: 'rgba(255,255,255,0.55)' },
    { t: 'S', p: [0, 0.05, -R], c: 'rgba(255,176,96,0.85)' }, // 南=太阳侧(窗朝南)
    { t: 'E', p: [-R, 0.05, 0], c: 'rgba(255,255,255,0.55)' },
    { t: 'W', p: [R, 0.05, 0], c: 'rgba(255,255,255,0.55)' },
  ]
  marks.forEach((m) => {
    const s = makeTextSprite(m.t, m.c)
    s.position.set(...m.p)
    scene.add(s)
  })
}

function buildGizmo() {
  gizmo = new THREE.Mesh(
    new THREE.SphereGeometry(0.16, 24, 24),
    new THREE.MeshBasicMaterial({ color: 0xffd98a })
  )
  const halo = new THREE.Mesh(
    new THREE.SphereGeometry(0.3, 24, 24),
    new THREE.MeshBasicMaterial({ color: 0xffcf7a, transparent: true, opacity: 0.28 })
  )
  gizmo.add(halo)
  // 不可见的放大命中球,便于点选(Raycaster 不校验 visible)
  gizmoHit = new THREE.Mesh(
    new THREE.SphereGeometry(0.5, 12, 12),
    new THREE.MeshBasicMaterial({ visible: false })
  )
  gizmo.add(gizmoHit)
  gizmo.position.copy(DEFAULT_LIGHT_POS)
  scene.add(gizmo)
}

function disposeManualLight() {
  if (manualLight) {
    scene.remove(manualLight)
    if (manualLight.target) scene.remove(manualLight.target)
    if (manualLight.dispose) manualLight.dispose()
    manualLight = null
    manualLightTarget = null
  }
}

function applyLightType() {
  const pos = manualLight ? manualLight.position.clone() : DEFAULT_LIGHT_POS.clone()
  disposeManualLight()
  const type = lightType.value

  if (type === 'directional') {
    const l = new THREE.DirectionalLight(0xfff1d6, 3)
    l.castShadow = shadowOn.value
    l.shadow.mapSize.set(2048, 2048)
    softenShadow(l.shadow)
    l.shadow.camera.near = 0.5
    l.shadow.camera.far = 40
    l.shadow.camera.left = -10
    l.shadow.camera.right = 10
    l.shadow.camera.top = 10
    l.shadow.camera.bottom = -10
    l.shadow.bias = -0.0002
    l.position.copy(pos)
    manualLightTarget = new THREE.Object3D()
    manualLightTarget.position.set(0, 1.0, 0)
    l.target = manualLightTarget
    scene.add(l)
    scene.add(manualLightTarget)
    manualLight = l
  } else if (type === 'spot') {
    const l = new THREE.SpotLight(0xfff1d6, 120, 60, Math.PI / 5, 0.35, 1.6)
    l.castShadow = shadowOn.value
    l.shadow.mapSize.set(2048, 2048)
    softenShadow(l.shadow)
    l.shadow.bias = -0.0002
    l.position.copy(pos)
    manualLightTarget = new THREE.Object3D()
    manualLightTarget.position.set(0, 1.0, 0)
    l.target = manualLightTarget
    scene.add(l)
    scene.add(manualLightTarget)
    manualLight = l
  } else {
    const l = new THREE.PointLight(0xfff1d6, 60, 60, 1.8)
    l.castShadow = shadowOn.value
    l.shadow.mapSize.set(1024, 1024)
    l.shadow.bias = -0.0003
    l.position.copy(pos)
    scene.add(l)
    manualLight = l
  }

  syncLightVisibility()
  if (mode.value === 'manual') {
    gizmo.position.copy(manualLight.position)
    updateCoords()
  }
}

function syncLightVisibility() {
  const sun = mode.value === 'sun'
  sunLight.visible = sun
  if (manualLight) manualLight.visible = !sun
}

function setMode(m) {
  if (mode.value === m) return
  if (m === 'manual') {
    // 从太阳位置接手,保证切换无缝
    if (manualLight) manualLight.position.copy(sunLight.position)
    gizmo.position.copy(sunLight.position)
  }
  mode.value = m
  syncLightVisibility()
  if (m === 'manual') updateCoords()
}

function sunDirection(alt, az) {
  const altR = alt * RAD
  const azR = az * RAD
  return new THREE.Vector3(
    -Math.sin(azR) * Math.cos(altR),
    Math.sin(altR),
    Math.cos(azR) * Math.cos(altR)
  )
}

function sunColorHex(alt) {
  if (alt < 6) return 0xff9a4a
  if (alt < 15) return 0xffb877
  if (alt < 30) return 0xffd9a8
  return 0xfff2da
}

function applySunPosition() {
  latestSun = sunPosition(lat.value, lng.value, dateStr.value, minutesOfDay)
  const dir = sunDirection(latestSun.altitude, latestSun.azimuth)
  sunLight.position.copy(dir).multiplyScalar(SUN_DISTANCE)
  if (latestSun.altitude > 0) {
    sunLight.intensity = SUN_BASE_INTENSITY * Math.max(0, Math.sin(latestSun.altitude * RAD))
    _sunColor.setHex(sunColorHex(latestSun.altitude))
    sunLight.color.copy(_sunColor)
  } else {
    sunLight.intensity = 0
  }
  gizmo.position.copy(sunLight.position)
}

function flushHud() {
  hudTime.value = latestSun.time
  hudAlt.value = latestSun.altitude.toFixed(1)
  hudAz.value = latestSun.azimuth.toFixed(1)
  hudWindowAngle.value = Math.max(0, 90 - Math.abs(latestSun.azimuth - 180)).toFixed(0)
  isNight.value = latestSun.altitude <= 0
  const night = latestSun.altitude <= 0
  hemi.intensity = night ? 0.14 : 0.68
  scene.background.setHex(night ? 0x0a0c14 : 0x15110d)
}

function toNDC(e) {
  const rect = renderer.domElement.getBoundingClientRect()
  return new THREE.Vector2(
    ((e.clientX - rect.left) / rect.width) * 2 - 1,
    -((e.clientY - rect.top) / rect.height) * 2 + 1
  )
}

function hitGizmo(e) {
  raycaster.setFromCamera(toNDC(e), camera)
  return raycaster.intersectObject(gizmoHit, false).length > 0
}

function clampLightPos(v) {
  v.x = THREE.MathUtils.clamp(v.x, -7, 7)
  v.y = THREE.MathUtils.clamp(v.y, 0.4, 7)
  v.z = THREE.MathUtils.clamp(v.z, -9, WALL_Z - 0.1)
}

function onPointerDown(e) {
  if (e.button !== 0 || dragging) return
  if (!hitGizmo(e)) return
  dragging = true
  controls.enabled = false
  renderer.domElement.style.cursor = 'grabbing'
  if (mode.value === 'sun') setMode('manual') // 抓太阳 → 切换手动
  camera.getWorldDirection(_tmpNormal)
  dragPlane.setFromNormalAndCoplanarPoint(_tmpNormal, manualLight.position)
  window.addEventListener('pointermove', onDragMove)
  window.addEventListener('pointerup', onDragEnd)
}

function onDragMove(e) {
  raycaster.setFromCamera(toNDC(e), camera)
  const pt = new THREE.Vector3()
  if (!raycaster.ray.intersectPlane(dragPlane, pt)) return
  clampLightPos(pt)
  manualLight.position.copy(pt)
  gizmo.position.copy(pt)
  updateCoords()
}

function onDragEnd() {
  dragging = false
  controls.enabled = true
  renderer.domElement.style.cursor = 'grab'
  window.removeEventListener('pointermove', onDragMove)
  window.removeEventListener('pointerup', onDragEnd)
}

function onPointerMove(e) {
  if (dragging) return
  if (hitGizmo(e)) {
    controls.enabled = false
    renderer.domElement.style.cursor = 'grab'
  } else {
    controls.enabled = true
    renderer.domElement.style.cursor = 'default'
  }
}

function updateCoords() {
  if (!manualLight) return
  const p = manualLight.position
  lightCoords.value = `x ${p.x.toFixed(2)} · y ${p.y.toFixed(2)} · z ${p.z.toFixed(2)}`
}

function toggleShadow() {
  shadowOn.value = !shadowOn.value
}

function toggleLamp() {
  lampOn.value = !lampOn.value
}

function resetManualLight() {
  if (!manualLight) return
  manualLight.position.copy(DEFAULT_LIGHT_POS)
  gizmo.position.copy(DEFAULT_LIGHT_POS)
  updateCoords()
}

function resetCamera() {
  controls.reset()
}

function stepTime(deltaMin) {
  minutesOfDay = (minutesOfDay + deltaMin + 1440) % 1440
  applySunPosition()
  flushHud()
}

function resetToNow() {
  const nowJinan = new Date(Date.now() + TZ_OFFSET_HOURS * 3600000)
  minutesOfDay = nowJinan.getUTCHours() * 60 + nowJinan.getUTCMinutes()
  dateStr.value = nowJinan.toISOString().slice(0, 10)
  applySunPosition()
  flushHud()
}

function onResize() {
  const container = canvasRef.value
  const w = container.clientWidth || window.innerWidth
  const h = container.clientHeight || window.innerHeight
  camera.aspect = w / h
  camera.updateProjectionMatrix()
  renderer.setSize(w, h)
}

function onVisibility() {
  if (document.hidden) {
    if (rafId) { cancelAnimationFrame(rafId); rafId = null; lastTickMs = null; lastFrameMs = null }
  } else if (!rafId) {
    lastTickMs = null
    lastFrameMs = null
    loop()
  }
}

function loop(now) {
  rafId = requestAnimationFrame(loop)
  const nowMs = now || performance.now()
  const dt = lastFrameMs == null ? 0 : (nowMs - lastFrameMs) / 1000
  lastFrameMs = nowMs

  // 推进模拟时间(太阳模式 + 播放中)
  if (lastTickMs != null && playing.value && mode.value === 'sun') {
    minutesOfDay += ((nowMs - lastTickMs) / 1000) * 25 * speed.value
    minutesOfDay %= 1440
    if (minutesOfDay < 0) minutesOfDay += 1440
  }
  lastTickMs = nowMs

  if (mode.value === 'sun') applySunPosition()

  // 窗扇平滑推拉(绕外窗框竖轴旋转,左扇 +θ / 右扇 -θ 向室外推开)
  currentOpen += (windowOpen.value - currentOpen) * Math.min(1, dt * 8)
  if (sashL) sashL.rotation.y = currentOpen * RAD
  if (sashR) sashR.rotation.y = -currentOpen * RAD

  controls.update()
  renderer.render(scene, camera)

  // HUD 节流刷新(约 5Hz),避免逐帧写 Vue ref
  if (lastHudFlushMs == null || nowMs - lastHudFlushMs > 200) {
    lastHudFlushMs = nowMs
    if (mode.value === 'sun') flushHud()
  }
}

function dispose() {
  if (rafId) { cancelAnimationFrame(rafId); rafId = null }
  controls.dispose()
  disposeManualLight()
  scene.traverse((obj) => {
    if (obj.geometry) obj.geometry.dispose()
    if (obj.material) {
      const mats = Array.isArray(obj.material) ? obj.material : [obj.material]
      mats.forEach((m) => m.dispose())
    }
  })
  compassTextures.forEach((t) => t.dispose())
  compassTextures = []
  renderer.dispose()
  renderer.domElement.removeEventListener('pointerdown', onPointerDown)
  renderer.domElement.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('resize', onResize)
  document.removeEventListener('visibilitychange', onVisibility)
  window.removeEventListener('pointermove', onDragMove)
  window.removeEventListener('pointerup', onDragEnd)
  if (renderer.domElement.parentNode) renderer.domElement.parentNode.removeChild(renderer.domElement)
}

watch(shadowOn, (on) => {
  if (!renderer) return
  renderer.shadowMap.enabled = on
  if (sunLight) sunLight.castShadow = on
  if (manualLight) manualLight.castShadow = on
  if (lampLight) lampLight.castShadow = on
})

watch(lightType, () => {
  if (renderer) applyLightType()
})

watch(lampOn, (on) => {
  if (lampLight) lampLight.visible = on
  if (lampShadeMat) lampShadeMat.emissive.setHex(on ? 0xffc87a : 0x000000)
})

watch([lat, lng, dateStr], () => {
  if (mode.value === 'sun') {
    applySunPosition()
    flushHud()
  }
})

onMounted(() => {
  reducedMotion.value = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  init()
})

onBeforeUnmount(dispose)
</script>

<style scoped>
.light-lab {
  position: fixed;
  inset: 0;
  overflow: hidden;
  background: #15110d;
}
.light-lab-canvas {
  position: absolute;
  inset: 0;
}
.light-lab-canvas :deep(canvas) {
  display: block;
}

.hud {
  position: absolute;
  top: 18px;
  left: 18px;
  z-index: 5;
  min-width: 268px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: rgba(20, 28, 45, 0.72);
  backdrop-filter: blur(20px) saturate(1.3);
  -webkit-backdrop-filter: blur(20px) saturate(1.3);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 14px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
  color: #fff;
}
.hud-title {
  font-size: 14px;
  font-weight: 600;
  opacity: 0.9;
  letter-spacing: 1px;
}
.hud-back {
  align-self: flex-start;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 12px;
  font-size: 12px;
  color: #fff;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s ease;
}
.hud-back:hover {
  background: rgba(255, 255, 255, 0.24);
}
.hud-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.hud-label {
  font-size: 12px;
  opacity: 0.65;
  flex-shrink: 0;
}
.hud-city {
  font-size: 12px;
  opacity: 0.85;
  flex-shrink: 0;
}
.hud-unit {
  font-size: 11px;
  opacity: 0.55;
}
.hud-time {
  font-size: 15px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.hud-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 12px;
  opacity: 0.85;
}
.hud-stats b {
  color: #ffd98a;
  font-variant-numeric: tabular-nums;
}
.hud-night {
  color: #8fb4ff;
}
.hud-seg {
  display: flex;
  gap: 4px;
}
.hud-seg button,
.hud-btn {
  padding: 4px 10px;
  font-size: 12px;
  color: #fff;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s;
}
.hud-seg button:hover,
.hud-btn:hover {
  background: rgba(255, 255, 255, 0.2);
}
.hud-seg button.active,
.hud-btn.on {
  background: rgba(255, 200, 100, 0.3);
  border-color: rgba(255, 200, 100, 0.5);
}
.hud-btn.main {
  font-size: 16px;
}
.hud-input {
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  color: #fff;
  font-size: 12px;
  padding: 3px 6px;
  color-scheme: dark;
  min-width: 0;
}
.hud-input.num {
  width: 62px;
  font-variant-numeric: tabular-nums;
}
.hud-range {
  flex: 1;
  min-width: 0;
  accent-color: #ffc864;
  cursor: pointer;
}
.hud-coords {
  font-size: 11px;
  opacity: 0.7;
  font-variant-numeric: tabular-nums;
}
.hud-footer {
  border-top: 1px solid rgba(255, 255, 255, 0.12);
  padding-top: 4px;
}
.hud-hint {
  font-size: 11px;
  opacity: 0.55;
  line-height: 1.5;
}
</style>
