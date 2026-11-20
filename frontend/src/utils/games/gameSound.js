/**
 * 游戏声音总开关:外壳头部那颗静音键的唯一状态源。
 * 音效(宠物连连看/小恐龙)、iframe 游戏(三维弹球)、悬浮 BGM 都读这一份,
 * 一处静音全哑;状态存 localStorage,下次进游戏沿用。
 * 键 ihomy:game:mute:v1;老版宠物连连看单独存的 ihomy:game:petlink:muted 读一次做迁移。
 */
import { ref } from 'vue'

export const SOUND_MUTE_KEY = 'ihomy:game:mute:v1'
const LEGACY_PETLINK_KEY = 'ihomy:game:petlink:muted'

/** 无痕/老环境没有 localStorage:读失败就当没静音,写失败只影响本次会话之外 */
function readStored() {
  try {
    const saved = localStorage.getItem(SOUND_MUTE_KEY)
    if (saved !== null) return saved === '1'
    if (localStorage.getItem(LEGACY_PETLINK_KEY) === '1') {
      localStorage.setItem(SOUND_MUTE_KEY, '1')
      return true
    }
  } catch (e) {
    /* 无痕模式/配额满:按未静音处理 */
  }
  return false
}

export const gameMuted = ref(readStored())

/** 显式设值(外壳键与将来的设置项共用);返回落地后的状态 */
export function setGameMuted(value) {
  gameMuted.value = !!value
  try {
    localStorage.setItem(SOUND_MUTE_KEY, gameMuted.value ? '1' : '0')
  } catch (e) {
    /* 存不了就仅本次会话生效 */
  }
  return gameMuted.value
}

export function toggleGameMuted() {
  return setGameMuted(!gameMuted.value)
}
