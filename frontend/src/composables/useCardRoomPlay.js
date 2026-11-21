// 牌桌类玩法(斗地主/掼蛋/保皇/够级)的「一起玩」房间层:房主权威、手牌点对点。
// 页面只提供四件事:怎么从房间成员建座位、公共快照长什么样、发给某席位的私牌负载、访客怎么落盘;
// 广播去重、重连补发、托管(席位交电脑)与每回合 35s 超时这些横切逻辑都在这里,四款玩法共用一套协议。
// 协议(全部走 WS game-signal,event 前缀由页面给):
//   xx:public  房主 → 全房  公共盘面(不含别人的手牌,只有各家张数)
//   xx:hand    房主 → 某人  该席位的私牌与"轮到你了"标记
//   xx:act     访客 → 房主  出牌意图 {seat, action};房主按自己手里的牌复验后才落盘
//   sync       访客 → 房主  重连补发全量(含各自的 xx:hand)
import { computed, onScopeDispose, reactive, ref, watch } from 'vue'
import { isSpectatorRole } from '@/utils/gameRoom'
import { createWakeTimers } from '@/utils/games/wakeTimers'

/** 轮到真人时的最长等待:超时交给电脑代打,别让一个人挂机把整桌拖住 */
export const CARD_ACT_TIMEOUT = 35000

/**
 * @param {Object} play useGameRoomPlay 的返回值
 * @param {Object} opts
 * @param {Number} opts.seatCount 单局席位数(斗地主 3 / 掼蛋与保皇之外见各页)
 * @param {String} opts.prefix 信令前缀(dz/gd/bh/gj)
 * @param {(seat:Number)=>Array} opts.getHand 房主侧取某席位的手牌
 * @param {()=>Object} opts.snapshot 房主侧公共快照(不能含别人的手牌)
 * @param {(state:Object)=>void} opts.applyPublic 访客侧落公共盘面
 * @param {(seat:Number)=>Object} opts.handPayload 房主侧发给某席位的私牌负载
 * @param {(data:Object)=>void} opts.applyHand 访客侧落自己的手牌与可用动作
 * @param {()=>void} [opts.onSync] 房主补发时再推一次一次性询问(可选)
 */
