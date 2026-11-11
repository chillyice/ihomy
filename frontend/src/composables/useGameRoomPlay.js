// 「一起玩」房间对局接入:游戏页读 ?room=<id> 后统一走这里。
// 职责:加载房间(seed/玩法/成员)、订阅 WS game-signal 房间事件、广播本端操作、上报成绩。
// PK:各端用同一 seed 独立跑,终局调 report();COOP:房主权威,房主广播快照、其他人只发操作意图。
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { roomApi } from '@/api'
import { useChatStore } from '@/stores/chat'
import { useUserStore } from '@/stores/user'
import { ROOM_STATUS, isCoop, roomGame } from '@/utils/gameRoom'

/**
 * @param {Object} [options]
 * @param {(data: Object) => void} [options.onSignal] 收到房间内 game-signal(已过滤 roomId)
 * @param {(room: Object) => void} [options.onRoom] 房间状态变化
 */
export function useGameRoomPlay({ onSignal, onRoom } = {}) {
  const route = useRoute()
  const router = useRouter()
  const { t } = useI18n()
  const chatStore = useChatStore()
  const userStore = useUserStore()

  const roomId = computed(() => Number(route.query.room) || 0)
  const active = computed(() => roomId.value > 0)
  const room = ref(null)
  const loading = ref(false)
  const reported = ref(false)
  const finishResult = ref(null)
  const now = ref(Date.now())

  const mode = computed(() => room.value?.mode || '')
  const coop = computed(() => isCoop(mode.value))
  const seed = computed(() => Number(room.value?.seed) || 0)
  const myId = computed(() => userStore.userInfo?.id)
  const isHost = computed(() => !!room.value && room.value.hostId === myId.value)
  const status = computed(() => room.value?.status || '')
  const playing = computed(() => status.value === ROOM_STATUS.PLAYING)
  const ended = computed(() => status.value === ROOM_STATUS.FINISHED || status.value === ROOM_STATUS.CANCELLED)
  const spec = computed(() => (room.value ? roomGame(room.value.gameCode) : null))
  const members = computed(() => room.value?.members || [])
  const myMember = computed(() => members.value.find((m) => m.userId === myId.value) || null)
  const rivalName = computed(() => {
    const other = members.value.find((m) => m.userId !== myId.value)
    return other?.name || ''
  })

  let unsubscribe = null
  let pollTimer = null
  let tickTimer = null

  function stopPolling() {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  function startPolling() {
    stopPolling()
    pollTimer = setInterval(() => {
      if (!playing.value) load({ silent: true })
    }, 4000)
  }

  const applyRoom = (next) => {
    if (!next) return
    const prevStatus = room.value?.status
    room.value = next
    onRoom?.(next)
    if (next.status === ROOM_STATUS.FINISHED || next.status === ROOM_STATUS.CANCELLED) {
      // 房间已定局:停止轮询,只在刚结束时给非上报者一次补拉结果的机会(WS 可能先于 finish 落库)
      stopPolling()
      if (next.status !== prevStatus) load({ silent: true })
    }
  }

  const load = async ({ silent = false } = {}) => {
    if (!active.value) return null
    if (!silent) loading.value = true
    try {
      const data = await roomApi.get(roomId.value)
      applyRoom(data)
      return data
    } catch {
      if (!silent) ElMessage.warning(t('games.room.loadFailed'))
      return null
    } finally {
      if (!silent) loading.value = false
    }
  }

  /** 上行房间信令(服务端补 from 并按房间广播,不回发自己) */
  const send = (event, payload = {}) => {
    if (!active.value) return false
    return chatStore.sendSignal({ type: 'game-signal', data: { roomId: roomId.value, event, ...payload } })
  }

  /** 请求房主补发当前盘面:WS 刚建立时 send 可能失败,这里重试几秒等连接就绪 */
  let syncTimer = null
  const requestSync = (event = 'sync') => {
    if (!active.value) return
    let tries = 0
    const attempt = () => {
      if (send(event)) {
        syncTimer = null
        return
      }
      if (++tries > 20) {
        syncTimer = null
        return
      }
      syncTimer = setTimeout(attempt, 500)
    }
    attempt()
  }

  /** 上报成绩(每人只报一次);PK 先完成制首报即终局,高分制等全员报完 */
  const report = async (score = 0) => {
    if (!active.value || reported.value) return null
    reported.value = true
    try {
      const res = await roomApi.finish(roomId.value, Math.max(0, Math.round(Number(score) || 0)))
      finishResult.value = res
      applyRoom(res)
      return res
    } catch {
      return null
    }
  }

  const leave = async () => {
    try {
      if (room.value && room.value.status !== ROOM_STATUS.FINISHED) await roomApi.leave(roomId.value)
    } catch {
      // 房间已结束时离开失败无所谓
    }
    router.push('/games')
  }

  const connect = () => {
    chatStore.ensureConnected()
    unsubscribe = chatStore.onGameSignal((data) => {
      if (!data || Number(data.roomId) !== roomId.value) return
      if (data.event === 'room') {
        applyRoom(data.room)
        return
      }
      onSignal?.(data)
    })
  }

  onMounted(async () => {
    if (!active.value) return
    connect()
    await load()
    startPolling()
    // 横幅计时不依赖全局时钟组件
    tickTimer = setInterval(() => {
      now.value = Date.now()
    }, 1000)
  })

  onBeforeUnmount(() => {
    unsubscribe?.()
    unsubscribe = null
    stopPolling()
    if (syncTimer) {
      clearTimeout(syncTimer)
      syncTimer = null
    }
    if (tickTimer) {
      clearInterval(tickTimer)
      tickTimer = null
    }
  })

  // reactive 包一层:模板里 play.room / play.seed 直接取值,不用到处写 .value
  return reactive({
    roomId,
    active,
    room,
    loading,
    roomGameCode: computed(() => room.value?.gameCode || ''),
    mode,
    coop,
    seed,
    isHost,
    status,
    playing,
    ended,
    spec,
    members,
    myId,
    myMember,
    rivalName,
    reported,
    finishResult,
    now,
    load,
    send,
    requestSync,
    report,
    leave,
  })
}
