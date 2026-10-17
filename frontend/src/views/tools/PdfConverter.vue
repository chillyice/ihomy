<!-- PDF 转换:PDF↔图片、PDF 提取文字。全部在本机浏览器完成,不上传服务器。 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('tools.title') }, { label: $t('tools.pdf.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <el-radio-group v-model="tab" size="small">
          <el-radio-button value="pdf2img">{{ $t('tools.pdf.tabPdf2img') }}</el-radio-button>
          <el-radio-button value="img2pdf">{{ $t('tools.pdf.tabImg2pdf') }}</el-radio-button>
          <el-radio-button value="pdf2txt">{{ $t('tools.pdf.tabPdf2txt') }}</el-radio-button>
        </el-radio-group>
      </div>
      <div class="tb-right">
        <template v-if="tab === 'pdf2img'">
          <el-button v-if="results.length" size="small" :disabled="busy" @click="downloadZip">
            <el-icon><Download /></el-icon>
            {{ $t('tools.pdf.downloadZip') }}
          </el-button>
        </template>
        <template v-else-if="tab === 'pdf2txt'">
          <el-button v-if="textPages.length" size="small" @click="copyText">{{ $t('tools.pdf.copyAll') }}</el-button>
          <el-button v-if="textPages.length" size="small" @click="downloadText">
            <el-icon><Download /></el-icon>
            {{ $t('tools.pdf.downloadTxt') }}
          </el-button>
        </template>
      </div>
    </PageToolbar>

    <!-- PDF → 图片 / PDF → 文本:共用一份已载入的 PDF -->
    <div v-if="tab !== 'img2pdf'" class="pdf-grid">
      <div class="card pdf-card">
        <div class="section-label">{{ $t('tools.pdf.sourcePdf') }}</div>

        <div
          v-if="!pdfFile"
          v-a11y-click
          class="pdf-drop"
          :class="{ dragging: dragPdf }"
          @click="pickPdf"
          @dragover.prevent="dragPdf = true"
          @dragleave="dragPdf = false"
          @drop.prevent="onPdfDrop"
        >
          <el-icon :size="36"><Document /></el-icon>
          <div class="pdf-drop-title">{{ $t('tools.pdf.dropPdf') }}</div>
          <div class="pdf-drop-sub">{{ $t('tools.pdf.dropPdfSub') }}</div>
        </div>

        <div v-else class="pdf-file">
          <img v-if="pdfThumbUrl" :src="pdfThumbUrl" alt="" class="pdf-thumb" />
          <div class="pdf-file-main">
            <div class="pdf-file-name">{{ pdfFile.name }}</div>
            <div class="pdf-file-meta">
              {{ $t('tools.pdf.fileMeta', { pages: pdfPageCount, size: formatBytes(pdfFile.size) }) }}
            </div>
          </div>
          <el-button size="small" text :disabled="busy" @click="pickPdf">{{ $t('tools.pdf.reselect') }}</el-button>
        </div>

        <div v-if="pdfLoading" class="pdf-hint">{{ $t('tools.pdf.loadingPdf') }}</div>
        <div class="pdf-hint pdf-privacy">{{ $t('tools.pdf.privacy') }}</div>
        <input ref="pdfInput" type="file" accept=".pdf,application/pdf" class="hidden-input" @change="onPdfPick" />
      </div>

      <div class="card pdf-card">
        <div class="section-label">
          {{ tab === 'pdf2img' ? $t('tools.pdf.toImages') : $t('tools.pdf.toText') }}
        </div>

        <template v-if="tab === 'pdf2img'">
          <div class="pdf-field">
            <label>{{ $t('tools.pdf.format') }}</label>
            <el-radio-group v-model="imgFormat" size="small" :disabled="busy">
              <el-radio-button value="png">PNG</el-radio-button>
              <el-radio-button value="jpeg">JPEG</el-radio-button>
            </el-radio-group>
          </div>
          <div class="pdf-field">
            <label>{{ $t('tools.pdf.quality') }}</label>
            <el-select v-model="imgQuality" size="small" :disabled="busy">
              <el-option value="standard" :label="$t('tools.pdf.qualityStandard')" />
              <el-option value="high" :label="$t('tools.pdf.qualityHigh')" />
              <el-option value="ultra" :label="$t('tools.pdf.qualityUltra')" />
            </el-select>
          </div>
        </template>

        <div class="pdf-field">
          <label>{{ $t('tools.pdf.pageRange') }}</label>
          <el-input
            v-model="pageRange"
            size="small"
            clearable
            :disabled="busy || !pdfFile"
            :placeholder="$t('tools.pdf.pageRangePlaceholder')"
          />
          <span v-if="pdfFile" class="pdf-hint">{{ $t('tools.pdf.pageRangeHint', { total: pdfPageCount }) }}</span>
        </div>

        <el-button
          v-if="tab === 'pdf2img'"
          type="primary"
          :loading="busy && phase === 'render'"
          :disabled="busy || !pdfFile"
          @click="runPdf2img"
        >
          {{ $t('tools.pdf.convert') }}
        </el-button>
        <el-button v-else type="primary" :loading="busy && phase === 'text'" :disabled="busy || !pdfFile" @click="runExtract">
          {{ $t('tools.pdf.extract') }}
        </el-button>
      </div>
    </div>

    <!-- 图片 → PDF -->
    <div v-else class="pdf-grid">
      <div class="card pdf-card">
        <div class="section-label">{{ $t('tools.pdf.sourceImages') }}</div>

        <div
          v-a11y-click
          class="pdf-drop"
          :class="{ dragging: dragImages }"
          @click="pickImages"
          @dragover.prevent="dragImages = true"
          @dragleave="dragImages = false"
          @drop.prevent="onImagesDrop"
        >
          <el-icon :size="36"><Picture /></el-icon>
          <div class="pdf-drop-title">{{ $t('tools.pdf.dropImages') }}</div>
          <div class="pdf-drop-sub">{{ $t('tools.pdf.dropImagesSub') }}</div>
        </div>

        <div v-if="images.length" class="pdf-img-grid">
          <div v-for="(img, idx) in images" :key="img.id" class="pdf-img-item">
            <img :src="img.url" alt="" />
            <span class="pdf-img-index">{{ idx + 1 }}</span>
            <div class="pdf-img-actions">
              <el-button
                link
                size="small"
                :title="$t('tools.pdf.moveUp')"
                :aria-label="$t('tools.pdf.moveUp')"
                :disabled="busy || idx === 0"
                @click="moveImage(idx, -1)"
              >
                <el-icon><ArrowUp /></el-icon>
              </el-button>
              <el-button
                link
                size="small"
                :title="$t('tools.pdf.moveDown')"
                :aria-label="$t('tools.pdf.moveDown')"
                :disabled="busy || idx === images.length - 1"
                @click="moveImage(idx, 1)"
              >
                <el-icon><ArrowDown /></el-icon>
              </el-button>
              <el-button
                link
                size="small"
                type="danger"
                :title="$t('tools.pdf.remove')"
                :aria-label="$t('tools.pdf.remove')"
                :disabled="busy"
                @click="removeImage(idx)"
              >
                <el-icon><Close /></el-icon>
              </el-button>
            </div>
          </div>
        </div>
        <div v-if="images.length" class="pdf-hint">
          {{ $t('tools.pdf.imageCount', { n: images.length }) }} · {{ $t('tools.pdf.sortHint') }}
        </div>

        <div class="pdf-hint pdf-privacy">{{ $t('tools.pdf.privacy') }}</div>
        <input ref="imageInput" type="file" accept="image/*" multiple class="hidden-input" @change="onImagesPick" />
      </div>

      <div class="card pdf-card">
        <div class="section-label">{{ $t('tools.pdf.toPdf') }}</div>

        <div class="pdf-field">
          <label>{{ $t('tools.pdf.pageSize') }}</label>
          <el-select v-model="pageSize" size="small" :disabled="busy">
            <el-option value="A4" :label="$t('tools.pdf.sizeA4')" />
            <el-option value="A4_LANDSCAPE" :label="$t('tools.pdf.sizeA4Landscape')" />
            <el-option value="IMAGE" :label="$t('tools.pdf.sizeFitImage')" />
          </el-select>
        </div>
        <div v-if="pageSize !== 'IMAGE'" class="pdf-field">
          <label>{{ $t('tools.pdf.margin') }}</label>
          <el-select v-model="margin" size="small" :disabled="busy">
            <el-option :value="0" :label="$t('tools.pdf.marginNone')" />
            <el-option :value="12" :label="$t('tools.pdf.marginNarrow')" />
            <el-option :value="24" :label="$t('tools.pdf.marginWide')" />
          </el-select>
        </div>

        <el-button type="primary" :loading="busy && phase === 'pdf'" :disabled="busy || !images.length" @click="generatePdf">
          {{ $t('tools.pdf.generate') }}
        </el-button>
        <el-button v-if="generatedUrl" :disabled="busy" @click="downloadGenerated">
          <el-icon><Download /></el-icon>
          {{ $t('tools.pdf.downloadPdf') }}
        </el-button>
      </div>
    </div>

    <!-- 进度与取消 -->
    <div v-if="busy" class="card pdf-progress">
      <div class="pdf-progress-head">
        <span>{{ phaseText }}</span>
        <el-button size="small" text type="danger" @click="cancel">{{ $t('tools.pdf.cancel') }}</el-button>
      </div>
      <el-progress :percentage="percent" :status="cancelFlag ? 'warning' : undefined" />
      <div class="pdf-hint">{{ $t('tools.pdf.progressPages', { done: progress.current, total: progress.total }) }}</div>
    </div>

    <!-- PDF → 图片结果 -->
    <div v-if="tab === 'pdf2img' && results.length" class="card pdf-results">
      <div class="pdf-results-head">
        <span class="section-label">{{ $t('tools.pdf.resultTitle', { n: results.length }) }}</span>
      </div>
      <div class="pdf-result-grid">
        <div v-for="item in results" :key="item.page" class="pdf-result-item">
          <img :src="item.url" alt="" loading="lazy" />
          <div class="pdf-result-meta">
            <span>{{ $t('tools.pdf.pageLabel', { n: item.page }) }}</span>
            <el-button link size="small" @click="downloadOne(item)">{{ $t('tools.pdf.download') }}</el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- PDF → 文本结果 -->
    <div v-if="tab === 'pdf2txt' && textPages.length" class="card pdf-results">
      <div class="pdf-results-head">
        <span class="section-label">{{ $t('tools.pdf.textTitle', { n: textPages.length }) }}</span>
      </div>
      <pre class="pdf-text">{{ displayText }}</pre>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, shallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ArrowDown, ArrowUp, Close, Document, Download, Picture } from '@element-plus/icons-vue'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import {
  buildImagePdf,
  createZip,
  downloadBlob,
  extractPdfText,
  formatBytes,
  loadPdfDocument,
  normalizeImage,
  parsePageRange,
  renderPdfPageToBlob,
} from '@/utils/pdfTools'

const { t } = useI18n()

const MAX_IMAGE_PAGES = 200
const MAX_IMAGES = 100
const QUALITY_SCALE = { standard: 1.5, high: 2, ultra: 3 }

const tab = ref('pdf2img')
const pdfInput = ref(null)
const imageInput = ref(null)

const pdfFile = ref(null)
// pdfjs 文档对象用 shallowRef:深层响应式代理会拦截其私有字段,getPage 会抛「Cannot read from private field」
const pdfDoc = shallowRef(null)
const pdfPageCount = ref(0)
const pdfThumbUrl = ref('')
const pdfLoading = ref(false)
const dragPdf = ref(false)

const imgFormat = ref('png')
const imgQuality = ref('high')
const pageRange = ref('')
const results = ref([])
const textPages = ref([])

const images = ref([])
const dragImages = ref(false)
const pageSize = ref('A4')
const margin = ref(24)
const generatedUrl = ref('')
const generatedName = ref('')

const busy = ref(false)
const cancelFlag = ref(false)
const phase = ref('')
const progress = ref({ current: 0, total: 0 })

const percent = computed(() => (progress.value.total ? Math.round((progress.value.current / progress.value.total) * 100) : 0))
const phaseText = computed(() => {
  if (phase.value === 'render') return t('tools.pdf.rendering')
  if (phase.value === 'text') return t('tools.pdf.extracting')
  if (phase.value === 'pdf') return t('tools.pdf.generating')
  return ''
})
const displayText = computed(() => textPages.value.map((p) => `${t('tools.pdf.pageDivider', { n: p.page })}\n${p.text}`).join('\n\n'))
const pdfBaseName = computed(() => (pdfFile.value?.name || 'document').replace(/\.pdf$/i, ''))

const revoke = (url) => {
  if (url) URL.revokeObjectURL(url)
}

const destroyPdf = () => {
  pdfDoc.value?.destroy?.()
  pdfDoc.value = null
}

const clearResults = () => {
  results.value.forEach((r) => revoke(r.url))
  results.value = []
}

const clearText = () => {
  textPages.value = []
}

const resetPdf = () => {
  destroyPdf()
  revoke(pdfThumbUrl.value)
  pdfThumbUrl.value = ''
  pdfFile.value = null
  pdfPageCount.value = 0
  pageRange.value = ''
  clearResults()
  clearText()
}

const pickPdf = () => {
  if (!busy.value) pdfInput.value?.click()
}

const loadPdf = async (file) => {
  if (!file) return
  if (file.type !== 'application/pdf' && !/\.pdf$/i.test(file.name)) {
    ElMessage.warning(t('tools.pdf.notPdf'))
    return
  }
  pdfLoading.value = true
  resetPdf()
  try {
    const doc = await loadPdfDocument(await file.arrayBuffer())
    pdfDoc.value = doc
    pdfFile.value = file
    pdfPageCount.value = doc.numPages
    const page = await doc.getPage(1)
    const thumb = await renderPdfPageToBlob(page, { scale: 0.5, format: 'jpeg', quality: 0.7 })
    page.cleanup?.()
    pdfThumbUrl.value = URL.createObjectURL(thumb)
  } catch (e) {
    console.error(e)
    if (e?.name === 'PasswordException') ElMessage.error(t('tools.pdf.encrypted'))
    else ElMessage.error(t('tools.pdf.loadFailed'))
    resetPdf()
  } finally {
    pdfLoading.value = false
  }
}

const onPdfPick = (e) => {
  const file = e.target.files?.[0]
  e.target.value = ''
  loadPdf(file)
}

const onPdfDrop = (e) => {
  dragPdf.value = false
  loadPdf(e.dataTransfer?.files?.[0])
}

const resolvePages = () => {
  const pages = parsePageRange(pageRange.value, pdfPageCount.value)
  if (!pages) {
    ElMessage.warning(t('tools.pdf.invalidRange', { total: pdfPageCount.value }))
    return null
  }
  if (pages.length > MAX_IMAGE_PAGES) {
    ElMessage.warning(t('tools.pdf.tooManyPages', { max: MAX_IMAGE_PAGES }))
    return null
  }
  return pages
}

const runPdf2img = async () => {
  if (!pdfDoc.value || busy.value) return
  const pages = resolvePages()
  if (!pages) return

  clearResults()
  busy.value = true
  cancelFlag.value = false
  phase.value = 'render'
  progress.value = { current: 0, total: pages.length }
  const scale = QUALITY_SCALE[imgQuality.value] || 2
  const format = imgFormat.value
  const ext = format === 'png' ? 'png' : 'jpg'
  try {
    for (let i = 0; i < pages.length; i++) {
      if (cancelFlag.value) break
      const page = await pdfDoc.value.getPage(pages[i])
      const blob = await renderPdfPageToBlob(page, { scale, format })
      page.cleanup?.()
      results.value.push({ page: pages[i], blob, url: URL.createObjectURL(blob), ext })
      progress.value.current = i + 1
    }
    if (cancelFlag.value) ElMessage.info(t('tools.pdf.cancelled'))
    else ElMessage.success(t('tools.pdf.convertDone', { n: results.value.length }))
  } catch (e) {
    console.error(e)
    ElMessage.error(t('tools.pdf.convertFailed'))
  } finally {
    busy.value = false
    phase.value = ''
  }
}

const downloadOne = (item) => {
  const n = String(item.page).padStart(3, '0')
  downloadBlob(item.blob, `${pdfBaseName.value}-p${n}.${item.ext}`)
}

const downloadZip = async () => {
  if (!results.value.length || busy.value) return
  busy.value = true
  try {
    const width = String(Math.max(...results.value.map((r) => r.page))).length
    const zip = await createZip(
      results.value.map((r) => ({
        name: `page-${String(r.page).padStart(width, '0')}.${r.ext}`,
        data: r.blob,
      })),
    )
    downloadBlob(zip, `${pdfBaseName.value}-images.zip`)
  } catch (e) {
    console.error(e)
    ElMessage.error(t('tools.pdf.zipFailed'))
  } finally {
    busy.value = false
  }
}

const runExtract = async () => {
  if (!pdfDoc.value || busy.value) return
  const pages = resolvePages()
  if (!pages) return

  clearText()
  busy.value = true
  cancelFlag.value = false
  phase.value = 'text'
  progress.value = { current: 0, total: pages.length }
  try {
    const result = await extractPdfText(pdfDoc.value, pages, {
      onProgress: (done, total) => (progress.value = { current: done, total }),
      isCancelled: () => cancelFlag.value,
    })
    textPages.value = result
    if (cancelFlag.value) ElMessage.info(t('tools.pdf.cancelled'))
    else if (!displayText.value.trim()) ElMessage.warning(t('tools.pdf.noText'))
    else ElMessage.success(t('tools.pdf.extractDone', { n: result.length }))
  } catch (e) {
    console.error(e)
    ElMessage.error(t('tools.pdf.extractFailed'))
  } finally {
    busy.value = false
    phase.value = ''
  }
}

const copyText = async () => {
  try {
    await navigator.clipboard.writeText(displayText.value)
    ElMessage.success(t('tools.pdf.copied'))
  } catch {
    ElMessage.error(t('tools.pdf.copyFailed'))
  }
}

const downloadText = () => {
  const blob = new Blob(['\ufeff', displayText.value], { type: 'text/plain;charset=utf-8' })
  downloadBlob(blob, `${pdfBaseName.value}.txt`)
}

const pickImages = () => {
  if (!busy.value) imageInput.value?.click()
}

const addImageFiles = (fileList) => {
  const files = Array.from(fileList || []).filter((f) => f.type.startsWith('image/') || /\.(png|jpe?g|webp|gif|bmp)$/i.test(f.name))
  if (!files.length) {
    ElMessage.warning(t('tools.pdf.invalidImage'))
    return
  }
  if (images.value.length + files.length > MAX_IMAGES) {
    ElMessage.warning(t('tools.pdf.tooManyImages', { max: MAX_IMAGES }))
    return
  }
  for (const file of files) {
    images.value.push({
      id: `${Date.now()}-${Math.random().toString(36).slice(2)}`,
      file,
      url: URL.createObjectURL(file),
    })
  }
}

const onImagesPick = (e) => {
  addImageFiles(e.target.files)
  e.target.value = ''
}

const onImagesDrop = (e) => {
  dragImages.value = false
  addImageFiles(e.dataTransfer?.files)
}

const moveImage = (index, delta) => {
  const target = index + delta
  if (target < 0 || target >= images.value.length) return
  const list = images.value
  ;[list[index], list[target]] = [list[target], list[index]]
}

const removeImage = (index) => {
  const [removed] = images.value.splice(index, 1)
  revoke(removed?.url)
}

const clearImages = () => {
  images.value.forEach((img) => revoke(img.url))
  images.value = []
}

const generatePdf = async () => {
  if (!images.value.length || busy.value) return
  busy.value = true
  cancelFlag.value = false
  phase.value = 'pdf'
  progress.value = { current: 0, total: images.value.length }
  try {
    const source = []
    for (let i = 0; i < images.value.length; i++) {
      if (cancelFlag.value) break
      const normalized = await normalizeImage(images.value[i].file)
      source.push({
        data: new Uint8Array(await normalized.blob.arrayBuffer()),
        width: normalized.width,
        height: normalized.height,
      })
      progress.value.current = i + 1
    }
    if (!source.length) {
      ElMessage.info(t('tools.pdf.cancelled'))
      return
    }
    const blob = buildImagePdf(source, { pageSize: pageSize.value, margin: margin.value })
    revoke(generatedUrl.value)
    generatedUrl.value = URL.createObjectURL(blob)
    generatedName.value = `${images.value[0].file.name.replace(/\.[^.]+$/, '')}-${source.length}.pdf`
    ElMessage.success(t('tools.pdf.generateDone', { n: source.length }))
  } catch (e) {
    console.error(e)
    ElMessage.error(t('tools.pdf.generateFailed'))
  } finally {
    busy.value = false
    phase.value = ''
  }
}

const downloadGenerated = () => {
  if (!generatedUrl.value) return
  const a = document.createElement('a')
  a.href = generatedUrl.value
  a.download = generatedName.value || 'images.pdf'
  document.body.appendChild(a)
  a.click()
  a.remove()
}

const cancel = () => {
  cancelFlag.value = true
}

onBeforeUnmount(() => {
  destroyPdf()
  revoke(pdfThumbUrl.value)
  clearResults()
  clearImages()
  revoke(generatedUrl.value)
})
</script>

<style scoped>
.pdf-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}
.pdf-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.pdf-drop {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 180px;
  padding: 24px;
  border: 2px dashed var(--color-border, rgba(0, 0, 0, 0.14));
  border-radius: 14px;
  cursor: pointer;
  text-align: center;
  transition:
    border-color 0.2s ease,
    background 0.2s ease;
}
.pdf-drop:hover,
.pdf-drop.dragging {
  border-color: var(--color-accent, var(--color-brand));
  background: rgba(var(--color-brand-rgb, 184, 140, 110), 0.06);
}
.pdf-drop-title {
  font-size: 14px;
  font-weight: 600;
}
.pdf-drop-sub {
  font-size: 12px;
  color: var(--color-text-secondary, #8a8a8a);
  line-height: 1.5;
}
.pdf-file {
  display: flex;
  align-items: center;
  gap: 12px;
}
.pdf-thumb {
  width: 72px;
  height: auto;
  max-height: 96px;
  object-fit: contain;
  border-radius: 8px;
  background: var(--color-card-2, rgba(0, 0, 0, 0.04));
}
.pdf-file-main {
  flex: 1;
  min-width: 0;
}
.pdf-file-name {
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pdf-file-meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-text-secondary, #8a8a8a);
}
.pdf-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.pdf-field label {
  font-size: 13px;
  font-weight: 500;
}
.pdf-hint {
  font-size: 12px;
  color: var(--color-text-secondary, #8a8a8a);
  line-height: 1.5;
}
.pdf-privacy {
  margin-top: auto;
  padding-top: 6px;
  opacity: 0.8;
}
.pdf-img-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(96px, 1fr));
  gap: 10px;
}
.pdf-img-item {
  position: relative;
  border-radius: 10px;
  overflow: hidden;
  background: var(--color-card-2, rgba(0, 0, 0, 0.04));
}
.pdf-img-item img {
  display: block;
  width: 100%;
  height: 96px;
  object-fit: cover;
}
.pdf-img-index {
  position: absolute;
  top: 6px;
  left: 6px;
  min-width: 20px;
  height: 20px;
  padding: 0 5px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 11px;
  line-height: 20px;
  text-align: center;
}
.pdf-img-actions {
  position: absolute;
  right: 4px;
  bottom: 4px;
  display: flex;
  gap: 2px;
  padding: 2px 4px;
  border-radius: 8px;
  background: var(--color-card, rgba(255, 255, 255, 0.9));
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.18);
}
.pdf-progress {
  margin-top: 16px;
}
.pdf-progress-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
}
.pdf-results {
  margin-top: 16px;
}
.pdf-results-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.pdf-result-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
}
.pdf-result-item {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--color-border, rgba(0, 0, 0, 0.08));
  border-radius: 10px;
  overflow: hidden;
}
.pdf-result-item img {
  display: block;
  width: 100%;
  height: 180px;
  object-fit: contain;
  background: var(--color-card-2, rgba(0, 0, 0, 0.03));
}
.pdf-result-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 8px;
  font-size: 12px;
}
.pdf-text {
  margin: 0;
  max-height: 480px;
  overflow: auto;
  padding: 12px;
  border-radius: 10px;
  background: var(--color-card-2, rgba(0, 0, 0, 0.03));
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
.hidden-input {
  display: none;
}
@media (max-width: 760px) {
  .pdf-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
