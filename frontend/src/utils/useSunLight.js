// 光影系统全局组合式函数:太阳位置 + 台灯 + 钟摆 + 时隙更新
// 从 Home.vue 提取,供 SunLightLayer.vue 和全局使用
// 用法:App.vue 调用 useSunLight() 并 provide(SUN_LIGHT_KEY, state)
//       SunLightLayer/AppSidebar 用 inject(SUN_LIGHT_KEY) 获取同一实例
import { ref, reactive, computed, watch, onMounted, onUnmounted, watchEffect } from 'vue'
import { gsap } from 'gsap'
import { getSunScene, currentSlotIndex } from '@/utils/windowLight'
import { applyAutoTheme } from '@/theme'

// provide/inject key(确保 App.vue 与 SunLightLayer/AppSidebar 共享同一状态)
export const SUN_LIGHT_KEY = Symbol('sunLight')

export function useSunLight() {
  const sunInfo = ref(null)
  const slotIdx = ref(currentSlotIndex())
  const sunScene = ref({
    source: { x: '50%', y: '-15%' },
    rotation: 0,
    palette: { bloom: 'transparent', core: 'transparent', mid: 'transparent', ambient: 'transparent' },
    rays: [],
    shadowVRotation: 0, shadowHTop: 50, frameTopOffset: 0,
    shadowIntensity: 0.7, shadowColor: 'rgb(8,12,28)',
    brightSpotColor: 'rgb(8,12,28)', brightSpotOpacity: 0.7,
    reflectionOpacity: 0, lightOpacity: 0, lampOpacity: 1,
    isNight: true, dayProgress: 0,
  })

  const lampMode = ref('auto')
  const lampTemp = ref(30)
  const lampBrightness = ref(50)
  // 光照测试模式:开启后可手动控制 slotIdx 循环
  const lightTestMode = ref(false)
  const lightTestPaused = ref(false)
  const weatherMode = ref('clear')
  let testTimer = null

  const weatherMultiplier = computed(() => ({ clear: 1.0, cloud: 0.55, rain: 0.25, snow: 0.4 }[weatherMode.value] ?? 1.0))

  const setWeather = (mode) => { weatherMode.value = mode; refreshScene() }

  const refreshScene = () => {
    if (!sunInfo.value) return
    const base = getSunScene(sunInfo.value, slotIdx.value)
    const m = weatherMultiplier.value
    sunScene.value = {
      ...base,
      lightOpacity: (base.lightOpacity ?? 0) * m,
      rays: (base.rays || []).map(r => ({ ...r, opacity: r.opacity * m })),
      reflectionOpacity: (base.reflectionOpacity ?? 0) * m,
    }
  }
  // 夜间超时关灯:X 分钟无操作自动关灯,有操作时立即开灯(仅 auto 模式 + 夜间生效)
  const idleMinutes = ref(5)
  const isIdle = ref(false)
  let idleTimer = null
  const IDLE_EVENTS = ['mousedown', 'keydown', 'touchstart', 'scroll', 'mousemove']

  const resetIdle = () => {
    if (isIdle.value) isIdle.value = false
    if (idleTimer) clearTimeout(idleTimer)
    idleTimer = setTimeout(() => {
      isIdle.value = true
    }, idleMinutes.value * 60 * 1000)
  }

  const toggleLamp = () => {
    const modes = ['auto', 'on', 'off']
    const i = modes.indexOf(lampMode.value)
    lampMode.value = modes[(i + 1) % 3]
  }

  // 光照测试:开启后每 208ms 推进一个时隙(5 分钟),288 个时隙=1 天循环
  const startLightTest = () => {
    lightTestMode.value = true
    lightTestPaused.value = false
    if (testTimer) clearInterval(testTimer)
    testTimer = setInterval(() => {
      if (lightTestPaused.value) return
      slotIdx.value = (slotIdx.value + 1) % 288
      refreshScene()
    }, 208)
  }
  const pauseLightTest = () => { lightTestPaused.value = !lightTestPaused.value }
  const stepLightTest = (dir) => {
    slotIdx.value = (slotIdx.value + dir + 288) % 288
    refreshScene()
  }
  const stopLightTest = () => {
    lightTestMode.value = false
    weatherMode.value = 'clear'
    if (testTimer) { clearInterval(testTimer); testTimer = null }
    slotIdx.value = currentSlotIndex()
    if (sunInfo.value) {
      sunScene.value = getSunScene(sunInfo.value, slotIdx.value)
    }
  }

  const lampStrength = computed(() => {
    if (lampMode.value === 'off') return 0
    if (lampMode.value === 'on') return 1
    // auto 模式:夜间台灯正常开,但空闲超时后关灯(日间 lampOpacity=0 不受影响)
    if (isIdle.value) return 0
    return sunScene.value.lampOpacity ?? 0
  })

  // mask-image 不支持 CSS transition,统一由 GSAP 补间驱动
  const lampAnim = reactive({ v: lampStrength.value })
  let lampTween = null
  watch(lampStrength, (nv) => {
    lampTween?.kill()
    lampTween = gsap.to(lampAnim, { v: nv, duration: 2, ease: 'power2.out', overwrite: true })
  })
  const lampStrengthAnim = computed(() => lampAnim.v)
  const lampB = computed(() => lampBrightness.value / 100)
  const lampDivOpacity = computed(() => lampStrengthAnim.value * 0.3)
  const lampRadius = computed(() => 65)
  const lampMaskAlpha = computed(() => 0.03 + 0.97 * lampB.value)

  const lampPendulumX = ref(0)
  const lampPendulumScaleX = ref(1)
  let lampRaf = null
  const startPendulum = () => {
    if (lampRaf) return
    const t0 = performance.now()
    const PERIOD = 8000
    const loop = (t) => {
      const phase = ((t - t0) % PERIOD) / PERIOD * Math.PI * 2
      const sin = Math.sin(phase)
      lampPendulumX.value = sin * 5
      lampPendulumScaleX.value = 1 - Math.abs(sin) * 0.2
      lampRaf = requestAnimationFrame(loop)
    }
    lampRaf = requestAnimationFrame(loop)
  }
  const stopPendulum = () => {
    if (lampRaf) { cancelAnimationFrame(lampRaf); lampRaf = null }
  }

  const lampMask = computed(() => {
    const s = lampStrengthAnim.value
    if (s <= 0.01) return 'none'
    const r = lampRadius.value
    const tr = lampMaskAlpha.value * r * s
    const te = r + 30
    const cx = 38.2 + lampPendulumX.value
    return `radial-gradient(circle at ${cx}% 38.2%, transparent 0%, transparent ${tr}vw, rgba(0,0,0,0.15) ${tr + (te - tr) * 0.3}vw, rgba(0,0,0,0.4) ${tr + (te - tr) * 0.55}vw, rgba(0,0,0,0.7) ${tr + (te - tr) * 0.8}vw, black ${te}vw)`
  })

  const lampColor = computed(() => {
    const t = lampTemp.value / 100
    const r = Math.round(255 - t * 35)
    const g = Math.round(180 + t * 50)
    const b = Math.round(100 + t * 155)
    return `${r},${g},${b}`
  })

  // 灰尘粒子:20 个
  const dustParticles = ref(
    Array.from({ length: 20 }, (_, i) => ({
      id: i,
      left: Math.random() * 100 + '%',
      top: Math.random() * 100 + '%',
      size: 1.5 + Math.random() * 4,
      duration: 10 + Math.random() * 15,
      delay: Math.random() * 12,
      drift: 40 + Math.random() * 80,
    }))
  )

  // 体积光束样式
  const rayStyles = computed(() => {
    const s = sunScene.value
    return s.rays.map((ray) => ({
      width: ray.width + 'px',
      transform: `translateX(${ray.offset}px) rotate(${s.rotation}deg)`,
      opacity: ray.opacity,
      filter: `blur(${ray.blur}px)`,
      background: `linear-gradient(to bottom, ${s.palette.core} 0%, ${s.palette.mid} 35%, transparent 75%)`,
    }))
  })

  const sourceStyle = computed(() => ({
    left: sunScene.value.source.x,
    top: sunScene.value.source.y,
  }))

  const bloomStyle = computed(() => ({
    left: sunScene.value.source.x,
    top: sunScene.value.source.y,
    background: `radial-gradient(circle, ${sunScene.value.palette.bloom} 0%, ${sunScene.value.palette.mid} 35%, transparent 70%)`,
  }))

  const brightSpotStyle = computed(() => ({
    background: sunScene.value.brightSpotColor || 'transparent',
    opacity: sunScene.value.brightSpotOpacity ?? 0,
  }))

  const reflectionStyle = computed(() => ({
    background: `radial-gradient(ellipse 60% 50% at ${sunScene.value.source.x} ${sunScene.value.source.y}, rgba(255,245,220,1) 0%, rgba(255,235,200,0.6) 30%, transparent 70%)`,
    opacity: sunScene.value.reflectionOpacity ?? 0,
  }))

  // 拉取太阳信息并启动时隙更新
  let slotTimer = null
  let flickerTimer = null

  const loadSunInfo = async () => {
    try {
      const res = await fetch('/api/public/sun-info')
      if (res.ok) {
        const json = await res.json()
        if (json.code === 0 && json.data) {
          sunInfo.value = json.data
          slotIdx.value = currentSlotIndex()
          sunScene.value = getSunScene(json.data, slotIdx.value)
          applyAutoTheme(sunScene.value.isNight)
        }
      }
    } catch (e) {}
  }

  onMounted(() => {
    loadSunInfo()
    watchEffect(() => {
      if (lampStrength.value > 0) startPendulum()
      else stopPendulum()
    })
    // 空闲检测:注册用户活动事件,启动超时定时器
    IDLE_EVENTS.forEach(e => window.addEventListener(e, resetIdle, { passive: true }))
    resetIdle()
    // 每 5 分钟更新时隙
    slotTimer = setInterval(() => {
      if (sunInfo.value) {
        const newIdx = currentSlotIndex()
        if (newIdx !== slotIdx.value) {
          slotIdx.value = newIdx
          sunScene.value = getSunScene(sunInfo.value, newIdx)
          applyAutoTheme(sunScene.value.isNight)
        }
      }
    }, 300000)
    // 每 10 秒微调光柱明暗
    flickerTimer = setInterval(() => {
      if (sunInfo.value) {
        const base = getSunScene(sunInfo.value, slotIdx.value)
        sunScene.value = {
          ...base,
          rays: base.rays.map((r) => ({
            ...r,
            opacity: Math.max(0.15, Math.min(0.85, r.opacity + (Math.random() - 0.5) * 0.12)),
          })),
        }
      }
    }, 10000)
  })

  onUnmounted(() => {
    stopPendulum()
    lampTween?.kill()
    if (slotTimer) clearInterval(slotTimer)
    if (flickerTimer) clearInterval(flickerTimer)
    if (testTimer) clearInterval(testTimer)
    IDLE_EVENTS.forEach(e => window.removeEventListener(e, resetIdle))
    if (idleTimer) clearTimeout(idleTimer)
  })

  return {
    sunInfo, slotIdx, sunScene,
    lampMode, lampTemp, lampBrightness, toggleLamp,
    idleMinutes, isIdle,
    lampStrength, lampStrengthAnim, lampDivOpacity, lampRadius, lampMask, lampColor,
    lampPendulumX, lampPendulumScaleX,
    dustParticles, rayStyles, sourceStyle, bloomStyle, brightSpotStyle, reflectionStyle,
    lightTestMode, lightTestPaused, weatherMode, setWeather, startLightTest, pauseLightTest, stepLightTest, stopLightTest,
  }
}
