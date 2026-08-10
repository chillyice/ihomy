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
  { path: '/points', name: 'Points', component: () => import('@/views/points/Points.vue') },
  { path: '/task', name: 'Task', component: () => import('@/views/task/Task.vue') },
  { path: '/reminder', name: 'Reminder', component: () => import('@/views/reminder/Reminder.vue') },
  { path: '/plan', name: 'Plan', component: () => import('@/views/plan/Plan.vue') },
  { path: '/wish', name: 'Wish', component: () => import('@/views/wish/Wish.vue') },
  { path: '/book', name: 'Book', component: () => import('@/views/book/Book.vue') },
  { path: '/tree', name: 'Tree', component: () => import('@/views/tree/Tree.vue') },
  { path: '/cascade', name: 'Cascade', component: () => import('@/views/cascade/Cascade.vue') },
  { path: '/chat', name: 'Chat', component: () => import('@/views/chat/Chat.vue') },
  { path: '/settings', name: 'Settings', component: () => import('@/views/Settings.vue') },
  { path: '/storage', name: 'Storage', component: () => import('@/views/storage/Storage.vue') },
  { path: '/item', name: 'Item', component: () => import('@/views/item/Item.vue') },
  // 运维管理页:仅 OPS 角色可访问（V3.8）
  { path: '/ops', name: 'Ops', component: () => import('@/views/ops/Ops.vue'), meta: { ops: true } },
  { path: '/more', name: 'More', component: () => import('@/views/More.vue'), meta: { public: true } },
  // 兜底:未匹配的路由重定向回首页
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 全局前置守卫:未登录访问受保护页面时跳登录页;OPS 专属页仅运维角色放行;
// OPS 登录后固定驻留运维页(除 Ops 外一律重定向,不进入普通用户首页/动态)
router.beforeEach((to) => {
  const userStore = useUserStore()
  if (!to.meta.public && !userStore.isLoggedIn) {
    return { name: 'Login', query: { redirect: to.fullPath } }
  }
  if (userStore.isOps && to.name !== 'Ops') {
    return { name: 'Ops' }
  }
  if (to.meta.ops && !userStore.isOps) {
    return { name: 'Home' }
  }
})

export default router
