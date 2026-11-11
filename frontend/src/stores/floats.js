// 浮窗开关:聊天浮窗 / 悬浮播放器是否显示。本机持久化(localStorage),默认开启。
// 浮窗拖到窗口底部删除区松手 = 关掉对应开关;用户可在聊天室、音乐页重新打开。
import { defineStore } from 'pinia'

export const FLOAT_CHAT_KEY = 'ihomy:float:chat'
export const FLOAT_MUSIC_KEY = 'ihomy:float:music'

const readFlag = (key) => {
  try {
    return localStorage.getItem(key) !== '0'
  } catch (e) {
    return true
  }
}
const writeFlag = (key, on) => {
  try {
    localStorage.setItem(key, on ? '1' : '0')
  } catch (e) {}
}

export const useFloatsStore = defineStore('floats', {
  state: () => ({
    chat: readFlag(FLOAT_CHAT_KEY),
    music: readFlag(FLOAT_MUSIC_KEY),
  }),
  actions: {
    setChat(on) {
      this.chat = !!on
      writeFlag(FLOAT_CHAT_KEY, this.chat)
    },
    setMusic(on) {
      this.music = !!on
      writeFlag(FLOAT_MUSIC_KEY, this.music)
    },
  },
})
