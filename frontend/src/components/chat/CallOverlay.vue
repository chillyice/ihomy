<!-- 1:1 通话界面:来电响铃 / 通话中全屏(视频大窗+本地小窗)/ 最小化小窗(可拖拽)。
     媒体与状态机在 stores/call.js;挂 App.vue(懒加载),内部自守卫登录态。
     整层 Teleport 到 body:来电是整屏接管,必须脱离一切祖先层叠上下文;
     定位用显式 top/right/bottom/left(构建目标 safari14 不支持 inset 简写,旧 iPad 上会塌成顶部小条) -->
<template>
  <Teleport to="body">
    <template v-if="userStore.isLoggedIn">
      <!-- 最小化小窗(通话中收起,整窗可拖拽) -->
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
          <span class="mini-name">{{ call.peerName }}</span>
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
            <el-avatar :size="96" class="ring-avatar">{{ call.peerName?.charAt(0) }}</el-avatar>
          </div>
          <el-icon :size="24" class="ring-type-icon" :class="{ shake: true }">
            <Phone v-if="!call.isVideo" />
            <VideoCamera v-else />
          </el-icon>
          <div class="ring-name">{{ call.peerName || $t('chat.call.member') }}</div>
          <div class="ring-hint">{{ $t(`chat.call.incoming.${call.isVideo ? 'video' : 'audio'}`) }}</div>
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

        <!-- 通话主体:大窗=对方(默认)/自己(互换后),小窗可点击互换视角 -->
        <template v-else>
          <video
            v-if="call.isVideo && mainStream"
            ref="mainVideoEl"
            playsinline
            webkit-playsinline
            autoplay
            :muted="swapped"
            class="call-remote-video"
          ></video>
          <div v-else class="call-audio-stage">
            <el-avatar :size="92" class="call-avatar">{{ call.peerName?.charAt(0) }}</el-avatar>
            <div class="call-peer">{{ call.peerName }}</div>
            <div class="call-state">{{ stateText }}</div>
          </div>
          <div
            v-if="call.isVideo && pipStream"
            class="call-pip"
            v-a11y-click
            :aria-label="$t('chat.call.swapView')"
            :title="$t('chat.call.swapView')"
            @click="swapped = !swapped"
          >
            <video ref="pipVideoEl" playsinline webkit-playsinline autoplay :muted="!swapped" class="call-local-video"></video>
          </div>
          <div v-if="call.isVideo" class="call-remote-name">{{ pipName }} · {{ stateText }}</div>

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
// 覆盖层:接听/拒绝是 user gesture(满足 iOS 播放要求);视频元素带 playsinline
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  Close,
  FullScreen,
  Microphone,
  Minus,
  Mute,
  Phone,
  Plus,
  RefreshRight,
  SwitchButton,
  VideoCamera,
  VideoPause,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useCallStore } from '@/stores/call'
import { useDragResize } from '@/utils/useDragResize'

const userStore = useUserStore()
const call = useCallStore()
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

// ---------- 视频挂载 ----------
// iPad/Safari 不认模板上的 srcObject 绑定,而且 v-if 切换会重建元素漏挂流:
// 统一用 ref 显式赋值 srcObject 并 play(),元素/流/状态任一变化都重挂一次。
// 互换视角(swapped)只是两路流换位置:大窗流/小窗流各一个 ref,不管显示的是谁。
const swapped = ref(false)
const mainVideoEl = ref(null)
const pipVideoEl = ref(null)
const miniVideoEl = ref(null)

const mainStream = computed(() => (swapped.value ? call.localStream : call.remoteStream))
const pipStream = computed(() => (swapped.value ? call.remoteStream : call.localStream))
const pipName = computed(() => {
  if (!call.isVideo) return call.peerName
  return swapped.value ? userStore.userInfo?.nickname : call.peerName
})

const attach = (el, stream) => {
  if (!el) return
  if (el.srcObject !== (stream || null)) el.srcObject = stream || null
  if (!stream) return
  const p = el.play?.()
  if (p && typeof p.catch === 'function') p.catch(() => {})
}

const syncVideos = () => {
  attach(mainVideoEl.value, mainStream.value)
  attach(pipVideoEl.value, pipStream.value)
  attach(miniVideoEl.value, call.remoteStream)
}

watch(
  () => [mainStream.value, pipStream.value, call.status, call.minimized, mainVideoEl.value, pipVideoEl.value, miniVideoEl.value],
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
})

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

// 新一通来电回到默认视角(上一通的互换状态不复用)
watch(
  () => call.status,
  (s) => {
    if (s === 'idle' || s === 'ringing-in' || s === 'ringing-out') swapped.value = false
  },
)

const reject = () => {
  call.signal({ event: 'reject', to: call.peerId, callId: call.callId })
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
  display: flex;
  align-items: center;
  justify-content: center;
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

.call-remote-video {
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
.call-remote-name {
  position: absolute;
  top: 18px;
  left: 50%;
  transform: translateX(-50%);
  color: rgba(255, 255, 255, 0.88);
  font-size: 14px;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.6);
}
.call-audio-stage {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  color: #fff;
}
.call-avatar {
  background: rgba(var(--color-brand-rgb), 0.9);
  font-size: 34px;
}
.call-peer {
  font-size: 20px;
  font-weight: 600;
}
.call-state {
  font-size: 13px;
  opacity: 0.75;
}
.call-local-video {
  position: absolute;
  right: 20px;
  bottom: 96px;
  width: 180px;
  border-radius: 12px;
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.4);
  background: #000;
}
/* 小窗容器:点击互换视角(光标提示可点) */
.call-pip {
  position: absolute;
  right: 20px;
  bottom: 96px;
  width: 180px;
  cursor: pointer;
}
.call-pip .call-local-video {
  position: static;
  width: 100%;
}
.call-actions {
  position: absolute;
  bottom: 28px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 16px;
}
.call-actions .el-button {
  --el-button-size: 52px;
}
/* 变焦步进:沉底居中,压在动作行下方一档 */
.call-zoom {
  position: absolute;
  bottom: 100px;
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
