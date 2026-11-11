<!-- 儿童模式:右下角常驻小入口,点开输入家长退出密码。密码只是本机门槛,不改变账号权限 -->
<template>
  <teleport to="body">
    <button v-if="!dialogVisible" type="button" class="child-lock" @click="open">
      <span aria-hidden="true">🔒</span>
      <span class="cl-text">{{ $t('displayMode.childLock') }}</span>
    </button>

    <el-dialog v-model="dialogVisible" width="360px" :title="$t('displayMode.exitTitle')" append-to-body>
      <p class="cl-tip">{{ $t('displayMode.exitTip') }}</p>
      <el-input
        v-model="pin"
        type="password"
        :maxlength="8"
        inputmode="numeric"
        show-password
        :placeholder="$t('displayMode.pinPlaceholder')"
        @keyup.enter="submit"
      />
      <div class="cl-error">{{ error }}</div>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="busy" @click="submit">{{ $t('displayMode.exitAction') }}</el-button>
      </template>
    </el-dialog>
  </teleport>
</template>

<script setup>
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useDisplayMode } from '@/composables/useDisplayMode'
import { isValidPin } from '@/utils/displayMode'

const { t } = useI18n()
const { disableChildMode } = useDisplayMode()

const dialogVisible = ref(false)
const pin = ref('')
const error = ref('')
const busy = ref(false)

const open = () => {
  pin.value = ''
  error.value = ''
  dialogVisible.value = true
}

const submit = async () => {
  if (!isValidPin(pin.value)) {
    error.value = t('displayMode.pinInvalid')
    return
  }
  busy.value = true
  try {
    const ok = await disableChildMode(pin.value)
    if (ok) {
      dialogVisible.value = false
      ElMessage.success(t('displayMode.exitDone'))
    } else {
      error.value = t('displayMode.wrongPin')
    }
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.child-lock {
  position: fixed;
  right: 12px;
  bottom: 12px;
  z-index: var(--z-float);
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-card);
  color: var(--color-text-secondary);
  font-size: 12px;
  opacity: 0.62;
  cursor: pointer;
}
.child-lock:active {
  opacity: 1;
}
.cl-tip {
  margin: 0 0 10px;
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.6;
}
.cl-error {
  min-height: 20px;
  font-size: 13px;
  color: var(--color-danger);
}
</style>
