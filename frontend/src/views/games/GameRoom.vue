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
            <!-- 玩法 tag 在游戏选择框前(对抗/合作);房主等待中可换游戏与玩法,
                 换游戏按进房顺序重排席位(不踢人),只换玩法不动席位 -->
            <div v-if="isHost && room.status === 'WAITING'" class="room-game-picker">
              <el-radio-group v-model="pickedMode" size="small" class="room-mode-picker" @change="changeMode">
                <el-radio-button v-for="m in modeOptions" :key="m" :value="m">
                  {{ m === 'COOP' ? $t('games.room.modeCoop') : $t('games.room.modePk') }}
                </el-radio-button>
              </el-radio-group>
              <el-select v-model="pickedGame" size="default" class="room-game-select" :loading="switchingGame" @change="changeGame">
                <el-option v-for="o in roomOptions" :key="o.code" :label="$t(o.nameKey)" :value="o.code" />
              </el-select>
              <span class="room-picker-hint">{{ $t('games.room.gamePickerHint') }}</span>
            </div>
            <template v-else>
              <div class="room-game">{{ gameName }}</div>
              <div class="room-tags">
                <span class="room-tag mode">{{ modeText }}</span>
              </div>
            </template>
            <div class="room-tags">
              <span class="room-tag" :class="statusClass">{{ statusText }}</span>
              <span class="room-tag">{{ seatText }}</span>
            </div>
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
          <!-- 参战成员(房主 + 玩家) -->
          <div v-for="m in seatView.players" :key="m.userId" class="room-member card" :class="{ host: m.userId === room.hostId }">
            <div class="room-avatar">
              <img v-if="m.avatar" :src="m.avatar" :alt="m.name" />
              <el-icon v-else :size="22"><UserFilled /></el-icon>
              <RoomChatBubble :text="roomChat.bubbleOf(room.id, m.userId)" />
            </div>
            <div class="room-member-main">
              <div class="room-member-name">
                {{ m.name }}
                <span v-if="m.userId === room.hostId" class="room-badge">{{ $t('games.room.host') }}</span>
                <span v-if="m.userId === myId" class="room-badge me">{{ $t('games.room.me') }}</span>
              </div>
              <div class="room-member-state">
                <template v-if="room.status === 'WAITING'">
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
          <!-- 空参战席:有电脑玩家的游戏自动补位,否则等加入 -->
          <div v-for="n in seatView.emptyPlayerSeats" :key="'bot' + n" class="room-member card" :class="gameSupportsAi ? 'bot' : 'empty'">
            <template v-if="gameSupportsAi">
              <div class="room-avatar bot-avatar">
                <el-icon :size="20"><Monitor /></el-icon>
              </div>
              <div class="room-member-main">
                <div class="room-member-name">
                  {{ $t('games.room.botName', { n }) }}
                  <span class="room-badge bot">{{ $t('games.room.botBadge') }}</span>
                </div>
                <div class="room-member-state">{{ $t('games.room.botState') }}</div>
              </div>
            </template>
            <template v-else>
              <el-icon :size="20"><Plus /></el-icon>
              <span>{{ $t('games.room.emptySeat') }}</span>
            </template>
          </div>
          <!-- 观战成员 -->
          <div v-for="m in seatView.spectators" :key="'sp' + m.userId" class="room-member card">
            <div class="room-avatar">
              <img v-if="m.avatar" :src="m.avatar" :alt="m.name" />
              <el-icon v-else :size="22"><UserFilled /></el-icon>
              <RoomChatBubble :text="roomChat.bubbleOf(room.id, m.userId)" />
            </div>
            <div class="room-member-main">
              <div class="room-member-name">
                {{ m.name }}
                <span class="room-badge spectate">{{ $t('games.room.roleSpectator') }}</span>
                <span v-if="m.userId === myId" class="room-badge me">{{ $t('games.room.me') }}</span>
              </div>
              <div class="room-member-state">{{ $t('games.room.spectating') }}</div>
            </div>
            <div v-if="m.userId !== myId && room.status !== 'FINISHED'" class="room-member-call">
              <el-tooltip :content="$t('games.room.callAudio')" placement="top">
                <el-button circle size="small" :disabled="!canCall" @click="callMember(m, 'AUDIO')">
                  <el-icon><Phone /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
            <!-- 观战者申请与参战玩家换位(满员时的上位通道;房主卡与观战卡不显示) -->
            <div v-if="canOfferSwapTo(m)" class="room-member-call">
              <el-tooltip :content="$t('games.room.swapRequest')" placement="top">
                <el-button circle size="small" :disabled="busy" @click="offerSwap(m)">
                  <el-icon><Switch /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
          </div>
          <!-- 空观战席:房间容量的剩余空位 -->
          <div v-for="n in seatView.emptySpectatorSeats" :key="'es' + n" class="room-member empty card">
            <el-icon :size="20"><Plus /></el-icon>
            <span>{{ $t('games.room.spectatorSeat') }}</span>
          </div>
        </div>

        <div class="room-actions">
          <template v-if="room.status === 'WAITING'">
            <!-- 参战成员:准备 + 随时转观战让席 + 自动准备 -->
            <template v-if="!isHost && !myIsSpectator">
              <el-checkbox v-model="autoReady" class="auto-ready">{{ $t('games.room.autoReady') }}</el-checkbox>
              <el-button size="default" round :type="myReady ? 'default' : 'primary'" :loading="busy" @click="toggleReady">
                {{ myReady ? $t('games.room.cancelReady') : $t('games.room.setReady') }}
              </el-button>
              <el-button size="default" round :disabled="busy" @click="switchMyRole('SPECTATOR')">
                {{ $t('games.room.toSpectator') }}
              </el-button>
            </template>
            <!-- 观战成员:有空位直切参战,满员去成员卡申请换位 -->
            <template v-else-if="!isHost">
              <el-button v-if="canTakeSeat" size="default" round type="primary" :loading="busy" @click="switchMyRole('PLAYER')">
                {{ $t('games.room.toPlayer') }}
              </el-button>
              <span v-else class="room-hint">{{ $t('games.room.seatFullHint') }}</span>
            </template>
            <el-button v-else type="primary" size="default" round :disabled="!canStart" :loading="busy" @click="start">
              {{ $t('games.room.start') }}
            </el-button>
            <span v-if="isHost" class="room-hint">{{ startHint }}</span>
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
            <!-- 局终调整席位:下一局换人打/换人看 -->
            <el-button v-if="!isHost && !myIsSpectator" size="default" round :disabled="busy" @click="switchMyRole('SPECTATOR')">
              {{ $t('games.room.toSpectator') }}
            </el-button>
            <el-button v-else-if="!isHost && canTakeSeat" size="default" round :loading="busy" @click="switchMyRole('PLAYER')">
              {{ $t('games.room.toPlayer') }}
            </el-button>
            <el-button size="default" round @click="router.push('/games')">{{ $t('games.room.backGames') }}</el-button>
          </template>
          <template v-else>
            <span class="room-hint">{{ $t('games.room.cancelled') }}</span>
            <el-button size="default" round @click="router.push('/games')">{{ $t('games.room.backGames') }}</el-button>
          </template>
          <!-- 换位申请条:目标玩家处理,发起者可撤回(对局中不展示,后端同样拒绝) -->
          <template v-if="pendingSwap && canSelfSwitch">
            <template v-if="pendingSwap.targetUserId === myId">
              <span class="room-swap-incoming">{{ $t('games.room.swapIncoming', { name: swapFromName }) }}</span>
              <el-button type="primary" size="default" round :loading="busy" @click="acceptSwapAct">
                {{ $t('games.room.swapAccept') }}
              </el-button>
              <el-button size="default" round :loading="busy" @click="cancelSwapAct">
                {{ $t('games.room.swapDecline') }}
              </el-button>
            </template>
            <template v-else-if="pendingSwap.userId === myId">
              <span class="room-hint">{{ $t('games.room.swapPending', { name: swapTargetName }) }}</span>
              <el-button size="small" round :loading="busy" @click="cancelSwapAct">
                {{ $t('games.room.swapCancel') }}
              </el-button>
            </template>
          </template>
        </div>

        <!-- 房间排行榜:只统计本房间的各局成绩,跨「再来一局」累计 -->
        <div v-if="board && board.rounds && board.rounds.length" class="room-board card">
          <div class="board-head">
            <span class="section-label">{{ $t('games.room.boardTitle') }}</span>
            <span class="board-rounds">{{ $t('games.room.boardRounds', { n: board.roundsCount || 0 }) }}</span>
          </div>
          <div class="board-ranking">
            <div v-for="(r, i) in board.ranking" :key="r.userId" class="board-row" :class="{ top: i === 0 }">
              <span class="board-rank">{{ i + 1 }}</span>
              <span class="board-name">{{ r.name }}</span>
              <span class="board-wins">{{ $t('games.room.boardWins', { n: r.wins }) }}</span>
              <span class="board-score">{{ $t('games.room.boardScore', { n: r.totalScore }) }}</span>
            </div>
          </div>
          <div v-if="lastRoundLine" class="board-last">{{ lastRoundLine }}</div>
        </div>

        <!-- 邀请:点「邀请」弹出多选(成员走私聊,家庭走对方聊天流) -->
        <RoomInvitePicker v-model:visible="inviteOpen" :room-id="room ? room.id : null" />
      </template>
    </div>
  </div>
