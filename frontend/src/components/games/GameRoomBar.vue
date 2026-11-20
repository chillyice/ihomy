<template>
  <!-- 房间临时聊天不再单独起浮球:这里只登记房间,聊天由全局聊天浮窗(ChatFloat 的「房间」页)承载 -->
  <template v-if="room">
    <div ref="rootEl" class="room-bar" :class="{ coop: isCoopMode, ended }">
      <div class="room-bar-inner">
        <div class="room-bar-main">
          <span class="room-bar-tag">{{ isCoopMode ? $t('games.room.modeCoop') : $t('games.room.modePk') }}</span>
          <span v-if="isSpectator" class="room-bar-tag spectate">{{ $t('games.room.spectating') }}</span>
          <!-- 成员头像:房间聊天气泡就弹在自己的头像上 -->
          <span class="room-bar-members">
            <span v-for="m in room.members" :key="m.userId" class="rb-member" :title="m.name">
              <span class="rb-avatar" :class="{ me: m.userId === myId }">
                <img v-if="m.avatar" :src="m.avatar" :alt="m.name" />
                <template v-else>{{ m.name?.charAt(0) }}</template>
              </span>
              <RoomChatBubble :text="roomChat.bubbleOf(room.id, m.userId)" />
            </span>
          </span>
          <span class="room-bar-players" :title="playerText">{{ playerText }}</span>
          <span class="room-bar-hint" :title="hintText">{{ hintText }}</span>
        </div>
        <div class="room-bar-actions">
          <RoomCallPicker v-if="!ended" :members="room.members" :my-id="myId" />
          <!-- 托管:有电脑玩家的游戏,对局中把自己的席位交给电脑(再点恢复) -->
          <el-button v-if="showAuto" size="small" round :type="autoOn ? 'warning' : 'default'" @click="toggleAuto">
            {{ autoOn ? $t('games.room.autoOff') : $t('games.room.autoOn') }}
          </el-button>
          <!-- 让位:对局中把席位让给指定观战者 -->
          <el-button v-if="showYield" size="small" round @click="yieldOpen = true">
            {{ $t('games.room.yieldSeat') }}
          </el-button>
          <el-button v-if="canSubmit && !isSpectator" size="small" type="primary" round :disabled="reported" @click="$emit('submit')">
            {{ reported ? $t('games.room.submitted') : $t('games.room.submitScore') }}
          </el-button>
          <!-- 房主收尾:有人挂机没交卷时不必干等,直接按当前成绩结算 -->
          <el-button v-if="isHost && !ended" size="small" round type="danger" plain @click="endRoom">
            {{ $t('games.room.endRoom') }}
          </el-button>
          <!-- 局终换位:参战随时可转观战;观战有空位可参战,满员回大厅申请换位 -->
          <el-tooltip v-if="canToggleSeat && isSpectator && !canTakeSeat" :content="$t('games.room.seatFull')" placement="top">
            <span class="room-bar-seat-wrap">
              <el-button size="small" round disabled>{{ $t('games.room.toPlayer') }}</el-button>
            </span>
          </el-tooltip>
          <el-button v-else-if="canToggleSeat" size="small" round :loading="switching" @click="toggleSeat">
            {{ isSpectator ? $t('games.room.toPlayer') : $t('games.room.toSpectator') }}
          </el-button>
          <!-- 退出对局:回到房间大厅保留成员身份,不再留在牌桌上 -->
          <el-button v-if="!ended" size="small" round @click="exitToHall">{{ $t('games.room.exitGame') }}</el-button>
          <el-button v-if="ended" size="small" round @click="$emit('leave')">{{ $t('games.room.backGames') }}</el-button>
          <!-- 结束自动回房:倒计时结束前也可直接点 -->
          <el-button v-if="returnCountdown > 0" size="small" type="primary" round @click="goHall">
            {{ $t('games.room.autoReturn', { n: returnCountdown }) }}
          </el-button>
        </div>
      </div>

      <!-- 让位:选择一名观战成员接管我的席位 -->
      <el-dialog v-model="yieldOpen" :title="$t('games.room.yieldSeat')" width="dialog-sm" append-to-body>
        <el-empty v-if="!spectators.length" :description="$t('games.room.yieldNoSpectator')" :image-size="60" />
        <div v-for="m in spectators" :key="m.userId" class="yield-row">
          <el-avatar :size="30">{{ m.name?.charAt(0) }}</el-avatar>
          <span class="yield-name">{{ m.name }}</span>
          <el-button size="small" type="primary" round :loading="yielding" @click="yieldTo(m)">
            {{ $t('games.room.yieldTo', { name: m.name }) }}
          </el-button>
        </div>
      </el-dialog>
    </div>
  </template>
