<!-- PDF 面板:PDF→图片、图片→PDF、PDF 提取文字。全部在本机浏览器完成(pdfjs 按需加载),
     不上传服务器;内核与 PDF 转换工具共用 utils/pdfTools。 -->
<template>
  <div class="bpc-stack">
    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.pdf.title') }}</span>
        <span>{{ $t('tools.pdf.privacy') }}</span>
      </div>
      <div class="bpc-card-bd">
        <BpcChips v-model="tab" :options="tabOptions" />
        <p v-if="notice" class="bpc-meta">
          <span :class="notice.kind === 'error' ? 'bpc-err' : 'bpc-flash'">{{ notice.text }}</span>
        </p>
      </div>
    </section>

    <template v-if="tab !== 'img2pdf'">
      <div class="bpc-grid">
        <section class="bpc-card">
          <div class="bpc-card-hd">
            <span>{{ $t('tools.pdf.sourcePdf') }}</span>
            <span>{{ pdfLoading ? $t('tools.pdf.loadingPdf') : pdfFile ? `${pdfPageCount} p` : '—' }}</span>
          </div>
          <div class="bpc-card-bd">
            <label v-if="!pdfFile" class="bp-drop">
              <input ref="pdfInput" class="bp-file" type="file" accept=".pdf,application/pdf" @change="onPdfPick" />
              <span class="bp-drop-title">{{ $t('tools.pdf.dropPdf') }}</span>
              <span class="bp-drop-sub">{{ $t('tools.pdf.dropPdfSub') }}</span>
            </label>

            <div v-else class="bp-pdf-file">
              <img v-if="pdfThumbUrl" :src="pdfThumbUrl" class="bp-pdf-thumb" alt="" />
              <div class="bp-pdf-main">
                <span class="bp-drop-title">{{ pdfFile.name }}</span>
                <span class="bp-drop-sub">{{ $t('tools.pdf.fileMeta', { pages: pdfPageCount, size: formatBytes(pdfFile.size) }) }}</span>
              </div>
              <button class="bpc-btn bpc-btn-sm" type="button" :disabled="busy" @click="pickPdf">{{ $t('tools.pdf.reselect') }}</button>
            </div>
            <input ref="pdfInputAlt" class="bp-file" type="file" accept=".pdf,application/pdf" @change="onPdfPick" />
            <p class="bpc-hint">{{ $t('tools.pdf.privacy') }}</p>
          </div>
        </section>

        <section class="bpc-card">
          <div class="bpc-card-hd">
            <span>{{ tab === 'pdf2img' ? $t('tools.pdf.toImages') : $t('tools.pdf.toText') }}</span>
            <span>{{ busy ? phaseText : 'idle' }}</span>
          </div>
          <div class="bpc-card-bd">
            <template v-if="tab === 'pdf2img'">
              <div class="bpc-field">
                <span class="bpc-label">{{ $t('tools.pdf.format') }}</span>
                <BpcChips v-model="imgFormat" :options="formatOptions" />
              </div>
              <div class="bpc-field">
                <label class="bpc-label" for="bp-pdf-quality">{{ $t('tools.pdf.quality') }}</label>
                <select id="bp-pdf-quality" v-model="imgQuality" class="bpc-select" :disabled="busy">
                  <option value="standard">{{ $t('tools.pdf.qualityStandard') }}</option>
                  <option value="high">{{ $t('tools.pdf.qualityHigh') }}</option>
                  <option value="ultra">{{ $t('tools.pdf.qualityUltra') }}</option>
                </select>
              </div>
            </template>

            <div class="bpc-field">
              <label class="bpc-label" for="bp-pdf-range">{{ $t('tools.pdf.pageRange') }}</label>
              <input
                id="bp-pdf-range"
                v-model="pageRange"
                class="bpc-input"
                type="text"
                :disabled="busy || !pdfFile"
                :placeholder="$t('tools.pdf.pageRangePlaceholder')"
              />
              <span v-if="pdfFile" class="bpc-hint">{{ $t('tools.pdf.pageRangeHint', { total: pdfPageCount }) }}</span>
            </div>

            <div class="bpc-actions">
              <button
                v-if="tab === 'pdf2img'"
                class="bpc-btn bpc-btn-primary"
                type="button"
                :disabled="busy || !pdfFile"
                @click="runPdf2img"
              >
                {{ $t('tools.pdf.convert') }}
              </button>
              <button v-else class="bpc-btn bpc-btn-primary" type="button" :disabled="busy || !pdfFile" @click="runExtract">
                {{ $t('tools.pdf.extract') }}
              </button>
            </div>
          </div>
        </section>
      </div>
    </template>

    <div v-else class="bpc-grid">
      <section class="bpc-card">
        <div class="bpc-card-hd">
          <span>{{ $t('tools.pdf.sourceImages') }}</span>
          <span>{{ images.length ? $t('tools.pdf.imageCount', { n: images.length }) : '—' }}</span>
        </div>
        <div class="bpc-card-bd">
          <label class="bp-drop">
            <input ref="imageInput" class="bp-file" type="file" accept="image/*" multiple @change="onImagesPick" />
            <span class="bp-drop-title">{{ $t('tools.pdf.dropImages') }}</span>
            <span class="bp-drop-sub">{{ $t('tools.pdf.dropImagesSub') }}</span>
          </label>

          <div v-if="images.length" class="bp-img-list">
            <div v-for="(img, idx) in images" :key="img.id" class="bp-img-item">
              <img :src="img.url" alt="" />
              <span class="bp-img-index">{{ idx + 1 }}</span>
              <span class="bp-img-actions">
                <button
                  class="bpc-btn bpc-btn-sm"
                  type="button"
                  :disabled="busy || idx === 0"
                  :title="$t('tools.pdf.moveUp')"
                  @click="moveImage(idx, -1)"
                >
                  ↑
                </button>
                <button
                  class="bpc-btn bpc-btn-sm"
                  type="button"
                  :disabled="busy || idx === images.length - 1"
                  :title="$t('tools.pdf.moveDown')"
                  @click="moveImage(idx, 1)"
                >
                  ↓
                </button>
                <button
                  class="bpc-btn bpc-btn-sm bpc-btn-danger"
                  type="button"
                  :disabled="busy"
                  :title="$t('tools.pdf.remove')"
                  @click="removeImage(idx)"
                >
                  ×
                </button>
              </span>
            </div>
          </div>
          <p v-if="images.length" class="bpc-hint">{{ $t('tools.pdf.sortHint') }}</p>
          <p class="bpc-hint">{{ $t('tools.pdf.privacy') }}</p>
        </div>
      </section>

      <section class="bpc-card">
        <div class="bpc-card-hd">
          <span>{{ $t('tools.pdf.toPdf') }}</span>
          <span>{{ busy ? phaseText : 'idle' }}</span>
        </div>
        <div class="bpc-card-bd">
          <div class="bpc-field">
            <label class="bpc-label" for="bp-pdf-size">{{ $t('tools.pdf.pageSize') }}</label>
            <select id="bp-pdf-size" v-model="pageSize" class="bpc-select" :disabled="busy">
              <option value="A4">{{ $t('tools.pdf.sizeA4') }}</option>
              <option value="A4_LANDSCAPE">{{ $t('tools.pdf.sizeA4Landscape') }}</option>
              <option value="IMAGE">{{ $t('tools.pdf.sizeFitImage') }}</option>
            </select>
          </div>
          <div v-if="pageSize !== 'IMAGE'" class="bpc-field">
            <label class="bpc-label" for="bp-pdf-margin">{{ $t('tools.pdf.margin') }}</label>
            <select id="bp-pdf-margin" v-model.number="margin" class="bpc-select" :disabled="busy">
              <option :value="0">{{ $t('tools.pdf.marginNone') }}</option>
              <option :value="12">{{ $t('tools.pdf.marginNarrow') }}</option>
              <option :value="24">{{ $t('tools.pdf.marginWide') }}</option>
            </select>
          </div>
          <div class="bpc-actions">
            <button class="bpc-btn bpc-btn-primary" type="button" :disabled="busy || !images.length" @click="generatePdf">
              {{ $t('tools.pdf.generate') }}
            </button>
            <button v-if="generated" class="bpc-btn" type="button" :disabled="busy" @click="downloadGenerated">
              {{ $t('tools.pdf.downloadPdf') }}
            </button>
          </div>
          <p v-if="generated" class="bpc-meta">
            <span class="bpc-flash">{{ generated.name }}</span>
          </p>
        </div>
      </section>
    </div>

    <section v-if="busy" class="bpc-card">
      <div class="bpc-card-bd">
        <div class="bpc-inline">
          <span class="bpc-label">{{ phaseText }}</span>
          <span class="bpc-meta">{{ $t('tools.pdf.progressPages', { done: progress.current, total: progress.total }) }}</span>
          <button class="bpc-btn bpc-btn-sm bpc-btn-danger" type="button" @click="cancel">{{ $t('tools.pdf.cancel') }}</button>
        </div>
        <div class="bp-progress"><span :style="{ width: `${percent}%` }"></span></div>
      </div>
    </section>

    <section v-if="tab === 'pdf2img' && results.length" class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.pdf.resultTitle', { n: results.length }) }}</span>
        <button class="bpc-btn bpc-btn-sm" type="button" :disabled="busy" @click="downloadZip">{{ $t('tools.pdf.downloadZip') }}</button>
      </div>
      <div class="bpc-card-bd">
        <div class="bp-result-grid">
          <div v-for="item in results" :key="item.page" class="bp-result-item">
            <img :src="item.url" alt="" loading="lazy" />
            <div class="bp-result-meta">
              <span>{{ $t('tools.pdf.pageLabel', { n: item.page }) }}</span>
              <button class="bpc-btn bpc-btn-sm" type="button" @click="downloadOne(item)">{{ $t('tools.pdf.download') }}</button>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section v-if="tab === 'pdf2txt' && textPages.length" class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.pdf.textTitle', { n: textPages.length }) }}</span>
        <span class="bp-text-actions">
          <BpcCopy :value="displayText" :label="$t('tools.pdf.copyAll')" />
          <button class="bpc-btn bpc-btn-sm" type="button" @click="downloadText">{{ $t('tools.pdf.downloadTxt') }}</button>
        </span>
      </div>
      <div class="bpc-card-bd">
        <pre class="bpc-out bpc-out-tall">{{ displayText }}</pre>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, shallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import BpcChips from '../BpcChips.vue'
