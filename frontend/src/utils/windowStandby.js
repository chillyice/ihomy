// 共用屏幕的公开展示范围由当前账号及家庭在这台设备上明确选择。
export function displayScope(user) {
  return user?.id && user?.familyId ? `${user.id}:${user.familyId}` : ''
}

function normalizeSharedDisplay(raw) {
  const id = Number(raw?.albumId)
  return { albumId: Number.isSafeInteger(id) && id > 0 ? id : null, schedule: raw?.schedule === true }
}

export function readSharedDisplay(scope) {
  try {
    return normalizeSharedDisplay(scope && JSON.parse(localStorage.getItem('ihomy:window:display:' + scope)))
  } catch {
    return normalizeSharedDisplay(null)
  }
}

export function writeSharedDisplay(scope, value) {
  if (!scope) return
  try {
    localStorage.setItem('ihomy:window:display:' + scope, JSON.stringify(normalizeSharedDisplay(value)))
  } catch {
    // 禁用存储的设备使用安全的默认展示。
  }
}

export function displayableAlbum(album) {
  return (
    !!album &&
    !album.readOnly &&
    (['PUBLIC', 'FAMILY', 'CIRCLE'].includes(album.visibility) || (!album.visibility && album.type === 'public'))
  )
}

export function selectedAlbumPhotos(data, selectedId) {
  if (!selectedId || Number(data?.album?.id) !== Number(selectedId) || data.readOnly || data.sharedFrom || !displayableAlbum(data.album))
    return []
  return Array.isArray(data.photos) ? data.photos.filter((p) => p?.url) : []
}

export function displaySchedule(data) {
  const items = Array.isArray(data) ? data : data?.items
  return Array.isArray(items)
    ? items.filter((it) => !it?.shared && ['REMINDER', 'TASK', 'PLAN', 'PLAN_TASK', 'ANNIVERSARY'].includes(it?.kind))
    : []
}

export function protectedStandbyRoute(path) {
  return /^\/games\/|^\/tools\/gba(?:\/|$)|^\/tools\/mindmap\/|\/(?:edit|new)(?:\/|$)|^\/cinema\//.test(path)
}

export function activeStandbyTask(doc) {
  if (!doc) return false
  if (doc.fullscreenElement || doc.activeElement?.closest?.('input, textarea, select, [contenteditable="true"], [contenteditable=""]'))
    return true
  if (
    Array.from(doc.querySelectorAll('.reader-overlay, .fp-sidebar, .recording, [data-standby-busy="true"]')).some(
      (el) => el.getClientRects().length,
    )
  )
    return true
  const drafts = doc.querySelectorAll('textarea, .chat-composer .msg-input input, [contenteditable="true"]')
  if (Array.from(drafts).some((el) => el.getClientRects().length && (el.value || el.textContent)?.trim())) return true
  return Array.from(doc.querySelectorAll('video')).some((video) => !video.paused && !video.ended && video.getClientRects().length)
}
