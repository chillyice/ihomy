<!-- 首页:客厅。书架(左)+ 窗与窗台(中)+ 相册柜(右)+ 写字台(底) -->
<template>
  <div ref="root" class="home-page">
    <!-- 顶部:薄木条,左品牌右用户 -->
    <header class="top-bar">
      <div class="brand" @click="$router.push('/')">
        <span class="brand-mark">ihomy</span>
      </div>
      <div class="top-right">
        <span v-if="userStore.isGuest" class="guest-entry" @click="$router.push('/login')">{{ $t('home.loginToView') }}</span>
      </div>
    </header>

    <!-- 客厅三栏 -->
    <div class="living-room">
      <!-- 左:书架 -->
      <aside class="bookshelf">
        <div class="shelf-label">{{ $t('home.shelf') }}</div>
        <div class="books">
          <div
            v-for="b in shelfBooks"
            :key="b.code"
            class="book-spine"
            :style="{ '--bg': b.color, '--rot': b.rot + 'deg' }"
            @click="$router.push(b.path)"
          >
            <span class="spine-title">{{ b.title }}</span>
            <span class="spine-icon">{{ b.icon }}</span>
          </div>
        </div>
        <div class="shelf-divider"></div>
        <div class="storage-entry" @click="$router.push('/more')">
          <span>{{ $t('home.storage') }}</span>
          <span class="arrow">→</span>
        </div>
      </aside>

      <!-- 中:窗 + 窗台 -->
      <main class="window-area">
        <div class="window-frame">
          <!-- 窗帘(左右两片,加载时拉开) -->
          <div class="curtain curtain-left" aria-hidden="true"></div>
          <div class="curtain curtain-right" aria-hidden="true"></div>

          <!-- 窗外景色 -->
          <div class="window-view" :style="{ background: windowLight.gradient }">
            <div class="window-decor" v-if="windowDecor.icon">{{ windowDecor.icon }}</div>
            <div class="window-stars" v-if="windowLight.scene === 'night'" aria-hidden="true">
              <span v-for="i in 6" :key="i" class="star" :style="starStyle(i)"></span>
            </div>
          </div>

          <!-- 窗台:今日动态便条 -->
          <div class="windowsill">
            <div class="sill-label">
              <span>{{ $t('home.today') }}</span>
              <span v-if="todayCount > 0" class="sill-count">{{ todayCount }}</span>
            </div>
            <div v-if="todayEvent" class="sill-festival">
              <span class="festival-emoji">{{ todayEvent.type === 'birthday' ? '🎂' : '🎉' }}</span>
              <span>{{ $t('home.todayEvent', { name: todayEvent.name, kind: $t(todayEvent.type === 'birthday' ? 'home.birthday' : 'home.anniversaryDay') }) }}</span>
            </div>
            <div v-if="feeds.length" class="sill-notes">
              <div
                v-for="(f, i) in feeds.slice(0, 4)"
                :key="i"
                class="sill-note"
                :style="{ '--rot': noteRot(i) + 'deg', '--bg': noteBg(i) }"
                @click="goFeed(f)"
              >
                <div class="note-author">{{ f.authorName || $t('feed.authorFallback') }}</div>
                <div class="note-text">{{ feedSummary(f) }}</div>
              </div>
            </div>
            <div v-else class="sill-empty">{{ $t('home.sillEmpty') }}</div>
          </div>
        </div>
      </main>

      <!-- 右:相册柜 -->
      <aside class="cabinet">
        <div class="cabinet-section">
          <div class="cabinet-label">{{ $t('home.albums') }}</div>
          <div class="photo-stack" v-if="photos.length">
            <div class="photo-top" @click="$router.push('/album')">
              <img :src="photos[0].url" :alt="photos[0].description || ''" />
              <div class="photo-edge edge-1"></div>
              <div class="photo-edge edge-2"></div>
            </div>
            <div class="photo-count">{{ photos.length }} {{ $t('home.photos') }}</div>
          </div>
          <div v-else class="cabinet-empty" @click="$router.push('/album')">{{ $t('home.noPhotos') }}</div>
        </div>

        <div class="cabinet-section" v-if="anniversaries.length">
          <div class="cabinet-label">{{ $t('home.anniversaries') }}</div>
          <div class="cal-items">
            <div
              v-for="a in anniversaries.slice(0, 3)"
              :key="a.id"
              class="cal-card"
              :class="{ imminent: isImminent(a) }"
              @click="$router.push('/anniversary')"
            >
              <div class="cal-date">
                <span class="cal-month">{{ a.month }}</span>
                <span class="cal-day">{{ a.day }}</span>
              </div>
              <div class="cal-info">
                <div class="cal-name">{{ a.name }}</div>
                <div v-if="a.daysLeft !== undefined" class="cal-days">{{ $t('home.daysLeftNum', { n: a.daysLeft }) }}</div>
              </div>
            </div>
          </div>
        </div>

        <div class="cabinet-section" v-if="latestBlogs.length">
          <div class="cabinet-label">{{ $t('home.latestBlogs') }}</div>
          <div
            v-for="(b, i) in latestBlogs.slice(0, 3)"
            :key="b.id"
            class="blog-sticky"
            :style="{ '--rot': (i % 2 === 0 ? -1 : 1) * (0.8 + i * 0.3) + 'deg' }"
            @click="$router.push(`/blog/${b.id}`)"
          >
            <div class="sticky-title">{{ b.title }}</div>
            <div class="sticky-meta">{{ $t('home.views', { count: b.viewCount || 0 }) }}</div>
          </div>
        </div>
      </aside>
    </div>

    <!-- 底部:写字台 -->
    <footer class="desk">
      <div class="desk-items">
        <div
          v-for="d in deskItems"
          :key="d.code"
          class="desk-item"
          @click="$router.push(d.path)"
        >
          <span class="desk-icon">{{ d.icon }}</span>
          <span class="desk-name">{{ d.title }}</span>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { publicApi, homeApi, blogApi, anniversaryApi } from '@/api'
