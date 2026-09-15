<!-- 3D 光影实验台:真实阴影贴图演示「太阳 → 带两扇平开窗的南墙 → 书桌空间」遮挡投影。
     交互:OrbitControls 左键旋转/右键平移/滚轮缩放;拖动发光小球移动光源。
     太阳模拟:客户端 NOAA 算法(见 utils/solarPosition.js,与后端 SolarUtil 同源),按日期+经纬度(默认济南)+
               时区 UTC+8 计算高度角/方位角,驱动平行太阳光;支持播放/暂停/加速/步进;窗朝南=方位角 180°(-z)。
     场景:坐北朝南小房间(净宽 4.8×深 4.2×高 2.8m,地面 Y=0),南墙两扇日字形外推平开窗(总宽 2.4×高 1.6,底 0.9)
           + 窗外小阳台(平台顶 0.75、护栏 0.30、两盆花);尺寸 m,墙在 z=0、室内 z>0、窗外阳台 z<0、Y 向上;
           北侧开敞(无后墙)、桌面台灯可开关、暖色漫反射补光随太阳高度/开窗增大;PCSS/PCF 阴影 + 阴影软硬可调。
           中古风配色:暖灰米色墙、深棕黑实木窗框、深胡桃木书桌、布艺台灯。
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

// 场景常量(单位:m;坐北朝南小房间,Y 向上;南墙(窗墙)在 z=0、室内 z>0、窗外阳台 z<0,窗朝南=-z)
const WALL_Z = 0
const WALL_W = 4.8           // 房间净宽(X)480cm
const WALL_H = 2.8           // 房间净高(Y)280cm(地面 Y=0,天花板 Y=2.8)
const WALL_T = 0.15          // 墙体厚 15cm
const ROOM_DEPTH = 4.2       // 房间净深(Z)420cm

// 窗(两扇日字形外推平开,左右各一扇、单扇带中间横向分格;总宽 240×高 160、底 90 顶 250,水平居中)
const WIN_W = 2.4
const WIN_H = 1.6
const WIN_BOTTOM = 0.9
const WIN_CX = 0
const WIN_CY = WIN_BOTTOM + WIN_H / 2
const WIN_FRAME = 0.12       // 外窗框 12cm(加厚)
const SASH_BAR = 0.07        // 窗棂/扇框 7cm(加粗)
const MULLION_BAR = 0.07     // 日字形中横棂 7cm(加粗)

// 书桌(长 270×深 70×高 75,靠窗墙居中,略宽于窗;三抽屉朝室内:中 1 大 + 左右 2 小对称)
const DESK_W = 2.7
const DESK_D = 0.7
const DESK_H = 0.75

// 桌下抽屉柜(整条柜体:高 15cm,顶贴桌面下沿 5cm 处;三扇抽屉面板朝室内 + 拉手)
const DRAWER_BANK_H = 0.15

// 阳台(窗外南侧:平台顶面 0.75、宽 260、进深 80、护栏高 30;左右两盆圆柱花盆)
const BALCONY_W = 2.6
const BALCONY_D = 0.8
const BALCONY_TOP = 0.75
const BALCONY_T = 0.12        // 平台板厚
const BALCONY_RAIL = 0.30     // 护栏高(平台顶面以上,矮护栏)

// 台灯(置于桌面左侧,聚光朝下打亮桌面)
const LAMP_X = -1.05
const LAMP_Z = 0.25
const LAMP_BASE_Y = DESK_H // 桌面顶高 75cm
const LAMP_INTENSITY = 10

// 默认相机(房间北侧朝南拍,完整框住窗户+整张桌子+抽屉,抽屉靠近画面底部、上下留白协调)
const CAMERA_FOV = 36
const CAMERA_NEAR = 0.1
const CAMERA_FAR = 20
const CAMERA_POS = new THREE.Vector3(0, 1.5, 3.6)
const CAMERA_TARGET = new THREE.Vector3(0, 1.4, 0) // 收窄 fov + 上抬目标,抽屉贴底、下方少留白

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

  camera = new THREE.PerspectiveCamera(CAMERA_FOV, w / h, CAMERA_NEAR, CAMERA_FAR)
  // 默认机位:房间北侧朝南拍,完整框住窗户+整张桌子+抽屉,上下留白
  camera.position.copy(CAMERA_POS)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.target.copy(CAMERA_TARGET)
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
  buildBalcony()
  buildDesk()
  buildLamp()
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

