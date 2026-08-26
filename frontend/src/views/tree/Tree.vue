<!-- 家谱页:按世代纵向排布的家谱树,夫妻并排,孩子挂在父辈下方;
     家庭成员可新增/编辑/删除成员,删除时后端自动清理引用 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('tree.title') }]" />

    <div class="tree-head">
      <el-button v-if="userStore.isLoggedIn" type="primary" @click="openEditor()">{{ $t('tree.add') }}</el-button>
      <span class="tree-tip">{{ $t('tree.tip') }}</span>
    </div>

    <div v-loading="loading" class="tree-body">
      <el-empty v-if="!loading && !roots.length" :description="$t('tree.emptyHint')" />
      <div v-else class="generation-list">
        <div v-for="gen in generations" :key="gen" class="generation-row">
          <div class="gen-label">{{ $t('tree.gen', { n: gen + 1 }) }}</div>
          <div class="gen-cards">
            <div v-for="unit in unitsByGen(gen)" :key="unitKey(unit)" class="family-unit">
              <div class="couple">
                <div
                  v-for="m in unit"
                  :key="m.id"
                  class="member-card"
                  :class="{ ghost: !m }"
                  @click="userStore.isLoggedIn && openEditor(m)"
                >
                  <div class="member-photo">
                    <img v-if="m.photo" :src="m.photo" :alt="m.name" />
                    <span v-else class="photo-fallback">{{ genderIcon(m.gender) }}</span>
                  </div>
                  <div class="member-name">{{ m.name }}</div>
                  <div v-if="m.birthDate" class="member-birth">{{ m.birthDate }}</div>
                </div>
                <span v-if="unit[0] && unit[1]" class="couple-mark">💞</span>
              </div>
              <div v-if="childrenOf(unit).length" class="children">
                <div class="children-line"></div>
                <div class="children-cards">
                  <div v-for="c in childrenOf(unit)" :key="c.id" class="child-node">
                    <div class="member-card mini" @click="userStore.isLoggedIn && openEditor(c)">
                      <div class="member-photo">
                        <img v-if="c.photo" :src="c.photo" :alt="c.name" />
                        <span v-else class="photo-fallback">{{ genderIcon(c.gender) }}</span>
                      </div>
                      <div class="member-name">{{ c.name }}</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 成员编辑对话框:新增/编辑共用;配偶/父亲/母亲从本家庭已有成员中选择 -->
    <el-dialog v-model="dialog" :title="form.id ? $t('tree.edit') : $t('tree.add')" width="480px">
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
  </div>
</template>

