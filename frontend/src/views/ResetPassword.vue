<template>
  <div class="login-page">
    <div class="bg-blobs">
      <div class="blob" style="background:#9CD0B5; top:8%; left:6%; width:340px; height:340px;"></div>
      <div class="blob" style="background:#EDDB8C; top:55%; left:62%; width:300px; height:300px;"></div>
      <div class="blob" style="background:#ECC0AC; top:70%; left:12%; width:260px; height:260px;"></div>
      <div class="blob" style="background:#A8C9DE; top:15%; left:70%; width:280px; height:280px;"></div>
    </div>
    <div class="login-card">
      <div class="login-title">ihomy</div>
      <div class="login-sub">{{ hasToken ? $t('passwordReset.resetTitle') : $t('passwordReset.forgotTitle') }}</div>

      <!-- 邮件链接进入:设置新密码 -->
      <el-form v-if="hasToken" ref="resetFormRef" :model="resetForm" :rules="resetRules" label-position="top">
        <el-form-item :label="$t('passwordReset.newPassword')" prop="password">
          <el-input v-model="resetForm.password" type="password" show-password :placeholder="$t('passwordReset.newPasswordPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('passwordReset.confirmPassword')" prop="confirmPassword">
          <el-input v-model="resetForm.confirmPassword" type="password" show-password :placeholder="$t('passwordReset.confirmPasswordPlaceholder')" @keyup.enter="onReset" />
        </el-form-item>
        <el-button type="primary" class="submit-btn" :loading="loading" @click="onReset">{{ $t('passwordReset.submitReset') }}</el-button>
      </el-form>

      <!-- 登录页「忘记密码」进入:输入邮箱 + 验证码,发送重置邮件 -->
      <template v-else>
        <div class="hint">{{ $t('passwordReset.intro') }}</div>
        <el-form ref="forgotFormRef" :model="forgotForm" :rules="forgotRules" label-position="top">
          <el-form-item :label="$t('login.email')" prop="email">
            <el-input v-model="forgotForm.email" :placeholder="$t('login.emailPlaceholder')" />
          </el-form-item>
          <el-form-item :label="$t('login.captcha')" prop="captchaCode">
            <div class="captcha-row">
              <el-input v-model="forgotForm.captchaCode" :placeholder="$t('login.captchaPlaceholder')" @keyup.enter="onForgot" />
              <img v-a11y-click v-if="captchaImage" :src="captchaImage" class="captcha-img" alt="captcha" :title="$t('login.captchaRefresh')" @click="loadCaptcha" />
            </div>
          </el-form-item>
          <el-button type="primary" class="submit-btn" :loading="loading" @click="onForgot">{{ $t('passwordReset.sendMail') }}</el-button>
        </el-form>
      </template>

      <div v-a11y-click class="toggle" @click="router.push('/login')">{{ $t('passwordReset.backToLogin') }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { authApi } from '@/api'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()

const token = typeof route.query.token === 'string' ? route.query.token : ''
const hasToken = computed(() => !!token)

const loading = ref(false)
const forgotFormRef = ref()
const resetFormRef = ref()
const captchaId = ref('')
const captchaImage = ref('')

const forgotForm = reactive({ email: '', captchaCode: '' })
const resetForm = reactive({ password: '', confirmPassword: '' })

const forgotRules = computed(() => ({
  email: [
    { required: true, message: t('login.emailRequired'), trigger: 'blur' },
    { type: 'email', message: t('login.emailInvalid'), trigger: 'blur' },
  ],
  captchaCode: [{ required: true, message: t('login.captchaRequired'), trigger: 'blur' }],
}))

const resetRules = computed(() => ({
  password: [
    { required: true, message: t('passwordReset.passwordLength'), trigger: 'blur' },
    { min: 6, max: 30, message: t('passwordReset.passwordLength'), trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: t('passwordReset.mismatch'), trigger: 'blur' },
    { validator: (r, v, cb) => (v === resetForm.password ? cb() : cb(new Error(t('passwordReset.mismatch')))), trigger: 'blur' },
  ],
}))

const loadCaptcha = async () => {
  try {
    const data = await authApi.captcha()
    captchaId.value = data.captchaId
    captchaImage.value = data.image
    forgotForm.captchaCode = ''
  } catch (e) {
    // 忽略
  }
}
if (!hasToken.value) loadCaptcha()

const onForgot = async () => {
  await forgotFormRef.value.validate()
  loading.value = true
  try {
    await authApi.forgotPassword({
      email: forgotForm.email.trim(),
      captchaId: captchaId.value,
      captchaCode: forgotForm.captchaCode.trim(),
    })
    ElMessage.success(t('passwordReset.sent'))
    loadCaptcha()
  } catch (e) {
    // 验证码一次性:失败后刷新
    loadCaptcha()
  } finally {
    loading.value = false
  }
}

const onReset = async () => {
  await resetFormRef.value.validate()
  loading.value = true
  try {
    await authApi.resetPassword({ token, password: resetForm.password })
    ElMessage.success(t('passwordReset.resetSuccess'))
    router.push('/login')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #EDE4D3 0%, #E2D8C4 50%, #D6CBB4 100%);
  padding: 16px;
  overflow: hidden;
}
.bg-blobs { position: absolute; inset: 0; z-index: 0; pointer-events: none; }
.blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.4;
}
.login-card {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 400px;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(20px) saturate(1.4);
  -webkit-backdrop-filter: blur(20px) saturate(1.4);
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 16px;
  padding: 32px 28px;
  box-shadow: 0 12px 40px rgba(58, 46, 34, 0.15);
}
html.dark .login-page {
  background: linear-gradient(135deg, var(--color-bg) 0%, var(--color-bg-2) 50%, var(--color-card) 100%);
}
html.dark .blob { opacity: 0.1; }
html.dark .login-card {
  background: rgba(var(--color-card-rgb), 0.55);
  border-color: rgba(255, 255, 255, 0.12);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.4);
}
.login-title {
  font-size: 26px;
  font-weight: 700;
  color: var(--color-primary);
  text-align: center;
}
.login-sub {
  text-align: center;
  color: var(--color-text-secondary);
  margin: 8px 0 24px;
  font-size: 14px;
}
.hint {
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 1.7;
  margin-bottom: 16px;
}
.captcha-row {
  display: flex;
  gap: 10px;
  width: 100%;
}
.captcha-img {
  height: 32px;
  border-radius: 4px;
  cursor: pointer;
  border: 1px solid var(--color-border);
}
.submit-btn {
  width: 100%;
  margin-top: 8px;
}
.toggle {
  text-align: center;
  margin-top: 18px;
  color: var(--color-accent);
  font-size: 13px;
  cursor: pointer;
}

@media (max-width: 768px) {
  .login-card { width: 92vw !important; max-width: 400px; padding: 24px 20px; }
  .login-title { font-size: 22px; }
}
</style>