// 墙面极淡肌理:程序化噪点 bump 贴图(一次性生成,共享)
let wallBumpTex = null
function getWallBumpTex() {
  if (wallBumpTex) return wallBumpTex
  const size = 128
  const c = document.createElement('canvas')
  c.width = c.height = size
  const ctx = c.getContext('2d')
  const img = ctx.createImageData(size, size)
  for (let i = 0; i < img.data.length; i += 4) {
    const n = 128 + (Math.random() - 0.5) * 18
    img.data[i] = img.data[i + 1] = img.data[i + 2] = n
    img.data[i + 3] = 255
  }
  ctx.putImageData(img, 0, 0)
  wallBumpTex = new THREE.CanvasTexture(c)
  wallBumpTex.wrapS = wallBumpTex.wrapT = THREE.RepeatWrapping
  wallBumpTex.repeat.set(5, 5)
  return wallBumpTex
}

function buildFloor() {
  // 地板:覆盖室内(0~4.2)+ 窗外阳台下方一小段地面
  const floor = new THREE.Mesh(
    new THREE.PlaneGeometry(6, 6),
    new THREE.MeshStandardMaterial({ color: 0xc8a06b, roughness: 0.85, metalness: 0 })
  )
  floor.rotation.x = -Math.PI / 2
  floor.position.set(0, 0, 2.0) // 覆盖 x∈[-3,3]、z∈[-1,5]
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
    new THREE.MeshStandardMaterial({ color: 0xe0d5c0, roughness: 0.95, side: THREE.DoubleSide, bumpMap: getWallBumpTex(), bumpScale: 0.4 })
  )
  wall.position.set(0, 0, WALL_Z)
  wall.castShadow = true
  wall.receiveShadow = true
  scene.add(wall)
}

function buildSideWalls() {
  const mat = new THREE.MeshStandardMaterial({ color: 0xe0d5c0, roughness: 0.95, bumpMap: getWallBumpTex(), bumpScale: 0.4 })
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
  const mat = new THREE.MeshStandardMaterial({ color: 0xe0d5c0, roughness: 0.95, bumpMap: getWallBumpTex(), bumpScale: 0.4 })
  const ceiling = new THREE.Mesh(new THREE.BoxGeometry(WALL_W, WALL_T, ROOM_DEPTH), mat)
  ceiling.position.set(0, WALL_H - WALL_T / 2, ROOM_DEPTH / 2)
  ceiling.castShadow = true
  ceiling.receiveShadow = true
  scene.add(ceiling)
}

