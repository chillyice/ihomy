<!-- 暖居(Warm Dwelling)主题桌面外壳:顶栏 + studio 外框 + 230px 侧栏 + 主区 -->
<!-- 光影(丁达尔体积光/窗影/尘/台灯)由全局 SunLightLayer 提供,此处只做布局 + 天气 AI 全屏底图 -->
<template>
  <div class="gc-root" :class="{ scrolled: scrolled }">
    <!-- 天气→AI 生图的全屏氛围底图层(最底,压在 SunLightLayer 之下);鼠标静止 3s 浮到最前 -->
    <div v-if="weatherBg" class="gc-weatherbg" :class="{ revealed: bgRevealed }" :style="{ backgroundImage: `url(${weatherBg})` }"></div>

    <div class="gc-wrap">
      <!-- 顶栏:家庭名 + 返回按钮 + 工具栏(滚动时胞吐进来) + 晨/暮分段开关 -->
      <div class="gc-topbar">
        <div class="gc-brand" title="返回首页" @click="navigate('/')">
          <div class="gc-logo">{{ familyInitial }}</div>
          <div>
            <h1 class="gc-title">{{ familyName || 'ihomy' }}</h1>
            <p class="gc-sub">{{ familyDescription || '一扇会呼吸的窗' }}</p>
          </div>
        </div>
        <button v-if="canBack" class="gc-back" title="返回上一页" @click="goBack">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 12H5"/><path d="M12 19l-7-7 7-7"/></svg>
        </button>
        <div class="gc-pin" :class="{ on: scrolled }"></div>
        <div class="gc-ctl">
          <div class="gc-seg" ref="segEl">
            <span ref="segThumb" class="gc-seg-thumb" aria-hidden="true"></span>
            <button ref="dawnBtn" :class="{ on: segMode === 'dawn' }" @click="themeStore.setMode('dawn')">☀ {{ $t('theme.dawn') }}</button>
            <button ref="duskBtn" :class="{ on: segMode === 'dusk' }" @click="themeStore.setMode('dusk')">☾ {{ $t('theme.dusk') }}</button>
          </div>
        </div>
      </div>

      <!-- 模拟应用 -->
      <div class="gc-studio">
        <div class="gc-app">
          <!-- 侧栏:按分类分组,组头可折叠,默认只展开内容组 -->
          <aside class="gc-side">
            <!-- 用户信息(预览 .user 位置):头像 + 昵称 + 所在家庭,点击进设置 -->
            <div class="gc-user" title="个人设置" @click="navigate('/settings')">
              <el-avatar :size="38" :src="userInfo?.avatar">{{ userInitial }}</el-avatar>
              <div class="gc-user-meta">
                <div class="gc-user-name">{{ userInfo?.nickname || '我' }}</div>
                <div class="gc-user-fam">{{ familyName || 'ihomy' }}</div>
              </div>
            </div>
            <nav>
              <div v-for="g in navGroups" :key="g.key" class="gc-nav-group">
                <button class="gc-nav-group-head" @click="toggleGroup(g.key)">
                  <svg class="gc-chev" :class="{ open: expanded[g.key] }" viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18l6-6-6-6"/></svg>
                  <span class="gc-group-label">{{ g.label }}</span>
                </button>
                <div v-if="expanded[g.key]" class="gc-nav-group-body">
                  <div
                    v-for="m in g.items"
                    :key="m.code"
                    class="gc-nav-item"
                    :class="{ act: isActive(m.path), 'widget-src': appStore.homeEditMode && draggableCodes.has(m.code) }"
                    :draggable="appStore.homeEditMode && draggableCodes.has(m.code)"
                    @click="appStore.homeEditMode && draggableCodes.has(m.code) ? null : navigate(m.path)"
                    @dragstart="onNavDragStart($event, m.code)"
                  >
                    <span class="gc-dot"></span>{{ m.title }}
                  </div>
                </div>
              </div>
            </nav>
          </aside>

          <!-- 主区 -->
          <div class="gc-main" ref="mainEl">
            <WarmHome v-if="route.path === '/home'" :weather-bg="weatherBg" />
            <router-view v-else v-slot="{ Component, route }">
              <transition :name="route.meta.transition || 'fade'" mode="out-in">
                <component :is="Component" :key="route.path" />
              </transition>
            </router-view>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, inject, watch, ref, onMounted, onBeforeUnmount, provide, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useThemeStore } from '@/stores/theme'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import { useWeatherBg } from '@/composables/useWeatherBg'
import WarmHome from '@/components/warm/WarmHome.vue'

