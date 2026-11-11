import { describe, it, expect } from 'vitest'
import { parseVersion, compareVersion, apkFileName, updatePlan, formatBytes, downloadPercent } from './appUpdate'

describe('parseVersion 版本号解析', () => {
  it('标准形态 V<主>.<次>', () => {
    expect(parseVersion('V11.84')).toEqual({ major: 11, minor: 84 })
    expect(parseVersion('  v9.3 ')).toEqual({ major: 9, minor: 3 })
  })

  it('不合法一律 null(宁漏不误)', () => {
    expect(parseVersion('V11')).toBeNull()
    expect(parseVersion('11.84')).toBeNull()
    expect(parseVersion('V11.84.1')).toBeNull()
    expect(parseVersion('')).toBeNull()
    expect(parseVersion(null)).toBeNull()
  })
})

describe('compareVersion 版本比较', () => {
  it('主版本优先于次版本', () => {
    expect(compareVersion('V12.0', 'V11.99')).toBe(1)
    expect(compareVersion('V11.99', 'V12.0')).toBe(-1)
  })

  it('同主版本比次版本', () => {
    expect(compareVersion('V11.83', 'V11.84')).toBe(-1)
    expect(compareVersion('V11.84', 'V11.84')).toBe(0)
    expect(compareVersion('V11.85', 'V11.84')).toBe(1)
  })

  it('任一侧非法返回 null', () => {
    expect(compareVersion('V11.84', 'abc')).toBeNull()
  })
})

describe('apkFileName 安装包缓存文件名', () => {
  it('版本号合法时给出纯文件名', () => {
    expect(apkFileName('V11.84')).toBe('ihomy-V11.84.apk')
  })

  it('版本号非法时返回空串(调用方据此放弃下载)', () => {
    expect(apkFileName('latest')).toBe('')
  })
})

describe('updatePlan 更新必要性判定', () => {
  it('已是最新:不打扰', () => {
    expect(updatePlan({ current: 'V11.84', latest: 'V11.84', minSupported: 'V11.80' })).toEqual({
      kind: 'none',
      required: false,
    })
  })

  it('有新版本但仍受支持:可选更新', () => {
    expect(updatePlan({ current: 'V11.82', latest: 'V11.84', minSupported: 'V11.80' })).toEqual({
      kind: 'optional',
      required: false,
    })
  })

  it('低于最低支持版本:强制更新', () => {
    expect(updatePlan({ current: 'V11.79', latest: 'V11.84', minSupported: 'V11.80' })).toEqual({
      kind: 'required',
      required: true,
    })
  })

  it('没配最低支持版本时只做可选更新', () => {
    expect(updatePlan({ current: 'V11.20', latest: 'V11.84' })).toEqual({ kind: 'optional', required: false })
  })

  it('版本号解析失败时不下结论', () => {
    expect(updatePlan({ current: 'unknown', latest: 'V11.84' }).kind).toBe('unknown')
    expect(updatePlan({}).kind).toBe('unknown')
  })

  it('服务端版本低于本机(开发自装包):不提示降级', () => {
    expect(updatePlan({ current: 'V11.90', latest: 'V11.84' }).kind).toBe('none')
  })
})

describe('formatBytes 体积文案', () => {
  it('MB / KB 两档', () => {
    expect(formatBytes(54379554)).toBe('51.9 MB')
    expect(formatBytes(2048)).toBe('2 KB')
    expect(formatBytes(0)).toBe('0 MB')
    expect(formatBytes(undefined)).toBe('0 MB')
  })
})

describe('downloadPercent 下载进度', () => {
  it('按已下载/总量四舍五入', () => {
    expect(downloadPercent(0, 100)).toBe(0)
    expect(downloadPercent(25, 100)).toBe(25)
    expect(downloadPercent(54, 100)).toBe(54)
  })

  it('封顶 99:100% 留给「确认成功」由调用方置位', () => {
    expect(downloadPercent(100, 100)).toBe(99)
    expect(downloadPercent(200, 100)).toBe(99)
  })

  it('总量未知返回 null(界面不显示百分比)', () => {
    expect(downloadPercent(10, 0)).toBeNull()
    expect(downloadPercent(10, -1)).toBeNull()
    expect(downloadPercent(10, undefined)).toBeNull()
  })

  it('已下载字节非法按 0 处理', () => {
    expect(downloadPercent(-5, 100)).toBe(0)
    expect(downloadPercent(undefined, 100)).toBe(0)
  })
})