import BpcCopy from '../BpcCopy.vue'
import {
  buildImagePdf,
  createZip,
  extractPdfText,
  formatBytes,
  loadPdfDocument,
  normalizeImage,
  parsePageRange,
  renderPdfPageToBlob,
} from '@/utils/pdfTools'
import { saveBlob } from '@/utils/tools/download'

const { t } = useI18n()

const MAX_IMAGE_PAGES = 200
const MAX_IMAGES = 100
const QUALITY_SCALE = { standard: 1.5, high: 2, ultra: 3 }

const tab = ref('pdf2img')
const pdfInput = ref(null)
const pdfInputAlt = ref(null)
const imageInput = ref(null)

const pdfFile = ref(null)
// pdfjs 文档对象用 shallowRef:深层响应式代理会拦截其私有字段,getPage 会抛「Cannot read from private field」
const pdfDoc = shallowRef(null)
const pdfPageCount = ref(0)
const pdfThumbUrl = ref('')
const pdfLoading = ref(false)

const imgFormat = ref('png')
const imgQuality = ref('high')
const pageRange = ref('')
const results = ref([])
const textPages = ref([])

const images = ref([])
const pageSize = ref('A4')
const margin = ref(24)
const generated = ref(null)

const busy = ref(false)
const cancelFlag = ref(false)
const phase = ref('')
const progress = ref({ current: 0, total: 0 })
const notice = ref(null)

