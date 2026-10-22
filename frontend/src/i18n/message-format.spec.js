import { describe, it, expect, vi } from 'vitest'
import { createI18n } from 'vue-i18n'
import zhCN from './zh-CN'
import en from './en'

// 文案编译守门:vue-i18n 的 message 语法里 `{` 是插值占位符的开始,
// 想显示字面量大括号必须写成 {'{名字}'}(见 zh-CN 的 kada.f4Desc / kada.f8Desc)。
// 写漏了 parity / used-keys 都看不出来,要到真正渲染那一刻才炸,而且行为随构建而变
// (@intlify/core-base 的 getCompileContext):
//   · 开发/测试构建:`console.error("Message compilation error: ...")` 然后**把原始串原样返回**;
//   · 生产构建:同一个错直接 throw —— 它抛在 $t() 里会打断整个组件渲染 → 整页空白
//     (2026-10-13 咔哒发布页实测)。
// 所以这里把两份文案逐条编译一遍,并把 NODE_ENV 临时切到 production 走生产那条分支
// (抛错),这样「写漏转义」是硬失败,而不是只留一条日志。
const flatten = (o, out = []) => {
  if (typeof o === 'string') out.push(o)
  else if (Array.isArray(o)) o.forEach((v) => flatten(v, out))
  else if (o && typeof o === 'object') Object.values(o).forEach((v) => flatten(v, out))
  return out
}

// 漏转义时两条分支的失败信号不同,两条都跑:dev 分支只记日志(先静音,失败信息由断言给),
// production 分支直接抛错。
function compileAll(messages) {
  const strings = flatten(messages)
  const i18n = createI18n({
    legacy: false,
    locale: 'probe',
    missingWarn: false,
    fallbackWarn: false,
    messages: { probe: strings.reduce((acc, s, i) => ({ ...acc, [`k${i}`]: s }), {}) },
  })
  const spy = vi.spyOn(console, 'error').mockImplementation(() => {})
  const failed = []
  const prevEnv = process.env.NODE_ENV
  for (const env of ['test', 'production']) {
    process.env.NODE_ENV = env
    strings.forEach((s, i) => {
      try {
        i18n.global.t(`k${i}`)
      } catch (e) {
        failed.push(`「${s.slice(0, 50)}」→ ${e.message}`)
      }
    })
  }
  process.env.NODE_ENV = prevEnv
  const logged = spy.mock.calls.map((c) => c.map(String).join(' ')).filter((t) => t.includes('compilation error'))
  spy.mockRestore()
  return { count: strings.length, failed: [...new Set(failed)], logged }
}

describe('i18n 文案可编译', () => {
  it('zh-CN 每条文案都能被 vue-i18n 编译', () => {
    const { count, failed, logged } = compileAll(zhCN)
    expect(count).toBeGreaterThan(0)
    expect([...failed, ...logged], `以下文案的 message 语法有问题:\n${[...failed, ...logged].join('\n')}`).toEqual([])
  })

  it('en 每条文案都能被 vue-i18n 编译', () => {
    const { count, failed, logged } = compileAll(en)
    expect(count).toBeGreaterThan(0)
    expect([...failed, ...logged], `以下文案的 message 语法有问题:\n${[...failed, ...logged].join('\n')}`).toEqual([])
  })

  it('字面量大括号按 i18n 转义书写(渲染出 {变量名} 且不报编译错)', () => {
    const i18n = createI18n({
      legacy: false,
      locale: 'zh-CN',
      missingWarn: false,
      fallbackWarn: false,
      messages: { 'zh-CN': zhCN },
    })
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {})
    // f11 是「命令输出进变量、用 {变量名} 引用」那条（原文案在 f8，重写发布页时随内容移动）。
    expect(i18n.global.t('kada.f11Desc')).toContain('{变量名}')
    const logged = spy.mock.calls.map((c) => c.map(String).join(' '))
    spy.mockRestore()
    expect(logged.filter((t) => t.includes('compilation error'))).toEqual([])
  })
})
