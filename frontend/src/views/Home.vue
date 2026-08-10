<!-- 首页:封面区(家庭信息+统计) + 相册轮播 + 家人动态流 + 侧栏快捷入口/纪念日/最新博客 -->
<template>
  <div class="home-page">
    <section class="cover">
      <img v-if="family?.coverImage" :src="family.coverImage" class="cover-img" :alt="$t('home.coverAlt')" />
      <div class="cover-overlay">
        <h1>{{ family?.coverText || $t('home.welcome') }}</h1>
        <p v-if="family?.coverSubtitle" class="cover-sub">{{ family.coverSubtitle }}</p>
        <HomeStatsBar
          class="cover-stats"
          :member-count="memberCount"
          :feed-count="todayCount"
        />
      </div>
      <div v-if="userStore.isGuest" class="guest-hint">
        {{ $t('home.guestHint') }}
        <el-button type="primary" size="small" @click="$router.push('/login')">{{ $t('home.loginToView') }}</el-button>
      </div>
    </section>

    <!-- 今日纪念日横幅:命中成员生日/家庭纪念日时显示,庆祝动效 -->
    <div v-if="todayEvent" class="festival-banner" :class="todayEvent.type === 'birthday' ? 'is-birthday' : 'is-anniversary'">
      <span class="festival-emoji">{{ todayEvent.type === 'birthday' ? '🎂' : '🎉' }}</span>
      <span class="festival-text">{{ $t('home.todayEvent', { name: todayEvent.name, kind: $t(todayEvent.type === 'birthday' ? 'home.birthday' : 'home.anniversaryDay') }) }}</span>
      <span class="festival-confetti" aria-hidden="true"></span>
    </div>

    <main class="main-content">
      <div class="content-grid">
        <div class="content-main">
          <AlbumCarousel :photos="photos" />

          <div class="feed-section">
            <ActivityFeed :home-id="homeId" :hid="hid" @loaded="onFeedLoaded" />
          </div>
        </div>

        <aside class="content-side">
          <div class="side-card">
            <div class="side-title">{{ $t('home.quickLinks') }}</div>
            <div class="side-modules">
              <div
                v-for="m in sideModules"
                :key="m.code"
                class="side-module"
                @click="goModule(m)"
              >
                <div class="side-icon">{{ iconFor(m.icon) }}</div>
                <div class="side-name">{{ m.title }}</div>
              </div>
              <div
                v-if="allSideModules.length > SIDE_MODULE_LIMIT"
                class="side-module"
                @click="$router.push('/more')"
              >
                <div class="side-icon">🗂️</div>
                <div class="side-name">{{ $t('more.title') }}</div>
              </div>
            </div>
          </div>

          <div v-if="anniversaries.length" class="side-card">
            <div class="side-title">🎉 {{ $t('home.anniversaries') }}</div>
            <div
              v-for="a in anniversaries"
              :key="a.id"
              class="side-anni"
              @click="$router.push('/anniversary')"
            >
              <div class="side-anni-date">{{ $t('anniversary.dateFormat', { leap: a.isLeap && a.calendar === 'lunar' ? $t('anniversary.leap') : '', month: a.month, day: a.day }) }}</div>
              <div class="side-anni-info">
                <div class="side-anni-name">{{ a.name }}</div>
                <div class="side-anni-meta">{{ a.calendar === 'lunar' ? $t('anniversary.lunar') : $t('anniversary.solar') }}{{ a.userName ? ' · ' + a.userName : '' }}</div>
              </div>
            </div>
            <div class="side-anni-more" @click="$router.push('/anniversary')">{{ $t('home.viewAllAnniversaries') }} →</div>
          </div>

          <div v-if="latestBlogs.length" class="side-card">
            <div class="side-title">{{ $t('home.latestBlogs') }}</div>
            <div
              v-for="b in latestBlogs"
              :key="b.id"
              class="side-blog"
              @click="$router.push(`/blog/${b.id}`)"
            >
              <div class="side-blog-title">{{ b.title }}</div>
              <div class="side-blog-meta">{{ $t('home.views', { count: b.viewCount || 0 }) }}</div>
            </div>
          </div>
        </aside>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { publicApi, homeApi, blogApi, anniversaryApi } from '@/api'
