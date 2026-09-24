<!-- 壁纸页 /wallpaper(meta.public + meta.standalone):家庭私有「只读氛围屏」。
     定位:桌面壁纸/常开副屏用,只做展示——时钟、天气、家人照片、太阳驱动的光影。
     设计边界(勿扩张):除首次登录外不含任何写操作、不上传、不导航;要写博客传照片回正常 ihomy 窗口。
     交互:鼠标静止在背景上 3s → 家人照片浮现、角落控件同时隐去;移动鼠标回到常态
          (沿用 V9.63 暖居「天气背景待机浮现」约定:静默计时 + 移动即复位)。
     主题:复用全局 themeStore(与站点共享 ihomy-theme 偏好);「自动」由太阳高度角驱动(useSunLight 内 applyAuto)。
     体积:本页刻意不引 Element Plus(原生 input/button + 主题 CSS 变量),保证壁纸首屏轻量。
     Wallpaper Engine:壳页(wallpaper-engine/)以 iframe 嵌入本页,属性面板的主题/晨暮走 postMessage 桥(见 onMessage)。 -->
<template>
  <div class="wp" @mousemove="onMove" @touchstart.passive="onMove">
    <!-- 兜底底色:用户关掉毛玻璃等特效时也不留白 -->
    <div class="wp-base" aria-hidden="true"></div>

    <!-- 全局光影层(体积光/窗影/尘/台灯/天气特效);太阳与天气状态由 App.vue provide -->
    <SunLightLayer />

    <!-- 常态环境层:时钟 + 天气(壁纸的主体内容) -->
    <div class="wp-ambient">
      <div class="wp-time">{{ clock.time }}</div>
      <div class="wp-meta">
        {{ clock.date }} · {{ clock.week }}
        <span v-if="familyName"> · {{ familyName }}</span>
      </div>
      <div v-if="weather" class="wp-weather">
        <span class="wp-temp">{{ Math.round(weather.temp) }}°</span>
        <span class="wp-wtext">{{ weather.text }}</span>
        <span class="wp-wcity">{{ weather.city }}</span>
      </div>
    </div>

    <!-- 家人照片:静止 3s 浮现(光影层仍在其上,照片被光柱/浮尘覆盖) -->
    <div class="wp-photos" :class="{ revealed: photosRevealed }" aria-hidden="true">
      <div v-if="stackCards.length" class="wp-stack">
        <div v-for="(p, i) in stackCards" :key="p.id" class="wp-pcard" :style="pcardStyle(i)">
          <img :src="p.url" :alt="p.description || ''" loading="lazy" />
        </div>
      </div>
      <div v-if="topPhoto?.description && photosRevealed" class="wp-cap">{{ topPhoto.description }}</div>
    </div>

    <!-- 角落控件:鼠标移动浮现、静止隐去(壁纸不留常驻界面) -->
    <div class="wp-ui" :class="{ visible: uiVisible }">
      <span v-if="photos.length && !photosRevealed" class="wp-hint">{{ $t('wallpaper.hoverHint') }}</span>
      <div class="wp-seg">
        <button v-for="t in themeStore.themes" :key="t.id" class="wp-segbtn"
                :class="{ on: themeStore.theme === t.id }" @click="themeStore.setTheme(t.id)">
          {{ t.label[locale === 'en' ? 'en' : 'zh'] }}
        </button>
      </div>
      <div class="wp-seg">
        <button class="wp-segbtn" :class="{ on: themeStore.autoMode }"
                :title="$t('wallpaper.autoHint')" @click="themeStore.setAutoMode(true)">{{ $t('wallpaper.auto') }}</button>
        <button class="wp-segbtn" :class="{ on: !themeStore.autoMode && themeStore.mode === 'dawn' }"
                @click="themeStore.setMode('dawn')">{{ $t('wallpaper.dawn') }}</button>
        <button class="wp-segbtn" :class="{ on: !themeStore.autoMode && themeStore.mode === 'dusk' }"
                @click="themeStore.setMode('dusk')">{{ $t('wallpaper.dusk') }}</button>
      </div>
    </div>

    <!-- 未登录:一次性登录卡(登录后本页只读;未登录时不显示任何家庭内容,演示家庭数据也不显示) -->
    <div v-if="!userStore.isLoggedIn" class="wp-login">
      <form class="wp-login-card" @submit.prevent="doLogin">
        <div class="wp-login-title">ihomy</div>
        <div class="wp-login-sub">{{ $t('wallpaper.loginSub') }}</div>
        <input v-model="form.email" class="wp-input" type="email" autocomplete="username"
               :placeholder="$t('wallpaper.email')" />
        <input v-model="form.password" class="wp-input" type="password" autocomplete="current-password"
               :placeholder="$t('wallpaper.password')" />
        <div class="wp-captcha">
          <input v-model="form.captchaCode" class="wp-input" :placeholder="$t('wallpaper.captcha')" />
          <img v-if="captchaImage" :src="captchaImage" class="wp-captcha-img"
               :alt="$t('wallpaper.captcha')" :title="$t('wallpaper.captchaRefresh')" @click="loadCaptcha" />
        </div>
        <div v-if="loginError" class="wp-login-err">{{ loginError }}</div>
        <button class="wp-login-btn" type="submit" :disabled="loggingIn">
          {{ loggingIn ? $t('wallpaper.loggingIn') : $t('wallpaper.login') }}
        </button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, inject, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import SunLightLayer from '@/components/SunLightLayer.vue'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import { useUserStore } from '@/stores/user'
