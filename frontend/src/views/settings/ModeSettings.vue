<!-- 显示模式:默认 / 老人 / 未成年(儿童)。
     只有选中未成年时才设退出密码;普通成员只能给自己设,家长可以选这台设备上要锁哪个成员。
     记录按账号存在本机(utils/displayMode.js),儿童模式只限制使用它的那个成员。 -->
<template>
  <div class="card settings-card">
    <div class="section-label">{{ $t('displayMode.title') }}</div>
    <p class="share-tip display-lead">{{ $t('displayMode.lead') }}</p>

    <!-- 家长:选择要为谁设置;普通成员只给自己设置 -->
    <el-form v-if="isOwner" label-position="top" class="settings-form">
      <el-form-item :label="$t('displayMode.targetUser')">
        <el-select v-model="targetId" class="target-select">
          <el-option v-for="m in memberOptions" :key="m.id" :value="m.id" :label="m.name" />
        </el-select>
        <div class="share-tip">{{ $t('displayMode.targetHint') }}</div>
      </el-form-item>
    </el-form>

    <div class="mode-list">
      <label v-for="opt in options" :key="opt.value" class="mode-row" :class="{ active: targetMode === opt.value }">
        <el-radio :model-value="targetMode" :value="opt.value" @change="onPick(opt.value)">
          <span class="mode-name">{{ $t(opt.labelKey) }}</span>
        </el-radio>
        <span class="mode-desc">{{ $t(opt.descKey) }}</span>
      </label>
    </div>

    <!-- 未成年模式已开启:本人用密码退出;家长改别人时可直接关闭或重设密码 -->
    <div v-if="targetMode === 'child'" class="pin-box">
      <template v-if="targetIsSelf">
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
            @keyup.enter="onExitChild"
          />
          <el-button type="primary" :loading="busy" @click="onExitChild">{{ $t('displayMode.exitAction') }}</el-button>
        </div>
        <div class="pin-error" :class="{ show: errorText }">{{ errorText }}</div>
      </template>
      <template v-else>
        <div class="pin-title">{{ $t('displayMode.childOnFor', { name: targetName }) }}</div>
        <div class="pin-row">
          <el-button :loading="busy" @click="onDisableFor">{{ $t('displayMode.disableFor') }}</el-button>
          <el-button type="primary" plain @click="openPinDialog">{{ $t('displayMode.resetPin') }}</el-button>
        </div>
      </template>
    </div>

    <div class="display-note">{{ $t('displayMode.note') }}</div>

    <!-- 设置退出密码:点「未成年模式」时弹出;成人/儿童都只能给自己设,家长可选目标成员。
         宽度走 class(dialog-sm = 420px):width="dialog-sm" 会把 --el-dialog-width 设成非法值,
         弹窗按 auto 宽度铺满内容栏(踩坑速查 §3.25) -->
    <el-dialog v-model="pinDialog" class="dialog-sm" :title="$t('displayMode.pinTitle')" append-to-body @closed="newPin = ''">
      <p class="dlg-tip">{{ $t('displayMode.setPinFor', { name: targetName }) }}</p>
      <el-input
        v-model="newPin"
        type="password"
        :maxlength="8"
        inputmode="numeric"
        show-password
        :placeholder="$t('displayMode.pinPlaceholder')"
        @keyup.enter="onSavePin"
      />
      <div class="pin-error" :class="{ show: pinMsg }">{{ pinMsg }}</div>
      <template #footer>
        <el-button @click="pinDialog = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="busy" @click="onSavePin">{{ $t('displayMode.pinSave') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { memberApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { useDisplayMode } from '@/composables/useDisplayMode'
import { isValidPin } from '@/utils/displayMode'

const { t } = useI18n()
const userStore = useUserStore()
const { mode, displayModeFor, setDisplayModeFor, enableChildMode, enableChildModeFor, disableChildMode } = useDisplayMode()

const myId = computed(() => userStore.userInfo?.id ?? null)
const isOwner = computed(() => userStore.isOwner)

