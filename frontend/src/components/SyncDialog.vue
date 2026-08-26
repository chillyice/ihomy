<!-- 从设备同步对话框:选存储设备 + includeEmpty,后台同步建相册;相册/放映厅复用 -->
<template>
  <el-dialog v-model="visible" append-to-body :title="$t('album.syncFromDevice')" width="440px">
    <el-form label-position="top">
      <el-form-item :label="$t('storage.devices')">
        <el-select v-model="deviceId" :placeholder="$t('storage.pickDevice')" style="width: 100%">
          <el-option v-for="d in devices" :key="d.id" :label="d.name" :value="d.id" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-checkbox v-model="includeEmpty">{{ $t('storage.includeEmpty') }}</el-checkbox>
      </el-form-item>
      <div v-if="result" class="sync-result">
        {{ $t('storage.syncDoneSummary', { albums: result.albums ?? 0, photos: result.photos ?? 0, dup: result.skippedDup ?? 0 }) }}
      </div>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="syncing" :disabled="!deviceId" @click="start">{{ $t('storage.syncNow') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { storageApi } from '@/api'

const { t } = useI18n()
const props = defineProps({ modelValue: Boolean })
const emit = defineEmits(['update:modelValue'])
const userStore = useUserStore()

const visible = ref(props.modelValue)
watch(() => props.modelValue, (v) => { visible.value = v; if (v) loadDevices() })
watch(visible, (v) => emit('update:modelValue', v))

const devices = ref([])
const deviceId = ref(0)
const includeEmpty = ref(false)
const syncing = ref(false)
const result = ref(null)
let timer = null

async function loadDevices() {
  devices.value = await storageApi.devices()
}

async function start() {
  syncing.value = true
  result.value = null
  try {
    const { taskId } = await storageApi.sync({ deviceId: deviceId.value, includeEmpty: includeEmpty.value })
    ElMessage.success(t('storage.syncStarted'))
    timer = setInterval(async () => {
      try {
        const p = await storageApi.syncProgress(taskId)
        if (p.status === 'DONE' || p.status === 'FAILED') {
          clearInterval(timer); timer = null
          syncing.value = false
          if (p.status === 'DONE') { result.value = p; ElMessage.success(p.message || t('storage.syncDone')) }
          else ElMessage.error(p.message || t('storage.syncFailed'))
        }
      } catch { clearInterval(timer); timer = null; syncing.value = false }
    }, 1000)
  } catch { syncing.value = false }
}
</script>

<style scoped>
.sync-result { margin-top: 8px; padding: 8px 12px; background: var(--color-bg-2); border-radius: 6px; font-size: 13px; }
</style>