import { useThemeStore } from '@/stores/theme'
import { authApi, publicApi } from '@/api'

const { locale, t } = useI18n()
const route = useRoute()
const userStore = useUserStore()
const themeStore = useThemeStore()
// 全局光影状态(App.vue provide):天气与太阳时隙都从这里取,本页不重复请求
const sunLight = inject(SUN_LIGHT_KEY, null)
const weather = computed(() => sunLight?.weather?.value || null)

// ========== 时钟(每秒对表,仅在显示串变化时写 ref,避免每帧无谓渲染) ==========
const clock = reactive({ time: '', date: '', week: '' })
let clockTimer = null
const syncClock = () => {
  const d = new Date()
  const time = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  const date = t('wallpaper.dateFmt', { m: d.getMonth() + 1, d: d.getDate() })
  const week = t(`weatherPage.w${d.getDay()}`)
  if (time !== clock.time) clock.time = time
  if (date !== clock.date) clock.date = date
  if (week !== clock.week) clock.week = week
}

// ========== 家人照片(仅登录后拉取;未登录不显示任何家庭内容) ==========
const photos = ref([])
const photoIndex = ref(0)
const familyName = ref('')
const PHOTO_STACK_N = 4
const topPhoto = computed(() => photos.value[photoIndex.value] || null)
const stackCards = computed(() => {
  const arr = photos.value
  const n = arr.length
  if (!n) return []
  const out = []
  for (let i = 0; i < Math.min(PHOTO_STACK_N, n); i++) out.push(arr[(photoIndex.value + i) % n])
  return out
})
// 扇形几何走 CSS 变量(与暖居卡牌堆同口径:变量才能被类覆写,写死在内联 transform 里就改不动)
const pcardStyle = (i) => {
  const n = stackCards.value.length
  const mid = (n - 1) / 2
  return {
    '--dx': `${((i - mid) * 30).toFixed(1)}px`,
    '--dy': `${(Math.abs(i - mid) * 14).toFixed(1)}px`,
    '--rot': `${((i - mid) * 4).toFixed(2)}deg`,
    '--z': 10 - i,
  }
}
const loadPhotos = async () => {
  if (!userStore.isLoggedIn) return
  try {
    // 带 JWT 调公开聚合:后端识别为成员 → 返回family 全量最新照片(非成员只回公开照片)
    const data = await publicApi.getHome()
    const list = Array.isArray(data?.photos) ? data.photos : []
    photos.value = list.filter((p) => p && p.url)
    if (photoIndex.value >= photos.value.length) photoIndex.value = 0
    if (data?.family?.name) familyName.value = data.family.name
  } catch (e) {
    // 网络/权限异常:保持环境层,照片区留空即可(壁纸不弹错误提示)
  }
}
let photoRefreshTimer = null
// 家人随时会加照片:10 分钟级刷新(成员视图后端不缓存,别频繁打)。
// 页内登录的场景也要起算,否则登录后只加载一次、之后新照片再不出现。
const startPhotoRefresh = () => {
  if (photoRefreshTimer) return
  photoRefreshTimer = setInterval(loadPhotos, 600000)
}

