// 各业务模块 API 汇总:统一走 request 实例,按模块导出供页面使用
import request from '@/api/request'

// 公开接口:无需登录,支持 hid(混淆 token)优先、home_id 兜底指定家庭
export const publicApi = {
  getHome: (homeId, hid) => request.get('/public/home', { params: { ...(hid ? { hid } : {}), ...(homeId ? { home_id: homeId } : {}) } }),
  getFeed: (limit = 10, homeId, hid) => request.get('/public/feed', { params: { limit, ...(hid ? { hid } : {}), ...(homeId ? { home_id: homeId } : {}) } }),
}

// 认证相关:图形验证码
export const authApi = {
  captcha: () => request.get('/auth/captcha'),
  families: () => request.get('/auth/families'),
}

// 首页模块:仪表盘 + 动态流
export const homeApi = {
  getDashboard: () => request.get('/home/dashboard'),
  getFeed: (limit = 20) => request.get('/home/feed', { params: { limit } }),
}

// 博客
export const blogApi = {
  list: (params) => request.get('/blog/list', { params }),
  detail: (id) => request.get(`/blog/${id}`),
  create: (data) => request.post('/blog', data),
  update: (id, data) => request.put(`/blog/${id}`, data),
  delete: (id) => request.delete(`/blog/${id}`),
  categories: () => request.get('/blog/categories'),
  categoryCounts: () => request.get('/blog/categories/counts'),
  addCategory: (name, parentId) => request.post('/blog/categories', { name, parentId }),
  renameCategory: (id, name, parentId) => request.put('/blog/categories', { id, name, parentId }),
  deleteCategory: (id, mode) => request.delete('/blog/categories', { params: { id, mode } }),
}

// 日记
export const diaryApi = {
  list: (params) => request.get('/diary/list', { params }),
  detail: (id) => request.get(`/diary/${id}`),
  create: (data) => request.post('/diary', data),
  update: (id, data) => request.put(`/diary/${id}`, data),
  remove: (id) => request.delete(`/diary/${id}`),
}

// 文件上传(FormData 方式)
export const fileApi = {
  upload: (file) => {
    const form = new FormData()
    form.append('file', file)
    return request.post('/file/upload', form, { headers: { 'Content-Type': 'multipart/form-data' } })
  },
}

// 家庭成员、角色调整与邀请码(加入家庭统一走注册带邀请码或 /auth/join)
export const memberApi = {
  list: () => request.get('/member/list'),
  setRole: (userId, roleCode) => request.put(`/member/${userId}/role`, { roleCode }),
  remove: (userId) => request.delete(`/member/${userId}`),
  createInvite: (roleCode) => request.post('/member/invite', { roleCode }),
  inviteList: () => request.get('/member/invite'),
}

// 家庭纪念日(阳历/农历)
export const anniversaryApi = {
  list: () => request.get('/anniversary/list'),
  create: (data) => request.post('/anniversary', data),
  update: (id, data) => request.put(`/anniversary/${id}`, data),
  remove: (id) => request.delete(`/anniversary/${id}`),
}

// 相册
export const albumApi = {
  list: () => request.get('/album/list'),
  detail: (id) => request.get(`/album/${id}`),
  create: (data) => request.post('/album', data),
  update: (id, data) => request.put(`/album/${id}`, data),
  remove: (id) => request.delete(`/album/${id}`),
}

// 照片(批量上传)
export const photoApi = {
  upload: (albumId, files) => {
    const form = new FormData()
    files.forEach((f) => form.append('files', f))
    return request.post(`/album/${albumId}/photos`, form, { headers: { 'Content-Type': 'multipart/form-data' } })
  },
updateDesc: (id, description) => request.put(`/photo/${id}`, { description }),
  remove: (id) => request.delete(`/photo/${id}`),
  cascade: (limit = 60) => request.get('/photo/cascade', { params: { limit } }),
}

// 统一点赞
export const likeApi = {
  toggle: (data) => request.post('/like/toggle', data),
  state: (contentType, contentId) => request.get('/like/state', { params: { contentType, contentId } }),
}

// 统一评论(树形回复)
export const commentApi = {
  list: (contentType, contentId) => request.get('/comment/list', { params: { contentType, contentId } }),
  create: (data) => request.post('/comment', data),
  remove: (id) => request.delete(`/comment/${id}`),
}

// 站内通知
export const notificationApi = {
  list: () => request.get('/notification/list'),
  unreadCount: () => request.get('/notification/unread-count'),
  markRead: (id) => request.put(`/notification/${id}/read`),
  markAllRead: () => request.put('/notification/read-all'),
}

// 家庭资料、公开家庭搜索与入家申请
export const familyApi = {
  get: () => request.get('/family'),
  create: (data) => request.post('/family', data),
  update: (data) => request.put('/family', data),
  search: (keyword) => request.get('/family/search', { params: { keyword } }),
  apply: (familyId, message) => request.post('/family/apply', { familyId, message }),
  applyList: () => request.get('/family/apply/list'),
  handleApply: (id, action) => request.put(`/family/apply/${id}`, null, { params: { action } }),
}

