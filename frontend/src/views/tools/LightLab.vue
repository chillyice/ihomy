<!-- 3D 光影实验台:真实阴影贴图演示「太阳 → 带两扇平开窗的南墙 → 中古风书桌空间」遮挡投影。
     交互:OrbitControls 左键旋转/右键平移/滚轮缩放;拖动发光小球移动光源。
     太阳模拟:客户端 NOAA 算法(见 utils/solarPosition.js,与后端 SolarUtil 同源),按日期+经纬度(默认济南)+
               时区 UTC+8 计算高度角/方位角,驱动平行太阳光;支持播放/暂停/加速/步进;窗朝南=方位角 180°(-z)。
     场景:赫鲁晓夫楼老式小户型,温暖中古风,平视(视线 115cm)看向日字形两扇外开窗 + 窗上雨棚;
           尺寸规格 cm÷100 换算成米,墙在 z=0、室内 z>0、窗外阳台 z<0;北侧开敞(无后墙)、桌面台灯可开关,
           暖色漫反射补光随太阳高度/开窗增大;PCSS/PCF 阴影 + 阴影软硬可调。
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

      <div class="hud-row">
        <span class="hud-label">阴影算法</span>
        <div class="hud-seg">
          <button :class="{ active: shadowMode === 'pcf' }" @click="shadowMode = 'pcf'">PCF 软阴影</button>
          <button :class="{ active: shadowMode === 'pcss' }" @click="shadowMode = 'pcss'">PCSS</button>
        </div>
      </div>

      <div class="hud-row" v-if="shadowMode === 'pcss'">
        <span class="hud-label">阴影软硬</span>
        <input type="range" min="0" max="0.0015" step="0.0001" v-model.number="pcssLightSize" class="hud-range" />
        <span class="hud-unit">{{ pcssLightSize.toFixed(4) }}</span>
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

