<template>
  <!-- 房间快速语音:一键选成员发起 1:1 语音(大厅与游戏内横幅共用) -->
  <el-popover v-model:visible="open" placement="bottom-end" :width="236" trigger="click">
    <template #reference>
      <slot>
        <el-button size="small" round :title="$t('games.room.quickCall')" :aria-label="$t('games.room.quickCall')">
          <el-icon><Phone /></el-icon>
        </el-button>
      </slot>
    </template>
    <div class="call-picker">
      <div class="call-picker-title">{{ $t('games.room.quickCall') }}</div>
      <el-empty v-if="!others.length" :description="$t('games.room.callNoOne')" :image-size="48" />
      <div v-for="m in others" :key="m.userId" class="call-picker-row">
        <img v-if="m.avatar" :src="m.avatar" class="call-picker-avatar" :alt="m.name" />
        <el-avatar v-else :size="28">{{ m.name?.charAt(0) }}</el-avatar>
        <span class="call-picker-name">{{ m.name }}</span>
        <el-tooltip :content="$t('games.room.callAudio')" placement="top">
          <el-button circle size="small" :disabled="!canCall" @click="call(m)">
            <el-icon><Phone /></el-icon>
          </el-button>
        </el-tooltip>
      </div>
    </div>
  </el-popover>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Phone } from '@element-plus/icons-vue'
import { useChatStore } from '@/stores/chat'
import { useCallStore } from '@/stores/call'

const props = defineProps({
  members: { type: Array, default: () => [] },
  myId: { type: [Number, String], default: null },
})

const { t } = useI18n()
const chatStore = useChatStore()
const callStore = useCallStore()

const open = ref(false)

const others = computed(() => (props.members || []).filter((m) => m.userId !== props.myId))
const canCall = computed(() => chatStore.socketStatus === 'open' && !callStore.inCall)

const call = async (m) => {
  if (!canCall.value) {
    ElMessage.warning(t('games.room.callUnavailable'))
    return
  }
  const ok = await callStore.start({ id: m.userId, name: m.name }, 'AUDIO')
  if (ok) open.value = false
  else ElMessage.warning(t('chat.call.startFailed'))
}
</script>

<style scoped>
.call-picker {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.call-picker-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 4px;
}
.call-picker-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 2px;
}
.call-picker-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: cover;
  display: block;
}
.call-picker-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