// ========== 待机浮现 & 控件隐去(沿用 V9.63 约定) ==========
const PHOTO_IDLE_MS = 3000 // 鼠标静止 3s → 照片浮现
const UI_HIDE_MS = 2200 // 控件先一步淡出,让"进入展示模式"有层次
const photosRevealed = ref(false)
const uiVisible = ref(false)
let revealTimer = null
let uiHideTimer = null
let slideTimer = null

const startSlide = () => {
  if (slideTimer || photos.value.length < 2) return
  slideTimer = setInterval(() => { photoIndex.value = (photoIndex.value + 1) % photos.value.length }, 6000)
}
const stopSlide = () => { if (slideTimer) { clearInterval(slideTimer); slideTimer = null } }

// 照片浮现倒计时:鼠标静止 3s 触发。页面刚加载(壁纸/副屏场景鼠标根本没动过)也要起算,
// 否则照片要等用户先动一下鼠标才会出现——留给挂载与登录完成后各调一次。
const scheduleReveal = () => {
  clearTimeout(revealTimer)
  if (!photos.value.length) return
  revealTimer = setTimeout(() => {
    photosRevealed.value = true
    uiVisible.value = false
    startSlide()
  }, PHOTO_IDLE_MS)
}

const onMove = (e) => {
  clearTimeout(revealTimer)
  clearTimeout(uiHideTimer)
  // 移动即回到常态:照片收起、轮播暂停(壁纸不在无人看时白跑定时器)
  if (photosRevealed.value) { photosRevealed.value = false; stopSlide() }
  uiVisible.value = true
  uiHideTimer = setTimeout(() => { uiVisible.value = false }, UI_HIDE_MS)
  // 落在角落控件/登录卡上不算"停在背景":只复位,不重新起算浮现
  if (e?.target?.closest?.('.wp-ui, .wp-login')) return
  scheduleReveal()
}

// ========== 登录(一次性;token 落 localStorage,后续由 request.js 401 自动续期) ==========
const form = reactive({ email: '', password: '', captchaCode: '' })
const captchaId = ref('')
const captchaImage = ref('')
const loggingIn = ref(false)
const loginError = ref('')
const loadCaptcha = async () => {
  try {
    const data = await authApi.captcha()
    captchaId.value = data.captchaId
    captchaImage.value = data.image
  } catch (e) {
    loginError.value = t('wallpaper.captchaFailed')
  }
}
const doLogin = async () => {
  if (loggingIn.value) return
  loginError.value = ''
  if (!form.email.trim() || !form.password || !form.captchaCode.trim()) {
    loginError.value = t('wallpaper.needAll')
    return
  }
  loggingIn.value = true
  try {
    await userStore.login({
      email: form.email.trim(),
      password: form.password,
      captchaId: captchaId.value,
      captchaCode: form.captchaCode.trim(),
    })
    form.password = ''
    form.captchaCode = ''
    await loadPhotos()
    scheduleReveal()
    startPhotoRefresh()
  } catch (e) {
    loginError.value = t('wallpaper.loginFailed')
    form.captchaCode = ''
    loadCaptcha()
  } finally {
    loggingIn.value = false
  }
}

