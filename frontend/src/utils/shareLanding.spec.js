import { describe, it, expect } from 'vitest'
import { parseShareQuery, showCoverBanner } from './shareLanding'

describe('parseShareQuery 分享链接参数', () => {
  it('正常分享链接:hid 优先,home_id 作为兜底', () => {
    expect(parseShareQuery('?hid=abc123def4567890')).toEqual({ hid: 'abc123def4567890', homeId: null })
    expect(parseShareQuery('?hid=abc123def4567890&home_id=7')).toEqual({ hid: 'abc123def4567890', homeId: 7 })
    expect(parseShareQuery('?home_id=7')).toEqual({ hid: '', homeId: 7 })
  })

  it('非法值一律当没带,不把怪串透给后端', () => {
    expect(parseShareQuery('')).toEqual({ hid: '', homeId: null })
    expect(parseShareQuery('?hid=&home_id=abc')).toEqual({ hid: '', homeId: null })
    expect(parseShareQuery('?hid=%20%20&home_id=-3')).toEqual({ hid: '', homeId: null })
    expect(parseShareQuery('?hid=../../etc/passwd')).toEqual({ hid: '', homeId: null })
    expect(parseShareQuery('?home_id=999999999999999999999')).toEqual({ hid: '', homeId: null })
  })
})

describe('showCoverBanner 封面门脸判定', () => {
  const family = { id: 2, coverImage: '/files/202611/cover.jpg' }

  it('游客(无当前家庭)看到别人家的封面就展示', () => {
    expect(showCoverBanner(family, undefined)).toBe(true)
    expect(showCoverBanner(family, null)).toBe(true)
  })

  it('自己家不挂门脸;别的家庭挂', () => {
    expect(showCoverBanner(family, 2)).toBe(false)
    expect(showCoverBanner(family, '2')).toBe(false)
    expect(showCoverBanner(family, 1)).toBe(true)
  })

  it('没有封面或家庭信息不全时不展示', () => {
    expect(showCoverBanner({ id: 2, coverImage: '' }, 1)).toBe(false)
    expect(showCoverBanner({ id: 2 }, 1)).toBe(false)
    expect(showCoverBanner({ coverImage: '/files/a.jpg' }, 1)).toBe(false)
    expect(showCoverBanner(null, 1)).toBe(false)
  })
})
