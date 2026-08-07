// 各业务模块 API 汇总:统一走 request 实例,按模块导出供页面使用
import request from '@/api/request'

// 公开接口:无需登录,支持 hid(混淆 token)优先、home_id 兜底指定家庭
export const publicApi = {
  getHome: (homeId, hid) => request.get('/public/home', { params: { ...(hid ? { hid } : {}), ...(homeId ? { home_id: homeId } : {}) } }),
  getFeed: (limit = 10, homeId, hid) => request.get('/public/feed', { params: { limit, ...(hid ? { hid } : {}), ...(homeId ? { home_id: homeId } : {}) } }),
}

// 认证相关:图形验证码、家庭列表/切换、邀请码加入
export const authApi = {
  captcha: () => request.get('/auth/captcha'),
  families: () => request.get('/auth/families'),
  switchFamily: (familyId) => request.post('/auth/family/switch', { familyId }),
  join: (inviteCode) => request.post('/auth/join', { inviteCode }),
}

// 首页模块:模块配置增删改查 + 仪表盘 + 动态流
export const homeApi = {
  getModules: () => request.get('/home/modules'),
  getAllModules: () => request.get('/home/modules/all'),
  updateModules: (data) => request.put('/home/modules', { modules: data }),
  addModule: (data) => request.post('/home/modules', data),
  getDashboard: () => request.get('/home/dashboard'),
  getFeed: (limit = 20) => request.get('/home/feed', { params: { limit } }),
}

// 博客
export const blogApi = {
  list: (params) => request.get('/blog/list', { params }),
  detail: (id) => request.get(`/blog/${id}`),
  create: (data) => request.post('/blog', data),
  update: (id, data) => request.put(`/blog/${id}`, data),
  remove: (id) => request.delete(`/blog/${id}`),
}

// 日记
export const diaryApi = {
  list: (params) => request.get('/diary/list', { params }),
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

// 家庭成员、角色调整与邀请码
export const memberApi = {
  list: () => request.get('/member/list'),
  setRole: (userId, roleCode) => request.put(`/member/${userId}/role`, { roleCode }),
  remove: (userId) => request.delete(`/member/${userId}`),
  createInvite: (roleCode) => request.post('/member/invite', { roleCode }),
  inviteList: () => request.get('/member/invite'),
  accept: (code) => request.post('/member/accept', { code }),
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
}

// 操作日志(运维用)
export const logApi = {
  page: (current = 1, size = 20) => request.get('/log', { params: { current, size } }),
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
