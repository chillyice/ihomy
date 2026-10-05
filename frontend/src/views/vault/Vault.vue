<!-- 家庭保险箱:家庭共用的账号密码条目(列表只显示掩码,查看/复制密码走单独接口并留痕) -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('vault.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <el-input
          v-model="keyword"
          size="small"
          clearable
          class="vault-search"
          :placeholder="$t('vault.searchPlaceholder')"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="category" size="small" class="vault-cat">
          <el-option :label="$t('vault.allCategories')" value="" />
          <el-option v-for="c in CATEGORIES" :key="c" :label="catText(c)" :value="c" />
        </el-select>
      </div>
      <div class="tb-right">
        <span v-if="filtered.length" class="vault-count">{{ $t('vault.count', { n: filtered.length }) }}</span>
        <el-button v-if="canManage" type="primary" @click="openEditor()">{{ $t('vault.add') }}</el-button>
      </div>
    </PageToolbar>

    <div v-loading="loading">
      <div v-if="filtered.length" class="vault-grid">
        <div v-for="item in filtered" :key="item.id" class="vault-card card">
          <div class="vc-head">
            <span class="vc-name" :title="item.name">{{ item.name }}</span>
            <div class="vc-tags">
              <el-tag size="small" effect="plain">{{ catText(item.category) }}</el-tag>
              <el-tag v-if="item.visibility === 'PRIVATE'" size="small" type="warning" effect="plain">
                {{ $t('vault.privateTag') }}
              </el-tag>
            </div>
          </div>

          <div class="vc-row">
            <span class="vc-label">{{ $t('vault.username') }}</span>
            <span v-if="item.username" class="vc-value" :title="item.username">{{ item.username }}</span>
            <span v-else class="vc-empty">{{ $t('vault.noUsername') }}</span>
            <span v-a11y-click
              v-if="item.username"
              class="vc-icon"
              :title="$t('vault.copyUsername')"
              @click="copyUsername(item)"
            >
              <el-icon><DocumentCopy /></el-icon>
            </span>
          </div>

          <div class="vc-row">
            <span class="vc-label">{{ $t('vault.password') }}</span>
            <span v-if="revealed[item.id] !== undefined" class="vc-value vc-plain">{{ revealed[item.id] || '—' }}</span>
            <span v-else-if="item.passwordMasked" class="vc-value vc-mask">{{ item.passwordMasked }}</span>
            <span v-else class="vc-empty">{{ $t('vault.noPassword') }}</span>
            <template v-if="item.passwordMasked">
              <span v-a11y-click
                class="vc-icon"
                :title="revealed[item.id] !== undefined ? $t('vault.hide') : $t('vault.reveal')"
                @click="toggleReveal(item)"
              >
                <el-icon><View v-if="revealed[item.id] === undefined" /><Hide v-else /></el-icon>
              </span>
              <span v-a11y-click class="vc-icon" :title="$t('vault.copyPassword')" @click="copyPassword(item)">
                <el-icon><DocumentCopy /></el-icon>
              </span>
            </template>
          </div>

          <div v-if="item.url" class="vc-row">
            <span class="vc-label">{{ $t('vault.url') }}</span>
            <a class="vc-link" :href="item.url" target="_blank" rel="noopener noreferrer" :title="item.url">{{ item.url }}</a>
          </div>

          <div v-if="item.note" class="vc-note">{{ item.note }}</div>

          <div v-if="tagList(item).length" class="vc-chips">
            <el-tag v-for="tg in tagList(item)" :key="tg" size="small" effect="plain" type="info">{{ tg }}</el-tag>
          </div>

          <div class="vc-foot">
            <span>{{ $t('vault.owner', { name: item.ownerName }) }}</span>
            <span v-if="item.updatedAt">{{ item.updatedAt.slice(0, 10) }}</span>
          </div>

          <div v-if="canManage" class="vc-actions">
            <el-tooltip :content="$t('common.edit')" placement="top" :show-after="300">
              <el-button size="small" text @click="openEditor(item)"><el-icon><Edit /></el-icon></el-button>
            </el-tooltip>
            <el-tooltip :content="$t('common.delete')" placement="top" :show-after="300">
              <el-button size="small" text type="danger" @click="onDelete(item)"><el-icon><Delete /></el-icon></el-button>
            </el-tooltip>
          </div>
        </div>
      </div>

      <div v-else-if="loadError" style="padding: 20px 0; text-align: center; color: var(--color-text-secondary)">
        {{ $t('common.loadFailed') }} <el-button text size="small" @click="load">{{ $t('common.retry') }}</el-button>
      </div>
      <el-empty v-else-if="!loading && !list.length" :description="$t('vault.emptyTitle')">
        <div class="vault-empty-hint">{{ $t('vault.emptyHint') }}</div>
      </el-empty>
      <el-empty v-else-if="!loading" :description="$t('vault.noMatch')" />
    </div>

    <!-- 条目编辑:密码留空表示不改;生成器挂在密码输入框旁 -->
    <el-dialog
      v-model="editor.visible"
      append-to-body
      :title="editor.form.id ? $t('vault.edit') : $t('vault.add')"
      width="480px"
    >
      <el-form :model="editor.form" label-position="top">
        <el-form-item :label="$t('vault.name')">
          <el-input v-model="editor.form.name" :placeholder="$t('vault.namePlaceholder')" maxlength="100" />
        </el-form-item>
        <el-form-item :label="$t('vault.category')">
          <el-select v-model="editor.form.category" style="width: 100%">
            <el-option v-for="c in CATEGORIES" :key="c" :label="catText(c)" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('vault.visibility')">
          <el-radio-group v-model="editor.form.visibility">
            <el-radio value="FAMILY">{{ $t('vault.visFamily') }}</el-radio>
            <el-radio value="PRIVATE">{{ $t('vault.visPrivate') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="$t('vault.username')">
          <el-input v-model="editor.form.username" :placeholder="$t('vault.usernamePlaceholder')" maxlength="200" />
        </el-form-item>
        <el-form-item :label="$t('vault.password')">
          <div class="vault-pw-row">
            <el-input
              v-model="editor.form.password"
              type="text"
              autocomplete="new-password"
              :placeholder="editor.form.id ? $t('vault.passwordKeep') : $t('vault.passwordPlaceholder')"
              maxlength="128"
            />
            <el-popover v-model:visible="generator.visible" placement="bottom-end" :width="300" trigger="click">
              <template #reference>
                <el-button>{{ $t('vault.generate') }}</el-button>
              </template>
              <div class="vault-gen">
                <div class="vault-gen-preview" :title="generator.preview">{{ generator.preview || '—' }}</div>
                <div class="vault-gen-row">
                  <span>{{ $t('vault.genLength') }}</span>
                  <el-slider v-model="generator.opts.length" :min="8" :max="64" size="small" @input="rollPassword" />
                  <span class="vault-gen-len">{{ generator.opts.length }}</span>
                </div>
                <div class="vault-gen-checks">
                  <el-checkbox v-model="generator.opts.upper" @change="rollPassword">{{ $t('vault.genUpper') }}</el-checkbox>
                  <el-checkbox v-model="generator.opts.lower" @change="rollPassword">{{ $t('vault.genLower') }}</el-checkbox>
                  <el-checkbox v-model="generator.opts.digit" @change="rollPassword">{{ $t('vault.genDigit') }}</el-checkbox>
                  <el-checkbox v-model="generator.opts.symbol" @change="rollPassword">{{ $t('vault.genSymbol') }}</el-checkbox>
                  <el-checkbox v-model="generator.opts.noAmbiguous" @change="rollPassword">{{ $t('vault.genNoAmbiguous') }}</el-checkbox>
                </div>
                <div class="vault-gen-foot">
                  <el-button size="small" @click="rollPassword">{{ $t('vault.generate') }}</el-button>
                  <el-button size="small" type="primary" :disabled="!generator.preview" @click="useGenerated">{{ $t('vault.genUse') }}</el-button>
                </div>
              </div>
            </el-popover>
          </div>
          <div v-if="editor.form.password" class="vault-strength">
            <div class="vault-strength-bar">
              <span
                v-for="i in 4"
                :key="i"
                class="vault-strength-seg"
                :class="{ on: i <= strength.score }"
                :style="{ '--seg-color': STRENGTH_COLORS[strength.score] }"
              ></span>
            </div>
            <span class="vault-strength-text" :style="{ color: STRENGTH_COLORS[strength.score] }">
              {{ $t('vault.strength') }}·{{ $t(`vault.strength${strength.labelKey[0].toUpperCase()}${strength.labelKey.slice(1)}`) }}
            </span>
          </div>
          <div v-if="editor.form.password && strength.hints.length" class="vault-strength-hint">
            {{ strengthHintText }}
          </div>
        </el-form-item>
        <el-form-item :label="$t('vault.url')">
          <el-input v-model="editor.form.url" :placeholder="$t('vault.urlPlaceholder')" maxlength="500" />
        </el-form-item>
        <el-form-item :label="$t('vault.tags')">
          <el-select
            v-model="editor.tags"
            multiple
            filterable
            allow-create
            default-first-option
            :placeholder="$t('vault.tagsPlaceholder')"
            style="width: 100%"
          >
            <el-option v-for="tg in tagSuggest" :key="tg" :label="tg" :value="tg" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('vault.note')">
          <el-input v-model="editor.form.note" type="textarea" :rows="3" :placeholder="$t('vault.notePlaceholder')" maxlength="1000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editor.visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
// 保险箱页:条目卡片墙 + 搜索/分类筛选;密码默认掩码,揭示/复制都走 /vault/{id}/password 留痕,
// 复制后 10 秒自动清剪贴板,揭示 30 秒自动隐藏。
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Search, View, Hide, DocumentCopy, Edit, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { vaultApi } from '@/api'
import { dictText } from '@/utils/dict'
import { generatePassword, passwordStrength } from '@/utils/password'
import { useUserStore } from '@/stores/user'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'

const { t } = useI18n()
const userStore = useUserStore()

const CATEGORIES = ['SITE', 'APP', 'BANK', 'SOCIAL', 'DEVICE', 'WIFI', 'OTHER']
/** 标签建议项的 i18n 键后缀(选中即按当前语言落成标签文本) */
const TAG_SUGGEST_KEYS = ['family', 'common', 'important', 'member', 'work']
const tagSuggest = computed(() => TAG_SUGGEST_KEYS.map((k) => t('vault.tagSuggest.' + k)))
const STRENGTH_COLORS = ['#c0453c', '#d9772f', '#c9a227', '#6b9b6b', '#4a7f5f']
/** 剪贴板自动清空延时(毫秒) */
const CLIPBOARD_CLEAR_MS = 10000
/** 揭示后的自动隐藏延时(毫秒) */
const REVEAL_HIDE_MS = 30000

const loading = ref(false)
const loadError = ref(false)
const saving = ref(false)
const list = ref([])
const keyword = ref('')
const category = ref('')
const revealed = reactive({})
const editor = reactive({ visible: false, form: {}, tags: [] })
const generator = reactive({
  visible: false,
  preview: '',
  opts: { length: 16, upper: true, lower: true, digit: true, symbol: true, noAmbiguous: false },
})

const canManage = computed(() => userStore.isOwner || userStore.hasPerm('vault:manage'))
const catText = (c) => dictText(t, 'vault_category', c)
const tagList = (item) => (item.tags ? item.tags.split(',').filter(Boolean) : [])

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return list.value.filter((i) => {
    if (category.value && i.category !== category.value) return false
    if (!kw) return true
    return [i.name, i.username, i.tags, i.note, i.url]
      .filter(Boolean)
      .some((v) => String(v).toLowerCase().includes(kw))
  })
})

const strength = computed(() => passwordStrength(editor.form.password))
const strengthHintText = computed(() =>
  strength.value.hints.map((h) => t(`vault.hint${h[0].toUpperCase()}${h.slice(1)}`)).join('；'))

const load = async () => {
  loading.value = true
  loadError.value = false
  try {
    list.value = await vaultApi.list()
  } catch (e) {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

/* ---------- 剪贴板:复制 + 定时清空(只在剪贴板仍是本次内容时清,避免误清用户其他内容) ---------- */
const clearTimers = new Map()

const copyText = async (text) => {
  if (navigator.clipboard && window.isSecureContext) {
    await navigator.clipboard.writeText(text)
    return true
  }
  const ta = document.createElement('textarea')
  ta.value = text
  document.body.appendChild(ta)
  ta.select()
  const ok = document.execCommand('copy')
  document.body.removeChild(ta)
  return ok
}

/** 取密码明文(唯一入口,后端每次返回都落一条操作日志) */
const fetchPlain = async (id) => {
  const res = await vaultApi.password(id)
  return res?.password ?? ''
}

const scheduleClear = (key, value) => {
  clearTimeout(clearTimers.get(key)?.timer)
  clearTimers.set(key, {
    value,
    timer: setTimeout(async () => {
      clearTimers.delete(key)
      if (await clearIfUnchanged(value)) ElMessage.info(t('vault.cleared'))
    }, CLIPBOARD_CLEAR_MS),
  })
}

/** 剪贴板仍是 value 才清空:读不到(权限/非安全上下文)或已被别的内容覆盖都不动 */
const clearIfUnchanged = async (value) => {
  try {
    if (!navigator.clipboard?.readText) return false
    const current = await navigator.clipboard.readText()
    if (current !== value) return false
    await navigator.clipboard.writeText('')
    return true
  } catch (e) {
    return false
  }
}

const copyUsername = async (item) => {
  try {
    await copyText(item.username)
    ElMessage.success(t('vault.copied'))
  } catch (e) {
    ElMessage.error(t('vault.copyFailed'))
  }
}

const copyPassword = async (item) => {
  try {
    const plain = await fetchPlain(item.id)
    if (!plain) return ElMessage.warning(t('vault.noPassword'))
    await copyText(plain)
    scheduleClear(`pw-${item.id}`, plain)
    ElMessage.success(t('vault.clipboardHint', { sec: CLIPBOARD_CLEAR_MS / 1000 }))
  } catch (e) {
    ElMessage.error(t('vault.copyFailed'))
  }
}

/* ---------- 密码揭示:明文只短暂留在页面上,超时自动隐藏 ---------- */
const revealTimers = new Map()

const toggleReveal = async (item) => {
  if (revealed[item.id] !== undefined) return hideReveal(item.id)
  try {
    revealed[item.id] = await fetchPlain(item.id)
    clearTimeout(revealTimers.get(item.id))
    revealTimers.set(item.id, setTimeout(() => hideReveal(item.id), REVEAL_HIDE_MS))
  } catch (e) {
    ElMessage.error(t('vault.revealFailed'))
  }
}

const hideReveal = (id) => {
  clearTimeout(revealTimers.get(id))
  revealTimers.delete(id)
  delete revealed[id]
}

/* ---------- 生成器 ---------- */
const rollPassword = () => {
  const pw = generatePassword(generator.opts)
  generator.preview = pw
  if (!pw) ElMessage.warning(t('vault.genEmpty'))
}

/** 打开面板即先摇一个,否则首屏预览为空、「使用」按钮点了没反应 */
watch(() => generator.visible, (open) => { if (open) rollPassword() })

const useGenerated = () => {
  if (!generator.preview) return
  editor.form.password = generator.preview
  generator.visible = false
}

/* ---------- 增删改 ---------- */
const openEditor = (item) => {
  editor.visible = true
  editor.form = item
    ? {
        id: item.id,
        name: item.name,
        category: item.category,
        username: item.username || '',
        password: '',
        url: item.url || '',
        note: item.note || '',
        visibility: item.visibility,
      }
    : {
        id: null,
        name: '',
        category: 'SITE',
        username: '',
        password: '',
        url: '',
        note: '',
        visibility: 'FAMILY',
      }
  editor.tags = tagList(item || {})
}

const onSave = async () => {
  if (!editor.form.name?.trim()) return ElMessage.warning(t('vault.nameRequired'))
  saving.value = true
  try {
    const { id, ...data } = editor.form
    data.tags = editor.tags.join(',')
    if (id) await vaultApi.update(id, data)
    else await vaultApi.create(data)
    ElMessage.success(t('common.success'))
    editor.visible = false
    await load()
  } catch (e) {
    ElMessage.error(t('vault.saveFailed'))
  } finally {
    saving.value = false
  }
}

const onDelete = async (item) => {
  await ElMessageBox.confirm(
    t('vault.deleteConfirm', { name: item.name }),
    t('common.deleteConfirm'),
    { type: 'warning', closeOnClickModal: true },
  )
  await vaultApi.remove(item.id)
  hideReveal(item.id)
  ElMessage.success(t('common.deleted'))
  await load()
}

onMounted(load)
// 离开页面:清掉全部定时器;剪贴板里还留着的密码立刻尽力清空(读不到就交给 10 秒兜底计时器之前的手动刷新)
onBeforeUnmount(() => {
  revealTimers.forEach((timer) => clearTimeout(timer))
  revealTimers.clear()
  clearTimers.forEach((entry) => {
    clearTimeout(entry.timer)
    clearIfUnchanged(entry.value)
  })
  clearTimers.clear()
})
</script>

<style scoped>
/* 工具栏 */
.vault-search { width: 260px; }
.vault-cat { width: 130px; }
.vault-count { color: #999; font-size: 12px; margin-right: 4px; }

/* 卡片墙 */
.vault-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
}
.vault-card {
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.vc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.vc-name {
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.vc-tags { display: flex; gap: 6px; flex-shrink: 0; }
.vc-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  min-width: 0;
}
.vc-label { color: #999; flex-shrink: 0; width: 40px; }
.vc-value {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}
.vc-plain { font-family: monospace; }
.vc-mask { letter-spacing: 2px; color: #777; }
.vc-empty { color: #bbb; font-size: 12px; }
.vc-link {
  color: var(--el-color-primary);
  text-decoration: none;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}
/* 行内小图标:用 transform 反馈 hover,不引 box-shadow(避免触发毛玻璃重算) */
.vc-icon {
  cursor: pointer;
  color: #999;
  flex-shrink: 0;
  display: inline-flex;
  transition: color .2s, transform .2s;
}
.vc-icon:hover { color: var(--el-color-primary); transform: scale(1.15); }
.vc-note {
  font-size: 13px;
  color: #666;
  white-space: pre-wrap;
  word-break: break-word;
}
.vc-chips { display: flex; flex-wrap: wrap; gap: 6px; }
.vc-foot {
  display: flex;
  justify-content: space-between;
  color: #999;
  font-size: 12px;
  margin-top: auto;
}
.vc-actions { display: flex; gap: 6px; }
.vc-actions :deep(.el-button.is-text) { padding: 5px 6px; }
.vc-actions :deep(.el-button.is-text + .el-button.is-text) { margin-left: 4px; }
.vault-empty-hint { color: #999; font-size: 13px; }

/* 编辑弹窗:密码行 + 生成器 + 强度条 */
.vault-pw-row { display: flex; gap: 8px; width: 100%; }
.vault-strength {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  width: 100%;
}
.vault-strength-bar { display: flex; gap: 4px; flex: 1; }
.vault-strength-seg {
  flex: 1;
  height: 4px;
  border-radius: 2px;
  background: rgba(128, 128, 128, .25);
  transition: background-color .25s;
}
.vault-strength-seg.on { background: var(--seg-color); }
.vault-strength-text { font-size: 12px; flex-shrink: 0; }
.vault-strength-hint { color: #999; font-size: 12px; margin-top: 4px; }

.vault-gen { display: flex; flex-direction: column; gap: 10px; }
.vault-gen-preview {
  font-family: monospace;
  font-size: 13px;
  word-break: break-all;
  padding: 8px 10px;
  border-radius: 8px;
  background: rgba(128, 128, 128, .12);
}
.vault-gen-row { display: flex; align-items: center; gap: 10px; font-size: 13px; }
.vault-gen-row :deep(.el-slider) { flex: 1; }
.vault-gen-len { width: 20px; text-align: right; }
.vault-gen-checks { display: flex; flex-direction: column; gap: 2px; }
.vault-gen-foot { display: flex; justify-content: flex-end; gap: 8px; }

@media (max-width: 768px) {
  .vault-search { width: 100%; }
  .vault-cat { width: 110px; }
  .vault-grid { grid-template-columns: 1fr; }
  .vc-actions .el-button { flex: 1; }
  .vault-gen-checks { flex-direction: row; flex-wrap: wrap; gap: 0 12px; }
}
</style>