function buildDesk() {
  const woodMat = new THREE.MeshStandardMaterial({ color: 0x5b3a26, roughness: 0.55 })
  const drawerMat = new THREE.MeshStandardMaterial({ color: 0x4f331f, roughness: 0.5 })
  const handleMat = new THREE.MeshStandardMaterial({ color: 0xb08d57, roughness: 0.35, metalness: 0.75 })

  // 桌面(270×70cm,厚 5cm),靠窗墙,桌沿平行窗
  const top = new THREE.Mesh(new THREE.BoxGeometry(DESK_W, 0.05, DESK_D), woodMat)
  top.position.set(0, DESK_H - 0.025, DESK_D / 2)
  top.castShadow = true
  top.receiveShadow = true
  scene.add(top)

  // 桌面边缘细微磨损(前缘浅色磨边)
  const wearEdge = new THREE.Mesh(
    new THREE.BoxGeometry(DESK_W, 0.018, 0.008),
    new THREE.MeshStandardMaterial({ color: 0x8a6644, roughness: 0.7 })
  )
  wearEdge.position.set(0, DESK_H - 0.02, DESK_D)
  wearEdge.castShadow = true
  scene.add(wearEdge)

  // 四条桌腿(6×6cm)
  const legGeo = new THREE.BoxGeometry(0.06, DESK_H - 0.05, 0.06)
  const legH = (DESK_H - 0.05) / 2
  for (const x of [-DESK_W / 2 + 0.04, DESK_W / 2 - 0.04]) {
    for (const z of [0.04, DESK_D - 0.04]) {
      const leg = new THREE.Mesh(legGeo, woodMat)
      leg.position.set(x, legH, z)
      leg.castShadow = true
      leg.receiveShadow = true
      scene.add(leg)
    }
  }

  // 桌下抽屉柜(双抽屉,整条柜体挂在桌面下沿前方,朝室内 +z)
  const bankW = DESK_W - 0.16        // 略窄于桌面,让开两侧桌腿
  const bankH = DRAWER_BANK_H        // 柜高
  const bankD = 0.45                 // 柜深(比桌面浅,后面留空)
  const bankY = DESK_H - 0.05 - bankH / 2 // 柜中心高(顶贴桌面下沿)
  const bankZ = DESK_D - bankD / 2        // 柜中心深(前缘与桌面齐平)
  const bank = new THREE.Mesh(new THREE.BoxGeometry(bankW, bankH, bankD), woodMat)
  bank.position.set(0, bankY, bankZ)
  bank.castShadow = true
  bank.receiveShadow = true
  scene.add(bank)

  // 三扇抽屉面板(中间 1 大 + 左右 2 小对称,略凸出于柜前,朝室内)+ 横向拉手
  const GAP = 0.02
  const MID_W = 1.10
  const SIDE_W = 0.68
  const frontZ = DESK_D + 0.012
  const specs = [
    { w: SIDE_W, x: -(MID_W / 2 + GAP + SIDE_W / 2) },
    { w: MID_W, x: 0 },
    { w: SIDE_W, x: MID_W / 2 + GAP + SIDE_W / 2 },
  ]
  specs.forEach(({ w, x }) => {
    const face = new THREE.Mesh(new THREE.BoxGeometry(w, bankH - 0.03, 0.016), drawerMat)
    face.position.set(x, bankY, frontZ)
    face.castShadow = true
    face.receiveShadow = true
    scene.add(face)
    const handle = new THREE.Mesh(new THREE.BoxGeometry(w * 0.5, 0.025, 0.035), handleMat)
    handle.position.set(x, bankY, frontZ + 0.03)
    handle.castShadow = true
    scene.add(handle)
  })
  buildDeskItems()
}

