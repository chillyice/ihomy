<!-- 聊天输入区:工具条在上(图片/站内/链接/游戏邀请/通话),输入行在下;/chat 页与浮窗共用 -->
<template>
  <div class="chat-composer">
    <div class="composer-toolbar">
      <el-tooltip :content="$t('chat.composer.image')" placement="top">
        <el-button text circle :disabled="!connected || uploading" @click="fileInput?.click()">
          <el-icon><Picture /></el-icon>
        </el-button>
      </el-tooltip>
      <el-tooltip :content="$t('chat.composer.objects')" placement="top">
        <el-button text circle :disabled="!connected" @click="pickerVisible = true">
          <el-icon><FolderOpened /></el-icon>
        </el-button>
      </el-tooltip>
      <el-tooltip :content="$t('chat.composer.link')" placement="top">
        <el-button text circle :disabled="!connected" @click="openLink">
          <el-icon><Link /></el-icon>
        </el-button>
      </el-tooltip>
      <el-tooltip :content="$t('chat.composer.game')" placement="top">
        <el-button text circle :disabled="!connected" @click="gameVisible = true">
          <el-icon><Trophy /></el-icon>
        </el-button>
      </el-tooltip>
      <el-tooltip :content="$t('chat.composer.call')" placement="top">
        <el-button text circle :disabled="!connected" @click="openCall">
          <el-icon><Phone /></el-icon>
        </el-button>
      </el-tooltip>
    </div>

    <!-- 待发送卡片草稿(分享注入/站内选择/游戏邀请) -->
    <div v-if="pendingCard" class="composer-pending">
      <el-icon class="pending-icon"><Paperclip /></el-icon>
      <span class="pending-title">{{ pendingCard.content }}</span>
      <el-icon class="pending-close" @click="pendingCard = null"><CircleClose /></el-icon>
    </div>

    <div class="msg-input">
      <el-input
        v-model="draft"
        :placeholder="$t('chat.placeholder')"
        :disabled="!connected || uploading"
        maxlength="2000"
        show-word-limit
        @keyup.enter="onSend"
      />
      <el-button type="primary" :loading="uploading" :disabled="!connected" @click="onSend">
        {{ $t('chat.send') }}
      </el-button>
    </div>

    <input ref="fileInput" type="file" accept="image/*" hidden @change="onFileChange" />

    <ChatObjectPicker v-model="pickerVisible" @pick="onObjectPick" />

    <!-- 站外链接:可选预览(unfurl),发送为 LINK -->
    <el-dialog v-model="linkVisible" :title="$t('chat.composer.linkTitle')" width="dialog-sm" append-to-body>
      <el-input v-model="linkUrl" :placeholder="$t('chat.composer.linkPlaceholder')" clearable />
      <div v-if="linkPreview" class="link-mini">
        <div class="link-mini-title">{{ linkPreview.title || linkUrl }}</div>
        <div v-if="linkPreview.description" class="link-mini-desc">{{ linkPreview.description }}</div>
      </div>
      <template #footer>
        <el-button @click="linkVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button :loading="linkLoading" :disabled="!linkUrlOk" @click="fetchPreview">
          {{ $t('chat.composer.fetchPreview') }}
        </el-button>
        <el-button type="primary" :disabled="!linkUrlOk" @click="sendLink">{{ $t('chat.send') }}</el-button>
      </template>
    </el-dialog>

    <!-- 游戏邀请:八个 H5 小游戏选一个 -->
    <el-dialog v-model="gameVisible" :title="$t('chat.composer.gameTitle')" width="dialog-md" append-to-body>
      <div class="game-grid">
        <button v-for="g in H5_GAMES" :key="g.code" type="button" class="game-pick" @click="pickGame(g)">
          <el-icon :size="20"><Trophy /></el-icon>
          <span>{{ $t(g.nameKey) }}</span>
        </button>
      </div>
    </el-dialog>

    <!-- 通话:选家庭成员 + 音/视频 -->
    <el-dialog v-model="callVisible" :title="$t('chat.composer.callTitle')" width="dialog-sm" append-to-body>
      <div v-loading="callLoading" class="call-members">
        <el-empty v-if="!callLoading && !members.length" :description="$t('chat.call.noMember')" :image-size="60" />
        <div v-for="m in members" :key="m.id" class="call-member-row">
          <el-avatar :size="34">{{ (m.nickname || m.username || 'U').charAt(0) }}</el-avatar>
          <span class="call-member-name">{{ m.nickname || m.username }}</span>
          <span class="call-member-actions">
            <el-tooltip :content="$t('chat.call.audio')" placement="top">
              <el-button circle size="small" @click="startCall(m, 'AUDIO')">
                <el-icon><Phone /></el-icon>
              </el-button>
            </el-tooltip>
            <el-tooltip :content="$t('chat.call.video')" placement="top">
              <el-button circle size="small" @click="startCall(m, 'VIDEO')">
                <el-icon><VideoCamera /></el-icon>
              </el-button>
            </el-tooltip>
          </span>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
