<!-- 设置页:个人资料(昵称/头像/生日/性别) + 家庭设置(名称/简介/封面/访客公开/分享链接) -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: '设置' }]" />

    <div class="settings-grid">
      <div class="card settings-card">
        <h2>个人资料</h2>
        <el-form :model="profile" label-position="top">
          <el-form-item label="昵称">
            <el-input v-model="profile.nickname" />
          </el-form-item>
          <el-form-item label="头像">
            <div class="upload-row">
              <el-upload :show-file-list="false" :http-request="uploadAvatar" accept="image/*">
                <img v-if="profile.avatar" :src="profile.avatar" class="avatar-preview" alt="头像" />
                <div v-else class="uploader-btn">点击上传头像</div>
              </el-upload>
              <el-button v-if="profile.avatar" link type="danger" @click="profile.avatar = ''">移除</el-button>
            </div>
          </el-form-item>
          <el-form-item label="生日">
            <el-date-picker v-model="profile.birthday" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
          <el-form-item label="性别">
            <el-radio-group v-model="profile.gender">
              <el-radio :value="0">保密</el-radio>
              <el-radio :value="1">男</el-radio>
              <el-radio :value="2">女</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-button type="primary" :loading="profileSaving" @click="saveProfile">保存资料</el-button>
        </el-form>
      </div>

      <div class="card settings-card">
        <h2>家庭设置</h2>
        <el-form :model="family" label-position="top">
          <el-form-item label="家庭名称">
            <el-input v-model="family.name" />
          </el-form-item>
          <el-form-item label="家庭简介">
            <el-input v-model="family.description" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item label="封面图片">
            <div class="upload-row">
              <el-upload :show-file-list="false" :http-request="uploadCover" accept="image/*">
                <el-button>上传封面</el-button>
              </el-upload>
              <img v-if="family.coverImage" :src="family.coverImage" class="cover-preview" alt="封面" />
              <el-button v-if="family.coverImage" link type="danger" @click="family.coverImage = ''">移除</el-button>
            </div>
          </el-form-item>
          <el-form-item label="封面文字">
            <el-input v-model="family.coverText" />
          </el-form-item>
          <el-form-item label="封面副标题">
            <el-input v-model="family.coverSubtitle" />
          </el-form-item>
          <el-form-item label="对访客公开">
            <el-switch v-model="family.isPublic" :active-value="1" :inactive-value="0" />
          </el-form-item>
          <el-form-item label="家庭分享链接">
            <div class="share-row">
              <el-input v-model="shareUrl" readonly>
                <template #append>
                  <el-button @click="copyShare">复制</el-button>
                </template>
              </el-input>
            </div>
            <div class="share-tip">访问者通过该链接可浏览家庭公开内容(需家庭开启"对访客公开")</div>
          </el-form-item>
          <el-button type="primary" :loading="familySaving" @click="saveFamily">保存家庭设置</el-button>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { profileApi, familyApi, fileApi } from '@/api'
import { ElMessage } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb.vue'

const profile = reactive({ nickname: '', avatar: '', birthday: null, gender: 0 })
const family = reactive({ name: '', description: '', coverImage: '', coverText: '', coverSubtitle: '', isPublic: 1 })
const shareToken = ref('')
const profileSaving = ref(false)
const familySaving = ref(false)

// 家庭分享链接:使用 16 位混淆 token 而非裸家庭 ID,防 ID 遍历
const shareUrl = computed(() => {
  if (!shareToken.value) return ''
  const base = `${location.origin}${location.pathname}`.replace(/\/$/, '')
  return `${base}/?hid=${shareToken.value}`
})

// 页面挂载时分别拉取个人资料与家庭设置,回填表单
const load = async () => {
  try {
    const p = await profileApi.get()
    Object.assign(profile, { nickname: p.nickname || '', avatar: p.avatar || '', birthday: p.birthday || null, gender: p.gender ?? 0 })
  } catch (e) {
    // 忽略
  }
  try {
    const f = await familyApi.get()
    Object.assign(family, {
      name: f.name || '', description: f.description || '', coverImage: f.coverImage || '',
      coverText: f.coverText || '', coverSubtitle: f.coverSubtitle || '', isPublic: f.isPublic ?? 1,
    })
    shareToken.value = f.shareToken || ''
  } catch (e) {
    // 忽略
  }
}

// 保存个人资料(头像/封面通过上传后回填 URL 一并提交)
const saveProfile = async () => {
  profileSaving.value = true
  try {
    await profileApi.update({
      nickname: profile.nickname, avatar: profile.avatar,
      birthday: profile.birthday || null, gender: profile.gender,
    })
    ElMessage.success('资料已保存')
  } finally {
    profileSaving.value = false
  }
}

const saveFamily = async () => {
  familySaving.value = true
  try {
    await familyApi.update({ ...family })
    ElMessage.success('家庭设置已保存')
  } finally {
    familySaving.value = false
  }
}

const copyShare = async () => {
  try {
    await navigator.clipboard.writeText(shareUrl.value)
    ElMessage.success('链接已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}

// 上传头像/封面:拿到文件 URL 回填表单,由保存动作落库
const uploadAvatar = async (options) => {
  try {
    const data = await fileApi.upload(options.file)
    profile.avatar = data.url
    ElMessage.success('头像已上传，点击保存资料生效')
  } catch {
    ElMessage.error('头像上传失败')
  }
}

const uploadCover = async (options) => {
  try {
    const data = await fileApi.upload(options.file)
    family.coverImage = data.url
    ElMessage.success('封面已上传，点击保存家庭设置生效')
  } catch {
    ElMessage.error('封面上传失败')
  }
}

onMounted(load)
</script>

<style scoped>
.settings-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.settings-card { margin-bottom: 16px; }
.settings-card h2 { color: var(--color-primary); margin-bottom: 16px; font-size: 17px; }
.share-row { display: flex; align-items: center; gap: 8px; width: 100%; }
.share-tip { color: var(--color-text-secondary); font-size: 12px; margin-top: 4px; }
.upload-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.avatar-preview { width: 80px; height: 80px; border-radius: 50%; object-fit: cover; border: 1px solid var(--color-border); cursor: pointer; }
.uploader-btn { width: 80px; height: 80px; border-radius: 50%; border: 1px dashed var(--color-border); display: flex; align-items: center; justify-content: center; color: var(--color-text-secondary); font-size: 12px; text-align: center; cursor: pointer; background: var(--color-bg); }
.cover-preview { max-width: 180px; max-height: 90px; object-fit: cover; border-radius: 6px; border: 1px solid var(--color-border); }
@media (max-width: 900px) {
  .settings-grid { grid-template-columns: 1fr; }
}
</style>