const tabOptions = computed(() => [
  { value: 'pdf2img', label: t('tools.pdf.tabPdf2img') },
  { value: 'img2pdf', label: t('tools.pdf.tabImg2pdf') },
  { value: 'pdf2txt', label: t('tools.pdf.tabPdf2txt') },
])
const formatOptions = computed(() => [
  { value: 'png', label: 'PNG' },
  { value: 'jpeg', label: 'JPEG' },
])

const percent = computed(() => (progress.value.total ? Math.round((progress.value.current / progress.value.total) * 100) : 0))
const phaseText = computed(() => {
  if (phase.value === 'render') return t('tools.pdf.rendering')
  if (phase.value === 'text') return t('tools.pdf.extracting')
  if (phase.value === 'pdf') return t('tools.pdf.generating')
  return ''
})
const displayText = computed(() => textPages.value.map((p) => `${t('tools.pdf.pageDivider', { n: p.page })}\n${p.text}`).join('\n\n'))
const pdfBaseName = computed(() => (pdfFile.value?.name || 'document').replace(/\.pdf$/i, ''))

const say = (text, kind = 'ok') => {
  notice.value = { text, kind }
  setTimeout(() => {
    if (notice.value?.text === text) notice.value = null
  }, 2600)
}

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

const resetPdf = () => {
  destroyPdf()
  revoke(pdfThumbUrl.value)
  pdfThumbUrl.value = ''
  pdfFile.value = null
  pdfPageCount.value = 0
  pageRange.value = ''
  clearResults()
  textPages.value = []
}

