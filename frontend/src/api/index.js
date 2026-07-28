import request from '@/api/request'

export const homeApi = {
  getModules: () => request.get('/home/modules'),
  getAllModules: () => request.get('/home/modules/all'),
  updateModules: (data) => request.put('/home/modules', { modules: data }),
  addModule: (data) => request.post('/home/modules', data),
  getDashboard: () => request.get('/home/dashboard'),
}

export const blogApi = {
  list: (params) => request.get('/blog/list', { params }),
  detail: (id) => request.get(`/blog/${id}`),
  create: (data) => request.post('/blog', data),
  update: (id, data) => request.put(`/blog/${id}`, data),
  remove: (id) => request.delete(`/blog/${id}`),
}

export const diaryApi = {
  list: (params) => request.get('/diary/list', { params }),
  create: (data) => request.post('/diary', data),
  update: (id, data) => request.put(`/diary/${id}`, data),
  remove: (id) => request.delete(`/diary/${id}`),
}

export const fileApi = {
  upload: (file) => {
    const form = new FormData()
    form.append('file', file)
    return request.post('/file/upload', form, { headers: { 'Content-Type': 'multipart/form-data' } })
  },
}
