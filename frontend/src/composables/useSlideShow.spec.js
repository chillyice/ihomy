import { effectScope, nextTick, ref } from 'vue'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { useSlideShow } from './useSlideShow'

beforeEach(() => {
  vi.useFakeTimers()
})
afterEach(() => {
  vi.useRealTimers()
})

// 组件里传的是 getter(() => n);spec 里用 ref 更顺手,这里统一转成 getter
const asGetter = (value, fallback) => {
  if (value == null) return fallback
  return typeof value === 'function' ? value : () => value.value
}
const mountShow = ({ count, active, interval = 5000 }) => {
  const effects = effectScope()
  const show = effects.run(() =>
    useSlideShow({
      count: asGetter(count),
      active: asGetter(active, () => true),
      interval,
    }),
  )
  return { show, effects }
}

it('advances on interval and wraps around', () => {
  const count = ref(3)
  const { show, effects } = mountShow({ count })
  expect(show.index.value).toBe(0)
  vi.advanceTimersByTime(5000)
  expect(show.index.value).toBe(1)
  vi.advanceTimersByTime(10000)
  expect(show.index.value).toBe(0)
  effects.stop()
})

it('pauses while hovered and stops when inactive', async () => {
  const count = ref(3)
  const active = ref(true)
  const { show, effects } = mountShow({ count, active })
  show.pause(true)
  vi.advanceTimersByTime(5000)
  expect(show.index.value).toBe(0)
  show.pause(false)
  vi.advanceTimersByTime(5000)
  expect(show.index.value).toBe(1)
  active.value = false
  await nextTick()
  vi.advanceTimersByTime(10000)
  expect(show.index.value).toBe(1)
  effects.stop()
})

it('clamps the index when items shrink and never advances a single item', () => {
  const count = ref(4)
  const { show, effects } = mountShow({ count })
  show.go(3)
  expect(show.index.value).toBe(3)
  count.value = 2
  return nextTick().then(() => {
    expect(show.index.value).toBe(1)
    count.value = 1
    return nextTick().then(() => {
      expect(show.index.value).toBe(0)
      vi.advanceTimersByTime(20000)
      expect(show.index.value).toBe(0)
      effects.stop()
    })
  })
})

it('clears its timer when the scope is disposed', () => {
  const count = ref(3)
  const { show, effects } = mountShow({ count })
  effects.stop()
  vi.advanceTimersByTime(10000)
  expect(show.index.value).toBe(0)
})

it('keeps the requested direction across wraparound and resumes forward autoplay', () => {
  const { show, effects } = mountShow({ count: ref(3) })
  show.prev()
  expect([show.index.value, show.direction.value]).toEqual([2, -1])
  vi.advanceTimersByTime(5000)
  expect([show.index.value, show.direction.value]).toEqual([0, 1])
  show.go(2)
  expect(show.direction.value).toBe(1)
  show.go(1)
  expect(show.direction.value).toBe(-1)
  effects.stop()
})
