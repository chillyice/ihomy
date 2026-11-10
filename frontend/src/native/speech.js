// 语音识别(仅原生壳):录一句话 → 系统识别 → 文本。语义解析在前端 utils/voiceRecord.js。
import { registerPlugin } from '@capacitor/core'

const IhomySpeech = registerPlugin('IhomySpeech')

/** 本机是否有可用识别服务(没有就不显示麦克风入口) */
export async function speechAvailable() {
  try {
    const res = await IhomySpeech.available()
    return !!(res && res.available)
  } catch (e) {
    return false
  }
}

/**
 * 说一句话并返回识别文本。
 * 失败时抛错并带 code:cancel(用户取消/页面离开)/ permission / no_service / network / no_match / failed。
 */
export async function recognizeOnce() {
  try {
    const res = await IhomySpeech.start()
    return String((res && res.text) || '').trim()
  } catch (e) {
    const msg = String((e && e.message) || '')
    const known = ['cancel', 'permission', 'no_service', 'network', 'no_match', 'busy', 'failed']
    const code = known.find((k) => msg.includes(k)) || 'failed'
    const err = new Error(msg || code)
    err.code = code
    throw err
  }
}

/** 页面离开/取消:收起识别 */
export async function cancelRecognition() {
  try {
    await IhomySpeech.cancel()
  } catch (e) {
    /* 没有在途识别,忽略 */
  }
}
