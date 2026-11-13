<!-- 设置页:桌面端左侧分组导航 + 右侧配置;移动端先给分组列表,点进单个大类(带返回)。
     分类合并口径:每日内容+天气 = 首页内容,个性化+显示模式 = 外观与显示(旧 ?tab= 仍可用,见 TAB_ALIAS) -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('settings.title') }]" />

    <div class="settings-layout">
      <!-- 桌面端:分组侧栏 -->
      <aside v-if="!isMobile" class="settings-side">
        <el-menu :default-active="activeId" class="settings-menu" @select="openPanel">
          <el-menu-item-group v-for="g in groups" :key="g.key" :title="$t(g.labelKey)">
            <el-menu-item v-for="p in g.items" :key="p.id" :index="p.id">
              <span class="menu-icon">{{ p.icon }}</span
              >{{ $t(p.labelKey) }}
            </el-menu-item>
          </el-menu-item-group>
        </el-menu>
      </aside>

      <!-- 移动端:分组列表 -->
      <div v-if="isMobile && !activeId" class="settings-index">
        <div v-for="g in groups" :key="g.key" class="settings-group">
          <div class="group-label">{{ $t(g.labelKey) }}</div>
          <div class="group-card">
            <div v-for="p in g.items" :key="p.id" v-a11y-click class="group-row" @click="openPanel(p.id)">
              <span class="row-icon">{{ p.icon }}</span>
              <span class="row-text">{{ $t(p.labelKey) }}</span>
              <el-icon class="row-arrow"><ArrowRight /></el-icon>
            </div>
          </div>
        </div>
      </div>

      <!-- 配置区:移动端单大类带返回条;桌面端全部 v-show 保留状态 -->
      <div v-else class="settings-body">
        <div v-if="isMobile" class="panel-bar">
          <div v-a11y-click class="panel-back" @click="closePanel">
            <el-icon><ArrowLeft /></el-icon>
            <span>{{ $t('settings.allSettings') }}</span>
          </div>
          <span class="panel-title">{{ currentPanel ? $t(currentPanel.labelKey) : '' }}</span>
        </div>

        <div v-show="activeId === 'profile'"><ProfileSettings /></div>
        <div v-show="activeId === 'family'"><FamilySettings /></div>
        <div v-show="activeId === 'ai'"><AiSettings /></div>
        <div v-show="activeId === 'pointsRule'"><PointsRuleSettings /></div>
        <!-- 首页内容:每日内容 + 天气(天气仅家长可配) -->
        <div v-show="activeId === 'content'">
          <div id="settings-daily"><DailySettings /></div>
          <div v-if="userStore.hasPerm('family:manage')" id="settings-weather"><WeatherSettings /></div>
        </div>
        <div v-show="activeId === 'media'"><MediaSettings /></div>
        <template v-if="activeId === 'storage'"><StorageView /></template>
        <!-- 外观与显示:光照/主题 + 显示模式 + 屏保(仅手机端) -->
        <div v-show="activeId === 'appearance'">
          <div id="settings-light"><LightSettings /></div>
          <div id="settings-display"><ModeSettings /></div>
          <div v-if="isMobile" id="settings-screensaver"><ScreensaverSettings /></div>
        </div>
        <div v-show="activeId === 'client'"><AppSettings /></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useDevice } from '@/composables/useDevice'
import Breadcrumb from '@/components/Breadcrumb.vue'
import StorageView from '@/views/storage/Storage.vue'
import ProfileSettings from './settings/ProfileSettings.vue'
import FamilySettings from './settings/FamilySettings.vue'
import AiSettings from './settings/AiSettings.vue'
import PointsRuleSettings from './settings/PointsRuleSettings.vue'
import DailySettings from './settings/DailySettings.vue'
import WeatherSettings from './settings/WeatherSettings.vue'
import MediaSettings from './settings/MediaSettings.vue'
import LightSettings from './settings/LightSettings.vue'
import ModeSettings from './settings/ModeSettings.vue'
import ScreensaverSettings from './settings/ScreensaverSettings.vue'
import AppSettings from './settings/AppSettings.vue'
import { isNativeApp } from '@/utils/platform'

const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const { isMobile } = useDevice()

// 大类清单:移动端列表与桌面侧栏共用这一份;perm/nativeOnly 决定可见性
const PANELS = [
  { id: 'profile', icon: '👤', labelKey: 'settings.cat.profile' },
  { id: 'family', icon: '🏠', labelKey: 'settings.cat.family' },
  { id: 'pointsRule', icon: '🏅', labelKey: 'settings.cat.pointsRule', perm: 'points:manage' },
  { id: 'ai', icon: '🤖', labelKey: 'settings.cat.ai', perm: 'family:manage' },
  { id: 'content', icon: '📅', labelKey: 'settings.cat.content' },
  { id: 'media', icon: '🎬', labelKey: 'settings.cat.media' },
  { id: 'storage', icon: '🗄️', labelKey: 'settings.cat.storage' },
  { id: 'appearance', icon: '🎨', labelKey: 'settings.cat.appearance' },
  { id: 'client', icon: '📱', labelKey: 'settings.cat.client', nativeOnly: true },
]

// 分组顺序:账号 → 家庭 → 内容与媒体 → 外观与设备
const GROUPS = [
  { key: 'account', labelKey: 'settings.group.account', ids: ['profile'] },
  { key: 'family', labelKey: 'settings.group.family', ids: ['family', 'pointsRule', 'ai'] },
  { key: 'content', labelKey: 'settings.group.content', ids: ['content', 'media', 'storage'] },
  { key: 'device', labelKey: 'settings.group.device', ids: ['appearance', 'client'] },
]

// 旧 ?tab= 别名:合并后落地到新大类,旧链接(放映厅/相册/日程等页面跳转)不失效
const TAB_ALIAS = { daily: 'content', weather: 'content', light: 'appearance', display: 'appearance' }

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

// 合并后旧链接仍指向某一小块:进页直接滚到那张卡(如老人模式的「设置」→ ?tab=display)
const TAB_ANCHOR = { daily: '#settings-daily', weather: '#settings-weather', light: '#settings-light', display: '#settings-display' }

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
.settings-menu :deep(.el-menu-item-group__title) {
  font-size: 12px;
  color: var(--color-text-secondary);
  padding: 8px 12px 4px;
  line-height: 1.4;
}
.settings-menu :deep(.el-menu-item) {
  height: 40px;
  line-height: 40px;
}
.menu-icon {
  margin-right: 8px;
}
.settings-body {
  flex: 1;
  min-width: 0;
}

/* 移动端分组列表:与「我的」页的行式入口同款 */
.settings-group + .settings-group {
  margin-top: 16px;
}
.group-label {
  font-size: 12.5px;
  color: var(--color-text-secondary);
  padding: 0 4px 6px;
}
.group-card {
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  box-shadow: var(--shadow), var(--shadow-inset);
  overflow: hidden;
}
.group-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px;
  cursor: pointer;
  font-size: 15px;
  color: var(--color-text);
}
.group-row + .group-row {
  border-top: 1px solid var(--color-border);
}
.group-row:active {
  background: var(--color-card-2);
}
.row-icon {
  width: 20px;
  text-align: center;
}
.row-text {
  flex: 1;
  min-width: 0;
}
.row-arrow {
  color: var(--color-text-secondary);
}

/* 移动端大类页顶部的返回条:一级列表 ↔ 单个大类 */
.panel-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.panel-back {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 14px;
  color: var(--color-accent);
  cursor: pointer;
  flex-shrink: 0;
}
.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
}
</style>
