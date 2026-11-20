import { SERVER_ORIGIN, serverUrl } from './assetUrl'

export function originalPhotoUrl(url) {
  return /^\/(?:files\/|api\/storage\/file-signed\?)/.test(url) ? serverUrl(url) : url
}

export function albumMemoryKey(user) {
  return `ihomy:album:last:${user?.id ?? 'guest'}:${user?.familyId ?? ''}`
}

/** Thumbnail variants exist only on ihomy file and signed device endpoints. Keep external URLs intact. */
export function albumThumbnail(url, pageOrigin = globalThis.location?.origin) {
  if (!url) return ''
  let parsed
  try {
    parsed = new URL(url, pageOrigin)
  } catch {
    return url
  }
  if (parsed.origin !== pageOrigin && parsed.origin !== SERVER_ORIGIN) return url
  if (!parsed.pathname.startsWith('/files/pictures/') && parsed.pathname !== '/api/storage/file-signed') return url
  parsed.searchParams.set('thumb', '1')
  if (parsed.pathname === '/api/storage/file-signed') parsed.searchParams.set('tv', '2')
  if (url.startsWith('/') && !url.startsWith('//')) return serverUrl(parsed.pathname + parsed.search + parsed.hash)
  return parsed.href
}

export function preferredAlbum(albums, visibleRoots, savedId) {
  const saved = albums.find((a) => String(a.id) === String(savedId))
  if (saved) {
    let root = saved
    const seen = new Set()
    while (root?.parentId && !seen.has(root.id)) {
      seen.add(root.id)
      root = albums.find((a) => a.id === root.parentId)
    }
    if (visibleRoots.some((a) => a.id === root?.id)) return saved.id
  }
  return (visibleRoots.find((a) => (a.totalPhotoCount ?? a.photoCount) > 0) || visibleRoots[0])?.id ?? null
}
