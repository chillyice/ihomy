<!-- 左侧导航栏:图标+文字,按 category 分组,hover 放大阴影加深 -->
<!-- 全局挂载(含沉浸式页),替代原顶部导航 -->
<template>
  <aside class="app-sidebar" :class="{ collapsed, 'dragging-edge': widgetDragging && !crossedEdge }">
    <!-- 顶部:家庭名 + 折叠按钮 -->
    <div class="sidebar-head">
      <span class="sidebar-brand" @click="$router.push('/')">{{ familyName || 'ihomy' }}</span>
      <span class="sidebar-toggle" @click="collapsed = !collapsed" :title="collapsed ? '展开' : '收起'">
        <el-icon><Fold v-if="!collapsed" /><Expand v-else /></el-icon>
      </span>
    </div>

    <!-- 导航列表 -->
    <nav class="sidebar-nav">
      <div v-for="g in groupedModules" :key="g.category" class="nav-group">
        <div v-if="!collapsed && g.modules.length" class="group-label">{{ categoryLabel(g.category) }}</div>
        <div
          v-for="m in g.modules"
          :key="m.code"
          class="nav-item-wrap"
        >
          <div
            class="nav-item"
            :class="{ active: isActive(m.path), 'widget-src': appStore.homeEditMode && widgetType(m.code) }"
            :title="m.title"
            @click="appStore.homeEditMode ? null : navigate(m.path)"
            @mousedown="appStore.homeEditMode && widgetType(m.code) && startWidgetDrag(widgetType(m.code), $event)"
          >
            <span class="nav-icon">
              <svg v-if="m.code === 'settings'" viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><path d="M12 2L9.5 4.5L6 4L5 7.5L2 9.5L3.5 13L2 16.5L5 18.5L6 22L9.5 21.5L12 24L14.5 21.5L18 22L19 18.5L22 16.5L20.5 13L22 9.5L19 7.5L18 4L14.5 4.5L12 2ZM12 16A4 4 0 1 1 12 8A4 4 0 0 1 12 16Z"/></svg>
              <svg v-else-if="m.code === 'ops'" viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><path d="M3 4H21V16H3V4ZM5 6V14H19V6H5ZM2 18H22V20H2V18Z"/></svg>
              <el-icon v-else><component :is="iconComp(m.code)" /></el-icon>
            </span>
            <span v-if="!collapsed" class="nav-text">{{ m.title }}</span>
          </div>
        </div>
      </div>
    </nav>

    <!-- 底部:主题/台灯/语言/用户 -->
    <div class="sidebar-foot">
      <div class="foot-row">
        <span class="foot-btn" :title="theme.dark ? '浅色' : '深色'" @click="onTheme">
          <el-icon><Sunny v-if="!theme.dark" /><Moon v-else /></el-icon>
        </span>
        <!-- 台灯三态开关:auto(自动)/on(常开)/off(关闭);关灯时冷蓝微光便于定位 -->
        <span class="foot-btn" :class="{ 'lamp-on': lampMode !== 'off', 'lamp-off': lampMode === 'off' }" :title="'台灯:' + (lampMode === 'auto' ? '自动' : lampMode === 'on' ? '常开' : '关闭')" @click="toggleLamp">
          {{ lampMode === 'auto' ? '🌑' : lampMode === 'on' ? '💡' : '⬛' }}
        </span>
        <!-- 光影效果开关 -->
        <span class="foot-btn" :class="{ 'lamp-on': sunLight?.shadowEnabled?.value }" :title="sunLight?.shadowEnabled?.value ? '光影效果:开' : '光影效果:关'" @click="toggleLightEffect">
          {{ sunLight?.shadowEnabled?.value ? '☀' : '☁' }}
        </span>
        <span class="foot-btn" title="语言" @click="onLang">
          {{ locale === 'en' ? 'EN' : '中' }}
        </span>
        <el-popover v-if="userStore.isLoggedIn" placement="top-end" :width="340" trigger="click" @show="loadNotifications">
          <template #reference>
            <el-badge :value="unreadCount" :hidden="!unreadCount" class="foot-badge">
              <span class="foot-btn" title="消息"><el-icon><Bell /></el-icon></span>
            </el-badge>
          </template>
          <div class="notify-panel">
            <div class="notify-head">
              <span>{{ $t('nav.notifications') }}</span>
              <el-button v-if="notifications.length" text size="small" @click="markAllRead">{{ $t('nav.allRead') }}</el-button>
            </div>
            <div v-if="notifications.length" class="notify-list">
              <div
                v-for="n in notifications"
                :key="n.id"
                class="notify-item"
                :class="{ unread: !n.isRead }"
                @click="onNotifyClick(n)"
              >
                <div class="notify-type">{{ notifyType(n.type) }}</div>
                <div class="notify-content">{{ n.content }}</div>
                <div class="notify-time">{{ notifyTime(n.createdAt) }}</div>
              </div>
            </div>
            <el-empty v-else :description="$t('nav.noNotification')" :image-size="60" />
          </div>
        </el-popover>
      </div>
      <div class="foot-user-row">
        <el-dropdown v-if="userStore.isLoggedIn" trigger="click" @command="onUserCommand" placement="top-start" popper-class="sidebar-user-popper" @visible-change="onDropdownVisible">
          <span class="foot-user">
            <el-avatar :size="28" :src="userInfo?.avatar">{{ (userInfo?.nickname || 'U').charAt(0) }}</el-avatar>
            <span v-if="!collapsed" class="user-name">{{ userInfo?.nickname || '我' }}</span>
          </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">{{ $t('settings.profile') }}</el-dropdown-item>
            <el-dropdown-item command="settings">{{ $t('nav.settings') }}</el-dropdown-item>
            <div v-if="families.length > 1" class="family-switch-wrapper">
              <el-dropdown-item class="family-switch-trigger">
                {{ $t('nav.switchFamily') }}
                <el-icon class="el-icon--right"><ArrowRight /></el-icon>
              </el-dropdown-item>
              <div class="family-switch-panel">
                <div
                  v-for="f in families"
                  :key="f.familyId"
                  class="family-switch-item"
                  :class="{ active: f.isCurrent }"
                  @click="switchFamily(f.familyId)"
                >
                  <span>{{ f.name }}</span>
                  <el-icon v-if="f.isCurrent"><Check /></el-icon>
                </div>
              </div>
            </div>
            <el-dropdown-item divided command="logout">{{ $t('nav.logout') }}</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
        <span v-else class="foot-btn" @click="$router.push('/login')">{{ $t('home.loginToView') }}</span>
        <span v-if="userStore.isLoggedIn && route.path === '/'" class="edit-mode-btn" :class="{ active: appStore.homeEditMode }" :title="appStore.homeEditMode ? '退出编辑' : '编辑首页'" @click="appStore.toggleHomeEditMode()">
          <el-icon><EditPen /></el-icon>
        </span>
        <!-- 非首页:编辑按钮位置显示迷你天气(图标+当前温度;有预警时显示最高级别颜色的三角叹号,悬浮看全部预警) -->
        <el-tooltip v-else-if="sidebarWeather" placement="top-end" :disabled="!sidebarWarnings.length" popper-class="mini-weather-popper">
          <template #content>
            <div v-for="w in sidebarWarnings" :key="w.id" class="mw-warn-row">
              <span class="mw-warn-dot" :style="{ background: warnLevelColor(w.level) }"></span>
              <span>{{ w.typeName }} {{ w.level }}预警 {{ (w.startTime || '').slice(5, 16) }} ~ {{ (w.endTime || '').slice(5, 16) }}</span>
            </div>
          </template>
          <span class="mini-weather" :title="sidebarWeather.city" @click="$router.push('/weather')">
            <i :class="'qi-' + sidebarWeather.iconCode" class="mw-icon"></i>
            <span v-if="!collapsed" class="mw-temp">{{ sidebarWeather.temp }}°</span>
            <span v-if="topWarningBadge" class="mw-warn-badge" :style="{ color: warnLevelColor(topWarningBadge.level) }">
              <el-icon><WarningFilled /></el-icon>
            </span>
          </span>
        </el-tooltip>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { ref, computed, inject, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { useWidgetDrag } from '@/utils/useWidgetDrag'
import { notificationApi, authApi } from '@/api'
import { ElMessage } from 'element-plus'
import { Sunny, Moon, Bell, Fold, Expand, Document, Notebook, Picture, Calendar, VideoPlay, Trophy, Aim, AlarmClock, List, Star, Wallet, PictureRounded, Share, User, Box, MapLocation, ChatDotRound, Food, Reading, Setting, Monitor, ArrowRight, Check, Headset, EditPen, WarningFilled } from '@element-plus/icons-vue'

// 导航图标:Element Plus 简约线性图标(统一风格,非彩色 emoji)
const ICON_MAP = {
  blog: Document, diary: Notebook, album: Picture, anniversary: Calendar, cinema: VideoPlay, music: Headset,
  points: Trophy, task: Aim, reminder: AlarmClock, plan: List, wish: Star,
  book: Wallet, cascade: PictureRounded, tree: Share, member: User, storage: Box, item: MapLocation,
  chat: ChatDotRound, kitchen: Food, library: Reading, settings: Setting, ops: Monitor,
}
const iconComp = (code) => ICON_MAP[code] || Document
import { applyLocale } from '@/i18n'
import { applyTheme, loadTheme } from '@/theme'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import { warnLevelColor, topWarning } from '@/utils/dict'

const router = useRouter()
const route = useRoute()
const appStore = useAppStore()
const userStore = useUserStore()
const { locale, t } = useI18n()
const { startDrag: startWidgetDrag, dragging: widgetDragging, crossed: crossedEdge } = useWidgetDrag()

const WIDGET_MAP = { blog: 'feed', task: 'task', points: 'today', weather: 'weather', anniversary: 'anni', kitchen: 'recipe', item: 'search', wish: 'wish', book: 'finance', album: 'album', music: 'music' }
const widgetType = (code) => WIDGET_MAP[code] || null

// 注入全局光影状态(与 SunLightLayer 共享同一实例);导航栏只用台灯开关,其余设置在 Settings 页
const sunLight = inject(SUN_LIGHT_KEY)
const { lampMode, toggleLamp } = sunLight || {}
// 迷你天气(非首页时显示在编辑按钮位置):简版天气 + 预警徽标
const sidebarWeather = computed(() => sunLight?.weather?.value || null)
const sidebarWarnings = computed(() => sunLight?.weatherDetail?.value?.warning || [])
const topWarningBadge = computed(() => topWarning(sidebarWarnings.value))
// 光影总开关:全量切换 5 项特效(对齐 MobileMePage 同款开关语义),
// 只切 shadowEnabled 会导致"关闭所有特效"后 blobs/毛玻璃/台灯/天气仍运行(频闪排查踩坑)
const toggleLightEffect = () => {
  if (!sunLight?.shadowEnabled) return
  const on = !sunLight.shadowEnabled.value
  sunLight.shadowEnabled.value = on
  sunLight.blobsEnabled.value = on
  sunLight.weatherEffectEnabled.value = on
  sunLight.glassEnabled.value = on
  sunLight.lampMode.value = on ? 'auto' : 'off'
}

const collapsed = ref(false)
const theme = ref(loadTheme())

const familyName = computed(() => appStore.familyName)
const userInfo = computed(() => userStore.userInfo)

// 导航路径映射(后端 icon 字段是字符串名,这里映射 code→路由路径)
const NAV_PATHS = {
  blog: '/blog', diary: '/diary', album: '/album', anniversary: '/anniversary',
  cinema: '/cinema', music: '/music', member: '/member', points: '/points', task: '/task',
  reminder: '/reminder', plan: '/plan', wish: '/wish', book: '/book',
  chat: '/chat', tree: '/tree', cascade: '/cascade',
  item: '/item', kitchen: '/kitchen', library: '/library', settings: '/settings', ops: '/ops',
}

// 模块列表:从 store 取,过滤出有路径映射的;末尾追加设置+运维管理(仅 OPS)虚拟模块到 system 分组
const navModules = computed(() => {
  const list = !appStore.modules.length ? [] : appStore.modules
    .filter(m => NAV_PATHS[m.code] && m.enabled !== 0 && m.code !== 'storage')
    .map(m => ({
      code: m.code,
      title: m.title,
      path: NAV_PATHS[m.code] || m.path,
      category: m.category || 'life',
      sortOrder: m.sortOrder || 99,
    }))
  // 追加设置(所有人可见)和运维管理(有 ops:view 权限)到 system 分组
  list.push({ code: 'settings', title: '设置', path: '/settings', category: 'system', sortOrder: 90 })
  if (userStore.hasPerm('ops:view')) {
    list.push({ code: 'ops', title: '运维管理', path: '/ops', category: 'system', sortOrder: 95 })
  }
  return list.sort((a, b) => a.sortOrder - b.sortOrder)
})

// 按 category 分组(相册合并到内容分组)
const groupedModules = computed(() => {
  const groups = {}
  for (const m of navModules.value) {
    const cat = m.category === 'album' ? 'content' : m.category
    if (!groups[cat]) groups[cat] = []
    groups[cat].push(m)
  }
  const order = ['content', 'life', 'social', 'system']
  return order
    .filter(c => groups[c] && groups[c].length)
    .map(c => ({ category: c, modules: groups[c] }))
})

const categoryLabel = (cat) => ({
  content: '内容', life: '生活', social: '成员', system: '系统',
}[cat] || '功能')

const isActive = (path) => {
  if (path === '/') return route.path === '/'
  return route.path.startsWith(path)
}

const navigate = (path) => {
  if (route.path === path) return
  router.push(path)
}

// 主题切换
const onTheme = () => {
  theme.value = applyTheme({ ...theme.value, dark: !theme.value.dark, autoMode: false })
  ElMessage.info({ message: '已切换到手动主题,日出日落自动切换已暂停(可在设置中恢复)', duration: 4000 })
}

// 语言切换
const onLang = () => {
  const next = locale.value === 'en' ? 'zh-CN' : 'en'
  applyLocale(next)
  ElMessage.success(next === 'en' ? 'Language switched' : '语言已切换')
}

// 通知
const unreadCount = ref(0)
const notifications = ref([])
const loadUnread = async () => {
  if (!userStore.isLoggedIn) return
  try { unreadCount.value = await notificationApi.unreadCount() } catch (e) {}
}
const loadNotifications = async () => {
  try {
    notifications.value = await notificationApi.list()
    await loadUnread()
  } catch (e) {}
}
const markAllRead = async () => {
  await notificationApi.markAllRead()
  notifications.value = notifications.value.map(n => ({ ...n, isRead: 1 }))
  unreadCount.value = 0
}
const onNotifyClick = async (n) => {
  if (!n.isRead) {
    await notificationApi.markRead(n.id)
    n.isRead = 1
    await loadUnread()
  }
  if (n.contentType === 'blog') router.push(`/blog/${n.contentId}`)
  else if (n.contentType === 'diary') router.push('/diary')
  else if (n.contentType === 'photo' && n.contentId) router.push('/album')
}
const notifyType = (type) => type === 'reply' ? t('notify.typeReply') : type === 'system' ? t('notify.typeSystem') : t('notify.typeComment')
const notifyTime = (d) => {
  if (!d) return ''
  const date = new Date(d)
  const diff = Date.now() - date.getTime()
  if (diff < 3600000) return Math.max(1, Math.floor(diff / 60000)) + ' 分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + ' 小时前'
  return date.toLocaleDateString(locale.value === 'en' ? 'en-US' : 'zh-CN')
}

// 用户下拉
const onUserCommand = (cmd) => {
  if (cmd === 'logout') {
    userStore.logout()
    location.reload()
  } else if (cmd === 'profile') {
    // 个人资料 → 跳转设置页并指定 profile 分类(即使已在设置页其他分类也能切回)
    router.push({ path: '/settings', query: { tab: 'profile' } })
  } else if (cmd === 'settings') {
    router.push('/settings')
  }
}

// 多家庭切换:下拉打开时加载家庭列表;hover 展开子菜单(纯 CSS),点击切换
const families = ref([])
const onDropdownVisible = async (visible) => {
  if (visible && userStore.isLoggedIn) {
    try { families.value = await authApi.families() } catch (e) { families.value = [] }
  }
}
const switchFamily = async (familyId) => {
  try {
    await userStore.switchFamily(familyId, true)
    ElMessage.success(t('nav.switchFamily') + ' ✓')
    location.reload()
  } catch (e) { ElMessage.error(e.message || 'Failed') }
}

onMounted(() => loadUnread())
</script>

<style scoped>
.app-sidebar {
  position: fixed;
  left: 0; top: 0; bottom: 0;
  width: 220px;
  z-index: 60;
  background: rgba(255, 255, 255, 0.35);
  backdrop-filter: blur(24px) saturate(1.1);
  -webkit-backdrop-filter: blur(24px) saturate(1.1);
  border-right: 1px solid rgba(255, 255, 255, 0.4);
  display: flex;
  flex-direction: column;
  transition: width 0.3s ease, background 1s ease, border-color 1s ease;
  overflow: hidden;
  contain: layout style;
  transform: translateZ(0);
}
.app-sidebar.collapsed {
  width: 64px;
}
html.dark .app-sidebar {
  background: rgba(20, 28, 45, 0.55);
  border-right-color: rgba(255, 255, 255, 0.12);
}

.sidebar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px;
  min-height: 64px;
  gap: 8px;
}
.sidebar-brand {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-primary);
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: color 0.2s;
}
.sidebar-brand:hover { color: var(--color-accent); }
.collapsed .sidebar-brand { display: none; }
.sidebar-toggle {
  cursor: pointer;
  color: var(--color-text-secondary);
  font-size: 18px;
  display: flex;
  align-items: center;
  padding: 4px;
  border-radius: 6px;
  transition: background 0.2s, color 0.2s;
}
.sidebar-toggle:hover {
  background: rgba(58, 46, 34, 0.08);
  color: var(--color-primary);
}