// ========== Wallpaper Engine 偏好:URL 参数优先于 postMessage ==========
// WE 桌面壁纸不能再用 iframe 嵌本页(跨域 iframe 会崩 WE 的 CEF,详见 wallpaper-engine/index.html),
// 壳页改成顶层跳转并把属性面板的 theme/mode 挂在查询串上带过来。普通浏览器访问不受影响(参数可选)。
// 只做一次、且在挂载时执行:属性本身就是页面加载时一次性送达的,后续改面板需重启预览。
const applyQueryPrefs = () => {
  const q = route.query
  if (q.theme === 'warm' || q.theme === 'guangchen') themeStore.setTheme(q.theme)
  if (q.mode === 'auto') themeStore.setAutoMode(true)
  else if (q.mode === 'dawn' || q.mode === 'dusk') themeStore.setMode(q.mode)
}

// ========== Wallpaper Engine 属性桥(可选) ==========
// WE 网页壁纸的壳页(wallpaper-engine/index.html)把属性面板的主题/晨暮 postMessage 进来,
// 因为桌面壁纸未必拿得到鼠标,控件点不动时靠这里换主题。普通浏览器访问完全不受影响。
// 只认形状正确的消息,且忽略自身派发的消息(e.source === window);不读任何家庭数据。
const onMessage = (e) => {
  if (e.source === window) return
  const msg = e.data && e.data.ihomyWallpaper
  if (!msg || typeof msg !== 'object') return
  if (msg.theme) themeStore.setTheme(msg.theme)
  if (msg.mode === 'auto') themeStore.setAutoMode(true)
  else if (msg.mode === 'dawn' || msg.mode === 'dusk') themeStore.setMode(msg.mode)
}

