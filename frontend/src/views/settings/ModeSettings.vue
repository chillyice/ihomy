<!-- 显示模式设置:老人模式(大字/高对比/简化首页)与儿童模式(导航白名单 + 本机退出密码) -->
<template>
  <div class="card settings-card">
    <div class="section-label">{{ $t('displayMode.title') }}</div>
    <p class="share-tip display-lead">{{ $t('displayMode.lead') }}</p>

    <div class="mode-list">
      <label v-for="opt in options" :key="opt.value" class="mode-row" :class="{ active: mode === opt.value }">
        <el-radio :model-value="mode" :value="opt.value" @change="onPick(opt.value)">
          <span class="mode-name">{{ $t(opt.labelKey) }}</span>
        </el-radio>
        <span class="mode-desc">{{ $t(opt.descKey) }}</span>
      </label>
    </div>

    <!-- 儿童模式退出密码:已开启时给「退出」;未开启时给「设置密码并开启」 -->
    <div v-if="mode === 'child'" class="pin-box">
      <div class="pin-title">{{ $t('displayMode.exitTitle') }}</div>
      <div class="pin-tip">{{ $t('displayMode.exitTip') }}</div>
      <div class="pin-row">
        <el-input
          v-model="exitPin"
          type="password"
          :maxlength="8"
          inputmode="numeric"
          show-password
          :placeholder="$t('displayMode.pinPlaceholder')"
          class="pin-input"
        />
        <el-button type="primary" :loading="busy" @click="onExitChild">{{ $t('displayMode.exitAction') }}</el-button>
      </div>
      <div class="pin-error" :class="{ show: errorText }">{{ errorText }}</div>
    </div>

    <div v-else class="pin-box">
      <div class="pin-title">{{ $t('displayMode.pinTitle') }}</div>
      <div class="pin-tip">{{ hasChildPin ? $t('displayMode.hasPin') : $t('displayMode.pinTip') }}</div>
      <div class="pin-row">
        <el-input
          v-model="newPin"
          type="password"
          :maxlength="8"
          inputmode="numeric"
          show-password
          :placeholder="$t('displayMode.pinPlaceholder')"
          class="pin-input"
        />
        <el-button type="primary" :loading="busy" @click="onSavePin">{{ $t('displayMode.pinSave') }}</el-button>
      </div>
      <div class="pin-error" :class="{ show: pinMsg }">{{ pinMsg }}</div>
    </div>

    <div class="display-note">{{ $t('displayMode.note') }}</div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useDisplayMode } from '@/composables/useDisplayMode'
import { isValidPin } from '@/utils/displayMode'

const { t } = useI18n()
const { mode, hasChildPin, setDisplayMode, enableChildMode, disableChildMode } = useDisplayMode()

const exitPin = ref('')
const newPin = ref('')
const busy = ref(false)
const errorText = ref('')
const pinMsg = ref('')

const options = [
  { value: 'normal', labelKey: 'displayMode.normal', descKey: 'displayMode.normalDesc' },
  { value: 'elder', labelKey: 'displayMode.elder', descKey: 'displayMode.elderDesc' },
  { value: 'child', labelKey: 'displayMode.child', descKey: 'displayMode.childDesc' },
]

const onPick = async (value) => {
  errorText.value = ''
  pinMsg.value = ''
  if (value === 'child') {
    // 已设过密码就直接切(退出仍需密码);没设过则先让家长在下面设一个
    if (hasChildPin.value) setDisplayMode('child')
    else pinMsg.value = t('displayMode.setPinFirst')
    return
  }
  setDisplayMode(value)
}

const onSavePin = async () => {
  if (!isValidPin(newPin.value)) {
    pinMsg.value = t('displayMode.pinInvalid')
    return
  }
  busy.value = true
  try {
    // 在普通/老人模式下设置密码 = 立即进入儿童模式(已设过则重置密码)
    const ok = await enableChildMode(newPin.value)
    pinMsg.value = ok ? '' : t('displayMode.pinFailed')
    if (ok) {
      newPin.value = ''
      ElMessage.success(t('displayMode.childStarted'))
    }
  } finally {
    busy.value = false
  }
}

const onExitChild = async () => {
  if (!isValidPin(exitPin.value)) {
    errorText.value = t('displayMode.pinInvalid')
    return
  }
  busy.value = true
  try {
    const ok = await disableChildMode(exitPin.value)
    if (ok) {
      ElMessage.success(t('displayMode.exitDone'))
      exitPin.value = ''
      errorText.value = ''
    } else {
      errorText.value = t('displayMode.wrongPin')
    }
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.display-lead {
  margin: 0 0 12px;
}
.mode-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.mode-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 14px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  cursor: pointer;
}
.mode-row.active {
  border-color: var(--color-accent);
  background: var(--color-card-2);
}
.mode-name {
  font-weight: 600;
}
.mode-desc {
  padding-left: 24px;
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.5;
}
.pin-box {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--color-border);
}
.pin-title {
  font-weight: 600;
  margin-bottom: 4px;
}
.pin-tip {
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.6;
  margin-bottom: 10px;
}
.pin-row {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
}
.pin-input {
  width: 200px;
  max-width: 100%;
}
.pin-error {
  min-height: 20px;
  color: var(--color-danger);
  font-size: 13px;
  opacity: 0;
}
.pin-error.show {
  opacity: 1;
}
.display-note {
  margin-top: 18px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--color-card-2);
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.6;
}
</style>