const pickPdf = () => {
  if (!busy.value) (pdfInput.value || pdfInputAlt.value)?.click()
}

const loadPdf = async (file) => {
  if (!file) return
  if (file.type !== 'application/pdf' && !/\.pdf$/i.test(file.name)) {
    say(t('tools.pdf.notPdf'), 'error')
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
    say(e?.name === 'PasswordException' ? t('tools.pdf.encrypted') : t('tools.pdf.loadFailed'), 'error')
    resetPdf()
  } finally {
    pdfLoading.value = false
  }
}

const onPdfPick = (event) => {
  const file = event.target.files?.[0]
  event.target.value = ''
  loadPdf(file)
}

const resolvePages = () => {
  const pages = parsePageRange(pageRange.value, pdfPageCount.value)
  if (!pages) {
    say(t('tools.pdf.invalidRange', { total: pdfPageCount.value }), 'error')
    return null
  }
  if (pages.length > MAX_IMAGE_PAGES) {
    say(t('tools.pdf.tooManyPages', { max: MAX_IMAGE_PAGES }), 'error')
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
    for (let i = 0; i < pages.length; i += 1) {
      if (cancelFlag.value) break
      const page = await pdfDoc.value.getPage(pages[i])
      const blob = await renderPdfPageToBlob(page, { scale, format })
      page.cleanup?.()
      results.value.push({ page: pages[i], blob, url: URL.createObjectURL(blob), ext })
      progress.value.current = i + 1
    }
    if (cancelFlag.value) say(t('tools.pdf.cancelled'))
    else say(t('tools.pdf.convertDone', { n: results.value.length }))
  } catch {
    say(t('tools.pdf.convertFailed'), 'error')
  } finally {
    busy.value = false
    phase.value = ''
  }
}

const downloadOne = (item) => {
  const n = String(item.page).padStart(3, '0')
  saveBlob(item.blob, `${pdfBaseName.value}-p${n}.${item.ext}`)
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
    saveBlob(zip, `${pdfBaseName.value}-images.zip`)
  } catch {
    say(t('tools.pdf.zipFailed'), 'error')
  } finally {
    busy.value = false
  }
}

const runExtract = async () => {
  if (!pdfDoc.value || busy.value) return
  const pages = resolvePages()
  if (!pages) return

  textPages.value = []
  busy.value = true
  cancelFlag.value = false
  phase.value = 'text'
  progress.value = { current: 0, total: pages.length }
  try {
    const result = await extractPdfText(pdfDoc.value, pages, {
      onProgress: (done, total) => {
        progress.value = { current: done, total }
      },
      isCancelled: () => cancelFlag.value,
    })
    textPages.value = result
    if (cancelFlag.value) say(t('tools.pdf.cancelled'))
    else if (!displayText.value.trim()) say(t('tools.pdf.noText'), 'error')
    else say(t('tools.pdf.extractDone', { n: result.length }))
  } catch {
    say(t('tools.pdf.extractFailed'), 'error')
  } finally {
    busy.value = false
    phase.value = ''
  }
}

