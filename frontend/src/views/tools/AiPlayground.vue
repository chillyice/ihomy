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

    <!-- 对话:左参数(系统提示词+更多设置) / 右对话窗口 —— 输入输出对齐全高 -->
    <div v-if="capability === 'chat'" class="pg-split">
      <div class="pg-panel pg-input">
        <div class="pg-panel-head">{{ $t('tools.aiPlayground.inputPanel') }}</div>
        <div class="pg-panel-body">
          <div class="pg-field-block">
            <span class="section-label">{{ $t('tools.aiPlayground.systemPrompt') }}</span>
            <el-input v-model="chatSystem" type="textarea" :rows="3" resize="none" :placeholder="$t('tools.aiPlayground.systemPlaceholder')" />
          </div>
          <div class="pg-more">
            <div class="pg-more-toggle" @click="chatMore = !chatMore">
              <span>{{ $t('tools.aiPlayground.moreSettings') }}</span>
              <span class="pg-more-hint">{{ $t('tools.aiPlayground.temperature') }}</span>
              <span class="pg-more-chev">{{ chatMore ? '▴' : '▾' }}</span>
            </div>
            <el-collapse-transition>
              <div v-if="chatMore" class="pg-more-body">
                <span class="section-label">{{ $t('tools.aiPlayground.temperature') }}</span>
                <div class="pg-temp">
                  <el-slider v-model="chatTemperature" :min="0" :max="2" :step="0.1" />
                  <span class="pg-temp-val">{{ chatTemperature.toFixed(1) }}</span>
                </div>
              </div>
            </el-collapse-transition>
          </div>
          <div class="pg-panel-actions">
            <el-button size="small" :disabled="!chatMessages.length" @click="clearChat">
              {{ $t('tools.aiPlayground.clear') }}
            </el-button>
          </div>
        </div>
      </div>

      <div class="pg-panel pg-output">
        <div class="pg-panel-head">{{ $t('tools.aiPlayground.outputPanel') }}</div>
        <div class="pg-panel-body pg-chat-output">
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
              resize="none"
              :placeholder="$t('tools.aiPlayground.chatPlaceholder')"
              @keydown.enter.exact.prevent="sendChat"
            />
            <el-button type="primary" :disabled="!chatInput.trim()" :loading="chatLoading" @click="sendChat">
              {{ $t('tools.aiPlayground.send') }}
            </el-button>
          </div>
          <div v-if="chatMeta" class="pg-meta">{{ chatMeta }}</div>
        </div>
      </div>
    </div>

    <!-- 图片生成:左输入(prompt+参考图+常用参数+更多设置) / 右结果图 —— 输入输出对齐全高 -->
    <div v-else-if="capability === 'image'" class="pg-split">
      <div class="pg-panel pg-input">
        <div class="pg-panel-head">{{ $t('tools.aiPlayground.inputPanel') }}</div>
        <div class="pg-panel-body">
          <div class="pg-field-block">
            <span class="section-label">{{ $t('tools.aiPlayground.imagePrompt') }}</span>
            <el-input v-model="imagePrompt" type="textarea" :rows="4" resize="none" :placeholder="$t('tools.aiPlayground.imagePlaceholder')" />
          </div>

          <div class="pg-field-block">
            <span class="section-label">{{ $t('tools.aiPlayground.refImages') }}</span>
            <input ref="refInputRef" type="file" accept="image/*" multiple hidden @change="onRefPick" />
            <div class="pg-ref-list">
              <div v-for="(img, i) in refImages" :key="i" class="pg-ref-item">
                <img :src="img.dataUrl" :alt="img.name" />
                <span class="pg-ref-del" @click="removeRefImage(i)">×</span>
              </div>
              <el-button v-if="refImages.length < 10" size="small" plain @click="refInputRef && refInputRef.click()">+ {{ $t('tools.aiPlayground.addRef') }}</el-button>
            </div>
          </div>

          <!-- 常用参数:尺寸放在外面 -->
          <div class="pg-field-block">
            <span class="section-label">{{ $t('tools.aiPlayground.imageSize') }}</span>
            <el-select v-model="imageSize" size="small" style="width: 100%">
              <el-option :label="$t('tools.aiPlayground.sizeDefault')" value="" />
              <el-option v-for="s in sizePresets" :key="s" :label="s" :value="s" />
            </el-select>
            <span class="pg-hint">{{ sizeTierNote }}</span>
          </div>

          <!-- 专业参数:张数/种子/引导/组图/水印/响应格式进「更多设置」 -->
          <div class="pg-more">
            <div class="pg-more-toggle" @click="imageMore = !imageMore">
              <span>{{ $t('tools.aiPlayground.moreSettings') }}</span>
              <span class="pg-more-hint">{{ $t('tools.aiPlayground.advancedHint') }}</span>
              <span class="pg-more-chev">{{ imageMore ? '▴' : '▾' }}</span>
            </div>
            <el-collapse-transition>
              <div v-if="imageMore" class="pg-more-body pg-more-grid">
                <div class="pg-field">
                  <span class="section-label">{{ $t('tools.aiPlayground.imageCount') }}</span>
                  <el-input-number v-model="imageCount" :min="1" :max="imageCountMax" size="small" :disabled="isProImage" />
                </div>
                <div class="pg-field">
                  <span class="section-label">{{ $t('tools.aiPlayground.seed') }}</span>
                  <el-input-number v-model="imageSeed" :min="-1" :max="2147483647" :controls="false" size="small" />
                  <span class="pg-field-hint">{{ $t('tools.aiPlayground.seedRandom') }}</span>
                </div>
                <div class="pg-field pg-field-wide">
                  <span class="section-label">{{ $t('tools.aiPlayground.guidance') }}</span>
                  <div class="pg-param-inline">
                    <el-checkbox v-model="guidanceCustom" size="small">{{ $t('tools.aiPlayground.guidanceCustom') }}</el-checkbox>
                    <el-slider v-model="imageGuidance" :min="1" :max="10" :step="0.5" :disabled="!guidanceCustom" class="pg-slider" />
                    <span v-if="guidanceCustom" class="pg-temp-val">{{ imageGuidance }}</span>
                  </div>
                </div>
                <div v-if="!isProImage" class="pg-field pg-field-wide">
                  <span class="section-label">{{ $t('tools.aiPlayground.sequential') }}</span>
                  <div class="pg-param-inline">
                    <el-select v-model="imageSequential" size="small" style="width: 120px">
                      <el-option :label="$t('tools.aiPlayground.sequentialOff')" value="disabled" />
                      <el-option :label="$t('tools.aiPlayground.sequentialAuto')" value="auto" />
                    </el-select>
                    <template v-if="imageSequential === 'auto'">
                      <span class="pg-field-hint">{{ $t('tools.aiPlayground.maxImages') }}</span>
                      <el-input-number v-model="imageMaxImages" :min="1" :max="10" size="small" style="width: 90px" />
                    </template>
                  </div>
                </div>
                <div class="pg-field">
                  <span class="section-label">{{ $t('tools.aiPlayground.watermark') }}</span>
                  <el-switch v-model="imageWatermark" />
                </div>
                <div class="pg-field">
                  <span class="section-label">{{ $t('tools.aiPlayground.responseFormat') }}</span>
                  <el-select v-model="imageRf" size="small" style="width: 120px">
                    <el-option label="url" value="url" />
                    <el-option label="b64_json" value="b64_json" />
                  </el-select>
                </div>
              </div>
            </el-collapse-transition>
            <span v-if="!imageMore" class="pg-hint">{{ $t('tools.aiPlayground.paramsNote') }}</span>
          </div>

          <div class="pg-panel-actions">
            <el-button type="primary" :disabled="!imagePrompt.trim()" :loading="imageLoading" @click="genImage">
              {{ $t('tools.aiPlayground.generate') }}
            </el-button>
          </div>
        </div>
      </div>

      <div class="pg-panel pg-output">
        <div class="pg-panel-head">{{ $t('tools.aiPlayground.outputPanel') }}</div>
        <div class="pg-panel-body pg-image-output">
          <div v-if="imageLoading" v-loading="imageLoading" class="pg-img-loading" />
          <div v-else-if="imageResults.length" class="pg-img-grid">
            <div v-for="(img, i) in imageResults" :key="i" class="pg-img-item">
              <el-image :src="img.url" :preview-src-list="[img.url]" fit="cover" loading="lazy" hide-on-click-modal />
              <el-button size="small" text @click="copyUrl(img.url)">{{ $t('tools.aiPlayground.copyUrl') }}</el-button>
            </div>
          </div>
          <div v-else class="pg-empty pg-output-empty">{{ $t('tools.aiPlayground.imageEmpty') }}</div>
        </div>
        <div v-if="imageMeta" class="pg-meta pg-output-meta">{{ imageMeta }}</div>
      </div>
    </div>

    <!-- 语音识别:左输入(音频+语言) / 右转写结果 —— 输入输出对齐全高 -->
    <div v-else class="pg-split">
      <div class="pg-panel pg-input">
        <div class="pg-panel-head">{{ $t('tools.aiPlayground.inputPanel') }}</div>
        <div class="pg-panel-body">
          <input ref="audioInputRef" type="file" accept="audio/*" hidden @change="onAudioPick" />
          <div class="pg-field-block">
            <span class="section-label">{{ $t('tools.aiPlayground.pickAudio') }}</span>
            <el-button :disabled="!currentStatus.available" @click="audioInputRef && audioInputRef.click()">
              {{ $t('tools.aiPlayground.pickAudio') }}
            </el-button>
            <span v-if="audioFile" class="pg-file">{{ audioFile.name }} ({{ Math.round(audioFile.size / 1024) }}KB)</span>
          </div>
          <div class="pg-field-block">
            <span class="section-label">{{ $t('tools.aiPlayground.language') }}</span>
            <el-select v-model="audioLanguage" size="small" style="width: 100%" clearable :placeholder="$t('tools.aiPlayground.language')">
              <el-option label="中文 (zh)" value="zh" />
              <el-option label="English (en)" value="en" />
            </el-select>
          </div>
          <div class="pg-panel-actions">
            <el-button type="primary" :disabled="!audioFile || !currentStatus.available" :loading="asrLoading" @click="doTranscribe">
              {{ $t('tools.aiPlayground.transcribe') }}
            </el-button>
          </div>
        </div>
      </div>

      <div class="pg-panel pg-output">
        <div class="pg-panel-head">{{ $t('tools.aiPlayground.outputPanel') }}</div>
        <div class="pg-panel-body pg-asr-output">
          <div v-if="asrResult" class="pg-asr-result">{{ asrResult }}</div>
          <div v-else class="pg-empty pg-output-empty">{{ $t('tools.aiPlayground.asrEmpty') }}</div>
        </div>
        <div v-if="asrMeta" class="pg-meta pg-output-meta">{{ asrMeta }}</div>
      </div>
    </div>

    <!-- ===== 天气背景 AI 生成(关联设置) ===== -->
    <div class="card pg-weather">
      <button :class="['pg-weatherhead', { on: weatherOpen }]" type="button" @click="weatherOpen = !weatherOpen">
        <span class="pg-weathertitle">{{ $t('tools.aiPlayground.weatherBgTitle') }}</span>
        <span class="pg-more-chev">{{ weatherOpen ? '▴' : '▾' }}</span>
      </button>
      <p class="pg-weatherhint">{{ $t('tools.aiPlayground.weatherBgHint') }}</p>
      <el-collapse-transition>
        <div v-if="weatherOpen" class="pg-weatherbody">
          <div class="pg-more-grid">
            <div class="pg-field">
              <span class="section-label">{{ $t('tools.aiPlayground.weatherBgEnabled') }}</span>
              <el-switch v-model="weatherCfg.enabled" />
            </div>
            <div class="pg-field">
              <span class="section-label">{{ $t('tools.aiPlayground.weatherBgStyle') }}</span>
              <el-select v-model="weatherCfg.style" size="small" style="width: 100%">
                <el-option v-for="s in weatherStyles" :key="s" :label="s" :value="s" />
              </el-select>
            </div>
            <div class="pg-field">
              <span class="section-label">{{ $t('tools.aiPlayground.weatherBgSize') }}</span>
              <el-select v-model="weatherCfg.size" size="small" style="width: 100%">
                <el-option v-for="s in weatherSizes" :key="s" :label="s" :value="s" />
              </el-select>
            </div>
            <div class="pg-field">
              <span class="section-label">{{ $t('tools.aiPlayground.weatherBgRefresh') }}</span>
              <el-input-number v-model="weatherCfg.refreshDays" :min="1" :max="30" size="small" style="width: 100%" />
            </div>
            <div class="pg-field">
              <span class="section-label">{{ $t('tools.aiPlayground.weatherBgWatermark') }}</span>
              <el-switch v-model="weatherCfg.watermark" />
            </div>
            <div class="pg-field pg-field-wide">
              <span class="section-label">{{ $t('tools.aiPlayground.weatherBgScene') }}</span>
              <el-input v-model="weatherCfg.scene" type="textarea" :rows="2" :placeholder="$t('tools.aiPlayground.weatherBgScenePlaceholder')" />
            </div>
          </div>
          <div class="pg-panel-actions">
            <el-button type="primary" size="small" @click="saveWeatherCfg">{{ $t('tools.aiPlayground.weatherBgSave') }}</el-button>
          </div>
        </div>
      </el-collapse-transition>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch } from 'vue'
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
  loadWeatherCfg()
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
const chatMore = ref(false) // 更多设置(温度)展开态

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
// Seedream 5.0 lite 要求总像素 ≥ 3686400(≥1920×1920);Seedream 5.0 pro 仅单图、档位 1K/1.5K/2K、
// 宽x高总像素 [921600,4624220] —— 按绑定的图片模型切换尺寸预设/提示/组图开关(避免把 lite 规则套到 pro 上)
const isProImage = computed(() => {
  const m = (statusMap.value.image?.model || '').toLowerCase()
  return m.includes('seedream') && m.includes('pro')
})
const sizePresets = computed(() => (isProImage.value
  ? ['1K', '1.5K', '2K']
  : ['2048x2048', '1920x1920', '1440x2560', '2560x1440', '2K', '4K', 'adaptive']))
