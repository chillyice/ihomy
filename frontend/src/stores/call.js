// 1:1 通话状态机:响铃(出/入)→ 连接(SDP/ICE 交换)→ 通话中 → 结束。
// 信令全走 WS call-signal(stores/chat.js 的 socket,按 to 定向);媒体 P2P 直连不过服务器。
// 通话结束由一方发 end 事件,服务端代落 CALL 记录卡(幂等);本 store 只管媒体与界面状态。
import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import i18n from '@/i18n'
import { chatApi } from '@/api'
import { useChatStore } from '@/stores/chat'
import { useUserStore } from '@/stores/user'
import {
  createPeer,
  getLocalStream,
  mediaEnvError,
  mediaErrorKey,
  setAudioEnabled,
  setVideoEnabled,
  stopStream,
} from '@/composables/useWebRTC'

const RING_TIMEOUT_MS = 30000

/** 通话 ID:安全上下文用 randomUUID,老内核回落时间戳+随机串(不允许在建通话时抛错) */
function newCallId() {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID()
  return 'c' + Date.now().toString(36) + Math.random().toString(36).slice(2, 10)
}

export const useCallStore = defineStore('call', {
  state: () => ({
    status: 'idle', // idle | ringing-out | ringing-in | connecting | active | ended
    callId: '',
    peerId: null,
    peerName: '',
    media: 'AUDIO', // AUDIO | VIDEO
    direction: null, // out | in
    localStream: null,
    remoteStream: null,
    pc: null,
    muted: false,
    cameraOff: false,
    minimized: false,
    startedAt: 0,
    failReason: '',
  }),
  getters: {
    inCall: (s) => ['ringing-out', 'ringing-in', 'connecting', 'active'].includes(s.status),
    isVideo: (s) => s.media === 'VIDEO',
  },
  actions: {
    /** 发起通话:拉媒体 → invite → 30s 无应答按超时结束;媒体失败按码提示并记录 failReason */
    async start(peer, media) {
      if (this.inCall) return false
      this.failReason = ''
      const chat = useChatStore()
      if (chat.socketStatus !== 'open') return false
      // 环境预检放在建通话之前:非安全上下文(局域网 http 页面)与不支持媒体的壳先按码报出原因,
      // 不要发 invite 让对端白响铃
      const envErr = mediaEnvError()
      if (envErr) {
        this.failReason = envErr
        return false
      }
      this.callId = newCallId()
      this.peerId = peer.id
      this.peerName = peer.name || ''
      this.media = media === 'VIDEO' ? 'VIDEO' : 'AUDIO'
      this.direction = 'out'
      this.status = 'ringing-out'
      try {
        const { stream, downgraded } = await getLocalStream(this.media)
        this.localStream = stream
        if (downgraded) {
          // 摄像头不可用:整通按语音走(invite 改带 AUDIO,对端界面同步),并明确告知发起人
          this.media = 'AUDIO'
          ElMessage.warning(i18n.global.t('chat.call.downgradedAudio'))
        }
      } catch (e) {
        this.reset()
        this.failReason = e?.code || 'generic' // reset 会清空,这里补记供发起方按码提示
        return false
      }
      this.signal({ event: 'invite', to: peer.id, callId: this.callId, media: this.media, from: this.me().id, fromName: this.me().name })
      this.ringTimer = setTimeout(() => {
        if (this.status === 'ringing-out') this.finishWith('timeout')
      }, RING_TIMEOUT_MS)
      return true
    },

    /** WS call-signal 下行入口(stores/chat.js 转发) */
    onSignal(d) {
      if (!d || !d.event) return
      const handlers = {
        invite: () => this.onInvite(d),
        accept: () => this.onAccept(d),
        reject: () => this.onReject(d),
        busy: () => this.onBusy(d),
        offer: () => this.onOffer(d),
        answer: () => this.onAnswer(d),
        ice: () => this.onIce(d),
        end: () => this.onRemoteEnd(d),
      }
      handlers[d.event]?.()
    },

    me() {
      const u = useUserStore().userInfo
      return { id: u?.id, name: u?.nickname }
    },

    signal(data) {
      useChatStore().sendSignal({ type: 'call-signal', data })
    },

    /** 来电:通话中回 busy,否则响铃等用户手势(接听/拒绝) */
    onInvite(d) {
      if (this.inCall) {
        this.signal({ event: 'busy', to: d.from, callId: d.callId })
        return
      }
      this.failReason = ''
      this.callId = d.callId
      this.peerId = d.from
      this.peerName = d.fromName || ''
      this.media = d.media === 'VIDEO' ? 'VIDEO' : 'AUDIO'
      this.direction = 'in'
      this.status = 'ringing-in'
    },

    /** 对端接了:主叫创建连接发 offer */
    async onAccept(d) {
      if (this.status !== 'ringing-out' || this.callId !== d.callId) return
      clearTimeout(this.ringTimer)
      this.status = 'connecting'
      await this.setupPeer()
      const offer = await this.pc.createOffer()
      await this.pc.setLocalDescription(offer)
      this.signal({ event: 'offer', to: this.peerId, callId: this.callId, sdp: this.pc.localDescription })
    },

    onReject(d) {
      if (this.callId !== d.callId) return
      this.finishWith('rejected')
    },

    onBusy(d) {
      if (this.callId !== d.callId) return
      this.finishWith('busy')
    },

    /** 被叫接听:拉媒体 → accept → 等 offer;拿不到媒体时明确报错并回绝,不再静默消失 */
    async accept() {
      if (this.status !== 'ringing-in') return
      this.status = 'connecting'
      try {
        const { stream, downgraded } = await getLocalStream(this.media)
        this.localStream = stream
        if (downgraded) ElMessage.warning(i18n.global.t('chat.call.downgradedAudio'))
      } catch (e) {
        ElMessage.error(i18n.global.t(mediaErrorKey(e?.code)))
        this.signal({ event: 'reject', to: this.peerId, callId: this.callId })
        this.reset()
        return
      }
      this.signal({ event: 'accept', to: this.peerId, callId: this.callId })
      await this.setupPeer()
    },

    async onOffer(d) {
      if (this.callId !== d.callId || !this.pc) return
      await this.pc.setRemoteDescription(d.sdp)
      const answer = await this.pc.createAnswer()
      await this.pc.setLocalDescription(answer)
      this.signal({ event: 'answer', to: this.peerId, callId: this.callId, sdp: this.pc.localDescription })
    },

    async onAnswer(d) {
      if (this.callId !== d.callId || !this.pc) return
      if (this.pc.signalingState === 'have-local-offer') {
        await this.pc.setRemoteDescription(d.sdp)
      }
    },

    async onIce(d) {
      if (this.callId !== d.callId || !this.pc) return
      try {
        await this.pc.addIceCandidate(d.candidate)
      } catch {
        /* 竞态候选(远端描述未就绪)忽略 */
      }
    },

    /** 远端挂断/取消:本地收尾,不再回发 end(end 幂等,避免二次落卡) */
    onRemoteEnd(d) {
      if (this.callId !== d.callId) return
      clearTimeout(this.ringTimer)
      this.teardown()
    },

    /** 建 RTCPeerConnection 并交换 ICE */
    async setupPeer() {
      const ice = await chatApi.iceServers().catch(() => ({ iceServers: [] }))
      this.pc = createPeer({
        iceServers: ice?.iceServers || [],
        localStream: this.localStream,
        onRemoteStream: (stream) => {
          this.remoteStream = stream
          if (this.status === 'connecting') {
            this.status = 'active'
            this.startedAt = Date.now()
          }
        },
        onIceCandidate: (candidate) => {
          this.signal({ event: 'ice', to: this.peerId, callId: this.callId, candidate })
        },
        onStateChange: (state) => {
          if (state === 'failed') this.hangup()
        },
      })
    },

    /** 主动挂断/取消:end 事件由服务端落 CALL 记录(结果按 reason 映射) */
    hangup() {
      if (!this.inCall) return
      this.finishWith(this.status === 'active' ? 'completed' : 'cancel')
    },

    /**
     * 以某原因结束通话:发 end(服务端代落 CALL 记录卡)再本地收尾。
     * 超时/拒绝/忙线/挂断全部走这里;对端先挂走 onRemoteEnd→teardown(不重复发 end)。
     */
    finishWith(reason) {
      if (!this.callId) return
      const duration = this.status === 'active' ? Math.round((Date.now() - this.startedAt) / 1000) : 0
      this.signal({ event: 'end', to: this.peerId, callId: this.callId, reason, duration, media: this.media })
      this.failReason = reason
      this.teardown()
    },

    teardown() {
      clearTimeout(this.ringTimer)
      stopStream(this.localStream)
      try {
        this.pc?.close()
      } catch {
        /* 已关 */
      }
      const wasInCall = this.inCall
      this.pc = null
      if (wasInCall) {
        this.status = 'ended'
        setTimeout(() => {
          if (this.status === 'ended') this.reset()
        }, 800)
      } else {
        this.reset()
      }
    },

    reset() {
      clearTimeout(this.ringTimer)
      stopStream(this.localStream)
      try {
        this.pc?.close()
      } catch {
        /* 已关 */
      }
      this.ringTimer = null
      this.pc = null
      this.localStream = null
      this.remoteStream = null
      this.callId = ''
      this.peerId = null
      this.peerName = ''
      this.direction = null
      this.status = 'idle'
      this.muted = false
      this.cameraOff = false
      this.minimized = false
      this.startedAt = 0
      this.failReason = ''
    },

    toggleMute() {
      this.muted = !this.muted
      setAudioEnabled(this.localStream, !this.muted)
    },
    toggleCamera() {
      this.cameraOff = !this.cameraOff
      setVideoEnabled(this.localStream, !this.cameraOff)
    },
  },
})
