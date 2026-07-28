<template>
  <div class="page">
    <div class="list-header">
      <h2>生活日志</h2>
      <el-button type="primary" @click="openEditor()">写日志</el-button>
    </div>

    <div v-for="d in list" :key="d.id" class="diary-item card">
      <div class="diary-meta">
        <span>{{ formatDate(d.createdAt) }}</span>
        <span v-if="d.mood" class="tag">{{ d.mood }}</span>
        <span v-if="d.weather" class="tag">{{ d.weather }}</span>
      </div>
      <div class="diary-content">{{ d.content }}</div>
      <div class="diary-actions">
        <el-button text @click="openEditor(d)">编辑</el-button>
        <el-button text type="danger" @click="onDel(d)">删除</el-button>
      </div>
    </div>
    <el-empty v-if="!list.length" description="还没有日志" />

    <el-dialog v-model="editor.visible" :title="editor.form.id ? '编辑日志' : '写日志'" width="500px">
      <el-form :model="editor.form" label-position="top">
        <el-form-item label="内容">
          <el-input v-model="editor.form.content" type="textarea" :rows="6" />
        </el-form-item>
        <el-form-item label="心情">
          <el-input v-model="editor.form.mood" />
        </el-form-item>
        <el-form-item label="天气">
          <el-input v-model="editor.form.weather" />
        </el-form-item>
        <el-form-item label="可见范围">
          <el-radio-group v-model="editor.form.visibility">
            <el-radio :value="0">仅自己</el-radio>
            <el-radio :value="1">家庭可见</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editor.visible = false">取消</el-button>
        <el-button type="primary" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { diaryApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const list = ref([])
const editor = reactive({ visible: false, form: { id: null, content: '', mood: '', weather: '', visibility: 0 } })

const formatDate = (d) => (d ? new Date(d).toLocaleString('zh-CN') : '')

const load = async () => {
  const data = await diaryApi.list({ current: 1, size: 50 })
  list.value = data.records || []
}

const openEditor = (d) => {
  if (d) Object.assign(editor.form, d)
  else Object.assign(editor.form, { id: null, content: '', mood: '', weather: '', visibility: 0 })
  editor.visible = true
}

const onSave = async () => {
  if (editor.form.id) await diaryApi.update(editor.form.id, editor.form)
  else await diaryApi.create(editor.form)
  ElMessage.success('保存成功')
  editor.visible = false
  load()
}

const onDel = async (d) => {
  await ElMessageBox.confirm('确认删除该日志？', '提示', { type: 'warning' })
  await diaryApi.remove(d.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.list-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.list-header h2 { color: var(--color-primary); }
.diary-item { margin-bottom: 12px; }
.diary-meta { font-size: 12px; color: var(--color-text-secondary); display: flex; gap: 8px; align-items: center; margin-bottom: 8px; }
.tag { background: #eef2f7; padding: 2px 8px; border-radius: 10px; }
.diary-content { white-space: pre-wrap; line-height: 1.7; }
.diary-actions { margin-top: 8px; text-align: right; }
</style>
