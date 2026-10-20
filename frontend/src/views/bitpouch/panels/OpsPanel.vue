<!-- 运维命令面板:可搜索、可复制的手册;危险命令要点两次才复制。只读静态数据,不连后端。 -->
<template>
  <div class="bpc-stack">
    <section class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ $t('tools.ops.title') }}</span>
        <span>{{ $t('tools.ops.count', { n: total }) }}</span>
      </div>
      <div class="bpc-card-bd">
        <div class="bpc-field">
          <input
            v-model="keyword"
            class="bpc-input"
            type="search"
            autocomplete="off"
            spellcheck="false"
            :placeholder="$t('tools.ops.searchPlaceholder')"
          />
        </div>
        <BpcChips v-model="domainId" :options="domainOptions" />
        <p class="bpc-hint">{{ $t('tools.ops.privacy') }}</p>
      </div>
    </section>

    <section v-for="domain in visible" :key="domain.id" class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ label(domain.title) }}</span>
        <span>{{ $t('tools.ops.count', { n: domain.commands.length }) }}</span>
      </div>
      <div class="bpc-card-bd">
        <div class="bpc-rows">
          <div v-for="command in domain.commands" :key="command.id" class="bpc-row">
            <div class="bpc-row-v">
              <div class="bpc-mono is-accent">{{ command.command }}</div>
              <div class="bpc-hint">{{ label(command.title) }} · {{ label(command.note) }}</div>
            </div>
            <span v-if="command.dangerous" class="bpc-tag is-danger">{{ $t('tools.ops.dangerous') }}</span>
            <button
              class="bpc-btn bpc-btn-sm"
              :class="{ 'bpc-btn-danger': command.dangerous }"
              type="button"
              @click="copyCommand(command, domain.id)"
            >
              {{ stateOf(command, domain.id) }}
            </button>
          </div>
        </div>
      </div>
    </section>

    <p v-if="!visible.length" class="bpc-hint">{{ $t('tools.ops.noMatch') }}</p>

    <section v-for="ref in references" :key="ref.id" class="bpc-card">
      <div class="bpc-card-hd">
        <span>{{ label(ref.title) }}</span>
        <span>{{ $t('tools.ops.reference') }}</span>
      </div>
      <div class="bpc-card-bd">
        <div class="bpc-table-wrap">
          <table class="bpc-table">
            <thead>
              <tr>
                <th v-for="(col, i) in ref.columns" :key="i">{{ label(col) }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, i) in ref.rows" :key="i">
                <td v-for="(cell, j) in row" :key="j">{{ cell }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import BpcChips from '../BpcChips.vue'
import { OPS_DOMAINS, OPS_REFERENCES } from '@/tools/opsCommands'

const { t, locale } = useI18n()

const keyword = ref('')
const domainId = ref('all')
const armedId = ref('')
const copiedId = ref('')
let armTimer = null
let copyTimer = null

const label = (value) => (locale.value?.startsWith('en') ? value.en : value.zh)

const domainOptions = computed(() => [
  { value: 'all', label: t('tools.ops.allDomains') },
  ...OPS_DOMAINS.map((domain) => ({ value: domain.id, label: label(domain.title) })),
])

const matches = (command, needle) =>
  !needle ||
  [command.command, command.title.zh, command.title.en, command.note.zh, command.note.en].some((text) =>
    String(text).toLowerCase().includes(needle),
  )

const visible = computed(() => {
  const needle = keyword.value.trim().toLowerCase()
  return OPS_DOMAINS.filter((domain) => domainId.value === 'all' || domain.id === domainId.value)
    .map((domain) => ({ ...domain, commands: domain.commands.filter((command) => matches(command, needle)) }))
    .filter((domain) => domain.commands.length)
})

const total = computed(() => visible.value.reduce((sum, domain) => sum + domain.commands.length, 0))

const references = computed(() => {
  const needle = keyword.value.trim().toLowerCase()
  if (!needle) return OPS_REFERENCES
  return OPS_REFERENCES.filter((ref) =>
    [ref.title.zh, ref.title.en, ...ref.rows.flat()].some((text) => String(text).toLowerCase().includes(needle)),
  )
})

const stateOf = (command, domain) => {
  const key = `${domain}:${command.id}`
  if (copiedId.value === key) return t('tools.ops.copied')
  if (armedId.value === key) return t('tools.ops.dangerousOk')
  return t('tools.ops.copy')
}

const copyCommand = async (command, domain) => {
  const key = `${domain}:${command.id}`
  if (command.dangerous && armedId.value !== key) {
    armedId.value = key
    clearTimeout(armTimer)
    armTimer = setTimeout(() => {
      if (armedId.value === key) armedId.value = ''
    }, 4000)
    return
  }
  try {
    await navigator.clipboard.writeText(command.command)
    copiedId.value = key
    clearTimeout(copyTimer)
    copyTimer = setTimeout(() => {
      if (copiedId.value === key) copiedId.value = ''
    }, 1500)
  } catch {
    copiedId.value = ''
  }
  armedId.value = ''
}

onBeforeUnmount(() => {
  clearTimeout(armTimer)
  clearTimeout(copyTimer)
})
</script>