import { gsap } from 'gsap'
import { getWindowLight, getWindowDecor } from '@/utils/windowLight'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const root = ref(null)
let ctx

const modules = ref([])
const photos = ref([])
const family = ref({})
const todayCount = ref(0)
const todayEvent = ref(null)
const latestBlogs = ref([])
const anniversaries = ref([])
const feeds = ref([])

const windowLight = ref(getWindowLight())
const windowDecor = ref(getWindowDecor())

// 书架上的书:从 modules 取阅读类
const SHELF_CODES = ['blog', 'diary', 'anniversary', 'reminder']
const shelfBooks = computed(() => {
  const colors = ['#8B6F47', '#A8483A', '#5A6B7C', '#6B7A5A']
  const icons = { blog: '📔', diary: '✒️', anniversary: '📅', reminder: '🔔' }
  const paths = { blog: '/blog', diary: '/diary', anniversary: '/anniversary', reminder: '/reminder' }
  const fallback = [
    { code: 'blog', title: '博客', path: '/blog' },
    { code: 'diary', title: '日记', path: '/diary' },
    { code: 'anniversary', title: '纪念日', path: '/anniversary' },
    { code: 'reminder', title: '提醒', path: '/reminder' },
  ]
  const ms = SHELF_CODES.map((code, i) => {
    const m = modules.value.find(x => x.code === code)
    return {
      code,
      title: m?.title || fallback[i].title,
      path: paths[code],
      icon: icons[code],
      color: colors[i],
      rot: (i % 2 === 0 ? -1 : 1) * (0.4 + i * 0.2),
    }
  })
  return ms
})