</template>

<script setup>
// 游戏内「一起玩」横幅:玩法/对手/当前比分/交卷与结算出口 + 对局动作(托管/让位/语音/离开)。
// COOP 不发积分也不交卷(房主完成即结算);PK 可随时交卷,避免一局太久把房间挂住。
// 观战成员不显示交卷;对局中可一键退回房间大厅(保留席位)或快速发起语音。
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { roomApi } from '@/api'
import { canTakePlayerSeat, isSpectatorRole, resultKey, roomHasAi } from '@/utils/gameRoom'
import { useRoomChatStore } from '@/stores/roomChat'
import { useRoomChatHost } from '@/composables/useRoomChatHost'
import RoomCallPicker from '@/components/games/RoomCallPicker.vue'
import RoomChatBubble from '@/components/games/RoomChatBubble.vue'

const props = defineProps({
  room: { type: Object, default: null },
  isHost: { type: Boolean, default: false },
  score: { type: Number, default: 0 },
  reported: { type: Boolean, default: false },
  ended: { type: Boolean, default: false },
  result: { type: String, default: 'HIGH_SCORE' },
  myId: { type: [Number, String], default: null },
  /** 实时权威牌局(麻将):房主中途退出即结束房间,否则其他人会一直等不到操作 */
  exitEndsRoom: { type: Boolean, default: false },
})
const emit = defineEmits(['submit', 'leave', 'auto'])

const { t } = useI18n()
const router = useRouter()
const roomChat = useRoomChatStore()

// 房间临时聊天复用全局聊天浮窗:横幅只登记当前房间,浮窗据此多出「房间」页
useRoomChatHost(() => props.room)

const isCoopMode = computed(() => props.room?.mode === 'COOP')
const isSpectator = computed(() => isSpectatorRole((props.room?.members || []).find((m) => m.userId === props.myId)?.role))
const canSubmit = computed(() => !!props.room && props.room.status === 'PLAYING' && !isCoopMode.value && props.result === 'HIGH_SCORE')
// 局终(FINISHED)可在横幅直切席位;对局中与已取消不提供,换位申请只在大厅
const canToggleSeat = computed(() => !!props.room && props.room.status === 'FINISHED' && !props.isHost)
const canTakeSeat = computed(() => canTakePlayerSeat(props.room))
const switching = ref(false)

// 实测横幅高度写进父容器 CSS 变量 --room-bar-h:麻将/飞行棋/数独这类自管高度的游戏页
// 用它扣减盘面尺寸公式,横幅出现、换行变高、消失都不至于把页面顶出一屏。
// 变量写在游戏卡(本组件的 parentElement)上,卡片 :fullscreen 时同样生效。
const rootEl = ref(null)
let barObserver = null
let barHost = null
const syncBarHeight = () => {
  const el = rootEl.value
  if (!el?.parentElement) return
  barHost = el.parentElement
  const marginBottom = parseFloat(getComputedStyle(el).marginBottom) || 0
  barHost.style.setProperty('--room-bar-h', `${Math.ceil(el.offsetHeight + marginBottom)}px`)
}
// 窗口 resize 兜底:横幅换行增高多由视口变化引起(转屏/分屏),个别 WebView 的
// ResizeObserver 不派发回调时也能跟上;元素自身变化(房间结束加按钮等)仍靠 RO。
const onWinResize = () => syncBarHeight()
watch(rootEl, (el) => {
  barObserver?.disconnect()
  barObserver = null
  if (!el) {
    // 横幅随房间清空而消失:摘掉残留变量,页面公式回落
    barHost?.style.removeProperty('--room-bar-h')
    barHost = null
    return
  }
  barObserver = new ResizeObserver(syncBarHeight)
  barObserver.observe(el)
  window.addEventListener('resize', onWinResize, { passive: true })
  // 首帧样式/字体晚到会让首测值偏高(容器查询/换行收窄后真实高度变小),
  // 按 useGameView.settleFit 同款思路补测几次收敛
  for (const delay of [80, 200, 500, 1000]) setTimeout(syncBarHeight, delay)
  syncBarHeight()
})
onBeforeUnmount(() => {
  barObserver?.disconnect()
  barObserver = null
  window.removeEventListener('resize', onWinResize)
  barHost?.style.removeProperty('--room-bar-h')
  barHost = null
})

