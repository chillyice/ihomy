// 「分享到聊天」统一入口:各页面(照片/视频/日记/图书/放映厅)一行调用。
// P1:卡片进草稿 + 引导去聊天室发送;P2 浮窗上线后由 chatStore.share 内部升级为直接展开浮窗。
import { ElMessage } from 'element-plus'
import { useChatStore } from '@/stores/chat'
import i18n from '@/i18n'

export function shareToChat(card) {
  if (!card) return
  useChatStore().share(card)
  ElMessage.success(i18n.global.t('chat.share.pending'))
}