// 写字台:操作类
const DESK_CODES = ['task', 'plan', 'book', 'wish', 'chat', 'points']
const deskItems = computed(() => {
  const icons = { task: '📋', plan: '🎯', book: '💰', wish: '💫', chat: '💬', points: '🎁' }
  const paths = { task: '/task', plan: '/plan', book: '/book', wish: '/wish', chat: '/chat', points: '/points' }
  const fallback = [
    { code: 'task', title: '任务' },
    { code: 'plan', title: '计划' },
    { code: 'book', title: '记账' },
    { code: 'wish', title: '愿望' },
    { code: 'chat', title: '聊天' },
    { code: 'points', title: '积分' },
  ]
  return DESK_CODES.map((code, i) => {
    const m = modules.value.find(x => x.code === code)
    return {
      code,
      title: m?.title || fallback[i].title,
      path: paths[code],
      icon: icons[code],
    }
  })
})

const isImminent = (a) => a.daysLeft !== undefined && a.daysLeft !== null && a.daysLeft <= 3

// 便条旋转/配色
const noteRot = (i) => (i % 2 === 0 ? -1 : 1) * (0.8 + (i % 3) * 0.5)
const NOTE_COLORS = ['var(--color-sticky)', 'var(--color-sticky-2)', 'var(--color-sticky-3)']
const noteBg = (i) => NOTE_COLORS[i % NOTE_COLORS.length]

const feedSummary = (f) => {
  if (f.type === 'blog') return f.title || ''
  if (f.type === 'diary') return (f.content || '').slice(0, 30)
  if (f.type === 'photo') return `${f.count || 0} 张照片`
  return ''
}

const goFeed = (f) => {
  if (f.type === 'blog' && f.id) router.push(`/blog/${f.id}`)
  else if (f.type === 'diary') router.push('/diary')
  else if (f.type === 'photo') router.push('/album')
}

// 星星位置(夜间)
const starStyle = (i) => {
  const positions = [
    { top: '15%', left: '20%', size: '2px' },
    { top: '25%', left: '60%', size: '3px' },
    { top: '40%', left: '35%', size: '2px' },
    { top: '55%', left: '75%', size: '3px' },
    { top: '30%', left: '85%', size: '2px' },
    { top: '50%', left: '15%', size: '2px' },
  ]
  const p = positions[i % positions.length]
  return { top: p.top, left: p.left, width: p.size, height: p.size }
}

const homeId = computed(() => route.query.home_id || '')
const hid = computed(() => route.query.hid || '')

const loadPublicHome = async () => {
  const data = await publicApi.getHome(homeId.value || undefined, hid.value || undefined)
  family.value = data.family || {}
  modules.value = data.modules || []
  photos.value = data.photos || []
  todayEvent.value = data.stats?.todayEvent || null
}

const loadUserHome = async () => {
  if (hid.value || homeId.value) {
    try { await loadPublicHome(); return } catch (e) {}
  }
  try {
    const dash = await homeApi.getDashboard()
    if (dash?.modules) modules.value = dash.modules
  } catch (e) {
    await loadPublicHome(); return
  }
  try {
    const pub = await publicApi.getHome()
    photos.value = pub.photos || []
    if (pub.family) family.value = pub.family
    todayEvent.value = pub.stats?.todayEvent || null
  } catch (e) {}
  try {
    const bl = await blogApi.list({ current: 1, size: 5 })
    latestBlogs.value = bl.records || []
  } catch (e) {}
  try {
    const list = await anniversaryApi.list()
    anniversaries.value = (list || []).slice(0, 5)
  } catch (e) {}
}

const loadFeed = async () => {
  try {
    feeds.value = props.hid
      ? await publicApi.getFeed(10, undefined, props.hid)
      : props.homeId
        ? await publicApi.getFeed(10, props.homeId)
        : userStore.isGuest
          ? await publicApi.getFeed(10)
          : await homeApi.getFeed(10)
    const today = new Date()
    today.setHours(0, 0, 0, 0)
    todayCount.value = feeds.value.filter(it => new Date(it.createdAt) >= today).length
  } catch (e) {
    feeds.value = []
  }
}

const props = defineProps({
  homeId: { type: [String, Number], default: '' },
  hid: { type: String, default: '' },
})