// 工具条与发送逻辑:图片走既有 /file/upload 通道(流式,不新增接口);
// 卡片/游戏/链接的负载构造在 utils/chatMsg(与后端同口径校验)
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleClose, FolderOpened, Link, Paperclip, Phone, Picture, Trophy, VideoCamera } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { chatApi, fileApi, memberApi } from '@/api'
import { useChatStore } from '@/stores/chat'
import { useCallStore } from '@/stores/call'
import { useUserStore } from '@/stores/user'
import { mediaErrorKey } from '@/composables/useWebRTC'
import { H5_GAMES, buildCardPayload, buildGameInvitePayload, buildLinkPayload } from '@/utils/chatMsg'
import ChatObjectPicker from '@/components/chat/ChatObjectPicker.vue'

const IMAGE_MAX_BYTES = 20 * 1024 * 1024

const { t } = useI18n()
const chatStore = useChatStore()

const draft = ref('')
const uploading = ref(false)
const fileInput = ref(null)
const pendingCard = ref(null) // {msgType, content, payload}

const pickerVisible = ref(false)
const linkVisible = ref(false)
const linkUrl = ref('')
const linkLoading = ref(false)
const linkPreview = ref(null)
const gameVisible = ref(false)
const callVisible = ref(false)
const callLoading = ref(false)
const members = ref([])
const callStore = useCallStore()

