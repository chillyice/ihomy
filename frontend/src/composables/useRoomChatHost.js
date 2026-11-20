// 「一起玩」房间临时聊天的宿主声明:页面只登记/交还当前房间,
// 全局聊天浮窗(ChatFloat)的「房间」页据此渲染,不再单独起房间聊天浮球。
// 大厅页与本局页会短暂交接同一间房,占用凭据在 store 里按 token 计,不靠「谁后写谁清」。
import { onBeforeUnmount, watch } from 'vue'
import { useRoomChatStore } from '@/stores/roomChat'

const isEnded = (room) => !room || room.status === 'FINISHED'

/**
 * @param {import('vue').Ref|Function} source 当前房间对象(ref 或 getter)
 */
export function useRoomChatHost(source) {
  const roomChat = useRoomChatStore()
  let token = null
  let claimedId = 0

  const sync = (room) => {
    const id = isEnded(room) ? 0 : Number(room.id) || 0
    if (id && id === claimedId) {
      roomChat.refreshRoom(room)
      return
    }
    if (token) {
      roomChat.releaseRoom(token)
      token = null
      claimedId = 0
    }
    if (!id) return
    token = roomChat.claimRoom(room)
    claimedId = id
  }

  watch(source, sync, { immediate: true })

  onBeforeUnmount(() => {
    if (token) roomChat.releaseRoom(token)
    token = null
    claimedId = 0
  })
}
