<template>
  <!-- 个人资料大类:昵称/头像/生日/性别/身份标签 + 语言/主题 -->
  <div class="card settings-card">
    <div class="section-label">{{ $t('settings.profile') }}</div>
    <el-form :model="profile" label-position="top">
      <el-form-item :label="$t('settings.nickname')">
        <el-input v-model="profile.nickname" />
      </el-form-item>
      <el-form-item :label="$t('settings.avatar')">
        <div class="upload-row">
          <el-upload :show-file-list="false" :http-request="onAvatarFileSelected" accept="image/*">
            <img v-if="profile.avatar" :src="profile.avatar" class="avatar-preview" :alt="$t('settings.avatar')" />
            <div v-else class="uploader-btn">{{ $t('settings.uploadAvatar') }}</div>
          </el-upload>
          <el-button v-if="profile.avatar" link type="danger" @click="profile.avatar = ''">{{ $t('common.remove') }}</el-button>
        </div>
      </el-form-item>
      <el-form-item :label="$t('settings.birthday')">
        <el-date-picker v-model="profile.birthday" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </el-form-item>
      <el-form-item :label="$t('settings.gender')">
        <el-radio-group v-model="profile.gender">
          <el-radio :value="0">{{ $t('settings.secret') }}</el-radio>
          <el-radio :value="1">{{ $t('settings.male') }}</el-radio>
          <el-radio :value="2">{{ $t('settings.female') }}</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item :label="$t('settings.label')">
        <div class="label-row">
          <el-select v-model="labelForm.label" filterable clearable style="width: 180px" :placeholder="$t('settings.labelPlaceholder')">
            <el-option v-for="p in presets" :key="p" :label="p" :value="p" />
          </el-select>
          <el-button @click="showLabelDialog = true">+ {{ $t('settings.newLabel') }}</el-button>
          <el-color-picker v-model="labelForm.color" />
          <el-button v-if="labelForm.label" link type="danger" @click="clearLabel">{{ $t('common.cancel') }}</el-button>
        </div>
        <div class="form-tip">{{ $t('settings.labelHint') }}</div>
      </el-form-item>
      <el-form-item :label="$t('settings.language')">
        <el-radio-group :model-value="locale" @change="onChangeLang">
          <el-radio value="zh-CN">中文</el-radio>
          <el-radio value="en">English</el-radio>
        </el-radio-group>
      </el-form-item>
      <div class="form-footer">
        <el-button type="primary" :loading="profileSaving" @click="saveProfile">{{ $t('settings.saveProfile') }}</el-button>
      </div>
    </el-form>
  </div>

  <!-- 退出登录:个人设置最下方 -->
  <div class="card settings-card">
    <div class="section-label">{{ $t('nav.logout') }}</div>
    <p class="share-tip">{{ $t('settings.logoutHint') }}</p>
    <el-button type="danger" plain @click="onLogout">{{ $t('nav.logout') }}</el-button>
  </div>

  <!-- 头像裁剪对话框 -->
  <AvatarCropper
    ref="avatarCropperRef"
    :title="$t('settings.avatarCropTitle')"
    :cancel-text="$t('common.cancel')"
    :confirm-text="$t('common.confirm')"
    @cropped="onAvatarCropped"
  />

  <!-- 新建身份标签对话框 -->
  <el-dialog v-model="showLabelDialog" :title="$t('settings.newLabel')" width="360px" append-to-body>
    <el-input v-model="newLabelName" :placeholder="$t('settings.labelPlaceholder')" @keyup.enter="addCustomLabel" />
    <template #footer>
      <el-button @click="showLabelDialog = false">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" :disabled="!newLabelName.trim()" @click="addCustomLabel">{{ $t('common.confirm') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { profileApi, fileApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import AvatarCropper from '@/components/AvatarCropper.vue'
import { applyLocale } from '@/i18n'

const { locale, t } = useI18n()
const userStore = useUserStore()

const profile = reactive({ nickname: '', avatar: '', birthday: null, gender: 0 })
const labelForm = reactive({ label: '', color: '#C9807A' })
const presets = [t('settings.presetDad'), t('settings.presetMom')]
const showLabelDialog = ref(false)
const newLabelName = ref('')
const profileSaving = ref(false)

