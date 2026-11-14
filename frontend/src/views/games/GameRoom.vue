<template>
  <div class="page room-page">
    <Breadcrumb :items="[{ label: $t('games.title'), to: '/games' }, { label: roomTitle }]" />

    <div v-loading="loading" class="room-wrap">
      <div v-if="!room" class="room-empty card">
        <div class="room-empty-text">{{ $t('games.room.loadFailed') }}</div>
        <el-button size="small" round @click="router.push('/games')">{{ $t('games.room.backGames') }}</el-button>
      </div>

      <template v-else>
        <div class="room-hero card">
          <div class="room-hero-main">
            <!-- 游戏室内选游戏:房主等待中可换,换完按新游戏席位随机分配玩家/观战 -->
            <div v-if="isHost && room.status === 'WAITING'" class="room-game-picker">
              <el-select v-model="pickedGame" size="default" class="room-game-select" :loading="switchingGame" @change="changeGame">
                <el-option v-for="o in roomOptions" :key="o.code" :label="$t(o.nameKey)" :value="o.code" />
              </el-select>
              <span class="room-picker-hint">{{ $t('games.room.gamePickerHint') }}</span>
            </div>
            <div v-else class="room-game">{{ gameName }}</div>
            <div class="room-tags">
              <span class="room-tag mode">{{ modeText }}</span>
              <span class="room-tag" :class="statusClass">{{ statusText }}</span>
              <span class="room-tag">{{ seatText }}</span>
            </div>
            <div class="room-desc">{{ $t(resultKeyText) }}</div>
          </div>
          <div class="room-hero-actions">
            <el-tooltip :disabled="canInvite" :content="$t('games.room.inviteLocked')" placement="top">
              <span class="room-invite-wrap">
                <el-button size="small" round :disabled="!canInvite" @click="invite">
                  <el-icon><Promotion /></el-icon>
                  {{ $t('games.room.invite') }}
                </el-button>
              </span>
            </el-tooltip>
            <RoomCallPicker :members="room.members" :my-id="myId" />
            <el-button size="small" round :loading="leaving" @click="leave">{{ $t('games.room.leave') }}</el-button>
          </div>
        </div>

        <div class="room-members">
          <div v-for="m in room.members" :key="m.userId" class="room-member card" :class="{ host: m.userId === room.hostId }">
            <div class="room-avatar">
              <img v-if="m.avatar" :src="m.avatar" :alt="m.name" />
              <el-icon v-else :size="22"><UserFilled /></el-icon>
            </div>
            <div class="room-member-main">
              <div class="room-member-name">
                {{ m.name }}
                <span v-if="m.userId === room.hostId" class="room-badge">{{ $t('games.room.host') }}</span>
                <span v-if="isSpectatorMember(m)" class="room-badge spectate">{{ $t('games.room.roleSpectator') }}</span>
                <span v-if="m.userId === myId" class="room-badge me">{{ $t('games.room.me') }}</span>
              </div>
              <div class="room-member-state">
                <template v-if="isSpectatorMember(m)">{{ $t('games.room.spectating') }}</template>
                <template v-else-if="room.status === 'WAITING'">
                  {{ m.ready ? $t('games.room.ready') : $t('games.room.notReady') }}
                </template>
                <template v-else-if="m.finishedAt">
                  {{ $t('games.room.finishedScore', { n: m.score ?? 0 }) }}
                </template>
                <template v-else>{{ $t('games.room.playing') }}</template>
              </div>
            </div>
            <div v-if="m.userId !== myId && room.status !== 'FINISHED'" class="room-member-call">
              <el-tooltip :content="$t('games.room.callAudio')" placement="top">
                <el-button circle size="small" :disabled="!canCall" @click="callMember(m, 'AUDIO')">
                  <el-icon><Phone /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip :content="$t('games.room.callVideo')" placement="top">
                <el-button circle size="small" :disabled="!canCall" @click="callMember(m, 'VIDEO')">
                  <el-icon><VideoCamera /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
          </div>
          <div v-for="n in emptySeats" :key="'seat' + n" class="room-member empty card">
            <el-icon :size="20"><Plus /></el-icon>
            <span>{{ $t('games.room.emptySeat') }}</span>
          </div>
        </div>

        <div class="room-actions">
          <template v-if="room.status === 'WAITING'">
            <el-button
              v-if="!isHost && !myIsSpectator"
              size="default"
              round
              :type="myReady ? 'default' : 'primary'"
              :loading="busy"
              @click="toggleReady"
            >
              {{ myReady ? $t('games.room.cancelReady') : $t('games.room.setReady') }}
            </el-button>
            <el-button v-else-if="!isHost" size="default" round disabled>
              {{ $t('games.room.spectatorHint') }}
            </el-button>
            <el-button v-else type="primary" size="default" round :disabled="!canStart" :loading="busy" @click="start">
              {{ $t('games.room.start') }}
            </el-button>
            <span class="room-hint">{{ startHint }}</span>
          </template>
          <template v-else-if="room.status === 'PLAYING'">
            <el-button type="primary" size="default" round @click="enterGame">{{ $t('games.room.enterGame') }}</el-button>
            <span class="room-hint">{{ myIsSpectator ? $t('games.room.spectatorHint') : $t('games.room.playingHint') }}</span>
          </template>
          <template v-else-if="room.status === 'FINISHED'">
            <div class="room-result">
              <span class="room-result-title">{{ resultTitle }}</span>
              <span class="room-result-list">
                {{ scoreList }}
              </span>
            </div>
            <el-button v-if="isHost" type="primary" size="default" round :loading="busy" @click="again">{{
              $t('games.room.again')
            }}</el-button>
            <el-button size="default" round @click="router.push('/games')">{{ $t('games.room.backGames') }}</el-button>
          </template>
          <template v-else>
            <span class="room-hint">{{ $t('games.room.cancelled') }}</span>
            <el-button size="default" round @click="router.push('/games')">{{ $t('games.room.backGames') }}</el-button>
          </template>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
