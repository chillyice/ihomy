<!-- 成员管理页:成员列表/角色调整/移出 + OWNER 专属的邀请码、入家申请审核、搜索加入新家庭 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: '家庭成员' }]" />

    <div class="section-header">
      <h2>家庭成员</h2>
      <el-button type="primary" plain @click="openSearch">加入新家庭</el-button>
    </div>

    <div v-if="userStore.isOwner" class="card invite-card">
      <div class="invite-title">邀请新成员</div>
      <div class="invite-row">
        <el-select v-model="inviteRole" style="width: 140px">
          <el-option label="成员" value="MEMBER" />
          <el-option label="孩童" value="CHILD" />
        </el-select>
        <el-button type="primary" @click="genInvite">生成邀请码</el-button>
      </div>
      <div v-if="inviteCodes.length" class="invite-list">
        <div v-for="c in inviteCodes" :key="c.id" class="invite-code">
          <code>{{ c.code }}</code>
          <span class="invite-uses">{{ c.usedCount }}/{{ c.maxUses }} 次</span>
          <span class="invite-expire">到期 {{ formatDate(c.expiresAt) }}</span>
        </div>
      </div>
    </div>

    <div v-if="userStore.isOwner && applies.length" class="card invite-card">
      <div class="invite-title">入家申请（{{ applies.length }}）</div>
      <div class="apply-row" v-for="a in applies" :key="a.id">
        <el-avatar :size="36">{{ (a.applicantName || 'U').charAt(0) }}</el-avatar>
        <div class="apply-info">
          <div class="apply-name">{{ a.applicantName }}</div>
          <div v-if="a.message" class="apply-msg">{{ a.message }}</div>
          <div class="apply-time">{{ formatDate(a.createdAt) }}</div>
        </div>
        <div class="apply-actions">
          <el-button size="small" type="success" @click="handleApply(a, 'approve')">通过</el-button>
          <el-button size="small" type="danger" plain @click="handleApply(a, 'reject')">拒绝</el-button>
        </div>
      </div>
    </div>

    <div v-loading="loading" class="member-list">
      <div v-for="m in members" :key="m.id" class="member-row card">
        <el-avatar :size="40" :src="m.avatar">{{ (m.nickname || m.username || 'U').charAt(0) }}</el-avatar>
        <div class="member-info">
          <div class="member-name">{{ m.nickname || m.username }}</div>
          <div class="member-un">@{{ m.username }}</div>
        </div>
        <div class="member-right">
          <el-select
            v-if="userStore.isOwner && m.id !== userStore.userInfo?.id"
            :model-value="m.roleCode"
            size="small"
            style="width: 100px"
            @change="(v) => changeRole(m, v)"
          >
            <el-option label="家长" value="OWNER" />
            <el-option label="成员" value="MEMBER" />
            <el-option label="孩童" value="CHILD" />
          </el-select>
          <el-tag v-else size="small">{{ roleName(m.roleCode) }}</el-tag>
          <el-button
            v-if="userStore.isOwner && m.id !== userStore.userInfo?.id"
            size="small"
            type="danger"
            text
            @click="removeMember(m)"
          >移出</el-button>
        </div>
      </div>
    </div>

    <el-dialog v-model="searchVisible" title="加入新家庭" width="440px">
      <el-input v-model="keyword" placeholder="输入家庭 ID 或名称搜索" clearable @keyup.enter="doSearch">
        <template #append>
          <el-button @click="doSearch">搜索</el-button>
        </template>
      </el-input>
      <div v-loading="searchLoading" class="search-result">
        <div v-for="f in searchResult" :key="f.id" class="search-item">
          <div class="search-item-info">
            <div class="search-item-name">
              {{ f.name }}
              <el-tag v-if="f.isDemo" size="small" type="warning">演示家庭</el-tag>
            </div>
            <div class="search-item-desc">{{ f.description || f.coverText || '暂无简介' }}</div>
            <div class="search-item-meta">{{ f.memberCount }} 位成员</div>
          </div>
          <el-button size="small" type="primary" :disabled="f.joined || f.pending" @click="applyFamily(f)">
            {{ f.joined ? '已加入' : f.pending ? '已申请' : '申请加入' }}
          </el-button>
        </div>
        <el-empty v-if="!searchLoading && keyword && !searchResult.length" description="未找到家庭" :image-size="60" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { memberApi, familyApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb.vue'

const userStore = useUserStore()
const members = ref([])
const loading = ref(false)
const inviteCodes = ref([])
const inviteRole = ref('MEMBER')
const applies = ref([])
const searchVisible = ref(false)
const keyword = ref('')
const searchLoading = ref(false)
const searchResult = ref([])

// 角色码转中文展示
const roleName = (r) =>
  ({ OWNER: '家长', MEMBER: '成员', CHILD: '孩童' }[r] || r)

const formatDate = (d) => (d ? new Date(d).toLocaleString('zh-CN') : '')

// 拉取成员列表;OWNER 额外加载邀请码与入家申请
const load = async () => {
  loading.value = true
  try {
    members.value = await memberApi.list()
    if (userStore.isOwner) {
      inviteCodes.value = await memberApi.inviteList()
      applies.value = await familyApi.applyList()
    }
  } finally {
    loading.value = false
  }
}

// 生成邀请码并刷新邀请码列表(新成员注册时凭码加入家庭)
const genInvite = async () => {
  const data = await memberApi.createInvite(inviteRole.value || 'MEMBER')
  ElMessage.success(`邀请码已生成：${data.code}`)
  inviteCodes.value = await memberApi.inviteList()
}

const changeRole = async (m, roleCode) => {
  try {
    await memberApi.setRole(m.id, roleCode)
    m.roleCode = roleCode
    ElMessage.success('角色已更新')
  } catch (e) {
    ElMessage.error('角色更新失败')
  }
}

// 移出成员:二次确认后删除并刷新列表
const removeMember = async (m) => {
  await ElMessageBox.confirm(`确认将 ${m.nickname || m.username} 移出家庭？`, '提示', { type: 'warning' })
  await memberApi.remove(m.id)
  ElMessage.success('已移出')
  load()
}

const openSearch = () => {
  keyword.value = ''
  searchResult.value = []
  searchVisible.value = true
}

// 按关键字搜索公开家庭(OWNER 审核通过的申请才真正入家)
const doSearch = async () => {
  if (!keyword.value.trim()) return
  searchLoading.value = true
  try {
    searchResult.value = await familyApi.search(keyword.value.trim())
  } finally {
    searchLoading.value = false
  }
}

// 提交入家申请,等待对方家长审核
const applyFamily = async (f) => {
  await familyApi.apply(f.id, '')
  ElMessage.success('申请已提交，等待家长审核')
  f.pending = true
}

// OWNER 审核入家申请:通过则对方成为 MEMBER
const handleApply = async (a, action) => {
  await familyApi.handleApply(a.id, action)
  ElMessage.success(action === 'approve' ? '已通过，对方已加入家庭' : '已拒绝')
  load()
}

onMounted(load)
</script>

<style scoped>
.card-header h2 { color: var(--color-primary); }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.invite-card { margin-bottom: 16px; }
.invite-title { font-weight: 600; color: var(--color-primary); margin-bottom: 10px; }
.invite-row { display: flex; gap: 10px; align-items: center; }
.invite-list { margin-top: 12px; display: flex; flex-direction: column; gap: 6px; }
.invite-code { display: flex; gap: 12px; align-items: center; font-size: 13px; }
.invite-usage { color: var(--color-text-secondary); font-size: 12px; }
.invite-expire { color: var(--color-text-secondary); font-size: 12px; }
.member-list { display: flex; flex-direction: column; gap: 10px; }
.member-row { display: flex; align-items: center; gap: 14px; padding: 12px 16px; }
.member-info { flex: 1; }
.member-name { font-weight: 600; font-size: 15px; color: var(--color-text); }
.member-name { font-size: 14px; }
.member-right { display: flex; align-items: center; gap: 10px; }
.apply-row { display: flex; align-items: center; gap: 12px; padding: 8px 0; border-bottom: 1px dashed var(--color-border); }
.apply-info { flex: 1; }
.apply-name { font-weight: 600; font-size: 14px; }
.apply-msg { color: var(--color-text-secondary); font-size: 12px; }
.apply-time { color: var(--color-text-secondary); font-size: 12px; }
.apply-actions { display: flex; gap: 8px; }
.search-result { margin-top: 14px; max-height: 320px; overflow-y: auto; display: flex; flex-direction: column; gap: 8px; }
.search-item { display: flex; align-items: center; gap: 12px; padding: 10px 12px; border: 1px solid var(--color-border); border-radius: 8px; }
.search-item-info { flex: 1; min-width: 0; }
.search-item-name { font-weight: 600; font-size: 14px; display: flex; align-items: center; gap: 6px; }
.search-item-desc { color: var(--color-text-secondary); font-size: 12px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.search-item-meta { color: var(--color-text-secondary); font-size: 12px; }
</style>