// PCSS(Percentage-Closer Soft Shadows)实验实现:可变半影(近遮挡硬、远处软)。
// 通过覆写 three.js 的 BASIC 阴影分支 getShadow 实现;仅本实验页生效(three 仅此处使用)。
const PCSS_SENTINEL = '// ihomy-pcss-shadow'
const PCSS_GET_SHADOW = /* glsl */ `
		${PCSS_SENTINEL}
		#define PCSS_BLOCKER_SAMPLES 16
		#define PCSS_PCF_SAMPLES 16
		#define PCSS_SEARCH_RADIUS 20.0
		#define PCSS_MIN_RADIUS 1.0
		#define PCSS_MAX_RADIUS 32.0
		uniform float pcssLightSize; // 光源角尺寸(软硬度),由 HUD 滑杆实时驱动

		// 16 个泊松盘采样点(归一化,单位圆内)
		const vec2 pcssPoisson16[ 16 ] = vec2[ 16 ](
			vec2( -0.94201624, -0.39906216 ), vec2( 0.94558609, -0.76890725 ),
			vec2( -0.094184101, -0.92938870 ), vec2( 0.34495938, 0.29387760 ),
			vec2( -0.91588581, 0.45771432 ), vec2( -0.81544232, -0.87912464 ),
			vec2( -0.38277543, 0.27676845 ), vec2( 0.97484398, 0.75648379 ),
			vec2( 0.44323325, -0.97511554 ), vec2( 0.53742981, -0.47373420 ),
			vec2( -0.26496911, -0.41893023 ), vec2( 0.79197514, 0.19090188 ),
			vec2( -0.24188840, 0.99706507 ), vec2( -0.81409955, 0.91437590 ),
			vec2( 0.19984126, 0.78641367 ), vec2( 0.14383161, -0.14100790 )
		);

		// 每个像素随机旋转采样盘,消除带状噪声
		float pcssHash( vec2 p ) {
			return fract( sin( dot( p, vec2( 12.9898, 78.233 ) ) ) * 43758.5453 );
		}

		vec2 pcssRotate( vec2 p, float s, float c ) {
			return vec2( p.x * c - p.y * s, p.x * s + p.y * c );
		}

		// 该深度是否比接收点更靠近光源(即是否为阻挡物/遮挡物)
		bool pcssIsOccluder( float depth, float receiverDepth ) {
			#ifdef USE_REVERSED_DEPTH_BUFFER
				return depth > receiverDepth;
			#else
				return depth < receiverDepth;
			#endif
		}

		// 该样本是否受光(1.0 受光 / 0.0 阴影),与 three.js 原生 BASIC 阴影判断一致
		float pcssIsLit( float depth, float receiverDepth ) {
			#ifdef USE_REVERSED_DEPTH_BUFFER
				return step( depth, receiverDepth );
			#else
				return step( receiverDepth, depth );
			#endif
		}

		float getShadow( sampler2D shadowMap, vec2 shadowMapSize, float shadowIntensity, float shadowBias, float shadowRadius, vec4 shadowCoord ) {

			float shadow = 1.0;

			shadowCoord.xyz /= shadowCoord.w;

			#ifdef USE_REVERSED_DEPTH_BUFFER

				shadowCoord.z -= shadowBias;

			#else

				shadowCoord.z += shadowBias;

			#endif

			bool inFrustum = shadowCoord.x >= 0.0 && shadowCoord.x <= 1.0 && shadowCoord.y >= 0.0 && shadowCoord.y <= 1.0;

			if ( inFrustum && shadowCoord.z <= 1.0 ) {

				vec2 texelSize = vec2( 1.0 ) / shadowMapSize;
				float receiverDepth = shadowCoord.z;
				float ang = pcssHash( gl_FragCoord.xy ) * 6.28318530718;
				float s = sin( ang );
				float c = cos( ang );

				// 1. 阻挡物搜索:在搜索盘内平均所有比接收点更近的深度
				float blockerDepthSum = 0.0;
				float blockerCount = 0.0;

				for ( int i = 0; i < PCSS_BLOCKER_SAMPLES; i ++ ) {

					vec2 offset = pcssRotate( pcssPoisson16[ i ], s, c ) * ( PCSS_SEARCH_RADIUS * texelSize );
					float d = texture2D( shadowMap, shadowCoord.xy + offset ).r;

					if ( pcssIsOccluder( d, receiverDepth ) ) {

						blockerDepthSum += d;
						blockerCount += 1.0;

					}

				}

				if ( blockerCount < 0.5 ) {

					shadow = 1.0; // 完全受光

				} else if ( blockerCount > float( PCSS_BLOCKER_SAMPLES ) - 0.5 ) {

					shadow = 0.0; // 完全遮挡(硬阴影)

				} else {

					// 2. 半影估计:阻挡物与接收点深度差越大,半影越软
					float avgBlockerDepth = blockerDepthSum / blockerCount;
					float penumbra = abs( receiverDepth - avgBlockerDepth ) / max( avgBlockerDepth, 1e-4 );
					float filterRadius = clamp( penumbra * pcssLightSize * shadowMapSize.x, PCSS_MIN_RADIUS, PCSS_MAX_RADIUS );

					// 3. PCF:按半影半径采样,平均受光比例
					float lit = 0.0;

					for ( int i = 0; i < PCSS_PCF_SAMPLES; i ++ ) {

						vec2 offset = pcssRotate( pcssPoisson16[ i ], s, c ) * ( filterRadius * texelSize );
						float d = texture2D( shadowMap, shadowCoord.xy + offset ).r;
						lit += pcssIsLit( d, receiverDepth );

					}

					shadow = lit / float( PCSS_PCF_SAMPLES );

				}

			}

			return mix( 1.0, shadow, shadowIntensity );

		}
`

// 幂等覆写 three.js 的 BASIC 阴影分支为 PCSS(防 HMR 重复覆写导致函数重复定义)
function installPcssShadow() {
  const chunk = THREE.ShaderChunk.shadowmap_pars_fragment
  if (chunk.includes(PCSS_SENTINEL)) return
  const sig = 'float getShadow( sampler2D shadowMap,'
  const sigIdx = chunk.lastIndexOf(sig)
  if (sigIdx === -1) return
  const ret = 'return mix( 1.0, shadow, shadowIntensity );'
  const retIdx = chunk.indexOf(ret, sigIdx)
  if (retIdx === -1) return
  const closeIdx = chunk.indexOf('}', retIdx)
  if (closeIdx === -1) return
  THREE.ShaderChunk.shadowmap_pars_fragment = chunk.slice(0, sigIdx) + PCSS_GET_SHADOW + chunk.slice(closeIdx + 1)
}

// PCSS 软硬度统一 uniform:所有材质共享同一对象引用,HUD 滑杆改动后下一帧即生效(无需重编译)
const pcssLightSizeUniform = { value: 0.0015 }

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
const shadowMode = ref('pcss') // 'pcf' = PCF 软阴影 / 'pcss' = PCSS 可变半影(默认)
const pcssLightSize = ref(0.0015) // PCSS 半影软硬度(0~0.0015,越小越硬;默认 0.0015 = 最软)
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
const lampOn = ref(true) // 台灯开关(默认开)
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
let bounceLight = null
let gizmo = null
let gizmoHit = null
let raycaster = null
let dragPlane = null
let windowGroup = null
let sashL = null
let sashR = null
let lampGroup = null
let lampLight = null
let lampShadeMat = null
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
const _skyNight = new THREE.Color(0x0a0c14) // 夜空
const _skyDay = new THREE.Color(0xa8d0f0)   // 白天明亮天空蓝

