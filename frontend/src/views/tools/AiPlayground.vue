<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('tools.title') }, { label: $t('tools.aiPlayground.title') }]" />

    <!-- 能力切换 + 模型状态 -->
    <div class="wk-bar card">
      <el-radio-group v-model="capability" size="small" class="wk-tabs">
        <el-radio-button value="chat">{{ $t('tools.aiPlayground.chat') }}</el-radio-button>
        <el-radio-button value="image">{{ $t('tools.aiPlayground.image') }}</el-radio-button>
        <el-radio-button value="asr">{{ $t('tools.aiPlayground.asr') }}</el-radio-button>
      </el-radio-group>
      <div v-if="statusLoaded" class="wk-status">
        <el-tag v-if="currentStatus.model" size="small" type="info">{{ currentStatus.model }}</el-tag>
        <el-tag size="small" :type="currentStatus.available ? 'success' : 'danger'">
          {{ currentStatus.available ? $t('tools.aiPlayground.ready') : $t('tools.aiPlayground.notReady') }}
        </el-tag>
      </div>
    </div>

    <el-alert
      v-if="statusLoaded && !currentStatus.available"
      :title="$t('tools.aiPlayground.notReadyTip')"
      type="warning"
      :closable="false"
      show-icon
      class="wk-alert"
    />

    <!-- ===== 对话:指令(左) | 舞台(右) ===== -->
    <div v-if="capability === 'chat'" class="wk-grid">
      <section class="wk-deck card">
        <div class="wk-title">{{ $t('tools.aiPlayground.panelInput') }}</div>
        <div class="wk-block">
          <span class="wk-eyebrow">{{ $t('tools.aiPlayground.zoneContent') }}</span>
          <el-input v-model="chatSystem" type="textarea" :rows="2" :placeholder="$t('tools.aiPlayground.systemPlaceholder')" />
        </div>
        <div class="wk-block wk-adv">
          <button :class="['wk-advbtn', { on: chatAdvOpen }]" type="button" @click="chatAdvOpen = !chatAdvOpen">
            <span>{{ chatAdvOpen ? $t('tools.aiPlayground.advCollapse') : $t('tools.aiPlayground.advExpand') }}</span>
            <i class="wk-chev"></i>
          </button>
          <el-collapse-transition>
            <div v-if="chatAdvOpen" class="wk-advbody">
              <div class="wk-field wk-field-pad">
                <span class="wk-flabel">{{ $t('tools.aiPlayground.temperature') }}</span>
                <div class="wk-slider">
                  <el-slider v-model="chatTemperature" :min="0" :max="2" :step="0.1" />
                  <span class="wk-sliderval">{{ chatTemperature.toFixed(1) }}</span>
                </div>
              </div>
            </div>
          </el-collapse-transition>
        </div>
        <div class="wk-composer">
          <el-input
            v-model="chatInput"
            type="textarea"
            :rows="2"
            :placeholder="$t('tools.aiPlayground.chatPlaceholder')"
            @keydown.enter.exact.prevent="sendChat"
          />
          <div class="wk-compose-actions">
            <el-button size="small" @click="clearChat">{{ $t('tools.aiPlayground.clear') }}</el-button>
            <el-button type="primary" :loading="chatLoading" @click="sendChat">{{ $t('tools.aiPlayground.send') }}</el-button>
          </div>
        </div>
      </section>
      <section class="wk-stage card">
        <div class="wk-title">
          <span>{{ $t('tools.aiPlayground.panelOutput') }}</span>
          <span v-if="chatMeta" class="wk-meta">{{ chatMeta }}</span>
        </div>
        <div ref="chatListRef" class="wk-msgs">
          <div v-for="(m, i) in chatMessages" :key="i" class="wk-msg" :class="m.role">
            <div class="wk-bubble">{{ m.content }}</div>
          </div>
          <div v-if="!chatMessages.length" class="wk-empty">{{ $t('tools.aiPlayground.chatEmpty') }}</div>
        </div>
      </section>
    </div>

    <!-- ===== 图片:指令(左) | 舞台(右) ===== -->
    <div v-else-if="capability === 'image'" class="wk-grid">
      <section class="wk-deck card">
        <div class="wk-title">{{ $t('tools.aiPlayground.panelInput') }}</div>
        <div class="wk-block">
          <span class="wk-eyebrow">{{ $t('tools.aiPlayground.zoneContent') }}</span>
          <el-input v-model="imagePrompt" type="textarea" :rows="3" :placeholder="$t('tools.aiPlayground.imagePlaceholder')" />
        </div>
        <div class="wk-block">
          <span class="wk-eyebrow">{{ $t('tools.aiPlayground.zoneParams') }}</span>
          <input ref="refInputRef" type="file" accept="image/*" multiple hidden @change="onRefPick" />
          <div class="wk-refs">
            <div v-for="(img, i) in refImages" :key="i" class="wk-ref">
              <img :src="img.dataUrl" :alt="img.name" />
              <span class="wk-ref-del" @click="removeRefImage(i)">×</span>
            </div>
            <el-button v-if="refImages.length < 10" size="small" plain @click="refInputRef && refInputRef.click()">
              + {{ $t('tools.aiPlayground.addRef') }}
            </el-button>
          </div>
          <div class="wk-fields">
            <div class="wk-field">
              <span class="wk-flabel">{{ $t('tools.aiPlayground.imageSize') }}</span>
              <el-select v-model="imageSize" size="small" style="width: 100%">
                <el-option :label="$t('tools.aiPlayground.sizeDefault')" value="" />
                <el-option v-for="s in sizePresets" :key="s" :label="s" :value="s" />
              </el-select>
            </div>
            <div v-if="imageSequential !== 'auto'" class="wk-field">
              <span class="wk-flabel">{{ $t('tools.aiPlayground.imageCount') }}</span>
              <el-input-number v-model="imageCount" :min="1" :max="4" size="small" style="width: 100%" />
            </div>
          </div>
        </div>
        <div class="wk-block wk-adv">
          <button :class="['wk-advbtn', { on: imageAdvOpen }]" type="button" @click="imageAdvOpen = !imageAdvOpen">
            <span>{{ imageAdvOpen ? $t('tools.aiPlayground.advCollapse') : $t('tools.aiPlayground.advExpand') }}</span>
            <i class="wk-chev"></i>
          </button>
          <el-collapse-transition>
            <div v-if="imageAdvOpen" class="wk-advbody">
              <div class="wk-fields">
                <div class="wk-field">
                  <span class="wk-flabel">{{ $t('tools.aiPlayground.seed') }}</span>
                  <el-input-number v-model="imageSeed" :min="-1" :max="2147483647" :controls="false" size="small" style="width: 100%" />
                  <span class="wk-hint">{{ $t('tools.aiPlayground.seedRandom') }}</span>
                </div>
                <div class="wk-field">
                  <span class="wk-flabel">{{ $t('tools.aiPlayground.guidance') }}</span>
                  <el-checkbox v-model="guidanceCustom" size="small">{{ $t('tools.aiPlayground.guidanceCustom') }}</el-checkbox>
                  <div class="wk-slider">
                    <el-slider v-model="imageGuidance" :min="1" :max="10" :step="0.5" :disabled="!guidanceCustom" />
                    <span v-if="guidanceCustom" class="wk-sliderval">{{ imageGuidance }}</span>
                  </div>
                </div>
                <div class="wk-field">
                  <span class="wk-flabel">{{ $t('tools.aiPlayground.watermark') }}</span>
                  <el-switch v-model="imageWatermark" />
                </div>
                <div class="wk-field">
                  <span class="wk-flabel">{{ $t('tools.aiPlayground.responseFormat') }}</span>
                  <el-select v-model="imageRf" size="small" style="width: 100%">
                    <el-option label="url" value="url" />
                    <el-option label="b64_json" value="b64_json" />
                  </el-select>
                </div>
              </div>
              <div class="wk-hint wk-hint-block">{{ $t('tools.aiPlayground.sizeTierNote') }}</div>
              <div class="wk-hint wk-hint-block">{{ $t('tools.aiPlayground.paramsNote') }}</div>
            </div>
          </el-collapse-transition>
        </div>
        <el-button type="primary" class="wk-go" :loading="imageLoading" @click="genImage">{{ $t('tools.aiPlayground.generate') }}</el-button>
      </section>
      <section class="wk-stage card">
        <div class="wk-title">
          <span>{{ $t('tools.aiPlayground.panelOutput') }}</span>
          <span v-if="imageMeta" class="wk-meta">{{ imageMeta }}</span>
        </div>
        <div v-if="imageLoading" v-loading="imageLoading" class="wk-imgstage" />
        <div v-if="imageResults.length" class="wk-imggrid">
          <div v-for="(img, i) in imageResults" :key="i" class="wk-imgitem">
            <el-image :src="img.url" :preview-src-list="[img.url]" fit="cover" loading="lazy" hide-on-click-modal />
            <el-button size="small" text @click="copyUrl(img.url)">{{ $t('tools.aiPlayground.copyUrl') }}</el-button>
          </div>
        </div>
        <div v-else class="wk-empty wk-empty-center">{{ $t('tools.aiPlayground.imageEmpty') }}</div>
      </section>
    </div>

    <!-- ===== 语音:指令(左) | 舞台(右) ===== -->
    <div v-else class="wk-grid">
      <section class="wk-deck card">
        <div class="wk-title">{{ $t('tools.aiPlayground.panelInput') }}</div>
        <div class="wk-block">
          <span class="wk-eyebrow">{{ $t('tools.aiPlayground.zoneContent') }}</span>
          <div class="wk-pickrow">
            <input ref="audioInputRef" type="file" accept="audio/*" hidden @change="onAudioPick" />
            <el-button :disabled="!currentStatus.available" @click="audioInputRef && audioInputRef.click()">
              {{ $t('tools.aiPlayground.pickAudio') }}
            </el-button>
            <span v-if="audioFile" class="wk-file">{{ audioFile.name }} ({{ Math.round(audioFile.size / 1024) }}KB)</span>
          </div>
        </div>
        <div class="wk-block">
          <span class="wk-eyebrow">{{ $t('tools.aiPlayground.zoneParams') }}</span>
          <div class="wk-fields">
            <div class="wk-field wk-field-wide">
              <span class="wk-flabel">{{ $t('tools.aiPlayground.language') }}</span>
              <el-select v-model="audioLanguage" size="small" style="width: 100%" clearable :placeholder="$t('tools.aiPlayground.language')">
                <el-option label="中文 (zh)" value="zh" />
                <el-option label="English (en)" value="en" />
              </el-select>
            </div>
          </div>
        </div>
        <el-button type="primary" class="wk-go" :loading="asrLoading" :disabled="!audioFile || !currentStatus.available" @click="doTranscribe">
          {{ $t('tools.aiPlayground.transcribe') }}
        </el-button>
      </section>
      <section class="wk-stage card">
        <div class="wk-title">
          <span>{{ $t('tools.aiPlayground.panelOutput') }}</span>
          <span v-if="asrMeta" class="wk-meta">{{ asrMeta }}</span>
        </div>
        <div v-if="asrResult" class="wk-transcript">{{ asrResult }}</div>
        <div v-else class="wk-empty wk-empty-center">{{ $t('tools.aiPlayground.asrEmpty') }}</div>
      </section>
    </div>

    <!-- ===== 天气背景 AI 生成(关联设置) ===== -->
    <div class="card wk-weather">
      <button :class="['wk-weatherhead', { on: weatherOpen }]" type="button" @click="weatherOpen = !weatherOpen">
        <span class="wk-weathertitle">{{ $t('tools.aiPlayground.weatherBgTitle') }}</span>
        <i class="wk-chev"></i>
      </button>
      <p class="wk-weatherhint">{{ $t('tools.aiPlayground.weatherBgHint') }}</p>
      <el-collapse-transition>
        <div v-if="weatherOpen" class="wk-weatherbody">
          <div class="wk-fields">
            <div class="wk-field wk-field-nowrap">
              <span class="wk-flabel">{{ $t('tools.aiPlayground.weatherBgEnabled') }}</span>
              <el-switch v-model="weatherCfg.enabled" />
            </div>
            <div class="wk-field">
              <span class="wk-flabel">{{ $t('tools.aiPlayground.weatherBgStyle') }}</span>
              <el-select v-model="weatherCfg.style" size="small" style="width: 100%">
                <el-option v-for="s in weatherStyles" :key="s" :label="s" :value="s" />
              </el-select>
            </div>
            <div class="wk-field">
              <span class="wk-flabel">{{ $t('tools.aiPlayground.weatherBgSize') }}</span>
              <el-select v-model="weatherCfg.size" size="small" style="width: 100%">
                <el-option v-for="s in weatherSizes" :key="s" :label="s" :value="s" />
              </el-select>
            </div>
            <div class="wk-field">
              <span class="wk-flabel">{{ $t('tools.aiPlayground.weatherBgRefresh') }}</span>
              <el-input-number v-model="weatherCfg.refreshDays" :min="1" :max="30" size="small" style="width: 100%" />
            </div>
            <div class="wk-field wk-field-nowrap">
              <span class="wk-flabel">{{ $t('tools.aiPlayground.weatherBgWatermark') }}</span>
              <el-switch v-model="weatherCfg.watermark" />
            </div>
          </div>
          <div class="wk-block">
            <span class="wk-flabel">{{ $t('tools.aiPlayground.weatherBgScene') }}</span>
            <el-input v-model="weatherCfg.scene" type="textarea" :rows="2" :placeholder="$t('tools.aiPlayground.weatherBgScenePlaceholder')" />
          </div>
          <div class="wk-weathersave">
            <el-button type="primary" size="small" @click="saveWeatherCfg">{{ $t('tools.aiPlayground.weatherBgSave') }}</el-button>
          </div>
        </div>
      </el-collapse-transition>
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
const sizePresets = ['2048x2048', '1920x1920', '1440x2560', '2560x1440', '2K', '4K', 'adaptive']
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
      n: groupMode ? null : imageCount.value,
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

