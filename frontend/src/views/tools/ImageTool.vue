<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('tools.title') }, { label: $t('tools.image.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <el-radio-group v-model="mode" size="small">
          <el-radio-button value="compress">{{ $t('tools.image.modeCompress') }}</el-radio-button>
          <el-radio-button value="favicon">{{ $t('tools.image.modeFavicon') }}</el-radio-button>
          <el-radio-button value="exif">{{ $t('tools.image.modeExif') }}</el-radio-button>
        </el-radio-group>
        <span class="image-meta">{{ metaText }}</span>
      </div>
      <div class="tb-right">
        <el-button size="small" :disabled="!file" @click="reset">{{ $t('tools.image.clear') }}</el-button>
      </div>
    </PageToolbar>

    <div class="image-grid">
      <div class="card image-pane">
        <label class="image-drop">
          <input type="file" class="image-file" :accept="accept" @change="onFileChange" />
          <span class="image-drop-title">{{ file ? file.name : $t('tools.image.choose') }}</span>
          <span class="image-drop-sub">{{ file ? formatBytes(file.size) : $t('tools.image.hint') }}</span>
        </label>

        <template v-if="mode === 'compress'">
          <div class="image-field">
            <label>{{ $t('tools.image.maxWidth') }}</label>
            <el-input-number v-model="maxWidth" size="small" :min="64" :max="8192" :step="128" controls-position="right" />
          </div>
          <div class="image-field">
            <label>{{ $t('tools.image.quality', { n: quality }) }}</label>
            <el-slider v-model="quality" :min="40" :max="100" :step="1" size="small" />
          </div>
          <div class="image-field">
            <label>{{ $t('tools.image.format') }}</label>
            <el-select v-model="format" size="small" style="width: 160px">
              <el-option value="auto" :label="$t('tools.image.formatAuto')" />
              <el-option value="jpeg" label="JPEG" />
              <el-option value="webp" label="WebP" />
              <el-option value="png" label="PNG" />
            </el-select>
          </div>
          <el-button size="small" type="primary" :disabled="!file" :loading="busy" @click="compress">
            {{ $t('tools.image.compress') }}
          </el-button>
          <p class="image-note">{{ $t('tools.image.metaCleared') }}</p>
        </template>

        <template v-else-if="mode === 'favicon'">
          <div class="image-field">
            <label>{{ $t('tools.image.sizes') }}</label>
            <el-checkbox-group v-model="sizes" size="small">
              <el-checkbox-button v-for="size in ICO_SIZES" :key="size" :value="size">{{ size }}</el-checkbox-button>
            </el-checkbox-group>
          </div>
          <el-button size="small" type="primary" :disabled="!file || !sizes.length" :loading="busy" @click="makeFavicon">
            {{ $t('tools.image.generate') }}
          </el-button>
        </template>

        <p v-else class="image-note">{{ $t('tools.image.exifHint') }}</p>

        <p class="image-privacy">{{ $t('tools.image.privacy') }}</p>
      </div>

      <div class="card image-pane">
        <div class="section-label">{{ $t('tools.image.result') }}</div>

        <div v-if="error" class="image-error">{{ error }}</div>

        <template v-else-if="mode === 'compress'">
          <div v-if="!compressed" class="image-empty">{{ $t('tools.image.emptyCompress') }}</div>
          <template v-else>
            <img :src="compressed.url" class="image-preview" :alt="$t('tools.image.previewAlt')" />
            <div class="image-stats">
              {{ compressed.width }} × {{ compressed.height }} · {{ formatBytes(compressed.size) }}
              <span class="image-delta">({{ deltaText }})</span>
            </div>
            <el-button size="small" :disabled="!compressed" @click="download(compressed.blob, `compressed.${compressed.ext}`)">
              {{ $t('tools.image.download') }}
            </el-button>
            <el-button size="small" @click="copyDataUri">{{ $t('tools.image.copyDataUri') }}</el-button>
          </template>
        </template>

        <template v-else-if="mode === 'favicon'">
          <div v-if="!ico" class="image-empty">{{ $t('tools.image.emptyFavicon') }}</div>
          <template v-else>
            <div class="image-stats">{{ $t('tools.image.icoStats', { n: ico.entries.length, size: formatBytes(ico.blob.size) }) }}</div>
            <el-button size="small" @click="download(ico.blob, 'favicon.ico')">{{ $t('tools.image.downloadIco') }}</el-button>
          </template>
        </template>

        <template v-else>
          <div v-if="!exif" class="image-empty">{{ $t('tools.image.emptyExif') }}</div>
          <div v-else-if="!exif.hasExif" class="image-empty">{{ $t('tools.image.noExif') }}</div>
          <div v-else class="image-table">
            <div v-for="tag in exif.tags" :key="tag.tag" class="image-tr">
              <span class="image-tag-name">{{ tag.name }}</span>
              <span class="image-tag-value">{{ tag.value }}</span>
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import { ICO_SIZES, MAX_IMAGE_BYTES, buildIco, parseJpegExif } from '@/utils/tools/imageTool'
import { formatFileSize as formatBytes } from '@/utils/tools/fileTool'

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

const download = (blob, name) => {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = name
  anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
  ElMessage.success(t('tools.image.downloaded'))
}

const copyDataUri = async () => {
  if (!compressed.value?.dataUrl) return
  try {
    await navigator.clipboard.writeText(compressed.value.dataUrl)
    ElMessage.success(t('tools.image.dataUriCopied'))
  } catch {
    ElMessage.error(t('tools.image.copyFailed'))
  }
}

watch(mode, () => {
  resetResult()
  if (mode.value === 'exif' && file.value) readExif()
})
</script>

<style scoped>
.image-meta {
  color: var(--color-text-secondary);
  font-size: 12px;
}
.image-grid {
  display: grid;
  grid-template-columns: minmax(0, 5fr) minmax(0, 4fr);
  gap: 16px;
  align-items: start;
}
@media (max-width: 900px) {
  .image-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
.image-pane {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.image-drop {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 120px;
  padding: 18px;
  border: 1px dashed var(--color-border);
  border-radius: 10px;
  color: var(--color-text-secondary);
  cursor: pointer;
  text-align: center;
}
.image-drop:hover,
.image-drop:focus-within {
  border-color: var(--color-accent);
  color: var(--color-text);
}
.image-file {
  display: none;
}
.image-drop-title {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  color: var(--color-text);
}
.image-drop-sub {
  font-size: 12px;
}
.image-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.image-field label {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.image-note,
.image-privacy {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.image-empty {
  padding: 24px 0;
  color: var(--color-text-secondary);
  font-size: 13px;
  text-align: center;
}
.image-error {
  padding: 10px 12px;
  border-radius: 8px;
  background: rgba(220, 80, 80, 0.12);
  color: var(--color-danger, #c0392b);
  font-size: 13px;
}
.image-preview {
  max-width: 100%;
  max-height: 320px;
  border-radius: 10px;
  object-fit: contain;
}
.image-stats {
  font-size: 12.5px;
  color: var(--color-text-secondary);
}
.image-delta {
  margin-left: 6px;
}
.image-table {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  overflow: hidden;
}
.image-tr {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.4fr);
  gap: 10px;
  padding: 6px 12px;
  font-size: 12.5px;
}
.image-tr:nth-child(odd) {
  background: var(--color-bg-soft);
}
.image-tag-name {
  color: var(--color-text-secondary);
}
.image-tag-value {
  word-break: break-word;
}
</style>
