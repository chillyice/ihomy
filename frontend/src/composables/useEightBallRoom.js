import { computed, onScopeDispose, reactive, ref, watch } from 'vue'
import { roomApi } from '@/api'
import { useChatStore } from '@/stores/chat'
import { useGameRoomPlay } from './useGameRoomPlay'
import { createEightBallGame, restoreEightBallGame } from '@/utils/games/eightBall'

// Keep a settled table and, while a shot is rolling, the input that launched it.
// Each device animates locally. Only the host commits the resulting table.
export function useEightBallRoom({ getGame, applyState, applyShot, applyPlacement }) {
  const play = useGameRoomPlay({ onSignal })
  const chat = useChatStore()
  const players = computed(() => {
    const members = play.members || []
    return [members.find((m) => m.role === 'HOST'), members.find((m) => m.role === 'PLAYER')]
  })
  const mySeat = computed(() => players.value.findIndex((m) => m && m.userId === play.myId))
  const names = computed(() => players.value.map((m) => m?.name || ''))
  const token = computed(() => (play.roomGameCode === 'game_eight_ball' ? play.room.roundToken : null))
  const connected = computed(() => chat.socketStatus === 'open')
  const revision = ref(-1)
  const turn = ref(-1)
  const peerPresent = ref(false)
  const pending = ref(false)
  const ready = computed(() => revision.value >= 0 && connected.value && (peerPresent.value || play.ended))
  const canAct = computed(() => ready.value && play.playing && !pending.value && mySeat.value >= 0 && mySeat.value === turn.value)
  let packet = null
  let lastPeer = 0
  let lastRequest = 0
  let reporting = false
  let heartbeat

  const storageKey = () => `ihomy:games:eight-ball:room:${play.roomId}:${play.myId}`
  const copy = (obj) => JSON.parse(JSON.stringify(obj))
  const send = (event, data = {}) => play.send(`eight:${event}`, { ...data, token: token.value })
  const broadcast = (to) => packet && send('table', to == null ? { packet } : { to, packet })

  function remember(next) {
    packet = next
    revision.value = next.revision
    turn.value = next.shot || next.state.phase !== 'ready' ? -1 : next.state.player
    pending.value = false
    try {
      localStorage.setItem(storageKey(), JSON.stringify({ room: play.roomId, token: token.value, packet }))
    } catch {}
  }

  function accept(next) {
    if (!next || !Number.isSafeInteger(next.revision) || next.revision <= revision.value) return false
    const state = restoreEightBallGame(next.state)
    if (!state || (next.shot && (state.phase !== 'ready' || !validShot(next.shot)))) return false
    applyState(state)
    if (next.shot && !applyShot(next.shot.angle, next.shot.power)) return false
    remember(next)
    return true
  }

  function validShot(action) {
    return Number.isFinite(action.angle) && Number.isFinite(action.power) && action.power > 0 && action.power <= 1
  }

  function commit(action, from) {
    if (!play.isHost || !play.playing || turn.value < 0 || players.value[turn.value]?.userId !== from || !action) return false
    let shot = null
    if (action.type === 'shoot' && validShot(action)) {
      if (!applyShot(action.angle, action.power)) return false
      shot = { angle: action.angle, power: action.power }
    } else if (action.type === 'place' && Number.isFinite(action.x) && Number.isFinite(action.y)) {
      if (!applyPlacement(action.x, action.y)) return false
    } else return false
    remember({ revision: revision.value + 1, state: shot ? packet.state : copy(getGame()), shot })
    broadcast()
    return true
  }

  function act(action) {
    if (!canAct.value) return false
    if (play.isHost) return commit(action, play.myId)
    pending.value = true
    lastRequest = Date.now()
    const sent = send('act', { action, revision: revision.value, to: play.room.hostId })
    if (!sent) pending.value = false
    return sent
  }

  async function reportWin() {
    const winner = players.value[packet?.state.winner]
    if (reporting || !play.isHost || !play.playing || packet?.state.phase !== 'won' || !winner) return
    reporting = true
    try {
      await roomApi.finish(play.roomId, 0, { winnerId: winner.userId, roundToken: token.value })
      await play.load({ silent: true })
    } catch {
      // The next heartbeat retries a lost finish request from the saved final table.
    } finally {
      reporting = false
    }
  }

  function settled() {
    if (!play.isHost || !play.playing || !packet?.shot || getGame().phase === 'rolling') return
    remember({ revision: revision.value + 1, state: copy(getGame()), shot: null })
    broadcast()
    reportWin()
  }

  function onSignal(data) {
    if (!token.value || data.token !== token.value || !play.myMember) return
    if (play.isHost) {
      if (data.from === players.value[1]?.userId) {
        lastPeer = Date.now()
        peerPresent.value = true
      }
      if (data.event === 'eight:sync') {
        if (data.revision !== revision.value) broadcast(data.from)
        else send('alive', { to: data.from })
      }
      if (data.event === 'eight:act') {
        if (data.revision !== revision.value || !commit(data.action, data.from)) broadcast(data.from)
      }
      return
    }
    if (data.from !== play.room.hostId) return
    if (data.event === 'eight:table' || data.event === 'eight:alive') {
      lastPeer = Date.now()
      peerPresent.value = true
      if (data.event === 'eight:table') {
        accept(data.packet)
        pending.value = false
      }
    }
  }

  function pulse() {
    if (!connected.value || !play.myMember || (!play.playing && !play.ended)) return
    if (Date.now() - lastPeer > 9000) peerPresent.value = false
    if (pending.value && Date.now() - lastRequest > 4000) pending.value = false
    if (play.isHost) reportWin()
    else send('sync', { to: play.room.hostId, revision: revision.value })
  }

  watch(
    [token, () => play.myMember?.userId, () => play.status],
    ([round, member, status], previous) => {
      if (round && member && status === 'FINISHED' && round === previous?.[0] && packet) return
      clearInterval(heartbeat)
      packet = null
      turn.value = revision.value = -1
      peerPresent.value = pending.value = false
      lastPeer = 0
      if (!round || !member || (!play.playing && !play.ended)) return
      try {
        const saved = JSON.parse(localStorage.getItem(storageKey()))
        if (saved?.room === play.roomId && saved.token === round) accept(saved.packet)
      } catch {}
      if (!packet && play.isHost && play.playing) {
        accept({ revision: 0, state: createEightBallGame(), shot: null })
      }
      pulse()
      heartbeat = setInterval(pulse, 3000)
    },
    { immediate: true },
  )
  watch(connected, (open) => {
    peerPresent.value = false
    if (open && token.value) pulse()
  })
  onScopeDispose(() => clearInterval(heartbeat))

  return reactive({
    play,
    mySeat,
    ready,
    names,
    canAct,
    watching: computed(() => play.isSpectator),
    shoot: (angle, power) => act({ type: 'shoot', angle, power }),
    place: (x, y) => act({ type: 'place', x, y }),
    settled,
    back: () => play.exitToLobby(),
  })
}
