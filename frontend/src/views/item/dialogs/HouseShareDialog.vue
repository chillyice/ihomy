<template>
  <el-dialog v-model="visible" append-to-body class="dialog-md" :title="$t('item.shareHouse')">
    <p class="share-tip">{{ $t('item.shareHouseTip', { house: house?.name || '' }) }}</p>
    <el-checkbox-group v-if="candidates.length" v-model="checked" class="share-families">
      <el-checkbox v-for="f in candidates" :key="f.familyId" :value="f.familyId" :label="f.name" />
    </el-checkbox-group>
    <el-empty v-else :image-size="60" :description="$t('item.shareHouseNoCandidate')" />
    <template #footer>
      <el-button @click="visible = false">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="saving" @click="save">{{ $t('common.confirm') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { itemApi, authApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { useI18n } from 'vue-i18n'

const emit = defineEmits(['saved'])
const { t } = useI18n()
const user = useUserStore()

const visible = ref(false)
const saving = ref(false)
const house = ref(null)
const candidates = ref([])
const checked = ref([])

/** 共享对象只能从「我所属的其他家庭」里挑:先取当前账号的家庭清单,去掉正在使用的这一家 */
const open = async (row) => {
  house.value = row
  visible.value = true
  checked.value = []
  const currentFamilyId = Number(user.userInfo?.familyId)
  try {
    const families = await authApi.families()
    candidates.value = (families || []).filter((f) => Number(f.familyId) !== currentFamilyId)
    const shares = await itemApi.houseShares(row.id)
    checked.value = (shares || []).map((s) => Number(s.familyId))
  } catch {
    candidates.value = []
  }
}

const save = async () => {
  saving.value = true
  try {
    await itemApi.saveHouseShares(house.value.id, checked.value)
    ElMessage.success(t('common.success'))
    visible.value = false
    emit('saved')
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.share-tip {
  margin: 0 0 14px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}
.share-families {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
}
</style>