const sizeTierNote = computed(() => (isProImage.value ? t('tools.aiPlayground.sizeTierNotePro') : t('tools.aiPlayground.sizeTierNote')))
const imageCountMax = computed(() => (isProImage.value ? 1 : 4))
const imagePrompt = ref('')
const imageSize = ref('')
const imageCount = ref(1)
const imageSeed = ref(-1) // -1=随机(不传)
const imageGuidance = ref(2.5)
const guidanceCustom = ref(false) // 关=不发送,用模型默认
const imageWatermark = ref(true)
const imageRf = ref('url')
const imageSequential = ref('disabled')
const imageMaxImages = ref(4)
const refImages = ref([]) // 参考图 [{name, dataUrl}]
const refInputRef = ref(null)
const imageLoading = ref(false)
const imageResults = ref([])
const imageMeta = ref('')
const imageMore = ref(false) // 更多设置(高级参数)展开态

// 绑定的图片模型切换时,若为 pro(不支持组图)则复位组图开关,避免残留 auto
watch(isProImage, (pro) => {
  if (pro) imageSequential.value = 'disabled'
})

const onRefPick = (e) => {
  for (const f of e.target.files || []) {
    if (refImages.value.length >= 10) break
    const reader = new FileReader()
    reader.onload = () => refImages.value.push({ name: f.name, dataUrl: reader.result })
    reader.readAsDataURL(f)
  }
  e.target.value = ''
}
const removeRefImage = (i) => { refImages.value.splice(i, 1) }

