// 诊断(仅安卓壳):前端错误先放内存环形缓冲,切后台时交给原生落盘;崩溃由原生侧自己写。
// 只本地留存,用户在设置页手动导出——不上传(计划 §5.3)。首屏不静态引入本模块。
import { App } from '@capacitor/app'
import { registerPlugin } from '@capacitor/core'
import { isAndroidApp } from '@/utils/platform'
import i18n from '@/i18n'

const IhomyDiagnostics = registerPlugin('IhomyDiagnostics')

/** 前端错误环形缓冲:与原生 front-errors.txt 的 200 条上限对齐 */
const MAX_ENTRIES = 200
const buffer = []
let installed = false
let flushing = false

function stringify(value) {
  if (value instanceof Error) return value.stack || value.message || String(value)
  if (typeof value === 'string') return value
  try {
    return JSON.stringify(value)
  } catch (e) {
    return String(value)
  }
}

function push(kind, args) {
  const text = args.map(stringify).join(' ').trim()
  if (!text) return
  buffer.push(`[${kind}] ${text}`)
  while (buffer.length > MAX_ENTRIES) buffer.shift()
}

/** 把内存里的前端错误交给原生(切后台/清空前调用);失败时留在缓冲里等下次 */
export async function flushDiagnostics() {
  if (flushing || buffer.length === 0) return
  flushing = true
  const lines = buffer.splice(0, buffer.length)
  try {
    await IhomyDiagnostics.report({ lines })
  } catch (e) {
    buffer.unshift(...lines)
    while (buffer.length > MAX_ENTRIES) buffer.shift()
    throw e
  } finally {
    flushing = false
  }
}

/** 安装采集:window.onerror / unhandledrejection / console.error */
export function installDiagnostics() {
  if (installed || !isAndroidApp()) return
  installed = true
  window.addEventListener('error', (e) => {
    const where = e.filename ? `${e.filename}:${e.lineno || 0}:${e.colno || 0}` : ''
    push('window', [e.message || 'error', where, e.error])
  })
  window.addEventListener('unhandledrejection', (e) => push('promise', [e.reason]))
  const originalError = console.error.bind(console)
  console.error = (...args) => {
    push('console', args)
    originalError(...args)
  }
  App.addListener('appStateChange', ({ isActive }) => {
    if (!isActive) flushDiagnostics().catch(() => {})
  }).catch(() => {})
  window.addEventListener('pagehide', () => {
    flushDiagnostics().catch(() => {})
  })
}

/** 设置页摘要:原生崩溃份数 + 前端错误条数(含尚未 flush 的内存条目) */
export async function diagnosticsSummary() {
  let native = { crashes: [], errors: 0 }
  try {
    native = await IhomyDiagnostics.list()
  } catch (e) {
    /* 插件不可用(理论上不会):按 0 显示 */
  }
  return {
    crashes: ((native && native.crashes) || []).length,
    errors: Number((native && native.errors) || 0) + buffer.length,
  }
}

function clip(text, max) {
  const s = String(text || '')
  return s.length > max ? s.slice(-max) : s
}

/**
 * 导出合并文本交给系统分享(用户自己发给开发者)。
 * 单份崩溃最多 20KB、前端错误最多 40KB,总量再压到 120KB,避免分享 IPC 过大。
 */
export async function exportDiagnostics() {
  await flushDiagnostics().catch(() => {})
  let list = { crashes: [], errors: 0 }
  try {
    list = await IhomyDiagnostics.list()
  } catch (e) {
    /* 没有原生文件也允许导出当前内存内容 */
  }
  const parts = []
  parts.push(`ihomy diagnostics · ${new Date().toISOString()}`)
  try {
    const { getAppVersion } = await import('./version')
    parts.push(`app: ${await getAppVersion()}`)
  } catch (e) {
    /* 版本号取不到不影响导出 */
  }
  const crashes = (list && list.crashes) || []
  parts.push(`crashes: ${crashes.length} · front-errors: ${Number((list && list.errors) || 0)}`)
  for (const item of crashes) {
    try {
      const res = await IhomyDiagnostics.read({ name: item.name })
      parts.push(`\n===== ${item.name} =====\n${clip(res && res.text, 20000)}`)
    } catch (e) {
      parts.push(`\n===== ${item.name} =====\n(unreadable)`)
    }
  }
  try {
    const res = await IhomyDiagnostics.read({ name: 'front-errors.txt' })
    if (res && res.text) parts.push(`\n===== front-errors.txt =====\n${clip(res.text, 40000)}`)
  } catch (e) {
    /* 没有前端错误文件 */
  }
  let text = parts.join('\n')
  if (text.length > 120000) text = text.slice(-120000)
  const { Share } = await import('@capacitor/share')
  await Share.share({
    title: i18n.global.t('appSettings.diagnosticsExportTitle'),
    text,
    dialogTitle: i18n.global.t('appSettings.diagnosticsExportTitle'),
  })
}

/** 清空(内存缓冲 + 原生文件),返回原生删除份数 */
export async function clearDiagnostics() {
  buffer.length = 0
  const res = await IhomyDiagnostics.clear()
  return Number((res && res.removed) || 0)
}
