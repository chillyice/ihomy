<!-- 账本权限弹窗(仅家长):按成员设置整本账本的可见与读写档位,保存只提交改动过的成员 -->
<template>
  <el-dialog v-model="dialogVisible" append-to-body width="520px" :title="$t('book.perm.title')">
    <p class="perm-hint">{{ $t('book.perm.hint') }}</p>
    <div v-loading="loading" class="perm-list">
      <div v-for="m in members" :key="m.userId" class="perm-row">
        <div class="perm-who">
          <span class="perm-name">{{ m.nickname || m.username }}</span>
          <el-tag v-if="m.roleCode === 'OWNER'" size="small" type="warning" effect="plain">
            {{ $t('book.perm.ownerTag') }}
          </el-tag>
        </div>
        <!-- 家长不能被降级:恒定可管理 -->
        <span v-if="m.roleCode === 'OWNER'" class="perm-owner">{{ $t('book.perm.levels.MANAGE') }}</span>
        <el-select v-else v-model="draft[m.userId]" size="small" class="perm-select">
          <el-option v-for="lv in levels" :key="lv" :label="$t('book.perm.levels.' + lv)" :value="lv">
            <span>{{ $t('book.perm.levels.' + lv) }}</span>
            <span class="perm-opt-desc">{{ $t('book.perm.levelDesc.' + lv) }}</span>
          </el-option>
        </el-select>
      </div>
      <el-empty v-if="!loading && !members.length" :description="$t('book.perm.empty')" />
    </div>
    <template #footer>
      <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="saving" :disabled="!changedCount" @click="onSave">
        {{ $t('common.save') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
// 账本权限:数据唯一来源 GET /book/permission(家长带成员档位);保存调 PUT /book/permission/{userId}
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'

import { bookApi } from '@/api'

const props = defineProps({
  visible: { type: Boolean, default: false },
})
const emit = defineEmits(['update:visible', 'saved'])

const { t } = useI18n()
const loading = ref(false)
const saving = ref(false)
const members = ref([])
const levels = ref(['NONE', 'READ', 'WRITE', 'MANAGE'])
// 表单草稿:userId → 档位;baseline 是载入时的值,保存只提交差异
const draft = reactive({})
const baseline = reactive({})

const dialogVisible = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v),
})

const changedCount = computed(() => members.value.filter((m) => draft[m.userId] !== baseline[m.userId]).length)

const load = async () => {
  loading.value = true
  try {
    const data = (await bookApi.permission()) || {}
    members.value = data.members || []
    if (Array.isArray(data.levels) && data.levels.length) levels.value = data.levels
    Object.keys(draft).forEach((k) => delete draft[k])
    Object.keys(baseline).forEach((k) => delete baseline[k])
    for (const m of members.value) {
      draft[m.userId] = m.level
      baseline[m.userId] = m.level
    }
  } finally {
    loading.value = false
  }
}

// 打开时重新取一次:别人刚改过的档位不能拿上一次的缓存覆盖
watch(
  () => props.visible,
  (v) => v && load(),
)

const onSave = async () => {
  const changed = members.value.filter((m) => m.roleCode !== 'OWNER' && draft[m.userId] !== baseline[m.userId])
  if (!changed.length) return
  saving.value = true
  try {
    await Promise.all(changed.map((m) => bookApi.setPermission(m.userId, draft[m.userId])))
    ElMessage.success(t('common.saveSuccess'))
    changed.forEach((m) => {
      baseline[m.userId] = draft[m.userId]
    })
    emit('saved')
    dialogVisible.value = false
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.perm-hint {
  margin: 0 0 10px;
  font-size: 12px;
  color: var(--color-text-secondary, #999);
}
.perm-list {
  max-height: 380px;
  overflow-y: auto;
}
.perm-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--color-border);
}
.perm-who {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.perm-name {
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.perm-select {
  width: 150px;
  flex-shrink: 0;
}
.perm-owner {
  font-size: 13px;
  color: var(--color-text-secondary, #999);
}
.perm-opt-desc {
  margin-left: 8px;
  font-size: 12px;
  color: var(--color-text-secondary, #999);
}
</style>