import AlbumCarousel from '@/components/AlbumCarousel.vue'
import HomeStatsBar from '@/components/HomeStatsBar.vue'
import ActivityFeed from '@/components/ActivityFeed.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const modules = ref([])
const photos = ref([])
const family = ref({})
const todayCount = ref(0)
const memberCount = ref(0)
const todayEvent = ref(null)
const latestBlogs = ref([])
const anniversaries = ref([])

const SIDE_MODULE_LIMIT = 8
const ANNIVERSARY_SHOW = 5

// 侧栏候选模块:右侧/底部/左侧位置的模块都算;首页最多展示 8 个,超出进"更多功能"
const allSideModules = computed(() =>
  modules.value.filter(m => m.position === 'right' || m.position === 'bottom' || m.position === 'left')
)

const sideModules = computed(() => allSideModules.value.slice(0, SIDE_MODULE_LIMIT))

const iconMap = {
  'icon-blog': '📝',
  'icon-diary': '📔',
  'icon-album': '📷',
  'icon-member': '👥',
  'icon-cover': '🖼️',
  'icon-study': '📚',
  'icon-toolbox': '🧰',
  'icon-anniversary': '🎉',
  'icon-photo': '🌊',
  'icon-tree': '🌳',
  'icon-storage': '🗄️',
  'icon-item': '📦',
}
// 模块图标映射,未收录的图标兜底为星星
const iconFor = (icon) => iconMap[icon] || '⭐'

const goModule = (m) => router.push(m.path)

// 动态流加载完成回调,拿到今日动态数用于封面统计
const onFeedLoaded = (count) => {
  todayCount.value = count
}

// 从 URL 读取家庭参数:hid 为混淆分享 token,home_id 为旧版兼容参数
const homeId = computed(() => route.query.home_id || '')
const hid = computed(() => route.query.hid || '')

// 访客/指定家庭:只走公开接口,拿公开模块、照片与成员数
const loadPublicHome = async () => {
  const data = await publicApi.getHome(homeId.value || undefined, hid.value || undefined)
  family.value = data.family || {}
  modules.value = data.modules || []
  photos.value = data.photos || []
  memberCount.value = data.stats?.memberCount || 0
  todayEvent.value = data.stats?.todayEvent || null
}