// 桌面物品:笔记本电脑/手机/硬壳相册/4 张拍立得照片,自然散放不拥挤(台灯由 buildLamp 单独摆放)
function buildDeskItems() {
  const y0 = DESK_H // 桌面顶 75cm
  // 1. 笔记本电脑(翻开:底座 + 后仰屏幕),桌面右半侧
  const laptop = new THREE.Group()
  const laptopBase = new THREE.Mesh(
    new THREE.BoxGeometry(0.34, 0.015, 0.23),
    new THREE.MeshStandardMaterial({ color: 0x3a3f47, roughness: 0.4, metalness: 0.6 })
  )
  laptopBase.position.y = 0.0075
  laptopBase.castShadow = true
  laptopBase.receiveShadow = true
  const hinge = new THREE.Group()
  hinge.position.set(0, 0.015, -0.115)
  const laptopScreen = new THREE.Mesh(
    new THREE.BoxGeometry(0.34, 0.24, 0.008),
    new THREE.MeshStandardMaterial({ color: 0x1f2329, roughness: 0.3, metalness: 0.4 })
  )
  laptopScreen.position.y = 0.12
  laptopScreen.castShadow = true
  hinge.add(laptopScreen)
  hinge.rotation.x = -0.3 // 屏幕后仰约 107°
  laptop.add(laptopBase, hinge)
  laptop.position.set(0.35, y0 + 0.005, 0.32)
  laptop.rotation.y = -0.12
  scene.add(laptop)

  // 2. 硬壳相册(30×24cm,厚 3.5cm),桌面左侧
  const album = new THREE.Mesh(
    new THREE.BoxGeometry(0.3, 0.035, 0.24),
    new THREE.MeshStandardMaterial({ color: 0x6e4a30, roughness: 0.6 })
  )
  album.position.set(-0.45, y0 + 0.02, 0.4)
  album.rotation.y = 0.3
  album.castShadow = true
  album.receiveShadow = true
  scene.add(album)

  // 3. 手机(16×7.5cm,厚 0.8cm),桌面右侧
  const phone = new THREE.Mesh(
    new THREE.BoxGeometry(0.16, 0.008, 0.075),
    new THREE.MeshStandardMaterial({ color: 0x1f2329, roughness: 0.3, metalness: 0.5 })
  )
  phone.position.set(1.0, y0 + 0.004, 0.2)
  phone.rotation.y = 0.5
  phone.castShadow = true
  phone.receiveShadow = true
  scene.add(phone)

  // 4. 拍立得照片(8.6×5.4cm,4 张)散落在相册旁,轻微旋转叠压
  const photoMat = new THREE.MeshStandardMaterial({ color: 0xf2ede2, roughness: 0.6 })
  const photoGeo = new THREE.BoxGeometry(0.086, 0.002, 0.054)
  const photoSpots = [
    { p: [-0.6, 0.005, 0.28], r: -0.4 },
    { p: [-0.52, 0.004, 0.24], r: 0.3 },
    { p: [-0.48, 0.003, 0.5], r: -0.15 },
    { p: [-0.66, 0.006, 0.46], r: 0.55 },
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
  const woodMat = new THREE.MeshStandardMaterial({ color: 0x5b3a26, roughness: 0.55 })
  const brassMat = new THREE.MeshStandardMaterial({ color: 0xc9a05a, roughness: 0.35, metalness: 0.8 })
  lampShadeMat = new THREE.MeshStandardMaterial({ color: 0xd8c8a8, roughness: 0.95, metalness: 0, emissive: 0x000000, side: THREE.DoubleSide })
  const trimMat = new THREE.MeshStandardMaterial({ color: 0xb89a6a, roughness: 0.9 })

  // 圆形厚重胡桃木底座(直径 18cm,厚 3.5cm)
  const base = new THREE.Mesh(new THREE.CylinderGeometry(0.09, 0.09, 0.035, 32), woodMat)
  base.position.y = 0.0175
  base.castShadow = true
  base.receiveShadow = true
  // 略带弯曲的黄铜细杆(直径 2.5cm,总高 40cm)
  const curve = new THREE.QuadraticBezierCurve3(
    new THREE.Vector3(0, 0, 0),
    new THREE.Vector3(0.015, 0.2, 0),
    new THREE.Vector3(0, 0.4, 0)
  )
  const pole = new THREE.Mesh(new THREE.TubeGeometry(curve, 12, 0.0125, 12, false), brassMat)
  pole.position.y = 0.035
  pole.castShadow = true
  // 倒梯形截顶圆锥布艺灯罩(上口 14cm、下口 21cm、高 15cm,米卡其色,下缘收边)
  const shade = new THREE.Mesh(new THREE.CylinderGeometry(0.07, 0.105, 0.15, 32, 1, true), lampShadeMat)
  shade.position.y = 0.035 + 0.4 + 0.075
  shade.castShadow = true
  const trim = new THREE.Mesh(new THREE.TorusGeometry(0.105, 0.008, 12, 32), trimMat)
  trim.rotation.x = Math.PI / 2
  trim.position.y = 0.035 + 0.4
  trim.castShadow = true
  lampGroup.add(base, pole, shade, trim)
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

function windowBar(w, h, d, cx, cy, cz, mat) {
  const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), mat)
  m.position.set(cx, cy, cz)
  m.castShadow = true
  m.receiveShadow = true
  return m
}

function buildWindow() {
  const frameMat = new THREE.MeshStandardMaterial({ color: 0x241a12, roughness: 0.55, metalness: 0 })
  const glassMat = new THREE.MeshStandardMaterial({
    color: 0xcfe4f0, roughness: 0.04, metalness: 0.1,
    transparent: true, opacity: 0.16, side: THREE.DoubleSide,
  })

  windowGroup = new THREE.Group()
  windowGroup.position.set(0, 0, WALL_Z)
  const D = WALL_T
  const f = WIN_FRAME

  // 外窗框(固定):上下左右四根 12cm 框,围成 240×160
  const addBar = (w, h, cx, cy) => windowGroup.add(windowBar(w, h, D, cx, cy, 0, frameMat))
  addBar(WIN_W, f, WIN_CX, WIN_BOTTOM + WIN_H - f / 2)
  addBar(WIN_W, f, WIN_CX, WIN_BOTTOM + f / 2)
  addBar(f, WIN_H, WIN_CX - WIN_W / 2 + f / 2, WIN_CY)
  addBar(f, WIN_H, WIN_CX + WIN_W / 2 - f / 2, WIN_CY)

  // 两扇日字形向外平开(各 108 宽,中横棂分上下两格),左扇合页在左、右扇合页在右,由中间向外推
  sashL = buildSash(frameMat, glassMat)
  sashL.position.set(WIN_CX - WIN_W / 2 + f, WIN_BOTTOM + f, 0)
  windowGroup.add(sashL)

  sashR = buildSash(frameMat, glassMat)
  sashR.scale.x = -1
  sashR.position.set(WIN_CX + WIN_W / 2 - f, WIN_BOTTOM + f, 0)
  windowGroup.add(sashR)

  scene.add(windowGroup)

  // 初始即按滑杆角度摆好,避免首帧从 0° 突跳
  currentOpen = windowOpen.value
  sashL.rotation.y = currentOpen * RAD
  sashR.rotation.y = -currentOpen * RAD
}

