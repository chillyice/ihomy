// 光影系统全局组合式函数:太阳位置 + 台灯 + 钟摆 + 时隙更新
// 从 Home.vue 提取,供 SunLightLayer.vue 和全局使用
// 用法:App.vue 调用 useSunLight() 并 provide(SUN_LIGHT_KEY, state)
//       SunLightLayer/AppSidebar 用 inject(SUN_LIGHT_KEY) 获取同一实例
import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue'
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
    windowAngle: 0, hasDirectLight: false,
  })

  const _saved = JSON.parse(localStorage.getItem('ihomy:effects') || 'null') || {}
  let _suspended = null
  const lampMode = ref(_saved.lampMode ?? 'auto')
  const lampTemp = ref(_saved.lampTemp ?? 30)
  const lampBrightness = ref(_saved.lampBrightness ?? 50)
  const shadowEnabled = ref(_saved.shadowEnabled ?? true)
  const weatherEffectEnabled = ref(_saved.weatherEffectEnabled ?? true)
  const blobsEnabled = ref(_saved.blobsEnabled ?? true)
  const glassEnabled = ref(_saved.glassEnabled ?? true)
  watch([lampMode, lampTemp, lampBrightness, shadowEnabled, weatherEffectEnabled, blobsEnabled, glassEnabled], () => {
    if (_suspended) return
    localStorage.setItem('ihomy:effects', JSON.stringify({
      lampMode: lampMode.value, lampTemp: lampTemp.value, lampBrightness: lampBrightness.value,
      shadowEnabled: shadowEnabled.value, weatherEffectEnabled: weatherEffectEnabled.value,
      blobsEnabled: blobsEnabled.value, glassEnabled: glassEnabled.value,
    }))
  })
  // 总开关:所有特效都关闭时为 false,用于门控定时器/API 调用/组件挂载
  const anyEffectEnabled = computed(() =>
    shadowEnabled.value || blobsEnabled.value || weatherEffectEnabled.value || lampMode.value !== 'off'
  )
  // 光照测试模式:开启后可手动控制 slotIdx 循环
  const lightTestMode = ref(false)
  const lightTestPaused = ref(false)
  const testSpeed = ref(1)
  const weatherMode = ref('clear')
  const precipLevel = ref(0)
  let testTimer = null

  // 多云模式:云遮阳光的随机闪烁系数(0~1),由 GSAP 缓慢补间
  const cloudFlicker = ref(1)
  let cloudTimer = null
  let cloudTween = null

  const setWeather = (mode, level = 0) => {
    weatherMode.value = mode
    precipLevel.value = level
    startCloudFlicker(mode === 'cloud')
    startLightning(mode === 'thunder')
    refreshScene()
  }

  // 多云闪烁:每 4~8 秒随机一个目标(0.1~1),GSAP 3~5s 缓慢补间,模拟云彩遮阳
  const startCloudFlicker = (on) => {
    if (cloudTimer) { clearInterval(cloudTimer); cloudTimer = null }
    if (cloudTween) { cloudTween.kill(); cloudTween = null }
    if (!on) { cloudFlicker.value = 1; return }
    const tick = () => {
      const target = 0.1 + Math.random() * 0.9
      cloudTween = gsap.to(cloudFlicker, { value: target, duration: 3 + Math.random() * 2, ease: 'power2.inOut', overwrite: true })
    }
    tick()
    cloudTimer = setInterval(tick, 4000 + Math.random() * 4000)
  }

  // 雷雨闪电:随机间隔触发,瞬间增亮(0→1→0.3→1→0 快速闪烁),模拟打雷闪电光
  const lightningFlash = ref(0)
  let lightningTimer = null
  const startLightning = (on) => {
    if (lightningTimer) { clearTimeout(lightningTimer); lightningTimer = null }
    if (!on) { lightningFlash.value = 0; return }
    const scheduleNext = () => {
      const isThunder = weatherMode.value === 'thunder'
      const minMs = isThunder ? 3000 : 10000
      const rangeMs = isThunder ? 7000 : 30000
      lightningTimer = setTimeout(() => {
        const tl = gsap.timeline()
        tl.to(lightningFlash, { value: 1, duration: 0.05 })
          .to(lightningFlash, { value: 0.3, duration: 0.1 })
          .to(lightningFlash, { value: 1, duration: 0.05 })
          .to(lightningFlash, { value: 0, duration: 0.4, ease: 'power2.out' })
        scheduleNext()
      }, minMs + Math.random() * rangeMs)
    }
    scheduleNext()
  }

  const refreshScene = () => {
    if (!sunInfo.value) return
    // 只设静态属性(调色板/旋转/阴影位置等),不烘焙天气系数
    // 天气系数(lightOpacity/rays opacity/reflectionOpacity/weatherShadow)由 computed 实时响应 cloudFlicker
    sunScene.value = getSunScene(sunInfo.value, slotIdx.value)
  }

  // 天气光照系数(实时响应 cloudFlicker):晴=1,雨雪=0,多云=cloudFlicker
  const weatherLightMul = computed(() => {
    if (weatherMode.value === 'rain' || weatherMode.value === 'snow' || weatherMode.value === 'thunder') return 0
    if (weatherMode.value === 'overcast') return 0.15
    if (weatherMode.value === 'fog') return 0.08
    if (weatherMode.value === 'cloud') return cloudFlicker.value
    return 1
  })

  // 天气覆盖阴影层:雨雪常显;多云随 cloudFlicker 平滑反向变化;晴天不显示
  // 与光柱/光源/灰尘的 weatherLightMul 完全同步(同一 cloudFlicker 驱动)
  const weatherShadowOpacity = computed(() => {
    if (!shadowEnabled.value) return 0
    if (weatherMode.value === 'rain' || weatherMode.value === 'snow' || weatherMode.value === 'thunder') return 1
    if (weatherMode.value === 'overcast') return 0.85
    if (weatherMode.value === 'fog') return 0.6
    if (weatherMode.value === 'cloud') return Math.max(0, 1 - cloudFlicker.value)
    return 0
  })
  // 夜间超时关灯:X 分钟无操作自动关灯,有操作时立即开灯(仅 auto 模式 + 夜间生效)
  // mousemove 节流:避免高频触发(每 2 秒最多一次 resetIdle)
  const idleMinutes = ref(5)
  const isIdle = ref(false)
  let idleTimer = null
  let lastIdleReset = 0
  const IDLE_EVENTS = ['mousedown', 'keydown', 'touchstart', 'scroll']
  const IDLE_EVENTS_THROTTLED = ['mousemove']

  const resetIdle = () => {
    const now = Date.now()
    if (now - lastIdleReset < 2000) return  // 节流:2秒内只处理一次
    lastIdleReset = now
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
    }, 208 / testSpeed.value)
  }
  const setTestSpeed = (sp) => {
    testSpeed.value = sp
    if (testTimer && lightTestMode.value && !lightTestPaused.value) {
      clearInterval(testTimer)
      testTimer = setInterval(() => {
        if (lightTestPaused.value) return
        slotIdx.value = (slotIdx.value + 1) % 288
        refreshScene()
      }, 208 / sp)
    }
  }
  const pauseLightTest = () => { lightTestPaused.value = !lightTestPaused.value }
  const stepLightTest = (dir) => {
    slotIdx.value = (slotIdx.value + dir + 288) % 288
    refreshScene()
  }
  const stopLightTest = () => {
    lightTestMode.value = false
    weatherMode.value = 'clear'
    precipLevel.value = 0
    startCloudFlicker(false)
    startLightning(false)
    if (testTimer) { clearInterval(testTimer); testTimer = null }
    slotIdx.value = currentSlotIndex()
    if (sunInfo.value) {
      sunScene.value = getSunScene(sunInfo.value, slotIdx.value)
    }
  }

  const setSlot = (idx) => {
    slotIdx.value = Math.max(0, Math.min(287, idx))
    refreshScene()
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

  // 钟摆运动已改为 CSS @keyframes lampSwing(见 main.css .lamp-light-pendulum),
  // 不再用 requestAnimationFrame 每帧写 ref,完全绕过 Vue 响应式。

  const lampMask = computed(() => {
    const s = lampStrengthAnim.value
    if (s <= 0.01) return 'none'
    const r = lampRadius.value
    const tr = lampMaskAlpha.value * r * s
    const te = r + 30
    return `radial-gradient(circle at 38.2% 38.2%, transparent 0%, transparent ${tr}vw, rgba(0,0,0,0.15) ${tr + (te - tr) * 0.3}vw, rgba(0,0,0,0.4) ${tr + (te - tr) * 0.55}vw, rgba(0,0,0,0.7) ${tr + (te - tr) * 0.8}vw, black ${te}vw)`
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

  // 雪花粒子:数量=precipLevel×10,六瓣雪花字符,缓落+横向飘移+自转
  const snowParticles = computed(() => {
    if (weatherMode.value !== 'snow' || precipLevel.value <= 0) return []
    const count = precipLevel.value * 10
    return Array.from({ length: count }, (_, i) => ({
      id: 's' + i,
      left: Math.random() * 100 + '%',
      size: 12 + Math.random() * 16,
      duration: 8 + Math.random() * 10,
      delay: Math.random() * 10,
      drift: 30 + Math.random() * 80,
      opacity: 0.5 + Math.random() * 0.4,
    }))
  })

  // 雨滴粒子:数量=precipLevel×10,快速下落
  const rainParticles = computed(() => {
    if ((weatherMode.value !== 'rain' && weatherMode.value !== 'thunder') || precipLevel.value <= 0) return []
    const count = precipLevel.value * 10
    return Array.from({ length: count }, (_, i) => ({
      id: 'r' + i,
      left: Math.random() * 100 + '%',
      height: 14 + Math.random() * 16,
      duration: 0.5 + Math.random() * 0.8,
      delay: Math.random() * 2,
      opacity: 0.3 + Math.random() * 0.4,
    }))
  })

  // 体积光束样式(天气系数实时应用)
  const rayStyles = computed(() => {
    const s = sunScene.value
    const mul = weatherLightMul.value
    return s.rays.map((ray) => ({
      width: ray.width + 'px',
      transform: `translateX(${ray.offset}px) rotate(${s.rotation}deg)`,
      opacity: ray.opacity * mul,
      filter: `blur(${ray.blur}px)`,
      background: `linear-gradient(to bottom, ${s.palette.core} 0%, ${s.palette.mid} 35%, transparent 75%)`,
    }))
  })

  // 光源整体不透明度(天气系数实时应用,控制 bloom 辉光)
  const lightLayerOpacity = computed(() => shadowEnabled.value ? (sunScene.value.lightOpacity ?? 0) * weatherLightMul.value : 0)

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
    opacity: shadowEnabled.value ? (sunScene.value.isNight ? (sunScene.value.brightSpotOpacity ?? 0) : (['rain', 'snow', 'cloud', 'thunder', 'overcast', 'fog'].includes(weatherMode.value) ? 0 : (sunScene.value.brightSpotOpacity ?? 0))) : 0,
  }))
  const reflectionStyle = computed(() => ({
    background: `radial-gradient(ellipse 60% 50% at ${sunScene.value.source.x} ${sunScene.value.source.y}, rgba(255,245,220,1) 0%, rgba(255,235,200,0.6) 30%, transparent 70%)`,
    opacity: shadowEnabled.value ? (sunScene.value.reflectionOpacity ?? 0) * weatherLightMul.value : 0,
  }))

  // 拉取太阳信息并启动时隙更新
  let slotTimer = null
  let flickerTimer = null
  let weatherTimer = null

  // 天气数据(供 Home.vue 天气面板使用)
  const weather = ref(null)
  const weatherDetail = ref(null)

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

  const loadSunInfoForDate = async (dateStr) => {
    try {
      const res = await fetch('/api/public/sun-info?date=' + dateStr)
      if (res.ok) {
        const json = await res.json()
        if (json.code === 0 && json.data) {
          sunInfo.value = json.data
          sunScene.value = getSunScene(json.data, slotIdx.value)
          applyAutoTheme(sunScene.value.isNight)
        }
      }
    } catch (e) {}
  }

  // 拉取真实天气并同步到光影系统(非光照测试模式)
  const loadWeather = async () => {
    loadWeatherDetail() // 详情(预警+今日高低温)与简版天气并行,供侧边栏迷你天气/首页天气卡片
    try {
      const res = await fetch('/api/public/weather')
      if (!res.ok) return
      const json = await res.json()
      if (json.code === 0 && json.data) {
        weather.value = json.data
        if (!lightTestMode.value && weatherEffectEnabled.value) {
          const cond = json.data.condition
          if (['rain', 'snow', 'cloud', 'thunder'].includes(cond)) {
            setWeather(cond, json.data.precipLevel || 1)
          } else {
            setWeather('clear', 0)
          }
        } else if (!weatherEffectEnabled.value) {
          setWeather('clear', 0)
        }
      }
    } catch (e) {}
  }

  // 拉取天气详情(now/7d/24h/预警/空气/指数;后端 Redis 缓存 30 分钟,失败静默)
  const loadWeatherDetail = async () => {
    try {
      const res = await fetch('/api/public/weather/detail')
      if (!res.ok) return
      const json = await res.json()
      if (json.code === 0 && json.data) weatherDetail.value = json.data
    } catch (e) {}
  }

  onMounted(() => {
    loadSunInfo()
    loadWeather()
    // 钟摆运动已改为 CSS @keyframes,无需 JS rAF;lampStrength 变化只决定元素是否渲染
    // 空闲检测:注册用户活动事件(mousemove 节流),启动超时定时器
    IDLE_EVENTS.forEach(e => window.addEventListener(e, resetIdle, { passive: true }))
    IDLE_EVENTS_THROTTLED.forEach(e => window.addEventListener(e, resetIdle, { passive: true }))
    resetIdle()
    // 每 5 分钟更新时隙
    slotTimer = setInterval(() => {
      if (sunInfo.value) {
        const newIdx = currentSlotIndex()
        if (newIdx !== slotIdx.value) {
          slotIdx.value = newIdx
          refreshScene()
          applyAutoTheme(sunScene.value.isNight)
        }
      }
    }, 300000)
    // 每 10 秒微调光柱明暗(天气系数由 computed 实时应用,这里只调基础值)
    // 所有效果关闭时跳过,避免持续触发 Vue 响应式更新和 CSS transition
    flickerTimer = setInterval(() => {
      if (!sunInfo.value) return
      if (!anyEffectEnabled.value) return
      const base = getSunScene(sunInfo.value, slotIdx.value)
      sunScene.value = {
        ...base,
        rays: base.rays.map((r) => ({
          ...r,
          opacity: Math.max(0.15, Math.min(0.85, r.opacity + (Math.random() - 0.5) * 0.12)),
        })),
      }
    }, 10000)
    // 每 30 分钟刷新真实天气(与后端缓存 TTL 同步);天气数据用于天气预报面板,始终获取
    weatherTimer = setInterval(loadWeather, 1800000)
  })

  onUnmounted(() => {
    lampTween?.kill()
    if (cloudTimer) clearInterval(cloudTimer)
    cloudTween?.kill()
    if (lightningTimer) clearTimeout(lightningTimer)
    if (slotTimer) clearInterval(slotTimer)
    if (flickerTimer) clearInterval(flickerTimer)
    if (weatherTimer) clearInterval(weatherTimer)
    if (testTimer) clearInterval(testTimer)
    IDLE_EVENTS.forEach(e => window.removeEventListener(e, resetIdle))
    IDLE_EVENTS_THROTTLED.forEach(e => window.removeEventListener(e, resetIdle))
    if (idleTimer) clearTimeout(idleTimer)
  })

  // 特效从全部关闭→开启时:立即刷新场景,避免等待下一个定时器周期
  watch(anyEffectEnabled, (enabled) => {
    if (enabled && sunInfo.value) {
      slotIdx.value = currentSlotIndex()
      refreshScene()
    }
  })

  // 毛玻璃开关:在 <html> 上切换 .no-glass 类,全局禁用 backdrop-filter
  watch(glassEnabled, (on) => {
    document.documentElement.classList.toggle('no-glass', !on)
  }, { immediate: true })

  // 播放器启动时暂停特效,关闭后恢复
  const suspendEffects = () => {
    if (_suspended) return
    _suspended = { shadowEnabled: shadowEnabled.value, weatherEffectEnabled: weatherEffectEnabled.value, blobsEnabled: blobsEnabled.value, glassEnabled: glassEnabled.value, lampMode: lampMode.value }
    shadowEnabled.value = false; weatherEffectEnabled.value = false; blobsEnabled.value = false; glassEnabled.value = false; lampMode.value = 'off'
  }
  const restoreEffects = () => {
    if (!_suspended) return
    shadowEnabled.value = _suspended.shadowEnabled; weatherEffectEnabled.value = _suspended.weatherEffectEnabled; blobsEnabled.value = _suspended.blobsEnabled; glassEnabled.value = _suspended.glassEnabled; lampMode.value = _suspended.lampMode
    _suspended = null
  }

  return {
    sunInfo, slotIdx, sunScene, weather, weatherDetail, loadWeather, loadSunInfoForDate,
    lampMode, lampTemp, lampBrightness, shadowEnabled, weatherEffectEnabled, blobsEnabled, glassEnabled, anyEffectEnabled, toggleLamp,
    idleMinutes, isIdle,
    lampStrength, lampStrengthAnim, lampDivOpacity, lampRadius, lampMask, lampColor,
    dustParticles, snowParticles, rainParticles, weatherShadowOpacity, lightLayerOpacity, rayStyles, sourceStyle, bloomStyle, brightSpotStyle, reflectionStyle, lightningFlash,
    lightTestMode, lightTestPaused, testSpeed, setTestSpeed, weatherMode, precipLevel, setWeather, startLightTest, pauseLightTest, stepLightTest, stopLightTest, setSlot, refreshScene,
    suspendEffects, restoreEffects,
  }
}
