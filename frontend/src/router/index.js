// 路由表 + 登录守卫:meta.public 标记的页面无需登录也可访问(访客浏览公开内容)
import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  { path: '/login', name: 'Login', component: () => import('@/views/Login.vue'), meta: { public: true } },
  { path: '/', name: 'Home', component: () => import('@/views/Home.vue'), meta: { public: true } },
  { path: '/blog', name: 'BlogList', component: () => import('@/views/blog/BlogList.vue'), meta: { public: true } },
  { path: '/blog/:id', name: 'BlogDetail', component: () => import('@/views/blog/BlogDetail.vue'), meta: { public: true } },
  { path: '/blog/edit/:id?', name: 'BlogEdit', component: () => import('@/views/blog/BlogEdit.vue') },
  { path: '/diary', name: 'DiaryList', component: () => import('@/views/diary/DiaryList.vue'), meta: { public: true } },
  { path: '/anniversary', name: 'Anniversary', component: () => import('@/views/Anniversary.vue'), meta: { public: true } },
  { path: '/album', name: 'Album', component: () => import('@/views/album/Album.vue'), meta: { public: true } },
  { path: '/album/:id', name: 'AlbumDetail', component: () => import('@/views/album/AlbumDetail.vue'), meta: { public: true } },
  { path: '/cinema', name: 'Cinema', component: () => import('@/views/cinema/Cinema.vue'), meta: { public: true } },
  { path: '/member', name: 'Member', component: () => import('@/views/Member.vue') },
  { path: '/settings', name: 'Settings', component: () => import('@/views/Settings.vue') },
  { path: '/more', name: 'More', component: () => import('@/views/More.vue'), meta: { public: true } },
  // 兜底:未匹配的路由重定向回首页
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 全局前置守卫:未登录访问受保护页面时跳登录页,携带 redirect 便于登录后回跳
router.beforeEach((to) => {
  const userStore = useUserStore()
  if (!to.meta.public && !userStore.isLoggedIn) {
    return { name: 'Login', query: { redirect: to.fullPath } }
  }
})

export default router
