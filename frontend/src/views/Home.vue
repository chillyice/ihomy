<template>
  <div class="home-page">
    <!-- 软件封面：文字 + 图片 -->
    <section class="cover">
      <img v-if="cover.coverImage" :src="cover.coverImage" class="cover-img" alt="封面" />
      <div class="cover-overlay">
        <h1>{{ cover.coverText || '欢迎来到我们的家庭空间' }}</h1>
        <p v-if="cover.coverSubtitle">{{ cover.coverSubtitle }}</p>
      </div>
    </section>

    <!-- 动态模块化首页：按 position 分区渲染 -->
    <div class="page">
      <div v-for="pos in positions" :key="pos" :class="['module-zone', 'zone-' + pos]">
        <div class="module-grid">
          <div
            v-for="m in modulesByPosition(pos)"
            :key="m.code"
            class="module-card"
            @click="goModule(m)"
          >
            <div class="module-icon">{{ iconFor(m.icon) }}</div>
            <div class="module-title">{{ m.title }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 用户栏 -->
    <div class="page user-bar">
      <span>{{ userStore.userInfo?.nickname }}</span>
      <el-button v-if="userStore.isOwner" text @click="$router.push('/member')">成员管理</el-button>
      <el-button text @click="onLogout">退出</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { homeApi } from '@/api'

const router = useRouter()
const userStore = useUserStore()

const positions = ['top', 'left', 'right', 'bottom']
const modules = ref([])
const cover = ref({})

const iconMap = {
  'icon-blog': '📝',
  'icon-diary': '📔',
  'icon-member': '👥',
  'icon-cover': '🖼️',
}
const iconFor = (icon) => iconMap[icon] || '⭐'

const modulesByPosition = (pos) => modules.value.filter((m) => m.position === pos)

const goModule = (m) => router.push(m.path)

const onLogout = () => {
  userStore.logout()
  router.push('/login')
}

onMounted(async () => {
  try {
    modules.value = await homeApi.getModules()
  } catch (e) {
    // 忽略，模块加载失败不阻断
  }
})
</script>

<style scoped>
.cover {
  position: relative;
  height: 220px;
  background: linear-gradient(135deg, #1F3A5F, #2E74B5);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.cover-img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; opacity: 0.6; }
.cover-overlay { position: relative; text-align: center; color: #fff; padding: 0 16px; }
.cover-overlay h1 { font-size: 28px; font-weight: 700; text-shadow: 0 2px 8px rgba(0,0,0,0.4); }
.cover-overlay p { margin-top: 8px; opacity: 0.9; }

.module-zone { margin-bottom: 12px; }
.module-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
}
.module-card {
  background: var(--color-card);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 20px 12px;
  text-align: center;
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}
.module-card:hover { transform: translateY(-3px); box-shadow: 0 6px 18px rgba(31,58,95,0.15); }
.module-icon { font-size: 30px; }
.module-title { margin-top: 8px; font-size: 14px; color: var(--color-text); }

.user-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  justify-content: flex-end;
  color: var(--color-text-secondary);
  font-size: 14px;
}

@media (max-width: 768px) {
  .cover { height: 170px; }
  .cover-overlay h1 { font-size: 22px; }
  .module-grid { grid-template-columns: repeat(auto-fill, minmax(110px, 1fr)); }
}
</style>