function buildSash(frameMat, glassMat) {
  const g = new THREE.Group()
  const SW = WIN_W / 2 - WIN_FRAME   // 单扇总宽 108cm(外框内净半宽)
  const SH = WIN_H - WIN_FRAME * 2   // 单扇总高 136cm(外框内净高)
  const GW = SW - SASH_BAR * 2       // 单扇玻璃宽 94cm
  const GH = SH - SASH_BAR * 2       // 单扇玻璃高 122cm
  const SB = SASH_BAR
  const MB = MULLION_BAR
  const topY = SH - SB / 2  // 上梃中心(扇内)
  const botY = SB / 2       // 下梃中心
  const ym = SH / 2         // 竖直中线

  // 四根细扇框(合页梃在左 x=0 侧 / 中缝梃在右 / 上梃 / 下梃)
  g.add(windowBar(SB, GH, SB, SB / 2, ym, 0, frameMat))          // 合页梃(左)
  g.add(windowBar(SB, GH, SB, SW - SB / 2, ym, 0, frameMat))     // 中缝梃(右)
  g.add(windowBar(SW, SB, SB, SW / 2, topY, 0, frameMat))        // 上梃
  g.add(windowBar(SW, SB, SB, SW / 2, botY, 0, frameMat))        // 下梃
  // 日字形中横棂(把扇分成上下两格)
  g.add(windowBar(SW, MB, SB, SW / 2, ym, 0, frameMat))

  // 玻璃:上下两格,透明不投影(阳光穿过)
  const paneH = (GH - MB) / 2
  const glassGeo = new THREE.PlaneGeometry(GW, paneH)
  const top = new THREE.Mesh(glassGeo, glassMat)
  top.position.set(SW / 2, ym + MB / 2 + paneH / 2, 0)
  const bot = new THREE.Mesh(glassGeo, glassMat)
  bot.position.set(SW / 2, ym - MB / 2 - paneH / 2, 0)
  g.add(top, bot)
  return g
}

function buildHangings() {
  // 窗户左侧:横版户型图装饰画框
  const frameMat = new THREE.MeshStandardMaterial({ color: 0x3d2b1a, roughness: 0.6 })
  const canvasMat = new THREE.MeshStandardMaterial({ color: 0xefe7d6, roughness: 0.8 })
  const frame = new THREE.Mesh(new THREE.BoxGeometry(0.6, 0.45, 0.03), frameMat)
  frame.position.set(-1.8, 1.7, WALL_T / 2 + 0.01)
  frame.castShadow = true
  frame.receiveShadow = true
  scene.add(frame)
  const canvas = new THREE.Mesh(new THREE.BoxGeometry(0.52, 0.37, 0.012), canvasMat)
  canvas.position.set(-1.8, 1.7, WALL_T / 2 + 0.035)
  scene.add(canvas)

  // 窗户右侧:软木板 + 若干便利贴
  const corkMat = new THREE.MeshStandardMaterial({ color: 0xc9a06a, roughness: 0.9 })
  const cork = new THREE.Mesh(new THREE.BoxGeometry(0.7, 0.5, 0.03), corkMat)
  cork.position.set(1.8, 1.7, WALL_T / 2 + 0.01)
  cork.castShadow = true
  cork.receiveShadow = true
  scene.add(cork)

  const noteColors = [0xc8a97a, 0x9c7a4a, 0xa86a4a, 0x7a6a52]
  const noteGeo = new THREE.BoxGeometry(0.09, 0.09, 0.006)
  const noteSpots = [
    { p: [-0.05, 0.12], r: -0.12 },
    { p: [0.12, -0.05], r: 0.18 },
    { p: [-0.18, -0.08], r: 0.08 },
    { p: [0.06, 0.16], r: -0.05 },
  ]
  noteSpots.forEach((n, i) => {
    const note = new THREE.Mesh(
      noteGeo,
      new THREE.MeshStandardMaterial({ color: noteColors[i % noteColors.length], roughness: 0.7 })
    )
    note.position.set(1.8 + n.p[0], 1.7 + n.p[1], WALL_T / 2 + 0.03)
    note.rotation.z = n.r
    scene.add(note)
  })
}

