import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  { path: '/login', name: 'Login', component: () => import('@/views/Login.vue'), meta: { public: true } },
  { path: '/', name: 'Home', component: () => import('@/views/Home.vue') },
  { path: '/blog', name: 'BlogList', component: () => import('@/views/blog/BlogList.vue') },
  { path: '/blog/:id', name: 'BlogDetail', component: () => import('@/views/blog/BlogDetail.vue') },
  { path: '/blog/edit/:id?', name: 'BlogEdit', component: () => import('@/views/blog/BlogEdit.vue') },
  { path: '/diary', name: 'DiaryList', component: () => import('@/views/diary/DiaryList.vue') },
  { path: '/member', name: 'Member', component: () => import('@/views/Member.vue') },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const userStore = useUserStore()
  if (!to.meta.public && !userStore.isLoggedIn) {
    return { name: 'Login', query: { redirect: to.fullPath } }
  }
})

export default router
