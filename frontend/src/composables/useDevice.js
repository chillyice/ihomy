import { ref, onMounted, onBeforeUnmount } from 'vue'

const isMobile = ref(false)
let initialized = false

function detect() {
  const ua = /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent)
  const mq = window.matchMedia('(max-width: 768px)').matches
  return ua || mq
}

function init() {
  if (initialized) return
  initialized = true
  isMobile.value = detect()
  const mql = window.matchMedia('(max-width: 768px)')
  const handler = (e) => { isMobile.value = detect() }
  if (mql.addEventListener) mql.addEventListener('change', handler)
  else mql.addListener(handler)
}

init()

export function useDevice() {
  return { isMobile }
}
