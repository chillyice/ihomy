<!-- 文件面板:文件头类型识别 + SHA-256、ZIP 打包、ZIP 解压。文件只在浏览器内读取,
     ZIP 用 fflate 按需加载;内核复用 utils/tools/fileTool 与 hashTool。 -->
<template>
  <div class="bpc-grid bpc-grid-wide">
    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.file.title') }}</span>
        <span>{{ metaText }}</span>
      </div>
      <div class="bpc-card-bd">
        <BpcChips v-model="mode" :options="modeOptions" />

        <label class="bp-drop">
          <input class="bp-file" type="file" :accept="accept" :multiple="mode === 'pack'" @change="onFileChange" />
          <span class="bp-drop-title">{{ fileLabel }}</span>
          <span class="bp-drop-sub">{{ $t('tools.file.hint', { max: formatBytes(MAX_PACK_BYTES) }) }}</span>
        </label>

        <template v-if="mode === 'detect'">
          <p class="bpc-hint">{{ $t('tools.file.choose') }}</p>
        </template>

        <template v-else-if="mode === 'pack'">
          <div v-if="files.length" class="bpc-rows">
            <div v-for="(name, index) in previewNames" :key="`${name}-${index}`" class="bpc-row">
              <span class="bpc-row-v">{{ name }}</span>
              <span class="bpc-row-k">{{ formatBytes(files[index].size) }}</span>
            </div>
          </div>
          <div class="bpc-field">
            <label class="bpc-label" for="bp-zip-name">{{ $t('tools.file.zipName') }}</label>
            <input id="bp-zip-name" v-model="zipName" class="bpc-input" type="text" :placeholder="$t('tools.file.zipNamePlaceholder')" />
          </div>
          <div class="bpc-actions">
            <button class="bpc-btn bpc-btn-primary" type="button" :disabled="!files.length || busy" @click="pack">
              {{ $t('tools.file.pack') }}
            </button>
          </div>
        </template>

        <template v-else>
          <p class="bpc-hint">{{ $t('tools.file.unpackHint') }}</p>
        </template>

        <div class="bpc-actions">
          <button class="bpc-btn" type="button" :disabled="!hasInput" @click="clearAll">{{ $t('tools.file.clear') }}</button>
        </div>
        <p class="bpc-hint">{{ $t('tools.file.privacy') }}</p>
      </div>
    </section>

    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ mode === 'unpack' ? $t('tools.file.entries') : $t('tools.file.result') }}</span>
        <span>{{ mode }}</span>
      </div>
      <div class="bpc-card-bd">
        <p v-if="error" class="bpc-err">{{ error }}</p>

        <template v-else-if="mode === 'detect'">
          <template v-if="head">
            <div class="bpc-field">
              <span class="bpc-label">{{ $t('tools.file.headBytes') }}</span>
              <pre class="bpc-out bpc-out-short">{{ head }}</pre>
            </div>
            <div class="bpc-rows">
              <div class="bpc-row">
                <span class="bpc-row-k">{{ $t('tools.file.result') }}</span>
                <span class="bpc-row-v is-accent">{{ detected ? detected.name : $t('tools.file.unknown') }}</span>
              </div>
              <div v-if="detected" class="bpc-row">
                <span class="bpc-row-k">MIME</span>
                <span class="bpc-row-v">{{ detected.mime }}</span>
              </div>
              <div v-if="sha256" class="bpc-row">
                <span class="bpc-row-k">{{ $t('tools.file.sha256') }}</span>
                <span class="bpc-row-v">{{ sha256 }}</span>
                <BpcCopy :value="sha256" :label="$t('bitPouch.tools.copy')" />
              </div>
            </div>
          </template>
          <p v-else class="bpc-hint">{{ $t('tools.file.choose') }}</p>
        </template>

        <template v-else-if="mode === 'unpack'">
          <p v-if="!entries.length" class="bpc-hint">{{ $t('tools.file.emptyEntries') }}</p>
          <div v-else class="bpc-rows">
            <div v-for="entry in entries" :key="entry.name" class="bpc-row">
              <span class="bpc-row-v">{{ entry.name }}</span>
              <span class="bpc-row-k">{{ formatBytes(entry.size) }}</span>
              <button class="bpc-btn bpc-btn-sm" type="button" @click="downloadEntry(entry)">{{ $t('tools.file.download') }}</button>
            </div>
          </div>
        </template>

        <template v-else>
          <p v-if="!packed" class="bpc-hint">{{ $t('tools.file.emptyResult') }}</p>
          <template v-else>
            <div class="bpc-rows">
              <div class="bpc-row">
                <span class="bpc-row-k">{{ $t('tools.file.result') }}</span>
                <span class="bpc-row-v is-accent">{{ `${zipName || 'archive'}.zip` }}</span>
              </div>
              <div class="bpc-row">
                <span class="bpc-row-k">{{ $t('tools.file.entries') }}</span>
                <span class="bpc-row-v">{{ $t('tools.file.zipStats', { n: packed.count, size: formatBytes(packed.size) }) }}</span>
              </div>
            </div>
            <div class="bpc-actions">
              <button class="bpc-btn" type="button" @click="downloadPacked">{{ $t('tools.file.download') }}</button>
            </div>
          </template>
        </template>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import BpcChips from '../BpcChips.vue'
import BpcCopy from '../BpcCopy.vue'
import { detectFileType, formatFileSize as formatBytes, planZipNames } from '@/utils/tools/fileTool'
import { runFile as hashFile } from '@/utils/tools/hashTool'
import { saveBlob } from '@/utils/tools/download'

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

const modeOptions = computed(() => [
  { value: 'detect', label: t('tools.file.modeDetect') },
  { value: 'pack', label: t('tools.file.modePack') },
  { value: 'unpack', label: t('tools.file.modeUnpack') },
])

const accept = computed(() => (mode.value === 'unpack' ? '.zip,application/zip' : ''))
const hasInput = computed(() => files.value.length > 0 || Boolean(detected.value) || entries.value.length > 0 || Boolean(packed.value))
const fileLabel = computed(() => {
  if (!files.value.length) return t(mode.value === 'pack' ? 'tools.file.chooseMany' : 'tools.file.choose')
  if (files.value.length === 1) return files.value[0].name
  return t('tools.file.selected', { n: files.value.length })
})
const previewNames = computed(() => planZipNames(files.value.map((file) => file.name)))
const metaText = computed(() => {
  if (mode.value === 'pack') {
    return t('tools.file.totalSize', { size: formatBytes(files.value.reduce((sum, file) => sum + file.size, 0)) })
  }
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
    const total = files.value.reduce((sum, file) => sum + file.size, 0)
    if (total > MAX_PACK_BYTES) {
      error.value = t('tools.file.errTooLarge', { max: formatBytes(MAX_PACK_BYTES) })
      return
    }
    const { zipSync } = await import('fflate')
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
</style>
