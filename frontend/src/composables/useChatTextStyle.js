// 聊天字体偏好:输入区改设置、消息区读样式,共用同一份(模块级单例)。
import { ref } from 'vue'
import { CHAT_STYLE_DEFAULT, loadChatStyle, normalizeChatStyle, saveChatStyle } from '@/utils/chatStyle'

const style = ref(loadChatStyle())

export function useChatTextStyle() {
  /** 改一项(字号/颜色),立刻落到本机并广播给所有挂着的消息区 */
  const setStyle = (patch) => {
    style.value = saveChatStyle(normalizeChatStyle({ ...style.value, ...patch }))
  }
  const reset = () => {
    style.value = saveChatStyle(normalizeChatStyle(CHAT_STYLE_DEFAULT))
  }
  return { style, setStyle, reset }
}