/** 直切席位:状态刷新靠房间广播(endRoom 同模式) */
const toggleSeat = async () => {
  switching.value = true
  try {
    await roomApi.switchRole(props.room.id, isSpectator.value ? 'PLAYER' : 'SPECTATOR')
  } catch (e) {
    ElMessage.warning(e?.response?.data?.message || t('games.room.actFailed'))
  } finally {
    switching.value = false
  }
}
const playerText = computed(() => {
  const list = props.room?.members || []
  return list.map((m) => `${m.name}${m.userId === props.myId ? `(${t('games.room.me')})` : ''}`).join(' · ')
})
const hintText = computed(() => {
  if (!props.room) return ''
  if (props.room.status === 'FINISHED') {
    if (isCoopMode.value) return t('games.room.coopDone')
    const winner = props.room.members.find((m) => m.userId === props.room.winnerId)
    return winner ? t('games.room.winner', { name: winner.name }) : t('games.room.draw')
  }
  if (props.room.status === 'CANCELLED') return t('games.room.cancelled')
  if (isSpectator.value) return t('games.room.spectatorHint')
  if (isCoopMode.value) return t('games.room.coopBoard')
  return `${t('games.room.myScore', { n: props.score })} · ${t(resultKey(props.room.gameCode))}`
})

/** 退出对局:普通游戏回房间大厅(保留席位,大厅里可再进) */
const exitToHall = async () => {
  if (props.exitEndsRoom && props.room?.status === 'PLAYING') {
    // 实时权威牌局(麻将):房主退出即结束;访客退出则离开房间,人位交给 AI,
    // 否则房主每回合都要等这个已经不在牌桌前的人超时。
    try {
      await (props.isHost ? roomApi.end(props.room.id) : roomApi.leave(props.room.id))
    } catch {
      // 房间已结束时忽略
    }
    router.push(props.isHost ? `/games/room/${props.room.id}` : '/games')
    return
  }
  if (props.room?.id) router.push(`/games/room/${props.room.id}`)
}

/** 房主结束房间:按当前成绩结算(避免有人挂机时整桌卡住) */
const endRoom = async () => {
  if (!props.room?.id) return
  try {
    await ElMessageBox.confirm(t('games.room.endRoomConfirm'), t('games.room.endRoom'), { type: 'warning' })
  } catch {
    return
  }
  try {
    await roomApi.end(props.room.id)
  } catch (e) {
    ElMessage.warning(e?.response?.data?.message || t('games.room.actFailed'))
  }
}

// ---------- 对局动作:托管 / 让位 / 自动回房 ----------

/** 托管:仅有电脑玩家的游戏,对局中参战者可用(观战没有可托管的事) */
const isPlaying = computed(() => props.room?.status === 'PLAYING')
const showAuto = computed(() => roomHasAi(props.room?.gameCode) && isPlaying.value && !props.ended && !isSpectator.value)
const autoOn = ref(false)
const toggleAuto = () => {
  autoOn.value = !autoOn.value
  emit('auto', autoOn.value)
}
watch(
  () => props.room?.status,
  (s) => {
    if (s !== 'PLAYING') autoOn.value = false
  },
)

