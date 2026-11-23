<template>
  <div class="window-shell" :class="{ 'has-rail': !compact, 'rail-collapsed': collapsed && !compact }">
    <aside v-if="!compact" class="window-rail">
      <router-link class="window-brand" to="/home" :aria-label="app.familyName || 'ihomy'">
        <img :src="app.family?.coverImage || appIcon.image" alt="" /><span>{{ app.familyName || 'ihomy' }}</span>
      </router-link>
      <LastDoingCard v-if="showDockCard" variant="dock" :compact="collapsed" />
      <nav :aria-label="$t('windowHome.directory')">
        <router-link class="rail-link" to="/home" :title="$t('nav.home')"
          ><el-icon><House /></el-icon><span>{{ $t('nav.home') }}</span></router-link
        >
        <button class="rail-link rail-directory" :title="$t('windowHome.directory')" @click="directory = true">
          <el-icon><Grid /></el-icon><span>{{ $t('windowHome.directory') }}</span>
        </button>
        <div class="rail-rule"></div>
        <div v-if="!collapsed" class="rail-section">{{ $t('windowHome.favorites') }}</div>
        <router-link v-for="item in nav.favorites.value" :key="item.code" class="rail-link" :to="item.path" :title="item.label">
          <el-icon><component :is="item.code === 'assistant' ? MagicStick : iconComp(item.icon || item.code)" /></el-icon
          ><span>{{ item.label }}</span>
        </router-link>
      </nav>
      <div class="rail-foot">
        <router-link v-if="user.isLoggedIn && !child" class="rail-link" to="/notification" :title="$t('nav.notifications')"
          ><el-icon><Bell /></el-icon><span>{{ $t('nav.notifications') }}</span></router-link
        >
        <router-link
          v-if="!child"
          class="rail-link"
          :to="user.isLoggedIn ? '/settings' : '/login'"
          :title="$t(user.isLoggedIn ? 'nav.settings' : 'home.loginRegister')"
          ><el-icon><User /></el-icon><span>{{ user.userInfo?.nickname || $t('home.loginRegister') }}</span></router-link
        >
        <button class="rail-link" :title="$t(collapsed ? 'windowHome.expand' : 'windowHome.collapse')" @click="collapsed = !collapsed">
          <el-icon><component :is="collapsed ? Expand : Fold" /></el-icon><span>{{ $t('windowHome.collapse') }}</span>
        </button>
      </div>
    </aside>
    <div v-if="compact && (!isMobile || isHome) && !game" class="window-top">
      <router-link class="window-brand" to="/home"
        ><img :src="app.family?.coverImage || appIcon.image" alt="" /><span>{{ app.familyName || 'ihomy' }}</span></router-link
      >
      <button class="directory-button" @click="directory = true">
        <el-icon><Grid /></el-icon>{{ $t('windowHome.directory') }}
      </button>
    </div>
    <main class="window-content">
      <MobileLayout window-host :key="String(user.userInfo?.id) + ':' + String(user.userInfo?.familyId)">
        <template #header-right>
          <button v-if="isMobile && !game" class="directory-button" @click="directory = true">
            <el-icon><Grid /></el-icon>{{ $t('windowHome.directory') }}
          </button>
        </template>
      </MobileLayout>
    </main>
    <WindowDirectory v-if="directory || directoryMounted" v-model="directory" :nav="nav" :narrow="compact" @select="openEntry" />
  </div>
</template>

