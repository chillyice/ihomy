<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('tools.title') }, { label: $t('tools.aiPlayground.title') }]" />

    <div class="page-toolbar card">
      <div class="tb-left">
        <el-radio-group v-model="capability" size="small">
          <el-radio-button value="chat">{{ $t('tools.aiPlayground.chat') }}</el-radio-button>
          <el-radio-button value="image">{{ $t('tools.aiPlayground.image') }}</el-radio-button>
          <el-radio-button value="asr">{{ $t('tools.aiPlayground.asr') }}</el-radio-button>
        </el-radio-group>
        <template v-if="statusLoaded">
          <el-tag v-if="currentStatus.model" size="small" type="info">{{ currentStatus.model }}</el-tag>
          <el-tag size="small" :type="currentStatus.available ? 'success' : 'danger'">
            {{ currentStatus.available ? $t('tools.aiPlayground.ready') : $t('tools.aiPlayground.notReady') }}
          </el-tag>
        </template>
      </div>
    </div>

    <el-alert
      v-if="statusLoaded && !currentStatus.available"
      :title="$t('tools.aiPlayground.notReadyTip')"
      type="warning"
      :closable="false"
      show-icon
      class="pg-alert"
    />

    <!-- 对话:多轮消息 + 系统提示词 + 温度 -->
    <div v-if="capability === 'chat'" class="pg-card card">
      <div class="pg-row">
        <span class="section-label">{{ $t('tools.aiPlayground.systemPrompt') }}</span>
        <el-input v-model="chatSystem" type="textarea" :rows="2" :placeholder="$t('tools.aiPlayground.systemPlaceholder')" />
      </div>
      <div class="pg-row pg-temp-row">
        <span class="section-label">{{ $t('tools.aiPlayground.temperature') }}</span>
        <div class="pg-temp">
          <el-slider v-model="chatTemperature" :min="0" :max="2" :step="0.1" />
          <span class="pg-temp-val">{{ chatTemperature.toFixed(1) }}</span>
        </div>
      </div>
      <div ref="chatListRef" class="pg-chat-list">
        <div v-for="(m, i) in chatMessages" :key="i" class="pg-msg" :class="m.role">
          <div class="pg-msg-bubble">{{ m.content }}</div>
        </div>
        <div v-if="!chatMessages.length" class="pg-empty">{{ $t('tools.aiPlayground.chatEmpty') }}</div>
      </div>
      <div class="pg-input-row">
        <el-input
          v-model="chatInput"
          type="textarea"
          :rows="2"
          :placeholder="$t('tools.aiPlayground.chatPlaceholder')"
          @keydown.enter.exact.prevent="sendChat"
        />
        <div class="pg-input-actions">
          <el-button size="small" @click="clearChat">{{ $t('tools.aiPlayground.clear') }}</el-button>
          <el-button type="primary" :loading="chatLoading" @click="sendChat">{{ $t('tools.aiPlayground.send') }}</el-button>
        </div>
      </div>
      <div v-if="chatMeta" class="pg-meta">{{ chatMeta }}</div>
    </div>

    <!-- 图片生成:prompt + 尺寸档位 + 张数(豆包 Seedream 参数面板) -->
    <div v-else-if="capability === 'image'" class="pg-card card">
      <div class="pg-row">
        <span class="section-label">{{ $t('tools.aiPlayground.imagePrompt') }}</span>
        <el-input v-model="imagePrompt" type="textarea" :rows="3" :placeholder="$t('tools.aiPlayground.imagePlaceholder')" />
      </div>
      <div class="pg-inline">
        <div class="pg-field">
          <span class="section-label">{{ $t('tools.aiPlayground.imageSize') }}</span>
          <el-select v-model="imageSize" size="small" style="width: 170px">
            <el-option :label="$t('tools.aiPlayground.sizeDefault')" value="" />
            <el-option v-for="s in sizePresets" :key="s" :label="s" :value="s" />
          </el-select>
        </div>
        <div class="pg-field">
          <span class="section-label">{{ $t('tools.aiPlayground.imageCount') }}</span>
          <el-input-number v-model="imageCount" :min="1" :max="4" size="small" />
        </div>
        <el-button type="primary" :loading="imageLoading" @click="genImage">{{ $t('tools.aiPlayground.generate') }}</el-button>
      </div>
      <div class="pg-hint">{{ $t('tools.aiPlayground.sizeTierNote') }}</div>
      <div v-if="imageLoading" v-loading="imageLoading" class="pg-img-loading" />
      <div v-if="imageResults.length" class="pg-img-grid">
        <div v-for="(img, i) in imageResults" :key="i" class="pg-img-item">
          <el-image :src="img.url" :preview-src-list="[img.url]" fit="cover" loading="lazy" hide-on-click-modal />
          <el-button size="small" text @click="copyUrl(img.url)">{{ $t('tools.aiPlayground.copyUrl') }}</el-button>
        </div>
      </div>
      <div v-if="imageMeta" class="pg-meta">{{ imageMeta }}</div>
    </div>

    <!-- 语音识别:音频文件 + 语言(SenseVoice 参数面板) -->
    <div v-else class="pg-card card">
      <div class="pg-inline">
        <input ref="audioInputRef" type="file" accept="audio/*" hidden @change="onAudioPick" />
        <el-button :disabled="!currentStatus.available" @click="audioInputRef && audioInputRef.click()">
          {{ $t('tools.aiPlayground.pickAudio') }}
        </el-button>
        <span v-if="audioFile" class="pg-file">{{ audioFile.name }} ({{ Math.round(audioFile.size / 1024) }}KB)</span>
        <el-select
          v-model="audioLanguage"
          size="small"
          style="width: 130px"
          clearable
          :placeholder="$t('tools.aiPlayground.language')"
        >
          <el-option label="中文 (zh)" value="zh" />
          <el-option label="English (en)" value="en" />
        </el-select>
        <el-button
          type="primary"
          :loading="asrLoading"
          :disabled="!audioFile || !currentStatus.available"
          @click="doTranscribe"
        >
          {{ $t('tools.aiPlayground.transcribe') }}
        </el-button>
      </div>
      <div v-if="asrResult" class="pg-asr-result">{{ asrResult }}</div>
      <div v-if="asrMeta" class="pg-meta">{{ asrMeta }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { aiApi } from '@/api'
import Breadcrumb from '@/components/Breadcrumb.vue'

const { t } = useI18n()

// ---- 能力状态(external.yml 配置驱动,未配置能力禁用并提示) ----
const capability = ref('chat')
const statusMap = ref({})
const statusLoaded = ref(false)
const currentStatus = computed(() => statusMap.value[capability.value] || { available: false, model: '' })

onMounted(async () => {
  try {
    statusMap.value = await aiApi.status()
    statusLoaded.value = true
  } catch (e) { /* toast 已由 request.js 统一弹出 */ }
})

// ---- 对话 ----
const chatSystem = ref('')
const chatTemperature = ref(0.7)
const chatMessages = ref([])
const chatInput = ref('')
const chatLoading = ref(false)
const chatMeta = ref('')
const chatListRef = ref(null)

const buildMessages = () => {
  const history = chatMessages.value.map((m) => ({ role: m.role, content: m.content }))
  const sys = chatSystem.value.trim()
  return sys ? [{ role: 'system', content: sys }, ...history] : history
}

const scrollChat = () => nextTick(() => {
  if (chatListRef.value) chatListRef.value.scrollTop = chatListRef.value.scrollHeight
})

const sendChat = async () => {
  const text = chatInput.value.trim()
  if (!text || chatLoading.value) return
  chatMessages.value.push({ role: 'user', content: text })
  chatInput.value = ''
  chatLoading.value = true
  try {
    const res = await aiApi.chat({ messages: buildMessages(), temperature: chatTemperature.value })
    chatMessages.value.push({ role: 'assistant', content: res.content })
    chatMeta.value = `${res.elapsedMs}ms`
  } catch (e) {
    // 报错 toast 已由 request.js 统一弹出;保留用户消息便于改参重发
  } finally {
    chatLoading.value = false
    scrollChat()
  }
}

const clearChat = () => { chatMessages.value = []; chatMeta.value = '' }

// ---- 图片生成 ----
// Seedream 5.0 模型要求总像素 ≥ 3686400(≥1920×1920),小尺寸会被方舟 400 拒绝
const sizePresets = ['2048x2048', '1920x1920', '1440x2560', '2560x1440', '2K', '4K']
const imagePrompt = ref('')
const imageSize = ref('')
const imageCount = ref(1)
const imageLoading = ref(false)
const imageResults = ref([])
const imageMeta = ref('')

const genImage = async () => {
  const prompt = imagePrompt.value.trim()
  if (!prompt || imageLoading.value) return
  imageLoading.value = true
  imageResults.value = []
  imageMeta.value = ''
  const start = Date.now()
  try {
    const data = await aiApi.image({ prompt, size: imageSize.value || null, n: imageCount.value })
    imageResults.value = data.map((d) => ({
      url: d.url || (d.b64_json ? 'data:image/png;base64,' + d.b64_json : ''),
    })).filter((d) => d.url)
    imageMeta.value = `${imageResults.value.length} · ${Date.now() - start}ms`
  } catch (e) {
    // 报错 toast 已由 request.js 统一弹出
  } finally {
    imageLoading.value = false
  }
}

const copyUrl = async (url) => {
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success(t('tools.aiPlayground.copied'))
  } catch (e) { /* 剪贴板权限失败静默 */ }
}

