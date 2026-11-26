<!-- 设置页:桌面端左侧分组导航 + 右侧配置;移动端先给分组列表,点进单个大类(带返回)。
     分类合并口径:每日内容+天气 = 首页内容,个性化+显示模式 = 外观与显示(旧 ?tab= 仍可用,见 TAB_ALIAS) -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('settings.title') }]" />

    <div class="settings-layout" :class="{ 'settings-mobile': isMobile }">
      <!-- 桌面端:分组侧栏 -->
      <aside v-if="!isMobile" class="settings-side paper-surface">
        <el-menu :default-active="activeId" class="settings-menu" @select="openPanel">
          <el-menu-item-group v-for="g in groups" :key="g.key" :title="$t(g.labelKey)">
            <el-menu-item v-for="p in g.items" :key="p.id" :index="p.id">
              <SettingsIcon :name="p.id" class="menu-icon" />{{ $t(p.labelKey) }}
            </el-menu-item>
          </el-menu-item-group>
        </el-menu>
      </aside>

      <!-- 移动端:分组列表 -->
      <SettingsDirectory v-if="isMobile && !activeId" :groups="groups" @open="openPanel" />

      <!-- 配置区:移动端单大类带返回条;桌面端全部 v-show 保留状态。
           无权限的大类(pointsRule/ai/backup)连挂载都跳过,组件不发请求也就不弹 403 -->
      <div v-else class="settings-body">
        <div v-if="isMobile && currentPanel" class="panel-bar paper-heading">
          <SettingsIcon :name="currentPanel.id" class="panel-icon" />
          <span class="panel-title">{{ $t(currentPanel.labelKey) }}</span>
          <button type="button" class="panel-back" @click="closePanel">
            <el-icon><ArrowLeft /></el-icon>
            <span>{{ $t('settings.allSettings') }}</span>
          </button>
        </div>

        <div v-for="panel in mountedPanels" :key="panel.id" v-show="activeId === panel.id">
          <component :is="panel.component" v-if="panel.component" />
          <!-- 首页内容:每日内容 + 画面布局 + 天气(天气仅家长可配) -->
          <template v-else-if="panel.id === 'content'">
            <div id="settings-daily"><DailySettings /></div>
            <div id="settings-layout"><PanelLayoutSettings /></div>
            <div v-if="userStore.hasPerm('family:manage')" id="settings-weather"><WeatherSettings /></div>
          </template>
          <!-- 外观与显示:主题(含主题专属设置)→ 光影 → 每日早安 → 屏幕保护 → 显示模式 → Wallpaper Engine -->
          <template v-else-if="panel.id === 'appearance'">
            <div id="settings-theme"><ThemeSettings /></div>
            <!-- 暖居 = 背景(仅桌面端暖居外壳)+ 窗下桌面小物 -->
            <div id="settings-warmbg">
              <WarmBackgroundSettings :show-objects="isTablet || (isWide && !isMobile)" />
            </div>
            <div id="settings-light"><LightSettings :show-tools="!isMobile" /></div>
            <div id="settings-morning"><MorningSettings /></div>
            <div id="settings-screensaver"><ScreensaverSettings /></div>
            <div id="settings-display"><ModeSettings /></div>
            <div id="settings-wallpaper"><WallpaperSettings /></div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useDevice } from '@/composables/useDevice'
import Breadcrumb from '@/components/Breadcrumb.vue'
import StorageView from '@/views/storage/Storage.vue'
import ProfileSettings from './settings/ProfileSettings.vue'
import HealthSettings from './settings/HealthSettings.vue'
import FamilySettings from './settings/FamilySettings.vue'
import AiSettings from './settings/AiSettings.vue'
import BackupSettings from './settings/BackupSettings.vue'
import PointsRuleSettings from './settings/PointsRuleSettings.vue'
import DailySettings from './settings/DailySettings.vue'
import PanelLayoutSettings from './settings/PanelLayoutSettings.vue'
import WeatherSettings from './settings/WeatherSettings.vue'
import MediaSettings from './settings/MediaSettings.vue'
import LightSettings from './settings/LightSettings.vue'
import ThemeSettings from './settings/ThemeSettings.vue'
import WallpaperSettings from './settings/WallpaperSettings.vue'
import WarmBackgroundSettings from './settings/WarmBackgroundSettings.vue'
import ModeSettings from './settings/ModeSettings.vue'
import MorningSettings from './settings/MorningSettings.vue'
import ScreensaverSettings from './settings/ScreensaverSettings.vue'
import AppSettings from './settings/AppSettings.vue'
import SettingsDirectory from './settings/SettingsDirectory.vue'
import SettingsIcon from './settings/SettingsIcon.vue'
import { isNativeApp } from '@/utils/platform'

const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const { isMobile, isTablet, isWide } = useDevice()

