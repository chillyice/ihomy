import { describe, it, expect, vi, beforeEach } from 'vitest'

// 拦截器行为用例:store/router/提示全打桩,http 走假 adapter,只验"发出去的请求长什么样"
const store = vi.hoisted(() => ({ token: '', refreshToken: '', refresh: vi.fn(), logout: vi.fn() }))
vi.mock('@/stores/user', () => ({ useUserStore: () => store }))
vi.mock('@/router', () => ({
  default: { currentRoute: { value: { meta: {}, fullPath: '/home' } }, push: vi.fn() },
}))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn() } }))

const { default: request } = await import('./request')

const token = (expOffsetSec) => {
  const seg = (o) => Buffer.from(JSON.stringify(o)).toString('base64url')
  return `${seg({ alg: 'HS512', typ: 'JWT' })}.${seg({ sub: '6', exp: Math.floor(Date.now() / 1000) + expOffsetSec })}.sig`
}
const EXPIRED = token(-60)          // access token 已过期
const VALID = token(3600)           // 未过期
const REFRESH_OK = token(7 * 86400) // 刷新令牌仍在有效期

// 假 adapter:记录每次真正发出的请求,直接回成功响应
let sent = []
request.defaults.adapter = async (config) => {
  sent.push({ url: config.url, auth: config.headers?.Authorization })
  return { data: { code: 0, message: 'success', data: {} }, status: 200, statusText: 'OK', headers: {}, config }
}

beforeEach(() => {
  sent = []
  store.token = ''
  store.refreshToken = ''
  store.refresh.mockReset()
  store.logout.mockReset()
  // 续期成功:以新令牌替换过期令牌(与 stores/user.js 的 setToken 行为一致)
  store.refresh.mockImplementation(async () => { store.token = VALID })
})

describe('请求预检续期', () => {
  it('access token 已过期且刷新令牌有效:先续期,再用新令牌发出', async () => {
    store.token = EXPIRED
    store.refreshToken = REFRESH_OK
    await request.get('/public/weather')
    expect(store.refresh).toHaveBeenCalledTimes(1)
    expect(sent).toHaveLength(1)
    expect(sent[0].auth).toBe(`Bearer ${VALID}`)
  })

  it('并发请求共享同一次续期', async () => {
    store.token = EXPIRED
    store.refreshToken = REFRESH_OK
    await Promise.all([request.get('/public/weather'), request.get('/public/sun-info'), request.get('/public/feed')])
    expect(store.refresh).toHaveBeenCalledTimes(1)
    expect(sent.map((s) => s.auth)).toEqual([`Bearer ${VALID}`, `Bearer ${VALID}`, `Bearer ${VALID}`])
  })

  it('刷新令牌也过期:不尝试续期(会话真的没了,按未登录发出去)', async () => {
    store.token = EXPIRED
    store.refreshToken = token(-60)
    await request.get('/public/weather')
    expect(store.refresh).not.toHaveBeenCalled()
    expect(sent).toHaveLength(1)
  })

  it('认证类端点自身不预检(避免续期递归触发自己)', async () => {
    store.token = EXPIRED
    store.refreshToken = REFRESH_OK
    await request.post('/auth/refresh', { refreshToken: REFRESH_OK })
    expect(store.refresh).not.toHaveBeenCalled()
  })

  it('令牌仍在有效期:不续期,原样发出', async () => {
    store.token = VALID
    store.refreshToken = REFRESH_OK
    await request.get('/public/weather')
    expect(store.refresh).not.toHaveBeenCalled()
    expect(sent[0].auth).toBe(`Bearer ${VALID}`)
  })

  it('续期失败不阻断请求:按现状发出,由响应侧原有流程收尾', async () => {
    store.token = EXPIRED
    store.refreshToken = REFRESH_OK
    store.refresh.mockRejectedValue(new Error('refresh failed'))
    await request.get('/public/weather')
    expect(sent).toHaveLength(1)
  })
})
