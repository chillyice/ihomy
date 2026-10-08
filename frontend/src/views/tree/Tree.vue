<!-- 家谱页:按世代纵向排布的家谱树,夫妻并排,孩子挂在父辈下方;
     家庭成员可新增/编辑/删除成员,删除时后端自动清理引用 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('tree.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <span class="tree-tip">{{ $t('tree.tip') }}</span>
      </div>
      <div class="tb-right">
        <el-button v-if="userStore.isLoggedIn" type="primary" @click="openEditor()">{{ $t('tree.add') }}</el-button>
      </div>
    </PageToolbar>

    <div v-loading="loading" class="tree-body">
      <el-empty v-if="!loading && !roots.length" :description="$t('tree.emptyHint')" />
      <div v-else class="generation-list">
        <div v-for="gen in generations" :key="gen" class="generation-row">
          <div class="gen-label">{{ $t('tree.gen', { n: gen + 1 }) }}</div>
          <div class="gen-cards">
            <div v-for="unit in unitsByGen(gen)" :key="unitKey(unit)" class="family-unit">
              <div class="couple">
                <div
                  v-a11y-click
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
                  <div v-if="m.userName" class="member-account" :title="$t('tree.account')">
                    <el-icon><UserFilled /></el-icon>{{ m.userName }}
                  </div>
                </div>
                <span v-if="unit[0] && unit[1]" class="couple-mark">💞</span>
              </div>
              <div v-if="childrenOf(unit).length" class="children">
                <div class="children-line"></div>
                <div class="children-cards">
                  <div v-for="c in childrenOf(unit)" :key="c.id" class="child-node">
                    <div v-a11y-click class="member-card mini" @click="userStore.isLoggedIn && openEditor(c)">
                      <div class="member-photo">
                        <img v-if="c.photo" :src="c.photo" :alt="c.name" />
                        <span v-else class="photo-fallback">{{ genderIcon(c.gender) }}</span>
                      </div>
                      <div class="member-name">{{ c.name }}</div>
                      <div v-if="c.userName" class="member-account" :title="$t('tree.account')">
                        <el-icon><UserFilled /></el-icon>{{ c.userName }}
                      </div>
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
          <el-select v-model="form.userId" clearable filterable style="width: 100%" :placeholder="$t('tree.accountPlaceholder')">
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
  </div>
</template>

<script setup>
// 家谱树:数据来自 /tree/list(全部成员+关系姓名);世代按 generation 分组,
// 每组把人按"夫妻单元"聚合(配偶并排),孩子通过 fatherId/motherId 归属到父辈单元
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { treeApi, fileApi, memberApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UserFilled } from '@element-plus/icons-vue'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'

const { t } = useI18n()
const userStore = useUserStore()

const loading = ref(false)
const members = ref([])
// 家庭成员账号(用于把家谱人物与账号对上)
const accounts = ref([])
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
  return members.value.filter((m) => (m.fatherId && ids.has(m.fatherId)) || (m.motherId && ids.has(m.motherId)))
}

const unitKey = (unit) => unit.map((m) => m.id).join('-')
const genderIcon = (g) => (g === 1 ? '👨' : g === 2 ? '👩' : '🧑')

// 编辑表单:排除本人后作为 父亲/母亲/配偶 候选项
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
// 可关联账号:已被其他家谱人物占用的账号不再重复出现(一个账号对应一位家人)
const linkableAccounts = computed(() => {
  const taken = new Set()
  for (const m of members.value) {
    if (m.userId && m.id !== form.id) taken.add(m.userId)
  }
  return accounts.value.filter((u) => !taken.has(u.id))
})

const openEditor = (m) => {
  if (!m) {
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

const load = async () => {
  loading.value = true
  try {
    members.value = await treeApi.list()
  } finally {
    loading.value = false
  }
}

// 家庭成员账号列表:仅登录可用(游客不请求,避免无谓 401)
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

onMounted(() => {
  load()
  loadAccounts()
})
</script>

<style scoped>
.tree-tip {
  color: var(--color-text-2);
  font-size: 12px;
}
.generation-list {
  display: flex;
  flex-direction: column;
  gap: 26px;
}
.generation-row {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}
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
.gen-cards {
  display: flex;
  flex-wrap: wrap;
  gap: 26px;
  align-items: flex-start;
}
.family-unit {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.couple {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 8px;
}
.couple-mark {
  color: #e85d75;
  font-size: 16px;
}
.member-card {
  width: 96px;
  padding: 10px 8px;
  border-radius: 12px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  text-align: center;
  cursor: pointer;
  transition:
    transform 0.15s,
    box-shadow 0.15s;
}
.member-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.12);
}
.member-card.mini {
  width: 78px;
}
.ghost {
  opacity: 0.35;
  cursor: default;
}
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
.member-card.mini .member-photo {
  width: 44px;
  height: 44px;
}
.member-photo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.photo-fallback {
  font-size: 26px;
}
.member-card.mini .photo-fallback {
  font-size: 20px;
}
.member-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}
.member-birth {
  font-size: 11px;
  color: var(--color-text-2);
  margin-top: 2px;
}
/* 关联账号徽标:小而不抢眼,仅提示此人与某账号对应 */
.member-account {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  max-width: 100%;
  margin-top: 3px;
  font-size: 10px;
  line-height: 1.3;
  color: var(--color-accent, var(--color-brand));
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.member-account :deep(.el-icon) {
  font-size: 11px;
  flex: none;
}
.children {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.children-line {
  width: 2px;
  height: 12px;
  background: var(--color-border-strong, #c5cfd9);
}
.children-cards {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
}
.child-node {
  display: flex;
  flex-direction: column;
  align-items: center;
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
  .tree-container {
    overflow-x: auto;
    padding: 8px 0;
  }
  .member-card {
    min-width: 120px;
  }
  .upload-row {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