const router = useRouter()
const route = useRoute()
const themeStore = useThemeStore()
const appStore = useAppStore()
const userStore = useUserStore()
const sunLight = inject(SUN_LIGHT_KEY)

const familyName = computed(() => appStore.familyName)
const familyDescription = computed(() => appStore.family?.description || '')
const familyInitial = computed(() => (familyName.value || 'ihomy').charAt(0))

// 当前登录用户信息(侧栏顶部 .user 位置:头像 + 昵称 + 所在家庭)
const userInfo = computed(() => userStore.userInfo)
const userInitial = computed(() => (userInfo.value?.nickname || '我').charAt(0))

// 天气→AI 生图的全屏氛围背景(「活窗」放大到整屏,最底层)
const { weatherBg, load: loadWeatherBg } = useWeatherBg()
watch(() => sunLight?.weather?.value, (w) => { if (w) loadWeatherBg(w) }, { immediate: true })

// 背景照片待机浮现:只有鼠标停在「背景板」上(天气照片实际可见的区域)且确有背景图时,
// 静止超过 3s → 背景浮到最前;移动鼠标 → 恢复原始状态
const bgRevealed = ref(false)
const BG_IDLE_MS = 3000
// 命中判定:studio 外框内(含侧栏/主区/卡片/间隙)与顶栏内容、浮层都不是背景板
const CONTENT_SELECTOR = '.gc-brand, .gc-seg, .gc-back, .el-overlay, .el-popper, .el-message'
const isBackgroundArea = (x, y) => {
  const el = document.elementFromPoint(x, y)
  if (!el) return false
  if (el.closest('.gc-studio')) return false // studio 外框盖住天气照片,内里一律不算背景
  if (el.closest(CONTENT_SELECTOR)) return false // 顶栏内容/浮层不算背景
  return true
}
let bgIdleTimer = null
const resetBgIdle = (e) => {
  clearTimeout(bgIdleTimer)
  bgRevealed.value = false
  if (!weatherBg.value) return // 无背景图片时不触发
  if (!isBackgroundArea(e.clientX, e.clientY)) return // 不是背景板不触发
  bgIdleTimer = setTimeout(() => { bgRevealed.value = true }, BG_IDLE_MS)
}
onMounted(() => { window.addEventListener('mousemove', resetBgIdle, { passive: true }) })
onBeforeUnmount(() => { clearTimeout(bgIdleTimer); window.removeEventListener('mousemove', resetBgIdle) })

const NAV_PATHS = {
  blog: '/blog', diary: '/diary', album: '/album', anniversary: '/anniversary',
  cinema: '/cinema', music: '/music', member: '/member', points: '/points', task: '/task',
  reminder: '/reminder', plan: '/plan', wish: '/wish', book: '/book',
  chat: '/chat', tree: '/tree', cascade: '/cascade',
  item: '/item', kitchen: '/kitchen', library: '/library', settings: '/settings', ops: '/ops',
  storage: '/storage/files', tools: '/tools',
}

const GROUP_ORDER = ['content', 'life', 'social', 'system']
const GROUP_LABELS = { content: '内容', life: '生活', social: '成员', system: '系统' }

// 折叠状态:默认只展开内容组,其余组收起(点击组头切换)
const expanded = ref({ content: true })
const toggleGroup = (key) => {
  expanded.value = { ...expanded.value, [key]: !expanded.value[key] }
}

// 按 category 分组(相册并入内容),分组顺序 content/life/social/system,组内 sortOrder
const navGroups = computed(() => {
  const list = !appStore.modules.length ? [] : appStore.modules
    .filter((m) => NAV_PATHS[m.code] && m.enabled !== 0)
    .map((m) => ({ code: m.code, title: m.title, path: NAV_PATHS[m.code] || m.path, category: m.category || 'life', sortOrder: m.sortOrder || 99 }))
  list.push({ code: 'settings', title: '设置', path: '/settings', category: 'system', sortOrder: 90 })
  if (userStore.hasPerm('ops:view')) list.push({ code: 'ops', title: '运维管理', path: '/ops', category: 'system', sortOrder: 95 })
  list.sort((a, b) => a.sortOrder - b.sortOrder)
  const groups = {}
  for (const m of list) {
    const cat = m.category === 'album' ? 'content' : m.category
    if (!groups[cat]) groups[cat] = []
    groups[cat].push(m)
  }
  return GROUP_ORDER
    .filter((c) => groups[c] && groups[c].length)
    .map((c) => ({ key: c, label: GROUP_LABELS[c] || '功能', items: groups[c] }))
})