// ---- 布局:专业参数折叠进「更多设置」 ----
const chatAdvOpen = ref(false)
const imageAdvOpen = ref(false)

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
.wk-alert { margin-bottom: 16px; }

/* ===== 顶部栏:能力切换 + 模型状态 ===== */
.wk-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.wk-status { display: flex; align-items: center; gap: 8px; }

/* ===== 工作台两栏:指令(左) | 舞台(右),等高 ===== */
.wk-grid {
  display: grid;
  grid-template-columns: minmax(0, 5fr) minmax(0, 6fr);
  gap: 16px;
  align-items: stretch;
}
@media (max-width: 900px) {
  .wk-grid { grid-template-columns: 1fr; }
}
/* 左:指令面板 */
.wk-deck { display: flex; flex-direction: column; gap: 18px; }
/* 右:舞台 */
.wk-stage { display: flex; flex-direction: column; gap: 14px; }

/* ===== 面板标题:细分割线 + 右侧 meta ===== */
.wk-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--color-text, #3a2e22);
  padding-bottom: 10px;
  border-bottom: 1px solid var(--color-border, rgba(58, 46, 34, 0.12));
}
.wk-meta { font-size: 12px; font-weight: 400; color: var(--color-text-secondary, #7a6b5a); }

/* ===== 内容块:eyebrow + 字段 ===== */
.wk-block { display: flex; flex-direction: column; gap: 10px; }
.wk-eyebrow {
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  color: var(--color-text-secondary, #7a6b5a);
}
.wk-flabel { font-size: 12px; color: var(--color-text-secondary, #7a6b5a); }
.wk-hint { font-size: 12px; color: var(--color-text-secondary, #7a6b5a); line-height: 1.5; }
.wk-hint-block { margin-top: 6px; }

/* ===== 字段网格:整齐两列,窄屏单列 ===== */
.wk-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 14px;
}
@media (max-width: 640px) {
  .wk-fields { grid-template-columns: 1fr; }
}
.wk-field { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.wk-field-wide { grid-column: 1 / -1; }
.wk-field-nowrap { flex-direction: row; align-items: center; gap: 10px; }
.wk-field-pad { padding: 2px 0; }

/* ===== 滑杆 ===== */
.wk-slider { flex: 1; display: flex; align-items: center; gap: 12px; min-width: 0; }
.wk-slider :deep(.el-slider) { flex: 1; }
.wk-sliderval { font-size: 13px; min-width: 28px; text-align: right; color: var(--color-text-secondary, #7a6b5a); }

/* ===== 更多设置:安静的文字开合,而非大框 ===== */
.wk-adv { border-top: 1px dashed var(--color-border, rgba(58, 46, 34, 0.12)); padding-top: 10px; }
.wk-advbtn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 2px;
  background: transparent;
  border: none;
  cursor: pointer;
  font-size: 12px;
  color: var(--color-text-secondary, #7a6b5a);
}
.wk-advbtn:hover { color: var(--color-accent, #a8483a); }
.wk-chev {
  width: 0; height: 0;
  border-left: 4px solid transparent;
  border-right: 4px solid transparent;
  border-top: 5px solid currentColor;
  transform: rotate(0deg);
  transition: transform 0.2s ease;
}
.wk-advbtn.on .wk-chev { transform: rotate(180deg); }
.wk-advbody {
  margin-top: 10px;
  padding: 12px;
  background: var(--color-bg-2, #e2d5bc);
  border-radius: 10px;
}

/* ===== 对话输入:composer(吸底) ===== */
.wk-composer { display: flex; flex-direction: column; gap: 10px; margin-top: auto; }
.wk-compose-actions { display: flex; gap: 10px; justify-content: flex-end; }

/* ===== 运行按钮:主行动,吸底 ===== */
.wk-go { width: 100%; margin-top: auto; }

/* ===== 对话消息 ===== */
.wk-msgs {
  flex: 1;
  min-height: 140px;
  max-height: 460px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--color-border, rgba(58, 46, 34, 0.12));
  border-radius: 12px;
  background: var(--color-bg-2, #e2d5bc);
}
.wk-msg { display: flex; }
.wk-msg.user { justify-content: flex-end; }
.wk-bubble {
  max-width: 78%;
  padding: 9px 13px;
  border-radius: 14px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.wk-msg.user .wk-bubble { background: var(--color-accent, #a8483a); color: #fff; border-bottom-right-radius: 4px; }
.wk-msg.assistant .wk-bubble { background: var(--color-card, #faf6ec); color: var(--color-text, #3a2e22); border-bottom-left-radius: 4px; }

/* ===== 空态 / 加载 ===== */
.wk-empty { margin: auto; font-size: 13px; color: var(--color-text-secondary, #7a6b5a); text-align: center; padding: 24px 12px; }
.wk-empty-center { text-align: center; }
.wk-imgstage { flex: 1; min-height: 200px; border-radius: 10px; }

/* ===== 参考图条 ===== */
.wk-refs { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.wk-ref {
  position: relative;
  width: 64px;
  height: 64px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--color-border, rgba(58, 46, 34, 0.12));
}
.wk-ref img { width: 100%; height: 100%; object-fit: cover; display: block; }
.wk-ref-del {
  position: absolute;
  top: 0;
  right: 0;
  width: 16px;
  height: 16px;
  line-height: 15px;
  text-align: center;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 12px;
  cursor: pointer;
  border-radius: 0 0 0 8px;
}

/* ===== 图片结果 ===== */
.wk-imggrid { margin-top: 4px; display: grid; grid-template-columns: repeat(auto-fill, minmax(170px, 1fr)); gap: 12px; }
.wk-imgitem { display: flex; flex-direction: column; gap: 6px; }
.wk-imgitem :deep(.el-image) {
  width: 100%;
  height: 180px;
  border-radius: 10px;
  background: var(--color-bg-2, #e2d5bc);
  overflow: hidden;
  display: block;
}

/* ===== 语音 ===== */
.wk-pickrow { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.wk-file { font-size: 13px; color: var(--color-text-secondary, #7a6b5a); }
.wk-transcript {
  flex: 1;
  padding: 14px;
  border: 1px solid var(--color-border, rgba(58, 46, 34, 0.12));
  border-radius: 10px;
  background: var(--color-card-2, #e8dec8);
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

/* ===== 天气背景(关联设置,独立卡片) ===== */
.wk-weather { margin-top: 16px; display: flex; flex-direction: column; gap: 12px; padding: 20px; }
.wk-weatherhead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  width: 100%;
  padding: 0;
  background: transparent;
  border: none;
  cursor: pointer;
  color: inherit;
}
.wk-weathertitle { font-size: 14px; font-weight: 600; color: var(--color-text, #3a2e22); }
.wk-weatherhint { margin: 0; font-size: 12px; color: var(--color-text-secondary, #7a6b5a); line-height: 1.6; }
.wk-weatherbody {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--color-border, rgba(58, 46, 34, 0.12));
}
.wk-weathersave { display: flex; justify-content: flex-end; }

@media (prefers-reduced-motion: reduce) {
  .wk-chev, .wk-advbtn { transition: none; }
}
</style>
