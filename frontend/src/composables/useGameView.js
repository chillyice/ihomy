import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

/**
 * 小游戏页面公共视图状态(全屏 / 宽屏 / 一屏适配)。
 *
 * - 全屏:走浏览器 Fullscreen API,目标是游戏卡本身。注意全屏元素之外的内容在浏览器全屏后不会被绘制,
 *   Element Plus 的下拉默认挂到 body,会「点开却看不见」;调用方把 `isFullscreen` 接到 el-select 的
 *   `:teleported="!isFullscreen"` 即可让弹层留在全屏元素内部。
 * - 宽屏:隐藏侧栏、放开内容宽度上限(html.game-wide),不触发浏览器全屏,顶栏与返回键仍然可用。
 * - 一屏适配:内容比内容区高时整体等比缩小,保证一页放得下且不出现页面滚动;内容不够高时保持原尺寸。
 *
 * @param {object} options
 * @param {import('vue').Ref<HTMLElement|null>} options.page 页面根容器(.page)
 * @param {import('vue').Ref<HTMLElement|null>} options.stage 游戏卡(全屏目标 / 适配对象)
 * @param {boolean} [options.fit=true] 是否锁成一屏高
 * @param {boolean} [options.scale=true] 是否按高度等比缩小(fit=true 时生效;false 时由页面自身 flex 撑满)
 */
export function useGameView({ page, stage, fit = true, scale = true } = {}) {
  const isFullscreen = ref(false)
  const isWide = ref(false)

  let fitTimer = null
  let resizeObserver = null
  let retryTimer = null
  let retryCount = 0

  const stageEl = () => stage?.value || null
  const pageEl = () => page?.value || null

  const clearStageTransform = () => {
    const el = stageEl()
    if (!el) return
    el.style.transform = ''
    el.style.transformOrigin = ''
  }

  const applyFit = () => {
    const pageNode = pageEl()
    if (!pageNode) return
    // 全屏时由 Fullscreen API 接管尺寸,不能再压页面高度或缩放卡片
    if (isFullscreen.value || !fit) {
      clearStageTransform()
      return
    }
    const viewportH = window.visualViewport?.height || window.innerHeight
    const rect = pageNode.getBoundingClientRect()
    // 暖居是主区内部滚动:按主区可视底边算;光尘/移动端按视口算
    const scroller = pageNode.closest('.gc-main')
    let bottom = viewportH
    let scrollerPadBottom = 0
    if (scroller) {
      const scrollerRect = scroller.getBoundingClientRect()
      bottom = Math.min(scrollerRect.bottom, viewportH)
      scrollerPadBottom = parseFloat(getComputedStyle(scroller).paddingBottom) || 0
    }
    const height = Math.max(220, Math.min(Math.floor(bottom - scrollerPadBottom - rect.top - 6), viewportH - 8))
    pageNode.style.height = `${height}px`
    const cs = getComputedStyle(pageNode)
    const padTop = parseFloat(cs.paddingTop) || 0
    const padY = padTop + (parseFloat(cs.paddingBottom) || 0)
    const contentH = Math.max(0, height - padY)
    if (!scale) {
      clearStageTransform()
      return
    }
    const stageNode = stageEl()
    if (!stageNode) return
    // 光尘/移动端工具栏没有胞吐走,仍占页面顶部高度,可用高度要扣掉它(暖居下工具栏在顶栏,stageTop≈0)
    const stageTop = Math.max(0, stageNode.getBoundingClientRect().top - pageNode.getBoundingClientRect().top - padTop)
    const availH = Math.max(0, contentH - stageTop)
    // offsetHeight 是布局高度,不受 transform 影响,测量结果稳定
    const naturalH = stageNode.offsetHeight
    if (!naturalH) return
    if (naturalH > availH + 1) {
      const k = Math.max(0.2, Math.round((availH / naturalH) * 1000) / 1000)
      stageNode.style.transformOrigin = 'top center'
      stageNode.style.transform = `scale(${k})`
    } else {
      clearStageTransform()
    }
  }

  // 用 setTimeout 而不是 rAF:后台标签页会暂停 rAF,切回后高度就再也算不出来
  const scheduleFit = () => {
    if (fitTimer) return
    fitTimer = setTimeout(() => {
      fitTimer = null
      applyFit()
    }, 16)
  }

  // 首帧外壳布局可能还没稳定,按递增间隔补量几次让高度收敛
  const settleFit = () => {
    scheduleFit()
    if (retryCount >= 6) return
    retryCount += 1
    retryTimer = setTimeout(settleFit, 120 + retryCount * 100)
  }

  const toggleFullscreen = async () => {
    // 缩放过的元素进全屏,内部坐标会跟着缩放,先还原再请求
    clearStageTransform()
    try {
      if (document.fullscreenElement) await document.exitFullscreen?.()
      else await stageEl()?.requestFullscreen?.()
    } catch (e) {
      // 浏览器拒绝(如 iOS)时保持窗口态
    }
  }

  const toggleWide = () => {
    isWide.value = !isWide.value
  }

  const onFullscreenChange = () => {
    const target = stageEl()
    isFullscreen.value = !!target && document.fullscreenElement === target
    nextTick(scheduleFit)
  }

  const onResize = () => scheduleFit()
  const onVisibility = () => {
    if (!document.hidden) scheduleFit()
  }

  watch(isWide, (value) => {
    document.documentElement.classList.toggle('game-wide', value)
    nextTick(scheduleFit)
  })

  onMounted(() => {
    window.addEventListener('resize', onResize, { passive: true })
    window.visualViewport?.addEventListener('resize', onResize, { passive: true })
    document.addEventListener('fullscreenchange', onFullscreenChange)
    document.addEventListener('visibilitychange', onVisibility)
    if (typeof ResizeObserver !== 'undefined' && stageEl()) {
      resizeObserver = new ResizeObserver(scheduleFit)
      resizeObserver.observe(stageEl())
    }
    settleFit()
  })

  onBeforeUnmount(() => {
    clearTimeout(fitTimer)
    clearTimeout(retryTimer)
    resizeObserver?.disconnect()
    resizeObserver = null
    window.removeEventListener('resize', onResize)
    window.visualViewport?.removeEventListener('resize', onResize)
    document.removeEventListener('fullscreenchange', onFullscreenChange)
    document.removeEventListener('visibilitychange', onVisibility)
    document.documentElement.classList.remove('game-wide')
    clearStageTransform()
    const pageNode = pageEl()
    if (pageNode) pageNode.style.height = ''
  })

  return { isFullscreen, isWide, toggleFullscreen, toggleWide, refit: scheduleFit }
}
