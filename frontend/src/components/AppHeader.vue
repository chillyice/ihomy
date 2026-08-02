<template>
  <header class="app-header">
    <div class="header-inner">
      <div class="brand" @click="$router.push('/')">
        <span class="brand-name">{{ familyName || 'ihomy' }}</span>
      </div>

      <nav class="nav-modules">
        <router-link
          v-for="m in modules.filter(x => x.position === 'top' || x.position === 'left')"
          :key="m.code"
          :to="m.path"
          class="nav-item"
        >
          {{ m.title }}
        </router-link>
      </nav>

      <div class="header-right">
        <el-badge v-if="userStore.isLoggedIn" :value="unreadCount" :hidden="!unreadCount" class="msg-badge">
          <el-icon class="msg-icon" @click="$router.push('/notification')">
            <Bell />
          </el-icon>
        </el-badge>

        <el-dropdown v-if="userStore.isLoggedIn" trigger="click" @command="onCommand">
          <div class="avatar-wrap">
            <el-avatar :size="32" :src="userStore.userInfo?.avatar">
              {{ (userStore.userInfo?.nickname || 'U').charAt(0) }}
            </el-avatar>
            <span class="avatar-name">{{ userStore.userInfo?.nickname }}</span>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人中心</el-dropdown-item>
              <el-dropdown-item v-if="userStore.isOwner" command="member">成员管理</el-dropdown-item>
              <el-dropdown-item v-if="userStore.isOwner" command="homeConfig">首页配置</el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <div v-else class="auth-actions">
          <el-button text @click="$router.push('/login')">登录</el-button>
          <el-button type="primary" @click="$router.push('/login?register=1')">注册</el-button>
        </div>
      </div>
    </div>
  </header>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { Bell } from '@element-plus/icons-vue'

defineProps({
  modules: { type: Array, default: () => [] },
  familyName: { type: String, default: '' },
})

const router = useRouter()
const userStore = useUserStore()
const unreadCount = ref(0)

const onCommand = (cmd) => {
  if (cmd === 'logout') {
    userStore.logout()
    router.push('/')
  } else if (cmd === 'member') {
    router.push('/member')
  } else if (cmd === 'homeConfig') {
    router.push('/home/config')
  } else if (cmd === 'profile') {
    router.push('/profile')
  }
}
</script>

<style scoped>
.app-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid rgba(31, 58, 95, 0.08);
  box-shadow: 0 1px 8px rgba(31, 58, 95, 0.04);
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
.brand { cursor: pointer; flex-shrink: 0; }
.brand-name {
  font-size: 22px;
  font-weight: 700;
  color: var(--color-primary);
  letter-spacing: 0.5px;
}
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

@media (max-width: 768px) {
  .header-inner { padding: 0 12px; gap: 12px; height: 56px; }
  .brand-name { font-size: 18px; }
  .nav-modules { gap: 14px; }
  .nav-item { font-size: 13px; }
  .avatar-name { display: none; }
}
</style>