const connected = computed(() => chatStore.socketStatus === 'open')
const linkUrlOk = computed(() => /^https?:\/\//.test(linkUrl.value.trim()) && linkUrl.value.trim().length <= 2000)

// 分享注入的草稿(其他页面 share() 进来)→ 变待发送卡片
onMounted(() => {
  const shared = chatStore.consumeShareCard()
  if (shared) setPending(shared)
})

const setPending = ({ msgType, content, payload }) => {
  if (!payload) return
  pendingCard.value = { msgType, content: content || '', payload }
}

/** 发送:有文字先发文字(当附言),有待发卡片则随后发出 */
const onSend = () => {
  if (!connected.value || uploading.value) return
  const text = draft.value.trim()
  if (text) {
    if (!chatStore.sendText(text)) return
    draft.value = ''
  }
  if (pendingCard.value) {
    const { msgType, content, payload } = pendingCard.value
    if (chatStore.sendContent({ msgType, content, payload })) {
      pendingCard.value = null
    } else {
      ElMessage.warning(t('chat.composer.sendFailed'))
    }
  }
}

/** 图片消息:上传既有通道 → /files URL + 自然尺寸 → 直接发送(附言=当前输入) */
const onFileChange = async (evt) => {
  const file = evt.target.files?.[0]
  evt.target.value = ''
  if (!file) return
  if (file.size > IMAGE_MAX_BYTES) {
    ElMessage.warning(t('chat.composer.tooLarge'))
    return
  }
  uploading.value = true
  try {
    const res = await fileApi.upload(file)
    const url = res?.data?.url
    if (!url) throw new Error('no url')
    const size = await imageSize(url)
    const caption = draft.value.trim()
    const ok = chatStore.sendContent({
      msgType: 'IMAGE',
      content: caption,
      payload: { url, width: size.w, height: size.h },
    })
    if (ok && caption) draft.value = ''
    if (!ok) ElMessage.warning(t('chat.composer.sendFailed'))
  } catch {
    ElMessage.error(t('chat.composer.sendImageFailed'))
  } finally {
    uploading.value = false
  }
}

const imageSize = (url) =>
  new Promise((resolve) => {
    const img = new Image()
    img.onload = () => resolve({ w: img.naturalWidth, h: img.naturalHeight })
    img.onerror = () => resolve({ w: 0, h: 0 })
    img.src = url
  })

const onObjectPick = (item) => {
  const payload = buildCardPayload(item)
  if (!payload) return
  setPending({ msgType: 'CARD', content: item.title, payload })
}

const openLink = () => {
  linkUrl.value = ''
  linkPreview.value = null
  linkVisible.value = true
}

const fetchPreview = async () => {
  const url = linkUrl.value.trim()
  if (!linkUrlOk.value) return
  linkLoading.value = true
  try {
    const res = await chatApi.unfurl(url)
    linkPreview.value = res?.data?.preview || null
  } catch {
    // 限流/抓取失败:静默降级,发送时不带预览
    linkPreview.value = null
    ElMessage.info(t('chat.composer.previewUnavailable'))
  } finally {
    linkLoading.value = false
  }
}

const sendLink = () => {
  const url = linkUrl.value.trim()
  if (!linkUrlOk.value) return
  const ok = chatStore.sendContent({ msgType: 'LINK', content: url, payload: buildLinkPayload(linkPreview.value) })
  if (ok) {
    linkVisible.value = false
    pendingCard.value = null
  } else {
    ElMessage.warning(t('chat.composer.sendFailed'))
  }
}

const openCall = async () => {
  callVisible.value = true
  if (members.value.length) return
  callLoading.value = true
  try {
    const list = await memberApi.list()
    members.value = (list || []).filter((m) => m.id !== useUserStore().userInfo?.id)
  } catch {
    members.value = []
  } finally {
    callLoading.value = false
  }
}

const startCall = async (m, media) => {
  const ok = await callStore.start({ id: m.id, name: m.nickname || m.username }, media)
  if (ok) callVisible.value = false
  else ElMessage.warning(callStore.failReason ? t(mediaErrorKey(callStore.failReason)) : t('chat.call.startFailed'))
}

const pickGame = (g) => {
  const payload = buildGameInvitePayload(g.code)
  if (!payload) return
  setPending({ msgType: 'GAME_INVITE', content: t(g.nameKey), payload })
  gameVisible.value = false
}
</script>

<style scoped>
.chat-composer {
  flex-shrink: 0;
}
.composer-toolbar {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-bottom: 6px;
}
.composer-pending {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  margin-bottom: 6px;
  border: 1px dashed rgba(var(--color-brand-rgb), 0.4);
  border-radius: 10px;
  background: rgba(var(--color-brand-rgb), 0.06);
  font-size: 13px;
  min-width: 0;
}
.pending-icon {
  color: var(--color-brand-text);
  flex-shrink: 0;
}
.pending-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pending-close {
  cursor: pointer;
  color: var(--color-text-secondary, #999);
  flex-shrink: 0;
}
.msg-input {
  display: flex;
  gap: 10px;
}
.link-mini {
  margin-top: 10px;
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--color-bg-2);
}
.link-mini-title {
  font-size: 13px;
  font-weight: 600;
}
.link-mini-desc {
  margin-top: 2px;
  font-size: 12px;
  color: var(--color-text-secondary, #888);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.call-members {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 120px;
}
.call-member-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 8px;
  border-radius: 10px;
}
.call-member-row:hover {
  background: var(--color-bg-2);
}
.call-member-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.call-member-actions {
  display: flex;
  gap: 6px;
}
.game-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}
.game-pick {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 14px 8px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: transparent;
  cursor: pointer;
  font-size: 13px;
  color: var(--color-text-primary, inherit);
  transition:
    transform var(--dur-fast) var(--ease-soft),
    background var(--dur-fast) ease;
}
.game-pick:hover {
  transform: translateY(-2px);
  background: rgba(var(--color-brand-rgb), 0.06);
}

@media (max-width: 768px) {
  .msg-input {
    gap: 8px;
  }
  .msg-input .el-button {
    flex-shrink: 0;
  }
  .game-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
