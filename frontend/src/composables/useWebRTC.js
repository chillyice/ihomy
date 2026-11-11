// WebRTC 1:1 通话底层:RTCPeerConnection 封装(媒体 P2P 直连,信令走 WS call-signal)。
// 纯函数式工厂,连接生命周期/状态机在 stores/call.js;本文件只管媒体与 SDP/ICE。
// 环境硬前提:HTTPS(生产已具备)或 http://localhost(安全上下文);局域网 IP 不可用。

/**
 * 拉流前环境预检:返回错误码,null 表示可以尝试。
 * 局域网 IP 的 http 页面浏览器会直接摘掉 mediaDevices 或按 SecurityError 拒绝,和「用户拒绝授权」表现不同,
 * 先分清这两者才能给出用户照做得了的提示(换 https 地址 / 去系统里授权)。
 */
export function mediaEnvError() {
  if (typeof window !== 'undefined' && window.isSecureContext === false) return 'insecure'
  if (!navigator.mediaDevices?.getUserMedia) return 'unsupported'
  return null
}

/** getUserMedia 失败 → 稳定错误码:denied(权限被拒)/ insecure(非安全上下文)/ notfound(无设备)/ busy(被占用)/ generic */
function mapMediaError(e) {
  const name = e?.name || ''
  if (name === 'SecurityError') return 'insecure'
  if (name === 'NotAllowedError') return 'denied'
  if (name === 'NotFoundError' || name === 'DevicesNotFoundError' || name === 'OverconstrainedError') return 'notfound'
  if (name === 'NotReadableError' || name === 'TrackStartError') return 'busy'
  return 'generic'
}

/** 错误码 → i18n 键(stores/call.js 与 ChatComposer 共用,键在 chat.call.err*) */
export function mediaErrorKey(code) {
  const cap = code ? code.charAt(0).toUpperCase() + code.slice(1) : 'Generic'
  return `chat.call.err${cap}`
}

/**
 * 拉取本地媒体流,返回 { stream, downgraded }。
 * 视频通话拿不到摄像头时降级为纯音频并标记 downgraded(整通按语音走);
 * 音频也拿不到或环境不支持(非安全上下文/过旧内核)时抛出带 code 的错误,由调用方按码提示。
 */
export async function getLocalStream(media) {
  const envErr = mediaEnvError()
  if (envErr) {
    throw Object.assign(new Error('media unavailable in this context'), { code: envErr })
  }
  const wantVideo = media === 'VIDEO'
  let firstError = null
  if (wantVideo) {
    // 前置摄像头:部分 iPad/安卓机型不认 `video:true` 的默认设备,显式 facingMode 更稳
    const attempts = [
      { audio: true, video: { facingMode: 'user' } },
      { audio: true, video: true },
    ]
    for (const constraints of attempts) {
      try {
        return { stream: await navigator.mediaDevices.getUserMedia(constraints), downgraded: false }
      } catch (e) {
        firstError = firstError || e // 摄像头不可用降级语音(计划 §8:失败降级提示)
      }
    }
  }
  try {
    return { stream: await navigator.mediaDevices.getUserMedia({ audio: true, video: false }), downgraded: wantVideo }
  } catch (e) {
    const code = mapMediaError(e)
    const finalCode = code === 'generic' && firstError ? mapMediaError(firstError) : code
    // 排查用:原始错误名(NotAllowedError / SecurityError / NotFoundError)决定是环境问题还是授权问题
    console.warn('[call] getUserMedia 失败', finalCode, e?.name || '', e?.message || '')
    throw Object.assign(new Error(e?.message || 'getUserMedia failed'), { code: finalCode })
  }
}

/** 创建 RTCPeerConnection 并接好本地流/回调;调用方负责 close */
export function createPeer({ iceServers, localStream, onRemoteStream, onIceCandidate, onStateChange }) {
  const pc = new RTCPeerConnection({ iceServers: iceServers || [] })
  if (localStream) {
    for (const track of localStream.getTracks()) pc.addTrack(track, localStream)
  }
  pc.onicecandidate = (e) => {
    if (e.candidate) onIceCandidate?.(e.candidate)
  }
  // 自己攒一条远端流:iPad/Safari 的 ontrack 可能不带 streams[0],而且音视频分两次回调。
  // 每次都把新轨道并入同一条流再回传,视频元素只需挂一次 srcObject。
  const remote = new MediaStream()
  pc.ontrack = (e) => {
    if (!e.track) return
    if (!remote.getTracks().includes(e.track)) remote.addTrack(e.track)
    onRemoteStream?.(remote)
  }
  pc.onconnectionstatechange = () => onStateChange?.(pc.connectionState)
  return pc
}

/** 静音/取消静音(音频轨) */
export function setAudioEnabled(stream, enabled) {
  stream?.getAudioTracks().forEach((t) => (t.enabled = enabled))
}

/** 开关摄像头(视频轨) */
export function setVideoEnabled(stream, enabled) {
  stream?.getVideoTracks().forEach((t) => (t.enabled = enabled))
}

/** 关闭并释放本地流全部轨道 */
export function stopStream(stream) {
  stream?.getTracks().forEach((t) => t.stop())
}