// 每分钟刷新窗外光线
let lightTimer = null
const refreshLight = () => {
  windowLight.value = getWindowLight()
  windowDecor.value = getWindowDecor()
}

onMounted(() => {
  if (userStore.isLoggedIn) { loadUserHome(); loadFeed() }
  else { loadPublicHome(); loadFeed() }
  lightTimer = setInterval(refreshLight, 60000)

  nextTick(() => {
    if (!root.value) return
    ctx = gsap.context(() => {
      // 故事感入场:窗帘从两侧拉开
      const tl = gsap.timeline({ defaults: { ease: 'power3.out' } })
      tl.to('.curtain-left', { x: '-100%', duration: 1.2, delay: 0.2 })
        .to('.curtain-right', { x: '100%', duration: 1.2 }, '<')
        .from('.windowsill', { y: 30, autoAlpha: 0, duration: 0.6 }, '-=0.4')
        .from('.sill-note', { y: -20, autoAlpha: 0, rotation: 0, stagger: 0.08, duration: 0.4 }, '-=0.2')
        .from('.book-spine', { y: -20, autoAlpha: 0, rotation: 0, stagger: 0.06, duration: 0.4 }, '-=0.6')
        .from('.desk-item', { y: 20, autoAlpha: 0, stagger: 0.05, duration: 0.4 }, '-=0.4')
    }, root.value)
  })
})

onUnmounted(() => {
  ctx?.revert()
  if (lightTimer) clearInterval(lightTimer)
})
</script>

<style scoped>
.home-page {
  min-height: 100vh;
  background: var(--color-bg);
  display: flex;
  flex-direction: column;
}

/* 顶栏:薄木条 */
.top-bar {
  height: 48px;
  background: var(--color-wood);
  border-bottom: 2px solid var(--color-wood-dark);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  flex-shrink: 0;
}
.brand { cursor: pointer; }
.brand-mark {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-old-white);
  letter-spacing: 1px;
  font-family: var(--font-serif);
}
.guest-entry {
  font-size: 13px;
  color: var(--color-old-white);
  cursor: pointer;
  opacity: 0.85;
}
.guest-entry:hover { opacity: 1; }

/* 客厅三栏 */
.living-room {
  flex: 1;
  display: grid;
  grid-template-columns: 220px 1fr 260px;
  gap: 20px;
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
  width: 100%;
}

/* 左:书架 */
.bookshelf {
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  padding: 16px 12px;
  box-shadow: var(--shadow-book);
  display: flex;
  flex-direction: column;
}
.shelf-label {
  font-size: 12px;
  color: var(--color-text-secondary);
  letter-spacing: 1px;
  margin-bottom: 12px;
  font-family: var(--font-serif);
}
.books {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.book-spine {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  background: var(--bg);
  border-radius: 2px 4px 4px 2px;
  cursor: pointer;
  transform: rotate(var(--rot, 0deg));
  transition: transform 0.3s cubic-bezier(.34,1.56,.64,1), box-shadow 0.2s;
  box-shadow: var(--shadow-paper);
  border-left: 3px solid rgba(0,0,0,0.15);
}
.book-spine:hover {
  transform: rotate(0deg) translateX(6px) scale(1.03);
  box-shadow: var(--shadow-lift);
  z-index: 5;
}
.spine-title {
  flex: 1;
  font-size: 14px;
  color: #F5EFE0;
  font-weight: 600;
  font-family: var(--font-serif);
}
.spine-icon { font-size: 16px; }

.shelf-divider {
  height: 1px;
  background: var(--color-border);
  margin: 16px 0 12px;
}
.storage-entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 8px;
  font-size: 13px;
  color: var(--color-text-secondary);
  cursor: pointer;
  border-radius: 4px;
  transition: background 0.2s, color 0.2s;
}
.storage-entry:hover { background: var(--color-bg-2); color: var(--color-accent); }
.storage-entry .arrow { transition: transform 0.2s; }
.storage-entry:hover .arrow { transform: translateX(4px); }

