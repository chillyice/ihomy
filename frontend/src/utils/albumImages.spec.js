import { describe, expect, it } from 'vitest'
import { albumThumbnail, preferredAlbum } from './albumImages'

const origin = 'https://family.example'

describe('album images', () => {
  it('uses the original local file as the authorized variant URL and preserves its cache version', () => {
    expect(albumThumbnail('/files/pictures/a.png?v=2#pic', origin)).toBe('/files/pictures/a.png?v=2&thumb=1#pic')
  })

  it('preserves device signatures and never appends parameters to unrelated image sources', () => {
    const signed = '/api/storage/file-signed?path=a%2Fb&sig=123'
    expect(albumThumbnail(signed, origin)).toBe(signed + '&thumb=1&tv=2')
    for (const url of [
      '/demo/leaf.svg',
      'https://elsewhere.example/files/pictures/a.png?sig=123',
      '//cdn.example/a.png',
      'data:image/png;base64,abc',
    ]) {
      expect(albumThumbnail(url, origin)).toBe(url)
    }
  })

  it('restores an accessible album or child and falls back within the filtered list', () => {
    const empty = { id: 1, photoCount: 0 }
    const withPhotos = { id: 2, totalPhotoCount: 3 }
    const child = { id: 3, parentId: 2, photoCount: 3 }
    const roots = [empty, withPhotos]
    const albums = [...roots, child]
    expect(preferredAlbum(albums, roots, '3')).toBe(3)
    expect(preferredAlbum(albums, roots, '1')).toBe(1)
    expect(preferredAlbum(albums, roots, '99')).toBe(2)
    expect(preferredAlbum(albums, [empty], '3')).toBe(1)
    expect(preferredAlbum(albums, [], '2')).toBeNull()
  })
})
