<!-- 相册列表页:封面/类型/照片数卡片网格,登录后可新建,家长或创建者可管理 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: '相册' }]" />

    <div class="list-header">
      <h2>相册</h2>
      <el-button v-if="userStore.isLoggedIn" type="primary" @click="openEditor()">新建相册</el-button>
    </div>

    <div v-loading="loading">
      <div v-if="albums.length" class="album-grid">
        <div
          v-for="a in albums"
          :key="a.id"
          class="album-card card"
          @click="$router.push(`/album/${a.id}`)"
        >
          <div class="album-cover-wrap">
            <div
              v-if="a.cover"
              class="album-cover"
              :style="{ backgroundImage: `url(${a.cover})` }"
            ></div>
            <div v-else class="album-cover album-cover-empty">
              <span>📷</span>
            </div>
            <span class="album-type" :class="a.type">{{ a.type === 'public' ? '公开' : '私密' }}</span>
            <span class="album-count">{{ a.photoCount }} 张</span>
          </div>
          <div class="album-info">
            <div class="album-name">{{ a.name }}</div>
            <div class="album-meta">{{ formatDate(a.createdAt) }}</div>
          </div>
          <div v-if="canManage(a)" class="album-actions" @click.stop>
            <el-button size="small" text @click="openEditor(a)">编辑</el-button>
            <el-button size="small" text type="danger" @click="onDel(a)">删除</el-button>
          </div>
        </div>
      </div>
      <el-empty v-else :description="userStore.isGuest ? '暂无公开相册' : '还没有相册，去创建一个吧'" />
    </div>

    <el-dialog v-model="editor.visible" :title="editor.form.id ? '编辑相册' : '新建相册'" width="420px">
      <el-form :model="editor.form" label-position="top">
        <el-form-item label="相册名称">
          <el-input v-model="editor.form.name" placeholder="如：宝宝成长、旅行回忆" />
        </el-form-item>
        <el-form-item label="可见范围">
          <el-radio-group v-model="editor.form.type">
            <el-radio value="public">公开（访客可见）</el-radio>
            <el-radio value="private">私密（仅家庭成员）</el-radio>
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
import { albumApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb.vue'

const userStore = useUserStore()
const albums = ref([])
const loading = ref(false)
const editor = reactive({ visible: false, form: { id: null, name: '', type: 'public' } })

// 管理权限:家长或相册创建者本人
const canManage = (a) =>
  userStore.isLoggedIn && (userStore.isOwner || a.createdBy === userStore.userInfo?.id)
const formatDate = (d) => (d ? new Date(d).toLocaleDateString('zh-CN') : '')

const load = async () => {
  loading.value = true
  try {
    albums.value = await albumApi.list()
  } finally {
    loading.value = false
  }
}

const openEditor = (a) => {
  if (a) Object.assign(editor.form, { id: a.id, name: a.name, type: a.type })
  else Object.assign(editor.form, { id: null, name: '', type: 'public' })
  editor.visible = true
}

const onSave = async () => {
  if (!editor.form.name) return ElMessage.warning('请输入相册名称')
  if (editor.form.id) await albumApi.update(editor.form.id, editor.form)
  else await albumApi.create(editor.form)
  ElMessage.success('保存成功')
  editor.visible = false
  load()
}

// 删除相册(连同相册内照片):二次确认后执行
const onDel = async (a) => {
  await ElMessageBox.confirm(`确认删除相册「${a.name}」？相册内照片将一并删除`, '提示', { type: 'warning' })
  await albumApi.remove(a.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<style scoped>
.list-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.list-header h2 { color: var(--color-primary); }
.album-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}
.album-card { overflow: hidden; cursor: pointer; transition: transform 0.15s, box-shadow 0.15s; }
.album-card:hover { transform: translateY(-3px); box-shadow: 0 8px 24px rgba(31,58,95,0.15); }
.album-cover-wrap { position: relative; aspect-ratio: 4 / 3; }
.album-cover {
  width: 100%;
  height: 100%;
  background-size: cover;
  background-position: center;
}
.album-cover-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 42px;
  background: linear-gradient(135deg, #eef2f7, #dce6f0);
}
.album-type {
  position: absolute;
  top: 10px;
  left: 10px;
  font-size: 11px;
  color: #fff;
  padding: 2px 10px;
  border-radius: 10px;
  background: rgba(46, 116, 181, 0.85);
}
.album-type.private { background: rgba(230, 162, 60, 0.9); }
.album-count {
  position: absolute;
  bottom: 10px;
  right: 10px;
  font-size: 12px;
  color: #fff;
  background: rgba(0, 0, 0, 0.5);
  padding: 2px 10px;
  border-radius: 10px;
}
.album-info { padding: 14px 16px 6px; }
.album-name { font-size: 15px; font-weight: 600; color: var(--color-text); }
.album-meta { font-size: 12px; color: var(--color-text-secondary); margin-top: 4px; }
.album-actions { padding: 6px 8px 10px; text-align: right; }

@media (max-width: 768px) {
  .album-grid { grid-template-columns: repeat(2, 1fr); }
}
</style>