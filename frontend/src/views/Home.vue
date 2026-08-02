<template>
  <div class="home-page">
    <AppHeader :modules="modules" :family-name="family?.name" />

    <section class="cover">
      <img v-if="family?.coverImage" :src="family.coverImage" class="cover-img" alt="封面" />
      <div class="cover-overlay">
        <h1>{{ family?.coverText || '欢迎来到我们的家庭空间' }}</h1>
        <p v-if="family?.coverSubtitle">{{ family.coverSubtitle }}</p>
      </div>
      <div v-if="userStore.isGuest" class="guest-hint">
        您正在以访客身份浏览公开内容
        <el-button type="primary" size="small" @click="$router.push('/login')">登录查看更多</el-button>
      </div>
    </section>

    <main class="main-content">
      <AlbumCarousel :photos="photos" />

      <div class="bottom-modules">
        <div
          v-for="m in bottomModules"
          :key="m.code"
          class="module-card"
          @click="goModule(m)"
        >
          <div class="module-icon">{{ iconFor(m.icon) }}</div>
          <div class="module-title">{{ m.title }}</div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { publicApi, homeApi } from '@/api'
import AppHeader from '@/components/AppHeader.vue'
import AlbumCarousel from '@/components/AlbumCarousel.vue'

const router = useRouter()
const userStore = useUserStore()

const modules = ref([])
const photos = ref([])
const family = ref({})

const bottomModules = computed(() => modules.value.filter(m => m.position === 'right' || m.position === 'bottom'))

const iconMap = {
  'icon-blog': '📝',
  'icon-diary': '📔',
  'icon-album': '📷',
  'icon-member': '👥',
  'icon-cover': '🖼️',
  'icon-study': '📚',
  'icon-toolbox': '🧰',
}
const iconFor = (icon) => iconMap[icon] || '⭐'

const goModule = (m) => router.push(m.path)

const loadPublicHome = async () => {
  const data = await publicApi.getHome()
  family.value = data.family || {}
  modules.value = data.modules || []
  photos.value = data.photos || []
}

const loadUserHome = async () => {
  try {
    const dash = await homeApi.getDashboard()
    if (dash?.modules) modules.value = dash.modules
    if (dash?.user) family.value = { name: 'ihomy' }
  } catch (e) {
    await loadPublicHome()
    return
  }
  // 已登录用户也加载公开照片用于首页轮播
  try {
    const pub = await publicApi.getHome()
    photos.value = pub.photos || []
    if (pub.family) family.value = pub.family
  } catch (e) {
    // 忽略
  }
}

onMounted(() => {
  if (userStore.isLoggedIn) {
    loadUserHome()
  } else {
    loadPublicHome()
  }
})
</script>

<style scoped>
.home-page { min-height: 100vh; background: var(--color-bg); }

.cover {
  position: relative;
  height: 280px;
  background: linear-gradient(135deg, #1F3A5F, #2E74B5);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.cover-img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; opacity: 0.5; }
.cover-overlay { position: relative; text-align: center; color: #fff; padding: 0 16px; z-index: 1; }
.cover-overlay h1 {
  font-size: 32px;
  font-weight: 700;
  text-shadow: 0 2px 12px rgba(0, 0, 0, 0.5);
  margin-bottom: 8px;
}
.cover-overlay p { font-size: 16px; opacity: 0.9; }
.guest-hint {
  position: absolute;
  bottom: 16px;
  right: 24px;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  padding: 8px 16px;
  border-radius: 20px;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 12px;
  z-index: 2;
}

.main-content {
  max-width: 1280px;
  margin: -40px auto 0;
  padding: 0 24px 32px;
  position: relative;
  z-index: 2;
}

.bottom-modules {
  margin-top: 24px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 16px;
}
.module-card {
  background: var(--color-card);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 24px 16px;
  text-align: center;
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}
.module-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 24px rgba(31, 58, 95, 0.15);
}
.module-icon { font-size: 36px; margin-bottom: 8px; }
.module-title { font-size: 15px; color: var(--color-text); font-weight: 500; }

@media (max-width: 768px) {
  .cover { height: 200px; }
  .cover-overlay h1 { font-size: 22px; }
  .cover-overlay p { font-size: 13px; }
  .guest-hint { right: 12px; bottom: 12px; font-size: 12px; padding: 6px 12px; }
  .main-content { padding: 0 12px 24px; margin-top: -24px; }
  .bottom-modules { grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); gap: 12px; }
  .module-card { padding: 16px 8px; }
  .module-icon { font-size: 28px; }
}
</style>
