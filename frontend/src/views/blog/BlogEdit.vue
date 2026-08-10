<!-- 博客编辑页:新增/编辑共用,含标题、封面上传、正文、状态与可见范围 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('blog.title'), to: '/blog' }, { label: isEdit ? $t('blog.editPost') : $t('blog.newPost') }]" />
    <div class="card">
      <h2>{{ isEdit ? $t('blog.editPost') : $t('blog.newPost') }}</h2>
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
        <el-button type="primary" :loading="loading" @click="onSave">{{ $t('common.save') }}</el-button>
      </el-form>
    </div>
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

const form = reactive({ title: '', content: '', coverImage: '', status: 1, visibility: 3 })

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
    Object.assign(form, { title: b.title, content: b.content, coverImage: b.coverImage, status: b.status, visibility: b.visibility })
  }
})
</script>
