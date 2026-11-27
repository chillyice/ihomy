<!-- 多人音视频通话界面(家庭内 mesh):来电响铃 / 通话中全屏 / 最小化小窗(可拖拽)。
     视频双布局:宫格并列(全员等分)与主次(一大 N 小,点任一路切主),语音为头像阵列。
     媒体与状态机在 stores/call.js;协议见 docs/设计想法/功能/多人通话-计划.md。
     挂 App.vue(懒加载),内部自守卫登录态;整层 Teleport 到 body,定位用显式 top/right/bottom/left
     (构建目标 safari14 不支持 inset 简写,旧 iPad 上会塌成顶部小条) -->
<template>
  <Teleport to="body">
    <template v-if="userStore.isLoggedIn">
      <!-- 最小化小窗(通话中收起,整窗可拖拽):显示主画面一路 -->
      <div v-if="call.status === 'active' && call.minimized" class="call-mini" :style="miniStyle" @pointerdown="onDragStart">
        <video
          v-if="call.isVideo && call.remoteStream"
          ref="miniVideoEl"
          playsinline
          webkit-playsinline
          autoplay
          class="call-mini-video"
        ></video>
        <div v-else class="call-mini-audio">
          <el-icon :size="20"><Phone /></el-icon>
          <span class="mini-name">{{ miniTitle }}</span>
        </div>
        <div class="call-mini-bar">
          <el-button text circle size="small" @click.stop="call.minimized = false">
            <el-icon><FullScreen /></el-icon>
          </el-button>
          <el-button text circle size="small" type="danger" @click.stop="call.hangup()">
            <el-icon><SwitchButton /></el-icon>
          </el-button>
        </div>
      </div>

      <!-- 全屏覆盖层:来电 / 连接中 / 通话中 -->
      <div v-else-if="call.status !== 'idle' && call.status !== 'ended' && !call.minimized" class="call-overlay">
        <!-- 来电响铃:整屏接管,大头像 + 脉冲光环 + 底部大按钮 -->
        <div v-if="call.status === 'ringing-in'" class="call-ring">
          <div class="ring-avatar-wrap">
            <span class="ring-pulse"></span>
            <span class="ring-pulse ring-pulse-late"></span>
            <el-avatar :size="96" class="ring-avatar">{{ call.callerName?.charAt(0) }}</el-avatar>
          </div>
          <el-icon :size="24" class="ring-type-icon" :class="{ shake: true }">
            <Phone v-if="!call.isVideo" />
            <VideoCamera v-else />
          </el-icon>
          <div class="ring-name">{{ call.callerName || $t('chat.call.member') }}</div>
          <div class="ring-hint">
            {{
              call.isGroup
                ? $t('chat.call.incoming.group', { name: call.callerName, n: call.ringCount })
                : $t(`chat.call.incoming.${call.isVideo ? 'video' : 'audio'}`)
            }}
          </div>
          <div class="ring-actions">
            <div class="ring-action">
              <el-button circle size="large" type="danger" class="ring-btn" @click="reject">
                <el-icon :size="26"><Close /></el-icon>
              </el-button>
              <span class="ring-action-label">{{ $t('chat.call.reject') }}</span>
            </div>
            <div class="ring-action">
              <el-button circle size="large" type="primary" class="ring-btn" @click="call.accept()">
                <el-icon :size="26"><Phone /></el-icon>
              </el-button>
              <span class="ring-action-label">{{ $t('chat.call.accept') }}</span>
            </div>
          </div>
        </div>

        <!-- 通话主体:视频=宫格/主次双布局;语音=头像阵列 -->
        <template v-else>
          <div class="call-stage" :class="['is-' + effectiveLayout, call.isVideo ? 'is-video' : 'is-audio']">
            <div
              v-for="tile in tiles"
              :key="tile.key"
              class="call-tile"
              :class="{ 'is-primary': effectiveLayout === 'focus' && tile.key === primaryKey, 'is-self': tile.self }"
              v-a11y-click
              :aria-label="$t('chat.call.pinView')"
              @click="onTileClick(tile)"
            >
              <video
                v-if="call.isVideo && tile.stream"
                :ref="(el) => setTileEl(tile.key, el)"
                playsinline
                webkit-playsinline
                autoplay
                :muted="tile.self"
                class="call-tile-video"
              ></video>
              <div v-else class="call-tile-avatar">
                <el-avatar :size="effectiveLayout === 'focus' && tile.key === primaryKey ? 92 : 52" class="call-avatar">{{
                  (tile.name || '?').charAt(0)
                }}</el-avatar>
                <!-- 纯语音:远端音频必须挂到媒体元素才出声(每人一路);自视图不回放自己 -->
                <audio v-if="!tile.self && tile.stream" :ref="(el) => setTileEl('a' + tile.key, el)" autoplay class="tile-audio"></audio>
              </div>
              <div class="call-tile-name">
                <span class="tile-name">{{ tile.self ? $t('chat.call.self') : tile.name }}</span>
                <span v-if="tile.stateLabel" class="tile-state">{{ tile.stateLabel }}</span>
              </div>
            </div>
          </div>

          <div class="call-topbar">
            <span class="call-title">{{ titleText }}</span>
            <span class="call-state">{{ stateText }}</span>
          </div>

          <div class="call-actions">
            <el-button circle size="large" :type="call.muted ? 'danger' : 'default'" @click="call.toggleMute()">
              <el-icon><Microphone v-if="!call.muted" /><Mute v-else /></el-icon>
            </el-button>
            <el-button v-if="call.isVideo" circle size="large" :type="call.cameraOff ? 'danger' : 'default'" @click="call.toggleCamera()">
              <el-icon><VideoCamera v-if="!call.cameraOff" /><VideoPause v-else /></el-icon>
            </el-button>
            <el-button v-if="canFlip" circle size="large" :loading="flipping" @click="onFlipCamera">
              <el-icon><RefreshRight /></el-icon>
            </el-button>
            <el-button
              v-if="call.isVideo && call.joinedPeers.length >= 2"
              circle
              size="large"
              :title="call.layout === 'grid' ? $t('chat.call.layoutFocus') : $t('chat.call.layoutGrid')"
              @click="call.setLayout(call.layout === 'grid' ? 'focus' : 'grid')"
            >
              <el-icon><Grid v-if="call.layout === 'grid'" /><TopLeft v-else /></el-icon>
            </el-button>
            <el-button v-if="call.status === 'active'" circle size="large" @click="call.minimized = true">
              <el-icon><Minus /></el-icon>
            </el-button>
            <el-button circle size="large" type="danger" @click="call.hangup()">
              <el-icon><SwitchButton /></el-icon>
            </el-button>
          </div>
          <!-- 变焦:仅视频轨支持 zoom 约束的设备(Android Chrome 等)出现 -->
          <div v-if="call.zoom.supported" class="call-zoom">
            <el-button circle size="small" @click="zoomStep(-1)"
              ><el-icon><Minus /></el-icon
            ></el-button>
            <span class="call-zoom-value">{{ zoomText }}</span>
            <el-button circle size="small" @click="zoomStep(1)"
              ><el-icon><Plus /></el-icon
            ></el-button>
          </div>
        </template>
      </div>
    </template>
  </Teleport>