// 场景常量(单位:m,规格以 cm ÷100 换算;墙在 z=0,室内 z>0,室外/阳台 z<0,窗朝南=-z)
const WALL_Z = 0
const WALL_W = 3.0           // 房间(南墙)宽 300cm(赫鲁晓夫楼小户型)
const WALL_H = 2.5           // 房间高 250cm(老式住宅净高)
const WALL_T = 0.15          // 墙体厚 15cm
const ROOM_DEPTH = 3.0       // 房间进深 300cm

// 平视视线高度 115cm
const EYE_HEIGHT = 1.15

// 窗(外框 110×105、透明区 100.7×95.7、底 80 顶 185;窗框 4.67cm(原 7cm 缩小 1/3);日字形两扇外开)
const WIN_W = 1.1
const WIN_H = 1.05
const WIN_BOTTOM = 0.8
const WIN_CX = 0
const WIN_CY = WIN_BOTTOM + WIN_H / 2
const WIN_GLASS_W = 1.0066
const WIN_GLASS_H = 0.9566
const WIN_FRAME = 0.0467
const SASH_BAR = 0.04        // 窗扇细框 4cm
const MULLION_BAR = 0.04     // 日字形中横棂 4cm

// 书桌(长120×深65×高75,靠窗墙居中)
const DESK_W = 1.2
const DESK_D = 0.65
const DESK_H = 0.75

// 台灯(置于桌面左侧,聚光朝下打亮桌面)
const LAMP_X = -0.48
const LAMP_Z = 0.2
const LAMP_BASE_Y = DESK_H // 桌面顶高 75cm
const LAMP_INTENSITY = 10

const DEFAULT_LIGHT_POS = new THREE.Vector3(0.5, 2.5, -4)

function init() {
  installPcssShadow()

  const container = canvasRef.value
  const w = container.clientWidth || window.innerWidth
  const h = container.clientHeight || window.innerHeight

  renderer = new THREE.WebGLRenderer({ antialias: true })
  renderer.setPixelRatio(Math.min(2, window.devicePixelRatio || 1))
  renderer.setSize(w, h)
  renderer.shadowMap.enabled = shadowOn.value
  renderer.shadowMap.type = shadowMode.value === 'pcss' ? THREE.BasicShadowMap : THREE.PCFShadowMap
  renderer.toneMapping = THREE.ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.12
  renderer.domElement.style.touchAction = 'none'
  container.appendChild(renderer.domElement)

  scene = new THREE.Scene()
  scene.background = new THREE.Color(0x15110d)

  camera = new THREE.PerspectiveCamera(50, w / h, 0.1, 100)
  // 平视,视线高度 115cm;坐在桌前(室内 z>0)看向窗,默认贴近窗景
  camera.position.set(0, EYE_HEIGHT, 1.0)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.target.set(0, EYE_HEIGHT, WALL_Z)
  controls.enableDamping = !reducedMotion.value
  controls.dampingFactor = 0.08
  controls.minDistance = 0.6
  controls.maxDistance = 20
  controls.maxPolarAngle = Math.PI * 0.56
  controls.mouseButtons = { LEFT: THREE.MOUSE.ROTATE, MIDDLE: THREE.MOUSE.DOLLY, RIGHT: THREE.MOUSE.PAN }
  controls.saveState()

  // 环境补光(日/夜在 loop 中调节强度)
  hemi = new THREE.HemisphereLight(0xcfd8ff, 0x2e2419, 0.5)
  scene.add(hemi)

  // 漫反射补光(模拟阳光经地板/家具反弹的间接光,暖色)
  bounceLight = new THREE.AmbientLight(0xffd9b0, 0)
  scene.add(bounceLight)

  // 太阳光(平行光,太阳模拟专用)
  sunLight = new THREE.DirectionalLight(0xfff2da, SUN_BASE_INTENSITY)
  sunLight.castShadow = shadowOn.value
  sunLight.shadow.mapSize.set(2048, 2048)
  sunLight.shadow.camera.near = 1
  sunLight.shadow.camera.far = 30
  sunLight.shadow.camera.left = -5
  sunLight.shadow.camera.right = 5
  sunLight.shadow.camera.top = 5
  sunLight.shadow.camera.bottom = -5
  sunLight.shadow.bias = -0.0002
  sunLight.target.position.set(0, 0.8, 0)
  scene.add(sunLight)
  scene.add(sunLight.target)

  buildFloor()
  buildWall()
  buildSideWalls()
  buildCeiling()
  buildWindow()
  buildHangings()
  buildDesk()
  buildLamp()
  buildTurntable()
  buildPlants()
  buildAwning()
  buildBalcony()
  buildGizmo()

  // 手动光源(初始平行光,默认隐藏;进入手动模式才显示)
  applyLightType()
  syncLightVisibility()

  // 给所有材质注入 PCSS 软硬度 uniform(共享引用,HUD 滑杆改动即时生效)
  scene.traverse((obj) => {
    if (!obj.material) return
    const mats = Array.isArray(obj.material) ? obj.material : [obj.material]
    mats.forEach((m) => {
      m.onBeforeCompile = (shader) => {
        shader.uniforms.pcssLightSize = pcssLightSizeUniform
      }
    })
  })

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
  // 地板:覆盖室内 + 窗外室外一小段地面(地面缩小,但保持不小于房间 3×3)
  const floor = new THREE.Mesh(
    new THREE.PlaneGeometry(5, 5),
    new THREE.MeshStandardMaterial({ color: 0x8a6a4f, roughness: 0.9, metalness: 0 })
  )
  floor.rotation.x = -Math.PI / 2
  floor.position.set(0, 0, 1.5) // 覆盖室内(0~3.0)与窗外室外(z<0)一小段地面
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
  // 挤出墙体厚度,沿 z 居中,形成真实窗洞侧壁(暖白墙面)
  const geo = new THREE.ExtrudeGeometry(shape, { depth: WALL_T, bevelEnabled: false })
  geo.translate(0, 0, -WALL_T / 2)
  const wall = new THREE.Mesh(
    geo,
    new THREE.MeshStandardMaterial({ color: 0xd9cbb0, roughness: 0.95, side: THREE.DoubleSide })
  )
  wall.position.set(0, 0, WALL_Z)
  wall.castShadow = true
  wall.receiveShadow = true
  scene.add(wall)
}