// 个人资料
export const profileApi = {
  get: () => request.get('/profile'),
  update: (data) => request.put('/profile', data),
  label: () => request.get('/profile/label'),
  saveLabel: (data) => request.put('/profile/label', data),
  removeLabel: () => request.delete('/profile/label'),
}

// 放映厅:视频库 + 想看列表
export const videoApi = {
  list: (params) => request.get('/video/list', { params }),
  // 视频/海报大文件上传:关闭超时(默认 15s 不够大文件传输)
  upload: (file) => {
    const form = new FormData()
    form.append('file', file)
    return request.post('/video/upload', form, { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 0 })
  },
  create: (data) => request.post('/video', data),
  update: (id, data) => request.put(`/video/${id}`, data),
  remove: (id) => request.delete(`/video/${id}`),
  wishList: () => request.get('/video/wish/list'),
  addWish: (data) => request.post('/video/wish', data),
  wishDone: (id) => request.put(`/video/wish/${id}/done`),
  wishRemove: (id) => request.delete(`/video/wish/${id}`),
}

// 积分商城(签到/兑换/上架管理)
export const pointsApi = {
  stats: () => request.get('/points/stats'),
  checkin: () => request.post('/points/checkin'),
  products: () => request.get('/points/products'),
  create: (data) => request.post('/points/products', data),
  update: (id, data) => request.put(`/points/products/${id}`, data),
  remove: (id) => request.delete(`/points/products/${id}`),
  redeem: (id) => request.post(`/points/products/${id}/redeem`),
  myOrders: () => request.get('/points/orders'),
  familyOrders: () => request.get('/points/orders/all'),
  markTaken: (id) => request.put(`/points/orders/${id}/taken`),
}

// 任务悬赏(发布/领取/完成/确认结算)
export const taskApi = {
  list: () => request.get('/task/list'),
  create: (data) => request.post('/task', data),
  claim: (id) => request.post(`/task/${id}/claim`),
  abandon: (id) => request.post(`/task/${id}/abandon`),
  finish: (id) => request.post(`/task/${id}/finish`),
  confirm: (id) => request.post(`/task/${id}/confirm`),
  cancel: (id) => request.post(`/task/${id}/cancel`),
}

// 提醒事项(一次性/每日/每周/每月,定时站内通知全家庭)
export const reminderApi = {
  list: () => request.get('/reminder/list'),
  create: (data) => request.post('/reminder', data),
  update: (id, data) => request.put(`/reminder/${id}`, data),
  remove: (id) => request.delete(`/reminder/${id}`),
  toggleDone: (id) => request.post(`/reminder/${id}/toggle-done`),
}

// 家庭计划(计划+子任务,进度自动联动)
export const planApi = {
  list: () => request.get('/plan/list'),
  create: (data) => request.post('/plan', data),
  update: (id, data) => request.put(`/plan/${id}`, data),
  remove: (id) => request.delete(`/plan/${id}`),
  addTask: (id, data) => request.post(`/plan/${id}/task`, data),
  updateTask: (id, data) => request.put(`/plan/task/${id}`, data),
  removeTask: (id) => request.delete(`/plan/task/${id}`),
}

// 愿望单(家庭共享愿望,分类/达成/放弃)
export const wishApi = {
  list: () => request.get('/wish/list'),
  create: (data) => request.post('/wish', data),
  update: (id, data) => request.put(`/wish/${id}`, data),
  remove: (id) => request.delete(`/wish/${id}`),
}

// 音乐曲库 + 歌单管理
export const musicApi = {
  list: () => request.get('/music/list'),
  albums: () => request.get('/music/albums'),
  upload: (file) => {
    const fd = new FormData()
    fd.append('file', file)
    return request.post('/music/upload', fd)
  },
  uploadAlbum: (files, album) => {
    const fd = new FormData()
    files.forEach(f => fd.append('files', f))
    if (album) fd.append('album', album)
    return request.post('/music/upload-album', fd)
  },
  add: (data) => request.post('/music', data),
  remove: (id) => request.delete(`/music/${id}`),
  batchRemove: (ids) => request.delete('/music/batch', { data: { ids } }),
  removeByAlbum: (album) => request.delete(`/music/album/${encodeURIComponent(album)}`),
  // 歌单
  playlistList: () => request.get('/music/playlist/list'),
  createPlaylist: (name) => request.post('/music/playlist', { name }),
  deletePlaylist: (id) => request.delete(`/music/playlist/${id}`),
  playlistTracks: (id) => request.get(`/music/playlist/${id}/tracks`),
  addTracks: (id, musicIds) => request.post(`/music/playlist/${id}/tracks`, { musicIds }),
  removeTrack: (id, musicId) => request.delete(`/music/playlist/${id}/tracks/${musicId}`),
  setBackground: (id) => request.put(`/music/playlist/${id}/set-background`),
  unsetBackground: () => request.delete('/music/playlist/unset-background'),
  getBackground: () => request.get('/music/background'),
}

