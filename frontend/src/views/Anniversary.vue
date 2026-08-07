<!-- 纪念日页:阳历/农历(含闰月)纪念日卡片列表,访客可读,登录后增删改 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: '纪念日' }]" />

    <div class="list-header">
      <h2>纪念日</h2>
      <el-button v-if="userStore.isLoggedIn" type="primary" @click="openEditor()">新增纪念日</el-button>
    </div>

    <div v-loading="loading">
      <div v-if="list.length" class="anni-grid">
        <div v-for="a in list" :key="a.id" class="anni-card card">
          <div class="anni-top">
            <span class="calendar-badge" :class="a.calendar">{{ a.calendar === 'lunar' ? '农历' : '阳历' }}</span>
            <span v-if="!a.recurring" class="once-badge">单次</span>
          </div>
          <div class="anni-date">{{ a.isLeap && a.calendar === 'lunar' ? '闰' : '' }}{{ a.month }}月{{ a.day }}日</div>
          <div class="anni-name">{{ a.name }}</div>
          <div class="anni-owner">
            <el-icon><User /></el-icon>
            <span>{{ a.userName || '家庭纪念日' }}</span>
          </div>
          <div v-if="userStore.isLoggedIn" class="anni-actions">
            <el-button size="small" text @click="openEditor(a)">编辑</el-button>
            <el-button size="small" text type="danger" @click="onDel(a)">删除</el-button>
          </div>
        </div>
      </div>
      <el-empty v-else :description="userStore.isGuest ? '暂无纪念日' : '还没有纪念日，去添加一个吧'" />
    </div>

    <el-dialog v-model="editor.visible" :title="editor.form.id ? '编辑纪念日' : '新增纪念日'" width="480px">
      <el-form :model="editor.form" label-position="top">
        <el-form-item label="名称">
          <el-input v-model="editor.form.name" placeholder="如：宝宝生日、结婚纪念日" />
        </el-form-item>
        <el-form-item label="历法">
          <el-radio-group v-model="editor.form.calendar">
            <el-radio value="solar">阳历</el-radio>
            <el-radio value="lunar">农历</el-radio>
          </el-radio-group>
        </el-form-item>
        <div class="form-row">
          <el-form-item label="月份">
            <el-select v-model="editor.form.month" style="width: 100%">
              <el-option v-for="m in 12" :key="m" :label="`${m} 月`" :value="m" />
            </el-select>
          </el-form-item>
          <el-form-item label="日期">
            <el-select v-model="editor.form.day" style="width: 100%">
              <el-option v-for="d in 31" :key="d" :label="`${d} 日`" :value="d" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item v-if="editor.form.calendar === 'lunar'" label="闰月">
          <el-switch v-model="editor.form.isLeap" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="关联成员">
          <el-select v-model="editor.form.userId" placeholder="选择成员（不选则为家庭纪念日）" clearable style="width: 100%">
            <el-option v-for="m in members" :key="m.id" :label="m.nickname || m.username" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="每年重复">
          <el-switch v-model="editor.form.recurring" :active-value="1" :inactive-value="0" />
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
import { anniversaryApi, memberApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { User } from '@element-plus/icons-vue'
import Breadcrumb from '@/components/Breadcrumb.vue'

const userStore = useUserStore()
const list = ref([])
const members = ref([])
const loading = ref(false)
// 编辑框状态:form 同时承担新增与编辑(以 id 区分)
const editor = reactive({
  visible: false,
  form: { id: null, name: '', calendar: 'solar', month: 1, day: 1, isLeap: 0, userId: null, recurring: 1 },
})

// 拉取纪念日列表
const load = async () => {
  loading.value = true
  try {
    list.value = await anniversaryApi.list()
  } finally {
    loading.value = false
  }
}

const loadMembers = async () => {
  if (!userStore.isLoggedIn) return
  try {
    members.value = await memberApi.list()
  } catch (e) {
    // 忽略
  }
}

// 打开编辑框:传入纪念日则回填(编辑),否则重置为默认值(新增)
const openEditor = (a) => {
  if (a) {
    Object.assign(editor.form, {
      id: a.id, name: a.name, calendar: a.calendar, month: a.month, day: a.day,
      isLeap: a.isLeap, userId: a.userId, recurring: a.recurring,
    })
  } else {
    Object.assign(editor.form, { id: null, name: '', calendar: 'solar', month: 1, day: 1, isLeap: 0, userId: null, recurring: 1 })
  }
  editor.visible = true
}

// 保存:有 id 走更新,无 id 走新增
const onSave = async () => {
  if (!editor.form.name) return ElMessage.warning('请输入纪念日名称')
  if (editor.form.id) await anniversaryApi.update(editor.form.id, editor.form)
  else await anniversaryApi.create(editor.form)
  ElMessage.success('保存成功')
  editor.visible = false
  load()
}

const onDel = async (a) => {
  await ElMessageBox.confirm(`确认删除「${a.name}」？`, '提示', { type: 'warning' })
  await anniversaryApi.remove(a.id)
  ElMessage.success('已删除')
  load()
}

onMounted(() => {
  load()
  loadMembers()
})
</script>

<style scoped>
.list-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.list-header h2 { color: var(--color-primary); }
.anni-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}
.anni-card { padding: 20px; display: flex; flex-direction: column; gap: 8px; }
.anni-top { display: flex; justify-content: space-between; }
.calendar-badge {
  font-size: 11px;
  padding: 2px 10px;
  border-radius: 10px;
}
.calendar-badge.solar { background: rgba(46, 116, 181, 0.1); color: var(--color-accent); }
.calendar-badge.lunar { background: rgba(230, 162, 60, 0.12); color: #B8860B; }
.once-badge { font-size: 11px; color: var(--color-text-secondary); }
.anni-date { font-size: 26px; font-weight: 700; color: var(--color-primary); }
.anni-name { font-size: 15px; color: var(--color-text); }
.anni-owner {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.anni-actions { margin-top: 4px; text-align: right; border-top: 1px solid rgba(31, 58, 95, 0.06); padding-top: 8px; }
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }

@media (max-width: 768px) {
  .anni-grid { grid-template-columns: 1fr; }
}
</style>