</template>

<script setup>
// 覆盖层:接听/拒绝是 user gesture(满足 iOS 播放要求);视频元素带 playsinline。
// 多路视频挂载沿用「ref 显式赋 srcObject」口径:iPad/Safari 不认模板绑定,元素/流变化都重挂。
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  Close,
  FullScreen,
  Grid,
  Microphone,
  Minus,
  Mute,
  Phone,
  Plus,
  RefreshRight,
  SwitchButton,
  TopLeft,
  VideoCamera,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useCallStore } from '@/stores/call'
import { usePlaybackLighting } from '@/utils/useSunLight'
import { isActiveStatus } from '@/utils/callMesh'
import { useDragResize } from '@/utils/useDragResize'

const userStore = useUserStore()
const call = useCallStore()
usePlaybackLighting(() => call.inCall && call.isVideo)
const { t } = useI18n()

// 最小化小窗位置(通话间记忆:挂断再打回到上次放的地方;键登记 LightSettings 清理清单)
const mini = useDragResize({
  x: Math.max(16, window.innerWidth - 236),
  y: 84,
  w: 220,
  h: 150,
  storageKey: 'ihomy:call:mini:v1',
})
const miniStyle = computed(() => ({
  left: `${mini.pos.value.x}px`,
  top: `${mini.pos.value.y}px`,
  zIndex: 'var(--z-float)',
}))
const onDragStart = (e) => mini.onDragStart(e)

