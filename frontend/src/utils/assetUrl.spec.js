import { describe, it, expect, vi, afterEach } from 'vitest'

// 壳内(origin 是 https://localhost)取站内静态资源必须补生产 origin:
// 模拟器核心 / ruffle 运行时按需加载就依赖这个改写;Web 端必须保持原样(零改动)。
afterEach(() => {
  vi.unstubAllEnvs()
  vi.resetModules()
  delete globalThis.window
})

describe('assetUrl 地址改写', () => {
  it('Web 端(未配 VITE_API_BASE):原样返回,不补 origin', async () => {
    vi.resetModules()
    const { serverUrl, assetUrl, NEED_FILE_ORIGIN } = await import('./assetUrl')
    expect(NEED_FILE_ORIGIN).toBe(false)
    expect(serverUrl('/emulatorjs/data/')).toBe('/emulatorjs/data/')
    expect(serverUrl('/ruffle')).toBe('/ruffle')
    expect(assetUrl('/files/a.jpg')).toBe('/files/a.jpg')
  })

  it('壳内 + 绝对基址:站内路径与 /files/ 都补生产 origin', async () => {
    vi.stubEnv('VITE_API_BASE', 'https://ihomy.top/api')
    globalThis.window = { Capacitor: { isNativePlatform: () => true, platform: 'android' } }
    vi.resetModules()
    const { serverUrl, assetUrl, NEED_FILE_ORIGIN } = await import('./assetUrl')
    expect(NEED_FILE_ORIGIN).toBe(true)
    expect(serverUrl('/emulatorjs/data/')).toBe('https://ihomy.top/emulatorjs/data/')
    expect(serverUrl('/ruffle')).toBe('https://ihomy.top/ruffle')
    expect(assetUrl('/files/a.jpg')).toBe('https://ihomy.top/files/a.jpg')
    // 非站内绝对路径不动
    expect(assetUrl('https://cdn.example/x.jpg')).toBe('https://cdn.example/x.jpg')
    expect(serverUrl('//cdn.example/x')).toBe('//cdn.example/x')
  })

  it('壳内但未配绝对基址(理论上不会出现):不猜 origin', async () => {
    globalThis.window = { Capacitor: { isNativePlatform: () => true, platform: 'android' } }
    vi.resetModules()
    const { serverUrl, NEED_FILE_ORIGIN } = await import('./assetUrl')
    expect(NEED_FILE_ORIGIN).toBe(false)
    expect(serverUrl('/emulatorjs/data/')).toBe('/emulatorjs/data/')
  })
})
