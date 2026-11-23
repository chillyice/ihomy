import { inject, onBeforeUnmount, onMounted } from 'vue'

/**
 * 手机游戏沉浸态的「返回」出口。
 *
 * 沉浸态下页头/底栏都被收起,返回键统一由 MobileLayout 的浮动按钮提供。多数游戏直接
 * 回所属模块就够了;房间局(如飞行棋)还需要先退房/结束房间,由页面注册自己的
 * 退出函数,浮动返回键优先调用它。
 */
export const GAME_EXIT_KEY = Symbol('ihomy:game-exit')

/** MobileLayout 侧:创建注册表并 provide,同时给浮动返回键一个统一的执行入口 */
export function createGameExitRegistry() {
  let handler = null
  return {
    provideValue: {
      set(fn) {
        handler = typeof fn === 'function' ? fn : null
      },
      clear(fn) {
        if (handler === fn) handler = null
      },
    },
    run() {
      return handler?.()
    },
    hasHandler() {
      return typeof handler === 'function'
    },
    reset() {
      handler = null
    },
  }
}

/** 游戏页侧:注册自己的退出逻辑，保留到卸载以支持取消确认后再次返回。 */
export function useGameExit(handler) {
  const registry = inject(GAME_EXIT_KEY, null)
  if (!registry || typeof handler !== 'function') return
  onMounted(() => registry.set(handler))
  onBeforeUnmount(() => registry.clear(handler))
}
