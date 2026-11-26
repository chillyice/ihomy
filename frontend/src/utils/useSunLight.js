// 光影系统全局组合式函数:太阳位置 + 台灯 + 钟摆 + 时隙更新
// 从 Home.vue 提取,供 SunLightLayer.vue 和全局使用
// 用法:App.vue 调用 useSunLight() 并 provide(SUN_LIGHT_KEY, state)
//       SunLightLayer/AppSidebar 用 inject(SUN_LIGHT_KEY) 获取同一实例
import { ref, shallowRef, reactive, computed, watch, onMounted, onUnmounted } from 'vue'
import { gsap } from 'gsap'
import {
  AUTO_LAMP_ON_BELOW,
  AUTO_LAMP_OFF_ABOVE,
  lampFromDaylight,
  ambientShadowOpacity,
  skyLighting,
  weatherOptics,
  getSunScene,
  currentSlotIndex,
  solarClock,
} from '@/utils/windowLight'
import { CASEMENTS, chooseWindowSashes, weatherClosesWindow } from '@/utils/windowSash'
import { solarSlotForTime, solarSlotPosition, sampleSolarDirection } from '@/utils/solarSamples'
import { resolveMorningWeather } from '@/utils/morningWeather'
import { useThemeStore } from '@/stores/theme'
import { setSunContext } from '@/theme'
import request from '@/api/request'

// provide/inject key(确保 App.vue 与 SunLightLayer/AppSidebar 共享同一状态)
export const SUN_LIGHT_KEY = Symbol('sunLight')
const SASH_SESSION_KEY = 'ihomy:window:last-pose'

