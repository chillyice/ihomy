<!-- 图片面板:压缩 / ICO 图标打包 / JPEG EXIF 查看,全部在本机 Canvas 与字节解析里完成。
     内核复用 utils/tools/imageTool,单文件上限与工具箱一致。 -->
<template>
  <div class="bpc-grid bpc-grid-wide">
    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.image.title') }}</span>
        <span>{{ metaText }}</span>
      </div>
      <div class="bpc-card-bd">
        <BpcChips v-model="mode" :options="modeOptions" />

        <label class="bp-drop">
          <input class="bp-file" type="file" :accept="accept" @change="onFileChange" />
          <span class="bp-drop-title">{{ file ? file.name : $t('tools.image.choose') }}</span>
          <span class="bp-drop-sub">{{ file ? formatBytes(file.size) : $t('tools.image.hint') }}</span>
        </label>

        <template v-if="mode === 'compress'">
          <div class="bpc-row-2">
            <div class="bpc-field">
              <label class="bpc-label" for="bp-img-width">{{ $t('tools.image.maxWidth') }}</label>
              <input id="bp-img-width" v-model.number="maxWidth" class="bpc-input" type="number" min="64" max="8192" step="128" />
            </div>
            <div class="bpc-field">
              <label class="bpc-label" for="bp-img-quality">{{ $t('tools.image.quality', { n: quality }) }}</label>
              <input id="bp-img-quality" v-model.number="quality" class="bp-range" type="range" min="40" max="100" step="1" />
            </div>
          </div>
          <div class="bpc-field">
            <label class="bpc-label" for="bp-img-format">{{ $t('tools.image.format') }}</label>
            <select id="bp-img-format" v-model="format" class="bpc-select">
              <option value="auto">{{ $t('tools.image.formatAuto') }}</option>
              <option value="jpeg">JPEG</option>
              <option value="webp">WebP</option>
              <option value="png">PNG</option>
            </select>
          </div>
          <div class="bpc-actions">
            <button class="bpc-btn bpc-btn-primary" type="button" :disabled="!file || busy" @click="compress">
              {{ $t('tools.image.compress') }}
            </button>
          </div>
          <p class="bpc-hint">{{ $t('tools.image.metaCleared') }}</p>
        </template>

        <template v-else-if="mode === 'favicon'">
          <div class="bpc-field">
            <span class="bpc-label">{{ $t('tools.image.sizes') }}</span>
            <BpcChips v-model="sizes" :options="sizeOptions" multiple />
          </div>
          <div class="bpc-actions">
            <button class="bpc-btn bpc-btn-primary" type="button" :disabled="!file || !sizes.length || busy" @click="makeFavicon">
              {{ $t('tools.image.generate') }}
            </button>
          </div>
        </template>

        <p v-else class="bpc-hint">{{ $t('tools.image.exifHint') }}</p>

        <div class="bpc-actions">
          <button class="bpc-btn" type="button" :disabled="!file" @click="reset">{{ $t('tools.image.clear') }}</button>
        </div>
        <p class="bpc-hint">{{ $t('tools.image.privacy') }}</p>
      </div>
    </section>

    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.image.result') }}</span>
        <span>{{ $t(`tools.image.mode${mode === 'compress' ? 'Compress' : mode === 'favicon' ? 'Favicon' : 'Exif'}`) }}</span>
      </div>
      <div class="bpc-card-bd">
        <p v-if="error" class="bpc-err">{{ error }}</p>

        <template v-else-if="mode === 'compress'">
          <p v-if="!compressed" class="bpc-hint">{{ $t('tools.image.emptyCompress') }}</p>
          <template v-else>
            <img :src="compressed.url" class="bp-preview-img" :alt="$t('tools.image.previewAlt')" />
            <div class="bpc-meta">
              <span>{{ compressed.width }} × {{ compressed.height }}</span>
              <span>{{ formatBytes(compressed.size) }}</span>
              <span class="bpc-flash">{{ deltaText }}</span>
            </div>
            <div class="bpc-actions">
              <button class="bpc-btn" type="button" @click="download(compressed.blob, `compressed.${compressed.ext}`)">
                {{ $t('tools.image.download') }}
              </button>
              <BpcCopy :value="compressed.dataUrl" :label="$t('tools.image.copyDataUri')" />
            </div>
          </template>
        </template>

        <template v-else-if="mode === 'favicon'">
          <p v-if="!ico" class="bpc-hint">{{ $t('tools.image.emptyFavicon') }}</p>
          <template v-else>
            <div class="bpc-meta">
              <span>{{ $t('tools.image.icoStats', { n: ico.entries.length, size: formatBytes(ico.blob.size) }) }}</span>
            </div>
            <div class="bpc-actions">
              <button class="bpc-btn" type="button" @click="download(ico.blob, 'favicon.ico')">
                {{ $t('tools.image.downloadIco') }}
              </button>
            </div>
          </template>
        </template>

        <template v-else>
          <p v-if="!exif" class="bpc-hint">{{ $t('tools.image.emptyExif') }}</p>
          <p v-else-if="!exif.hasExif" class="bpc-hint">{{ $t('tools.image.noExif') }}</p>
          <div v-else class="bpc-table-wrap">
            <table class="bpc-table">
              <tbody>
                <tr v-for="tag in exif.tags" :key="tag.tag">
                  <td class="bp-tag-name">{{ tag.name }}</td>
                  <td>{{ tag.value }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import BpcChips from '../BpcChips.vue'
import BpcCopy from '../BpcCopy.vue'
import { ICO_SIZES, MAX_IMAGE_BYTES, buildIco, parseJpegExif } from '@/utils/tools/imageTool'
import { formatFileSize as formatBytes } from '@/utils/tools/fileTool'
import { saveBlob } from '@/utils/tools/download'

const { t } = useI18n()

const mode = ref('compress')
const file = ref(null)
const maxWidth = ref(1920)
const quality = ref(82)
const format = ref('auto')
const sizes = ref([16, 32, 48, 256])

const busy = ref(false)
const error = ref('')
const compressed = ref(null)
const ico = ref(null)
const exif = ref(null)

const modeOptions = computed(() => [
  { value: 'compress', label: t('tools.image.modeCompress') },
  { value: 'favicon', label: t('tools.image.modeFavicon') },
  { value: 'exif', label: t('tools.image.modeExif') },
])
const sizeOptions = computed(() => ICO_SIZES.map((size) => ({ value: size, label: String(size) })))

const accept = computed(() => (mode.value === 'exif' ? 'image/jpeg' : 'image/*'))
const metaText = computed(() =>
  file.value ? t('tools.image.meta', { size: formatBytes(file.value.size), max: formatBytes(MAX_IMAGE_BYTES) }) : '',
)
const deltaText = computed(() => {
  if (!compressed.value || !file.value?.size) return ''
  const ratio = Math.round((1 - compressed.value.size / file.value.size) * 100)
  return ratio >= 0 ? t('tools.image.saved', { n: ratio }) : t('tools.image.grown', { n: Math.abs(ratio) })
})

const resetResult = () => {
  error.value = ''
  if (compressed.value?.url) URL.revokeObjectURL(compressed.value.url)
  compressed.value = null
  ico.value = null
  exif.value = null
}

const reset = () => {
  file.value = null
  resetResult()
}

const loadImage = (source) =>
  new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve(image)
    image.onerror = () => reject(new Error('decode'))
    image.src = URL.createObjectURL(source)
  })

