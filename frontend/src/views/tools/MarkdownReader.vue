<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('tools.title') }, { label: $t('tools.markdown.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <el-input v-model="fileName" size="small" :placeholder="$t('tools.markdown.fileName')" readonly style="width: 240px">
          <template #prefix>
            <el-icon><Document /></el-icon>
          </template>
        </el-input>
        <input ref="fileInput" type="file" accept=".md,.markdown,.txt" style="display: none" @change="handleFileSelect" />
        <el-button size="small" @click="triggerFileSelect">
          {{ $t('tools.markdown.openFile') }}
        </el-button>
        <el-button size="small" @click="clearContent">
          {{ $t('tools.markdown.clear') }}
        </el-button>
      </div>
      <div class="tb-right">
        <el-button size="small" :disabled="!htmlContent" @click="copyHtml">
          {{ $t('tools.markdown.copyHtml') }}
        </el-button>
      </div>
    </PageToolbar>

    <div v-if="htmlContent" class="markdown-container">
      <div class="markdown-pane">
        <div class="markdown-content" v-html="htmlContent"></div>
      </div>
    </div>

    <div v-else class="card markdown-empty">
      <div class="markdown-empty-content">
        <el-icon :size="48" color="var(--color-text-secondary)">
          <Document />
        </el-icon>
        <p class="markdown-empty-text">{{ $t('tools.markdown.emptyState') }}</p>
        <p class="markdown-empty-hint">{{ $t('tools.markdown.hint') }}</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import { Document } from '@element-plus/icons-vue'
import { renderMarkdown } from '@/utils/markdown'
import { readFile } from '@/utils/tools/markdownTool'

const { t } = useI18n()

const fileInput = ref(null)
const fileName = ref('')
const rawMarkdown = ref('')
const htmlContent = computed(() => (rawMarkdown.value ? renderMarkdown(rawMarkdown.value) : ''))

const triggerFileSelect = () => {
  fileInput.value?.click()
}

const handleFileSelect = async (event) => {
  const file = event.target.files?.[0]
  if (!file) return

  fileName.value = file.name
  try {
    rawMarkdown.value = await readFile(file)
    ElMessage.success(t('tools.markdown.fileLoaded'))
  } catch (error) {
    rawMarkdown.value = ''
    ElMessage.error(t(`tools.markdown.${error.code === 'fileTooLarge' ? 'fileTooLarge' : 'loadFailed'}`))
  } finally {
    // 清空 input,允许重复选择同一文件
    event.target.value = ''
  }
}

const clearContent = () => {
  rawMarkdown.value = ''
  fileName.value = ''
  ElMessage.info(t('tools.markdown.cleared'))
}

const copyHtml = async () => {
  if (!htmlContent.value) return
  try {
    await navigator.clipboard.writeText(htmlContent.value)
    ElMessage.success(t('tools.markdown.copied'))
  } catch {
    ElMessage.error(t('tools.markdown.copyFailed'))
  }
}
</script>

<style scoped>
.markdown-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-height: calc(100vh - 200px);
}

.markdown-pane {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: var(--color-bg-soft);
  border-radius: 12px;
  border: 1px solid var(--color-border);
}

.markdown-content {
  font-size: 15px;
  line-height: 1.8;
  color: var(--color-text-primary);
}

.markdown-content :deep(h1) {
  font-size: 2em;
  margin: 0.67em 0;
  padding-bottom: 0.5em;
  border-bottom: 1px solid var(--color-border);
}

.markdown-content :deep(h2) {
  font-size: 1.5em;
  margin: 0.83em 0;
  padding-bottom: 0.3em;
  border-bottom: 1px solid var(--color-border);
}

.markdown-content :deep(h3) {
  font-size: 1.17em;
  margin: 1em 0;
}

.markdown-content :deep(p) {
  margin: 0.5em 0;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin: 0.5em 0;
  padding-left: 2em;
}

.markdown-content :deep(li) {
  margin: 0.3em 0;
}

.markdown-content :deep(blockquote) {
  margin: 0.5em 0;
  padding-left: 1em;
  border-left: 3px solid var(--color-accent);
  color: var(--color-text-secondary);
}

.markdown-content :deep(code.inline-code) {
  display: inline-block;
  padding: 2px 6px;
  margin: 0 2px;
  font-family: 'Cascadia Code', Consolas, monospace;
  font-size: 0.9em;
  background: var(--color-bg-soft);
  border-radius: 4px;
  color: var(--color-accent);
}

.markdown-content :deep(pre) {
  margin: 0.5em 0;
  padding: 12px;
  overflow-x: auto;
  background: var(--color-bg-soft);
  border-radius: 8px;
  border: 1px solid var(--color-border);
}

.markdown-content :deep(pre code) {
  display: block;
  font-family: 'Cascadia Code', Consolas, monospace;
  font-size: 0.9em;
  line-height: 1.6;
  color: var(--color-text-primary);
  white-space: pre-wrap;
}

.markdown-content :deep(a) {
  color: var(--color-accent);
  text-decoration: none;
}

.markdown-content :deep(a:hover) {
  text-decoration: underline;
}

.markdown-content :deep(hr) {
  margin: 1em 0;
  border: none;
  border-top: 1px solid var(--color-border);
}

.markdown-empty {
  padding: 60px 0;
  text-align: center;
}

.markdown-empty-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.markdown-empty-text {
  margin: 0;
  font-size: 16px;
  color: var(--color-text-primary);
}

.markdown-empty-hint {
  margin: 0;
  font-size: 14px;
  color: var(--color-text-secondary);
}

@media (max-width: 768px) {
  .markdown-pane {
    padding: 12px;
  }

  .markdown-content {
    font-size: 14px;
  }
}
</style>