// ---------- 瓦片(宫格/主次通用) ----------
// tiles:自己一路 + 每个活动状态成员;连接中包含终态成员(拒绝/忙线/超时带状态标签),接通后只留活动路
const showTerminal = computed(() => ['ringing-out', 'connecting'].includes(call.status))
const tiles = computed(() => {
  const list = [{ key: 'self', self: true, name: userStore.userInfo?.nickname || '', stream: call.localStream, stateLabel: '' }]
  for (const p of call.participants) {
    if (!showTerminal.value && !isActiveStatus(p.status)) continue
    list.push({
      key: String(p.id),
      self: false,
      name: p.name,
      stream: p.status === 'joined' ? p.remoteStream : null,
      stateLabel: stateLabelOf(p),
      id: p.id,
    })
  }
  return list
})

const stateLabelOf = (p) => {
  if (p.status === 'joined') return ''
  if (['inviting', 'ringing'].includes(p.status)) return t('chat.call.waiting')
  if (p.status === 'rejected') return t('chat.call.declined')
  if (p.status === 'busy') return t('chat.call.busyLine')
  if (p.status === 'timeout') return t('chat.call.noReply')
  return t('chat.call.disconnected')
}

// 双布局:两路时永远主次(大窗+小条,1:1 观感),三路起才可切宫格
const effectiveLayout = computed(() => (tiles.value.length >= 3 ? call.layout : 'focus'))
const primaryKey = computed(() => {
  if (effectiveLayout.value !== 'focus') return ''
  if (call.pinnedId != null && tiles.value.some((x) => x.key === String(call.pinnedId))) return String(call.pinnedId)
  return call.primaryPeer ? String(call.primaryPeer.id) : 'self'
})

const onTileClick = (tile) => {
  if (effectiveLayout.value !== 'focus' && tiles.value.length < 3) return
  if (tile.self) {
    // 钉自己 = 与主画面互换(1:1 的互换视角语义)
    if (tiles.value.length === 2) {
      const other = tiles.value.find((x) => !x.self)
      call.pinPeer(other?.id)
    } else {
      call.setLayout('grid')
    }
    return
  }
  call.pinPeer(tile.id)
}

// ---------- 视频挂载 ----------
const tileEls = new Map()
const setTileEl = (key, el) => {
  if (el) tileEls.set(key, el)
  else tileEls.delete(key)
}
const miniVideoEl = ref(null)

const attach = (el, stream) => {
  if (!el) return
  if (el.srcObject !== (stream || null)) el.srcObject = stream || null
  if (!stream) return
  const p = el.play?.()
  if (p && typeof p.catch === 'function') p.catch(() => {})
}

const syncVideos = () => {
  for (const tile of tiles.value) {
    if (!call.isVideo || !tile.stream) continue
    attach(tileEls.get(tile.key), tile.stream)
  }
  if (!call.isVideo) {
    for (const tile of tiles.value) {
      if (tile.self || !tile.stream) continue
      attach(tileEls.get('a' + tile.key), tile.stream)
    }
  }
  attach(miniVideoEl.value, call.remoteStream)
}

watch(
  () => [
    tiles.value.map((x) => `${x.key}:${x.stream ? '1' : '0'}`).join(','),
    call.status,
    call.minimized,
    call.isVideo,
    miniVideoEl.value,
  ],
  () => nextTick(syncVideos),
  { immediate: true },
)

// ---------- 前后置切换 / 变焦 ----------
const flipping = ref(false)
const canFlip = ref(false)

