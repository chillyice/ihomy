import { expect, it, vi } from 'vitest'
import { activityRevision, recordActivityWrite, sharedActivityRead } from './activity'

it('coalesces reads only within one current identity and revision and preserves foreground feedback', async () => {
  let scope = 'first user/family/revision'
  const first = Promise.withResolvers()
  const second = Promise.withResolvers()
  const load = vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
  const read = sharedActivityRead(load, () => scope)
  const old = read({ background: true })
  expect(load.mock.calls[0][0].isBackgroundRead()).toBe(true)
  expect(read()).toBe(old)
  expect(load.mock.calls[0][0].isBackgroundRead()).toBe(false)
  scope = 'other family or revision'
  const current = read()
  first.resolve(99)
  expect(await old).toBe(99)
  expect(read()).toBe(current)
  second.resolve(0)
  expect(await current).toBe(0)
  expect(load).toHaveBeenCalledTimes(2)
})

it('invalidates only affected summaries on writes', () => {
  const before = { ...activityRevision }
  recordActivityWrite({ method: 'get', url: '/plan/today' })
  recordActivityWrite({ method: 'delete', url: '/not-a-domain/1' })
  expect(activityRevision).toEqual(before)
  recordActivityWrite({ method: 'put', url: '/notification/1/read' })
  recordActivityWrite({ method: 'post', url: '/reminder/2/toggle-done' })
  recordActivityWrite({ method: 'put', url: '/album/3/audience' })
  recordActivityWrite({ method: 'post', url: '/recycle/restore', params: { type: 'PHOTO' } })
  expect(activityRevision).toEqual({ notifications: before.notifications + 1, agenda: before.agenda + 1, photos: before.photos + 2 })
})
