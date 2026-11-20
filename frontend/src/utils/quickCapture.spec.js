import { describe, it, expect, vi, beforeEach } from 'vitest'

const albumApi = {
  list: vi.fn(),
  create: vi.fn(),
}
const photoApi = { upload: vi.fn() }

vi.mock('@/api', () => ({ albumApi, photoApi }))
vi.mock('@/utils/platform', () => ({ isNativeApp: () => false }))

const { pickWritableAlbum, ensureQuickAlbum, resetQuickAlbumCache } = await import('./quickCapture')

describe('pickWritableAlbum 挑可写的同名相册', () => {
  it('跳过别人家分享来的只读相册(sharedFrom)', () => {
    const list = [
      { id: 271, name: '随手拍', sharedFrom: { familyName: '养生协会' }, readOnly: true },
      { id: 266, name: '随手拍' },
    ]
    expect(pickWritableAlbum(list, '随手拍')).toEqual({ id: 266, name: '随手拍' })
  })

  it('只有分享来的同名相册时返回 null(交给调用方新建)', () => {
    const list = [{ id: 271, name: '随手拍', sharedFrom: { familyName: '养生协会' }, readOnly: true }]
    expect(pickWritableAlbum(list, '随手拍')).toBeNull()
  })

  it('名字不同、空列表都不命中', () => {
    expect(pickWritableAlbum([{ id: 266, name: '旅行' }], '随手拍')).toBeNull()
    expect(pickWritableAlbum(null, '随手拍')).toBeNull()
  })
})

describe('ensureQuickAlbum 取或建随手拍相册', () => {
  beforeEach(() => {
    resetQuickAlbumCache()
    albumApi.list.mockReset()
    albumApi.create.mockReset()
  })

  it('自家已有同名相册就直接复用,不再新建', async () => {
    albumApi.list.mockResolvedValue([
      { id: 271, name: '随手拍', sharedFrom: { familyName: '养生协会' }, readOnly: true },
      { id: 266, name: '随手拍' },
    ])
    await expect(ensureQuickAlbum('随手拍')).resolves.toBe(266)
    expect(albumApi.create).not.toHaveBeenCalled()
  })

  it('只有分享来的同名相册时新建一本自家的', async () => {
    albumApi.list.mockResolvedValue([{ id: 271, name: '随手拍', sharedFrom: { familyName: '养生协会' }, readOnly: true }])
    albumApi.create.mockResolvedValue({ id: 300, name: '随手拍' })
    await expect(ensureQuickAlbum('随手拍')).resolves.toBe(300)
    expect(albumApi.create).toHaveBeenCalledWith({ name: '随手拍', type: 'private', visibility: 'FAMILY' })
  })

  it('缓存命中后不再请求列表', async () => {
    albumApi.list.mockResolvedValue([{ id: 266, name: '随手拍' }])
    await ensureQuickAlbum('随手拍')
    await ensureQuickAlbum('随手拍')
    expect(albumApi.list).toHaveBeenCalledTimes(1)
  })
})