// 记账本(家庭收支记录,月度统计)
export const bookApi = {
  list: (month) => request.get('/book/list', { params: { month } }),
  summary: () => request.get('/book/summary'),
  create: (data) => request.post('/book', data),
  update: (id, data) => request.put(`/book/${id}`, data),
  remove: (id) => request.delete(`/book/${id}`),
}

// 运维管理(仅 OPS 角色)
export const opsApi = {
  stats: (params) => request.get('/ops/stats', { params }),
  server: () => request.get('/ops/server'),
  logs: (params) => request.get('/ops/logs', { params }),
  weatherQuota: () => request.get('/ops/weather/quota'),
  weatherFinance: () => request.get('/ops/weather/finance'),
  weatherStats: () => request.get('/ops/weather/stats'),
  weatherTimeline: (range) => request.get('/ops/weather/timeline', { params: { range } }),
}

// 家谱(家庭隐私数据,需登录)
export const treeApi = {
  list: () => request.get('/tree/list'),
  create: (data) => request.post('/tree', data),
  update: (id, data) => request.put(`/tree/${id}`, data),
  remove: (id) => request.delete(`/tree/${id}`),
}

// 聊天室(历史/未读/已读游标;实时走 WebSocket)
export const chatApi = {
  history: (params) => request.get('/chat/history', { params }),
  unread: () => request.get('/chat/unread'),
  read: (msgId) => request.post('/chat/read', { msgId }),
}

// 存储管理(家庭级设备 + 文件浏览 + 一键同步)
export const storageApi = {
  devices: () => request.get('/storage/device/list'),
  addDevice: (data) => request.post('/storage/device', data),
  updateDevice: (id, data) => request.put(`/storage/device/${id}`, data),
  removeDevice: (id) => request.delete(`/storage/device/${id}`),
  browse: (deviceId, path) => request.get('/storage/browse', { params: { deviceId, path } }),
  fileUrl: (deviceId, path, download) => {
    const base = `/api/storage/file?deviceId=${deviceId}&path=${encodeURIComponent(path)}`
    return download ? `${base}&download=true` : base
  },
  sync: (data) => request.post('/storage/sync', data),
  syncProgress: (taskId) => request.get(`/storage/sync/progress/${taskId}`),
}

// 物品定位(房子/房间/家具/物品四级 + 跨级搜索)
export const itemApi = {
  houses: () => request.get('/item/house/list'),
  addHouse: (data) => request.post('/item/house', data),
  updateHouse: (id, data) => request.put(`/item/house/${id}`, data),
  removeHouse: (id) => request.delete(`/item/house/${id}`),
  rooms: (houseId) => request.get('/item/room/list', { params: { houseId } }),
  addRoom: (data) => request.post('/item/room', data),
  updateRoom: (id, data) => request.put(`/item/room/${id}`, data),
  removeRoom: (id) => request.delete(`/item/room/${id}`),
  furnitures: (roomId) => request.get('/item/furniture/list', { params: { roomId } }),
  addFurniture: (data) => request.post('/item/furniture', data),
  updateFurniture: (id, data) => request.put(`/item/furniture/${id}`, data),
  removeFurniture: (id) => request.delete(`/item/furniture/${id}`),
  list: (params) => request.get('/item/list', { params }),
  create: (data) => request.post('/item', data),
  update: (id, data) => request.put(`/item/${id}`, data),
  remove: (id) => request.delete(`/item/${id}`),
}

export const kitchenApi = {
  menu: () => request.get('/kitchen/menu'),
  detail: (id) => request.get(`/kitchen/recipe/${id}`),
  create: (data) => request.post('/kitchen/recipe', data),
  update: (id, data) => request.put(`/kitchen/recipe/${id}`, data),
  remove: (id) => request.delete(`/kitchen/recipe/${id}`),
}

// 电子图书(家庭书架:上传/分类树/在线阅读/书签/批量操作)
export const libraryApi = {
  list: (params) => request.get('/library/list', { params }),
  detail: (id) => request.get(`/library/${id}`),
  create: (data) => request.post('/library', data),
  update: (id, data) => request.put(`/library/${id}`, data),
  delete: (id) => request.delete(`/library/${id}`),
  batchDelete: (ids) => request.delete('/library/batch', { data: { ids } }),
  batchMove: (ids, categoryId) => request.put('/library/batch/move', { ids, categoryId }),
  categories: () => request.get('/library/categories'),
  addCategory: (name, parentId) => request.post('/library/categories', { name, parentId }),
  updateCategory: (id, name, parentId) => request.put(`/library/categories/${id}`, { name, parentId }),
  deleteCategory: (id, mode) => request.delete(`/library/categories/${id}`, { params: { mode } }),
  upload: (file) => {
    const form = new FormData()
    form.append('file', file)
    return request.post('/library/upload', form, { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 0 })
  },
  getBorrow: (id) => request.get(`/library/${id}/borrow`),
  updateBorrow: (id, data) => request.put(`/library/${id}/borrow`, data),
  getBookmarks: (id) => request.get(`/library/${id}/bookmarks`),
  addBookmark: (id, data) => request.post(`/library/${id}/bookmarks`, data),
  deleteBookmark: (bmId) => request.delete(`/library/bookmarks/${bmId}`),
}