onMounted(async () => {
  window.addEventListener('message', onMessage)
  applyQueryPrefs()
  document.title = 'ihomy'
  syncClock()
  clockTimer = setInterval(syncClock, 1000)
  if (userStore.isLoggedIn) {
    await loadPhotos()
    scheduleReveal()
    startPhotoRefresh()
  } else {
    loadCaptcha()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('message', onMessage)
  clearInterval(clockTimer)
  clearInterval(photoRefreshTimer)
  clearTimeout(revealTimer)
  clearTimeout(uiHideTimer)
  stopSlide()
})
</script>

<style scoped>
.wp { position: fixed; inset: 0; overflow: hidden; color: var(--color-text); }

/* 兜底底色(压在所有光影层之下:SunLightLayer 的 glass-bg 是 z=2) */
.wp-base {
  position: fixed; inset: 0; z-index: 0; pointer-events: none;
  background: linear-gradient(160deg, var(--color-bg) 0%, var(--color-bg-2) 100%);
}

/* ===== 常态环境层:时钟 + 天气(左下) ===== */
.wp-ambient {
  position: fixed; inset: 0; z-index: 10; pointer-events: none;
  display: flex; flex-direction: column; justify-content: flex-end;
  padding: clamp(28px, 4.5vw, 72px); gap: 2px;
}
.wp-time {
  font-size: clamp(52px, 8.5vw, 118px); font-weight: 250; line-height: 1;
  letter-spacing: .01em; font-variant-numeric: tabular-nums;
}
.wp-meta {
  margin-top: 6px; font-size: 13.5px; letter-spacing: .08em; color: var(--color-text-secondary);
}
.wp-weather { display: flex; align-items: baseline; gap: 10px; margin-top: 14px; }
.wp-temp { font-size: 26px; font-weight: 500; font-variant-numeric: tabular-nums; }
.wp-wtext { font-size: 15px; }
.wp-wcity { font-size: 13px; color: var(--color-text-secondary); }

/* ===== 家人照片:待机 3s 浮现(z=30,低于浮尘/体积光 76/78,照片被光覆盖) ===== */
.wp-photos {
  position: fixed; inset: 0; z-index: 30; pointer-events: none;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  opacity: 0; transition: opacity .9s ease;
}
.wp-photos.revealed { opacity: 1; }
.wp-stack { position: relative; width: min(46vw, 640px); aspect-ratio: 4 / 3; }
.wp-pcard {
  position: absolute; inset: 0; border-radius: 14px; overflow: hidden;
  background: var(--color-line); box-shadow: 0 18px 46px rgba(0, 0, 0, .24);
  z-index: var(--z, 10);
  transform: translate(var(--dx, 0), var(--dy, 0)) rotate(var(--rot, 0deg));
  transition: transform .8s cubic-bezier(.22, 1, .36, 1);
}
.wp-pcard img { width: 100%; height: 100%; object-fit: cover; display: block; }
.wp-cap {
  margin-top: 28px; padding: 7px 18px; border-radius: 999px; max-width: 46vw;
  font-size: 13px; color: #fff; background: rgba(0, 0, 0, .38);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

/* ===== 角落控件:移动浮现、静止隐去(右下,避让左下时钟) ===== */
.wp-ui {
  position: fixed; right: clamp(24px, 4vw, 64px); bottom: clamp(24px, 4vw, 64px); z-index: 40;
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap; justify-content: flex-end;
  opacity: 0; transform: translateY(6px); pointer-events: none;
  transition: opacity .5s ease, transform .5s ease;
}
.wp-ui.visible { opacity: 1; transform: none; pointer-events: auto; }
.wp-hint { font-size: 12px; letter-spacing: .04em; color: var(--color-text-secondary); }
/* 触屏没有光标,"鼠标停 3 秒"的提示无意义 */
@media (hover: none) { .wp-hint { display: none; } }
/* 窄屏(手机/竖屏副屏):右下角控件会换行并压住左下角的日期/天气行,改挂右上角 */
@media (max-width: 768px) {
  .wp-ui { top: clamp(18px, 4vw, 28px); bottom: auto; }
  .wp-hint { display: none; }
}
.wp-seg {
  display: flex; gap: 2px; padding: 3px; border-radius: 12px;
  background: rgba(var(--color-card-rgb), .74); border: 1px solid var(--color-border);
}
.wp-segbtn {
  all: unset; cursor: pointer; padding: 6px 12px; border-radius: 9px;
  font-size: 12.5px; font-weight: 600; color: var(--color-text-secondary);
  transition: background .2s ease, color .2s ease; white-space: nowrap;
}
.wp-segbtn:hover { color: var(--color-text); }
.wp-segbtn.on { background: var(--color-brand); color: var(--color-brand-text); }

/* ===== 登录卡(z=90,压在体积光 78 之上保证表单清晰;灯光层 pointer-events:none 不挡输入) =====
   遮罩本身 pointer-events:none、只让卡片吃点击 —— 否则未登录时全屏遮罩会把右下角主题切换按钮一起挡住 */
.wp-login {
  position: fixed; inset: 0; z-index: 90; pointer-events: none;
  display: flex; align-items: center; justify-content: center; padding: 24px;
  background: rgba(0, 0, 0, .3);
}
.wp-login-card {
  width: min(360px, 92vw); padding: 30px 26px 24px; border-radius: 16px;
  display: flex; flex-direction: column; gap: 12px; pointer-events: auto;
  background: var(--color-card); border: 1px solid var(--color-border); box-shadow: var(--shadow-hover);
}
.wp-login-title { font-size: 22px; font-weight: 600; letter-spacing: .04em; }
.wp-login-sub { font-size: 12.5px; color: var(--color-text-secondary); margin-bottom: 4px; }
.wp-input {
  width: 100%; box-sizing: border-box; padding: 10px 12px; border-radius: 10px;
  border: 1px solid var(--color-border); background: var(--color-card-2);
  color: var(--color-text); font-size: 14px; font-family: inherit; outline: none;
}
.wp-input:focus { border-color: var(--color-brand); }
.wp-captcha { display: flex; gap: 10px; align-items: center; }
.wp-captcha-img {
  height: 40px; width: 108px; flex-shrink: 0; border-radius: 8px; cursor: pointer;
  object-fit: cover; border: 1px solid var(--color-border);
}
.wp-login-err { font-size: 12.5px; color: var(--color-accent); }
.wp-login-btn {
  all: unset; cursor: pointer; text-align: center; padding: 11px 16px; border-radius: 12px;
  font-size: 14px; font-weight: 600; background: var(--color-brand); color: var(--color-brand-text);
  transition: background .2s ease;
}
.wp-login-btn:hover { background: var(--color-brand-hover); }
.wp-login-btn[disabled] { opacity: .6; cursor: default; }
</style>