// 「一起玩」房间大厅:成员/准备/开赛/邀请/1:1 通话/自动进游戏。
// 建房已由 Games.vue 完成并发了邀请卡;这里负责把成员聚起来(2~8 人)再进游戏页。
// 游戏室内房主可换游戏:换完按新游戏的玩家席位随机分配玩家/观战(后端随机,前端只展示)。
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Phone, Plus, Promotion, UserFilled, VideoCamera } from '@element-plus/icons-vue'
import Breadcrumb from '@/components/Breadcrumb.vue'
import RoomCallPicker from '@/components/games/RoomCallPicker.vue'
import { roomApi } from '@/api'
import { useChatStore } from '@/stores/chat'
import { useCallStore } from '@/stores/call'
import { useUserStore } from '@/stores/user'
import { ROOM_GAMES, ROOM_STATUS, isCoop, isSpectatorRole, resultKey, roomGame, roomGamePath } from '@/utils/gameRoom'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const chatStore = useChatStore()
const callStore = useCallStore()
const userStore = useUserStore()

const roomId = computed(() => Number(route.params.id) || 0)
const room = ref(null)
const loading = ref(false)
const busy = ref(false)
const leaving = ref(false)
const switchingGame = ref(false)
const pickedGame = ref('')
let entered = false
let unsubscribe = null
let pollTimer = null

const myId = computed(() => userStore.userInfo?.id)
const isHost = computed(() => room.value?.hostId === myId.value)
const myReady = computed(() => !!room.value?.members.find((m) => m.userId === myId.value)?.ready)
const myIsSpectator = computed(() => isSpectatorRole(room.value?.members.find((m) => m.userId === myId.value)?.role))
const isSpectatorMember = (m) => isSpectatorRole(m?.role)
const roomOptions = computed(() => Object.values(ROOM_GAMES))
const gameName = computed(() => (room.value ? t(roomGame(room.value.gameCode)?.nameKey || 'games.title') : t('games.room.title')))
const roomTitle = computed(() => (room.value ? gameName.value : t('games.room.title')))
const modeText = computed(() => (isCoop(room.value?.mode) ? t('games.room.modeCoop') : t('games.room.modePk')))
const resultKeyText = computed(() => (room.value ? resultKey(room.value.gameCode) : 'games.room.resultScore'))
const statusText = computed(() => t(`games.room.status.${String(room.value?.status || 'WAITING').toLowerCase()}`))
const statusClass = computed(() => String(room.value?.status || '').toLowerCase())
const canCall = computed(() => chatStore.socketStatus === 'open' && !callStore.inCall)
const canInvite = computed(() => !!room.value && room.value.status === ROOM_STATUS.WAITING)
const seatText = computed(() => {
  if (!room.value) return ''
  const players = room.value.playersCount ?? room.value.members.filter((m) => !isSpectatorMember(m)).length
  const spectators = room.value.members.length - players
  return t('games.room.seatSummary', { players, max: room.value.maxSeatPlayers || 8, spectators })
})
const emptySeats = computed(() => {
  const max = Math.min(Number(room.value?.maxPlayers) || 8, 8)
  return Math.max(0, max - (room.value?.members.length || 0))
})
/** 玩家(非观战)都就绪才能开赛;观战不要求准备 */
const players = computed(() => (room.value?.members || []).filter((m) => !isSpectatorMember(m)))
const canStart = computed(() => players.value.length >= 2 && players.value.every((m) => m.ready))
const startHint = computed(() => {
  if (!room.value) return ''
  if (players.value.length < 2) return t('games.room.needTwoPlayers')
  const waiting = players.value.filter((m) => !m.ready).length
  return waiting ? t('games.room.waitPlayersReady', { n: waiting }) : t('games.room.allPlayersReady')
})
const resultTitle = computed(() => {
  if (!room.value) return ''
  if (isCoop(room.value.mode)) return t('games.room.coopDone')
  const winner = room.value.members.find((m) => m.userId === room.value.winnerId)
  return winner ? t('games.room.winner', { name: winner.name }) : t('games.room.draw')
})
const scoreList = computed(() => {
  if (!room.value || isCoop(room.value.mode)) return ''
  return room.value.members
    .filter((m) => m.finishedAt)
    .map((m) => `${m.name} ${m.score ?? 0}`)
    .join(' · ')
})