/* 中:窗 */
.window-area {
  display: flex;
  align-items: stretch;
}
.window-frame {
  position: relative;
  flex: 1;
  background: var(--color-wood);
  border-radius: var(--radius);
  padding: 14px;
  box-shadow: var(--shadow-book);
  overflow: hidden;
  min-height: 480px;
  display: flex;
  flex-direction: column;
}

/* 窗帘:加载时从两侧拉开 */
.curtain {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 50%;
  background: linear-gradient(180deg, rgba(168, 72, 58, 0.85), rgba(139, 111, 71, 0.75));
  background-image: repeating-linear-gradient(90deg, rgba(0,0,0,0.05) 0 2px, transparent 2px 20px);
  z-index: 3;
  pointer-events: none;
}
.curtain-left { left: 0; }
.curtain-right { right: 0; }

/* 窗外景色 */
.window-view {
  position: relative;
  flex: 1;
  border-radius: 4px;
  overflow: hidden;
  min-height: 280px;
  transition: background 2s ease;
}
.window-decor {
  position: absolute;
  top: 16px;
  right: 20px;
  font-size: 28px;
  opacity: 0.8;
}
.window-stars .star {
  position: absolute;
  background: #F5EFE0;
  border-radius: 50%;
  box-shadow: 0 0 4px #F5EFE0;
  animation: twinkle 3s infinite;
}
@keyframes twinkle {
  0%, 100% { opacity: 0.3; }
  50% { opacity: 1; }
}

