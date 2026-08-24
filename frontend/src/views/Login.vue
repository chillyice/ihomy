<template>
  <div class="login-page">
    <!-- 背景色块(同首页风格) -->
    <div class="bg-blobs">
      <div class="blob" style="background:#9CD0B5; top:8%; left:6%; width:340px; height:340px;"></div>
      <div class="blob" style="background:#EDDB8C; top:55%; left:62%; width:300px; height:300px;"></div>
      <div class="blob" style="background:#ECC0AC; top:70%; left:12%; width:260px; height:260px;"></div>
      <div class="blob" style="background:#A8C9DE; top:15%; left:70%; width:280px; height:280px;"></div>
    </div>
    <div class="login-card">
      <div class="login-title">ihomy</div>
      <div class="login-sub">{{ isRegister ? $t('login.registerTitle') : $t('login.welcome') }}</div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-radio-group v-if="isRegister" v-model="regMode" class="reg-mode">
          <el-radio-button :value="'create'">{{ $t('login.createFamily') }}</el-radio-button>
          <el-radio-button :value="'join'">{{ $t('login.joinFamily') }}</el-radio-button>
        </el-radio-group>

        <el-form-item v-if="isRegister && regMode === 'create'" :label="$t('login.familyName')" prop="familyName">
          <el-input v-model="form.familyName" :placeholder="$t('login.familyNamePlaceholder')" />
        </el-form-item>
        <el-form-item v-if="isRegister && regMode === 'join'" :label="$t('login.inviteCode')" prop="inviteCode">
          <el-input v-model="form.inviteCode" :placeholder="$t('login.inviteCodePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('login.email')" prop="email">
          <el-input v-model="form.email" :placeholder="$t('login.emailPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('login.password')" prop="password">
          <el-input v-model="form.password" type="password" show-password :placeholder="$t('login.passwordPlaceholder')" />
        </el-form-item>
        <el-form-item v-if="isRegister" :label="$t('login.confirmPassword')" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password :placeholder="$t('login.confirmPasswordPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('login.captcha')" prop="captchaCode">
          <div class="captcha-row">
            <el-input v-model="form.captchaCode" :placeholder="$t('login.captchaPlaceholder')" @keyup.enter="onSubmit" />
            <img v-if="captchaImage" :src="captchaImage" class="captcha-img" alt="captcha" :title="$t('login.captchaRefresh')" @click="loadCaptcha" />
          </div>
        </el-form-item>

        <el-button type="primary" class="submit-btn" :loading="loading" @click="onSubmit">
          {{ isRegister ? $t('login.register') : $t('login.title') }}
        </el-button>
      </el-form>

      <div class="toggle" @click="toggleMode">
        {{ isRegister ? $t('login.hasAccount') : $t('login.noAccount') }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { authApi } from '@/api'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const { t } = useI18n()

const isRegister = ref(route.query.register === '1' || !!route.query.invite)
const regMode = ref(route.query.invite ? 'join' : 'create')
const loading = ref(false)
const formRef = ref()
const captchaId = ref('')
const captchaImage = ref('')

const _isReg = route.query.register === '1' || !!route.query.invite
const form = reactive({
  familyName: '',
  inviteCode: route.query.invite || '',
  email: _isReg ? '' : 'demo@ihomy.local',
  password: _isReg ? '' : 'guest123',
  confirmPassword: '',
  captchaCode: _isReg ? '' : 'qwer',
})

const rules = computed(() => ({
  email: [
    { required: true, message: t('login.emailRequired'), trigger: 'blur' },
    { type: 'email', message: t('login.emailInvalid'), trigger: 'blur' },
  ],
  password: [{ required: true, message: t('login.passwordRequired'), trigger: 'blur' }],
  confirmPassword: isRegister.value
    ? [
        { required: true, message: t('login.confirmPasswordRequired'), trigger: 'blur' },
        {
          validator: (r, v, cb) => (v === form.password ? cb() : cb(new Error(t('login.passwordMismatch')))),
          trigger: 'blur',
        },
      ]
    : [],
  captchaCode: [{ required: true, message: t('login.captchaRequired'), trigger: 'blur' }],
  familyName: isRegister.value && regMode.value === 'create' ? [{ required: true, message: t('login.familyNameRequired'), trigger: 'blur' }] : [],
  inviteCode: isRegister.value && regMode.value === 'join' ? [{ required: true, message: t('login.inviteCodeRequired'), trigger: 'blur' }] : [],
}))

// 加载图形验证码(登录/注册共用,进入页面即加载)
const loadCaptcha = async () => {
  try {
    const data = await authApi.captcha()
    captchaId.value = data.captchaId
    captchaImage.value = data.image
    form.captchaCode = ''
  } catch (e) {
    // 忽略
  }
}
loadCaptcha()

const toggleMode = () => {
  isRegister.value = !isRegister.value
  if (isRegister.value) { form.email = ''; form.password = ''; form.confirmPassword = ''; form.captchaCode = '' }
  else { form.email = 'demo@ihomy.local'; form.password = 'guest123'; form.captchaCode = 'qwer' }
  loadCaptcha()
}

const onSubmit = async () => {
  await formRef.value.validate()
  loading.value = true
  try {
    if (isRegister.value) {
      const payload = {
        email: form.email.trim(),
        password: form.password,
        confirmPassword: form.confirmPassword,
        captchaId: captchaId.value,
        captchaCode: form.captchaCode.trim(),
        familyName: regMode.value === 'create' ? form.familyName : '',
        inviteCode: regMode.value === 'join' ? form.inviteCode.trim() : '',
      }
      await userStore.register(payload)
      // 注册成功后不自动登录:跳转登录页,登录后才能访问自己的家庭
      ElMessage.success(regMode.value === 'create' ? t('login.createdHint') : t('login.joinedHint'))
      isRegister.value = false
      form.password = ''
      form.confirmPassword = ''
      form.captchaCode = ''
      loadCaptcha()
    } else {
      await userStore.login({
        email: form.email.trim(),
        password: form.password,
        captchaId: captchaId.value,
        captchaCode: form.captchaCode.trim(),
      })
      ElMessage.success(t('login.loginSuccess'))
      router.push(route.query.redirect || '/')
    }
  } catch (e) {
    // 验证码一次性:失败后强制刷新,避免用旧验证码反复试
    loadCaptcha()
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
/* 背景色块:同首页毛玻璃风格 */
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
  background: linear-gradient(135deg, #0F1A2E 0%, #162238 50%, #1A2540 100%);
}
html.dark .blob { opacity: 0.1; }
html.dark .login-card {
  background: rgba(30, 40, 65, 0.55);
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
.reg-mode {
  display: flex;
  margin-bottom: 18px;
}
.reg-mode .el-radio-button {
  flex: 1;
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
</style>
