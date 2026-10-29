<!-- 家谱面板(已并入「家庭成员」页):关系图谱 + 成员编辑 + 从家庭成员导入。
     点击卡片选中两人 → 计算中华传统称谓;图谱不显示"第 N 代",关系线按夫妻/亲子/补充关系区分。 -->
<template>
  <div class="tree-panel">
    <div class="tree-head">
      <div class="tree-head-text">
        <div class="tree-tip">{{ $t('tree.tip') }}</div>
        <div v-if="graphStats.orphanLinks" class="tree-warn">
          {{ $t('tree.orphanWarning', { n: graphStats.orphanLinks }) }}
        </div>
      </div>
      <div class="tree-head-actions">
        <el-button v-if="unlinkedAccounts.length" plain @click="openImport">{{ $t('tree.importFromMembers') }}</el-button>
        <el-button type="primary" @click="openEditor()">{{ $t('tree.add') }}</el-button>
      </div>
    </div>

    <div class="tree-legend">
      <span class="legend-item"><i class="legend-line spouse"></i>{{ $t('tree.legendSpouse') }}</span>
      <span class="legend-item"><i class="legend-line parent"></i>{{ $t('tree.legendParent') }}</span>
      <span class="legend-tip">{{ $t('tree.legendHint') }}</span>
    </div>

    <div v-loading="loading" class="tree-graph-wrap">
      <FamilyTreeGraph :members="members" v-model:selected="selectedIds" @edit="openEditor" />
    </div>

    <div class="kin-card" :class="{ active: selectedIds.length === 2 }">
      <template v-if="selectedIds.length === 0">
        <span class="kin-empty">{{ $t('tree.selectTwoHint') }}</span>
      </template>
      <template v-else-if="selectedIds.length === 1">
        <span class="kin-empty">{{ $t('tree.selectedHint', { name: nameOf(selectedIds[0]) }) }}</span>
      </template>
      <template v-else>
        <div class="kin-row">
          <div class="kin-person">{{ nameOf(selectedIds[0]) }}</div>
          <div class="kin-arrow">→</div>
          <div class="kin-person">{{ nameOf(selectedIds[1]) }}</div>
          <el-tag size="small" :type="relationTagType(mutual.aToB.type)" effect="plain">
            {{ relationTypeLabel(mutual.aToB.type) }}
          </el-tag>
        </div>
        <div class="kin-terms">
          <div class="kin-term">
            <div class="kin-term-main">
              <span class="kin-term-label">{{ $t('tree.calls', { a: nameOf(selectedIds[0]), b: nameOf(selectedIds[1]) }) }}</span>
              <strong>{{ termLabel(mutual.aToB.code) }}</strong>
            </div>
            <span v-if="pathLabel(mutual.aToB)" class="kin-path">{{ pathLabel(mutual.aToB) }}</span>
          </div>
          <div class="kin-term">
            <div class="kin-term-main">
              <span class="kin-term-label">{{ $t('tree.calls', { a: nameOf(selectedIds[1]), b: nameOf(selectedIds[0]) }) }}</span>
              <strong>{{ termLabel(mutual.bToA.code) }}</strong>
            </div>
            <span v-if="pathLabel(mutual.bToA)" class="kin-path">{{ pathLabel(mutual.bToA) }}</span>
          </div>
        </div>
        <div v-if="mutual.aToB.code === 'none'" class="kin-note">{{ $t('tree.noRelationHint') }}</div>
        <el-button class="kin-clear" link size="small" @click="selectedIds = []">{{ $t('tree.clearSelection') }}</el-button>
      </template>
    </div>

    <!-- 成员编辑:新增/编辑共用;配偶/父亲/母亲从本家庭已有成员中选择 -->
    <el-dialog v-model="dialog" append-to-body :title="form.id ? $t('tree.edit') : $t('tree.add')" width="480px">
      <el-form :model="form" label-width="90px">
        <el-form-item :label="$t('tree.name')">
          <el-input v-model="form.name" :placeholder="$t('tree.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('tree.gender')">
          <el-radio-group v-model="form.gender">
            <el-radio :value="0">{{ $t('tree.unknown') }}</el-radio>
            <el-radio :value="1">{{ $t('tree.male') }}</el-radio>
            <el-radio :value="2">{{ $t('tree.female') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="$t('tree.birthDate')">
          <el-date-picker v-model="form.birthDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item :label="$t('tree.photo')">
          <div class="upload-row">
            <el-upload :show-file-list="false" :http-request="uploadPhoto" accept="image/*">
              <el-button>{{ $t('tree.uploadPhoto') }}</el-button>
            </el-upload>
            <img v-if="form.photo" :src="form.photo" class="photo-preview" :alt="$t('tree.photo')" />
            <el-button v-if="form.photo" link type="danger" @click="form.photo = ''">{{ $t('common.remove') }}</el-button>
          </div>
        </el-form-item>
        <el-form-item :label="$t('tree.account')">
          <el-select
            v-model="form.userId"
            clearable
            filterable
            style="width: 100%"
            :placeholder="$t('tree.accountPlaceholder')"
            @change="onAccountChange"
          >
            <el-option v-for="u in linkableAccounts" :key="u.id" :label="u.nickname || u.username" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('tree.spouse')">
          <el-select v-model="form.spouseId" clearable filterable style="width: 100%">
            <el-option v-for="m in otherMembers" :key="m.id" :label="m.name" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('tree.father')">
          <el-select v-model="form.fatherId" clearable filterable style="width: 100%">
            <el-option v-for="m in otherMembers" :key="m.id" :label="m.name" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('tree.mother')">
          <el-select v-model="form.motherId" clearable filterable style="width: 100%">
            <el-option v-for="m in otherMembers" :key="m.id" :label="m.name" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('tree.note')">
          <el-input v-model="form.note" type="textarea" :rows="2" :placeholder="$t('tree.notePlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-if="form.id" type="danger" plain @click="remove(form.id)">{{ $t('common.delete') }}</el-button>
        <el-button @click="dialog = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 从家庭成员导入:把未进入家谱的账号一键建成家谱人物,避免两份名单各录一遍 -->
    <el-dialog v-model="importVisible" append-to-body :title="$t('tree.importTitle')" width="440px">
      <div class="import-hint">{{ $t('tree.importHint') }}</div>
      <el-checkbox v-if="unlinkedAccounts.length" v-model="importAll" class="import-all">{{ $t('tree.importAll') }}</el-checkbox>
      <div class="import-list">
        <el-checkbox-group v-model="importIds">
          <el-checkbox v-for="u in unlinkedAccounts" :key="u.id" :value="u.id" class="import-item">
            <el-avatar :size="26" :src="u.avatar || undefined">{{ (u.nickname || u.username || 'U').charAt(0) }}</el-avatar>
            <span>{{ u.nickname || u.username }}</span>
          </el-checkbox>
        </el-checkbox-group>
        <el-empty v-if="!unlinkedAccounts.length" :description="$t('tree.allLinked')" :image-size="70" />
      </div>
      <template #footer>
        <el-button @click="importVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="importing" :disabled="!importIds.length" @click="doImport">{{
          $t('common.confirm')
        }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { treeApi, fileApi, memberApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computeMutualKinship } from '@/utils/kinship'
import { kinshipLabel } from '@/utils/kinshipTerms'
import { buildFamilyGraph } from '@/utils/familyGraph'
import FamilyTreeGraph from '@/components/family/FamilyTreeGraph.vue'

const { t, locale } = useI18n()
const userStore = useUserStore()

const loading = ref(false)
const members = ref([])
const accounts = ref([])
const selectedIds = ref([])
const dialog = ref(false)
const saving = ref(false)
const importVisible = ref(false)
const importing = ref(false)
const importIds = ref([])

const graphStats = computed(() => buildFamilyGraph(members.value).stats)

const form = reactive({
  id: null,
  name: '',
  gender: 0,
  birthDate: null,
  photo: '',
  userId: null,
  spouseId: null,
  fatherId: null,
  motherId: null,
  note: '',
})

const otherMembers = computed(() => members.value.filter((m) => m.id !== form.id))

// 一个账号只对应一位家谱人物;编辑自己时保留当前账号
const linkedUserIds = computed(() => new Set(members.value.map((m) => m.userId).filter(Boolean)))
const linkableAccounts = computed(() => {
  const taken = new Set(linkedUserIds.value)
  if (form.userId) taken.delete(form.userId)
  return accounts.value.filter((u) => !taken.has(u.id))
})
const unlinkedAccounts = computed(() => accounts.value.filter((u) => !linkedUserIds.value.has(u.id)))

const mutual = computed(() => {
  if (selectedIds.value.length !== 2) return { aToB: { code: null, type: 'none' }, bToA: { code: null, type: 'none' } }
  return computeMutualKinship(members.value, selectedIds.value[0], selectedIds.value[1])
})

const nameOf = (id) => members.value.find((m) => m.id === id)?.name || ''
const termLabel = (code) => kinshipLabel(code, locale.value === 'en' ? 'en' : 'zh-CN')
// 关系推导路径(如「妈妈的爸爸的弟弟」):称谓怎么来的看得见;一步直达的关系不重复显示
const pathLabel = (r) => {
  const chain = r?.chain || []
  if (chain.length < 2) return ''
  const parts = chain.map(termLabel)
  return locale.value === 'en' ? parts.join("'s ") : parts.join('的')
}
const relationTypeLabel = (type) =>
  ({ blood: t('tree.relationBlood'), affinity: t('tree.relationAffinity'), spouse: t('tree.relationSpouse') })[type] ||
  t('tree.relationNone')
const relationTagType = (type) => (type === 'blood' ? 'success' : type === 'affinity' || type === 'spouse' ? 'warning' : 'info')

const importAll = computed({
  get: () => unlinkedAccounts.value.length > 0 && importIds.value.length === unlinkedAccounts.value.length,
  set: (checked) => {
    importIds.value = checked ? unlinkedAccounts.value.map((u) => u.id) : []
  },
})

const resetForm = () => {
  Object.assign(form, {
    id: null,
    name: '',
    gender: 0,
    birthDate: null,
    photo: '',
    userId: null,
    spouseId: null,
    fatherId: null,
    motherId: null,
    note: '',
  })
}

const openEditor = (m) => {
  if (!m) {
    resetForm()
  } else {
    Object.assign(form, {
      id: m.id,
      name: m.name || '',
      gender: m.gender ?? 0,
      birthDate: m.birthDate || null,
      photo: m.photo || '',
      userId: m.userId || null,
      spouseId: m.spouseId || null,
      fatherId: m.fatherId || null,
      motherId: m.motherId || null,
      note: m.note || '',
    })
  }
  dialog.value = true
}

// 选家庭成员账号时,空着的姓名/照片/性别/生日自动带出,减少重复录入
const onAccountChange = (userId) => {
  if (!userId) return
  const u = accounts.value.find((item) => item.id === userId)
  if (!u) return
  if (!form.name.trim()) form.name = u.nickname || u.username || ''
  if (!form.photo) form.photo = u.avatar || ''
  if (!form.gender && u.gender) form.gender = u.gender
  if (!form.birthDate && u.birthday) form.birthDate = String(u.birthday).slice(0, 10)
}

const load = async () => {
  if (!userStore.isLoggedIn) return
  loading.value = true
  try {
    members.value = await treeApi.list()
  } finally {
    loading.value = false
  }
}

const loadAccounts = async () => {
  if (!userStore.isLoggedIn) return
  try {
    accounts.value = await memberApi.list()
  } catch {
    accounts.value = []
  }
}

const save = async () => {
  if (!form.name.trim()) return ElMessage.warning(t('tree.nameRequired'))
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      gender: form.gender,
      birthDate: form.birthDate || null,
      photo: form.photo || null,
      userId: form.userId || null,
      spouseId: form.spouseId || null,
      fatherId: form.fatherId || null,
      motherId: form.motherId || null,
      note: form.note || null,
    }
    if (form.id) await treeApi.update(form.id, payload)
    else await treeApi.create(payload)
    ElMessage.success(t('tree.saved'))
    dialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}