onMounted(async () => {
  // 多摄像头(手机前后置)或粗指针设备才显示翻转按钮;桌面单摄像头点了也切不动
  try {
    const devices = (await navigator.mediaDevices?.enumerateDevices?.()) || []
    const cams = devices.filter((d) => d.kind === 'videoinput').length
    canFlip.value = cams > 1 || !!window.matchMedia?.('(any-pointer: coarse)').matches
  } catch (e) {
    canFlip.value = false
  }
  try {
    const saved = localStorage.getItem('ihomy:call:layout:v1')
    if (saved === 'grid' || saved === 'focus') call.layout = saved
  } catch (e) {
    /* 本地记忆不可用就保持默认 */
  }
})

// 布局偏好记本机(下次通话沿用)
watch(
  () => call.layout,
  (v) => {
    try {
      localStorage.setItem('ihomy:call:layout:v1', v)
    } catch (e) {}
  },
)

const onFlipCamera = async () => {
  if (flipping.value) return
  flipping.value = true
  const ok = await call.switchCamera()
  flipping.value = false
  if (!ok) ElMessage.warning(t('chat.call.flipFailed'))
}

const zoomStep = (dir) => {
  const z = call.zoom
  call.setZoom(z.value + dir * Math.max(z.step, 0.1))
}
const zoomText = computed(() => `${Math.round(call.zoom.value * 10) / 10}x`)

const reject = () => {
  call.signal({ event: 'reject', to: call.callerId, callId: call.callId })
  call.reset()
}

// 通话计时:elapsed 由 interval 递增(规范 17:computed 内禁 Date.now,走 ref 驱动)
const elapsed = ref(0)
let tickTimer = null
watch(
  () => call.status,
  (s) => {
    clearInterval(tickTimer)
    elapsed.value = 0
    if (s === 'active') {
      tickTimer = setInterval(() => {
        elapsed.value += 1
      }, 1000)
    }
  },
  { immediate: true },
)
onUnmounted(() => clearInterval(tickTimer))

const titleText = computed(() => {
  if (call.isGroup) return t('chat.call.groupTitle', { n: Math.max(call.joinedPeers.length + 1, 2) })
  return call.peerName || t('chat.call.member')
})
const miniTitle = computed(() => titleText.value)

const stateText = computed(() => {
  if (call.status === 'ringing-out') return t('chat.call.ringing')
  if (call.status === 'connecting') return t('chat.call.connecting')
  if (call.status === 'active') {
    const m = Math.floor(elapsed.value / 60)
    const s = elapsed.value % 60
    return `${m}:${String(s).padStart(2, '0')}`
  }
  return ''
})
</script>

<style scoped>
.call-overlay {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  background: rgba(10, 12, 16, 0.94);
  z-index: var(--z-overlay);
}

/* 来电响铃:整屏接管,内容居中偏上,动作按钮沉底 */
.call-ring {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding-bottom: 140px;
  color: #fff;
}
.ring-avatar-wrap {
  position: relative;
  width: 96px;
  height: 96px;
  margin-bottom: 8px;
}
.ring-avatar {
  background: rgba(var(--color-brand-rgb), 0.9);
  font-size: 40px;
}
.ring-pulse {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  border-radius: 50%;
  border: 2px solid rgba(var(--color-brand-rgb), 0.55);
  animation: ring-pulse 1.8s ease-out infinite;
  pointer-events: none;
}
.ring-pulse-late {
  animation-delay: 0.9s;
}
@keyframes ring-pulse {
  0% {
    transform: scale(1);
    opacity: 0.8;
  }
  100% {
    transform: scale(1.9);
    opacity: 0;
  }
}
.ring-type-icon {
  color: rgba(255, 255, 255, 0.9);
}
.ring-type-icon.shake {
  animation: ring-shake 1.2s ease-in-out infinite;
}
@keyframes ring-shake {
  0%,
  100% {
    transform: rotate(0);
  }
  20% {
    transform: rotate(-12deg);
  }
  40% {
    transform: rotate(10deg);
  }
  60% {
    transform: rotate(-8deg);
  }
  80% {
    transform: rotate(6deg);
  }
}
.ring-name {
  font-size: 22px;
  font-weight: 600;
}
.ring-hint {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.72);
}
.ring-actions {
  position: absolute;
  right: 0;
  bottom: calc(40px + env(safe-area-inset-bottom, 0px));
  left: 0;
  display: flex;
  justify-content: center;
  gap: 76px;
}
.ring-action {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.ring-btn {
  --el-button-size: 64px;
}
.ring-btn.el-button {
  width: 64px;
  height: 64px;
}
.ring-action-label {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.8);
}

