import { defineStore } from 'pinia'
import { ref, shallowRef, watch } from 'vue'
import { sampleDeskPlan } from '@/utils/deskSpace'

// 设备的桌面偏好只存开关和入口，不保存家庭内容或物品快照。
const STORAGE_KEY = 'ihomy:window-desk:v1'

export const useWindowDeskStore = defineStore('windowDesk', () => {
  let saved
  try {
    saved = JSON.parse(localStorage.getItem(STORAGE_KEY))
  } catch {
    // 浏览器禁用存储时仍可在本次浏览中调整。
  }
  const enabled = ref(saved?.enabled !== false)
  const destinations = ref(
    saved?.destinations && typeof saved.destinations === 'object' && !Array.isArray(saved.destinations) ? saved.destinations : {},
  )
  const arrangement = ref(0)
  const plan = shallowRef(sampleDeskPlan())

  function rearrange({ manual = false } = {}) {
    plan.value = sampleDeskPlan(Math.random, manual || !plan.value.length ? 1 : 0)
    arrangement.value++
  }

  function setDestination(kind, path) {
    const next = { ...destinations.value }
    if (path) next[kind] = path
    else delete next[kind]
    destinations.value = next
  }

  function savePreferences() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({ enabled: enabled.value, destinations: destinations.value }))
    } catch {
      // 本机存储不可用，保留内存中的选择。
    }
  }
  if (saved?.positions) savePreferences()
  watch([enabled, destinations], savePreferences)

  return { enabled, destinations, arrangement, plan, rearrange, setDestination }
})