const downloadText = () => {
  saveBlob(new Blob(['\ufeff', displayText.value], { type: 'text/plain;charset=utf-8' }), `${pdfBaseName.value}.txt`)
}

const onImagesPick = (event) => {
  addImageFiles(event.target.files)
  event.target.value = ''
}

const addImageFiles = (fileList) => {
  const files = Array.from(fileList || []).filter((f) => f.type.startsWith('image/') || /\.(png|jpe?g|webp|gif|bmp)$/i.test(f.name))
  if (!files.length) {
    say(t('tools.pdf.invalidImage'), 'error')
    return
  }
  if (images.value.length + files.length > MAX_IMAGES) {
    say(t('tools.pdf.tooManyImages', { max: MAX_IMAGES }), 'error')
    return
  }
  for (const file of files) {
    images.value.push({ id: `${Date.now()}-${Math.random().toString(36).slice(2)}`, file, url: URL.createObjectURL(file) })
  }
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
    for (let i = 0; i < images.value.length; i += 1) {
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
      say(t('tools.pdf.cancelled'))
      return
    }
    const blob = buildImagePdf(source, { pageSize: pageSize.value, margin: margin.value })
    if (generated.value?.url) revoke(generated.value.url)
    generated.value = {
      url: URL.createObjectURL(blob),
      name: `${images.value[0].file.name.replace(/\.[^.]+$/, '')}-${source.length}.pdf`,
    }
    say(t('tools.pdf.generateDone', { n: source.length }))
  } catch {
    say(t('tools.pdf.generateFailed'), 'error')
  } finally {
    busy.value = false
    phase.value = ''
  }
}

const downloadGenerated = () => {
  if (!generated.value) return
  const anchor = document.createElement('a')
  anchor.href = generated.value.url
  anchor.download = generated.value.name || 'images.pdf'
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
}

const cancel = () => {
  cancelFlag.value = true
}

onBeforeUnmount(() => {
  destroyPdf()
  revoke(pdfThumbUrl.value)
  clearResults()
  clearImages()
  if (generated.value?.url) revoke(generated.value.url)
})
</script>

<style scoped>
.bp-drop {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 120px;
  padding: 18px;
  border: 1px dashed var(--line);
  border-radius: 2px;
  color: var(--muted);
  cursor: pointer;
  text-align: center;
}
.bp-drop:hover,
.bp-drop:focus-within {
  border-color: var(--accent);
  color: var(--text);
}
.bp-file {
  display: none;
}
.bp-drop-title {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text);
  font-size: 13px;
}
.bp-drop-sub {
  font-size: 11.5px;
}
.bp-pdf-file {
  display: flex;
  align-items: center;
  gap: 12px;
}
.bp-pdf-thumb {
  width: 72px;
  border: 1px solid var(--line);
  border-radius: 2px;
}
.bp-pdf-main {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
  flex: 1;
}
.bp-progress {
  height: 6px;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 2px;
  background: var(--bg);
}
.bp-progress span {
  display: block;
  height: 100%;
  background: var(--accent);
  transition: width 0.2s ease;
}
.bp-result-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
}
.bp-result-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  border: 1px solid var(--line);
  border-radius: 2px;
  background: var(--bg);
  padding: 8px;
}
.bp-result-item img {
  width: 100%;
  height: 150px;
  object-fit: contain;
}
.bp-result-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: var(--muted);
  font: 11px/1.5 var(--mono);
}
.bp-img-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(110px, 1fr));
  gap: 10px;
}
.bp-img-item {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 6px;
  border: 1px solid var(--line);
  border-radius: 2px;
  background: var(--bg);
}
.bp-img-item img {
  width: 100%;
  height: 84px;
  object-fit: cover;
}
.bp-img-index {
  position: absolute;
  top: 10px;
  left: 10px;
  padding: 0 5px;
  background: var(--accent);
  color: var(--accent-ink);
  font: 10.5px/1.6 var(--mono);
}
.bp-img-actions {
  display: flex;
  gap: 4px;
}
.bp-img-actions .bpc-btn {
  padding: 2px 7px;
}
.bp-text-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}
</style>