function buildSideWalls() {
  const mat = new THREE.MeshStandardMaterial({ color: 0xc7b795, roughness: 0.95 })
  // 东西侧墙(北侧开敞,不建后墙)
  const sideGeo = new THREE.BoxGeometry(WALL_T, WALL_H, ROOM_DEPTH)
  ;[-WALL_W / 2, WALL_W / 2].forEach((x) => {
    const wall = new THREE.Mesh(sideGeo, mat)
    wall.position.set(x, WALL_H / 2, ROOM_DEPTH / 2)
    wall.castShadow = true
    wall.receiveShadow = true
    scene.add(wall)
  })
}

function buildCeiling() {
  const mat = new THREE.MeshStandardMaterial({ color: 0xe6dcc8, roughness: 0.95 })
  const ceiling = new THREE.Mesh(new THREE.BoxGeometry(WALL_W, WALL_T, ROOM_DEPTH), mat)
  ceiling.position.set(0, WALL_H - WALL_T / 2, ROOM_DEPTH / 2)
  ceiling.castShadow = true
  ceiling.receiveShadow = true
  scene.add(ceiling)
}

function buildDesk() {
  const woodMat = new THREE.MeshStandardMaterial({ color: 0x7a5230, roughness: 0.55 })
  const faceMat = new THREE.MeshStandardMaterial({ color: 0x6b4c30, roughness: 0.5 })
  const knobMat = new THREE.MeshStandardMaterial({ color: 0xc9b27a, roughness: 0.3, metalness: 0.7 })

  // 桌面(120×65cm,厚 5cm),靠墙,桌沿平行窗
  const top = new THREE.Mesh(new THREE.BoxGeometry(DESK_W, 0.05, DESK_D), woodMat)
  top.position.set(0, DESK_H - 0.025, DESK_D / 2)
  top.castShadow = true
  top.receiveShadow = true
  scene.add(top)

  // 四条实木桌腿(7×7cm)
  const legGeo = new THREE.BoxGeometry(0.07, DESK_H - 0.05, 0.07)
  const legH = (DESK_H - 0.05) / 2
  for (const x of [-DESK_W / 2 + 0.05, DESK_W / 2 - 0.05]) {
    for (const z of [0.05, DESK_D - 0.05]) {
      const leg = new THREE.Mesh(legGeo, woodMat)
      leg.position.set(x, legH, z)
      leg.castShadow = true
      leg.receiveShadow = true
      scene.add(leg)
    }
  }

  // 桌下抽屉柜(整条,含左/中/右三抽屉,中间偏宽):挂在桌面下沿前方,底部悬空留出腿部空间
  const bankW = 1.0      // 抽屉柜总宽(略窄于桌面,让开桌腿)
  const bankH = 0.12     // 柜高(原 0.24 减半,抽屉更浅)
  const bankD = 0.42     // 柜深(比桌面浅,后面留空)
  const bankY = DESK_H - 0.05 - bankH / 2 // 柜中心高(顶贴桌面下沿)
  const bankZ = DESK_D - bankD / 2        // 柜中心深(前缘与桌面齐平)
  const bank = new THREE.Mesh(new THREE.BoxGeometry(bankW, bankH, bankD), woodMat)
  bank.position.set(0, bankY, bankZ)
  bank.castShadow = true
  bank.receiveShadow = true
  scene.add(bank)

  // 三个抽屉前脸(略凸出于柜前,中间偏宽)+ 拉手
  const GAP = 0.02
  const MID_W = 0.4
  const SIDE_W = 0.28
  const frontZ = DESK_D + 0.01
  const specs = [
    { w: SIDE_W, x: -(GAP + MID_W / 2 + SIDE_W / 2) },
    { w: MID_W, x: 0 },
    { w: SIDE_W, x: GAP + MID_W / 2 + SIDE_W / 2 },
  ]
  specs.forEach(({ w, x }) => {
    const face = new THREE.Mesh(new THREE.BoxGeometry(w, bankH - 0.03, 0.015), faceMat)
    face.position.set(x, bankY, frontZ)
    face.castShadow = true
    face.receiveShadow = true
    scene.add(face)
    const knob = new THREE.Mesh(new THREE.CylinderGeometry(0.022, 0.022, 0.03, 12), knobMat)
    knob.rotation.x = Math.PI / 2
    knob.position.set(x, bankY, frontZ + 0.02)
    knob.castShadow = true
    scene.add(knob)
  })
  buildDeskItems()
}