</template>

<script setup>
// 「一起玩」房间大厅:成员/准备(含自动准备)/开赛/邀请/1:1 通话/自动进游戏。
// 点「一起玩」直进房(Games.vue 用默认游戏建房),选游戏/玩法都在这里完成;
// 房主换游戏按进房顺序重排席位(不踢人),只换玩法不动席位。
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Monitor, Phone, Plus, Promotion, Switch, UserFilled, VideoCamera } from '@element-plus/icons-vue'
import Breadcrumb from '@/components/Breadcrumb.vue'
import RoomCallPicker from '@/components/games/RoomCallPicker.vue'
import RoomInvitePicker from '@/components/games/RoomInvitePicker.vue'
import RoomChatBubble from '@/components/games/RoomChatBubble.vue'
import { roomApi } from '@/api'
import { useChatStore } from '@/stores/chat'
import { useCallStore } from '@/stores/call'
import { useRoomChatStore } from '@/stores/roomChat'
import { useRoomChatHost } from '@/composables/useRoomChatHost'
import { useUserStore } from '@/stores/user'
import {
  ROOM_GAMES,
  ROOM_ROLE,
  ROOM_STATUS,
  canSwitchRole,
  canTakePlayerSeat,
  isCoop,
  isSpectatorRole,
  roomGame,
  roomGamePath,
  roomHasAi,
  roomModes,
  seatLayout,
} from '@/utils/gameRoom'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const chatStore = useChatStore()
const callStore = useCallStore()
const roomChat = useRoomChatStore()
const userStore = useUserStore()

