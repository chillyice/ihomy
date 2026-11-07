import { describe, it, expect } from 'vitest'
import { nextScrolled } from './warmToolbar'

// 观察窗尺寸(1912×914 下暖居主区):内容可视高 802px
const CH = 802
// 记账本:外壳工具栏(h50+m12)+ 页签工具栏(h54+m12),一次胞吐拿掉两份
const LOSS = 128
const TH = 24
const base = { threshold: TH, loss: LOSS, clientHeight: CH }

describe('nextScrolled 工具栏胞吐判定', () => {
  it('起手在阈值内不胞吐', () => {
    expect(nextScrolled({ ...base, scrolled: false, scrollTop: 0, scrollHeight: 3001 })).toBe(false)
    expect(nextScrolled({ ...base, scrolled: false, scrollTop: TH - 4, scrollHeight: 3001 })).toBe(false)
  })

  it('越过阈值且页面还够长时胞吐', () => {
    expect(nextScrolled({ ...base, scrolled: false, scrollTop: TH + 96, scrollHeight: 3001 })).toBe(true)
  })

  it('页面总高不足一次胞吐时永不胞吐(短月份页)', () => {
    // sh=873 / ch=802 / loss=128 → 可滚距离 71 < 128:胞吐后 scrollTop 必被夹回 0
    for (const st of [0, 30, 40, 60, 71]) {
      expect(nextScrolled({ ...base, scrolled: false, scrollTop: st, scrollHeight: 873 })).toBe(false)
    }
  })

  it('临近底部不胞吐(避免往下翻又弹回)', () => {
    // 可滚距离 810,底部临界点 = 810 - 128 = 682
    expect(nextScrolled({ ...base, scrolled: false, scrollTop: 682, scrollHeight: 1612 })).toBe(true)
    expect(nextScrolled({ ...base, scrolled: false, scrollTop: 683, scrollHeight: 1612 })).toBe(false)
    expect(nextScrolled({ ...base, scrolled: false, scrollTop: 810, scrollHeight: 1612 })).toBe(false)
  })

  it('已胞吐后保持到滚回胞吐前的画面才收回', () => {
    // 收回线 = th - 4 - loss = 24 - 4 - 128 → 负数钳到 0:这里刚好落在顶部才收回
    expect(nextScrolled({ ...base, scrolled: true, scrollTop: 0, scrollHeight: 3001 })).toBe(false)
  })

  it('阈值够大时收回线往上让出 loss,不在同一位置反复翻转', () => {
    // th=200 / loss=128 → 收回线 68;胞吐线 196,两线之间是稳定的迟滞区
    const cfg = { ...base, threshold: 200 }
    expect(nextScrolled({ ...cfg, scrolled: false, scrollTop: 100, scrollHeight: 3001 })).toBe(false)
    expect(nextScrolled({ ...cfg, scrolled: false, scrollTop: 197, scrollHeight: 3001 })).toBe(true)
    expect(nextScrolled({ ...cfg, scrolled: true, scrollTop: 150, scrollHeight: 3001 })).toBe(true)
    expect(nextScrolled({ ...cfg, scrolled: true, scrollTop: 69, scrollHeight: 3001 })).toBe(true)
    expect(nextScrolled({ ...cfg, scrolled: true, scrollTop: 68, scrollHeight: 3001 })).toBe(false)
  })

  it('量不到胞吐高度(loss=0)时退回单一阈值,不会卡死', () => {
    // 首次滚动、工具栏还没渲染出来:只按阈值判断
    const cfg = { threshold: TH, loss: 0, clientHeight: CH }
    expect(nextScrolled({ ...cfg, scrolled: false, scrollTop: 100, scrollHeight: 3001 })).toBe(true)
    expect(nextScrolled({ ...cfg, scrolled: true, scrollTop: 100, scrollHeight: 3001 })).toBe(true)
    expect(nextScrolled({ ...cfg, scrolled: true, scrollTop: TH - 4, scrollHeight: 3001 })).toBe(false)
  })

  it('负 scrollTop(Safari 橡皮筋)不误判', () => {
    expect(nextScrolled({ ...base, scrolled: false, scrollTop: -20, scrollHeight: 3001 })).toBe(false)
    expect(nextScrolled({ ...base, scrolled: true, scrollTop: -20, scrollHeight: 3001 })).toBe(false)
  })

  it('已胞吐但页面变得滚不动(切到只有几条记录的月份)时收回', () => {
    // 内容从长月切到短月,可滚距离只剩 71 < 128:继续留在顶栏就再也回不去
    expect(nextScrolled({ ...base, scrolled: true, scrollTop: 71, scrollHeight: 873 })).toBe(false)
    // 可滚距离刚好等于胞吐高度也不该继续钉着(钉住后一滚就夹回来)
    expect(nextScrolled({ ...base, scrolled: true, scrollTop: 128, scrollHeight: 930 })).toBe(false)
    // 还够滚就保持
    expect(nextScrolled({ ...base, scrolled: true, scrollTop: 200, scrollHeight: 1000 })).toBe(true)
  })
})
