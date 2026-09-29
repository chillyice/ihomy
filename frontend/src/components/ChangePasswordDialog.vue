<template>
  <!-- 首登强制改密:种子账号(admin/ops)首次登录时弹出,改密前后端会限制其它接口 -->
  <el-dialog
    :model-value="userStore.mustChangePassword"
    :title="$t('passwordChange.title')"
    width="420px"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="false"
    append-to-body
  >
    <div class="pwd-tip">{{ $t('passwordChange.tip') }}</div>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item :label="$t('passwordChange.old')" prop="oldPassword">
        <el-input v-model="form.oldPassword" type="password" show-password :placeholder="$t('login.passwordPlaceholder')" />
      </el-form-item>
      <el-form-item :label="$t('passwordChange.new')" prop="newPassword">
        <el-input v-model="form.newPassword" type="password" show-password :placeholder="$t('passwordChange.length')" />
      </el-form-item>
      <el-form-item :label="$t('passwordChange.confirm')" prop="confirmPassword">
        <el-input v-model="form.confirmPassword" type="password" show-password @keyup.enter="submit" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="onLogout">{{ $t('passwordChange.logout') }}</el-button>
      <el-button type="primary" :loading="loading" @click="submit">{{ $t('passwordChange.submit') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const router = useRouter()
const route = useRoute()
const { t } = useI18n()

const formRef = ref()
const loading = ref(false)
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const rules = computed(() => ({
  oldPassword: [{ required: true, message: t('passwordChange.oldRequired'), trigger: 'blur' }],
  newPassword: [
    { required: true, message: t('passwordChange.newRequired'), trigger: 'blur' },
    { min: 6, max: 30, message: t('passwordChange.length'), trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: t('passwordChange.confirmRequired'), trigger: 'blur' },
    {
      validator: (r, v, cb) => (v === form.newPassword ? cb() : cb(new Error(t('passwordChange.mismatch')))),
      trigger: 'blur',
    },
  ],
}))

const submit = async () => {
  await formRef.value.validate()
  loading.value = true
  try {
    await userStore.changePassword({ oldPassword: form.oldPassword, newPassword: form.newPassword })
    ElMessage.success(t('passwordChange.success'))
    form.oldPassword = ''
    form.newPassword = ''
    form.confirmPassword = ''
    // 改密前若停在登录页,改完进入首页(家庭聚合数据此时才允许加载)
    if (route.path === '/login') router.push(route.query.redirect || '/')
  } catch (e) {
    // 错误提示由请求拦截器统一弹出
  } finally {
    loading.value = false
  }
}

const onLogout = () => {
  userStore.logout()
  router.replace('/login')
}
</script>

<style scoped>
.pwd-tip {
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 1.6;
  margin-bottom: 16px;
}
</style>