const roomId = computed(() => Number(route.params.id) || 0)
const room = ref(null)
const loading = ref(false)
const busy = ref(false)
const leaving = ref(false)
const switchingGame = ref(false)
const pickedGame = ref('')
const pickedMode = ref('')
const inviteOpen = ref(false)
const board = ref(null)
let entered = false
let unsubscribe = null
let pollTimer = null

// 房间临时聊天复用全局聊天浮窗:大厅只登记房间,浮窗据此多出「房间」页(大厅页与对局页同一条会话)
useRoomChatHost(room)

const myId = computed(() => userStore.userInfo?.id)
const isHost = computed(() => room.value?.hostId === myId.value)
const myReady = computed(() => !!room.value?.members.find((m) => m.userId === myId.value)?.ready)
const myIsSpectator = computed(() => isSpectatorRole(room.value?.members.find((m) => m.userId === myId.value)?.role))
const isSpectatorMember = (m) => isSpectatorRole(m?.role)
const roomOptions = computed(() => Object.values(ROOM_GAMES))
const gameName = computed(() => (room.value ? t(roomGame(room.value.gameCode)?.nameKey || 'games.title') : t('games.room.title')))
const roomTitle = computed(() => (room.value ? gameName.value : t('games.room.title')))
const modeText = computed(() => (isCoop(room.value?.mode) ? t('games.room.modeCoop') : t('games.room.modePk')))
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
/** 席位视图:玩家→空参战席(有 AI 补电脑)→观战→空观战席;席位数量跟游戏走 */
const seatView = computed(() => seatLayout(room.value))
const gameSupportsAi = computed(() => roomHasAi(room.value?.gameCode))
/** 当前游戏的玩法选项(房主切换用) */
const modeOptions = computed(() => (room.value ? roomModes(room.value.gameCode) : []))
/** 玩家(非观战)都就绪才能开赛;有电脑玩家的游戏 1 名真人即可;观战不要求准备 */
const players = computed(() => (room.value?.members || []).filter((m) => !isSpectatorMember(m)))
const canStart = computed(() => players.value.length >= (gameSupportsAi.value ? 1 : 2) && players.value.every((m) => m.ready))
const startHint = computed(() => {
  if (!room.value) return ''
  const min = gameSupportsAi.value ? 1 : 2
  if (players.value.length < min) return t('games.room.needTwoPlayers')
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

// ---------- 自助换位(参战⇄观战) ----------
const canSelfSwitch = computed(() => canSwitchRole(room.value, myId.value))
const canTakeSeat = computed(() => canTakePlayerSeat(room.value))
const pendingSwap = computed(() => room.value?.pendingSwap || null)
const swapFromName = computed(() => (room.value?.members || []).find((m) => m.userId === pendingSwap.value?.userId)?.name || '')
const swapTargetName = computed(() => (room.value?.members || []).find((m) => m.userId === pendingSwap.value?.targetUserId)?.name || '')
/** 自己是观战且无待处理申请时,可向参战玩家(非房主)发换位申请 */
const canOfferSwapTo = (m) =>
  !!room.value && canSelfSwitch.value && myIsSpectator.value && !pendingSwap.value && m.role === ROOM_ROLE.PLAYER && m.userId !== myId.value

const switchMyRole = async (role) => {
  busy.value = true
  try {
    applyRoom(await roomApi.switchRole(roomId.value, role))
  } catch (e) {
    ElMessage.warning(e?.response?.data?.message || t('games.room.actFailed'))
  } finally {
    busy.value = false
  }
}

const offerSwap = async (m) => {
  busy.value = true
  try {
    applyRoom(await roomApi.requestSwap(roomId.value, m.userId))
  } catch (e) {
    ElMessage.warning(e?.response?.data?.message || t('games.room.actFailed'))
  } finally {
    busy.value = false
  }
}

const acceptSwapAct = async () => {
  busy.value = true
  try {
    applyRoom(await roomApi.acceptSwap(roomId.value))
  } catch (e) {
    ElMessage.warning(e?.response?.data?.message || t('games.room.actFailed'))
  } finally {
    busy.value = false
  }
}

/** 拒绝与取消同一个接口:发起者或目标都可撤 */
const cancelSwapAct = async () => {
  busy.value = true
  try {
    applyRoom(await roomApi.cancelSwap(roomId.value))
  } catch (e) {
    ElMessage.warning(e?.response?.data?.message || t('games.room.actFailed'))
  } finally {
    busy.value = false
  }
}

/** 房主换游戏:按进房顺序重排席位(不踢人),广播后全员更新 */
const changeGame = async (code) => {
  if (!code || !room.value || code === room.value.gameCode) return
  switchingGame.value = true
  try {
    // 换游戏时玩法回落到新游戏的缺省(后端校验 + 缺省口径一致)
    applyRoom(await roomApi.setGame(roomId.value, code))
  } catch (e) {
    ElMessage.warning(e?.response?.data?.message || t('games.room.actFailed'))
    pickedGame.value = room.value.gameCode // 回弹到当前游戏
  } finally {
    switchingGame.value = false
  }
}

/** 房主只换玩法:不动席位与 seed(PK⇄COOP 不打散座位) */
const changeMode = async (mode) => {
  if (!mode || !room.value || mode === room.value.mode) return
  switchingGame.value = true
  try {
    applyRoom(await roomApi.setGame(roomId.value, room.value.gameCode, mode))
  } catch (e) {
    ElMessage.warning(e?.response?.data?.message || t('games.room.actFailed'))
    pickedMode.value = room.value.mode
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
  pickedMode.value = next.mode
  if (next.status === ROOM_STATUS.FINISHED && prevStatus !== ROOM_STATUS.FINISHED) {
    loadBoard()
  }
  if (next.status === ROOM_STATUS.PLAYING && prevStatus === ROOM_STATUS.WAITING && !entered) {
    entered = true
    enterGame()
  }
}

// ---------- 自动准备(勾选后等待中自动就绪,勾选状态记本机) ----------
const AUTO_READY_KEY = 'ihomy:room:autoReady'
const autoReady = ref(false)
try {
  autoReady.value = localStorage.getItem(AUTO_READY_KEY) === '1'
} catch {}
watch(autoReady, (v) => {
  try {
    localStorage.setItem(AUTO_READY_KEY, v ? '1' : '0')
  } catch {}
  if (v) maybeAutoReady()
})
/** 自动准备:等待中、我是玩家、还没准备 → 立即补一次准备 */
const maybeAutoReady = async () => {
  if (!autoReady.value || !room.value) return
  if (room.value.status !== ROOM_STATUS.WAITING || isHost.value || myIsSpectator.value || myReady.value) return
  try {
    applyRoom(await roomApi.ready(roomId.value, true))
  } catch {}
}
watch(
  () => [room.value?.status, room.value?.members?.length, myReady.value],
  () => maybeAutoReady(),
)

// ---------- 房间排行榜(只统计本房间) ----------
const loadBoard = async () => {
  if (!roomId.value) return
  try {
    board.value = await roomApi.board(roomId.value)
  } catch {
    board.value = null
  }
}
const lastRoundLine = computed(() => {
  const rounds = board.value?.rounds || []
  if (!rounds.length) return ''
  const lastNo = rounds[rounds.length - 1].roundNo
  const line = rounds
    .filter((r) => r.roundNo === lastNo)
    .map((r) => `${r.name} ${r.score ?? 0}`)
    .join(' · ')
  return t('games.room.boardLastRound', { n: lastNo, list: line })
})

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

/** 邀请:打开多选弹窗(成员走私聊、家庭走对方聊天流),不在建房/点按钮时静默发卡 */
const invite = () => {
  if (!room.value) return
  chatStore.ensureConnected()
  inviteOpen.value = true
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
  loadBoard()
  maybeAutoReady()
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
.room-hero-actions {
  display: flex;
  gap: 8px;
}
.room-mode-picker {
  flex: none;
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
.room-member.bot {
  border-style: dashed;
  background: transparent;
}
.bot-avatar {
  background: rgba(0, 0, 0, 0.08);
  color: var(--color-text-secondary);
}
.room-badge.bot {
  background: rgba(0, 0, 0, 0.08);
  color: var(--color-text-secondary);
}
.auto-ready {
  height: auto;
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
  /* 不能裁切:房间聊天气泡从头像上方弹出 */
  position: relative;
}
.room-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  border-radius: 50%;
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
.room-swap-incoming {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-accent);
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
.room-board {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
}
.board-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.board-rounds {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.board-ranking {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.board-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}
.board-row.top {
  color: var(--color-accent);
  font-weight: 600;
}
.board-rank {
  flex: none;
  width: 20px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}
.board-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.board-wins,
.board-score {
  flex: none;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-secondary);
}
.board-row.top .board-wins,
.board-row.top .board-score {
  color: inherit;
}
.board-last {
  font-size: 12px;
  color: var(--color-text-secondary);
  border-top: 1px dashed var(--color-border);
  padding-top: 8px;
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