const genImage = async () => {
  const prompt = imagePrompt.value.trim()
  if (!prompt || imageLoading.value) return
  imageLoading.value = true
  imageResults.value = []
  imageMeta.value = ''
  const start = Date.now()
  try {
    const groupMode = imageSequential.value === 'auto'
    const data = await aiApi.image({
      prompt,
      size: imageSize.value || null,
      n: groupMode ? null : (isProImage.value ? 1 : imageCount.value),
      imageUrls: refImages.value.map((r) => r.dataUrl),
      seed: imageSeed.value >= 0 ? imageSeed.value : null,
      guidanceScale: guidanceCustom.value ? imageGuidance.value : null,
      watermark: imageWatermark.value,
      responseFormat: imageRf.value,
      sequentialMode: groupMode ? 'auto' : null,
      sequentialMaxImages: groupMode ? imageMaxImages.value : null,
    })
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

// ---- 天气背景 AI 生成配置(localStorage 持久化,作用于首页「天气」组件) ----
const WEATHER_CFG_KEY = 'ihomy:weather-bg-config:v1'
const weatherStyles = ['温柔插画风格', '写实摄影质感', '水彩手绘风', '油画细腻笔触', '极简扁平插画']
const weatherSizes = ['2048x2048', '2560x1440', '1440x2560', '1920x1920']
const weatherOpen = ref(false)
const weatherCfg = ref({ enabled: true, style: '温柔插画风格', size: '2048x2048', refreshDays: 7, scene: '', watermark: false })
const loadWeatherCfg = () => {
  try {
    const raw = localStorage.getItem(WEATHER_CFG_KEY)
    if (raw) weatherCfg.value = { ...weatherCfg.value, ...JSON.parse(raw) }
  } catch (e) { /* ignore */ }
}
const saveWeatherCfg = () => {
  try {
    localStorage.setItem(WEATHER_CFG_KEY, JSON.stringify(weatherCfg.value))
    ElMessage.success(t('tools.aiPlayground.weatherBgSaved'))
  } catch (e) { ElMessage.error('保存失败') }
}
</script>

<style scoped>
.pg-alert { margin-bottom: 16px; }

/* ---- 左右分栏:输入(l) / 输出(r) 对齐全高,窄屏纵向堆叠 ---- */
.pg-split { display: flex; gap: 18px; align-items: stretch; }
.pg-panel {
  flex: 1 1 auto; min-width: 0;
  display: flex; flex-direction: column;
  border: 1px solid var(--color-border, rgba(58, 46, 34, 0.12));
  border-radius: 14px;
  background: var(--color-card, #faf6ec);
  overflow: hidden;
}
.pg-input { flex: 0 0 40%; max-width: 420px; }
.pg-output { flex: 1 1 auto; }

.pg-panel-head {
  padding: 10px 14px;
  font-size: 12px; font-weight: 600; letter-spacing: 0.06em;
  color: var(--color-text-secondary, #7a6b5a);
  background: var(--color-bg-2, #e2d5bc);
  border-bottom: 1px solid var(--color-border, rgba(58, 46, 34, 0.1));
  flex-shrink: 0;
}
.pg-panel-body {
  flex: 1; min-height: 300px;
  display: flex; flex-direction: column; gap: 14px;
  padding: 14px;
}
.pg-field-block { display: flex; flex-direction: column; gap: 8px; }
.pg-field { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.pg-field-wide { grid-column: 1 / -1; }
.pg-field-hint { font-size: 12px; color: var(--color-text-secondary, #7a6b5a); }
.pg-param-inline { display: flex; align-items: center; gap: 8px; min-width: 0; }
.pg-slider { width: 140px; flex-shrink: 2; }
.pg-meta { font-size: 12px; color: var(--color-text-secondary, #7a6b5a); }
.pg-hint { font-size: 12px; color: var(--color-text-secondary, #7a6b5a); line-height: 1.5; }

/* 输入类操作按钮固定在面板底部 */
.pg-panel-actions { margin-top: auto; display: flex; justify-content: flex-end; gap: 8px; }

/* ---- 「更多设置」折叠区 ---- */
.pg-more { border-top: 1px solid var(--color-border, rgba(58,46,34,0.1)); padding-top: 8px; }
.pg-more-toggle {
  display: flex; align-items: center; gap: 8px;
  padding: 6px 2px; cursor: pointer; user-select: none;
  font-size: 13px; font-weight: 500; color: var(--color-text, #3a2e22);
  border-radius: 8px;
}
.pg-more-toggle:hover { color: var(--color-accent, #a8483a); }
.pg-more-hint { flex: 1; font-size: 11px; color: var(--color-text-secondary, #7a6b5a); }
.pg-more-chev { font-size: 11px; color: var(--color-text-secondary, #7a6b5a); }
.pg-more-body { display: flex; flex-direction: column; gap: 12px; padding: 10px 2px 4px; }
.pg-more-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px 14px; }

/* 温度滑杆 */
.pg-temp { display: flex; align-items: center; gap: 10px; }
.pg-temp :deep(.el-slider) { flex: 1; }
.pg-temp-val { font-size: 13px; color: var(--color-text-secondary, #7a6b5a); min-width: 28px; text-align: right; }

/* ---- 对话输出(右侧消息窗) ---- */
.pg-chat-output { gap: 10px; }
.pg-chat-list {
  flex: 1; min-height: 0;
  overflow-y: auto;
  display: flex; flex-direction: column; gap: 10px;
  padding: 12px;
  border: 1px solid var(--color-border, rgba(58, 46, 34, 0.12));
  border-radius: 10px;
  background: var(--color-bg-2, #e2d5bc);
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
.pg-input-row { display: flex; gap: 10px; align-items: flex-end; }
.pg-input-row :deep(.el-textarea) { flex: 1; }

/* ---- 图片输出(右侧结果) ---- */
.pg-image-output { gap: 12px; }
.pg-img-loading { flex: 1; min-height: 200px; border-radius: 10px; }
.pg-img-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
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
.pg-output-empty { align-self: center; text-align: center; }

/* ---- 语音转写输出 ---- */
.pg-asr-result {
  padding: 12px;
  border: 1px solid var(--color-border, rgba(58, 46, 34, 0.12));
  border-radius: 10px;
  background: var(--color-card-2, #e8dec8);
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 输出面板底部耗时条 */
.pg-output-meta { padding: 0 14px 10px; margin-top: auto; flex-shrink: 0; }

/* 参考图 */
.pg-ref-list { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.pg-ref-item {
  position: relative;
  width: 56px; height: 56px;
  border-radius: 8px; overflow: hidden;
  border: 1px solid var(--color-border, rgba(58, 46, 34, 0.12));
}
.pg-ref-item img { width: 100%; height: 100%; object-fit: cover; display: block; }
.pg-ref-del {
  position: absolute; top: 0; right: 0;
  width: 16px; height: 16px; line-height: 14px; text-align: center;
  background: rgba(0, 0, 0, 0.55); color: #fff; font-size: 12px; cursor: pointer;
  border-radius: 0 0 0 8px;
}
.pg-file { font-size: 13px; color: var(--color-text-secondary, #7a6b5a); }

/* 窄屏:输入/输出纵向堆叠 */
@media (max-width: 920px) {
  .pg-split { flex-direction: column; }
  .pg-input { flex: none; max-width: none; }
  .pg-panel-body { min-height: 200px; }
}

/* ---- 天气背景 AI 生成(关联设置,独立卡片) ---- */
.pg-weather { margin-top: 16px; padding: 20px; }
.pg-weatherhead { display: flex; align-items: center; justify-content: space-between; gap: 10px; width: 100%; padding: 0; background: transparent; border: none; cursor: pointer; color: inherit; }
.pg-weathertitle { font-size: 14px; font-weight: 600; color: var(--color-text, #3a2e22); }
.pg-weatherhint { margin: 0 0 4px; font-size: 12px; color: var(--color-text-secondary, #7a6b5a); line-height: 1.6; }
.pg-weatherbody { display: flex; flex-direction: column; gap: 14px; padding-top: 12px; border-top: 1px dashed var(--color-border, rgba(58, 46, 34, 0.12)); }
</style>