/** 房主换游戏:后端随机重新分配席位,广播后全员更新 */
const changeGame = async (code) => {
  if (!code || !room.value || code === room.value.gameCode) return
  switchingGame.value = true
  try {
    applyRoom(await roomApi.setGame(roomId.value, code))
  } catch (e) {
    ElMessage.warning(e?.response?.data?.message || t('games.room.actFailed'))
    pickedGame.value = room.value.gameCode // 回弹到当前游戏
  } finally {
    switchingGame.value = false
  }
}

const applyRoom = (next) => {
  if (!next) return
  // 只有「本页停留期间,房间从等待变成开赛」才自动进游戏页。
  // 从游戏页点「退出对局」回大厅时会重新挂载本页、首次拿到的是 PLAYING,
  // 以前这里会立刻把玩家又弹回牌桌,表现为「退不出对局」。
  const prevStatus = room.value?.status
  room.value = next
  pickedGame.value = next.gameCode
  if (next.status === ROOM_STATUS.PLAYING && prevStatus === ROOM_STATUS.WAITING && !entered) {
    entered = true
    enterGame()
  }
}

const load = async ({ silent = false } = {}) => {
  if (!roomId.value) return null
  if (!silent) loading.value = true
  try {
    const data = await roomApi.get(roomId.value)
    // 未加入且还在等待:直接加入(邀请卡点进来的成员)
    if (data && data.status === ROOM_STATUS.WAITING && !data.members.some((m) => m.userId === myId.value)) {
      try {
        const joined = await roomApi.join(roomId.value)
        applyRoom(joined)
        return joined
      } catch (e) {
        // 满员/已开赛:留在大厅展示现状,不要吞成「加载失败」
        ElMessage.warning(e?.response?.data?.message || t('games.room.actFailed'))
      }
    }
    applyRoom(data)
    if (data?.status === ROOM_STATUS.PLAYING && !data.members.some((m) => m.userId === myId.value)) {
      ElMessage.warning(t('games.room.notJoined'))
      router.replace('/games')
      return null
    }
    return data
  } catch {
    if (!silent) ElMessage.warning(t('games.room.loadFailed'))
    return null
  } finally {
    if (!silent) loading.value = false
  }
}

const enterGame = () => {
  if (!room.value) return
  router.push(roomGamePath(room.value.gameCode, room.value.id))
}

const toggleReady = async () => {
  busy.value = true
  try {
    applyRoom(await roomApi.ready(roomId.value, !myReady.value))
  } catch {
    ElMessage.warning(t('games.room.actFailed'))
  } finally {
    busy.value = false
  }
}

const start = async () => {
  busy.value = true
  try {
    applyRoom(await roomApi.start(roomId.value))
  } catch (e) {
    ElMessage.warning(e?.response?.data?.message || t('games.room.actFailed'))
  } finally {
    busy.value = false
  }
}

