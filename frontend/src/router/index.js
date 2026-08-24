// 路由表 + 登录守卫:默认所有页面游客可浏览,仅纯写/个人页需登录
import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  { path: '/login', name: 'Login', component: () => import('@/views/Login.vue'), meta: { public: true } },
  { path: '/', name: 'Home', component: () => import('@/views/Home.vue'), meta: { public: true } },
  { path: '/blog', name: 'BlogList', component: () => import('@/views/blog/BlogList.vue'), meta: { public: true } },
  { path: '/blog/:id', name: 'BlogDetail', component: () => import('@/views/blog/BlogDetail.vue'), meta: { public: true } },
  { path: '/blog/edit/:id?', name: 'BlogEdit', component: () => import('@/views/blog/BlogEdit.vue'), meta: { requiresAuth: true } },
  { path: '/diary', name: 'DiaryList', component: () => import('@/views/diary/DiaryList.vue'), meta: { public: true } },
  { path: '/anniversary', name: 'Anniversary', component: () => import('@/views/Anniversary.vue'), meta: { public: true } },
  { path: '/album', name: 'Album', component: () => import('@/views/album/Album.vue'), meta: { public: true } },
  { path: '/album/:id', name: 'AlbumDetail', component: () => import('@/views/album/AlbumDetail.vue'), meta: { public: true } },
  { path: '/cinema', name: 'Cinema', component: () => import('@/views/cinema/Cinema.vue'), meta: { public: true } },
  { path: '/music', name: 'Music', component: () => import('@/views/music/Music.vue'), meta: { public: true } },
  { path: '/member', name: 'Member', component: () => import('@/views/Member.vue'), meta: { requiresAuth: true } },
  { path: '/points', name: 'Points', component: () => import('@/views/points/Points.vue'), meta: { public: true } },
  { path: '/task', name: 'Task', component: () => import('@/views/task/Task.vue'), meta: { public: true } },
  { path: '/reminder', name: 'Reminder', component: () => import('@/views/reminder/Reminder.vue'), meta: { public: true } },
  { path: '/plan', name: 'Plan', component: () => import('@/views/plan/Plan.vue'), meta: { public: true } },
  { path: '/wish', name: 'Wish', component: () => import('@/views/wish/Wish.vue'), meta: { public: true } },
  { path: '/book', name: 'Book', component: () => import('@/views/book/Book.vue'), meta: { public: true } },
  { path: '/tree', name: 'Tree', component: () => import('@/views/tree/Tree.vue'), meta: { public: true } },
  { path: '/cascade', name: 'Cascade', component: () => import('@/views/cascade/Cascade.vue'), meta: { public: true } },
  { path: '/chat', name: 'Chat', component: () => import('@/views/chat/Chat.vue'), meta: { requiresAuth: true } },
  { path: '/settings', name: 'Settings', component: () => import('@/views/Settings.vue'), meta: { requiresAuth: true } },
  { path: '/item', name: 'Item', component: () => import('@/views/item/Item.vue'), meta: { public: true } },
  { path: '/kitchen', name: 'Kitchen', component: () => import('@/views/kitchen/Kitchen.vue'), meta: { public: true } },
  { path: '/kitchen/ingredients', name: 'Ingredient', component: () => import('@/views/kitchen/Ingredient.vue'), meta: { public: true } },
  { path: '/kitchen/recipe/:id', name: 'RecipeDetail', component: () => import('@/views/kitchen/RecipeDetail.vue'), meta: { public: true } },
  { path: '/kitchen/recipe/new', name: 'RecipeNew', component: () => import('@/views/kitchen/RecipeEdit.vue'), meta: { requiresAuth: true } },
  { path: '/kitchen/recipe/:id/edit', name: 'RecipeEdit', component: () => import('@/views/kitchen/RecipeEdit.vue'), meta: { requiresAuth: true } },
  // 运维管理页:仅 OPS 角色可访问
  { path: '/ops', name: 'Ops', component: () => import('@/views/ops/Ops.vue'), meta: { ops: true } },
  // 兜底:未匹配的路由重定向回首页
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 全局前置守卫:仅 requiresAuth 页面拦截;OPS 专属页仅运维角色放行
router.beforeEach((to) => {
  const userStore = useUserStore()
  // 需登录的页面(写操作/个人设置)才拦截
  if (to.meta.requiresAuth && !userStore.isLoggedIn) {
    return { name: 'Login', query: { redirect: to.fullPath } }
  }
  // 纯 OPS 账号(无家庭角色)只能访问运维页
  if (userStore.isPureOps && to.name !== 'Ops') {
    return { name: 'Ops' }
  }
  // 运维页要求 ops:view 权限
  if (to.meta.ops && !userStore.hasPerm('ops:view')) {
    return { name: 'Home' }
  }
})

export default router