// 桌面物品:无序自然倾斜,不严格对齐桌沿
function buildDeskItems() {
  const y0 = DESK_H // 桌面顶 75cm
  // 1. 闭合笔记本(34×23cm,厚 2cm)
  const laptop = new THREE.Mesh(
    new THREE.BoxGeometry(0.34, 0.02, 0.23),
    new THREE.MeshStandardMaterial({ color: 0x3a3f47, roughness: 0.4, metalness: 0.6 })
  )
  laptop.position.set(0.16, y0 + 0.01, 0.4)
  laptop.rotation.y = -0.18
  laptop.castShadow = true
  laptop.receiveShadow = true
  scene.add(laptop)

  // 2. 横版闭合相册(32×26cm,厚 3cm),斜靠笔记本一角
  const album = new THREE.Mesh(
    new THREE.BoxGeometry(0.32, 0.03, 0.26),
    new THREE.MeshStandardMaterial({ color: 0x5d6a4a, roughness: 0.7 })
  )
  album.position.set(-0.16, y0 + 0.02, 0.42)
  album.rotation.y = 0.35
  album.rotation.x = -0.12 // 斜靠一角,微倾
  album.castShadow = true
  album.receiveShadow = true
  scene.add(album)

  // 3. 手机(16×7.5cm,厚 0.8cm),平面轻微旋转
  const phone = new THREE.Mesh(
    new THREE.BoxGeometry(0.16, 0.008, 0.075),
    new THREE.MeshStandardMaterial({ color: 0x1f2329, roughness: 0.3, metalness: 0.5 })
  )
  phone.position.set(0.4, y0 + 0.004, 0.2)
  phone.rotation.y = 0.5
  phone.castShadow = true
  phone.receiveShadow = true
  scene.add(phone)

  // 4. 拍立得照片(8.6×5.4cm,4 张)散落在相册旁,互相轻微叠压
  const photoMat = new THREE.MeshStandardMaterial({ color: 0xf2ede2, roughness: 0.6 })
  const photoGeo = new THREE.BoxGeometry(0.086, 0.002, 0.054)
  const photoSpots = [
    { p: [-0.34, 0.005, 0.34], r: -0.4 },
    { p: [-0.28, 0.004, 0.3], r: 0.3 },
    { p: [-0.26, 0.003, 0.4], r: -0.12 },
    { p: [-0.36, 0.006, 0.26], r: 0.55 },
  ]
  photoSpots.forEach(({ p, r }) => {
    const ph = new THREE.Mesh(photoGeo, photoMat)
    ph.position.set(p[0], y0 + p[1], p[2])
    ph.rotation.y = r
    ph.castShadow = true
    ph.receiveShadow = true
    scene.add(ph)
  })
}

