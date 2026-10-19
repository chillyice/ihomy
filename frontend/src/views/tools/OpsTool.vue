<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('tools.title') }, { label: $t('tools.ops.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <el-input v-model="keyword" size="small" clearable :placeholder="$t('tools.ops.searchPlaceholder')" style="width: 240px">
          <template #prefix
            ><el-icon><Search /></el-icon
          ></template>
        </el-input>
        <el-select v-model="domainId" size="small" style="width: 140px">
          <el-option value="all" :label="$t('tools.ops.allDomains')" />
          <el-option v-for="domain in OPS_DOMAINS" :key="domain.id" :value="domain.id" :label="label(domain.title)" />
        </el-select>
      </div>
      <div class="tb-right">
        <span class="ops-count">{{ $t('tools.ops.count', { n: total }) }}</span>
      </div>
    </PageToolbar>

    <div class="card ops-card">
      <template v-if="visibleDomains.length">
        <section v-for="domain in visibleDomains" :key="domain.id" class="ops-domain">
          <div class="section-label">{{ label(domain.title) }}</div>
          <div v-for="command in domain.commands" :key="command.id" class="ops-row">
            <div class="ops-row-head">
              <span class="ops-title">
                {{ label(command.title) }}
                <span v-if="command.dangerous" class="ops-badge">{{ $t('tools.ops.dangerous') }}</span>
              </span>
              <el-button size="small" text @click="copyCommand(command)">{{ $t('tools.ops.copy') }}</el-button>
            </div>
            <pre class="ops-cmd">{{ command.command }}</pre>
            <p class="ops-note">{{ label(command.note) }}</p>
          </div>
        </section>
      </template>
      <div v-else class="ops-empty">{{ $t('tools.ops.noMatch') }}</div>

      <p class="ops-privacy">{{ $t('tools.ops.privacy') }}</p>
    </div>

    <div class="card ops-card">
      <div class="section-label">{{ $t('tools.ops.reference') }}</div>
      <section v-for="reference in OPS_REFERENCES" :key="reference.id" class="ops-ref">
        <div class="ops-ref-title">{{ label(reference.title) }}</div>
        <div v-if="reference.columns" class="ops-table">
          <div class="ops-tr ops-th">
            <span v-for="(column, index) in reference.columns" :key="index">{{ label(column) }}</span>
          </div>
          <div v-for="(row, index) in reference.rows" :key="index" class="ops-tr">
            <span v-for="(cell, cellIndex) in row" :key="cellIndex">{{ cell }}</span>
          </div>
        </div>
        <div v-else class="ops-snippets">
          <div v-for="(row, index) in reference.rows" :key="index" class="ops-snippet">
            <pre class="ops-cmd">{{ row[0] }}</pre>
            <p class="ops-note">{{ row[1] }}</p>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import { OPS_DOMAINS, OPS_REFERENCES } from '@/tools/opsCommands'

const { t, locale } = useI18n()

const keyword = ref('')
const domainId = ref('all')

const label = (value) => (locale.value?.startsWith('en') ? value.en : value.zh)

const matches = (command, needle) =>
  !needle ||
  [command.command, command.title.zh, command.title.en, command.note.zh, command.note.en].some((text) =>
    text.toLowerCase().includes(needle),
  )

const visibleDomains = computed(() => {
  const needle = keyword.value.trim().toLowerCase()
  return OPS_DOMAINS.filter((domain) => domainId.value === 'all' || domain.id === domainId.value)
    .map((domain) => ({ ...domain, commands: domain.commands.filter((command) => matches(command, needle)) }))
    .filter((domain) => domain.commands.length)
})

const total = computed(() => visibleDomains.value.reduce((sum, domain) => sum + domain.commands.length, 0))

const copyCommand = async (command) => {
  if (command.dangerous) {
    try {
      await ElMessageBox.confirm(t('tools.ops.dangerousConfirm'), t('tools.ops.dangerousTitle'), {
        type: 'warning',
        confirmButtonText: t('tools.ops.dangerousOk'),
        cancelButtonText: t('tools.ops.cancel'),
      })
    } catch {
      return
    }
  }
  try {
    await navigator.clipboard.writeText(command.command)
    ElMessage.success(t('tools.ops.copied'))
  } catch {
    ElMessage.error(t('tools.ops.copyFailed'))
  }
}
</script>

<style scoped>
.ops-count {
  color: var(--color-text-secondary);
  font-size: 12px;
}
.ops-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.ops-domain + .ops-domain {
  margin-top: 10px;
  padding-top: 14px;
  border-top: 1px solid var(--color-border);
}
.ops-row {
  padding: 10px 0;
  border-bottom: 1px solid var(--color-border);
}
.ops-row:last-child {
  border-bottom: 0;
}
.ops-row-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.ops-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
}
.ops-badge {
  padding: 1px 7px;
  border-radius: 999px;
  background: rgba(220, 80, 80, 0.14);
  color: var(--color-danger, #c0392b);
  font-size: 11px;
  font-weight: 500;
}
.ops-cmd {
  margin: 6px 0 0;
  padding: 9px 12px;
  border-radius: 8px;
  background: var(--color-bg-soft);
  font-family: 'Cascadia Code', Consolas, monospace;
  font-size: 12.5px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.ops-note {
  margin: 5px 0 0;
  color: var(--color-text-secondary);
  font-size: 12px;
}
.ops-empty {
  padding: 24px 0;
  color: var(--color-text-secondary);
  font-size: 13px;
  text-align: center;
}
.ops-privacy {
  margin: 6px 0 0;
  color: var(--color-text-secondary);
  font-size: 12px;
}
.ops-ref + .ops-ref {
  margin-top: 18px;
}
.ops-ref-title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
}
.ops-table {
  border: 1px solid var(--color-border);
  border-radius: 8px;
  overflow: hidden;
  font-size: 12.5px;
}
.ops-tr {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 2fr);
  gap: 10px;
  padding: 6px 12px;
}
.ops-tr:nth-child(odd) {
  background: var(--color-bg-soft);
}
.ops-th {
  color: var(--color-text-secondary);
  font-size: 12px;
  font-weight: 600;
}
.ops-snippets {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