export function useCardRoomPlay(play, opts) {
  const { seatCount = 4, prefix = 'card', getHand, snapshot, applyPublic, handPayload, applyHand, onSync } = opts

  // 座位表:房主 0 号位,其余按后端返回的加入顺序入座,空位电脑补满(与麻将房同口径)
  // 先建空表再填充:buildSeats 里要读上一版座位(保留各家的托管标记),不能在自己的初始化表达式里读自己
  const seats = ref([])
  seats.value = buildSeats()
  // 各家剩余张数:房主按真手牌算,访客吃公共快照(自己那份也以真手牌为准由 xx:hand 覆盖)
  const counts = ref(new Array(seatCount).fill(0))
  // 本机托管:自己的席位交给电脑,自己只看
  const autoOn = ref(false)

  const waiters = new Map()
  // 35s 超时登记:房主切后台时 setTimeout 被节流,整桌会跟着干等,回前台时到期的立即补跑
  const wakeTimers = createWakeTimers()
  let lastPublishKey = ''

  const mySeat = computed(() => {
    // 单机局固定 0 号位:页面用同一套「等本机出手」流程,不必为没有房间再写一条分支
    if (!play.active) return 0
    const id = play.myId
    if (id == null) return -1
    return seats.value.findIndex((s) => s.userId != null && String(s.userId) === String(id))
  })
  const seated = computed(() => mySeat.value >= 0)
  /** 房间里没有我的席位(观战/还没入座):桌面照常渲染,但不给操作区,免得看着能点其实点不动 */
  const watching = computed(() => play.active && !seated.value)
  /** 房主(或单机)才是裁判:只有他能推进牌局、发牌、鉴定出牌 */
  const authority = computed(() => !play.active || play.isHost)
  /** 房间里只渲染不推进(访客端) */
  const isGuest = computed(() => play.active && !play.isHost)

  const seatAt = (seat) => seats.value[seat] || null
  /** 这一席位是不是电脑(空位补的电脑,或本人开着托管) */
  const isAiSeat = (seat) => {
    const st = seatAt(seat)
    return !st || st.ai || st.auto
  }
  /** 这一席位是不是"我"(我的席位开托管时也算电脑,由房主端 AI 代打) */
  const isMySeat = (seat) => seat === mySeat.value

  function buildSeats() {
    if (!play.active) return []
    const hostId = play.room?.hostId
    const players = (play.room?.members || [])
      .filter((m) => !isSpectatorRole(m.role))
      .sort((a, b) => {
        if (String(a.userId) === String(hostId)) return -1
        if (String(b.userId) === String(hostId)) return 1
        return 0
      })
    const prev = new Map(seats.value?.map((s) => [String(s.userId), s]) || [])
    const out = []
    for (const m of players.slice(0, seatCount)) {
      const keep = prev.get(String(m.userId))
      out.push({
        seat: out.length,
        userId: m.userId,
        name: m.name,
        avatar: m.avatar || '',
        ai: false,
        auto: !!keep?.auto,
      })
    }
    while (out.length < seatCount) {
      out.push({ seat: out.length, userId: null, name: '', avatar: '', ai: true, auto: false })
    }
    return out
  }

  /** 房间成员变了(有人进/出)就重排座位;离开的人席位已交电脑,叫醒等他出牌的循环 */
  function syncSeats() {
    if (!play.active) return
    const next = buildSeats()
    const changed =
      JSON.stringify(next.map((s) => [s.userId, s.ai, s.auto])) !== JSON.stringify(seats.value.map((s) => [s.userId, s.ai, s.auto]))
    seats.value = next
    if (!changed) return
    for (const [seat, done] of [...waiters]) {
      const st = seatAt(seat)
      if (!st || st.ai || st.auto) {
        waiters.delete(seat)
        done(null)
      }
    }
  }

  watch(() => play.room?.id, syncSeats)
  watch(() => (play.members || []).map((m) => `${m.userId}:${m.role}`).join(','), syncSeats)

  // ---------- 房主:广播 ----------

  /** 房主广播公共盘面 + 按人下发各自手牌;同一动作常触发两次发布,内容一样就不重复推 */
  function publish({ force = false } = {}) {
    if (!play.active || !play.isHost) return
    counts.value = seats.value.map((s) => (getHand(s.seat) || []).length)
    const state = snapshot()
    const outs = []
    for (const s of seats.value) {
      if (s.ai || s.userId == null || String(s.userId) === String(play.myId)) continue
      outs.push({ to: s.userId, seat: s.seat, ...handPayload(s.seat) })
    }
    const key = JSON.stringify([state, outs])
    if (!force && key === lastPublishKey) return
    lastPublishKey = key
    play.send(`${prefix}:public`, { state })
    for (const o of outs) play.send(`${prefix}:hand`, o)
  }

  /** 重连/开局补发:盘面 + 各自手牌必须真发,不能吃去重 */
  function resync() {
    if (!play.isHost) return
    publish({ force: true })
    onSync?.()
  }

  // ---------- 轮次等待 ----------

  // 回合倒计时:正在等谁(actSeat)与剩余秒数,裁判端随 waitAct 滴答;访客端吃 turn-ttl 广播自己滴答。
  // 纯展示口径(各端起步有网络时差),真实超时仍以裁判端 waitAct 的 setTimeout 为准。
  const actSeat = ref(-1)
  const actSecondsLeft = ref(0)
  let actTicker = null

  function stopActCountdown() {
    if (actTicker) {
      clearInterval(actTicker)
      actTicker = null
    }
    actSeat.value = -1
    actSecondsLeft.value = 0
  }

  function startActCountdown(seat, timeoutMs) {
    actSeat.value = seat
    actSecondsLeft.value = Math.max(1, Math.round(timeoutMs / 1000))
    if (actTicker) clearInterval(actTicker)
    actTicker = setInterval(() => {
      actSecondsLeft.value = Math.max(0, actSecondsLeft.value - 1)
      if (actSecondsLeft.value === 0 && actTicker) {
        clearInterval(actTicker)
        actTicker = null
      }
    }, 1000)
  }

  onScopeDispose(() => {
    stopActCountdown()
    wakeTimers.destroy()
  })

  /**
   * 房主等某席位出牌:真人点击(waiters 被 resolveLocal/远端意图解开)或超时返回 null。
   * 超时/托管/席位空出来都由调用方按 null 交给电脑。
   */
  function waitAct(seat, timeoutMs = CARD_ACT_TIMEOUT) {
    return new Promise((resolve) => {
      let timer = null
      const done = (action) => {
        if (timer) {
          wakeTimers.cancel(timer)
          timer = null
        }
        if (waiters.get(seat) === done) waiters.delete(seat)
        stopActCountdown()
        resolve(action)
      }
      if (timeoutMs > 0) {
        startActCountdown(seat, timeoutMs)
        // 全房广播剩余时限:各端本地滴答展示「还剩几秒交电脑」,不用等公共盘面轮询
        if (play.active) play.send(`${prefix}:turn-ttl`, { seat, ttl: timeoutMs })
        timer = wakeTimers.schedule(() => {
          if (waiters.get(seat) === done) {
            waiters.delete(seat)
            stopActCountdown()
            resolve(null)
          }
        }, timeoutMs)
      }
      waiters.set(seat, done)
    })
  }

  /** 房主收到远端意图(只认该座位本人;托管/电脑席位忽略) */
  function handleAct(data) {
    const seat = Number(data.seat)
    const st = seatAt(seat)
    if (!st || st.ai || st.auto) return
    if (st.userId == null || String(st.userId) !== String(data.from)) return
    const done = waiters.get(seat)
    if (!done) return
    waiters.delete(seat)
    done(data.action || null)
  }

  /** 本机出手:裁判端(房主/单机)直接叫醒自己的等待;访客端把意图发给房主 */
  function local(action) {
    if (authority.value) {
      const done = waiters.get(mySeat.value)
      if (!done) return false
      waiters.delete(mySeat.value)
      done(action)
      return true
    }
    if (!play.active || mySeat.value < 0 || !play.room?.hostId) return false
    return play.send(`${prefix}:act`, { to: play.room.hostId, seat: mySeat.value, action })
  }

  // ---------- 托管 ----------

  /** 房主处理托管请求:标记席位由电脑代打,并立刻叫醒正在等它的回合/询问 */
  function handleAuto(data) {
    const st = seats.value.find((s) => s.userId != null && String(s.userId) === String(data.from))
    if (!st || st.ai) return
    st.auto = !!data.on
    if (st.auto) {
      const done = waiters.get(st.seat)
      if (done) {
        waiters.delete(st.seat)
        done(null)
      }
    }
    publish()
  }

  /** 本机托管开关:房主自己直接生效,访客发信令请房主代打 */
  function setAuto(on) {
    if (!play.active || mySeat.value < 0) return false
    autoOn.value = !!on
    if (play.isHost) {
      const st = seats.value[mySeat.value]
      if (st) st.auto = !!on
      if (on) {
        const done = waiters.get(mySeat.value)
        if (done) {
          waiters.delete(mySeat.value)
          done(null)
        }
      }
      return true
    }
    return play.send('auto', { to: play.room.hostId, on: !!on })
  }

  /** 网页断线时牌桌上的点击都会静默失败,页面用 chatStore.socketStatus 先挡一下 */

  // ---------- 信令入口 ----------

  function onSignal(data) {
    if (!data) return
    const fromHost = String(data.from) === String(play.room?.hostId)
    if (data.event === `${prefix}:public`) {
      if (!isGuest.value || !fromHost) return
      const state = data.state || {}
      applyPublic(state)
      if (Array.isArray(state.counts)) counts.value = Array.from({ length: seatCount }, (_, i) => Math.max(0, Number(state.counts[i]) || 0))
      return
    }
    if (data.event === `${prefix}:hand`) {
      if (!isGuest.value || !fromHost) return
      if (Number(data.seat) !== mySeat.value) return
      applyHand(data)
      if (Array.isArray(data.tiles)) {
        counts.value = counts.value.map((n, i) => (i === mySeat.value ? data.tiles.length : n))
      }
      return
    }
    if (data.event === `${prefix}:turn-ttl`) {
      // 访客侧回合倒计时:房主等某真人席位时广播,各端自己滴答(展示口径,真实超时在裁判端)
      if (!isGuest.value || !fromHost) return
      const ttl = Number(data.ttl) || 0
      if (ttl > 0) startActCountdown(Number(data.seat) || -1, ttl)
      else stopActCountdown()
      return
    }
    if (data.event === `${prefix}:act`) {
      if (!play.isHost) return
      handleAct(data)
      return
    }
    if (data.event === 'auto' && play.isHost) {
      handleAuto(data)
      return
    }
    if (data.event === 'sync' && play.isHost) resync()
  }

  return reactive({
    seats,
    counts,
    autoOn,
    mySeat,
    seated,
    watching,
    authority,
    isGuest,
    actSeat,
    actSecondsLeft,
    seatAt,
    isAiSeat,
    isMySeat,
    publish,
    resync,
    waitAct,
    local,
    setAuto,
    onSignal,
    syncSeats,
  })
}
