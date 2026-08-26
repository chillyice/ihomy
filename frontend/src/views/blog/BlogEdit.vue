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
          <el-input v-model="form.coverImage" :placeholder="$t('blog.coverUrlPlaceholder')">
            <template #append>
              <el-upload :show-file-list="false" :before-upload="onUpload">
                <el-button>{{ $t('common.upload') }}</el-button>
              </el-upload>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item :label="$t('blog.contentMarkdown')">
          <el-input v-model="form.content" type="textarea" :rows="14" :placeholder="$t('blog.markdownHint')" />
        </el-form-item>
        <el-form-item :label="$t('blog.category')">
          <div class="category-row">
            <el-select
              v-model="form.category"
              filterable
              clearable
              :placeholder="$t('blog.categoryPlaceholder')"
              style="flex: 1"
            >
              <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
            </el-select>
            <el-button @click="showCategoryDialog = true">+ {{ $t('blog.newCategory') }}</el-button>
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
          </el-radio-group>
        </el-form-item>
        <div class="form-footer">
          <el-button type="primary" :loading="loading" @click="onSave">{{ $t('common.save') }}</el-button>
        </div>
      </el-form>
    </div>

    <!-- 新建分类对话框 -->
    <el-dialog v-model="showCategoryDialog" :title="$t('blog.newCategory')" width="360px" append-to-body>
      <el-input v-model="newCategoryName" :placeholder="$t('blog.categoryPlaceholder')" @keyup.enter="addCategory" />
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
const showCategoryDialog = ref(false)
const newCategoryName = ref('')
const savingCategory = ref(false)

// 拉取家庭已有分类,供 el-select 下拉
const loadCategories = async () => {
  try {
    categories.value = await blogApi.categories() || []
  } catch (e) {
    // 忽略
  }
}

// 新建分类:加入下拉列表并选中
const addCategory = async () => {
  const name = newCategoryName.value.trim()
  if (!name || savingCategory.value) return
  savingCategory.value = true
  try {
    await blogApi.addCategory(name)
    categories.value = await blogApi.categories() || []
    form.category = name
    newCategoryName.value = ''
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
const onSave = async () => {
  if (!form.title) return ElMessage.warning(t('blog.inputTitle'))
  loading.value = true
  try {
    if (isEdit.value) await blogApi.update(route.params.id, form)
    else await blogApi.create(form)
    ElMessage.success(t('common.saveSuccess'))
    router.push('/blog')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  // 编辑模式下拉取详情回填表单
  if (isEdit.value) {
    const b = await blogApi.detail(route.params.id)
    Object.assign(form, { title: b.title, content: b.content, coverImage: b.coverImage, category: b.category || '', status: b.status, visibility: b.visibility })
  }
  loadCategories()
})
</script>

<style scoped>
.category-row { display: flex; gap: 8px; width: 100%; }
.form-footer { display: flex; justify-content: flex-end; margin-top: 4px; }

@media (max-width: 768px) {
  .category-row { flex-direction: column; }
  .form-footer { flex-direction: column; gap: 8px; }
  .form-footer .el-button { width: 100%; }
}
</style>