.sidebar-nav {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 4px 10px;
  transform: translateZ(0);
  will-change: transform;
}
.sidebar-nav::-webkit-scrollbar { width: 4px; }
.sidebar-nav::-webkit-scrollbar-thumb { background: rgba(58,46,34,0.15); border-radius: 2px; }

.nav-group {
  margin-bottom: 8px;
}
.group-label {
  font-size: 11px;
  color: var(--color-text-secondary);
  padding: 8px 12px 4px;
  letter-spacing: 1px;
  white-space: nowrap;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 9px 12px;
  border-radius: 10px;
  cursor: pointer;
  color: var(--color-text);
  white-space: nowrap;
  transition: transform 0.2s ease, box-shadow 0.2s ease, background 0.2s ease, color 0.2s ease;
  position: relative;
}
.nav-item:hover {
  background: rgba(255, 255, 255, 0.5);
  transform: translateX(4px) scale(1.03);
  box-shadow: 0 4px 12px rgba(58, 46, 34, 0.12);
}
html.dark .nav-item:hover {
  background: rgba(255, 255, 255, 0.08);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}
.nav-item.active {
  background: rgba(168, 72, 58, 0.12);
  color: var(--color-accent);
  font-weight: 600;
}
html.dark .nav-item.active {
  background: rgba(232, 220, 200, 0.1);
}
.nav-icon {
  font-size: 18px;
  flex-shrink: 0;
  width: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.nav-text {
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
}
.collapsed .nav-item {
  justify-content: center;
  padding: 9px 0;
}
.collapsed .nav-text { display: none; }

.sidebar-foot {
  padding: 10px 12px 16px;
  border-top: 1px solid rgba(58, 46, 34, 0.08);
}
html.dark .sidebar-foot {
  border-top-color: rgba(255, 255, 255, 0.08);
}
.foot-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  justify-content: center;
}
.collapsed .foot-row { gap: 0; }
.foot-btn {
  cursor: pointer;
  color: var(--color-text-secondary);
  padding: 6px 10px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  transition: background 0.2s, color 0.2s, transform 0.2s;
}
.foot-btn:hover {
  background: rgba(58, 46, 34, 0.08);
  color: var(--color-primary);
  transform: scale(1.08);
}
html.dark .foot-btn:hover {
  background: rgba(255, 255, 255, 0.08);
}
.foot-user-row { display: flex; align-items: center; gap: 6px; }
.foot-user-row > :deep(.el-dropdown) { flex: 1; min-width: 0; }
/* 迷你天气(非首页时占编辑按钮位置) */
.mini-weather { display: inline-flex; align-items: center; gap: 4px; cursor: pointer; padding: 3px 6px; border-radius: 8px; flex: none; }
.mini-weather:hover { background: rgba(58, 46, 34, 0.06); }
.mw-icon { font-size: 16px; line-height: 1; color: var(--color-text); }
.mw-temp { font-size: 13px; font-weight: 600; color: var(--color-text); font-variant-numeric: tabular-nums; }
.mw-warn-badge { display: inline-flex; align-items: center; }
.mw-warn-badge :deep(.el-icon) { font-size: 14px; }
:global(.mini-weather-popper) { max-width: 320px; }
.mw-warn-row { display: flex; align-items: center; gap: 6px; font-size: 12px; line-height: 1.6; }
.mw-warn-dot { width: 8px; height: 8px; border-radius: 50%; flex: none; }
.foot-user {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 12px;
  border-radius: 8px;
  transition: background 0.2s;
  flex: 1;
}
.foot-user:hover { background: rgba(58, 46, 34, 0.08); }
html.dark .foot-user:hover { background: rgba(255, 255, 255, 0.08); }
.user-name {
  font-size: 13px;
  color: var(--color-text);
}
.collapsed .user-name { display: none; }
.collapsed .foot-user { justify-content: center; }

