<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('library.title'), to: '/library' }, { label: isEdit ? $t('library.editBook') : $t('library.upload') }]" />
    <div class="card">
      <el-form :model="form" label-position="top">
        <el-form-item :label="$t('library.bookTitle')">
          <el-input v-model="form.title" :placeholder="$t('library.titlePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('library.author')">
          <el-input v-model="form.author" :placeholder="$t('library.authorPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('library.bookFile')">
          <div class="upload-row">
            <el-input v-model="form.fileUrl" :placeholder="$t('library.fileUrlPlaceholder')" readonly>
              <template #append>
                <el-upload :show-file-list="false" :before-upload="onUploadFile" accept=".epub,.pdf,.txt,.mobi">
                  <el-button :loading="uploading">{{ $t('common.upload') }}</el-button>
                </el-upload>
              </template>
            </el-input>
          </div>
          <div v-if="form.fileFormat" class="format-info">{{ form.fileFormat }} <span v-if="form.fileSize">· {{ formatSize(form.fileSize) }}</span></div>
        </el-form-item>
        <el-form-item :label="$t('library.coverImage')">
          <el-input v-model="form.coverUrl" :placeholder="$t('library.coverUrlPlaceholder')">
            <template #append>
              <el-upload :show-file-list="false" :before-upload="onUploadCover">
                <el-button>{{ $t('common.upload') }}</el-button>
              </el-upload>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item :label="$t('library.description')">
          <el-input v-model="form.description" type="textarea" :rows="4" :placeholder="$t('library.descPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('library.categoriesLabel')">
          <div class="category-row">
            <el-cascader
              v-model="form.categoryIds"
              :options="categoryCascader"
              :props="{ expandTrigger: 'hover', checkStrictly: true, emitPath: false, multiple: true }"
              :placeholder="$t('library.selectCategories')"
              clearable
              filterable
              collapse-tags
              collapse-tags-tooltip
              style="flex: 1"
            />
            <el-button @click="showCategoryDialog = true">+ {{ $t('library.newCategory') }}</el-button>
          </div>
        </el-form-item>
        <el-form-item :label="$t('library.tags')">
          <el-input v-model="form.tags" :placeholder="$t('library.tagsPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('library.visibility')">
          <el-radio-group v-model="form.visibility">
            <el-radio :value="0">{{ $t('library.onlySelf') }}</el-radio>
            <el-radio :value="3">{{ $t('library.familyVisible') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <div class="form-footer">
          <el-button type="primary" :loading="loading" @click="onSave">{{ $t('common.save') }}</el-button>
        </div>
      </el-form>
    </div>

    <el-dialog v-model="showCategoryDialog" :title="$t('library.newCategory')" width="380px" append-to-body>
      <el-form label-position="top">
        <el-form-item :label="$t('library.parentCategory')">
          <el-cascader
            v-model="newCategoryParentId"
            :options="categoryCascader"
            :props="{ expandTrigger: 'hover', checkStrictly: true, emitPath: false }"
            :placeholder="$t('library.rootCategory')"
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="$t('library.categoryPlaceholder')">
          <el-input v-model="newCategoryName" @keyup.enter="addCategory" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCategoryDialog = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :disabled="!newCategoryName.trim() || savingCategory" :loading="savingCategory" @click="addCategory">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { libraryApi, fileApi } from '@/api'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const loading = ref(false)
const uploading = ref(false)

const form = reactive({ title: '', author: '', description: '', coverUrl: '', fileUrl: '', fileFormat: '', fileSize: null, categoryIds: [], tags: '', status: 1, visibility: 3 })
const categories = ref([])
const showCategoryDialog = ref(false)
const newCategoryName = ref('')
const newCategoryParentId = ref(null)
const savingCategory = ref(false)

const categoryCascader = computed(() => {
  const byParent = {}
  for (const c of categories.value) {
    const pid = c.parentId || 0
    if (!byParent[pid]) byParent[pid] = []
    byParent[pid].push(c)
  }
  const build = (pid) => (byParent[pid] || []).map(c => ({
    value: c.id,
    label: c.name,
    children: build(c.id).length ? build(c.id) : undefined,
  }))
  return build(0)
})

const formatSize = (bytes) => {
  if (!bytes) return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

const loadCategories = async () => {
  try { categories.value = await libraryApi.categories() || [] } catch (e) {}
}

const addCategory = async () => {
  const name = newCategoryName.value.trim()
  if (!name || savingCategory.value) return
  savingCategory.value = true
  try {
    await libraryApi.addCategory(name, newCategoryParentId.value)
    await loadCategories()
    ElMessage.success(t('common.saveSuccess'))
    newCategoryName.value = ''
    newCategoryParentId.value = null
    showCategoryDialog.value = false
  } catch (e) {
    ElMessage.error(e.message || 'Failed')
  } finally {
    savingCategory.value = false
  }
}

const onUploadFile = async (file) => {
  uploading.value = true
  try {
    const data = await libraryApi.upload(file)
    form.fileUrl = data.url
    form.fileFormat = data.fileFormat
    form.fileSize = data.fileSize
    if (!form.title) {
      const name = file.name.replace(/\.[^.]+$/, '')
      form.title = name
    }
  } catch (e) {
    ElMessage.error(e.message || t('library.uploadFailed'))
  } finally {
    uploading.value = false
  }
  return false
}

const onUploadCover = async (file) => {
  try {
    const data = await fileApi.upload(file)
    form.coverUrl = data.url
  } catch (e) {
    ElMessage.error(e.message || 'Failed')
  }
  return false
}

const onSave = async () => {
  if (!form.title) return ElMessage.warning(t('library.inputTitle'))
  if (!form.fileUrl) return ElMessage.warning(t('library.inputFile'))
  loading.value = true
  try {
    if (isEdit.value) await libraryApi.update(route.params.id, form)
    else await libraryApi.create(form)
    ElMessage.success(t('common.saveSuccess'))
    router.push('/library')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  if (isEdit.value) {
    const b = await libraryApi.detail(route.params.id)
    Object.assign(form, { title: b.title, author: b.author, description: b.description, coverUrl: b.coverUrl, fileUrl: b.fileUrl, fileFormat: b.fileFormat, fileSize: b.fileSize, categoryIds: b.categoryIds || [], tags: b.tags || '', status: b.status, visibility: b.visibility })
  }
  loadCategories()
})
</script>

<style scoped>
.category-row { display: flex; gap: 8px; width: 100%; }
.upload-row { width: 100%; }
.format-info { font-size: 12px; color: var(--color-text-secondary); margin-top: 4px; }
.cat-hint { font-size: 12px; color: var(--color-text-secondary); opacity: 0.6; margin-top: 4px; }
.form-footer { display: flex; justify-content: flex-end; margin-top: 4px; }

@media (max-width: 768px) {
  .category-row { flex-direction: column; }
  .form-footer { flex-direction: column; gap: 8px; }
  .form-footer .el-button { width: 100%; }
}
</style>
