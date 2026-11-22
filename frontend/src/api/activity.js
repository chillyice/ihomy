import { reactive } from 'vue'

// Only successful writes invalidate the corresponding read models. No personal data is retained here.
export const activityRevision = reactive({ notifications: 0, agenda: 0, photos: 0 })

export function recordActivityWrite({ method, url, params } = {}) {
  if (!['post', 'put', 'patch', 'delete'].includes(method)) return
  const domain = url?.split('/')[1]
  if (domain === 'notification') activityRevision.notifications++
  if (domain === 'plan' || domain === 'reminder') activityRevision.agenda++
  if (
    domain === 'album' ||
    domain === 'photo' ||
    (domain === 'recycle' && ['photo', 'album'].includes(String(params?.type).toLowerCase()))
  ) {
    activityRevision.photos++
  }
}

// The sidebar, home dashboard and desk can request the same summary on mount.
// Share only concurrent reads for this account, family and revision.
export function sharedActivityRead(load, scope) {
  let pending
  return ({ background = false } = {}) => {
    const key = scope()
    if (pending?.key === key) {
      if (!background) pending.background = false
      return pending.result
    }
    const read = { key, background }
    const result = load({ isBackgroundRead: () => read.background }).finally(() => {
      if (pending?.result === result) pending = undefined
    })
    read.result = result
    pending = read
    return result
  }
}
