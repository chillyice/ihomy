<!-- 纪念日页签:阳历/农历(含闰月)纪念日卡片,访客可读,登录后增删改 -->
<template>
  <div v-loading="loading">
    <div v-if="list.length" class="anni-grid">
      <div v-for="a in list" :key="a.id" class="anni-card card">
        <div class="anni-top">
          <span class="calendar-badge" :class="a.calendar">{{
            a.calendar === 'lunar' ? t('anniversary.lunar') : t('anniversary.solar')
          }}</span>
          <span v-if="a.recurring === 'ONCE'" class="once-badge">{{ t('anniversary.once') }}</span>
        </div>
        <div class="anni-date">
          {{
            t('anniversary.dateFormat', {
              leap: a.isLeap && a.calendar === 'lunar' ? t('anniversary.leap') : '',
              month: a.month,
              day: a.day,
            })
          }}
        </div>
        <div class="anni-name">{{ a.name }}</div>
        <div class="anni-owner">
          <el-icon><User /></el-icon>
          <span>{{ memberText(a) }}</span>
        </div>
        <div v-if="userStore.isLoggedIn" class="anni-actions">
          <el-tooltip :content="t('common.edit')" placement="top" :show-after="300">
            <el-button size="small" text @click="openEditor(a)"
              ><el-icon><Edit /></el-icon
            ></el-button>
          </el-tooltip>
          <el-tooltip :content="t('common.delete')" placement="top" :show-after="300">
            <el-button size="small" text type="danger" @click="onDel(a)"
              ><el-icon><Delete /></el-icon
            ></el-button>
          </el-tooltip>
        </div>
      </div>
    </div>
    <el-empty v-else :description="userStore.isGuest ? t('anniversary.guestEmpty') : t('anniversary.emptyHint')" />

    <AnniversaryDialog v-model="editorVisible" :item="editing" :members="members" @saved="load" />
  </div>
</template>

<script setup>
// 纪念日列表:访客看演示家庭的公开数据(后端白名单),登录后可增删改
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { User, Edit, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { anniversaryApi, memberApi } from '@/api'
import { useUserStore } from '@/stores/user'
import AnniversaryDialog from '../dialogs/AnniversaryDialog.vue'

const { t } = useI18n()
const userStore = useUserStore()
const list = ref([])
const members = ref([])
const loading = ref(false)
const editorVisible = ref(false)
const editing = ref(null)

// 关联成员展示:多位用顿号连接,未关联即为家庭级纪念日
const memberText = (a) =>
  Array.isArray(a.memberNames) && a.memberNames.length ? a.memberNames.join('、') : t('anniversary.familyAnniversary')

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
    // 成员列表只用于关联选择,失败不影响纪念日展示
  }
}

const openEditor = (a = null) => {
  editing.value = a
  editorVisible.value = true
}

const onDel = async (a) => {
  await ElMessageBox.confirm(t('anniversary.deleteConfirm', { name: a.name }), t('common.tip'), {
    type: 'warning',
    closeOnClickModal: true,
  })
  await anniversaryApi.remove(a.id)
  ElMessage.success(t('common.deleted'))
  load()
}

onMounted(() => {
  load()
  loadMembers()
})
defineExpose({ openEditor })
</script>

<style scoped>
.anni-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}
.anni-card {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.anni-top {
  display: flex;
  justify-content: space-between;
}
.calendar-badge {
  font-size: 11px;
  padding: 2px 10px;
  border-radius: 10px;
}
.calendar-badge.solar {
  background: rgba(46, 116, 181, 0.1);
  color: var(--color-accent);
}
.calendar-badge.lunar {
  background: rgba(230, 162, 60, 0.12);
  color: #b8860b;
}
.once-badge {
  font-size: 11px;
  color: var(--color-text-secondary);
}
.anni-date {
  font-size: 26px;
  font-weight: 700;
  color: var(--color-primary);
}
.anni-name {
  font-size: 15px;
  color: var(--color-text);
}
.anni-owner {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.anni-actions {
  margin-top: 4px;
  text-align: right;
  border-top: 1px solid rgba(31, 58, 95, 0.06);
  padding-top: 8px;
}
.anni-actions :deep(.el-button) {
  padding: 5px 6px;
}
.anni-actions :deep(.el-button + .el-button) {
  margin-left: 4px;
}

@media (max-width: 768px) {
  .anni-grid {
    grid-template-columns: 1fr;
  }
}
</style>
