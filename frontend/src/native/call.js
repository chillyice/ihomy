// 通话权限前置(仅原生壳):发起/接听前主动把麦克风(视频还要摄像头)要到手,
// 并在本机第一次使用时先解释一句「接下来会弹系统授权框」,避免用户把系统弹框当成故障。
// 网页端直接放行,浏览器自己的站点权限流程不变。
import { registerPlugin } from '@capacitor/core'
import { ElMessage, ElMessageBox } from 'element-plus'
import i18n from '@/i18n'
import { isNativeApp } from '@/utils/platform'

const IhomyCall = registerPlugin('IhomyCall')

/** 本机是否已经看过说明框(语音/视频各算一次) */
const INTRO_KEY = 'ihomy:call:permIntro:v1'
const t = (key) => i18n.global.t(key)

/**
 * 确保通话权限可用:返回 { ok, reason }。
 * reason: 'cancelled'(用户关掉说明框)| 'denied'(本次拒绝)| 'blocked'(拒绝且不再询问)。
 * 调用方拿 ok=false 就不再拉流,避免 getUserMedia 第二次弹框。
 */
export async function ensureCallPermissions({ video } = {}) {
  if (!isNativeApp()) return { ok: true, reason: '' }
  const introKey = `${INTRO_KEY}:${video ? 'video' : 'audio'}`
  if (!localStorage.getItem(introKey)) {
    try {
      await ElMessageBox.confirm(t(video ? 'chat.call.permIntroVideo' : 'chat.call.permIntroAudio'), t('chat.call.permTitle'), {
        confirmButtonText: t('chat.call.permContinue'),
        cancelButtonText: t('common.cancel'),
        type: 'info',
      })
    } catch (e) {
      return { ok: false, reason: 'cancelled' }
    }
    try {
      localStorage.setItem(introKey, '1')
    } catch (e) {
      /* 无痕环境存不了:下次再看一次说明,不影响功能 */
    }
  }

  let res
  try {
    res = await IhomyCall.ensure({ video: !!video })
  } catch (e) {
    // 插件异常不拦通话:继续走 getUserMedia,由浏览器按真实原因报错
    console.warn('[call] 原生权限申请失败', e)
    return { ok: true, reason: '' }
  }
  if (res?.ok) return { ok: true, reason: '' }

  if (res?.blocked) {
    // 「拒绝且不再询问」:系统不会再弹框,只能去应用设置里改
    try {
      await ElMessageBox.confirm(t('chat.call.permBlocked'), t('chat.call.permTitle'), {
        confirmButtonText: t('chat.call.openSettings'),
        cancelButtonText: t('common.cancel'),
        type: 'warning',
      })
      await IhomyCall.openSettings()
    } catch (e) {
      /* 用户选择稍后自己处理 */
    }
    return { ok: false, reason: 'blocked' }
  }
  ElMessage.warning(t(video ? 'chat.call.permDeniedVideo' : 'chat.call.permDeniedAudio'))
  return { ok: false, reason: 'denied' }
}