const members = ref([])
const targetId = ref(myId.value)
// 别的成员的模式存在本机,改动后用版本号催一次重算
const version = ref(0)

const targetIsSelf = computed(() => String(targetId.value) === String(myId.value))
const targetName = computed(() => {
  if (targetIsSelf.value) return userStore.userInfo?.nickname || userStore.userInfo?.username || t('displayMode.selfName')
  return members.value.find((m) => String(m.id) === String(targetId.value))?.name || t('displayMode.selfName')
})
const memberOptions = computed(() => members.value)
const targetMode = computed(() => {
  version.value
  return targetIsSelf.value ? mode.value : displayModeFor(targetId.value).mode
})

const exitPin = ref('')
const newPin = ref('')
const busy = ref(false)
const pinDialog = ref(false)
const errorText = ref('')
const pinMsg = ref('')

const options = [
  { value: 'normal', labelKey: 'displayMode.normal', descKey: 'displayMode.normalDesc' },
  { value: 'elder', labelKey: 'displayMode.elder', descKey: 'displayMode.elderDesc' },
  { value: 'child', labelKey: 'displayMode.child', descKey: 'displayMode.childDesc' },
]

const openPinDialog = () => {
  newPin.value = ''
  pinMsg.value = ''
  pinDialog.value = true
}

// 选未成年:先弹窗设密码;本人已有密码时直接保持开启
const onPick = (value) => {
  errorText.value = ''
  if (value === 'child') {
    if (targetMode.value === 'child') return
    openPinDialog()
    return
  }
  if (targetMode.value === 'child' && targetIsSelf.value) {
    // 退出必须验密码,直接在下方退出框输入
    errorText.value = t('displayMode.exitTip')
    return
  }
  setDisplayModeFor(targetId.value, value)
  errorText.value = ''
  version.value++
}

const onSavePin = async () => {
  if (!isValidPin(newPin.value)) {
    pinMsg.value = t('displayMode.pinInvalid')
    return
  }
  busy.value = true
  try {
    const ok = targetIsSelf.value ? await enableChildMode(newPin.value) : await enableChildModeFor(targetId.value, newPin.value)
    if (!ok) {
      pinMsg.value = t('displayMode.pinFailed')
      return
    }
    pinDialog.value = false
    version.value++
    ElMessage.success(t('displayMode.childStarted'))
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
      version.value++
    } else {
      errorText.value = t('displayMode.wrongPin')
    }
  } finally {
    busy.value = false
  }
}

const onDisableFor = () => {
  setDisplayModeFor(targetId.value, 'normal')
  version.value++
  ElMessage.success(t('displayMode.disableDone'))
}

onMounted(async () => {
  if (targetId.value == null) targetId.value = myId.value
  if (!isOwner.value) return
  try {
    const list = await memberApi.list()
    members.value = (list || []).map((m) => ({
      id: m.id,
      name: m.nickname || m.username || String(m.id),
    }))
    if (!members.value.some((m) => String(m.id) === String(targetId.value))) targetId.value = myId.value
  } catch (e) {
    // 拉不到成员时只留自己,不影响本机设置
    members.value = []
  }
})

watch(myId, (id) => {
  if (targetId.value == null) targetId.value = id
})
watch(isOwner, (owner) => {
  if (!owner) {
    targetId.value = myId.value
    members.value = []
  }
})
</script>

<style scoped>
.settings-card {
  margin-bottom: 24px;
  background: var(--color-card);
}
.display-lead {
  margin: 0 0 12px;
}
.target-select {
  width: 220px;
  max-width: 100%;
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
  border-top: 1px dashed color-mix(in srgb, var(--color-border) 45%, var(--color-text-secondary));
}
.pin-title {
  font-weight: 600;
  margin-bottom: 4px;
}
.pin-tip,
.dlg-tip {
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
.settings-form .el-form-item {
  margin-bottom: 12px;
}
</style>
