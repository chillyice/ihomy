<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('tools.title') }, { label: $t('tools.file.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <el-radio-group v-model="mode" size="small">
          <el-radio-button value="detect">{{ $t('tools.file.modeDetect') }}</el-radio-button>
          <el-radio-button value="pack">{{ $t('tools.file.modePack') }}</el-radio-button>
          <el-radio-button value="unpack">{{ $t('tools.file.modeUnpack') }}</el-radio-button>
        </el-radio-group>
        <span class="file-meta">{{ metaText }}</span>
      </div>
      <div class="tb-right">
        <el-button size="small" :disabled="!hasInput" @click="clearAll">{{ $t('tools.file.clear') }}</el-button>
      </div>
    </PageToolbar>

    <div class="file-grid">
      <div class="card file-pane">
        <label class="file-drop">
          <input type="file" class="file-input" :accept="accept" :multiple="mode === 'pack'" @change="onFileChange" />
          <span class="file-drop-title">{{ fileLabel }}</span>
          <span class="file-drop-sub">{{ $t('tools.file.hint', { max: formatBytes(MAX_PACK_BYTES) }) }}</span>
        </label>

        <template v-if="mode === 'detect'">
          <div v-if="head" class="file-info">
            <div class="section-label">{{ $t('tools.file.headBytes') }}</div>
            <pre class="file-hex">{{ head }}</pre>
            <div v-if="detected" class="file-result">
              <span class="file-type">{{ detected.name }}</span>
              <span class="file-mime">{{ detected.mime }}</span>
            </div>
            <div v-else class="image-empty">{{ $t('tools.file.unknown') }}</div>
            <div v-if="sha256" class="file-result">
              <span class="file-mime">{{ $t('tools.file.sha256') }}</span>
              <code class="file-hash">{{ sha256 }}</code>
            </div>
          </div>
        </template>

        <template v-else-if="mode === 'pack'">
          <div v-if="files.length" class="file-list">
            <div v-for="(item, index) in previewNames" :key="index" class="file-list-row">
              <span class="file-list-name">{{ item }}</span>
              <span class="file-size">{{ formatBytes(files[index].size) }}</span>
            </div>
          </div>
          <div class="file-field">
            <label for="zip-name">{{ $t('tools.file.zipName') }}</label>
            <el-input id="zip-name" v-model="zipName" size="small" :placeholder="$t('tools.file.zipNamePlaceholder')" />
          </div>
          <el-button size="small" type="primary" :disabled="!files.length" :loading="busy" @click="pack">
            {{ $t('tools.file.pack') }}
          </el-button>
        </template>

        <template v-else>
          <div v-if="!entries.length" class="image-empty">{{ $t('tools.file.unpackHint') }}</div>
        </template>

        <p class="file-privacy">{{ $t('tools.file.privacy') }}</p>
      </div>

      <div class="card file-pane">
        <div class="section-label">{{ mode === 'unpack' ? $t('tools.file.entries') : $t('tools.file.result') }}</div>

        <div v-if="error" class="file-error">{{ error }}</div>

        <template v-else-if="mode === 'unpack'">
          <div v-if="!entries.length" class="image-empty">{{ $t('tools.file.emptyEntries') }}</div>
          <div v-else class="file-list">
            <div v-for="entry in entries" :key="entry.name" class="file-list-row">
              <span class="file-list-name">{{ entry.name }}</span>
              <span class="file-size">{{ formatBytes(entry.size) }}</span>
              <el-button size="small" text @click="downloadEntry(entry)">{{ $t('tools.file.download') }}</el-button>
            </div>
          </div>
        </template>

        <template v-else>
          <div v-if="!packed" class="image-empty">{{ $t('tools.file.emptyResult') }}</div>
          <template v-else>
            <div class="file-result">
              <span class="file-type">{{ zipName || 'archive' }}.zip</span>
              <span class="file-mime">{{ $t('tools.file.zipStats', { n: packed.count, size: formatBytes(packed.size) }) }}</span>
            </div>
            <el-button size="small" @click="downloadPacked">{{ $t('tools.file.download') }}</el-button>
          </template>
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
import { detectFileType, formatFileSize as formatBytes, planZipNames } from '@/utils/tools/fileTool'
import { runFile as hashFile } from '@/utils/tools/hashTool'

const MAX_PACK_BYTES = 200 * 1024 * 1024
const HEAD_BYTES = 64

const { t, locale } = useI18n()

const mode = ref('detect')
const files = ref([])
const detected = ref(null)
const head = ref('')
const sha256 = ref('')
const zipName = ref('archive')
const packed = ref(null)
const entries = ref([])
const error = ref('')
const busy = ref(false)

const accept = computed(() => (mode.value === 'unpack' ? '.zip,application/zip' : ''))
const hasInput = computed(() => (mode.value === 'pack' ? files.value.length > 0 : files.value.length > 0 || Boolean(detected.value)))
const fileLabel = computed(() => {
  if (!files.value.length) return t(mode.value === 'pack' ? 'tools.file.chooseMany' : 'tools.file.choose')
  if (files.value.length === 1) return files.value[0].name
  return t('tools.file.selected', { n: files.value.length })
})
const previewNames = computed(() => planZipNames(files.value.map((file) => file.name)))
const metaText = computed(() => {
  if (mode.value === 'pack') return t('tools.file.totalSize', { size: formatBytes(files.value.reduce((sum, file) => sum + file.size, 0)) })
  if (mode.value === 'unpack') return entries.value.length ? t('tools.file.entryCount', { n: entries.value.length }) : ''
  return files.value[0] ? formatBytes(files.value[0].size) : ''
})

const clearAll = () => {
  files.value = []
  detected.value = null
  head.value = ''
  sha256.value = ''
  packed.value = null
  entries.value = []
  error.value = ''
}

const onFileChange = async (event) => {
  const list = Array.from(event.target.files || [])
  clearAll()
  if (!list.length) return
  files.value = mode.value === 'pack' ? list : list.slice(0, 1)
  if (mode.value === 'detect') await inspect(files.value[0])
  if (mode.value === 'unpack') await unpack(files.value[0])
}

async function inspect(file) {
  busy.value = true
  error.value = ''
  try {
    const bytes = new Uint8Array(await file.slice(0, HEAD_BYTES).arrayBuffer())
    head.value = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join(' ')
    detected.value = detectFileType(bytes)
    const digest = await hashFile(file, { algorithms: ['sha256'] }, { locale: locale.value?.startsWith('en') ? 'en' : 'zh' })
    sha256.value = digest.ok ? digest.data.results[0]?.value || '' : ''
  } catch {
    error.value = t('tools.file.errRead')
  } finally {
    busy.value = false
  }
}

async function pack() {
  busy.value = true
  error.value = ''
  packed.value = null
  try {
    const { zipSync } = await import('fflate')
    const total = files.value.reduce((sum, file) => sum + file.size, 0)
    if (total > MAX_PACK_BYTES) {
      error.value = t('tools.file.errTooLarge', { max: formatBytes(MAX_PACK_BYTES) })
      return
    }
    const names = previewNames.value
    const input = {}
    for (let index = 0; index < files.value.length; index += 1) {
      input[names[index]] = new Uint8Array(await files.value[index].arrayBuffer())
    }
    const output = zipSync(input, { level: 6 })
    packed.value = { bytes: output, size: output.length, count: names.length }
  } catch {
    error.value = t('tools.file.errZip')
  } finally {
    busy.value = false
  }
}

async function unpack(file) {
  busy.value = true
  error.value = ''
  try {
    const { unzipSync } = await import('fflate')
    const map = unzipSync(new Uint8Array(await file.arrayBuffer()))
    entries.value = Object.entries(map)
      .filter(([name]) => !name.endsWith('/'))
      .map(([name, data]) => ({ name, data, size: data.length }))
  } catch {
    error.value = t('tools.file.errUnzip')
  } finally {
    busy.value = false
  }
}

const saveBlob = (blob, name) => {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = name
  anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
  ElMessage.success(t('tools.file.downloaded'))
}

const downloadPacked = () => {
  if (!packed.value) return
  saveBlob(new Blob([packed.value.bytes], { type: 'application/zip' }), `${zipName.value || 'archive'}.zip`)
}

const downloadEntry = (entry) => saveBlob(new Blob([entry.data]), entry.name.split('/').pop())

watch(mode, () => {
  const current = files.value
  clearAll()
  if (!current.length) return
  files.value = mode.value === 'pack' ? current : current.slice(0, 1)
  if (mode.value === 'detect') inspect(files.value[0])
  if (mode.value === 'unpack') unpack(files.value[0])
})
</script>

<style scoped>
.file-meta {
  color: var(--color-text-secondary);
  font-size: 12px;
}
.file-grid {
  display: grid;
  grid-template-columns: minmax(0, 5fr) minmax(0, 4fr);
  gap: 16px;
  align-items: start;
}
@media (max-width: 900px) {
  .file-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
.file-pane {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.file-drop {
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
.file-drop:hover,
.file-drop:focus-within {
  border-color: var(--color-accent);
  color: var(--color-text);
}
.file-input {
  display: none;
}
.file-drop-title {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  color: var(--color-text);
}
.file-drop-sub {
  font-size: 12px;
}
.file-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.file-hex {
  margin: 0;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--color-bg-soft);
  font-family: 'Cascadia Code', Consolas, monospace;
  font-size: 12px;
  line-height: 1.7;
  word-break: break-all;
}
.file-result {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
}
.file-type {
  font-size: 14px;
  font-weight: 600;
}
.file-mime {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.file-hash {
  font-family: 'Cascadia Code', Consolas, monospace;
  font-size: 11.5px;
  word-break: break-all;
}
.file-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.file-field label {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.file-list {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  overflow: hidden;
}
.file-list-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 12px;
  font-size: 12.5px;
}
.file-list-row:nth-child(odd) {
  background: var(--color-bg-soft);
}
.file-list-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.file-size {
  color: var(--color-text-secondary);
  font-size: 12px;
}
.file-privacy {
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
.file-error {
  padding: 10px 12px;
  border-radius: 8px;
  background: rgba(220, 80, 80, 0.12);
  color: var(--color-danger, #c0392b);
  font-size: 13px;
}
</style>
