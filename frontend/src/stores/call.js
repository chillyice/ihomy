// 多人音视频通话(家庭内 mesh):全连接 P2P,信令全走 WS call-signal(stores/chat.js 按对定向转发)。
// 协议与建连方向见 docs/设计想法/功能/多人通话-计划.md §2:
// - 主叫是协议权威:串行处理 accept,对每个接通者发 offer,向其余成员广播 join、给新成员发 roster;
// - 第 3 人起,新接通者向已接通的非主叫成员发 offer;
// - 主叫挂断 = end 全体(服务端按 callId 落一张 CALL 卡),成员挂断 = leave 自己(1:1 仍走 end 保持落卡口径);
// - 1:1 与旧版壳完全互通(主叫 offer 流向不变,旧端忽略 group/roster/join/leave 等新事件)。
// 名单迁移的纯函数内核在 utils/callMesh.js。
import { defineStore } from 'pinia'
import { ElMessage } from 'element-plus'
import i18n from '@/i18n'
import { chatApi } from '@/api'
import { useChatStore } from '@/stores/chat'
import { useUserStore } from '@/stores/user'
import { isNativeApp } from '@/utils/platform'
import { MAX_PARTICIPANTS, applyRoster, isActiveStatus, joinedOthers, markPeer, upsertParticipant } from '@/utils/callMesh'
import {
  applyZoom,
  createPeer,
  flipVideoTrack,
  getLocalStream,
  mediaEnvError,
  mediaErrorKey,
  setAudioEnabled,
  setVideoEnabled,
  stopStream,
  zoomCapabilities,
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
    media: 'AUDIO', // AUDIO | VIDEO
    direction: null, // out | in
    /** 主叫(协议权威);isCaller = 主叫是我 */
    callerId: null,
    callerName: '',
    /** 群组标记(>2 人);来电响铃时的人数展示 */
    isGroup: false,
    ringCount: 2,
    /**
     * 参与者(不含我):[{id,name,status,pc,remoteStream}]
     * status: inviting(已邀) / ringing(响铃,远端视角) / joined(接通) / rejected / busy / timeout / left / failed
     * pc / remoteStream 为原生对象,Vue 对非普通对象不做代理,直接存安全。
     */
    participants: [],
    localStream: null,
    muted: false,
    cameraOff: false,
    minimized: false,
    startedAt: 0,
    failReason: '',
    facing: 'user', // user | environment(视频通话切前后置)
    zoom: { supported: false, min: 1, max: 1, step: 0.1, value: 1 },
    /** 布局:grid 宫格并列 / focus 主次(一大 N 小);pinnedId=主窗成员 */
    layout: 'grid',
    pinnedId: null,
  }),
  getters: {
    inCall: (s) => ['ringing-out', 'ringing-in', 'connecting', 'active'].includes(s.status),
    isVideo: (s) => s.media === 'VIDEO',
    isCaller() {
      return this.callerId != null && Number(this.callerId) === Number(this.me().id)
    },
    /** 已接通成员(除我) */
    joinedPeers: (s) => s.participants.filter((p) => p.status === 'joined'),
    /** 主窗成员:钉选优先,否则第一路已接通 */
    primaryPeer: (s) => {
      const pinned = s.pinnedId != null ? s.participants.find((p) => Number(p.id) === Number(s.pinnedId) && p.status === 'joined') : null
      return pinned || s.participants.find((p) => p.status === 'joined') || null
    },
    /** 兼容旧 UI(最小化窗/状态文案):主窗成员名与远端流 */
    peerName() {
      return this.primaryPeer?.name || this.callerName || ''
    },
    remoteStream() {
      return this.primaryPeer?.remoteStream || null
    },
  },
  actions: {
    /** 发起通话:拉媒体 → 逐个 invite → 30s 无接听按超时收尾;媒体失败按码提示并记录 failReason */
    async start(members, media) {
      if (this.inCall) return false
      this.failReason = ''
      const chat = useChatStore()
      if (chat.socketStatus !== 'open') return false
      const list = (Array.isArray(members) ? members : [members]).filter((m) => m && m.id != null)
      if (!list.length) return false
      if (list.length > MAX_PARTICIPANTS - 1) {
        this.failReason = 'tooMany'
        return false
      }
      // 环境预检放在建通话之前:非安全上下文(局域网 http 页面)与不支持媒体的壳先按码报出原因,
      // 不要发 invite 让对端白响铃
      const envErr = mediaEnvError()
      if (envErr) {
        this.failReason = envErr
        return false
      }
      // 壳内先把系统麦克风/摄像头权限要到手:提示文案由 native/call.js 负责,
      // 这里用 'permission'/'cancelled' 让调用方不要重复弹一遍
      if (!(await this.ensurePermissions(media === 'VIDEO'))) return false
      this.callId = newCallId()
      this.media = media === 'VIDEO' ? 'VIDEO' : 'AUDIO'
      this.direction = 'out'
      this.isGroup = list.length > 1
      this.callerId = this.me().id
      this.status = 'ringing-out'
      try {
        const { stream, downgraded } = await getLocalStream(this.media)
        this.localStream = stream
        this.refreshZoomCaps()
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
      this.participants = list.map((m) => ({ id: m.id, name: m.name || '', status: 'inviting' }))
      const total = list.length + 1
      for (const m of list) {
        this.signal({ event: 'invite', to: m.id, callId: this.callId, media: this.media, group: total > 2, count: total })
      }
      this.armRingTimer()
      return true
    },

    /** WS call-signal 下行入口(stores/chat.js 转发):按事件路由,offer/answer/ice 再按 from 路由到对应 pc */
    onSignal(d) {
      if (!d || !d.event) return
      const handlers = {
        invite: () => this.onInvite(d),
        accept: () => this.onAccept(d),
        reject: () => this.onReject(d),
        busy: () => this.onBusy(d),
        roster: () => this.onRoster(d),
        join: () => this.onJoin(d),
        offer: () => this.onOffer(d),
        answer: () => this.onAnswer(d),
        ice: () => this.onIce(d),
        leave: () => this.onLeave(d),
        end: () => this.onRemoteEnd(d),
      }
      handlers[d.event]?.()
    },

    me() {
      const u = useUserStore().userInfo
      return { id: u?.id, name: u?.nickname }
    },

    /**
     * 壳内通话权限前置(网页端直接放行):先把麦克风/摄像头要到手再拉流,
     * 避免「正在接通」阶段被系统权限框打断。失败时写入约定的 failReason:
     * 'cancelled' = 用户关掉说明框;'permission' = 原生侧已经提示过(调用方别重复弹)。
     */
    async ensurePermissions(video) {
      if (!isNativeApp()) return true
      try {
        const { ensureCallPermissions } = await import('@/native/call')
        const res = await ensureCallPermissions({ video })
        if (res?.ok) return true
        this.failReason = res?.reason === 'cancelled' ? 'cancelled' : 'permission'
        return false
      } catch (e) {
        // 插件模块加载失败不拦通话:交给 getUserMedia 按真实原因报错
        console.warn('[call] 权限前置模块加载失败', e)
        return true
      }
    },

    signal(data) {
      useChatStore().sendSignal({ type: 'call-signal', data })
    },

    /** 主叫 30s 定时:无人接通按超时收尾;有人接通则把未应答者释放(leave,不落卡)后继续 */
    armRingTimer() {
      clearTimeout(this.ringTimer)
      this.ringTimer = setTimeout(() => {
        if (this.status !== 'ringing-out' && this.status !== 'connecting') return
        const joined = this.participants.filter((p) => p.status === 'joined')
        if (joined.length === 0) {
          this.finishWith('timeout')
          return
        }
        for (const p of this.participants) {
          if (['inviting', 'ringing'].includes(p.status)) this.signal({ event: 'leave', to: p.id, callId: this.callId })
        }
        this.participants = this.participants.map((p) => (['inviting', 'ringing'].includes(p.status) ? { ...p, status: 'timeout' } : p))
      }, RING_TIMEOUT_MS)
    },

    /** 来电:通话中回 busy,否则响铃等用户手势(接听/拒绝) */
    onInvite(d) {
      if (this.inCall) {
        this.signal({ event: 'busy', to: d.from, callId: d.callId })
        return
      }
      this.failReason = ''
      this.callId = d.callId
      this.callerId = d.from
      this.callerName = d.fromName || ''
      this.media = d.media === 'VIDEO' ? 'VIDEO' : 'AUDIO'
      this.direction = 'in'
      this.isGroup = !!d.group
      this.ringCount = Number(d.count) || 2
      this.participants = []
      this.status = 'ringing-in'
    },

    /** 被叫接听:拉媒体 → accept(建连等主叫 offer / roster) */
    async accept() {
      if (this.status !== 'ringing-in') return
      this.status = 'connecting'
      if (!(await this.ensurePermissions(this.media === 'VIDEO'))) {
        this.signal({ event: 'reject', to: this.callerId, callId: this.callId })
        this.reset()
        return
      }
      try {
        const { stream, downgraded } = await getLocalStream(this.media)
        this.localStream = stream
        this.refreshZoomCaps()
        if (downgraded) ElMessage.warning(i18n.global.t('chat.call.downgradedAudio'))
      } catch (e) {
        ElMessage.error(i18n.global.t(mediaErrorKey(e?.code)))
        this.signal({ event: 'reject', to: this.callerId, callId: this.callId })
        this.reset()
        return
      }
      // 主叫先记为已接通(它的 offer 马上会到);其余成员等 roster/join
      this.participants = upsertParticipant([], { id: this.callerId, name: this.callerName, status: 'joined' })
      this.signal({ event: 'accept', to: this.callerId, callId: this.callId })
    },

    /** 主叫侧:成员接通 → 记 joined、下发 roster、向其余已接通者广播 join、主动发 offer */
    async onAccept(d) {
      if (this.callId !== d.callId || !this.isCaller) return
      if (!['ringing-out', 'connecting', 'active'].includes(this.status)) return
      clearTimeout(this.ringTimer)
      if (this.status === 'ringing-out') this.status = 'connecting'
      const entry = this.participants.find((p) => Number(p.id) === Number(d.from))
      if (!entry || entry.status === 'joined') return // 重复 accept 幂等
      this.participants = markPeer(this.participants, d.from, 'joined')
      // roster = 除新成员外的全部已接通者(含我);新成员对非主叫成员自行发 offer
      const roster = this.participants
        .filter((p) => p.status === 'joined' && Number(p.id) !== Number(d.from))
        .map((p) => ({ id: p.id, name: p.name }))
      this.signal({ event: 'roster', to: d.from, callId: this.callId, peers: roster, group: true })
      for (const p of joinedOthers(this.participants, this.me().id)) {
        if (Number(p.id) === Number(d.from)) continue
        this.signal({ event: 'join', to: p.id, callId: this.callId, peer: { id: d.from, name: entry.name }, group: true })
      }
      await this.ensurePeer(d.from, entry.name, { initiator: true })
    },

    onReject(d) {
      if (this.callId !== d.callId || !this.isCaller) return
      this.onDecline(d.from, 'rejected')
    },

    onBusy(d) {
      if (this.callId !== d.callId || !this.isCaller) return
      this.onDecline(d.from, 'busy')
    },

    /** 主叫侧:某人拒接/忙线;全部终态且无人接通才整通收尾(保持 1:1 的落卡语义) */
    onDecline(peerId, status) {
      this.participants = markPeer(this.participants, peerId, status)
      const anyActive = this.participants.some((p) => isActiveStatus(p.status))
      const anyJoined = this.participants.some((p) => p.status === 'joined')
      if (!anyJoined && !anyActive) this.finishWith(status)
    },

    /** 新接通成员:合并名单(本地已有的不动),并对非主叫成员主动发 offer(主叫的 offer 在路上) */
    onRoster(d) {
      if (this.callId !== d.callId) return
      this.participants = applyRoster(this.participants, d.peers)
      for (const p of joinedOthers(this.participants, this.me().id)) {
        if (Number(p.id) === Number(this.callerId)) continue
        this.ensurePeer(p.id, p.name, { initiator: true })
      }
    },

    /** 通报有新成员接通:只登记,连接等对方(新成员)的 offer 送达 */
    onJoin(d) {
      if (this.callId !== d.callId || d.peer?.id == null) return
      const cur = this.participants.find((p) => Number(p.id) === Number(d.peer.id))
      if (cur && !isActiveStatus(cur.status)) return // 终态不回退
      this.participants = upsertParticipant(this.participants, { id: d.peer.id, name: d.peer.name || '', status: 'joined' })
    },

    /** 建 RTCPeerConnection;initiator=true 时立刻发 offer。pc/远端流回填进对应参与者 */
    async ensurePeer(peerId, name, { initiator = false } = {}) {
      this.participants = upsertParticipant(this.participants, { id: peerId, name, status: 'joined' })
      const cur = this.participants.find((p) => Number(p.id) === Number(peerId))
      if (cur?.pc) return cur.pc
      const ice = await chatApi.iceServers().catch(() => ({ iceServers: [] }))
      const pc = createPeer({
        iceServers: ice?.iceServers || [],
        localStream: this.localStream,
        onRemoteStream: (stream) => {
          this.patchPeer(peerId, { remoteStream: stream })
          if (this.status === 'connecting') {
            this.status = 'active'
            this.startedAt = Date.now()
            this.watchMedia()
          }
        },
        onIceCandidate: (candidate) => {
          this.signal({ event: 'ice', to: peerId, callId: this.callId, candidate })
        },
        onStateChange: (state) => {
          if (state === 'failed') {
            // 单路失败:标记该成员并释放;其余路继续(网格里少一路)
            this.patchPeer(peerId, { status: 'failed', pc: null, remoteStream: null })
            try {
              pc.close()
            } catch (e) {
              /* 已关 */
            }
          }
        },
      })
      this.patchPeer(peerId, { pc })
      if (initiator) {
        const offer = await pc.createOffer()
        await pc.setLocalDescription(offer)
        this.signal({ event: 'offer', to: peerId, callId: this.callId, sdp: pc.localDescription })
      }
      return pc
    },

    /** 应答方:收到 offer 时 pc 可能还没建(惰性创建),回 answer */
    async onOffer(d) {
      if (this.callId !== d.callId || !this.inCall) return
      const cur = this.participants.find((p) => Number(p.id) === Number(d.from))
      if (cur && !isActiveStatus(cur.status)) return
      const pc = cur?.pc || (await this.ensurePeer(d.from, cur?.name || '', { initiator: false }))
      if (pc.signalingState !== 'stable' && pc.signalingState !== 'have-remote-offer') return
      await pc.setRemoteDescription(d.sdp)
      const answer = await pc.createAnswer()
      await pc.setLocalDescription(answer)
      this.signal({ event: 'answer', to: d.from, callId: this.callId, sdp: pc.localDescription })
    },

    async onAnswer(d) {
      if (this.callId !== d.callId) return
      const pc = this.participants.find((p) => Number(p.id) === Number(d.from))?.pc
      if (pc && pc.signalingState === 'have-local-offer') await pc.setRemoteDescription(d.sdp)
    },

    async onIce(d) {
      if (this.callId !== d.callId) return
      const pc = this.participants.find((p) => Number(p.id) === Number(d.from))?.pc
      if (!pc) return
      try {
        await pc.addIceCandidate(d.candidate)
      } catch {
        /* 竞态候选(远端描述未就绪)忽略 */
      }
    },

    /** 成员离开(leave,不落卡):释放该路;离开后没人了就静默收尾 */
    onLeave(d) {
      if (this.callId !== d.callId) return
      const pc = this.participants.find((p) => Number(p.id) === Number(d.from))?.pc
      try {
        pc?.close()
      } catch (e) {
        /* 已关 */
      }
      this.patchPeer(d.from, { status: 'left', pc: null, remoteStream: null })
      if (joinedOthers(this.participants, this.me().id).length === 0) {
        this.failReason = 'completed'
        this.teardown()
      }
    },

    /** 主叫整通结束/超时:本地收尾,不再回发 end(end 幂等,避免二次落卡) */
    onRemoteEnd(d) {
      if (this.callId !== d.callId) return
      clearTimeout(this.ringTimer)
      this.failReason = d.reason || ''
      this.teardown()
    },

    patchPeer(peerId, patch) {
      this.participants = this.participants.map((p) => (Number(p.id) === Number(peerId) ? { ...p, ...patch } : p))
    },

    /**
     * 媒体看门狗:接通 6 秒后聚合所有 pc 的入站音频字节,全部为 0 说明 P2P 通道没真正建立
     * (典型:双方都在运营商 NAT 后而服务端没配 TURN)。给一句可照做的提示,不自动挂断。
     */
    watchMedia() {
      clearTimeout(this.mediaWatchTimer)
      this.mediaWatchTimer = setTimeout(async () => {
        if (this.status !== 'active') return
        try {
          let bytes = 0
          for (const p of this.participants) {
            if (!p.pc) continue
            const stats = await p.pc.getStats()
            stats.forEach((r) => {
              if (r.type === 'inbound-rtp' && r.kind === 'audio') bytes += Number(r.bytesReceived) || 0
            })
            if (bytes > 0) break
          }
          if (bytes <= 0) ElMessage.warning(i18n.global.t('chat.call.noMedia'))
        } catch (e) {
          /* getStats 不可用(旧内核)就不提示,不误报 */
        }
      }, 6000)
    },

    /**
     * 挂断:主叫 = end 全体(服务端落一张 CALL 卡);成员 = 1:1 走 end(保持落卡口径)、
     * 群组走 leave(他人继续)。超时/拒绝/忙线/挂断全部收敛到 finishWith;对端先挂走 onRemoteEnd。
     */
    hangup() {
      if (!this.inCall) return
      if (this.isCaller) {
        this.finishWith(this.status === 'active' ? 'completed' : 'cancel')
        return
      }
      const others = this.participants.filter((p) => Number(p.id) !== Number(this.me().id) && isActiveStatus(p.status))
      const duration = this.status === 'active' ? Math.round((Date.now() - this.startedAt) / 1000) : 0
      const reason = this.status === 'active' ? 'completed' : 'cancel'
      if (others.length <= 1) {
        for (const p of others) this.signal({ event: 'end', to: p.id, callId: this.callId, reason, duration, media: this.media })
      } else {
        for (const p of others) this.signal({ event: 'leave', to: p.id, callId: this.callId })
      }
      this.failReason = reason
      this.teardown()
    },

    /**
     * 以某原因整通结束:向所有活动成员发 end(服务端按 callId 落一张 CALL 卡)再本地收尾。
     * 对端先挂走 onRemoteEnd→teardown(不重复发 end)。
     */
    finishWith(reason) {
      if (!this.callId) return
      const duration = this.status === 'active' ? Math.round((Date.now() - this.startedAt) / 1000) : 0
      for (const p of this.participants) {
        if (!isActiveStatus(p.status) || Number(p.id) === Number(this.me().id)) continue
        this.signal({ event: 'end', to: p.id, callId: this.callId, reason, duration, media: this.media })
      }
      this.failReason = reason
      this.teardown()
    },

    teardown() {
      clearTimeout(this.ringTimer)
      clearTimeout(this.mediaWatchTimer)
      stopStream(this.localStream)
      for (const p of this.participants) {
        try {
          p.pc?.close()
        } catch {
          /* 已关 */
        }
      }
      const wasInCall = this.inCall
      this.participants = []
      this.pinnedId = null
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
      clearTimeout(this.mediaWatchTimer)
      stopStream(this.localStream)
      this.ringTimer = null
      this.localStream = null
      this.participants = []
      this.callId = ''
      this.media = 'AUDIO'
      this.direction = null
      this.callerId = null
      this.callerName = ''
      this.isGroup = false
      this.ringCount = 2
      this.status = 'idle'
      this.muted = false
      this.cameraOff = false
      this.minimized = false
      this.startedAt = 0
      this.failReason = ''
      this.facing = 'user'
      this.zoom = { supported: false, min: 1, max: 1, step: 0.1, value: 1 }
      this.layout = 'grid'
      this.pinnedId = null
    },

    toggleMute() {
      this.muted = !this.muted
      setAudioEnabled(this.localStream, !this.muted)
    },
    toggleCamera() {
      this.cameraOff = !this.cameraOff
      setVideoEnabled(this.localStream, !this.cameraOff)
    },

    /** 探测当前视频轨的变焦能力(拉到流后调一次;轨道换了要重探) */
    refreshZoomCaps() {
      const track = this.localStream?.getVideoTracks()[0]
      const caps = zoomCapabilities(track)
      if (caps) {
        this.zoom = { supported: true, min: caps.min, max: caps.max, step: caps.step, value: caps.min }
      } else {
        this.zoom = { supported: false, min: 1, max: 1, step: 0.1, value: 1 }
      }
    },

    /** 切换前置/后置:新视频轨 replaceTrack 进每一条已建立的连接,不重协商 */
    async switchCamera() {
      if (!this.isVideo || !this.localStream) return false
      const facing = this.facing === 'user' ? 'environment' : 'user'
      const track = await flipVideoTrack(this.localStream, facing)
      if (!track) return false
      this.facing = facing
      this.localStream.addTrack(track)
      for (const p of this.participants) {
        const sender = p.pc?.getSenders?.().find((s) => s.track?.kind === 'video')
        if (sender) {
          try {
            await sender.replaceTrack(track)
          } catch (e) {
            /* 换轨失败保持原画面 */
          }
        }
      }
      this.refreshZoomCaps()
      return true
    },

    /** 变焦(仅支持的设备显示控件) */
    async setZoom(value) {
      if (!this.zoom.supported) return
      const v = Math.min(this.zoom.max, Math.max(this.zoom.min, value))
      const ok = await applyZoom(this.localStream?.getVideoTracks()[0], v)
      if (ok) this.zoom.value = v
    },

    /** 布局:宫格并列 / 主次(一大 N 小);钉选某一路切主 */
    setLayout(mode) {
      this.layout = mode === 'focus' ? 'focus' : 'grid'
      if (this.layout === 'grid') this.pinnedId = null
    },
    pinPeer(id) {
      this.layout = 'focus'
      this.pinnedId = id
    },
  },
})
