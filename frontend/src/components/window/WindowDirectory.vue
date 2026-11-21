<template>
  <el-dialog
    v-model="open"
    class="window-directory"
    :title="$t('windowHome.directory')"
    :fullscreen="narrow"
    width="min(900px, 92vw)"
    append-to-body
    @closed="reset"
    :close-on-click-modal="!editing"
  >
    <div class="wd-top">
      <el-input
        v-model="query"
        clearable
        :prefix-icon="Search"
        :placeholder="$t('windowHome.query')"
        :aria-label="$t('windowHome.query')"
      />
      <el-button v-if="!editing" :disabled="nav.busy.value" @click="beginEdit">{{ $t('windowHome.edit') }}</el-button>
      <template v-else>
        <el-button @click="editing = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="nav.busy.value" @click="commit">{{ $t('common.save') }}</el-button>
      </template>
    </div>
    <div v-if="!query && (narrow || editing)" class="wd-fav">
      <div class="wd-head">
        <h3>{{ $t('windowHome.favorites') }}</h3>
        <el-button v-if="editing" text @click="draft = [...DEFAULT_WINDOW_FAVORITES]">{{ $t('windowHome.reset') }}</el-button>
      </div>
      <p v-if="!shownFavorites.length">{{ $t('windowHome.noFavorites') }}</p>
      <div v-for="(item, i) in shownFavorites" :key="item.code" class="wd-pin">
        <button class="wd-link" @click="go(item)">
          <el-icon><component :is="icon(item)" /></el-icon>{{ item.label }}
        </button>
        <template v-if="editing">
          <el-button
            text
            :disabled="i === 0"
            :aria-label="$t('windowHome.up', { name: item.label })"
            :icon="ArrowUp"
            @click="shift(i, -1)"
          />
          <el-button
            text
            :disabled="i === shownFavorites.length - 1"
            :aria-label="$t('windowHome.down', { name: item.label })"
            :icon="ArrowDown"
            @click="shift(i, 1)"
          />
          <el-button text :aria-label="$t('windowHome.unpin', { name: item.label })" :icon="Minus" @click="toggle(item.code)" />
        </template>
      </div>
    </div>
    <div v-if="app.loadFailed && !app.modules.length" class="wd-message" role="status">
      {{ $t('home.familyLoadFailed') }} <el-button :loading="app.loading" @click="app.init()">{{ $t('common.retry') }}</el-button>
    </div>
    <div v-if="groups.length" class="wd-groups">
      <section v-for="group in groups" :key="group.category">
        <h3>{{ $t('windowHome.categories.' + group.category) }}</h3>
        <div v-for="item in group.items" :key="item.code" class="wd-row">
          <button class="wd-link" @click="editing ? toggle(item.code) : go(item)">
            <el-icon><component :is="icon(item)" /></el-icon><span>{{ item.label }}</span>
            <el-icon class="wd-end"><component :is="editing ? (draft.includes(item.code) ? Check : Plus) : ArrowRight" /></el-icon>
          </button>
        </div>
      </section>
    </div>
    <div v-else class="wd-message" role="status">
      <p>{{ $t(query ? 'windowHome.noResults' : 'windowHome.noAccess') }}</p>
      <el-button v-if="query" @click="query = ''">{{ $t('windowHome.all') }}</el-button>
    </div>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ArrowDown, ArrowRight, ArrowUp, Check, MagicStick, Minus, Plus, Search } from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/app'
import { iconComp } from '@/utils/navModules'
import { DEFAULT_WINDOW_FAVORITES, windowGroups } from '@/utils/windowDirectory'
const props = defineProps({ nav: { type: Object, required: true }, narrow: Boolean })
const open = defineModel({ type: Boolean })
const app = useAppStore()
const emit = defineEmits(['select'])
const { t } = useI18n()
const editing = ref(false)
const draft = ref([])
const query = ref('')
const groups = computed(() => windowGroups(props.nav.entries.value, query.value))
const shownFavorites = computed(() =>
  editing.value
    ? draft.value.map((code) => props.nav.entries.value.find((item) => item.code === code)).filter(Boolean)
    : props.nav.favorites.value,
)
const icon = (item) => (item.code === 'assistant' ? MagicStick : iconComp(item.icon || item.code))
function beginEdit() {
  draft.value = [...props.nav.codes.value]
  editing.value = true
  query.value = ''
}
function reset() {
  query.value = ''
  editing.value = false
}
function toggle(code) {
  draft.value = draft.value.includes(code) ? draft.value.filter((it) => it !== code) : [...draft.value, code]
}
function shift(index, delta) {
  const from = draft.value.indexOf(shownFavorites.value[index].code)
  const to = draft.value.indexOf(shownFavorites.value[index + delta].code)
  const [code] = draft.value.splice(from, 1)
  draft.value.splice(to, 0, code)
}
async function commit() {
  try {
    if (await props.nav.save(draft.value)) editing.value = false
  } catch {
    ElMessage.error(t('common.saveFailed'))
  }
}
function go(item) {
  if (editing.value) return
  emit('select', item.path)
}
</script>

<style scoped>
.wd-top,
.wd-head,
.wd-pin,
.wd-row,
.wd-link {
  display: flex;
  align-items: center;
  gap: 8px;
}
.wd-top {
  position: sticky;
  top: 0;
  background: var(--color-card);
  padding-bottom: 14px;
  z-index: 1;
}
.wd-top .el-input {
  flex: 1;
  min-width: 0;
}
.wd-top .el-button,
.wd-pin .el-button {
  margin: 0;
}
.wd-head {
  justify-content: space-between;
}
.wd-fav {
  margin-bottom: 20px;
}
.wd-pin {
  border-bottom: 1px solid var(--color-border);
}
.wd-groups {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  align-items: start;
}
.wd-groups section {
  background: var(--color-bg);
  border-radius: 14px;
  padding: 14px 18px;
}
h3 {
  font-size: 15px;
  color: var(--color-brand);
  margin: 0 0 6px;
}
.wd-link {
  flex: 1;
  min-width: 0;
  width: 100%;
  min-height: 44px;
  border: 0;
  background: transparent;
  color: var(--color-text);
  text-align: left;
  font: inherit;
  cursor: pointer;
}
.wd-link:hover,
.wd-link:focus-visible {
  color: var(--color-brand);
  background: var(--color-card-2);
}
.wd-link .wd-end {
  margin-left: auto;
}
.wd-message {
  padding: 16px 0;
  color: var(--color-text-secondary);
}
@media (max-width: 899px) {
  .wd-groups {
    grid-template-columns: 1fr;
  }
}
</style>
<style>
.theme-window .el-overlay-dialog .el-dialog.window-directory.is-fullscreen {
  --el-dialog-margin-top: 0px;
  width: 100% !important;
  max-width: 100% !important;
  height: 100dvh;
  max-height: 100dvh !important;
  margin: 0 !important;
  border-radius: 0;
  padding-top: max(12px, env(safe-area-inset-top));
  padding-bottom: env(safe-area-inset-bottom);
  display: flex;
  flex-direction: column;
}
.theme-window .el-overlay-dialog .el-dialog.window-directory.is-fullscreen > .el-dialog__body {
  min-height: 0;
  max-height: none !important;
  overflow-y: auto;
  flex: 1;
}
</style>