<script setup>
import { computed, defineAsyncComponent, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Grid, House, MagicStick, Expand, Fold, User, Bell } from '@element-plus/icons-vue'
import MobileLayout from '@/layouts/MobileLayout.vue'
import { useDevice } from '@/composables/useDevice'
import { useDisplayMode } from '@/composables/useDisplayMode'
import { useWindowDirectory } from '@/composables/useWindowDirectory'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { iconComp } from '@/utils/navModules'
import { isMobileGamePath, isMobileHomePath } from '@/utils/mobileNav'
import { useWindowPanel } from '@/utils/windowPanel'
import { useAppIconStore } from '@/stores/appIcon'
import { lastDoingMode } from '@/utils/lastDoing'
import LastDoingCard from '@/components/lastdoing/LastDoingCard.vue'
const WindowDirectory = defineAsyncComponent(() => import('./WindowDirectory.vue'))
const route = useRoute()
const router = useRouter()
const app = useAppStore()
const appIcon = useAppIconStore()
const user = useUserStore()
const nav = useWindowDirectory()
const { isChild: child } = useDisplayMode()
const { isMobile, isTablet, isWide } = useDevice()
const compact = computed(() => isMobile.value || !isWide.value)
const showDockCard = computed(
  () => user.isLoggedIn && lastDoingMode({ isMobile: isMobile.value, isTablet: isTablet.value, isWide: isWide.value }) === 'dock',
)
const isHome = computed(() => isMobileHomePath(route.path))
const game = computed(() => isMobile.value && isMobileGamePath(route.path))
const collapsed = ref(false)
const directory = useWindowPanel('directory')
const navigation = router.options.history
const directoryMounted = ref(false)
// A same-URL entry lets the browser's Back close the overlay without re-running page hooks.
// Choosing a destination replaces this entry. Its Back destination is the original page.
const stopListening = navigation.listen(() => {
  directory.value = !!navigation.state?.windowDirectory
})
watch(
  directory,
  (open) => {
    if (open) {
      directoryMounted.value = true
      if (!navigation.state?.windowDirectory) navigation.push(route.fullPath, { windowDirectory: true })
    } else if (navigation.state?.windowDirectory && navigation.location === route.fullPath) {
      router.back()
    }
  },
  { flush: 'sync' },
)
if (navigation.state?.windowDirectory) directory.value = true
async function openEntry(path) {
  const to = router.resolve(path)
  if (to.fullPath === route.fullPath) {
    directory.value = false
    return
  }
  await router.replace({ path: to.path, query: to.query, hash: to.hash, state: { windowDirectory: false } })
}
onBeforeUnmount(() => {
  stopListening()
  directory.value = false
})
watch(
  () => route.fullPath,
  () => {
    directory.value = !!navigation.state?.windowDirectory
  },
)
watch([() => user.userInfo?.id, () => user.userInfo?.familyId, child], () => {
  directory.value = false
})
</script>

<style scoped>
.window-shell {
  min-height: 100dvh;
  color: var(--color-text);
  background: var(--color-bg);
  --rail-width: 218px;
}
.window-shell.rail-collapsed {
  --rail-width: 76px;
}
.has-rail {
  display: grid;
  grid-template-columns: var(--rail-width) minmax(0, 1fr);
}
.window-rail {
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  align-self: start;
  height: 100dvh;
  overflow: auto;
  box-sizing: border-box;
  padding: 24px 12px 12px;
  background: var(--color-card);
  border-right: 1px solid var(--color-border);
}
.window-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  color: var(--color-text);
  font-size: 20px;
  font-weight: 700;
  text-decoration: none;
}
.window-brand img {
  width: 38px;
  height: 38px;
  object-fit: cover;
  border-radius: 10px;
}
.window-brand span,
.rail-link span {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.window-rail .window-brand {
  padding: 0 6px 24px;
}
.rail-link,
.directory-button {
  display: flex;
  align-items: center;
  gap: 12px;
  border: 0;
  border-radius: 10px;
  cursor: pointer;
  font: inherit;
  text-decoration: none;
}
.rail-link {
  width: 100%;
  min-height: 46px;
  padding: 8px 14px;
  box-sizing: border-box;
  text-align: left;
  background: transparent;
  color: var(--color-text);
}
.rail-link .el-icon {
  flex: none;
  font-size: 19px;
  color: var(--color-brand);
}
.rail-link:hover,
.rail-link:focus-visible {
  background: var(--color-card-2);
}
.rail-link.router-link-exact-active,
.directory-button {
  background: var(--color-brand);
  color: var(--color-brand-text);
}
.rail-link.router-link-exact-active .el-icon {
  color: inherit;
}
.rail-rule {
  height: 1px;
  background: var(--color-border);
  margin: 18px 4px;
}
.rail-section {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-secondary);
  padding: 0 14px 6px;
}
.rail-foot {
  margin-top: auto;
  padding-top: 24px;
}
.rail-collapsed .window-rail .window-brand span,
.rail-collapsed .rail-link span:not(.el-icon) {
  display: none;
}
.rail-collapsed .rail-link {
  justify-content: center;
}
.rail-collapsed .rail-directory {
  flex-direction: column;
  gap: 2px;
  padding: 5px 0;
}
.rail-collapsed .rail-link.rail-directory span {
  display: inline;
  white-space: normal;
  font-size: 12px;
}
.window-content {
  min-width: 0;
}
.window-top {
  position: sticky;
  top: 0;
  z-index: var(--z-nav);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  min-height: 54px;
  padding: calc(6px + env(safe-area-inset-top, 0px)) 16px 6px;
  box-sizing: border-box;
  background: var(--color-card);
  border-bottom: 1px solid var(--color-border);
}
.window-top .window-brand {
  font-size: 17px;
}
.window-top .window-brand img {
  width: 32px;
  height: 32px;
}
.directory-button {
  flex: none;
  min-height: 40px;
  padding: 6px 12px;
  font-size: 14px;
  font-weight: 600;
  gap: 7px;
}
:global(.fp-edit-focus) .window-rail,
:global(.fp-edit-focus) .window-top {
  display: none;
}
:global(.fp-edit-focus) .has-rail {
  display: block;
}
</style>