const isActive = (path) => (path === '/' ? route.path === '/' : route.path.startsWith(path))
const navigate = (path) => { if (route.path !== path) router.push(path) }

// 编辑模式下:侧栏模块可拖入首页(与 WarmHome 的 dataTransfer 标记 'application/x-ihomy-widget' 约定)
// settings/ops 是虚拟入口(不在 appStore.modules 内),不会成为拖拽源
const draggableCodes = computed(() => new Set(appStore.modules.map((m) => m.code)))
const onNavDragStart = (e, code) => {
  e.dataTransfer.setData('application/x-ihomy-widget', code)
  e.dataTransfer.effectAllowed = 'copy'
}

// 主区内部滚动:工具栏滚出内容窗口顶部时触发胞吐
const mainEl = ref(null)
const scrolled = ref(false)
const pinned = ref(false) // toolbar 是否在顶栏(反向延迟回来,配合 holder 渐显让内容平滑下移)
const threshold = ref(36)
const measureThreshold = () => {
  const toolbar = mainEl.value?.querySelector('.page-toolbar')
  if (toolbar && mainEl.value) {
    threshold.value = toolbar.getBoundingClientRect().top - mainEl.value.getBoundingClientRect().top
  }
}
const onMainScroll = () => { scrolled.value = (mainEl.value?.scrollTop ?? 0) > threshold.value - 4 }
onMounted(() => {
  measureThreshold()
  mainEl.value?.addEventListener('scroll', onMainScroll, { passive: true })
})
onBeforeUnmount(() => mainEl.value?.removeEventListener('scroll', onMainScroll))
watch(() => route.fullPath, () => nextTick(measureThreshold))

// 晨暮分段开关滑块:选中态(高亮 + thumb)跟随本地 segMode,扫光结束后才更新,让滑块滑动可见(否则被扫光全屏幕布盖住看不到)
const segMode = ref(themeStore.mode)
const segEl = ref(null)
const segThumb = ref(null)
const dawnBtn = ref(null)
const duskBtn = ref(null)
const positionSegThumb = () => {
  const seg = segEl.value
  const thumb = segThumb.value
  const btn = segMode.value === 'dawn' ? dawnBtn.value : duskBtn.value
  if (!seg || !thumb || !btn) return
  const cs = getComputedStyle(seg)
  const borderL = parseFloat(cs.borderLeftWidth) || 0
  const borderT = parseFloat(cs.borderTopWidth) || 0
  const sr = seg.getBoundingClientRect()
  const br = btn.getBoundingClientRect()
  thumb.style.left = `${br.left - sr.left - borderL}px`
  thumb.style.top = `${br.top - sr.top - borderT}px`
  thumb.style.width = `${br.width}px`
  thumb.style.height = `${br.height}px`
}
// 晨暮切换会触发扫光(html 挂 theme-sweeping),等扫光结束再滑滑块
const waitSweepEnd = () => new Promise((resolve) => {
  const root = document.documentElement
  if (!root.classList.contains('theme-sweeping')) return resolve()
  const mo = new MutationObserver(() => {
    if (!root.classList.contains('theme-sweeping')) { mo.disconnect(); resolve() }
  })
  mo.observe(root, { attributes: true, attributeFilter: ['class'] })
  setTimeout(() => { mo.disconnect(); resolve() }, 2100) // 兜底(扫光最长约 2s)
})
watch(() => themeStore.mode, async (m) => {
  await waitSweepEnd()
  if (themeStore.mode !== m) return // 等待期间又切了,交给最新一次
  segMode.value = m
  positionSegThumb()
})
onMounted(() => {
  // 首次定位不播放过渡(否则加载时滑块从 0 尺寸滑入)
  if (segThumb.value) segThumb.value.style.transition = 'none'
  positionSegThumb()
  requestAnimationFrame(() => { if (segThumb.value) segThumb.value.style.transition = '' })
  // 语言切换等导致按钮宽度变化时重定位
  const ro = new ResizeObserver(() => positionSegThumb())
  if (segEl.value) ro.observe(segEl.value)
  onBeforeUnmount(() => ro.disconnect())
})

// pinned 跟随 scrolled:正向立即进顶栏,反向延迟回来(等 holder 渐显完成后 toolbar 再回位)
let pinTimer = null
watch(scrolled, (val) => {
  clearTimeout(pinTimer)
  if (val) {
    pinned.value = true
  } else {
    pinTimer = setTimeout(() => { pinned.value = false }, 450)
  }
})

provide('gc-scrolled', scrolled)
provide('gc-pinned', pinned)