function buildLamp() {
  lampGroup = new THREE.Group()
  const metalMat = new THREE.MeshStandardMaterial({ color: 0x30343c, roughness: 0.4, metalness: 0.7 })
  lampShadeMat = new THREE.MeshStandardMaterial({ color: 0x1e4a3a, roughness: 0.5, metalness: 0.2, emissive: 0x000000, side: THREE.DoubleSide })

  // 底座
  const base = new THREE.Mesh(new THREE.CylinderGeometry(0.11, 0.13, 0.035, 24), metalMat)
  base.position.y = 0.0175
  base.castShadow = true
  base.receiveShadow = true
  // 灯杆
  const arm = new THREE.Mesh(new THREE.CylinderGeometry(0.02, 0.026, 0.4, 16), metalMat)
  arm.position.y = 0.035 + 0.2
  arm.castShadow = true
  // 灯罩(锥体,尖朝上、开口朝下,双面可见)
  const shade = new THREE.Mesh(new THREE.ConeGeometry(0.15, 0.2, 24, 1, true), lampShadeMat)
  shade.position.y = 0.035 + 0.4 + 0.1
  shade.castShadow = true
  lampGroup.add(base, arm, shade)
  lampGroup.position.set(LAMP_X, LAMP_BASE_Y, LAMP_Z)
  scene.add(lampGroup)

  // 台灯光源:聚光灯朝下打向桌面
  lampLight = new THREE.SpotLight(0xffe0b0, LAMP_INTENSITY, 8, 0.7, 0.55, 2)
  lampLight.castShadow = shadowOn.value
  lampLight.shadow.mapSize.set(1024, 1024)
  lampLight.shadow.bias = -0.0004
  lampLight.shadow.camera.near = 0.1
  lampLight.shadow.camera.far = 10
  lampLight.position.set(LAMP_X, LAMP_BASE_Y + 0.4, LAMP_Z)
  lampLight.target.position.set(LAMP_X, 0.72, LAMP_Z + 0.3)
  scene.add(lampLight)
  scene.add(lampLight.target)
  lampLight.visible = lampOn.value
  lampShadeMat.emissive.setHex(lampOn.value ? 0xffc87a : 0x000000)
}

function buildAwning() {
  // 窗上雨棚(悬挑于窗上方;高一点、进深短三分之二)
  const AW = 1.1   // 与窗同宽 110cm
  const AD = 0.33  // 进深 33cm(短三分之二)
  const AT = 0.06  // 板厚 6cm
  const AY = 2.25  // 底面离地 225cm(窗顶 220 之上,高一点)
  const mat = new THREE.MeshStandardMaterial({ color: 0x8a7a68, roughness: 0.85 })
  const awning = new THREE.Mesh(new THREE.BoxGeometry(AW, AT, AD), mat)
  awning.position.set(0, AY + AT / 2, -AD / 2)
  awning.castShadow = true
  awning.receiveShadow = true
  scene.add(awning)
}

function windowBar(w, h, d, cx, cy, cz, mat) {
  const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), mat)
  m.position.set(cx, cy, cz)
  m.castShadow = true
  m.receiveShadow = true
  return m
}

function buildWindow() {
  const frameMat = new THREE.MeshStandardMaterial({ color: 0x6b4a2f, roughness: 0.5 })
  const glassMat = new THREE.MeshStandardMaterial({
    color: 0xbfd8e8, roughness: 0.05, metalness: 0.1,
    transparent: true, opacity: 0.18, side: THREE.DoubleSide,
  })

  windowGroup = new THREE.Group()
  windowGroup.position.set(0, 0, WALL_Z)
  const D = WALL_T
  const f = WIN_FRAME

  // 外窗框(固定):上下左右四根 4.67cm 框,围成 110×105
  const addBar = (w, h, cx, cy) => windowGroup.add(windowBar(w, h, D, cx, cy, 0, frameMat))
  addBar(WIN_W, f, WIN_CX, WIN_BOTTOM + WIN_H - f / 2)
  addBar(WIN_W, f, WIN_CX, WIN_BOTTOM + f / 2)
  addBar(f, WIN_H, WIN_CX - WIN_W / 2 + f / 2, WIN_CY)
  addBar(f, WIN_H, WIN_CX + WIN_W / 2 - f / 2, WIN_CY)

  // 两扇日字形向外平开(透明区各 50.3×95.7,中横棂分上下两格),左扇合页在左、右扇合页在右,由中间向外推
  sashL = buildSash(frameMat, glassMat)
  sashL.position.set(WIN_CX - WIN_W / 2 + f, 0, 0)
  windowGroup.add(sashL)

  sashR = buildSash(frameMat, glassMat)
  sashR.scale.x = -1
  sashR.position.set(WIN_CX + WIN_W / 2 - f, 0, 0)
  windowGroup.add(sashR)

  scene.add(windowGroup)

  // 初始即按滑杆角度摆好,避免首帧从 0° 突跳
  currentOpen = windowOpen.value
  sashL.rotation.y = currentOpen * RAD
  sashR.rotation.y = -currentOpen * RAD
}

