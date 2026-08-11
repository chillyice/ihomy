<!-- 全局顶栏:品牌/家庭切换/模块导航/通知铃铛/用户菜单,登录与访客展示不同入口 -->
<template>
  <header class="app-header">
    <div class="header-inner">
      <div class="brand" @click="onBrandClick">
        <span class="brand-name">{{ userStore.isOps ? $t('nav.ops') : (appStore.familyName || 'ihomy') }}</span>
        <el-dropdown v-if="userStore.isLoggedIn && families.length > 1" trigger="click" class="family-switch" @command="onSwitchFamily">
          <span class="family-switch-trigger">
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="f in families" :key="f.familyId" :command="f.familyId" :disabled="f.isCurrent">
                {{ f.name }}<span v-if="f.isCurrent" class="family-current">（当前）</span>
                <span v-if="f.isDefault" class="family-current">默认</span>
                <span v-if="f.role" class="family-role">{{ f.role }}</span>
              </el-dropdown-item>
              <el-dropdown-item divided command="set-default">将当前家庭设为默认</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <!-- 首页模块导航:普通用户可见;超过 6 项时前 5 项平铺,其余收进"更多"下拉 -->
      <nav v-if="!userStore.isOps" class="nav-modules">
        <router-link
          v-for="m in navPrimary"
          :key="m.code"
          :to="m.path"
          class="nav-item"
        >
          {{ m.title }}
        </router-link>
        <el-dropdown v-if="navSecondary.length" trigger="hover" @command="(p) => router.push(p)">
          <span class="nav-item nav-more">
            {{ $t('more.title') }}<el-icon class="more-arrow"><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="m in navSecondary" :key="m.code" :command="m.path">{{ m.title }}</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </nav>

      <div class="header-right">
        <!-- 语言切换:中/英,选择即持久化并全局生效 -->
        <el-dropdown trigger="click" @command="onLang">
          <div class="lang-trigger" :title="$t('nav.language')">
            <span>{{ locale === 'en' ? 'EN' : '中' }}</span>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="zh-CN" :disabled="locale === 'zh-CN'">中文</el-dropdown-item>
              <el-dropdown-item command="en" :disabled="locale === 'en'">English</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <!-- 主题切换:明暗模式 + 主题色,持久化到本地 -->
        <el-dropdown trigger="click" class="theme-trigger" @command="onThemeCommand">
          <div class="lang-trigger" :title="$t('nav.theme')">
            <el-icon><Sunny v-if="!theme.dark" /><Moon v-else /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item :command="{ dark: !theme.dark, theme: theme.theme }">
                {{ theme.dark ? $t('theme.light') : $t('theme.dark') }}
              </el-dropdown-item>
              <el-dropdown-item divided disabled style="opacity: .9">{{ $t('nav.themeColor') }}</el-dropdown-item>
              <el-dropdown-item v-for="t in THEMES" :key="t.key" :command="{ dark: theme.dark, theme: t.key }">
                <span class="theme-swatch" :style="{ background: t.accent }"></span>
                {{ $t('theme.presets.' + t.key) }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <el-popover v-if="userStore.isLoggedIn && !userStore.isOps" placement="bottom-end" :width="340" trigger="click" @show="loadNotifications">
          <template #reference>
            <el-badge :value="unreadCount" :hidden="!unreadCount" class="msg-badge">
              <el-icon class="msg-icon"><Bell /></el-icon>
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
                <div class="notify-time">{{ formatTime(n.createdAt) }}</div>
              </div>
            </div>
            <el-empty v-else :description="$t('nav.noNotification')" :image-size="60" />
          </div>
        </el-popover>

        <el-dropdown v-if="userStore.isLoggedIn" trigger="click" @command="onCommand">
          <div class="avatar-wrap">
            <el-avatar :size="32" :src="userStore.userInfo?.avatar">
              {{ (userStore.userInfo?.nickname || 'U').charAt(0) }}
            </el-avatar>
            <span class="avatar-name">{{ userStore.userInfo?.nickname }}</span>
          </div>
          <template #dropdown>
            <el-dropdown-menu v-if="userStore.isOps">
              <el-dropdown-item command="ops">{{ $t('nav.ops') }}</el-dropdown-item>
              <el-dropdown-item divided command="logout">{{ $t('nav.logout') }}</el-dropdown-item>
            </el-dropdown-menu>
            <el-dropdown-menu v-else>
              <el-dropdown-item command="profile">{{ $t('settings.profile') }}</el-dropdown-item>
              <el-dropdown-item command="settings">{{ $t('nav.settings') }}</el-dropdown-item>
              <el-dropdown-item divided command="logout">{{ $t('nav.logout') }}</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <div v-else class="auth-actions">
          <el-button text @click="$router.push('/login')">{{ $t('login.title') }}</el-button>
          <el-button type="primary" @click="$router.push('/login?register=1')">{{ $t('login.register') }}</el-button>
        </div>
      </div>
    </div>
  </header>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { notificationApi, authApi } from '@/api'
import { Bell, ArrowDown, Sunny, Moon } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { applyLocale } from '@/i18n'
import { applyTheme, initTheme, THEMES } from '@/theme'

const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()
const { locale, t } = useI18n()
const theme = ref({ dark: false, theme: 'ocean' })
const unreadCount = ref(0)
const notifications = ref([])
const families = ref([])

// 主题命令:明暗切换或选色盘,applyTheme 内部持久化
const onThemeCommand = (cmd) => {
  theme.value = applyTheme(cmd)
}

// 语言切换:持久化偏好并同步 document.lang
const onLang = (lang) => {
  applyLocale(lang)
  ElMessage.success(lang === 'en' ? 'Language switched' : '语言已切换')
}

// 拉取我的全部家庭列表,供切换下拉使用
const loadFamilies = async () => {
  if (!userStore.isLoggedIn) return
  try {
    families.value = await authApi.families()
  } catch (e) {
    // 忽略
  }
}

// 家庭切换:set-default 把当前家庭设为默认;普通切换则换 token 后刷新首页数据回首页
const onSwitchFamily = async (command) => {
  try {
    if (command === 'set-default') {
      const cur = families.value.find((f) => f.isCurrent)
      if (!cur) return
      await userStore.switchFamily(cur.familyId, true)
      ElMessage.success(locale.value === 'en' ? 'Set as default family' : '已设为默认家庭')
      await loadFamilies()
      return
    }
    await userStore.switchFamily(command)
    appStore.reset()
    await appStore.init(true)
    ElMessage.success(locale.value === 'en' ? 'Family switched' : '已切换家庭')
    router.push('/')
  } catch (e) {
    // 忽略
  }
}

// 顶部导航只展示 position 为 top/left 的模块;超过 5 项时前 5 项平铺,其余收进"更多"下拉
const navAll = computed(() =>
  appStore.modules.filter((m) => m.position === 'top' || m.position === 'left'),
)
const navPrimary = computed(() => navAll.value.slice(0, 5))
const navSecondary = computed(() => navAll.value.slice(5))

const loadUnread = async () => {
  if (!userStore.isLoggedIn) return
  try {
    unreadCount.value = await notificationApi.unreadCount()
  } catch (e) {
    // 忽略
  }
}

// 品牌点击:运维用户回运维页,普通用户回首页
const onBrandClick = () => router.push(userStore.isOps ? '/ops' : '/')

// 打开铃铛面板时拉取通知列表并刷新未读数
const loadNotifications = async () => {
  try {
    notifications.value = await notificationApi.list()
    await loadUnread()
  } catch (e) {
    // 忽略
  }
}

const markAllRead = async () => {
  await notificationApi.markAllRead()
  notifications.value = notifications.value.map((n) => ({ ...n, isRead: 1 }))
  unreadCount.value = 0
}

// 点击通知:未读先标记已读,再按内容类型跳转到对应页面
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

const notifyType = (type) => (type === 'reply' ? t('notify.typeReply') : type === 'system' ? t('notify.typeSystem') : t('notify.typeComment'))

// 相对时间展示:分钟/小时级距,超过一天显示具体日期
const formatTime = (d) => {
  if (!d) return ''
  const date = new Date(d)
  const now = Date.now()
  const diff = now - date.getTime()
  if (diff < 3600000) return t('time.minuteAgo', { n: Math.max(1, Math.floor(diff / 60000)) })
  if (diff < 86400000) return t('time.hourAgo', { n: Math.floor(diff / 3600000) })
  return date.toLocaleDateString(locale.value === 'en' ? 'en-US' : 'zh-CN')
}

// 用户下拉菜单命令分发(个人中心/设置/成员管理/运维/登出)
const onCommand = (cmd) => {
  if (cmd === 'logout') {
    // 登出后保持当前 URL(刷新为访客视图),不强制回首页
    userStore.logout()
    location.reload()
  } else if (cmd === 'member') {
    router.push('/member')
  } else if (cmd === 'ops') {
    router.push('/ops')
  } else if (cmd === 'profile' || cmd === 'settings') {
    router.push('/settings')
  }
}

onMounted(() => {
  theme.value = { dark: initTheme().dark, theme: initTheme().theme }
  loadUnread()
  loadFamilies()
  watch(() => userStore.isLoggedIn, () => { loadUnread(); loadFamilies() })
})
</script>

<style scoped>
.app-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: var(--color-card);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--color-border, rgba(31, 58, 95, 0.08));
  box-shadow: var(--shadow);
}
.header-inner {
  max-width: 1280px;
  margin: 0 auto;
  padding: 0 24px;
  height: 64px;
  display: flex;
  align-items: center;
  gap: 32px;
}
.brand { cursor: pointer; flex-shrink: 0; display: flex; align-items: center; gap: 4px; }
.brand-name {
  font-size: 22px;
  font-weight: 700;
  color: var(--color-primary);
  letter-spacing: 0.5px;
}
.family-switch-trigger {
  display: inline-flex;
  align-items: center;
  color: var(--color-text-secondary);
  cursor: pointer;
  font-size: 12px;
}
.family-switch-trigger:hover { color: var(--color-accent); }
.family-current { color: var(--color-text-secondary); font-size: 12px; }
.family-role { margin-left: 6px; font-size: 11px; color: var(--color-accent); }
.nav-modules {
  display: flex;
  align-items: center;
  gap: 24px;
  flex: 1;
  overflow-x: auto;
}
.nav-item {
  font-size: 15px;
  color: var(--color-text);
  padding: 6px 4px;
  border-bottom: 2px solid transparent;
  transition: color 0.15s, border-color 0.15s;
  white-space: nowrap;
}
.nav-item:hover { color: var(--color-accent); }
.nav-item.router-link-active {
  color: var(--color-accent);
  border-bottom-color: var(--color-accent);
}
.nav-more { display: inline-flex; align-items: center; gap: 2px; cursor: pointer; }
.more-arrow { font-size: 12px; }
.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}
.msg-icon {
  font-size: 20px;
  color: var(--color-text-secondary);
  cursor: pointer;
}
.msg-icon:hover { color: var(--color-accent); }
.avatar-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 20px;
  transition: background 0.15s;
}
.avatar-wrap:hover { background: rgba(31, 58, 95, 0.05); }
.avatar-name {
  font-size: 14px;
  color: var(--color-text);
}
.auth-actions { display: flex; gap: 8px; }
.lang-trigger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: var(--color-text-secondary);
  font-size: 13px;
  padding: 4px 6px;
  border-radius: 6px;
}
.lang-trigger:hover { color: var(--color-accent); background: rgba(31, 58, 95, 0.05); }
.theme-swatch {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  margin-right: 6px;
  vertical-align: -1px;
}
.notify-panel { display: flex; flex-direction: column; gap: 8px; }
.notify-head { display: flex; justify-content: space-between; align-items: center; font-weight: 600; }
.notify-list { max-height: 320px; overflow-y: auto; display: flex; flex-direction: column; }
.notify-item {
  padding: 10px 8px;
  border-radius: 8px;
  cursor: pointer;
  border-bottom: 1px solid rgba(31, 58, 95, 0.06);
}
.notify-item:hover { background: rgba(46, 116, 181, 0.06); }
.notify-item.unread { background: rgba(46, 116, 181, 0.05); }
.notify-type { font-size: 11px; color: var(--color-accent); margin-bottom: 2px; }
.notify-content { font-size: 13px; color: var(--color-text); }
.notify-time { font-size: 11px; color: var(--color-text-secondary); margin-top: 2px; }

@media (max-width: 768px) {
  .app-header { padding-top: env(safe-area-inset-top); }
  .header-inner { padding: 0 12px; gap: 12px; height: 56px; }
  .brand-name { font-size: 18px; }
  .nav-modules { gap: 14px; }
  .nav-item { font-size: 13px; }
  .avatar-name { display: none; }
}
</style>