const canvasToBlob = (canvas, type, qualityValue) => new Promise((resolve) => canvas.toBlob((blob) => resolve(blob), type, qualityValue))

async function compress() {
  resetResult()
  if (!file.value) return
  busy.value = true
  try {
    const image = await loadImage(file.value)
    const scale = Math.min(1, maxWidth.value / image.naturalWidth)
    const width = Math.max(1, Math.round(image.naturalWidth * scale))
    const height = Math.max(1, Math.round(image.naturalHeight * scale))
    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    canvas.getContext('2d').drawImage(image, 0, 0, width, height)
    const type = format.value === 'auto' ? (file.value.type === 'image/png' ? 'image/png' : 'image/jpeg') : `image/${format.value}`
    const blob = await canvasToBlob(canvas, type, quality.value / 100)
    if (!blob) throw new Error('encode')
    const actualType = blob.type || type
    compressed.value = {
      blob,
      url: URL.createObjectURL(blob),
      width,
      height,
      size: blob.size,
      dataUrl: canvas.toDataURL(actualType, quality.value / 100),
      ext: actualType.includes('png') ? 'png' : actualType.includes('webp') ? 'webp' : 'jpg',
    }
  } catch {
    error.value = t('tools.image.errImage')
  } finally {
    busy.value = false
  }
}

async function makeFavicon() {
  resetResult()
  if (!file.value) return
  busy.value = true
  try {
    const image = await loadImage(file.value)
    const frames = []
    for (const size of [...sizes.value].sort((a, b) => a - b)) {
      const canvas = document.createElement('canvas')
      canvas.width = size
      canvas.height = size
      const context = canvas.getContext('2d')
      const scale = Math.max(size / image.naturalWidth, size / image.naturalHeight)
      const width = image.naturalWidth * scale
      const height = image.naturalHeight * scale
      context.drawImage(image, (size - width) / 2, (size - height) / 2, width, height)
      const blob = await canvasToBlob(canvas, 'image/png')
      if (!blob) throw new Error('encode')
      frames.push({ width: size, height: size, data: new Uint8Array(await blob.arrayBuffer()) })
    }
    const bytes = buildIco(frames)
    ico.value = {
      blob: new Blob([bytes], { type: 'image/x-icon' }),
      entries: frames.map((frame) => ({ width: frame.width, height: frame.height })),
    }
  } catch (err) {
    error.value = t(`tools.image.err.${err?.code || 'image'}`)
  } finally {
    busy.value = false
  }
}

async function readExif() {
  resetResult()
  if (!file.value) return
  busy.value = true
  try {
    const bytes = new Uint8Array(await file.value.arrayBuffer())
    exif.value = parseJpegExif(bytes)
  } catch (err) {
    error.value = t(`tools.image.err.${err?.code || 'image'}`)
  } finally {
    busy.value = false
  }
}

const onFileChange = async (event) => {
  const next = event.target.files?.[0] ?? null
  resetResult()
  if (!next) {
    file.value = null
    return
  }
  if (next.size > MAX_IMAGE_BYTES) {
    file.value = null
    error.value = t('tools.image.errTooLarge', { max: formatBytes(MAX_IMAGE_BYTES) })
    return
  }
  file.value = next
  if (mode.value === 'exif') await readExif()
}

const download = (blob, name) => saveBlob(blob, name)

watch(mode, () => {
  resetResult()
  if (mode.value === 'exif' && file.value) readExif()
})

onBeforeUnmount(resetResult)
</script>

<style scoped>
.bp-drop {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 110px;
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
.bp-range {
  width: 100%;
  accent-color: var(--accent);
}
.bp-preview-img {
  max-width: 100%;
  max-height: 320px;
  border: 1px solid var(--line);
  border-radius: 2px;
  object-fit: contain;
}
.bp-tag-name {
  color: var(--muted);
  white-space: nowrap;
}
</style>
