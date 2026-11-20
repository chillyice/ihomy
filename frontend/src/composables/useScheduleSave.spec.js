import { describe, expect, it, vi } from 'vitest'
import { useScheduleSave } from './useScheduleSave'

describe('schedule record and audience saving', () => {
  it.each(['REMINDER', 'ANNIVERSARY'])('retries sharing the saved %s without creating it again', async (kind) => {
    const saved = { kind, id: 21 }
    const write = vi.fn().mockResolvedValue(saved)
    const share = vi.fn().mockRejectedValueOnce(new Error('offline')).mockRejectedValueOnce(new Error('offline')).mockResolvedValue(null)
    const notify = vi.fn()
    const close = vi.fn()
    const dialog = useScheduleSave(share, notify, close)
    await dialog.submit(write)
    expect(dialog.pendingShare.value).toEqual(saved)
    expect(notify).toHaveBeenCalledWith(saved)
    expect(close).not.toHaveBeenCalled()
    await dialog.submit()
    await dialog.submit()
    expect(share.mock.calls).toEqual([
      [kind, 21],
      [kind, 21],
      [kind, 21],
    ])
    expect(write).toHaveBeenCalledTimes(1)
    expect(close).toHaveBeenCalledTimes(1)
    expect(dialog.pendingShare.value).toBeNull()
  })

  it('starts fresh after closing a failed share and opening a new record', async () => {
    const share = vi.fn().mockRejectedValueOnce(new Error('offline')).mockResolvedValue(null)
    const dialog = useScheduleSave(share, vi.fn(), vi.fn())
    await dialog.submit(async () => ({ kind: 'REMINDER', id: 21 }))
    dialog.reset()
    await dialog.submit(async () => ({ kind: 'ANNIVERSARY', id: 32 }))
    expect(share.mock.calls).toEqual([
      ['REMINDER', 21],
      ['ANNIVERSARY', 32],
    ])
  })

  it('ignores a second submit in flight and lets a failed record write be retried', async () => {
    const first = Promise.withResolvers()
    const write = vi.fn().mockReturnValueOnce(first.promise).mockResolvedValue({ kind: 'REMINDER', id: 21 })
    const share = vi.fn()
    const dialog = useScheduleSave(share, vi.fn(), vi.fn())
    const saving = dialog.submit(write)
    await dialog.submit(write)
    first.reject(new Error('not saved'))
    await expect(saving).rejects.toThrow('not saved')
    expect(write).toHaveBeenCalledTimes(1)
    expect(share).not.toHaveBeenCalled()
    expect(dialog.pendingShare.value).toBeNull()
    expect(dialog.saving.value).toBe(false)
    await dialog.submit(write)
    expect(write).toHaveBeenCalledTimes(2)
    expect(share).toHaveBeenCalledWith('REMINDER', 21)
  })
})