// ---- 语音识别 ----
const audioInputRef = ref(null)
const audioFile = ref(null)
const audioLanguage = ref('')
const asrLoading = ref(false)
const asrResult = ref('')
const asrMeta = ref('')

const onAudioPick = (e) => {
  audioFile.value = e.target.files && e.target.files[0] ? e.target.files[0] : null
  e.target.value = ''
}

const doTranscribe = async () => {
  if (!audioFile.value || asrLoading.value) return
  asrLoading.value = true
  asrResult.value = ''
  asrMeta.value = ''
  const start = Date.now()
  try {
    const res = await aiApi.transcribe(audioFile.value, audioLanguage.value || null)
    asrResult.value = res.text || '(空)'
    asrMeta.value = `${Date.now() - start}ms`
  } catch (e) {
    // 报错 toast 已由 request.js 统一弹出
  } finally {
    asrLoading.value = false
  }
}
</script>

<style scoped>
.pg-alert { margin-bottom: 16px; }
.pg-card { padding: 18px 20px; }
.pg-row { margin-bottom: 14px; display: flex; flex-direction: column; gap: 8px; }
.pg-temp-row { flex-direction: row; align-items: center; gap: 14px; }
.pg-temp { flex: 1; display: flex; align-items: center; gap: 10px; max-width: 420px; }
.pg-temp :deep(.el-slider) { flex: 1; }
.pg-temp-val { font-size: 13px; color: var(--color-text-secondary, #7a6b5a); min-width: 28px; text-align: right; }
.pg-chat-list {
  max-height: 420px;
  min-height: 120px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--color-border, rgba(58, 46, 34, 0.12));
  border-radius: 10px;
  background: var(--color-bg-2, #e2d5bc);
  margin-bottom: 12px;
  contain: layout style;
}
.pg-msg { display: flex; }
.pg-msg.user { justify-content: flex-end; }
.pg-msg-bubble {
  max-width: 78%;
  padding: 8px 12px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.pg-msg.user .pg-msg-bubble { background: var(--color-accent, #a8483a); color: #fff; }
.pg-msg.assistant .pg-msg-bubble { background: var(--color-card, #faf6ec); color: var(--color-text, #3a2e22); }
.pg-empty { margin: auto; font-size: 13px; color: var(--color-text-secondary, #7a6b5a); }
.pg-input-row { display: flex; gap: 12px; align-items: flex-end; }
.pg-input-row :deep(.el-textarea) { flex: 1; }
.pg-input-actions { display: flex; flex-direction: column; gap: 8px; }
.pg-meta { margin-top: 10px; font-size: 12px; color: var(--color-text-secondary, #7a6b5a); }
.pg-inline { display: flex; align-items: flex-end; gap: 14px; flex-wrap: wrap; }
.pg-field { display: flex; flex-direction: column; gap: 6px; }
.pg-hint { margin-top: 8px; font-size: 12px; color: var(--color-text-secondary, #7a6b5a); }
.pg-img-loading { height: 200px; border-radius: 10px; }
.pg-img-grid {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
}
.pg-img-item { display: flex; flex-direction: column; gap: 6px; }
.pg-img-item :deep(.el-image) {
  width: 100%;
  aspect-ratio: 1;
  border-radius: 10px;
  background: var(--color-bg-2, #e2d5bc);
  overflow: hidden;
}
.pg-file { font-size: 13px; color: var(--color-text-secondary, #7a6b5a); }
.pg-asr-result {
  margin-top: 14px;
  padding: 12px;
  border: 1px solid var(--color-border, rgba(58, 46, 34, 0.12));
  border-radius: 10px;
  background: var(--color-card-2, #e8dec8);
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
