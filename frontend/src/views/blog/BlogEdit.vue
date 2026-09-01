<!-- 博客编辑页:新增/编辑共用,含标题、封面上传、正文、状态与可见范围 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('blog.title'), to: '/blog' }, { label: isEdit ? $t('blog.editPost') : $t('blog.newPost') }]" />
    <div class="card">
      <el-form :model="form" label-position="top">
        <el-form-item :label="$t('common.title')">
          <el-input v-model="form.title" :placeholder="$t('blog.titlePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('blog.coverImage')">
          <div class="cover-uploader">
            <el-upload :show-file-list="false" :before-upload="onUpload" accept="image/*">
              <img v-if="form.coverImage" :src="form.coverImage" class="cover-preview" :alt="$t('blog.coverImage')" />
              <div v-else class="cover-uploader-btn">{{ $t('blog.uploadCover') }}</div>
            </el-upload>
            <el-button v-if="form.coverImage" link type="danger" @click="form.coverImage = ''">{{ $t('common.remove') }}</el-button>
          </div>
        </el-form-item>
        <el-form-item :label="$t('blog.contentMarkdown')">
          <el-input v-model="form.content" type="textarea" :rows="14" :placeholder="$t('blog.markdownHint')" />
        </el-form-item>
        <el-form-item :label="$t('blog.category')">
          <div class="category-row">
            <el-cascader
              v-model="form.category"
              :options="categoryTree"
              :props="{ expandTrigger: 'hover', checkStrictly: true, emitPath: false }"
              :placeholder="$t('blog.categoryPlaceholder')"
              clearable
              filterable
              style="flex: 1"
            />
            <el-button @click="openNewCatDialog">+ {{ $t('blog.newCategory') }}</el-button>
          </div>
        </el-form-item>
        <el-form-item :label="$t('blog.status')">
          <el-radio-group v-model="form.status">
            <el-radio :value="0">{{ $t('blog.draft') }}</el-radio>
            <el-radio :value="1">{{ $t('blog.publish') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="$t('blog.visibility')">
          <el-radio-group v-model="form.visibility">
            <el-radio :value="0">{{ $t('blog.onlySelf') }}</el-radio>
            <el-radio :value="3">{{ $t('blog.familyVisible') }}</el-radio>
            <el-radio :value="4">{{ $t('blog.publicVisible') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <div class="form-footer">
          <el-button type="primary" :loading="loading" @click="onSave">{{ $t('common.save') }}</el-button>
        </div>
      </el-form>
    </div>

    <!-- 新建分类对话框 -->
    <el-dialog v-model="showCategoryDialog" :title="$t('blog.newCategory')" width="380px" append-to-body>
      <el-form label-position="top">
        <el-form-item :label="$t('blog.parentCategory')">
          <el-cascader
            v-model="newCatParent"
            :options="categoryTree"
            :props="{ expandTrigger: 'hover', checkStrictly: true, emitPath: false }"
            :placeholder="$t('blog.rootCategory')"
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="$t('blog.categoryNamePlaceholder')">
          <el-input v-model="newCatName" @keyup.enter="addCategory" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCategoryDialog = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :disabled="!newCatName.trim() || savingCategory" :loading="savingCategory" @click="addCategory">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { blogApi, fileApi } from '@/api'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
// 路由带 id 即为编辑模式,否则是新增
const isEdit = computed(() => !!route.params.id)
const loading = ref(false)

const form = reactive({ title: '', content: '', coverImage: '', category: '', status: 1, visibility: 3 })
const categories = ref([])
const legacyCategory = ref(null)
const showCategoryDialog = ref(false)
const newCatName = ref('')
const newCatParent = ref(null)
const savingCategory = ref(false)

// 扁平分类列表 → el-cascader 树形 options(value=path,checkStrictly 可选任意层级)
const categoryTree = computed(() => {
  const byParent = {}
  for (const item of categories.value) {
    const pid = item.parentId || 0
    if (!byParent[pid]) byParent[pid] = []
    byParent[pid].push(item)
  }
  const build = (parentId) => (byParent[parentId] || []).map(item => ({
    value: item.path,
    label: item.name,
    children: item.childCount > 0 ? build(item.id) : undefined,
  }))
  const tree = build(0)
  // 旧分类(不在分类表,如迁移前的自由文本)注入顶级选项,保证编辑时 cascader 能回显
  if (legacyCategory.value && !categories.value.some(c => c.path === legacyCategory.value)) {
    tree.push({ value: legacyCategory.value, label: legacyCategory.value })
  }
  return tree
})

const openNewCatDialog = () => {
  newCatName.value = ''
  newCatParent.value = null
  showCategoryDialog.value = true
}

// 拉取家庭已有分类
const loadCategories = async () => {
  try {
    categories.value = await blogApi.categories() || []
  } catch (e) {}
  // 编辑回填的分类不在分类表中(旧数据):记录为顶级选项,cascader 对不上的值显示空白
  if (form.category && !categories.value.some(c => c.path === form.category)) legacyCategory.value = form.category
}

// 新建分类:加入下拉列表并选中
const addCategory = async () => {
  const name = newCatName.value.trim()
  if (!name || savingCategory.value) return
  savingCategory.value = true
  try {
    await blogApi.addCategory(name, newCatParent.value)
    categories.value = await blogApi.categories() || []
    // 选中新建的分类:拼路径
    const parent = categories.value.find(c => c.id === newCatParent.value)
    form.category = parent ? parent.path + '/' + name : name
    showCategoryDialog.value = false
  } catch (e) {
    ElMessage.error(e.message || 'Failed')
  } finally {
    savingCategory.value = false
  }
}

// 封面图片上传:回填 URL 到表单,返回 false 阻止 el-upload 默认提交
const onUpload = async (file) => {
  const data = await fileApi.upload(file)
  form.coverImage = data.url
  return false
}

// 保存:编辑走 update,新增走 create,成功后回列表页
// 分类归一:清空选择(undefined/null)转空串,后端把空分类落"未分类"
const onSave = async () => {
  if (!form.title) return ElMessage.warning(t('blog.inputTitle'))
  loading.value = true
  try {
    const payload = { ...form, category: form.category || '' }
    if (isEdit.value) await blogApi.update(route.params.id, payload)
    else await blogApi.create(payload)
    ElMessage.success(t('common.saveSuccess'))
    router.push('/blog')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  // 编辑模式下拉取详情回填表单(后端 status/visibility 为字符串枚举,表单用数字码,同 DiaryEdit 转换)
  if (isEdit.value) {
    const b = await blogApi.detail(route.params.id)
    Object.assign(form, {
      title: b.title,
      content: b.content,
      coverImage: b.coverImage,
      category: b.category || '',
      status: b.status === 'PUBLISHED' ? 1 : 0,
      visibility: b.visibility === 'PRIVATE' ? 0 : b.visibility === 'PUBLIC' ? 4 : 3,
    })
  }
  loadCategories()
})
</script>

<style scoped>
.category-row { display: flex; gap: 8px; width: 100%; }
.form-footer { display: flex; justify-content: flex-end; margin-top: 4px; }
.cover-uploader { display: flex; align-items: flex-end; gap: 12px; }
.cover-preview { width: 180px; height: 100px; object-fit: cover; border-radius: 8px; border: 1px solid var(--color-border); cursor: pointer; }
.cover-uploader-btn { width: 180px; height: 100px; border-radius: 8px; border: 1px dashed var(--color-border); display: flex; align-items: center; justify-content: center; color: var(--color-text-secondary); font-size: 12px; cursor: pointer; background: var(--color-bg); }

@media (max-width: 768px) {
  .category-row { flex-direction: column; }
  .form-footer { flex-direction: column; gap: 8px; }
  .form-footer .el-button { width: 100%; }
}
</style>