/** 让位:对局中把自己的席位让给指定观战者(REST 换角色,广播后游戏页把席位接管过去) */
const spectators = computed(() => (props.room?.members || []).filter((m) => isSpectatorRole(m.role)))
const showYield = computed(() => isPlaying.value && !props.ended && !isSpectator.value && spectators.value.length > 0)
const yieldOpen = ref(false)
const yielding = ref(false)
const yieldTo = async (m) => {
  if (!props.room?.id || yielding.value) return
  yielding.value = true
  try {
    await roomApi.yieldSeat(props.room.id, m.userId)
    yieldOpen.value = false
    ElMessage.success(t('games.room.yieldDone', { name: m.name }))
  } catch (e) {
    ElMessage.warning(e?.response?.data?.message || t('games.room.actFailed'))
  } finally {
    yielding.value = false
  }
}

/** 一局结束自动回房间大厅(倒计时 4s,期间可提前点) */
const AUTO_RETURN_SECONDS = 4
const returnCountdown = ref(0)
let returnTimer = null
const stopReturn = () => {
  if (returnTimer) {
    clearInterval(returnTimer)
    returnTimer = null
  }
  returnCountdown.value = 0
}
const goHall = () => {
  stopReturn()
  if (props.room?.id) router.push(`/games/room/${props.room.id}`)
}
watch(
  () => props.room?.status,
  (s, prev) => {
    if (s === 'FINISHED' && prev === 'PLAYING') {
      stopReturn()
      returnCountdown.value = AUTO_RETURN_SECONDS
      returnTimer = setInterval(() => {
        returnCountdown.value -= 1
        if (returnCountdown.value <= 0) goHall()
      }, 1000)
    } else if (s !== 'FINISHED') {
      stopReturn()
    }
  },
)
onBeforeUnmount(stopReturn)
</script>

<style scoped>
/* 横幅自身是 inline-size 容器:窄卡(竖屏手机)下内部自动紧凑化;
   任何换行增高都会被 --room-bar-h 实测变量如实上报,游戏页公式扣减后仍保一屏。
   align-self:stretch —— 数独卡 align-items:center,不加会连 inline-size 包裹一起塌成 2px 宽 */
.room-bar {
  container-type: inline-size;
  align-self: stretch;
  margin-bottom: 10px;
  border-radius: 12px;
  border: 1px solid rgba(var(--color-brand-rgb), 0.3);
  background: rgba(var(--color-brand-rgb), 0.08);
  font-size: 13px;
}
.room-bar-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px 10px;
  flex-wrap: wrap;
  padding: 8px 14px;
}
@container (max-width: 560px) {
  .room-bar-inner {
    padding: 6px 10px;
    gap: 6px 8px;
  }
  /* 玩家名单与状态提示压缩成省略号,完整内容走 title */
  .room-bar-main {
    flex: 1 1 auto;
    gap: 6px 8px;
  }
  .room-bar-players,
  .room-bar-hint {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .room-bar-actions {
    flex-wrap: wrap;
    justify-content: flex-end;
  }
}
.room-bar.coop {
  border-color: rgba(103, 194, 58, 0.4);
  background: rgba(103, 194, 58, 0.1);
}
.room-bar.ended {
  border-color: var(--color-border);
  background: rgba(0, 0, 0, 0.04);
}
.room-bar-main {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  min-width: 0;
}
.room-bar-tag {
  padding: 1px 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  background: rgba(var(--color-brand-rgb), 0.16);
  color: var(--color-accent);
  flex: none;
}
.room-bar-tag.spectate {
  background: rgba(0, 0, 0, 0.08);
  color: var(--color-text-secondary);
}
.room-bar-players {
  font-weight: 600;
}
/* 成员头像:24px 圆片,房间聊天气泡挂在这上面 */
.room-bar-members {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: none;
}
.rb-member {
  position: relative;
  z-index: 1;
  display: inline-flex;
}
.rb-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: 1px solid var(--color-border);
  background: rgba(var(--color-brand-rgb), 0.14);
  color: var(--color-accent);
  font-size: 11px;
  font-weight: 600;
  overflow: hidden;
}
.rb-avatar.me {
  border-color: var(--color-accent);
}
.rb-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.room-bar-hint {
  color: var(--color-text-secondary);
}
.room-bar-actions {
  display: flex;
  gap: 8px;
  flex: none;
}
.room-bar-seat-wrap {
  display: inline-flex;
}
.yield-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 0;
}
.yield-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