const onChangeLang = (v) => applyLocale(v)

// 新建自定义身份标签:加入预设列表并选中
const addCustomLabel = () => {
  const name = newLabelName.value.trim()
  if (!name) return ElMessage.warning(t('settings.labelPlaceholder'))
  if (!presets.includes(name)) presets.push(name)
  labelForm.label = name
  newLabelName.value = ''
  showLabelDialog.value = false
}

// 保存个人资料(头像/封面通过上传后回填 URL 一并提交)
const saveProfile = async () => {
  profileSaving.value = true
  try {
    const updated = await profileApi.update({
      nickname: profile.nickname,
      avatar: profile.avatar,
      birthday: profile.birthday || null,
      gender: profile.gender,
    })
    // 同步到全局 userStore,顶栏头像立即刷新
    if (updated) {
      userStore.userInfo = {
        ...userStore.userInfo,
        nickname: updated.nickname,
        avatar: updated.avatar,
        birthday: updated.birthday,
        gender: updated.gender,
      }
      localStorage.setItem('userInfo', JSON.stringify(userStore.userInfo))
    }
    // 身份标签与资料分开保存(接口独立),有值才提交
    if (labelForm.label) await profileApi.saveLabel({ label: labelForm.label, color: labelForm.color })
    ElMessage.success(t('settings.profileSaved'))
  } finally {
    profileSaving.value = false
  }
}

// 取消身份标签
const clearLabel = async () => {
  labelForm.label = ''
  await profileApi.removeLabel()
  ElMessage.success(t('settings.labelRemoved'))
}

// 退出登录:个人设置最下方,确认后登出并刷新回未登录首页
const onLogout = () => {
  ElMessageBox.confirm(t('settings.logoutConfirm'), t('nav.logout'), {
    confirmButtonText: t('common.confirm'),
    cancelButtonText: t('common.cancel'),
    type: 'warning',
    closeOnClickModal: true,
  })
    .then(() => {
      userStore.logout()
      location.reload()
    })
    .catch(() => {})
}

// 头像上传:先弹裁剪框,裁剪后再上传
const avatarCropperRef = ref(null)
const onAvatarFileSelected = (options) => {
  avatarCropperRef.value?.open(options.file)
}
const onAvatarCropped = async (file) => {
  try {
    const data = await fileApi.upload(file)
    profile.avatar = data.url
    ElMessage.success(t('settings.avatarUploaded'))
  } catch {
    ElMessage.error(t('settings.uploadFailed'))
  }
}

// 页面挂载时并行拉取个人资料与身份标签(两者相互独立,各自 catch 互不影响)
onMounted(async () => {
  const [p, l] = await Promise.all([profileApi.get().catch(() => null), profileApi.label().catch(() => null)])
  if (p) Object.assign(profile, { nickname: p.nickname || '', avatar: p.avatar || '', birthday: p.birthday || null, gender: p.gender ?? 0 })
  // 身份标签独立接口拉取(未设置时 data 为 null)
  if (l) Object.assign(labelForm, { label: l.label || '', color: l.color || '#C9807A' })
})
</script>

<style scoped>
.settings-card {
  margin-bottom: 16px;
  background: var(--color-card);
}
.label-row {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.form-tip {
  color: #776e62;
  font-size: 12px;
  margin-top: 8px;
  line-height: 1.4;
  width: 100%;
}
.share-tip {
  color: #776e62;
  font-size: 12px;
  margin-top: 8px;
  line-height: 1.4;
  width: 100%;
}
html.dark .form-tip,
html.dark .share-tip {
  color: #9a9088;
}
.upload-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
/* 表单提交按钮:右下角对齐 */
.form-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 4px;
}
.avatar-preview {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid var(--color-border);
  cursor: pointer;
}
.uploader-btn {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  border: 1px dashed var(--color-border);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-secondary);
  font-size: 12px;
  text-align: center;
  cursor: pointer;
  background: var(--color-bg);
}
@media (max-width: 768px) {
  .settings-card .el-input,
  .settings-card .el-select {
    width: 100%;
  }
}
</style>
