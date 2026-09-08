import { ref } from 'vue'

// 百度短语音要求 16kHz/16bit/单声道;浏览器麦克风默认 44.1k/48k 浮点,这里采集后降采样为 16k PCM 并封装成 WAV
const TARGET_RATE = 16000

/**
 * 麦克风录音组合式:start() 开始采集,stop() 返回 16kHz 单声道 WAV Blob。
 * 采 PCM 而非 MediaRecorder(webm/opus),因为百度短语音只收 pcm/wav/amr/m4a。
 */
export function useVoiceRecorder() {
  const recording = ref(false)
  let ctx = null
  let stream = null
  let source = null
  let processor = null
  let pcm = []

  const start = async () => {
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      throw new Error('unsupported')
    }
    pcm = []
    stream = await navigator.mediaDevices.getUserMedia({
      audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true, autoGainControl: true },
    })
    const AC = window.AudioContext || window.webkitAudioContext
    // 声明目标采样率;若不支持则 ctx.sampleRate 回落到硬件采样率,由下方 onaudioprocess 线性降采样兜底
    ctx = new AC({ sampleRate: TARGET_RATE })
    if (ctx.state === 'suspended') await ctx.resume()
    source = ctx.createMediaStreamSource(stream)
    processor = ctx.createScriptProcessor(4096, 1, 1)
    processor.onaudioprocess = (e) => {
      const data = e.inputBuffer.getChannelData(0)
      const ratio = ctx.sampleRate / TARGET_RATE
      if (ratio <= 1.0001) {
        for (let i = 0; i < data.length; i++) pcm.push(to16(data[i]))
      } else {
        const outLen = Math.floor(data.length / ratio)
        for (let i = 0; i < outLen; i++) {
          const pos = i * ratio
          const i0 = Math.floor(pos)
          const i1 = Math.min(data.length - 1, i0 + 1)
          const frac = pos - i0
          pcm.push(to16(data[i0] * (1 - frac) + data[i1] * frac))
        }
      }
    }
    source.connect(processor)
    processor.connect(ctx.destination)
    recording.value = true
  }

  const stop = () =>
    new Promise((resolve) => {
      recording.value = false
      try { processor && processor.disconnect() } catch (e) { /* ignore */ }
      try { source && source.disconnect() } catch (e) { /* ignore */ }
      try { stream && stream.getTracks().forEach((t) => t.stop()) } catch (e) { /* ignore */ }
      try { ctx && ctx.close() } catch (e) { /* ignore */ }
      const blob = encodeWav(pcm, TARGET_RATE)
      pcm = []
      ctx = source = processor = stream = null
      resolve(blob)
    })

  return { recording, start, stop }
}

function to16(s) {
  const v = s < 0 ? s * 0x8000 : s * 0x7fff
  return Math.max(-0x8000, Math.min(0x7fff, Math.round(v)))
}

function encodeWav(samples, sampleRate) {
  const buffer = new ArrayBuffer(44 + samples.length * 2)
  const view = new DataView(buffer)
  const writeStr = (offset, str) => {
    for (let i = 0; i < str.length; i++) view.setUint8(offset + i, str.charCodeAt(i))
  }
  writeStr(0, 'RIFF')
  view.setUint32(4, 36 + samples.length * 2, true)
  writeStr(8, 'WAVE')
  writeStr(12, 'fmt ')
  view.setUint32(16, 16, true)
  view.setUint16(20, 1, true)
  view.setUint16(22, 1, true)
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * 2, true)
  view.setUint16(32, 2, true)
  view.setUint16(34, 16, true)
  writeStr(36, 'data')
  view.setUint32(40, samples.length * 2, true)
  let offset = 44
  for (let i = 0; i < samples.length; i++, offset += 2) {
    view.setInt16(offset, samples[i], true)
  }
  return new Blob([buffer], { type: 'audio/wav' })
}