function buildSash(frameMat, glassMat) {
  const g = new THREE.Group()
  const SW = WIN_GLASS_W / 2 // 单扇宽 50.3cm
  const SH = WIN_GLASS_H
  const SB = SASH_BAR
  const MB = MULLION_BAR
  const y0 = WIN_BOTTOM + (WIN_H - WIN_GLASS_H) / 2 // 玻璃下缘(与窗框对齐)
  const y1 = y0 + SH
  const ym = y0 + SH / 2

  // 四根细扇框(合页梃 / 中缝梃 / 上梃 / 下梃)
  g.add(windowBar(SB, SH, SB, SB / 2, ym, 0, frameMat))          // 合页梃(左)
  g.add(windowBar(SB, SH, SB, SW - SB / 2, ym, 0, frameMat))     // 中缝梃(右)
  g.add(windowBar(SW, SB, SB, SW / 2, y1 - SB / 2, 0, frameMat)) // 上梃
  g.add(windowBar(SW, SB, SB, SW / 2, y0 + SB / 2, 0, frameMat)) // 下梃
  // 日字形中横棂(把扇分成上下两格)
  g.add(windowBar(SW, MB, SB, SW / 2, ym, 0, frameMat))

  // 玻璃:上下两格,透明不投影(阳光穿过)
  const gw = SW - SB * 2
  const gh = (SH - SB * 2 - MB) / 2
  const glassGeo = new THREE.PlaneGeometry(gw, gh)
  const top = new THREE.Mesh(glassGeo, glassMat)
  top.position.set(SW / 2, ym + MB / 2 + gh / 2, 0)
  const bot = new THREE.Mesh(glassGeo, glassMat)
  bot.position.set(SW / 2, ym - MB / 2 - gh / 2, 0)
  g.add(top, bot)
  return g
}

function buildHangings() {
  // 窗户侧边墙面:软木便签框(右,横版 70×45)
  const cork = new THREE.Mesh(
    new THREE.BoxGeometry(0.7, 0.45, 0.02),
    new THREE.MeshStandardMaterial({ color: 0xc9a06a, roughness: 0.9 })
  )
  cork.position.set(0.9, 1.95, WALL_T / 2 + 0.01)
  cork.castShadow = true
  cork.receiveShadow = true
  scene.add(cork)
  // 户型图便利框(左,竖版 50×60)
  const plan = new THREE.Mesh(
    new THREE.BoxGeometry(0.5, 0.6, 0.02),
    new THREE.MeshStandardMaterial({ color: 0x8a5a30, roughness: 0.7 })
  )
  plan.position.set(-0.85, 1.55, WALL_T / 2 + 0.01)
  plan.castShadow = true
  plan.receiveShadow = true
  scene.add(plan)
}

function buildTurntable() {
  // 唱片机机身(42×35×22cm),桌子右侧地面
  const body = new THREE.Mesh(
    new THREE.BoxGeometry(0.42, 0.22, 0.35),
    new THREE.MeshStandardMaterial({ color: 0x5a4632, roughness: 0.6 })
  )
  body.position.set(0.9, 0.11, 0.5)
  body.castShadow = true
  body.receiveShadow = true
  scene.add(body)

  // 2-3 张黑胶唱片(直径 30cm,厚 0.6cm),两张斜靠机身、一张平放
  const recMat = new THREE.MeshStandardMaterial({ color: 0x14161a, roughness: 0.4, metalness: 0.3 })
  const recGeo = new THREE.CylinderGeometry(0.15, 0.15, 0.006, 32)
  const lean = new THREE.Mesh(recGeo, recMat)
  lean.position.set(1.15, 0.12, 0.65)
  lean.rotation.x = Math.PI / 2 - 0.35
  lean.rotation.z = 0.2
  lean.castShadow = true
  lean.receiveShadow = true
  scene.add(lean)
  const lean2 = new THREE.Mesh(recGeo, recMat)
  lean2.position.set(1.17, 0.1, 0.7)
  lean2.rotation.x = Math.PI / 2 - 0.3
  lean2.rotation.z = 0.32
  lean2.castShadow = true
  lean2.receiveShadow = true
  scene.add(lean2)
  const flat = new THREE.Mesh(recGeo, recMat)
  flat.position.set(0.95, 0.003, 0.9)
  flat.rotation.x = Math.PI / 2
  flat.castShadow = true
  flat.receiveShadow = true
  scene.add(flat)
}

