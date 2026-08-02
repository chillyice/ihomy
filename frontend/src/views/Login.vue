<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-title">ihomy</div>
      <div class="login-sub">{{ isRegister ? '注册并创建家庭' : '欢迎回家，请登录' }}</div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item v-if="isRegister" label="家庭名称" prop="familyName">
          <el-input v-model="form.familyName" placeholder="如：张家的小院" />
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-form-item v-if="isRegister" label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="你的昵称" />
        </el-form-item>

        <el-button type="primary" class="submit-btn" :loading="loading" @click="onSubmit">
          {{ isRegister ? '注册' : '登录' }}
        </el-button>
      </el-form>

      <div class="toggle" @click="isRegister = !isRegister">
        {{ isRegister ? '已有账号？去登录' : '没有账号？去注册' }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const isRegister = ref(route.query.register === '1' || false)
const loading = ref(false)
const formRef = ref()

const form = reactive({
  familyName: '',
  username: '',
  password: '',
  nickname: '',
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  nickname: isRegister.value ? [{ required: true, message: '请输入昵称', trigger: 'blur' }] : [],
}

const onSubmit = async () => {
  await formRef.value.validate()
  loading.value = true
  try {
    if (isRegister.value) {
      await userStore.register(form)
    } else {
      await userStore.login({ username: form.username, password: form.password })
    }
    ElMessage.success(isRegister.value ? '注册成功' : '登录成功')
    router.push(route.query.redirect || '/')
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
