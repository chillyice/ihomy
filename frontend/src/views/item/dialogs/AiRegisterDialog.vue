<template>
  <el-dialog v-model="visible" append-to-body :title="$t('item.aiRegisterTitle')" width="480px">
    <div class="share-tip">{{ $t('item.aiRegisterTip') }}</div>
    <el-input
      v-model="text"
      type="textarea"
      :rows="3"
      maxlength="200"
      :placeholder="$t('item.aiRegisterPh')"
      @keydown.ctrl.enter="$emit('confirm')"
    />
    <div class="ai-voice-row">
      <el-button size="small" text :class="{ recording }" :loading="processing" @click="$emit('voice')">
        <el-icon><Microphone /></el-icon> {{ recording ? $t('item.voiceStop') : $t('item.voiceDictate') }}
      </el-button>
    </div>
    <div v-if="reply" class="ai-reply">{{ reply }}</div>
    <template #footer>
      <el-button @click="visible = false">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="putting" @click="$emit('confirm')">{{ $t('item.aiRegisterGo') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { Microphone } from '@element-plus/icons-vue'

defineProps({
  reply: { type: String, default: '' },
  putting: Boolean,
  recording: Boolean,
  processing: Boolean,
})
defineEmits(['voice', 'confirm'])
const visible = defineModel()
const text = defineModel('text', { default: '' })
</script>

<style scoped>
.ai-voice-row {
  margin-top: 8px;
  text-align: right;
}
.ai-reply {
  margin-top: 10px;
  padding: 8px 12px;
  border-radius: 10px;
  background: var(--color-card-2, rgba(0, 0, 0, 0.03));
  border: 1px solid var(--color-border);
  font-size: 13px;
  color: var(--color-text);
}
</style>