// 工具栏胞吐 FLIP:工具栏视觉转移(pinned)时,从内容窗口顶部平滑飞到顶栏(细胞膜),外壳随后渐隐融合(膜溶解)
// 用 Web Animations API 做位移(避免内联 transition 覆盖外壳渐隐的 CSS transition)
watch(pinned, async (val) => {
  const toolbar = document.querySelector('.page-toolbar')
  if (!toolbar) return
  toolbar.classList.remove('fused')
  const first = toolbar.getBoundingClientRect()
  await nextTick()
  const last = toolbar.getBoundingClientRect()
  let dx = 0, dy = 0
  if (val) {
    // 正向:钳制起始位置到内容窗口顶部(避免滚动过快时从屏幕外滑入)
    const mainTop = mainEl.value?.getBoundingClientRect().top ?? last.top
    const startTop = Math.max(first.top, mainTop)
    dx = first.left - last.left
    dy = startTop - last.top
  } else {
    dx = first.left - last.left
    dy = first.top - last.top
  }
  if (Math.abs(dx) > 1 || Math.abs(dy) > 1) {
    toolbar.animate(
      [{ transform: `translate(${dx}px, ${dy}px)` }, { transform: 'translate(0, 0)' }],
      { duration: 450, easing: 'cubic-bezier(.4,0,.2,1)' }
    )
  }
  // 外壳渐隐:FLIP 位移后半段开始膜融合
  if (val) setTimeout(() => toolbar.classList.add('fused'), 200)
})

// 返回按钮:历史返回(无历史时隐藏)
const canBack = ref(false)
const goBack = () => router.back()
watch(() => route.fullPath, () => { canBack.value = window.history.state?.back !== null }, { immediate: true })
</script>

<style>
.gc-root { min-height: 100vh; color: var(--color-text); }

/* 天气 AI 生图全屏底图(最底层,压在 SunLightLayer 之下) */
.gc-weatherbg { position: fixed; inset: 0; z-index: 0; background-size: cover; background-position: center; opacity: .3; pointer-events: none; transition: opacity 1.2s ease; }
/* 待机浮现:鼠标静止 3s 后浮到最前(盖过内容层,pointer-events:none 不挡交互;移动鼠标即移除该类恢复) */
.gc-weatherbg.revealed { z-index: 30; opacity: 1; }

/* ===== 预览壳 ===== */
.gc-wrap { position: relative; z-index: 10; max-width: 1180px; margin: 0 auto; height: 100vh; padding: 24px 20px 24px; display: flex; flex-direction: column; box-sizing: border-box; }
.gc-topbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 18px; flex-wrap: wrap; flex-shrink: 0; height: 44px; }
.gc-brand { display: flex; align-items: center; gap: 14px; cursor: pointer; }
.gc-logo { width: 44px; height: 44px; border-radius: 14px; background: var(--color-brand); color: var(--color-card);
  display: grid; place-items: center; font-weight: 700; font-size: 20px; box-shadow: var(--shadow); }
.gc-title { font-size: 18px; margin: 0; font-weight: 650; letter-spacing: .3px; color: var(--color-text); }
.gc-sub { margin: 2px 0 0; font-size: 12.5px; color: var(--color-text-secondary); }
.gc-ctl { display: flex; gap: 10px; align-items: center; }
/* 返回按钮(历史返回,无历史时隐藏) */
.gc-back { all: unset; cursor: pointer; display: grid; place-items: center; width: 34px; height: 34px; border-radius: 10px; border: 1px solid var(--color-border); background: var(--color-card); color: var(--color-text-secondary); flex-shrink: 0; box-shadow: var(--shadow); transition: color .2s, background .2s; }
.gc-back:hover { color: var(--color-text); background: var(--color-card-2); }
/* 工具栏胞吐进来的插槽(顶栏中间,左对齐) */
.gc-pin { flex: 1; min-width: 0; display: flex; align-items: center; justify-content: flex-start; height: 100%; overflow: hidden; }
.gc-seg { position: relative; display: inline-flex; padding: 4px; gap: 4px; background: var(--color-card); border: 1px solid var(--color-border); border-radius: 14px; box-shadow: var(--shadow); }
.gc-seg-thumb { position: absolute; border-radius: 11px; background: var(--color-brand); box-shadow: var(--shadow); transition: left .25s cubic-bezier(.4,0,.2,1), top .25s cubic-bezier(.4,0,.2,1), width .25s cubic-bezier(.4,0,.2,1), height .25s cubic-bezier(.4,0,.2,1); pointer-events: none; z-index: 0; }
.gc-seg button { all: unset; cursor: pointer; position: relative; z-index: 1; padding: 8px 20px; border-radius: 11px; font-size: 13.5px; color: var(--color-text-secondary); transition: color .25s; }
.gc-seg button.on { color: var(--color-card); }
.gc-seg:has(button.on:hover) .gc-seg-thumb { background: var(--color-brand-hover); }