/* ---------- 通话主体:瓦片舞台 ---------- */
.call-stage {
  position: absolute;
  top: 44px;
  right: 12px;
  bottom: 108px;
  left: 12px;
  display: flex;
  gap: 8px;
}
/* 宫格并列:全员等分,自动换行 */
.call-stage.is-grid {
  flex-wrap: wrap;
  align-content: center;
}
.call-stage.is-grid .call-tile {
  flex: 1 1 40%;
  max-width: 50%;
  height: min(48%, 46vh);
}
/* 主次:主窗占满,其余沉底成一条 */
.call-stage.is-focus {
  flex-direction: column;
}
.call-stage.is-focus .call-tile.is-primary {
  flex: 1 1 auto;
  min-height: 0;
}
.call-stage.is-focus .call-tile:not(.is-primary) {
  flex: 0 0 auto;
  height: 96px;
  width: 168px;
  align-self: flex-start;
}
.call-stage.is-audio .call-tile {
  height: auto;
  min-height: 120px;
}
.call-tile {
  position: relative;
  min-width: 0;
  border-radius: 12px;
  overflow: hidden;
  background: rgba(24, 28, 34, 0.9);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.35);
  cursor: pointer;
  transition: box-shadow var(--dur-fast) ease;
}
.call-tile.is-primary {
  cursor: default;
}
.call-tile-video {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  background: #000;
}
/* 自视图镜像(主窗钉自己时也镜像) */
.call-tile.is-self .call-tile-video {
  transform: scaleX(-1);
}
.call-tile-avatar {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.tile-audio {
  display: none;
}
.call-avatar {
  background: rgba(var(--color-brand-rgb), 0.9);
  color: #fff;
}
.call-tile-name {
  position: absolute;
  right: 8px;
  bottom: 6px;
  left: 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  color: rgba(255, 255, 255, 0.92);
  font-size: 12px;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.6);
  pointer-events: none;
}
.tile-state {
  flex: none;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.72);
}

.call-topbar {
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: calc(100% - 32px);
  color: rgba(255, 255, 255, 0.88);
  font-size: 14px;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.6);
}
.call-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.call-state {
  flex: none;
  font-size: 13px;
  opacity: 0.75;
  font-variant-numeric: tabular-nums;
}

.call-actions {
  position: absolute;
  bottom: calc(24px + env(safe-area-inset-bottom, 0px));
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 14px;
}
.call-actions .el-button {
  --el-button-size: 52px;
}
/* 变焦步进:沉底居中,压在动作行下方一档 */
.call-zoom {
  position: absolute;
  bottom: calc(96px + env(safe-area-inset-bottom, 0px));
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(8px);
}
.call-zoom-value {
  min-width: 44px;
  text-align: center;
  color: rgba(255, 255, 255, 0.92);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 640px) {
  .call-stage.is-grid .call-tile {
    flex-basis: 100%;
    max-width: 100%;
    height: min(30%, 32vh);
  }
  .call-stage.is-focus .call-tile:not(.is-primary) {
    width: 128px;
    height: 76px;
  }
}

.call-mini {
  position: fixed;
  width: 220px;
  border-radius: 12px;
  overflow: hidden;
  background: rgba(12, 14, 18, 0.92);
  box-shadow: var(--shadow-hover);
  touch-action: none;
  cursor: grab;
}
.call-mini:active {
  cursor: grabbing;
}
.call-mini-video {
  width: 100%;
  height: 150px;
  object-fit: cover;
  display: block;
}
.call-mini-audio {
  height: 84px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 14px;
  color: #fff;
}
.mini-name {
  font-size: 14px;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.call-mini-bar {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
  padding: 2px 6px;
  cursor: grab;
  touch-action: none;
}
</style>