export function useSunLight(canReadEnvironment = () => true) {
  const themeStore = useThemeStore()
  const deskCasters = shallowRef([])
  let lastPoseWasOpen = false
  try {
    lastPoseWasOpen = sessionStorage.getItem(SASH_SESSION_KEY) === 'open'
  } catch {
    // A WebView without session storage starts with an open sash in fair weather.
  }
  const fairWeatherSashes = shallowRef(chooseWindowSashes(lastPoseWasOpen))
  const sunInfo = ref(null)
  // 共享 sun-info 的取数时刻(ms):判断「页面长开几小时后」这份数据还能不能直接复用
  const sunInfoAt = ref(0)
  const livePosition = ref(currentSlotIndex())
  const liveSlot = computed(() => Math.min(Math.floor(livePosition.value), (sunInfo.value?.slots?.length || 288) - 1))
  const lightTestMode = ref(false)
  const lightTestPaused = ref(true)
  const previewDate = ref('')
  const previewLoadFailed = ref(false)
  const previewSunInfo = ref(null)
  const previewSlot = ref(null)
  const previewSample = shallowRef(null)
  let previewRequest = null
  const activeSunInfo = computed(() => (lightTestMode.value ? previewSunInfo.value : sunInfo.value))
  const slotIdx = computed(() => (lightTestMode.value ? (previewSlot.value ?? liveSlot.value) : liveSlot.value))
  const initialScene = getSunScene(null, 0)
  const sunScene = computed(() => {
    if (!activeSunInfo.value) return initialScene
    const sample = lightTestMode.value ? previewSample.value : sampleSolarDirection(sunInfo.value, livePosition.value)
    return getSunScene(activeSunInfo.value, slotIdx.value, sample)
  })

  const _saved = JSON.parse(localStorage.getItem('ihomy:effects') || 'null') || {}
  let _suspended = null
  // 光影总开关:关闭时下方每项按「关」呈现,但各自的偏好仍留在存储里,重新打开即恢复
  const effectsEnabled = ref(_saved.effectsEnabled ?? true)
  const _lampMode = ref(_saved.lampMode ?? 'auto')
  const lampTemp = ref(_saved.lampTemp ?? 30)
  const lampBrightness = ref(_saved.lampBrightness ?? 50)
  const _shadowEnabled = ref(_saved.shadowEnabled ?? true)
  // 阴影深度 0~100，默认 70。
  const projectionStandard = ref(_saved.projectionStandard === 'real' ? 'real' : 'corrected')
  const shadowDepth = ref(_saved.shadowDepth ?? 70)
  const featherStrength = ref(Number.isFinite(_saved.featherStrength) ? Math.min(100, Math.max(0, _saved.featherStrength)) : 50)
  const _weatherEffectEnabled = ref(_saved.weatherEffectEnabled ?? true)
  const _blobsEnabled = ref(_saved.blobsEnabled ?? true)
  const _glassEnabled = ref(_saved.glassEnabled ?? true)
  // 对外的开关都经总开关过滤:组件与样式只看得到最终生效值,写回仍落到各自偏好
  const lampMode = computed({
    get: () => (effectsEnabled.value ? _lampMode.value : 'off'),
    set: (v) => (_lampMode.value = v),
  })
  const shadowEnabled = computed({
    get: () => effectsEnabled.value && _shadowEnabled.value,
    set: (v) => (_shadowEnabled.value = v),
  })
  const weatherEffectEnabled = computed({
    get: () => effectsEnabled.value && _weatherEffectEnabled.value,
    set: (v) => (_weatherEffectEnabled.value = v),
  })
  const blobsEnabled = computed({
    get: () => effectsEnabled.value && _blobsEnabled.value,
    set: (v) => (_blobsEnabled.value = v),
  })
  const glassEnabled = computed({
    get: () => effectsEnabled.value && _glassEnabled.value,
    set: (v) => (_glassEnabled.value = v),
  })
  watch(
    [
      effectsEnabled,
      _lampMode,
      lampTemp,
      lampBrightness,
      _shadowEnabled,
      shadowDepth,
      featherStrength,
      projectionStandard,
      _weatherEffectEnabled,
      _blobsEnabled,
      _glassEnabled,
    ],
    () => {
      // 挂起期间(suspendEffects 批量置 false / restoreEffects 批量还原)绝不落盘:
      // 否则播放器挂起时写下的「全关」会覆盖掉用户真正保存的偏好
      if (_suspended) return
      localStorage.setItem(
        'ihomy:effects',
        JSON.stringify({
          effectsEnabled: effectsEnabled.value,
          lampMode: _lampMode.value,
          lampTemp: lampTemp.value,
          lampBrightness: lampBrightness.value,
          shadowEnabled: _shadowEnabled.value,
          projectionStandard: projectionStandard.value,
          shadowDepth: shadowDepth.value,
          featherStrength: featherStrength.value,
          weatherEffectEnabled: _weatherEffectEnabled.value,
          blobsEnabled: _blobsEnabled.value,
          glassEnabled: _glassEnabled.value,
        }),
      )
    },
  )
  // 总开关:所有特效都关闭时为 false,用于门控定时器/API 调用/组件挂载
  const anyEffectEnabled = computed(
    () => shadowEnabled.value || blobsEnabled.value || weatherEffectEnabled.value || lampMode.value !== 'off',
  )
  // 光照测试模式:开启后可手动控制 slotIdx 循环
  const testSpeed = ref(1)
  const weather = ref(null)
  const weatherDetail = ref(null)
  const previewWeather = ref(null)
  const selectedWeather = computed(() => (lightTestMode.value ? (previewWeather.value ?? weather.value) : weather.value))
  const activeWeather = computed(() => (weatherEffectEnabled.value ? selectedWeather.value : null))
  const realStormClosed = computed(() => weatherClosesWindow(weather.value))
  const stormClosed = computed(() => weatherClosesWindow(selectedWeather.value))
  const windowSashes = computed(() => (stormClosed.value ? CASEMENTS : fairWeatherSashes.value))
  watch(
    () => (realStormClosed.value ? CASEMENTS : fairWeatherSashes.value),
    (sashes) => {
      try {
        sessionStorage.setItem(SASH_SESSION_KEY, sashes === CASEMENTS ? 'closed' : 'open')
      } catch {
        // In-session rotations still use the current pose.
      }
    },
    { immediate: true },
  )
  const weatherMode = computed(() => resolveMorningWeather(activeWeather.value))
  const setWeather = (condition, level = 0) => {
    previewWeather.value = { condition, precipLevel: level }
  }
  const precipLevel = computed({
    get: () => (['rain', 'snow', 'thunder'].includes(weatherMode.value) ? (activeWeather.value?.precipLevel ?? 1) : 0),
    set: (level) => setWeather(weatherMode.value, level),
  })
  let testTimer = null

  const DIM_MODES = ['fog', 'overcast']
  const FOG_VISIBLE_MS = 4000
  const fogVisible = ref(false)
  let fogTimer = null
  const playTransient = () => {
    fogVisible.value = true
    // 重新进入昏暗(含 30 分钟一次的天气刷新)时重播一次;不在雾里越积越多
    if (fogTimer) clearTimeout(fogTimer)
    fogTimer = setTimeout(() => {
      fogVisible.value = false
      fogTimer = null
    }, FOG_VISIBLE_MS)
  }
  const stopTransient = () => {
    if (fogTimer) {
      clearTimeout(fogTimer)
      fogTimer = null
    }
    fogVisible.value = false
  }

  // 多云模式:云遮阳光的随机闪烁系数(0~1),由 GSAP 缓慢补间
  const cloudFlicker = ref(1)
  let cloudTimer = null
  let cloudTween = null

  // 多云闪烁:每 4~8 秒随机一个目标(0.1~1),GSAP 3~5s 缓慢补间,模拟云彩遮阳
  const startCloudFlicker = (on) => {
    if (cloudTimer) {
      clearInterval(cloudTimer)
      cloudTimer = null
    }
    if (cloudTween) {
      cloudTween.kill()
      cloudTween = null
    }
    if (!on) {
      cloudFlicker.value = 1
      return
    }
    const tick = () => {
      const target = 0.1 + Math.random() * 0.9
      cloudTween = gsap.to(cloudFlicker, { value: target, duration: 3 + Math.random() * 2, ease: 'power2.inOut', overwrite: true })
    }
    tick()
    cloudTimer = setInterval(tick, 4000 + Math.random() * 4000)
  }

  watch(() => weatherMode.value === 'cloud' && sunScene.value.hasDirectLight, startCloudFlicker)
  watch([weatherMode, activeWeather], ([mode]) => {
    if (DIM_MODES.includes(mode)) playTransient()
    else stopTransient()
  })

  const optics = computed(() => weatherOptics(weatherMode.value, activeWeather.value))
  const skyLight = computed(() => skyLighting(sunScene.value, optics.value, cloudFlicker.value))
  const shadowAlpha = computed(() => Math.min(1, Math.max(0, shadowDepth.value / 70) * sunScene.value.shadowIntensity))

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
    if (now - lastIdleReset < 2000) return // 节流:2秒内只处理一次
    lastIdleReset = now
    if (isIdle.value) isIdle.value = false
    if (idleTimer) clearTimeout(idleTimer)
    idleTimer = setTimeout(
      () => {
        isIdle.value = true
      },
      idleMinutes.value * 60 * 1000,
    )
  }

  const toggleLamp = () => {
    const modes = ['auto', 'on', 'off']
    const i = modes.indexOf(lampMode.value)
    lampMode.value = modes[(i + 1) % 3]
  }

  // 光照测试:开启后每 208ms 推进一个时隙(5 分钟),288 个时隙=1 天循环
  const scheduleTestPlayback = () => {
    if (testTimer) clearInterval(testTimer)
    testTimer = null
    if (!lightTestMode.value || lightTestPaused.value || !previewSunInfo.value) return
    testTimer = setInterval(() => {
      previewSample.value = null
      previewSlot.value = (slotIdx.value + 1) % previewSunInfo.value.slots.length
    }, 208 / testSpeed.value)
  }
  const startLightTest = () => {
    if (lightTestMode.value) return
    previewSunInfo.value = sunInfo.value
    previewDate.value = sunInfo.value?.date ?? ''
    previewLoadFailed.value = false
    previewSlot.value = sunInfo.value ? liveSlot.value : null
    previewSample.value = sampleSolarDirection(sunInfo.value, livePosition.value)
    lightTestPaused.value = true
    lightTestMode.value = true
  }
  // 暂停期间也更新下一次播放的间隔。
  const setTestSpeed = (sp) => {
    testSpeed.value = sp
    if (lightTestMode.value) scheduleTestPlayback()
  }
  const pauseLightTest = () => {
    lightTestPaused.value = !lightTestPaused.value
    scheduleTestPlayback()
  }
  const stepLightTest = (dir) => {
    const count = activeSunInfo.value?.slots?.length || 288
    setSlot((slotIdx.value + dir + count) % count)
  }
  const stopLightTest = () => {
    previewRequest = null
    previewSunInfo.value = null
    lightTestMode.value = false
    lightTestPaused.value = true
    previewWeather.value = null
    scheduleTestPlayback()
    syncLiveClock()
  }

  const setSlot = (idx) => {
    lightTestPaused.value = true
    scheduleTestPlayback()
    previewSample.value = null
    previewSlot.value = Math.max(0, Math.min((activeSunInfo.value?.slots?.length || 288) - 1, idx))
  }

  // Meter before applying the lamp, independent of slider depth and cloud
  // animation. Hysteresis prevents repeated switches near the threshold.
  const autoLampOn = computed((previous) => {
    const level = skyLight.value.metered
    if (level <= AUTO_LAMP_ON_BELOW) return true
    if (level >= AUTO_LAMP_OFF_ABOVE) return false
    return previous ?? level < (AUTO_LAMP_ON_BELOW + AUTO_LAMP_OFF_ABOVE) / 2
  })
  const lampStrength = computed(() => {
    if (lampMode.value === 'off') return 0
    if (lampMode.value === 'on') return 1
    if (isIdle.value) return 0
    const level = skyLight.value.metered
    // Scrubbing a frozen preview must not depend on the previously visited time.
    if (!lightTestMode.value && !autoLampOn.value) return 0
    return lampFromDaylight(level)
  })

  // mask-image 不支持 CSS transition,统一由 GSAP 补间驱动
  const lampAnim = reactive({ v: lampStrength.value })
  let lampTween = null
  watch([lampStrength, lightTestMode], ([strength, testing]) => {
    lampTween?.kill()
    if (testing) {
      // A seek or playback tick represents a new solar time, even when only milliseconds elapsed.
      lampAnim.v = strength
      lampTween = null
    } else {
      lampTween = gsap.to(lampAnim, { v: strength, duration: 2, ease: 'power2.out', overwrite: true })
    }
  })
  const lampStrengthAnim = computed(() => lampAnim.v)
  const lampB = computed(() => lampBrightness.value / 100)
  const lampDivOpacity = computed(() => lampStrengthAnim.value * 0.3)
  const lampRadius = computed(() => 65)
  const lampMaskAlpha = computed(() => 0.03 + 0.97 * lampB.value)

  // 钟摆运动已改为 CSS @keyframes lampSwing(见 main.css .lamp-light-pendulum),
  // 不再用 requestAnimationFrame 每帧写 ref,完全绕过 Vue 响应式。

  // 台灯遮罩:用 radial-gradient 拼出「中心透光、向外渐暗」的胶光;光源点取 38.2%(黄金分割),
  // 视觉上比正中更自然。透明半径 tr 随亮度与台灯强度缩放(灯越亮/越强,透光区越大),
  // 外圈 te = r + 30 为羽化外边界,中间三段 stop(0.15/0.4/0.7)把过渡拉成软边,避免出现硬光圈。
  // 全灭(强度≈0)时返回 none,直接不下发遮罩。
  const lampMask = computed(() => {
    const s = lampStrengthAnim.value
    if (s <= 0.01) return 'none'
    const r = lampRadius.value
    const tr = lampMaskAlpha.value * r * s
    const te = r + 30
    return `radial-gradient(circle at 38.2% 38.2%, transparent 0%, transparent ${tr}vw, rgba(0,0,0,0.15) ${tr + (te - tr) * 0.3}vw, rgba(0,0,0,0.4) ${tr + (te - tr) * 0.55}vw, rgba(0,0,0,0.7) ${tr + (te - tr) * 0.8}vw, black ${te}vw)`
  })

  // 台灯色温插值:lampTemp 0 → 暖橙(255,180,100),100 → 冷白(220,230,255),
  // 三通道各按 t 线性推进,得到中间色温的连续过渡
  const lampColor = computed(() => {
    const t = lampTemp.value / 100
    const r = Math.round(255 - t * 35)
    const g = Math.round(180 + t * 50)
    const b = Math.round(100 + t * 155)
    return `${r},${g},${b}`
  })

  // 灰尘粒子:20 个
  const sampleDust = () =>
    Array.from({ length: 20 }, (_, i) => ({
      id: i,
      left: Math.random() * 100 + '%',
      top: Math.random() * 100 + '%',
      size: 1.5 + Math.random() * 4,
      duration: 10 + Math.random() * 15,
      delay: Math.random() * 12,
      drift: 40 + Math.random() * 80,
    }))
  const dustParticles = shallowRef(sampleDust())

  // 雪花粒子:数量=precipLevel×10,六瓣雪花字符,缓落+横向飘移+自转
  function sampleSnow() {
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
  }

  // 雨滴粒子:数量=precipLevel×10,快速下落
  function sampleRain() {
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
  }
  const snowParticles = shallowRef([])
  const rainParticles = shallowRef([])
  function refreshPrecipitation() {
    snowParticles.value = sampleSnow()
    rainParticles.value = sampleRain()
  }
  watch([weatherMode, precipLevel], refreshPrecipitation, { immediate: true })

  function rearrangeScene() {
    if (!realStormClosed.value) fairWeatherSashes.value = chooseWindowSashes(fairWeatherSashes.value !== CASEMENTS)
    dustParticles.value = sampleDust()
    refreshPrecipitation()
  }

  const lightLayerOpacity = computed(() => (shadowEnabled.value ? skyLight.value.direct : 0))
  const skyTint = computed(() => skyLight.value.tint)
  // Reserve a small part of the daylight range for directional contrast.
  // Keep the blocked surface continuous as the beam crosses the edge of the window.
  const castShade = computed(() => shadowAlpha.value * sunScene.value.skyLevel * optics.value.direct * 0.43)
  const roomShade = computed(() => ambientShadowOpacity(castShade.value, lightLayerOpacity.value))
  const diffuseSkyStyle = computed(() => {
    const sky = skyLight.value
    if (sky.diffuseOpacity <= 0) return null
    return {
      background: `radial-gradient(ellipse 110% 100% at ${sunScene.value.source.x} 0%, ${sky.diffuseColor} 0%, transparent 100%)`,
      opacity: sky.diffuseOpacity,
      mixBlendMode: 'screen',
    }
  })

  let slotTimer = null
  let weatherTimer = null
  let weatherVisible = false
  let nextWeatherAt = 0
  let currentWeatherRun = null
  let liveRequest = null
  let retrySunAfter = 0

  const loadSunInfo = async (changedLocation = false) => {
    if (changedLocation) {
      liveRequest = null
      retrySunAfter = 0
    }
    if (!canReadEnvironment() || liveRequest || Date.now() < retrySunAfter) return
    const run = Symbol()
    liveRequest = run
    try {
      const data = await request.get('/public/sun-info')
      if (liveRequest === run) applySunInfo(data)
    } catch {
      if (liveRequest === run) retrySunAfter = Date.now() + 5 * 60_000
    } finally {
      if (liveRequest === run) liveRequest = null
    }
  }

  const loadSunInfoForDate = async (dateStr) => {
    if (!lightTestMode.value || !canReadEnvironment()) return false
    const run = Symbol()
    previewRequest = run
    previewDate.value = dateStr
    previewLoadFailed.value = false
    const previousSlots = previewSunInfo.value?.slots
    previewSample.value = null
    previewSunInfo.value = null
    scheduleTestPlayback()
    try {
      const data = await request.get('/public/sun-info', { params: { date: dateStr } })
      if (previewRequest !== run || data?.date !== dateStr) return false
      previewSunInfo.value = data.timeline ? { ...data, slots: data.timeline } : data
      const previousTime = previousSlots?.[previewSlot.value]?.time
      const clockSlot = previewSlot.value ?? currentSlotIndex(data.timezone)
      const minutes = previousTime ? Number(previousTime.slice(0, 2)) * 60 + Number(previousTime.slice(3)) : clockSlot * 5
      previewSlot.value = solarSlotForTime(previewSunInfo.value.slots, minutes, clockSlot)
      return true
    } catch {
      return false
    } finally {
      if (previewRequest === run) {
        previewRequest = null
        previewLoadFailed.value = !previewSunInfo.value
        if (previewLoadFailed.value) lightTestPaused.value = true
        scheduleTestPlayback()
      }
    }
  }

  // 实时缓存也供天气页复用；预览的日期、时刻独立保留。
  const applySunInfo = (data) => {
    if (!data) return
    if (Date.parse(data.generatedAt) < Date.parse(sunInfo.value?.generatedAt)) return
    sunInfo.value = data.timeline ? { ...data, slots: data.timeline } : data
    sunInfoAt.value = Date.now()
    livePosition.value = solarSlotPosition(sunInfo.value.slots, sunInfoAt.value, currentSlotIndex(data.timezone))
    if (lightTestMode.value && !previewDate.value) {
      previewDate.value = data.date
      previewSunInfo.value = sunInfo.value
      previewSlot.value ??= liveSlot.value
      scheduleTestPlayback()
    }
  }

  // 共享 sun-info 距取数的时长(ms);从未取到返回 Infinity
  const sunInfoAge = () => (sunInfoAt.value ? Date.now() - sunInfoAt.value : Infinity)

  // 日表按地点当地日期换新，白天每半小时更新一次实时天文数据。
  const SUN_INFO_TTL = 30 * 60 * 1000
  function syncLiveClock() {
    const { date, slot } = solarClock(sunInfo.value?.timezone)
    livePosition.value = solarSlotPosition(sunInfo.value?.slots, Date.now(), slot)
    if (sunInfo.value?.date !== date || sunInfoAge() >= SUN_INFO_TTL) loadSunInfo()
  }

  // Keep the last observation on failure. A manual city change supersedes any older request.
  const loadWeather = async () => {
    if (!canReadEnvironment()) return
    clearTimeout(weatherTimer)
    const run = Symbol()
    currentWeatherRun = run
    let refreshMinutes = 30
    async function update(path, target) {
      try {
        const data = await request.get(path, { isBackgroundRead: () => true })
        if (data && currentWeatherRun === run) target.value = data
      } catch {
        refreshMinutes = 5
      }
    }
    await Promise.all([update('/public/weather', weather), update('/public/weather/detail', weatherDetail)])
    if (currentWeatherRun !== run) return
    currentWeatherRun = null
    nextWeatherAt = Date.now() + refreshMinutes * 60_000
    setWeatherVisible(weatherVisible)
  }

  function setWeatherVisible(visible) {
    weatherVisible = visible
    clearTimeout(weatherTimer)
    if (!visible || !canReadEnvironment() || currentWeatherRun) return
    const delay = nextWeatherAt - Date.now()
    if (delay > 0) weatherTimer = setTimeout(() => setWeatherVisible(weatherVisible), delay)
    else loadWeather()
  }

  const onVisibilityChange = () => {
    if (!document.hidden) syncLiveClock()
  }

  watch(canReadEnvironment, (allowed) => {
    liveRequest = null
    previewRequest = null
    retrySunAfter = 0
    currentWeatherRun = null
    nextWeatherAt = 0
    if (allowed) loadSunInfo()
    setWeatherVisible(weatherVisible)
  })

  onMounted(() => {
    loadSunInfo()
    // 钟摆运动已改为 CSS @keyframes,无需 JS rAF;lampStrength 变化只决定元素是否渲染
    // 空闲检测:注册用户活动事件(mousemove 节流),启动超时定时器
    IDLE_EVENTS.forEach((e) => window.addEventListener(e, resetIdle, { passive: true }))
    IDLE_EVENTS_THROTTLED.forEach((e) => window.addEventListener(e, resetIdle, { passive: true }))
    document.addEventListener('visibilitychange', onVisibilityChange)
    resetIdle()
    // Sample the backend's five-minute directions between slots without polling the API.
    const slotDuration = 15_000
    function scheduleSlot() {
      slotTimer = setTimeout(
        () => {
          if (!document.hidden) syncLiveClock()
          scheduleSlot()
        },
        slotDuration - (Date.now() % slotDuration),
      )
    }
    scheduleSlot()
  })

  onUnmounted(() => {
    lampTween?.kill()
    if (cloudTimer) clearInterval(cloudTimer)
    cloudTween?.kill()
    if (fogTimer) clearTimeout(fogTimer)
    if (slotTimer) clearTimeout(slotTimer)
    liveRequest = null
    previewRequest = null
    currentWeatherRun = null
    clearTimeout(weatherTimer)
    if (testTimer) clearInterval(testTimer)
    IDLE_EVENTS.forEach((e) => window.removeEventListener(e, resetIdle))
    IDLE_EVENTS_THROTTLED.forEach((e) => window.removeEventListener(e, resetIdle))
    document.removeEventListener('visibilitychange', onVisibilityChange)
    if (idleTimer) clearTimeout(idleTimer)
  })

  // 特效从全部关闭→开启时:立即刷新场景,避免等待下一个定时器周期
  watch(anyEffectEnabled, (enabled) => {
    if (enabled) syncLiveClock()
  })

  // 毛玻璃开关:在 <html> 上切换 .no-glass 类,全局禁用 backdrop-filter
  watch(
    glassEnabled,
    (on) => {
      document.documentElement.classList.toggle('no-glass', !on)
    },
    { immediate: true },
  )

  // 把太阳方位角/高度角写入 theme 模块,供晨暮切换扫光的方向计算
  watch(
    [sunScene, () => themeStore.autoMode],
    ([s]) => {
      if (s && s.azimuth != null) {
        setSunContext({ azimuth: s.azimuth, altitude: s.altitude ?? 0, isNight: !!s.isNight })
        themeStore.applyAuto(s.isNight, { sweep: !lightTestMode.value })
      }
    },
    { immediate: true },
  )

  // 播放器启动时暂停特效,关闭后恢复
  const suspendEffects = () => {
    if (_suspended) return
    _suspended = {
      shadowEnabled: shadowEnabled.value,
      weatherEffectEnabled: weatherEffectEnabled.value,
      blobsEnabled: blobsEnabled.value,
      glassEnabled: glassEnabled.value,
      lampMode: lampMode.value,
    }
    shadowEnabled.value = false
    weatherEffectEnabled.value = false
    blobsEnabled.value = false
    glassEnabled.value = false
    lampMode.value = 'off'
  }
  const restoreEffects = () => {
    if (!_suspended) return
    shadowEnabled.value = _suspended.shadowEnabled
    weatherEffectEnabled.value = _suspended.weatherEffectEnabled
    blobsEnabled.value = _suspended.blobsEnabled
    glassEnabled.value = _suspended.glassEnabled
    lampMode.value = _suspended.lampMode
    _suspended = null
  }

  return {
    deskCasters,
    windowSashes,
    rearrangeScene,
    setWeatherVisible,
    sunInfo,
    previewDate,
    previewLoadFailed,
    activeSunInfo,
    sunInfoAt,
    sunInfoAge,
    applySunInfo,
    loadSunInfo,
    slotIdx,
    sunScene,
    weather,
    weatherDetail,
    loadWeather,
    loadSunInfoForDate,
    effectsEnabled,
    lampMode,
    lampTemp,
    lampBrightness,
    shadowEnabled,
    projectionStandard,
    shadowDepth,
    featherStrength,
    shadowAlpha,
    weatherEffectEnabled,
    blobsEnabled,
    glassEnabled,
    anyEffectEnabled,
    toggleLamp,
    idleMinutes,
    isIdle,
    lampStrength,
    lampStrengthAnim,
    lampDivOpacity,
    lampRadius,
    lampMask,
    lampColor,
    dustParticles,
    snowParticles,
    rainParticles,
    castShade,
    roomShade,
    lightLayerOpacity,
    skyTint,
    skyLight,
    diffuseSkyStyle,
    lightTestMode,
    lightTestPaused,
    testSpeed,
    setTestSpeed,
    weatherMode,
    cloudFlicker,
    precipLevel,
    fogVisible,
    setWeather,
    startLightTest,
    pauseLightTest,
    stepLightTest,
    stopLightTest,
    setSlot,
    suspendEffects,
    restoreEffects,
  }
}