<script setup>
// 家谱树:数据来自 /tree/list(全部成员+关系姓名);世代按 generation 分组,
// 每组把人按"夫妻单元"聚合(配偶并排),孩子通过 fatherId/motherId 归属到父辈单元
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { treeApi, fileApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb.vue'

const { t } = useI18n()
const userStore = useUserStore()

const loading = ref(false)
const members = ref([])
const dialog = ref(false)
const saving = ref(false)

// 按世代排序分组(升序:祖先在前)
const generations = computed(() => {
  const gs = [...new Set(members.value.map((m) => m.generation ?? 0))].sort((a, b) => a - b)
  return gs
})

const roots = computed(() => members.value.filter((m) => !m.fatherId && !m.motherId))

// 某世代的"夫妻单元":把同代中互为配偶的人合并到一个单元(至多两人)
const unitsByGen = (gen) => {
  const inGen = members.value.filter((m) => (m.generation ?? 0) === gen)
  const units = []
  const used = new Set()
  for (const m of inGen) {
    if (used.has(m.id)) continue
    const spouse = inGen.find((s) => s.id === m.spouseId)
    const unit = [m]
    if (spouse) {
      unit.push(spouse)
      used.add(spouse.id)
    }
    used.add(m.id)
    units.push(unit)
  }
  return units
}

// 某夫妻单元的孩子:有 fatherId 或 motherId 指向单元内任一成员,且不在更早世代(防重复计数按自身世代呈现)
const childrenOf = (unit) => {
  const ids = new Set(unit.map((m) => m.id))
  return members.value.filter(
    (m) => (m.fatherId && ids.has(m.fatherId)) || (m.motherId && ids.has(m.motherId)),
  )
}

const unitKey = (unit) => unit.map((m) => m.id).join('-')
const genderIcon = (g) => (g === 1 ? '👨' : g === 2 ? '👩' : '🧑')

// 编辑表单:排除本人后作为 父亲/母亲/配偶 候选项
const form = reactive({ id: null, name: '', gender: 0, birthDate: null, photo: '', spouseId: null, fatherId: null, motherId: null, note: '' })
const otherMembers = computed(() => members.value.filter((m) => m.id !== form.id))

const openEditor = (m) => {
  if (!m) {
    Object.assign(form, { id: null, name: '', gender: 0, birthDate: null, photo: '', spouseId: null, fatherId: null, motherId: null, note: '' })
  } else {
    Object.assign(form, {
      id: m.id, name: m.name || '', gender: m.gender ?? 0,
      birthDate: m.birthDate || null, photo: m.photo || '',
      spouseId: m.spouseId || null, fatherId: m.fatherId || null,
      motherId: m.motherId || null, note: m.note || '',
    })
  }
  dialog.value = true
}

const load = async () => {
  loading.value = true
  try {
    members.value = await treeApi.list()
  } finally {
    loading.value = false
  }
}

const save = async () => {
  if (!form.name.trim()) return ElMessage.warning(t('tree.nameRequired'))
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(), gender: form.gender, birthDate: form.birthDate || null,
      photo: form.photo || null, spouseId: form.spouseId || null,
      fatherId: form.fatherId || null, motherId: form.motherId || null, note: form.note || null,
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
    await ElMessageBox.confirm(t('tree.deleteConfirm'), t('common.tip'), { type: 'warning' })
  } catch {
    return
  }
  await treeApi.remove(id)
  ElMessage.success(t('tree.deleted'))
  dialog.value = false
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

onMounted(load)
</script>

<style scoped>
.tree-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.tree-tip { color: var(--color-text-2); font-size: 12px; }
.generation-list { display: flex; flex-direction: column; gap: 26px; }
.generation-row { display: flex; align-items: flex-start; gap: 14px; }
.gen-label {
  flex-shrink: 0;
  width: 64px;
  padding-top: 12px;
  text-align: center;
  border-radius: 8px;
  background: linear-gradient(120deg, var(--color-primary), var(--color-accent));
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}
.gen-cards { display: flex; flex-wrap: wrap; gap: 26px; align-items: flex-start; }
.family-unit { display: flex; flex-direction: column; align-items: center; }
.couple { display: flex; align-items: center; gap: 8px; padding-bottom: 8px; }
.couple-mark { color: #e85d75; font-size: 16px; }
.member-card {
  width: 96px;
  padding: 10px 8px;
  border-radius: 12px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  text-align: center;
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}
.member-card:hover { transform: translateY(-3px); box-shadow: 0 6px 16px rgba(0, 0, 0, 0.12); }
.member-card.mini { width: 78px; }
.ghost { opacity: 0.35; cursor: default; }
.member-photo {
  width: 56px;
  height: 56px;
  margin: 0 auto 6px;
  border-radius: 50%;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #eef2f7;
}
.member-card.mini .member-photo { width: 44px; height: 44px; }
.member-photo img { width: 100%; height: 100%; object-fit: cover; }
.photo-fallback { font-size: 26px; }
.member-card.mini .photo-fallback { font-size: 20px; }
.member-name { font-size: 13px; font-weight: 600; color: var(--color-text); }
.member-birth { font-size: 11px; color: var(--color-text-2); margin-top: 2px; }
.children { display: flex; flex-direction: column; align-items: center; }
.children-line { width: 2px; height: 12px; background: var(--color-border-strong, #c5cfd9); }
.children-cards { display: flex; flex-wrap: wrap; gap: 10px; justify-content: center; }
.child-node { display: flex; flex-direction: column; align-items: center; }
.upload-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.photo-preview { width: 64px; height: 64px; border-radius: 50%; object-fit: cover; border: 1px solid var(--color-border); }

@media (max-width: 768px) {
  .tree-container { overflow-x: auto; padding: 8px 0; }
  .member-card { min-width: 120px; }
  .upload-row { flex-direction: column; align-items: flex-start; }
}
</style>