.edit-mode-btn {
  flex-shrink: 0;
  width: 28px; height: 28px;
  display: flex; align-items: center; justify-content: center;
  border-radius: 8px;
  cursor: pointer;
  color: var(--color-text-secondary);
  transition: background 0.2s, color 0.2s;
}
.edit-mode-btn:hover { background: rgba(58,46,34,0.08); }
.edit-mode-btn.active { background: rgba(184,140,110,0.2); color: var(--color-accent, #b88c6e); }
html.dark .edit-mode-btn:hover { background: rgba(255,255,255,0.08); }
html.dark .edit-mode-btn.active { background: rgba(212,178,152,0.2); color: #d4b298; }
.collapsed .edit-mode-btn { display: none; }

/* 编辑模式:导航项变为组件来源,向右下偏移+虚线框占位 */
.nav-item-wrap { position: relative; margin: 2px 0; }
.nav-item.widget-src { cursor: grab; }
.nav-item.widget-src:active { cursor: grabbing; }
.nav-item.widget-src {
  transform: translate(4px, 4px);
  background: rgba(184,140,110,0.1);
  border: 1px dashed rgba(184,140,110,0.4);
  transition: transform 0.3s cubic-bezier(0.34,1.56,0.64,1), background 0.2s, border-color 0.2s;
}
.nav-item.widget-src:hover {
  transform: translate(8px, 8px) scale(1.05);
  background: rgba(184,140,110,0.2);
  border-color: rgba(184,140,110,0.6);
  box-shadow: 0 6px 20px rgba(184,140,110,0.15);
}
/* 虚线占位框(编辑模式下原位置) */
.nav-item-wrap:has(.widget-src)::before {
  content: '';
  position: absolute; inset: 0;
  border: 1px dashed rgba(184,140,110,0.2);
  border-radius: 10px;
  pointer-events: none;
}
html.dark .nav-item.widget-src { background: rgba(212,178,152,0.1); border-color: rgba(212,178,152,0.3); }
html.dark .nav-item.widget-src:hover { background: rgba(212,178,152,0.2); border-color: rgba(212,178,152,0.5); }
html.dark .nav-item-wrap:has(.widget-src)::before { border-color: rgba(212,178,152,0.15); }

/* 拖拽时侧边栏右边界气泡效果 */
.app-sidebar.dragging-edge::after {
  content: '';
  position: absolute; right: -2px; top: 50%;
  width: 24px; height: 120px;
  transform: translateY(-50%);
  background: radial-gradient(ellipse 12px 60px at right center, rgba(184,140,110,0.35), transparent 70%);
  pointer-events: none;
  animation: bubblePulse 0.8s ease-in-out infinite;
}
@keyframes bubblePulse { 0%,100% { opacity: 0.6; transform: translateY(-50%) scaleX(1); } 50% { opacity: 1; transform: translateY(-50%) scaleX(1.3); } }

/* 通知面板 */
.notify-panel { max-height: 400px; overflow-y: auto; }
.notify-head {
  display: flex; justify-content: space-between; align-items: center;
  padding: 4px 0 10px; border-bottom: 1px solid var(--color-border);
  margin-bottom: 8px; font-weight: 600;
}
.notify-list { display: flex; flex-direction: column; gap: 6px; }
.notify-item {
  padding: 8px 10px; border-radius: 8px; cursor: pointer;
  transition: background 0.2s;
}
.notify-item:hover { background: rgba(58,46,34,0.06); }
.notify-item.unread { background: rgba(168,72,58,0.08); }
.notify-type { font-size: 12px; color: var(--color-text-secondary); }
.notify-content { font-size: 13px; margin: 2px 0; }
.notify-time { font-size: 11px; color: var(--color-text-secondary); }

/* 台灯按钮点亮态:暖黄背景+辉光 */
.foot-btn.lamp-on {
  background: rgba(255, 200, 100, 0.22);
  box-shadow: 0 0 12px rgba(255, 200, 100, 0.3);
}
/* 关灯态:用伪元素做径向发光圈,确保关灯后按钮可见 */
.foot-btn.lamp-off {
  position: relative;
  background: rgba(100, 150, 220, 0.2);
}
.foot-btn.lamp-off::after {
  content: '';
  position: absolute;
  inset: -8px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(100, 150, 220, 0.5) 0%, rgba(100, 150, 220, 0.2) 40%, transparent 70%);
  z-index: -1;
  pointer-events: none;
  animation: lamp-glow 2.5s ease-in-out infinite;
}
@keyframes lamp-glow {
  0%, 100% { transform: scale(0.9); opacity: 0.6; }
  50% { transform: scale(1.2); opacity: 1; }
}
html.dark .foot-btn.lamp-off::after {
  background: radial-gradient(circle, rgba(100, 150, 220, 0.7) 0%, rgba(100, 150, 220, 0.3) 40%, transparent 70%);
}
</style>

<style>
.sidebar-user-popper .el-scrollbar,
.sidebar-user-popper .el-scrollbar__wrap,
.sidebar-user-popper .el-scrollbar__view,
.sidebar-user-popper .el-dropdown-menu {
  overflow: visible !important;
}
.sidebar-user-popper .family-switch-wrapper { position: relative; }
.sidebar-user-popper .family-switch-trigger { display: flex; justify-content: space-between; align-items: center; }
.sidebar-user-popper .family-switch-panel {
  display: none;
  position: absolute;
  left: 100%;
  top: 0;
  min-width: 160px;
  background: var(--el-bg-color, #fff);
  border: 1px solid var(--el-border-color, #e4e7ed);
  border-radius: 6px;
  padding: 4px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
  z-index: 10;
}
.sidebar-user-popper .family-switch-wrapper:hover .family-switch-panel { display: block; }
.sidebar-user-popper .family-switch-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 12px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  transition: background 0.15s;
}
.sidebar-user-popper .family-switch-item:hover { background: var(--el-fill-color-light, #f5f7fa); }
.sidebar-user-popper .family-switch-item.active { color: var(--el-color-primary, #a8483a); font-weight: 600; }
</style>
