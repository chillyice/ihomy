<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-title">ihomy</div>
      <div class="login-sub">{{ isRegister ? '注册 ihomy 账号' : '欢迎回家，请登录' }}</div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-radio-group v-if="isRegister" v-model="regMode" class="reg-mode">
          <el-radio-button :value="'create'">创建家庭</el-radio-button>
          <el-radio-button :value="'join'">加入家庭</el-radio-button>
        </el-radio-group>

        <el-form-item v-if="isRegister && regMode === 'create'" label="家庭名称" prop="familyName">
          <el-input v-model="form.familyName" placeholder="如：张家的小院" />
        </el-form-item>
        <el-form-item v-if="isRegister && regMode === 'join'" label="邀请码" prop="inviteCode">
          <el-input v-model="form.inviteCode" placeholder="输入家人分享给你的邀请码" />
        </el-form-item>
        <el-form-item label="注册邮箱" prop="email">
          <el-input v-model="form.email" placeholder="邮箱即登录账号" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-form-item v-if="isRegister" label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password placeholder="请再次输入密码" />
        </el-form-item>
        <el-form-item label="验证码" prop="captchaCode">
          <div class="captcha-row">
            <el-input v-model="form.captchaCode" placeholder="输入右侧验证码（测试环境固定 qwer）" @keyup.enter="onSubmit" />
            <img v-if="captchaImage" :src="captchaImage" class="captcha-img" alt="验证码" title="点击刷新" @click="loadCaptcha" />
          </div>
        </el-form-item>

        <el-button type="primary" class="submit-btn" :loading="loading" @click="onSubmit">
          {{ isRegister ? '注册' : '登录' }}
        </el-button>
      </el-form>

      <div class="toggle" @click="toggleMode">
        {{ isRegister ? '已有账号？去登录' : '没有账号？去注册' }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { authApi } from '@/api'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const isRegister = ref(route.query.register === '1' || !!route.query.invite)
const regMode = ref(route.query.invite ? 'join' : 'create')
const loading = ref(false)
const formRef = ref()
const captchaId = ref('')
const captchaImage = ref('')

const form = reactive({
  familyName: '',
  inviteCode: route.query.invite || '',
  email: '',
  password: '',
  confirmPassword: '',
  captchaCode: '',
})

const rules = computed(() => ({
  email: [
    { required: true, message: '请输入注册邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  confirmPassword: isRegister.value
    ? [
        { required: true, message: '请再次输入密码', trigger: 'blur' },
        {
          validator: (r, v, cb) => (v === form.password ? cb() : cb(new Error('两次输入的密码不一致'))),
          trigger: 'blur',
        },
      ]
    : [],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
  familyName: isRegister.value && regMode.value === 'create' ? [{ required: true, message: '请输入家庭名称', trigger: 'blur' }] : [],
  inviteCode: isRegister.value && regMode.value === 'join' ? [{ required: true, message: '请输入邀请码', trigger: 'blur' }] : [],
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
      ElMessage.success(regMode.value === 'create' ? '家庭创建成功，请登录' : '已加入家庭，请登录')
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
      ElMessage.success('登录成功')
      router.push(route.query.redirect || '/')
    }
  } catch (e) {
    // 验证码一次性:失败后强制刷新,避免用旧验证码反复试
    loadCaptcha()
    throw e
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1F3A5F 0%, #2E74B5 100%);
  padding: 16px;
}
.login-card {
  width: 100%;
  max-width: 400px;
  background: #fff;
  border-radius: 16px;
  padding: 32px 28px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.2);
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
