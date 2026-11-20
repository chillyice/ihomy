import { effectScope, nextTick, ref } from 'vue'
import { expect, it, vi } from 'vitest'
import { useHomeCardData } from './useHomeCardData'

it('loads visible cards, drops an old family response and retries failures', async () => {
  const visible = ref(false)
  const family = ref(1)
  const result = ref('')
  const first = Promise.withResolvers()
  const request = vi.fn().mockReturnValueOnce(first.promise).mockRejectedValueOnce(new Error('offline')).mockResolvedValue('new family')
  const effects = effectScope()
  const data = effects.run(() =>
    useHomeCardData(
      visible,
      family,
      request,
      (value) => {
        result.value = value
      },
      () => {
        result.value = ''
      },
    ),
  )
  try {
    expect(request).not.toHaveBeenCalled()
    visible.value = true
    await nextTick()
    expect(data.status.value).toBe('loading')
    family.value = 2
    await nextTick()
    first.resolve('old family')
    await first.promise
    expect(result.value).toBe('')
    expect(data.status.value).toBe('error')
    data.retry()
    await nextTick()
    expect(result.value).toBe('new family')
    visible.value = false
    await nextTick()
    expect(data.status.value).toBe('idle')
    expect(result.value).toBe('')
  } finally {
    effects.stop()
  }
})
