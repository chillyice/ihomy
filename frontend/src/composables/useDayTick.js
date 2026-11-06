// 按天变化的「今天」基准:定时器每分钟跑,但只在跨天时替换 Date 引用。
// 下游可能用对象身份做 watch(V11.40 的教训:每分钟 new Date() 会把翻页/滚动顶回起点),
// 所以绝不能每分钟新造 Date;只有 todayStr 变了才换引用。
import { onBeforeUnmount, ref } from 'vue'
import { todayStr } from '@/utils/date'

/**
 * @param {number} intervalMs 检查间隔,默认 60s(跨天最多延迟 1 分钟)
 * @returns {{ now: import('vue').Ref<Date>, tick: () => void }} now 只在跨天时换新 Date
 */
export function useDayTick(intervalMs = 60000) {
  const now = ref(new Date())
  const tick = () => {
    const t = new Date()
    if (todayStr(t) !== todayStr(now.value)) now.value = t
  }
  const timer = setInterval(tick, intervalMs)
  // 切后台再回来补一次,手机端不会一直停在旧日期
  const onVisible = () => {
    if (!document.hidden) tick()
  }
  document.addEventListener('visibilitychange', onVisible)
  onBeforeUnmount(() => {
    clearInterval(timer)
    document.removeEventListener('visibilitychange', onVisible)
  })
  return { now, tick }
}
