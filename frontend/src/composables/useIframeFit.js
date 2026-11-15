import { onBeforeUnmount, onMounted } from 'vue'

/**
 * iframe 游戏页等比缩放(fullBleed 的补充):游戏内容比可视区高时,不让游戏内部出
 * 滚动条,而是把 iframe 拉到内容自然高、再用 transform 等比缩回可视区——与 H5 小游戏
 * 的 stage 缩放同一思路,对象换成 iframe;布局宽按 1/k 补偿,缩回后仍铺满横向空间。
 * 跨源 iframe 读不到内部文档,自动跳过,由外部网页自己处理滚动。
 *
 * 触发:stage 尺寸变化(ResizeObserver,覆盖窗口缩放/宽屏/页面全屏)+ iframe 每次加载完。
 * 收敛:拉高 iframe 会触发游戏内部 resize 重排,内容高可能再变(如推箱子按视口放大盘面),
 *       最多补量 4 次;应用后 300ms 复核一次,吸收游戏内字体/图片迟到的布局变化。
 */
export function useIframeFit({ stage, frame } = {}) {
  let observer = null
  let settleTimer = null
  let lastApplied = ''

  const clearFit = (el) => {
    if (!el) return
    el.style.transform = ''
    el.style.transformOrigin = ''
    el.style.width = ''
    el.style.height = ''
  }

  const contentHeight = (doc) => {
    const de = doc.documentElement
    return Math.max(de ? de.scrollHeight : 0, doc.body ? doc.body.scrollHeight : 0)
  }

  const applyFit = () => {
    const stageEl = stage?.value
    const frameEl = frame?.value
    if (!stageEl || !frameEl) return
    let doc = null
    try {
      doc = frameEl.contentDocument
    } catch (e) {
      // 跨源:读不到文档,交给外部网页自己滚
    }
    if (!doc || !doc.documentElement) {
      clearFit(frameEl)
      return
    }
    const availW = stageEl.clientWidth
    const availH = stageEl.clientHeight
    if (!availW || !availH) return
    // 先清掉上一次的行内尺寸再量,避免缩放态污染测量
    clearFit(frameEl)
    let layoutH = 0
    for (let i = 0; i < 4; i += 1) {
      const cH = contentHeight(doc)
      if (cH <= availH + 1) {
        layoutH = 0 // 盛得下:保持 CSS 铺满,不缩
        break
      }
      if (layoutH && cH <= layoutH + 1) break // 拉高后重排不再长高:收敛
      layoutH = cH
      frameEl.style.height = `${layoutH}px` // 拉到内容高,触发游戏内部 resize 重排
    }
    if (!layoutH) {
      lastApplied = ''
      return
    }
    const k = Math.max(0.2, Math.round((availH / layoutH) * 1000) / 1000)
    frameEl.style.width = `${Math.round(availW / k)}px`
    frameEl.style.height = `${layoutH}px`
    frameEl.style.transformOrigin = 'top left'
    frameEl.style.transform = `scale(${k})`
    const signature = `${k}x${layoutH}`
    if (signature !== lastApplied) {
      lastApplied = signature
      clearTimeout(settleTimer)
      settleTimer = setTimeout(() => {
        settleTimer = null
        applyFit()
      }, 300)
    }
  }

  onMounted(() => {
    if (typeof ResizeObserver !== 'undefined' && stage?.value) {
      observer = new ResizeObserver(() => applyFit())
      observer.observe(stage.value)
    }
    window.addEventListener('resize', applyFit, { passive: true })
  })

  onBeforeUnmount(() => {
    clearTimeout(settleTimer)
    observer?.disconnect()
    observer = null
    window.removeEventListener('resize', applyFit)
    clearFit(frame?.value)
  })

  return { refit: applyFit }
}