/* ===== studio 外框(钉在浏览器视口内,内容内部滚动) ===== */
.gc-studio { flex: 1; min-height: 0; display: flex; border-radius: 22px; overflow: hidden; background: var(--color-bg); border: 1px solid var(--color-border); box-shadow: var(--shadow-hover); }
.gc-app { display: grid; grid-template-columns: 230px 1fr; height: 100%; width: 100%; }

/* 侧栏 */
.gc-side { min-height: 0; overflow-y: auto; background: linear-gradient(180deg, var(--color-card), var(--color-card-2)); border-right: 1px solid var(--color-line); padding: 16px 14px; }
.gc-nav-item { display: flex; align-items: center; gap: 10px; padding: 11px 12px; border-radius: 11px; font-size: 13.5px; color: var(--color-text-secondary); cursor: pointer; margin-bottom: 2px; transition: .2s; }
.gc-nav-item:hover { background: var(--color-line); color: var(--color-text); }
.gc-nav-item.act { background: var(--color-brand); color: var(--color-card); box-shadow: var(--shadow); }
/* 编辑模式:可拖入首页的模块(背景高亮 + 抓手光标;settings/ops 不受影响) */
.gc-nav-item.widget-src { cursor: grab; background: rgba(var(--color-brand-rgb), .12); box-shadow: inset 0 0 0 1px rgba(var(--color-brand-rgb), .35); }
.gc-nav-item.widget-src:hover { background: rgba(var(--color-brand-rgb), .2); box-shadow: inset 0 0 0 1px rgba(var(--color-brand-rgb), .6); }
.gc-nav-item.widget-src:active { cursor: grabbing; }
.gc-dot { width: 7px; height: 7px; border-radius: 50%; background: currentColor; opacity: .7; flex-shrink: 0; }

/* 侧栏用户信息(预览 .side .user):头像 + 昵称 + 所在家庭 */
.gc-user { display: flex; align-items: center; gap: 11px; padding: 4px 6px 16px; border-bottom: 1px solid var(--color-line); margin-bottom: 12px; cursor: pointer; }
.gc-user .el-avatar { flex-shrink: 0; background: var(--color-green); color: var(--color-card); font-weight: 700; }
.gc-user-meta { min-width: 0; }
.gc-user-name { font-size: 13.5px; font-weight: 600; color: var(--color-text); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.gc-user-fam { font-size: 11.5px; color: var(--color-text-tertiary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

/* 侧栏分组:组头可折叠,默认只展开内容组 */
.gc-nav-group { margin-bottom: 4px; }
.gc-nav-group-head {
  all: unset;
  display: flex;
  align-items: center;
  gap: 7px;
  width: 100%;
  padding: 9px 12px;
  box-sizing: border-box;
  font-size: 11.5px;
  font-weight: 650;
  letter-spacing: .5px;
  color: var(--color-text-tertiary);
  cursor: pointer;
  transition: color .2s;
}
.gc-nav-group-head:hover { color: var(--color-text-secondary); }
.gc-chev { flex-shrink: 0; transition: transform .2s ease; }
.gc-chev.open { transform: rotate(90deg); }
.gc-group-label { flex: 1; }
.gc-nav-group-body { display: flex; flex-direction: column; }
.gc-nav-group-body .gc-nav-item { padding-left: 28px; }

/* 主区(内部滚动) */
.gc-main { min-height: 0; overflow-y: auto; padding: 14px 24px 30px; }

/* 按钮/标签(暖居专属) */
.gc-btn { display: inline-flex; align-items: center; justify-content: center; border: 1px solid var(--color-border); background: var(--color-line); color: var(--color-text-secondary); border-radius: 11px; padding: 9px 16px; font-size: 13px; cursor: pointer; transition: .2s; font-weight: 550; }
.gc-btn:hover { background: var(--color-card-2); color: var(--color-text); }
.gc-btn.ghost { background: transparent; }
.gc-btn.sm { padding: 6px 12px; font-size: 12px; border-radius: 9px; }

@media (max-width: 880px) {
  .gc-app { grid-template-columns: 1fr; }
  .gc-side { display: none; }
}
</style>