// 登录用户首页:优先拉私有仪表盘;带家庭参数或接口失败时降级走公开数据
const loadUserHome = async () => {
  if (hid.value || homeId.value) {
    try {
      await loadPublicHome()
      return
    } catch (e) {
      // 忽略
    }
  }
  try {
    const dash = await homeApi.getDashboard()
    if (dash?.modules) modules.value = dash.modules
  } catch (e) {
    await loadPublicHome()
    return
  }
  try {
    const pub = await publicApi.getHome()
    photos.value = pub.photos || []
    if (pub.family) family.value = pub.family
    memberCount.value = pub.stats?.memberCount || 0
    todayEvent.value = pub.stats?.todayEvent || null
  } catch (e) {
    // 忽略
  }
  try {
    const bl = await blogApi.list({ current: 1, size: 5 })
    latestBlogs.value = bl.records || []
  } catch (e) {
    // 忽略
  }
  try {
    const list = await anniversaryApi.list()
    const shuffled = [...(list || [])].sort(() => Math.random() - 0.5)
    anniversaries.value = shuffled.slice(0, ANNIVERSARY_SHOW)
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
  min-height: 320px;
  background: linear-gradient(135deg, #1F3A5F 0%, #2E74B5 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.cover-img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0.5;
}
.cover-overlay {
  position: relative;
  text-align: center;
  color: #fff;
  padding: 0 16px;
  z-index: 1;
}
.cover-overlay h1 {
  font-size: 36px;
  font-weight: 700;
  text-shadow: 0 2px 16px rgba(0, 0, 0, 0.5);
  margin-bottom: 12px;
}
.cover-sub {
  font-size: 16px;
  opacity: 0.9;
  margin-bottom: 20px;
}
.cover-stats { margin-top: 8px; }

.guest-hint {
  position: absolute;
  bottom: 20px;
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

/* 今日纪念日横幅:渐变底+粒子动效,生日暖色/纪念日主色 */
.festival-banner {
  position: relative;
  max-width: 1280px;
  margin: 16px auto -16px;
  padding: 14px 24px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  overflow: hidden;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
}
.festival-banner.is-birthday { background: linear-gradient(120deg, #f472b6, #fb7185); }
.festival-banner.is-anniversary { background: linear-gradient(120deg, var(--color-primary), var(--color-accent)); }
.festival-emoji { font-size: 22px; animation: festival-bounce 1.6s infinite; }
.festival-confetti {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
  background: radial-gradient(circle at 15% 70%, rgba(255,255,255,.5) 0 2px, transparent 3px),
              radial-gradient(circle at 32% 25%, rgba(255,255,255,.35) 0 2px, transparent 3px),
              radial-gradient(circle at 55% 80%, rgba(255,255,255,.45) 0 2px, transparent 3px),
              radial-gradient(circle at 75% 20%, rgba(255,255,255,.4) 0 2px, transparent 3px),
              radial-gradient(circle at 90% 65%, rgba(255,255,255,.5) 0 2px, transparent 3px);
  background-size: 220px 160px;
  animation: festival-drift 4s linear infinite;
}
@keyframes festival-bounce {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-4px); }
}
@keyframes festival-drift {
  0% { background-position: 0 0, 0 0, 0 0, 0 0, 0 0; }
  100% { background-position: 220px 160px, 220px 160px, 220px 160px, 220px 160px, 220px 160px; }
}

.main-content {
  max-width: 1280px;
  margin: -32px auto 0;
  padding: 0 24px 32px;
  position: relative;
  z-index: 2;
}

.content-grid {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 24px;
}
.content-main { display: flex; flex-direction: column; gap: 24px; }
.feed-section { min-height: 200px; }

.content-side {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.side-card {
  background: var(--color-card);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  padding: 20px;
}
.side-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-primary);
  margin-bottom: 14px;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(31, 58, 95, 0.08);
}
.side-modules {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}
.side-module {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 14px 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s, transform 0.15s;
}
.side-module:hover {
  background: rgba(46, 116, 181, 0.08);
  transform: translateY(-2px);
}
.side-icon { font-size: 26px; margin-bottom: 4px; }
.side-name { font-size: 13px; color: var(--color-text); }

.side-blog {
  padding: 10px 0;
  border-bottom: 1px solid rgba(31, 58, 95, 0.05);
  cursor: pointer;
}
.side-blog:last-child { border-bottom: none; }
.side-blog:hover .side-blog-title { color: var(--color-accent); }
.side-blog-title {
  font-size: 14px;
  color: var(--color-text);
  line-height: 1.4;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.side-blog-meta {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.side-anni {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid rgba(31, 58, 95, 0.05);
  cursor: pointer;
}
.side-anni:last-of-type { border-bottom: none; }
.side-anni:hover .side-anni-name { color: var(--color-accent); }
.side-anni-date {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-accent);
  flex-shrink: 0;
}
.side-anni-info { min-width: 0; }
.side-anni-name {
  font-size: 14px;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.side-anni-meta {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.side-anni-more {
  margin-top: 10px;
  text-align: center;
  font-size: 13px;
  color: var(--color-accent);
  cursor: pointer;
  padding-top: 8px;
  border-top: 1px solid rgba(31, 58, 95, 0.06);
}
.side-anni-more:hover { opacity: 0.8; }

@media (max-width: 1024px) {
  .content-grid { grid-template-columns: 1fr; }
}
@media (max-width: 768px) {
  .cover { min-height: 220px; }
  .cover-overlay h1 { font-size: 22px; }
  .cover-sub { font-size: 13px; }
  .guest-hint { right: 12px; bottom: 12px; font-size: 12px; padding: 6px 12px; }
  .main-content { padding: 0 12px 24px; margin-top: -20px; }
  .side-modules { grid-template-columns: repeat(3, 1fr); }
}
</style>
