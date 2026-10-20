<!-- Markdown 阅读器面板:选本地 .md/.txt 文件后按 bit_pouch 的排版渲染。
     解析与消毒走 utils/markdown.js 的 renderMarkdown(全站唯一富文本入口),文件不上传。 -->
<template>
  <div class="bpc-stack">
    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.markdown.title') }}</span>
        <span>{{ fileName || '—' }}</span>
      </div>
      <div class="bpc-card-bd">
        <div class="bpc-actions">
          <label class="bpc-btn bp-open">
            <input class="bp-file" type="file" accept=".md,.markdown,.txt" @change="onFileChange" />
            {{ $t('tools.markdown.openFile') }}
          </label>
          <button class="bpc-btn" type="button" :disabled="!rawMarkdown" @click="clear">
            {{ $t('tools.markdown.clear') }}
          </button>
          <BpcCopy :value="htmlContent" :label="$t('tools.markdown.copyHtml')" />
        </div>
        <p v-if="status" class="bpc-meta">
          <span :class="statusKind === 'error' ? 'bpc-err' : 'bpc-flash'">{{ status }}</span>
        </p>
        <p v-else class="bpc-hint">{{ $t('tools.markdown.hint') }}</p>
        <p class="bpc-hint">{{ $t('tools.image.privacy') }}</p>
      </div>
    </section>

    <section v-if="htmlContent" class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ fileName }}</span>
        <span>{{ stats }}</span>
      </div>
      <div class="bpc-card-bd">
        <article class="bp-md" v-html="htmlContent"></article>
      </div>
    </section>

    <section v-else class="bpc-card">
      <div class="bpc-card-bd">
        <p class="bpc-hint">{{ $t('tools.markdown.emptyState') }}</p>
        <p class="bpc-hint">{{ $t('tools.markdown.hint') }}</p>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import BpcCopy from '../BpcCopy.vue'
import { renderMarkdown } from '@/utils/markdown'
import { readFile } from '@/utils/tools/markdownTool'

const { t } = useI18n()

const fileName = ref('')
const rawMarkdown = ref('')
const status = ref('')
const statusKind = ref('ok')

const htmlContent = computed(() => (rawMarkdown.value ? renderMarkdown(rawMarkdown.value) : ''))
const stats = computed(() => {
  if (!rawMarkdown.value) return ''
  const lines = rawMarkdown.value.split(/\r\n?|\n/).length
  return `${lines} lines · ${rawMarkdown.value.length} chars`
})

const onFileChange = async (event) => {
  const file = event.target.files?.[0]
  if (!file) return
  status.value = ''
  try {
    rawMarkdown.value = await readFile(file)
    fileName.value = file.name
    statusKind.value = 'ok'
    status.value = t('tools.markdown.fileLoaded')
  } catch (error) {
    rawMarkdown.value = ''
    fileName.value = ''
    statusKind.value = 'error'
    status.value = t(error?.code === 'fileTooLarge' ? 'tools.markdown.fileTooLarge' : 'tools.markdown.loadFailed')
  } finally {
    // 清空 input,允许重复选择同一文件
    event.target.value = ''
  }
}

const clear = () => {
  rawMarkdown.value = ''
  fileName.value = ''
  status.value = t('tools.markdown.cleared')
  statusKind.value = 'ok'
}
</script>

<style scoped>
.bp-open {
  cursor: pointer;
}
.bp-file {
  display: none;
}
.bp-md {
  max-height: 68vh;
  overflow: auto;
  color: var(--text);
  font-size: 14px;
  line-height: 1.85;
}
.bp-md :deep(h1),
.bp-md :deep(h2),
.bp-md :deep(h3),
.bp-md :deep(h4) {
  margin: 1.1em 0 0.5em;
  color: var(--text);
  font-weight: 700;
  line-height: 1.4;
}
.bp-md :deep(h1) {
  font-size: 1.7em;
  padding-bottom: 0.35em;
  border-bottom: 1px solid var(--line);
}
.bp-md :deep(h2) {
  font-size: 1.35em;
  padding-bottom: 0.25em;
  border-bottom: 1px solid var(--line-soft);
}
.bp-md :deep(h3) {
  font-size: 1.15em;
  color: var(--accent);
}
.bp-md :deep(p) {
  margin: 0.6em 0;
}
.bp-md :deep(ul),
.bp-md :deep(ol) {
  margin: 0.6em 0;
  padding-left: 1.8em;
}
.bp-md :deep(li) {
  margin: 0.25em 0;
}
.bp-md :deep(a) {
  color: var(--accent);
  text-decoration: none;
  border-bottom: 1px solid transparent;
}
.bp-md :deep(a:hover) {
  border-bottom-color: var(--accent);
}
.bp-md :deep(blockquote) {
  margin: 0.8em 0;
  padding: 2px 0 2px 12px;
  border-left: 2px solid var(--accent);
  color: var(--muted);
}
.bp-md :deep(code) {
  padding: 1px 5px;
  border: 1px solid var(--term-line);
  border-radius: 2px;
  background: var(--term-bg);
  color: var(--term-text);
  font-family: var(--mono);
  font-size: 0.9em;
}
.bp-md :deep(pre) {
  margin: 0.8em 0;
  padding: 12px;
  overflow-x: auto;
  border: 1px solid var(--term-line);
  border-radius: 2px;
  background: var(--term-bg);
}
.bp-md :deep(pre code) {
  padding: 0;
  border: 0;
  background: transparent;
  white-space: pre-wrap;
}
.bp-md :deep(table) {
  width: 100%;
  margin: 0.8em 0;
  border-collapse: collapse;
  font-size: 12.5px;
}
.bp-md :deep(th),
.bp-md :deep(td) {
  padding: 6px 9px;
  border: 1px solid var(--line);
  text-align: left;
}
.bp-md :deep(th) {
  background: var(--panel);
  color: var(--muted);
}
.bp-md :deep(hr) {
  margin: 1.2em 0;
  border: 0;
  border-top: 1px solid var(--line);
}
.bp-md :deep(img) {
  max-width: 100%;
  height: auto;
}
</style>
