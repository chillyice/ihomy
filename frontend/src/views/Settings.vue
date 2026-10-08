<!-- 设置页:左侧设置大类导航(个人资料/家庭设置/每日内容),点击大类切换右侧对应小类配置 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('settings.title') }]" />

    <div class="settings-layout">
      <aside class="settings-side">
        <el-menu :default-active="active" @select="(i) => (active = i)" class="settings-menu">
          <el-menu-item index="profile"> <span class="menu-icon">👤</span>{{ $t('settings.cat.profile') }} </el-menu-item>
          <el-menu-item index="family"> <span class="menu-icon">🏠</span>{{ $t('settings.cat.family') }} </el-menu-item>
          <el-menu-item v-if="userStore.hasPerm('family:manage')" index="ai">
            <span class="menu-icon">🤖</span>{{ $t('settings.cat.ai') }}
          </el-menu-item>
          <el-menu-item v-if="userStore.hasPerm('points:manage')" index="pointsRule">
            <span class="menu-icon">🏅</span>{{ $t('settings.cat.pointsRule') }}
          </el-menu-item>
          <el-menu-item index="daily"> <span class="menu-icon">📅</span>{{ $t('settings.cat.daily') }} </el-menu-item>
          <el-menu-item v-if="userStore.hasPerm('family:manage')" index="weather">
            <span class="menu-icon">🌤️</span>{{ $t('settings.cat.weather') }}
          </el-menu-item>
          <el-menu-item index="media"> <span class="menu-icon">🎬</span>{{ $t('settings.cat.media') }} </el-menu-item>
          <el-menu-item index="storage"> <span class="menu-icon">🗄️</span>{{ $t('settings.cat.storage') }} </el-menu-item>
          <el-menu-item index="light"> <span class="menu-icon">🎨</span>{{ $t('settings.personalize') }} </el-menu-item>
        </el-menu>
      </aside>

      <div class="settings-body">
        <div v-show="active === 'profile'"><ProfileSettings /></div>
        <div v-show="active === 'family'"><FamilySettings /></div>
        <div v-show="active === 'ai'"><AiSettings /></div>
        <div v-show="active === 'pointsRule'"><PointsRuleSettings /></div>
        <div v-show="active === 'daily'"><DailySettings /></div>
        <div v-show="active === 'weather'"><WeatherSettings /></div>
        <template v-if="active === 'storage'"><StorageView /></template>
        <div v-show="active === 'media'"><MediaSettings /></div>
        <div v-show="active === 'light'"><LightSettings /></div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
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

const userStore = useUserStore()
const route = useRoute()

// 当前选中设置大类;支持 ?tab= 跳转(从导航栏头像下拉"个人资料"进入时切到 profile)
const active = ref(route.query.tab || 'profile')
watch(
  () => route.query.tab,
  (tab) => {
    if (tab) active.value = tab
  },
)
</script>

<style scoped>
.settings-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.settings-side {
  width: 180px;
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
.menu-icon {
  margin-right: 8px;
}
.settings-body {
  flex: 1;
  min-width: 0;
}
@media (max-width: 900px) {
  .settings-layout {
    flex-direction: column;
  }
  .settings-side {
    width: 100%;
  }
  .settings-menu {
    display: flex;
  }
  .settings-menu .el-menu-item {
    flex: 1;
    justify-content: center;
  }
}

@media (max-width: 768px) {
  .settings-layout {
    flex-direction: column;
  }
  .settings-side {
    width: 100% !important;
    position: static !important;
    margin-bottom: 12px;
  }
  .settings-side .el-menu {
    display: flex;
    flex-direction: row;
    overflow-x: auto;
  }
  .settings-side .el-menu-item {
    white-space: nowrap;
    flex-shrink: 0;
  }
  .form-row {
    flex-direction: column;
    gap: 0;
  }
}
</style>