function buildBalcony() {
  // 窗外小阳台:平台顶面 0.75(略低于窗底 0.9)、护栏 0.30、左右两盆圆柱花盆
  const slabMat = new THREE.MeshStandardMaterial({ color: 0xa6a6a6, roughness: 0.9 })
  const railMat = new THREE.MeshStandardMaterial({ color: 0x4a4a4a, roughness: 0.5, metalness: 0.5 })
  const T = 0.035

  const bz = WALL_Z - BALCONY_D / 2            // 平台中心 z=-0.4
  const frontZ = WALL_Z - BALCONY_D            // 外沿 z=-0.8
  const railTop = BALCONY_TOP + BALCONY_RAIL   // 护栏顶 1.05
  const railMid = BALCONY_TOP + BALCONY_RAIL / 2 // 护栏中 0.9

  // 平台板(浅灰水泥,宽 260×深 80×厚 12)
  const slab = new THREE.Mesh(new THREE.BoxGeometry(BALCONY_W, BALCONY_T, BALCONY_D), slabMat)
  slab.position.set(0, BALCONY_TOP - BALCONY_T / 2, bz)
  slab.castShadow = true
  slab.receiveShadow = true
  scene.add(slab)

  const addRail = (w, h, d, x, y, z) => {
    const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), railMat)
    m.position.set(x, y, z)
    m.castShadow = true
    m.receiveShadow = true
    scene.add(m)
  }

  // 护栏:顶部扶手 + 中部横杆(U 形:前 + 左右)
  addRail(BALCONY_W, T, T, 0, railTop, frontZ + T / 2)
  addRail(T, T, BALCONY_D, -BALCONY_W / 2 + T / 2, railTop, bz)
  addRail(T, T, BALCONY_D, BALCONY_W / 2 - T / 2, railTop, bz)
  addRail(BALCONY_W, T, T, 0, railMid, frontZ + T / 2)
  // 前缘竖向栏杆(6 根)
  const balCount = 6
  for (let i = 0; i < balCount; i++) {
    const x = -BALCONY_W / 2 + T + ((BALCONY_W - T * 2) / (balCount - 1)) * i
    addRail(T, BALCONY_RAIL, T, x, railMid, frontZ + T / 2)
  }
  // 两侧中间竖杆
  addRail(T, BALCONY_RAIL, T, -BALCONY_W / 2 + T / 2, railMid, bz - BALCONY_D / 2 + T / 2)
  addRail(T, BALCONY_RAIL, T, BALCONY_W / 2 - T / 2, railMid, bz - BALCONY_D / 2 + T / 2)

  // 两盆圆柱花盆(哑光陶土),左右各一
  const potMat = new THREE.MeshStandardMaterial({ color: 0xa85230, roughness: 0.85 })
  const plantMat = new THREE.MeshStandardMaterial({ color: 0x4a7c46, roughness: 0.9 })
  const potH = 0.28
  const potR = 0.16
  ;[-1, 1].forEach((s) => {
    const px = s * 0.85
    const pot = new THREE.Mesh(new THREE.CylinderGeometry(potR, potR * 0.82, potH, 24), potMat)
    pot.position.set(px, BALCONY_TOP + potH / 2, bz)
    pot.castShadow = true
    pot.receiveShadow = true
    const leaf = new THREE.Mesh(new THREE.SphereGeometry(0.16, 16, 12), plantMat)
    leaf.position.set(px, BALCONY_TOP + potH + 0.1, bz)
    leaf.castShadow = true
    leaf.receiveShadow = true
    scene.add(pot, leaf)
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
