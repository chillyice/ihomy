<template>
  <div class="page">
    <el-button text @click="$router.back()">← 返回</el-button>
    <div class="card">
      <h2>{{ isEdit ? '编辑博客' : '写博客' }}</h2>
      <el-form :model="form" label-position="top">
        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="博客标题" />
        </el-form-item>
        <el-form-item label="封面图片">
          <el-input v-model="form.coverImage" placeholder="封面图URL">
            <template #append>
              <el-upload :show-file-list="false" :before-upload="onUpload">
                <el-button>上传</el-button>
              </el-upload>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="正文（Markdown）">
          <el-input v-model="form.content" type="textarea" :rows="14" placeholder="支持 Markdown" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="0">草稿</el-radio>
            <el-radio :value="1">发布</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-button type="primary" :loading="loading" @click="onSave">保存</el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { blogApi, fileApi } from '@/api'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const loading = ref(false)

const form = reactive({ title: '', content: '', coverImage: '', status: 1, visibility: 0 })

const onUpload = async (file) => {
  const data = await fileApi.upload(file)
  form.coverImage = data.url
  return false
}

const onSave = async () => {
  if (!form.title) return ElMessage.warning('请输入标题')
  loading.value = true
  try {
    if (isEdit.value) await blogApi.update(route.params.id, form)
    else await blogApi.create(form)
    ElMessage.success('保存成功')
    router.push('/blog')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  if (isEdit.value) {
    const b = await blogApi.detail(route.params.id)
    Object.assign(form, { title: b.title, content: b.content, coverImage: b.coverImage, status: b.status, visibility: b.visibility })
  }
})
</script>