function buildPlants() {
  const clayMat = new THREE.MeshStandardMaterial({ color: 0xa85a38, roughness: 0.8 })
  const leafMat = new THREE.MeshStandardMaterial({ color: 0x3f6b3a, roughness: 0.9 })

  // 唱片机旁地面盆栽:花盆直径 18cm,植株总高 60cm
  const pot1 = new THREE.Mesh(new THREE.CylinderGeometry(0.09, 0.075, 0.18, 24), clayMat)
  pot1.position.set(1.15, 0.09, 1.3)
  pot1.castShadow = true
  pot1.receiveShadow = true
  scene.add(pot1)
  const leaf1 = new THREE.Mesh(new THREE.SphereGeometry(0.22, 20, 16), leafMat)
  leaf1.position.set(1.15, 0.5, 1.3)
  leaf1.castShadow = true
  leaf1.receiveShadow = true
  scene.add(leaf1)

  // 桌角小盆栽:花盆直径 10cm,植株高 25cm
  const pot2 = new THREE.Mesh(new THREE.CylinderGeometry(0.05, 0.04, 0.1, 18), clayMat)
  pot2.position.set(-0.45, DESK_H + 0.05, 0.5)
  pot2.castShadow = true
  pot2.receiveShadow = true
  scene.add(pot2)
  const leaf2 = new THREE.Mesh(new THREE.SphereGeometry(0.09, 14, 12), leafMat)
  leaf2.position.set(-0.45, DESK_H + 0.21, 0.5)
  leaf2.castShadow = true
  leaf2.receiveShadow = true
  scene.add(leaf2)
}

function buildBalcony() {
  // 窗外阳台(生产结构):悬挑于窗下、比窗台稍低,配一盆绿植
  const mat = new THREE.MeshStandardMaterial({ color: 0x6e5c4a, roughness: 0.8 })
  const BW = 1.6  // 阳台宽(略宽于窗 1.1)
  const BD = 0.55 // 阳台深(向外)
  const BT = 0.1  // 板厚
  const TOP = WIN_BOTTOM - 0.08 // 阳台顶比窗底稍低
  const BZ = WALL_Z - BD / 2
  const balcony = new THREE.Mesh(new THREE.BoxGeometry(BW, BT, BD), mat)
  balcony.position.set(0, TOP - BT / 2, BZ)
  balcony.castShadow = true
  balcony.receiveShadow = true
  const potMat = new THREE.MeshStandardMaterial({ color: 0xb0603a, roughness: 0.7 })
  const pot = new THREE.Mesh(new THREE.CylinderGeometry(0.16, 0.13, 0.28, 24), potMat)
  pot.position.set(-0.4, TOP + 0.14, BZ)
  pot.castShadow = true
  pot.receiveShadow = true
  const leafMat = new THREE.MeshStandardMaterial({ color: 0x3f6b3a, roughness: 0.9 })
  const leaf = new THREE.Mesh(new THREE.SphereGeometry(0.16, 16, 12), leafMat)
  leaf.position.set(-0.4, TOP + 0.14 + 0.22, BZ)
  leaf.castShadow = true
  leaf.receiveShadow = true
  scene.add(balcony, pot, leaf)
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

  // 白昼因子:从 -3°(晨昏蒙影)到 12°(日出后)平滑过渡——白天整体环境变亮、天空变蓝,区别于黑夜
  const day = THREE.MathUtils.smoothstep(latestSun.altitude, -3, 12)
  hemi.intensity = THREE.MathUtils.lerp(0.14, 1.0, day)
  scene.background.lerpColors(_skyNight, _skyDay, day)

  // 漫反射补光:太阳越高、窗开得越大,室内反弹光越亮(暖色,模拟地板/家具漫反射)
  const sunUp = Math.max(0, Math.sin(latestSun.altitude * RAD))
  const winOpen = Math.sin(currentOpen * RAD) // 0(关) ~ 1(全开)
  bounceLight.intensity = 0.4 * sunUp * (0.4 + 0.6 * winOpen)
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

function applyShadowMode() {
  if (!renderer) return
  // three.js 检测到 type 变化会自动重建深度纹理 + 重编译材质(sampler2DShadow ↔ sampler2D)
  renderer.shadowMap.type = shadowMode.value === 'pcss' ? THREE.BasicShadowMap : THREE.PCFShadowMap
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

watch(shadowMode, () => {
  applyShadowMode()
})

watch(pcssLightSize, (v) => {
  pcssLightSizeUniform.value = v
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