/* 窗台 */
.windowsill {
  margin-top: 14px;
  background: var(--color-wood-dark);
  border-radius: 4px;
  padding: 12px 14px;
  min-height: 100px;
  position: relative;
  z-index: 2;
}
.sill-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--color-old-white);
  margin-bottom: 8px;
  font-family: var(--font-serif);
  letter-spacing: 1px;
}
.sill-count {
  background: var(--color-accent);
  color: #F5EFE0;
  padding: 1px 6px;
  border-radius: 8px;
  font-size: 11px;
}
.sill-festival {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: var(--color-sticky);
  color: var(--color-ink);
  padding: 4px 10px;
  border-radius: 2px;
  font-size: 12px;
  margin-bottom: 8px;
  transform: rotate(-1deg);
  box-shadow: var(--shadow-paper);
  font-weight: 600;
}
.sill-notes {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.sill-note {
  background: var(--bg);
  padding: 6px 10px;
  border-radius: 2px;
  transform: rotate(var(--rot, 0deg));
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  box-shadow: var(--shadow-paper);
  max-width: 180px;
}
.sill-note:hover {
  transform: rotate(0deg) translateY(-2px);
  box-shadow: var(--shadow-lift);
}
.note-author {
  font-size: 11px;
  color: var(--color-text-secondary);
  font-weight: 600;
}
.note-text {
  font-size: 12px;
  color: var(--color-ink);
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sill-empty {
  font-size: 12px;
  color: var(--color-old-white);
  opacity: 0.6;
  font-style: italic;
}

/* 右:相册柜 */
.cabinet {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.cabinet-section {
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  padding: 14px;
  box-shadow: var(--shadow);
}
.cabinet-label {
  font-size: 12px;
  color: var(--color-text-secondary);
  letter-spacing: 1px;
  margin-bottom: 12px;
  font-family: var(--font-serif);
}

/* 照片叠堆 */
.photo-stack {
  position: relative;
  cursor: pointer;
}
.photo-top {
  position: relative;
  background: var(--color-old-white);
  padding: 6px;
  border-radius: 2px;
  box-shadow: var(--shadow-book);
  transform: rotate(-1.5deg);
  transition: transform 0.3s;
}
.photo-top:hover { transform: rotate(0deg) scale(1.03); }
.photo-top img {
  display: block;
  width: 100%;
  aspect-ratio: 4 / 3;
  object-fit: cover;
  border-radius: 1px;
}
.photo-edge {
  position: absolute;
  background: var(--color-old-white);
  border-radius: 2px;
  box-shadow: var(--shadow-paper);
}
.edge-1 {
  bottom: -6px;
  left: 8px;
  right: 8px;
  height: 12px;
  transform: rotate(2deg);
  z-index: -1;
}
.edge-2 {
  bottom: -3px;
  left: 4px;
  right: 4px;
  height: 8px;
  transform: rotate(-1deg);
  z-index: -2;
}
.photo-count {
  text-align: center;
  font-size: 11px;
  color: var(--color-text-secondary);
  margin-top: 10px;
}
.cabinet-empty {
  text-align: center;
  font-size: 12px;
  color: var(--color-text-secondary);
  padding: 20px;
  cursor: pointer;
  font-style: italic;
}

/* 纪念日翻页日历 */
.cal-items { display: flex; flex-direction: column; gap: 6px; }
.cal-card {
  display: flex;
  gap: 10px;
  padding: 8px;
  border-radius: 4px;
  cursor: pointer;
  background: var(--color-card-2);
  transition: background 0.2s, transform 0.2s;
}
.cal-card:hover { background: var(--color-bg-2); transform: translateX(2px); }
.cal-card.imminent {
  background: var(--color-sticky);
  border-left: 3px solid var(--color-accent);
}
.cal-date {
  display: flex;
  flex-direction: column;
  align-items: center;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 3px;
  padding: 4px 8px;
  min-width: 40px;
}
.cal-month { font-size: 10px; color: var(--color-accent); font-weight: 600; }
.cal-day { font-size: 16px; font-weight: 700; color: var(--color-ink); line-height: 1; }
.cal-info { flex: 1; min-width: 0; }
.cal-name {
  font-size: 12px;
  color: var(--color-ink);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cal-days { font-size: 10px; color: var(--color-text-secondary); margin-top: 2px; }

/* 博客便签 */
.blog-sticky {
  background: var(--color-sticky);
  padding: 8px 10px;
  border-radius: 2px;
  margin-bottom: 6px;
  cursor: pointer;
  transform: rotate(var(--rot, 0deg));
  transition: transform 0.2s, box-shadow 0.2s;
  box-shadow: var(--shadow-paper);
}
.blog-sticky:last-child { margin-bottom: 0; }
.blog-sticky:hover { transform: rotate(0deg) translateY(-2px); box-shadow: var(--shadow-lift); }
.sticky-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sticky-meta { font-size: 10px; color: var(--color-text-secondary); margin-top: 2px; }

/* 底部:写字台 */
.desk {
  background: var(--color-wood);
  border-top: 2px solid var(--color-wood-dark);
  padding: 14px 24px;
  flex-shrink: 0;
}
.desk-items {
  display: flex;
  justify-content: center;
  gap: 32px;
  max-width: 1400px;
  margin: 0 auto;
  flex-wrap: wrap;
}
.desk-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  padding: 6px 10px;
  border-radius: 4px;
  transition: background 0.2s, transform 0.2s;
}
.desk-item:hover { background: rgba(245, 239, 224, 0.15); transform: translateY(-2px); }
.desk-icon { font-size: 22px; }
.desk-name {
  font-size: 12px;
  color: var(--color-old-white);
  font-family: var(--font-serif);
}

@media (max-width: 1024px) {
  .living-room { grid-template-columns: 1fr; }
  .bookshelf { order: 2; }
  .window-area { order: 1; }
  .cabinet { order: 3; }
}
@media (max-width: 768px) {
  .top-bar { padding: 0 12px; }
  .living-room { padding: 12px; gap: 12px; }
  .window-frame { min-height: 360px; padding: 8px; }
  .window-view { min-height: 200px; }
  .books { flex-direction: row; flex-wrap: wrap; }
  .book-spine { flex: 1 1 calc(50% - 4px); min-width: 0; }
  .desk-items { gap: 16px; }
  .desk-icon { font-size: 20px; }
  .desk-name { font-size: 11px; }
}
</style>