const invite = () => {
  if (!room.value) return
  chatStore.ensureConnected()
  const payload = { gameCode: room.value.gameCode, roomId: room.value.id, mode: room.value.mode }
  const ok = chatStore.sendContent({
    msgType: 'GAME_INVITE',
    content: t('games.room.inviteContent', { name: gameName.value }),
    payload,
  })
  if (ok) ElMessage.success(t('games.room.inviteSent'))
  else ElMessage.warning(t('games.room.inviteFailed'))
}

const callMember = async (member, media) => {
  if (!canCall.value) {
    ElMessage.warning(t('games.room.callUnavailable'))
    return
  }
  const ok = await callStore.start({ id: member.userId, name: member.name }, media)
  if (!ok) ElMessage.warning(t('chat.call.startFailed'))
}

const leave = async () => {
  leaving.value = true
  try {
    if (room.value && room.value.status !== ROOM_STATUS.FINISHED) await roomApi.leave(roomId.value)
  } catch {
    // 已结束/已被移除时忽略
  } finally {
    leaving.value = false
    router.push('/games')
  }
}

// 原地重开:保留成员与席位、重置成绩,再点「开始」即可;不用重建房间重新邀请
const again = async () => {
  busy.value = true
  try {
    entered = false
    applyRoom(await roomApi.again(roomId.value))
  } catch (e) {
    ElMessage.warning(e?.response?.data?.message || t('games.room.actFailed'))
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  chatStore.ensureConnected()
  unsubscribe = chatStore.onGameSignal((data) => {
    if (!data || Number(data.roomId) !== roomId.value || data.event !== 'room') return
    applyRoom(data.room)
  })
  await load()
  pollTimer = setInterval(() => {
    if (room.value?.status === ROOM_STATUS.WAITING) load({ silent: true })
  }, 5000)
})

onBeforeUnmount(() => {
  unsubscribe?.()
  unsubscribe = null
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
})

watch(roomId, () => {
  entered = false
  load()
})
</script>

<style scoped>
.room-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 200px;
}
.room-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.room-game {
  font-size: 22px;
  font-weight: 700;
}
.room-game-picker {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.room-game-select {
  width: 200px;
}
.room-picker-hint {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.room-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}
.room-tag {
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  background: rgba(0, 0, 0, 0.06);
  color: var(--color-text-secondary);
}
.room-tag.mode {
  background: rgba(var(--color-brand-rgb), 0.14);
  color: var(--color-accent);
  font-weight: 600;
}
.room-tag.seed {
  font-variant-numeric: tabular-nums;
}
.room-tag.waiting {
  background: rgba(230, 162, 60, 0.16);
  color: #b9791f;
}
.room-tag.playing {
  background: rgba(103, 194, 58, 0.16);
  color: #4b8f2a;
}
.room-tag.finished {
  background: rgba(var(--color-brand-rgb), 0.16);
  color: var(--color-accent);
}
.room-desc {
  margin-top: 10px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.room-hero-actions {
  display: flex;
  gap: 8px;
}
.room-members {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}
.room-member {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  min-height: 74px;
}
.room-member.host {
  border: 1px solid rgba(var(--color-brand-rgb), 0.5);
}
.room-member.empty {
  border-style: dashed;
  color: var(--color-text-secondary);
  justify-content: center;
  gap: 6px;
  font-size: 13px;
  background: transparent;
}
.room-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(var(--color-brand-rgb), 0.14);
  color: var(--color-accent);
  flex: none;
  overflow: hidden;
}
.room-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.room-member-main {
  min-width: 0;
  flex: 1;
}
.room-member-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.room-badge {
  padding: 0 6px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 600;
  background: rgba(var(--color-brand-rgb), 0.16);
  color: var(--color-accent);
}
.room-badge.me {
  background: rgba(0, 0, 0, 0.08);
  color: var(--color-text-secondary);
}
.room-badge.spectate {
  background: rgba(0, 0, 0, 0.08);
  color: var(--color-text-secondary);
}
.room-invite-wrap {
  display: inline-flex;
}
.room-member-state {
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.room-member-call {
  display: flex;
  gap: 6px;
  flex: none;
}
.room-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding: 4px 2px 20px;
}
.room-hint {
  font-size: 13px;
  color: var(--color-text-secondary);
}
.room-result {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-right: auto;
}
.room-result-title {
  font-size: 16px;
  font-weight: 700;
}
.room-result-list {
  font-size: 13px;
  color: var(--color-text-secondary);
}
.room-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 48px 20px;
}
.room-empty-text {
  color: var(--color-text-secondary);
  font-size: 14px;
}
@media (max-width: 640px) {
  .room-hero-actions {
    width: 100%;
  }
}
</style>
