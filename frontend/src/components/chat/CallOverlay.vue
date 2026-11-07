<!-- 1:1 通话界面:来电响铃 / 通话中全屏(视频大窗+本地小窗)/ 最小化小窗(可拖拽)。
     媒体与状态机在 stores/call.js;挂 App.vue(懒加载),内部自守卫登录态 -->
<template>
  <template v-if="userStore.isLoggedIn">
    <!-- 最小化小窗(通话中收起,可拖拽) -->
    <div v-if="call.status === 'active' && call.minimized" class="call-mini" :style="miniStyle">
      <video
        v-if="call.isVideo && call.remoteStream"
        :src-object.prop="call.remoteStream"
        playsinline
        autoplay
        class="call-mini-video"
      ></video>
      <div v-else class="call-mini-audio">
        <el-icon :size="20"><Phone /></el-icon>
        <span class="mini-name">{{ call.peerName }}</span>
      </div>
      <div class="call-mini-bar" @pointerdown="onDragStart">
        <el-button text circle size="small" @click="call.minimized = false">
          <el-icon><FullScreen /></el-icon>
        </el-button>
        <el-button text circle size="small" type="danger" @click="call.hangup()">
          <el-icon><SwitchButton /></el-icon>
        </el-button>
      </div>
    </div>

    <!-- 全屏覆盖层:来电 / 连接中 / 通话中 -->
    <div v-else-if="call.status !== 'idle' && call.status !== 'ended' && !call.minimized" class="call-overlay">
      <!-- 来电响铃 -->
      <div v-if="call.status === 'ringing-in'" class="call-ring card">
        <el-icon :size="34" class="ring-icon" :class="{ shake: true }">
          <Phone v-if="!call.isVideo" />
          <VideoCamera v-else />
        </el-icon>
        <div class="ring-name">{{ call.peerName || $t('chat.call.member') }}</div>
        <div class="ring-hint">{{ $t(`chat.call.incoming.${call.isVideo ? 'video' : 'audio'}`) }}</div>
        <div class="ring-actions">
          <el-button type="danger" round @click="reject">{{ $t('chat.call.reject') }}</el-button>
          <el-button type="primary" round @click="call.accept()">{{ $t('chat.call.accept') }}</el-button>
        </div>
      </div>

      <!-- 通话主体 -->
      <template v-else>
        <video
          v-if="call.isVideo && call.remoteStream"
          :src-object.prop="call.remoteStream"
          playsinline
          autoplay
          class="call-remote-video"
        ></video>
        <div v-else class="call-audio-stage">
          <el-avatar :size="92" class="call-avatar">{{ call.peerName?.charAt(0) }}</el-avatar>
          <div class="call-peer">{{ call.peerName }}</div>
          <div class="call-state">{{ stateText }}</div>
        </div>
        <video
          v-if="call.isVideo && call.localStream"
          :src-object.prop="call.localStream"
          playsinline
          autoplay
          muted
          class="call-local-video"
        ></video>
        <div v-if="call.isVideo" class="call-remote-name">{{ call.peerName }} · {{ stateText }}</div>

        <div class="call-actions">
          <el-button circle size="large" :type="call.muted ? 'danger' : 'default'" @click="call.toggleMute()">
            <el-icon><Microphone v-if="!call.muted" /><Mute v-else /></el-icon>
          </el-button>
          <el-button v-if="call.isVideo" circle size="large" :type="call.cameraOff ? 'danger' : 'default'" @click="call.toggleCamera()">
            <el-icon><VideoCamera v-if="!call.cameraOff" /><VideoPause v-else /></el-icon>
          </el-button>
          <el-button v-if="call.status === 'active'" circle size="large" @click="call.minimized = true">
            <el-icon><Minus /></el-icon>
          </el-button>
          <el-button circle size="large" type="danger" @click="call.hangup()">
            <el-icon><SwitchButton /></el-icon>
          </el-button>
        </div>
      </template>
    </div>
  </template>
</template>

<script setup>
// 覆盖层:接听/拒绝是 user gesture(满足 iOS 播放要求);视频元素带 playsinline
import { computed, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { FullScreen, Microphone, Minus, Mute, Phone, SwitchButton, VideoCamera, VideoPause } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useCallStore } from '@/stores/call'
import { useDragResize } from '@/utils/useDragResize'

const userStore = useUserStore()
const call = useCallStore()
const { t } = useI18n()

// 最小化小窗位置(不持久化,通话级临时)
const mini = useDragResize({
  x: Math.max(16, window.innerWidth - 236),
  y: 84,
  w: 220,
  h: 150,
  storageKey: '',
})
const miniStyle = computed(() => ({
  left: `${mini.pos.value.x}px`,
  top: `${mini.pos.value.y}px`,
  zIndex: 'var(--z-float)',
}))
const onDragStart = (e) => mini.onDragStart(e)

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
  inset: 0;
  background: rgba(10, 12, 16, 0.94);
  z-index: var(--z-overlay);
  display: flex;
  align-items: center;
  justify-content: center;
}
.call-ring {
  text-align: center;
  padding: 28px 40px;
  min-width: 260px;
}
.ring-icon {
  color: var(--color-brand-text);
}
.ring-icon.shake {
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
  margin-top: 10px;
  font-size: 18px;
  font-weight: 600;
}
.ring-hint {
  margin-top: 4px;
  font-size: 13px;
  color: var(--color-text-secondary, #999);
}
.ring-actions {
  margin-top: 18px;
  display: flex;
  gap: 12px;
  justify-content: center;
}

.call-remote-video {
  position: absolute;
  inset: 0;
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

.call-mini {
  position: fixed;
  width: 220px;
  border-radius: 12px;
  overflow: hidden;
  background: rgba(12, 14, 18, 0.92);
  box-shadow: var(--shadow-hover);
  touch-action: none;
}
.call-mini-video {
  width: 100%;
  height: 150px;
  object-fit: cover;
  display: block;
  pointer-events: none;
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