const remove = async (id) => {
  try {
    await ElMessageBox.confirm(t('tree.deleteConfirm'), t('common.tip'), { type: 'warning', closeOnClickModal: true })
  } catch {
    return
  }
  await treeApi.remove(id)
  ElMessage.success(t('tree.deleted'))
  dialog.value = false
  selectedIds.value = selectedIds.value.filter((x) => x !== id)
  await load()
}

const uploadPhoto = async (options) => {
  try {
    const data = await fileApi.upload(options.file)
    form.photo = data.url
  } catch {
    ElMessage.error(t('tree.uploadFailed'))
  }
}

const openImport = () => {
  importIds.value = []
  importVisible.value = true
}

const doImport = async () => {
  if (!importIds.value.length) return
  importing.value = true
  try {
    const data = await treeApi.importMembers(importIds.value)
    ElMessage.success(t('tree.imported', { n: data?.count ?? importIds.value.length }))
    importVisible.value = false
    await load()
  } finally {
    importing.value = false
  }
}

onMounted(() => {
  load()
  loadAccounts()
})
</script>

<style scoped>
.tree-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.tree-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.tree-tip {
  color: var(--color-text-2);
  font-size: 12px;
  line-height: 1.6;
}
.tree-warn {
  margin-top: 4px;
  color: #d98b2b;
  font-size: 12px;
}
.tree-head-actions {
  flex: none;
  display: flex;
  gap: 8px;
}
.tree-legend {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  color: var(--color-text-2);
  font-size: 12px;
}
.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.legend-line {
  display: inline-block;
  width: 26px;
  height: 0;
  border-top: 2px solid var(--color-primary);
}
.legend-line.spouse {
  border-top: 3px solid #e85d75;
}
.legend-tip {
  opacity: 0.75;
}
.tree-graph-wrap {
  min-height: 220px;
}
.kin-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 16px;
  border: 1px dashed var(--color-border);
  border-radius: 12px;
  background: var(--color-card);
  color: var(--color-text-2);
  font-size: 13px;
}
.kin-card.active {
  border-style: solid;
  border-color: color-mix(in srgb, var(--color-primary) 45%, var(--color-border));
}
.kin-empty {
  color: var(--color-text-2);
}
.kin-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.kin-person {
  color: var(--color-text);
  font-weight: 600;
}
.kin-arrow {
  color: var(--color-text-2);
}
.kin-terms {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 22px;
}
.kin-term {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.kin-term-main {
  display: flex;
  align-items: center;
  gap: 8px;
}
.kin-term-label {
  color: var(--color-text-2);
  font-size: 12px;
}
.kin-term strong {
  color: var(--color-primary);
  font-size: 18px;
  letter-spacing: 1px;
}
.kin-path {
  color: var(--color-text-2);
  font-size: 12px;
  opacity: 0.8;
}
.kin-note {
  color: #d98b2b;
  font-size: 12px;
}
.kin-clear {
  margin-left: auto;
}
.import-hint {
  color: var(--color-text-2);
  font-size: 12px;
  line-height: 1.6;
  margin-bottom: 10px;
}
.import-all {
  margin-bottom: 6px;
  font-weight: 600;
}
.import-list {
  max-height: 320px;
  overflow-y: auto;
}
.import-item {
  display: flex;
  width: 100%;
  margin: 0 0 6px;
}
.import-item :deep(.el-checkbox__label) {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.upload-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.photo-preview {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid var(--color-border);
}
@media (max-width: 768px) {
  .tree-head {
    flex-direction: column;
  }
  .tree-head-actions {
    width: 100%;
    justify-content: flex-end;
  }
  .kin-card {
    flex-wrap: wrap;
  }
  .kin-clear {
    margin-left: 0;
  }
  .upload-row {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
