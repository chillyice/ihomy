<template>
  <section class="assistant-action">
    <header>
      <strong>{{ $t('chat.assistant.tools.' + action.toolName) }}</strong>
      <span role="status">{{ $t('chat.assistant.states.' + action.status) }}</span>
    </header>
    <p>{{ action.preview }}</p>
    <label v-if="diary && action.status === 'PENDING'">
      {{ $t('chat.assistant.audience') }}
      <select v-model="visibility" :disabled="disabled">
        <option value="PRIVATE">{{ $t('chat.assistant.private') }}</option>
        <option value="FAMILY">{{ $t('chat.assistant.family') }}</option>
      </select>
    </label>
    <p v-else-if="diary && action.status === 'DONE'">
      {{ $t('chat.assistant.audience') }} · {{ $t(action.visibility === 'FAMILY' ? 'chat.assistant.family' : 'chat.assistant.private') }}
    </p>
    <p v-if="action.error" role="alert">{{ action.error }}</p>
    <footer v-if="action.status === 'PENDING'">
      <el-button size="small" :disabled="assistant.busy" @click="assistant.decide(action, false)">{{
        $t('chat.assistant.cancel')
      }}</el-button>
      <el-button size="small" type="primary" :disabled="disabled" @click="assistant.decide(action, true, visibility)">
        {{ $t('chat.assistant.confirm') }}
      </el-button>
    </footer>
    <router-link v-else-if="action.status === 'DONE' && path" :to="path">{{ $t('chat.assistant.open') }}</router-link>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useAssistantStore } from '@/stores/assistant'
import { getDisplayMode } from '@/composables/useDisplayMode'
import { assistantPath } from '@/utils/assistantNavigation'
const props = defineProps({ action: { type: Object, required: true } })
const assistant = useAssistantStore()
const diary = computed(() => props.action.toolName === 'create_diary')
const visibility = ref(props.action.visibility === 'FAMILY' ? 'FAMILY' : 'PRIVATE')
const disabled = computed(() => assistant.busy || !assistant.status?.enabled)
const path = computed(() => assistantPath(props.action.result?.path, getDisplayMode() === 'child'))
</script>

<style scoped>
.assistant-action {
  margin-top: 8px;
  padding: 10px;
  border: 1px solid var(--color-border);
  border-radius: 14px;
  background: var(--color-card);
}
header,
footer,
label {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
header {
  justify-content: space-between;
}
header span,
label {
  color: var(--color-text-secondary);
  font-size: 12px;
}
p {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  margin: 8px 0;
}
footer {
  margin-top: 10px;
}
select {
  max-width: 100%;
  background: var(--color-card);
  color: inherit;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 4px;
}
</style>