// 大类清单:移动端列表与桌面侧栏共用这一份;perm/nativeOnly 决定可见性
// perm 与后端 @RequirePermission 同码:面板对无权角色隐藏,挂载也一并跳过(防隐形面板发请求吃 403)
// 健康面板不加 perm:令牌/同意/审计全是本人自助端点,孩子与访客也能配自己的
const PANELS = [
  { id: 'profile', labelKey: 'settings.cat.profile', component: ProfileSettings },
  { id: 'health', labelKey: 'settings.cat.health', component: HealthSettings },
  { id: 'family', labelKey: 'settings.cat.family', component: FamilySettings },
  { id: 'pointsRule', labelKey: 'settings.cat.pointsRule', perm: 'points:manage', component: PointsRuleSettings },
  { id: 'ai', labelKey: 'settings.cat.ai', perm: 'family:manage', component: AiSettings },
  { id: 'backup', labelKey: 'settings.cat.backup', perm: 'family:manage', component: BackupSettings },
  { id: 'content', labelKey: 'settings.cat.content' },
  { id: 'media', labelKey: 'settings.cat.media', component: MediaSettings },
  { id: 'storage', labelKey: 'settings.cat.storage', component: StorageView },
  { id: 'appearance', labelKey: 'settings.cat.appearance' },
  { id: 'client', labelKey: 'settings.cat.client', nativeOnly: true, component: AppSettings },
]

// 分组顺序:账号 → 家庭 → 内容与媒体 → 外观与设备
const GROUPS = [
  { key: 'account', labelKey: 'settings.group.account', ids: ['profile', 'health'] },
  { key: 'family', labelKey: 'settings.group.family', ids: ['family', 'pointsRule', 'ai', 'backup'] },
  { key: 'content', labelKey: 'settings.group.content', ids: ['content', 'media', 'storage'] },
  { key: 'device', labelKey: 'settings.group.device', ids: ['appearance', 'client'] },
]

// 旧 ?tab= 别名:合并后落地到新大类,旧链接(放映厅/相册/日程等页面跳转)不失效
const TAB_ALIAS = { daily: 'content', weather: 'content', theme: 'appearance', light: 'appearance', display: 'appearance' }

const visiblePanels = computed(() => PANELS.filter((p) => (!p.perm || userStore.hasPerm(p.perm)) && (!p.nativeOnly || isNativeApp())))

const groups = computed(() =>
  GROUPS.map((g) => ({ ...g, items: g.ids.map((id) => visiblePanels.value.find((p) => p.id === id)).filter(Boolean) })).filter(
    (g) => g.items.length,
  ),
)

// 当前大类由路由 query 驱动:点进/返回都走 history,系统返回键能退回列表
const activeId = computed(() => {
  const raw = String(route.query.tab || '')
  const id = TAB_ALIAS[raw] || raw
  if (id && visiblePanels.value.some((p) => p.id === id)) return id
  // 桌面端没有指定大类时默认落在个人资料;移动端留在列表
  return isMobile.value ? '' : 'profile'
})

const currentPanel = computed(() => visiblePanels.value.find((p) => p.id === activeId.value))
// 手机只挂载打开的分类，避免无关配置发请求；桌面切换时保留表单草稿。
const mountedPanels = computed(() => visiblePanels.value.filter((p) => p.id === activeId.value || (!isMobile.value && p.id !== 'storage')))

// 合并后旧链接仍指向某一小块:进页直接滚到那张卡(如老人模式的「设置」→ ?tab=display)
const TAB_ANCHOR = {
  daily: '#settings-daily',
  weather: '#settings-weather',
  theme: '#settings-theme',
  light: '#settings-light',
  display: '#settings-display',
}

// 进/出大类都从顶部开始看;带旧锚点参数时滚到对应卡片
watch(
  () => [activeId.value, String(route.query.tab || '')],
  () => {
    const anchor = TAB_ANCHOR[String(route.query.tab || '')]
    nextTick(() => {
      if (anchor) document.querySelector(anchor)?.scrollIntoView({ block: 'start' })
      else if (isMobile.value) window.scrollTo({ top: 0 })
    })
  },
  { immediate: true },
)

const openPanel = (id) => {
  if (!id) return
  router.push({ path: '/settings', query: { tab: id } })
}

const closePanel = () => router.push({ path: '/settings' })
</script>

<style scoped>
.settings-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.settings-mobile {
  padding: 14px clamp(12px, 3.6vw, 18px) 20px;
}
.settings-side {
  width: 190px;
  flex-shrink: 0;
  background: var(--color-card);
  border-radius: var(--radius);
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow), var(--shadow-inset);
  padding: 8px;
  position: sticky;
  top: 42px;
}
.settings-menu {
  border-right: none;
  background: transparent;
}
.settings-menu :deep(.el-menu-item-group + .el-menu-item-group) {
  margin-block-start: 8px;
  padding-block-start: 6px;
  border-block-start: 1px solid var(--color-border);
}
.settings-menu :deep(.el-menu-item-group__title) {
  font-size: 13px;
  font-weight: 650;
  color: var(--color-text);
  padding: 8px 12px 4px;
  line-height: 1.4;
}
.settings-menu :deep(.el-menu-item) {
  height: 40px;
  line-height: 40px;
}
.menu-icon {
  width: 26px;
  height: 26px;
  margin-right: 8px;
}
.settings-body {
  flex: 1;
  min-width: 0;
}

/* 移动端大类页顶部的返回条:一级列表 ↔ 单个大类 */
.panel-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px 4px 12px;
  background-color: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 14px;
  margin-bottom: 12px;
}
.panel-back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-height: 44px;
  padding: 0 6px;
  margin-inline-start: auto;
  background: transparent;
  border: 0;
  font: inherit;
  font-size: 13px;
  color: var(--color-text);
  cursor: pointer;
  flex-shrink: 0;
}
.panel-title {
  min-width: 0;
  overflow-wrap: anywhere;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text);
}
</style>
