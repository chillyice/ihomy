// 移动端底部导航的共享状态:五格配置 + 与服务端的偏好同步。
//
// 为什么放 composable 而不是 store:只有移动外壳(TabBar/编辑器/我的页)用,不进 Pinia 序列化;
// 但三处必须看到同一份状态,所以模块级 ref 单例(同 utils/userPref.js 的做法)。
import { computed, ref } from 'vue'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { useDisplayMode } from '@/composables/useDisplayMode'
import {
  DEFAULT_MOBILE_TABS,
  MOBILE_TABS_LOCAL_KEY,
  MOBILE_TABS_SERVER_KEY,
  buildMobileTabCandidates,
  normalizeMobileTabs,
  parseMobileTabs,
  readMobileTabs,
  resolveMobileTab,
  serializeMobileTabs,
} from '@/utils/mobileTabs'
import { syncPref, writePref } from '@/utils/userPref'

const stored = ref(readMobileTabs())
const editorOpen = ref(false)
// 一次会话只跟服务端对一次;失败不锁死(下次进页面还能重试)
let synced = false
let syncing = false

export function useMobileTabs() {
  const appStore = useAppStore()
  const userStore = useUserStore()
  const { isChild } = useDisplayMode()

  const candidates = computed(() =>
    buildMobileTabCandidates(appStore.modules, {
      hasOps: userStore.isOps || userStore.isOwner,
      child: isChild.value,
    }),
  )

  const validCodes = computed(() => candidates.value.map((c) => c.code))
  const codes = computed(() => normalizeMobileTabs(stored.value, validCodes.value))
  // 五格按顺序解析;槽位缺失时用同位置默认值兜底,保证永远渲染 5 个入口
  const tabs = computed(() => {
    const list = candidates.value
    return codes.value.map((code, i) => {
      const hit = resolveMobileTab(code, list)
      return (
        hit ||
        resolveMobileTab(DEFAULT_MOBILE_TABS[i] || 'home', list) || {
          code: 'home',
          titleKey: 'mobile.home',
          path: '/home',
          shellTab: 'home',
        }
      )
    })
  })

  const ensureSynced = async () => {
    // 游客只写本机;登录后再调一次(见 MobileLayout 的 isLoggedIn 监听),不能把 guest 那次当已同步
    if (!userStore.isLoggedIn) return
    if (synced || syncing) return
    syncing = true
    try {
      const remote = await syncPref(MOBILE_TABS_LOCAL_KEY, MOBILE_TABS_SERVER_KEY)
      if (remote) {
        const parsed = parseMobileTabs(remote)
        if (parsed) stored.value = parsed
      }
      synced = true
    } finally {
      syncing = false
    }
  }

  const save = (next) => {
    const normalized = normalizeMobileTabs(next, validCodes.value)
    stored.value = normalized
    writePref(MOBILE_TABS_LOCAL_KEY, MOBILE_TABS_SERVER_KEY, serializeMobileTabs(normalized))
    return normalized
  }

  const reset = () => save(DEFAULT_MOBILE_TABS)

  return {
    candidates,
    codes,
    tabs,
    editorOpen,
    openEditor: () => {
      editorOpen.value = true
    },
    ensureSynced,
    save,
    reset,
  }
}

/** 账号切换时复位「已对齐」标记并重新读本机缓存(模块级状态,故意不跟组件生命周期走) */
export function resetMobileTabsSync() {
  synced = false
  syncing = false
  stored.value = readMobileTabs()
}
