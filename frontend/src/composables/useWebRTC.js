// WebRTC 1:1 通话底层:RTCPeerConnection 封装(媒体 P2P 直连,信令走 WS call-signal)。
// 纯函数式工厂,连接生命周期/状态机在 stores/call.js;本文件只管媒体与 SDP/ICE。
// 环境硬前提:HTTPS(生产已具备)或 http://localhost(安全上下文);局域网 IP 不可用。

/** 拉取本地媒体流;视频通话被拒绝(无摄像头/用户拒绝)时降级为音频 */
export async function getLocalStream(media) {
  const wantVideo = media === 'VIDEO'
  try {
    return await navigator.mediaDevices.getUserMedia({ audio: true, video: wantVideo })
  } catch (e) {
    if (wantVideo) {
      // 摄像头不可用降级语音(计划 §8:失败降级提示)
      return await navigator.mediaDevices.getUserMedia({ audio: true, video: false })
    }
    throw e
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
  pc.ontrack = (e) => {
    const stream = e.streams?.[0]
    if (stream) onRemoteStream?.(stream)
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
