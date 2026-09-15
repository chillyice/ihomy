<!-- 光尘(Light & Dust)主题桌面外壳:顶栏 + studio 外框 + 230px 侧栏 + 主区 -->
<!-- 光影(丁达尔体积光/窗影/尘/台灯)由全局 SunLightLayer 提供,此处只做布局 + 天气 AI 全屏底图 -->
<template>
  <div class="gc-root" :class="{ scrolled: scrolled }">
    <!-- 天气→AI 生图的全屏氛围底图层(最底,压在 SunLightLayer 之下) -->
    <div v-if="weatherBg" class="gc-weatherbg" :style="{ backgroundImage: `url(${weatherBg})` }"></div>

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
          <div class="gc-seg">
            <button :class="{ on: themeStore.mode === 'dawn' }" @click="themeStore.setMode('dawn')">☀ {{ $t('theme.dawn') }}</button>
            <button :class="{ on: themeStore.mode === 'dusk' }" @click="themeStore.setMode('dusk')">☾ {{ $t('theme.dusk') }}</button>
          </div>
        </div>
      </div>

      <!-- 模拟应用 -->
      <div class="gc-studio">
        <div class="gc-app">
          <!-- 侧栏:按分类分组,组头可折叠,默认只展开内容组 -->
          <aside class="gc-side">
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
                    :class="{ act: isActive(m.path) }"
                    @click="navigate(m.path)"
                  >
                    <span class="gc-dot"></span>{{ m.title }}
                  </div>
                </div>
              </div>
            </nav>
          </aside>

          <!-- 主区 -->
          <div class="gc-main" ref="mainEl">
            <GuangchenHome v-if="route.path === '/home'" :weather-bg="weatherBg" />
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
import GuangchenHome from '@/components/guangchen/GuangchenHome.vue'

const router = useRouter()
const route = useRoute()
const themeStore = useThemeStore()
const appStore = useAppStore()
const userStore = useUserStore()
const sunLight = inject(SUN_LIGHT_KEY)

const familyName = computed(() => appStore.familyName)
const familyDescription = computed(() => appStore.family?.description || '')
const familyInitial = computed(() => (familyName.value || 'ihomy').charAt(0))

// 天气→AI 生图的全屏氛围背景(「活窗」放大到整屏,最底层)
const { weatherBg, load: loadWeatherBg } = useWeatherBg()
watch(() => sunLight?.weather?.value, (w) => { if (w) loadWeatherBg(w) }, { immediate: true })

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
.gc-seg { display: inline-flex; padding: 4px; gap: 4px; background: var(--color-card); border: 1px solid var(--color-border); border-radius: 14px; box-shadow: var(--shadow); }
.gc-seg button { all: unset; cursor: pointer; padding: 8px 20px; border-radius: 11px; font-size: 13.5px; color: var(--color-text-secondary); transition: .25s; }
.gc-seg button.on { background: var(--color-brand); color: var(--color-card); box-shadow: var(--shadow); }
.gc-seg button.on:hover { background: var(--color-brand-hover); }

/* ===== studio 外框(钉在浏览器视口内,内容内部滚动) ===== */
.gc-studio { flex: 1; min-height: 0; display: flex; border-radius: 22px; overflow: hidden; background: var(--color-bg); border: 1px solid var(--color-border); box-shadow: var(--shadow-hover); }
.gc-app { display: grid; grid-template-columns: 230px 1fr; height: 100%; width: 100%; }

/* 侧栏 */
.gc-side { min-height: 0; overflow-y: auto; background: linear-gradient(180deg, var(--color-card), var(--color-card-2)); border-right: 1px solid var(--color-line); padding: 16px 14px; }
.gc-nav-item { display: flex; align-items: center; gap: 10px; padding: 11px 12px; border-radius: 11px; font-size: 13.5px; color: var(--color-text-secondary); cursor: pointer; margin-bottom: 2px; transition: .2s; }
.gc-nav-item:hover { background: var(--color-line); color: var(--color-text); }
.gc-nav-item.act { background: var(--color-brand); color: var(--color-card); box-shadow: var(--shadow); }
.gc-dot { width: 7px; height: 7px; border-radius: 50%; background: currentColor; opacity: .7; flex-shrink: 0; }

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

/* 按钮/标签(光尘专属) */
.gc-btn { display: inline-flex; align-items: center; justify-content: center; border: 1px solid var(--color-border); background: var(--color-line); color: var(--color-text-secondary); border-radius: 11px; padding: 9px 16px; font-size: 13px; cursor: pointer; transition: .2s; font-weight: 550; }
.gc-btn:hover { background: var(--color-card-2); color: var(--color-text); }
.gc-btn.ghost { background: transparent; }
.gc-btn.sm { padding: 6px 12px; font-size: 12px; border-radius: 9px; }

@media (max-width: 880px) {
  .gc-app { grid-template-columns: 1fr; }
  .gc-side { display: none; }
}
</style>
