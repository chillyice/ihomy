<!-- 设置页:左侧设置大类导航(个人资料/家庭设置/每日内容),点击大类切换右侧对应小类配置 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('settings.title') }]" />

    <div class="settings-layout">
      <aside class="settings-side">
        <el-menu :default-active="active" @select="(i) => (active = i)" class="settings-menu">
          <el-menu-item index="profile">
            <span class="menu-icon">👤</span>{{ $t('settings.cat.profile') }}
          </el-menu-item>
          <el-menu-item index="family">
            <span class="menu-icon">🏠</span>{{ $t('settings.cat.family') }}
          </el-menu-item>
          <el-menu-item index="daily">
            <span class="menu-icon">📅</span>{{ $t('settings.cat.daily') }}
          </el-menu-item>
          <el-menu-item index="member" v-if="userStore.isOwner">
            <span class="menu-icon">👥</span>{{ $t('settings.cat.member') }}
          </el-menu-item>
          <el-menu-item index="storage">
            <span class="menu-icon">🗄️</span>{{ $t('settings.cat.storage') }}
          </el-menu-item>
        </el-menu>
      </aside>

      <div class="settings-body">
        <!-- 个人资料大类:昵称/头像/生日/性别/身份标签 + 语言/主题 -->
        <template v-if="active === 'profile'">
          <div class="card settings-card">
            <h2>{{ $t('settings.profile') }}</h2>
            <el-form :model="profile" label-position="top">
              <el-form-item :label="$t('settings.nickname')">
                <el-input v-model="profile.nickname" />
              </el-form-item>
              <el-form-item :label="$t('settings.avatar')">
                <div class="upload-row">
                  <el-upload :show-file-list="false" :http-request="uploadAvatar" accept="image/*">
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
                  <el-select v-model="labelForm.label" filterable allow-create style="width: 180px" :placeholder="$t('settings.labelPlaceholder')">
                    <el-option v-for="p in presets" :key="p" :label="p" :value="p" />
                  </el-select>
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
              <el-form-item :label="$t('settings.theme')">
                <div class="theme-row">
                  <el-switch v-model="theme.autoMode" @change="onToggleAutoMode" active-text="日出日落自动切换" />
                  <el-radio-group v-if="!theme.autoMode" :model-value="theme.dark" @change="onChangeTheme">
                    <el-radio :value="false">{{ $t('theme.light') }}</el-radio>
                    <el-radio :value="true">{{ $t('theme.dark') }}</el-radio>
                  </el-radio-group>
                  <div class="theme-swatches">
                    <span
                      v-for="t in THEMES"
                      :key="t.key"
                      class="theme-dot"
                      :class="{ active: theme.theme === t.key }"
                      :style="{ background: t.accent }"
                      :title="$t('theme.presets.' + t.key)"
                      @click="changeThemeColor(t.key)"
                    ></span>
                  </div>
                </div>
              </el-form-item>
              <el-button type="primary" :loading="profileSaving" @click="saveProfile">{{ $t('settings.saveProfile') }}</el-button>
            </el-form>
          </div>
        </template>

        <!-- 家庭设置:基本信息 + 背景音乐(户主可编辑) -->
        <template v-if="active === 'family'">
          <div class="card settings-card">
            <h2>{{ $t('settings.family') }}</h2>
            <el-form :model="family" label-position="top">
              <el-form-item :label="$t('settings.familyName')">
                <el-input v-model="family.name" />
              </el-form-item>
              <el-form-item :label="$t('settings.description')">
                <el-input v-model="family.description" type="textarea" :rows="2" />
              </el-form-item>
              <el-form-item :label="$t('settings.cover')">
                <div class="upload-row">
                  <el-upload :show-file-list="false" :http-request="uploadCover" accept="image/*">
                    <el-button>{{ $t('settings.uploadCover') }}</el-button>
                  </el-upload>
                  <img v-if="family.coverImage" :src="family.coverImage" class="cover-preview" :alt="$t('settings.cover')" />
                  <el-button v-if="family.coverImage" link type="danger" @click="family.coverImage = ''">{{ $t('common.remove') }}</el-button>
                </div>
              </el-form-item>
              <el-form-item :label="$t('settings.coverText')">
                <el-input v-model="family.coverText" />
              </el-form-item>
              <el-form-item :label="$t('settings.coverSubtitle')">
                <el-input v-model="family.coverSubtitle" />
              </el-form-item>
              <el-form-item :label="$t('settings.visitorPublic')">
                <el-switch v-model="family.isPublic" :active-value="1" :inactive-value="0" />
              </el-form-item>
              <el-form-item :label="$t('settings.shareLink')">
                <div class="share-row">
                  <el-input v-model="shareUrl" readonly>
                    <template #append>
                      <el-button @click="copyShare">{{ $t('settings.copy') }}</el-button>
                    </template>
                  </el-input>
                </div>
                <div class="share-tip">{{ $t('settings.shareTip') }}</div>
              </el-form-item>
              <el-button type="primary" :loading="familySaving" @click="saveFamily">{{ $t('settings.saveFamily') }}</el-button>
            </el-form>
          </div>

          <!-- 创建新家庭:当前用户成为新家庭的 OWNER -->
          <div class="card settings-card">
            <h2>创建新家庭</h2>
            <p class="share-tip">创建一个新的家庭组,你将成为新家庭的家长(OWNER)。创建后自动切换到新家庭,可在顶栏切换回原家庭。</p>
            <el-button type="success" plain @click="createNewFamily">创建新家庭</el-button>
          </div>

          <!-- 背景音乐:仅户主(OWNER)可见;可上传音乐文件或填外链,保存后全家人全局播放 -->
          <div v-if="userStore.isOwner" class="card settings-card">
            <h2>{{ $t('music.title') }}</h2>
            <el-form label-position="top">
              <el-form-item :label="$t('music.upload')">
                <div class="upload-row">
                  <el-upload :show-file-list="false" :http-request="uploadMusic" accept="audio/*">
                    <el-button>{{ $t('music.uploadBtn') }}</el-button>
                  </el-upload>
                  <span v-if="family.musicTitle" class="music-name">{{ family.musicTitle }}</span>
                </div>
              </el-form-item>
              <el-form-item :label="$t('music.url')">
                <el-input v-model="family.musicUrl" :placeholder="$t('music.urlPlaceholder')" />
              </el-form-item>
              <el-form-item :label="$t('music.name')">
                <el-input v-model="family.musicTitle" :placeholder="$t('music.namePlaceholder')" />
              </el-form-item>
              <el-form-item v-if="family.musicUrl" :label="$t('music.preview')">
                <audio :src="family.musicUrl" controls preload="none" class="music-audio"></audio>
              </el-form-item>
              <div class="music-actions">
                <el-button type="primary" :loading="familySaving" @click="saveFamily">{{ $t('settings.saveFamily') }}</el-button>
                <el-button v-if="family.musicUrl" @click="removeMusic">{{ $t('music.remove') }}</el-button>
              </div>
              <div class="share-tip">{{ $t('music.tip') }}</div>
            </el-form>
          </div>
        </template>

        <!-- 每日内容 -->
        <template v-if="active === 'daily'">
          <div class="card settings-card">
            <h2>{{ $t('settings.dailyContent') }}</h2>
            <el-form label-position="top">
              <el-form-item :label="$t('settings.dailyImage')">
                <el-switch v-model="daily.imageOn" />
              </el-form-item>
              <el-form-item :label="$t('settings.dailyKnowledge')">
                <el-switch v-model="daily.knowledgeOn" />
              </el-form-item>
              <el-form-item :label="$t('settings.knowledgeTypes')">
                <el-checkbox-group v-model="daily.types">
                  <el-checkbox v-for="c in KNOWLEDGE_TYPES" :key="c.key" :value="c.key">
                    {{ $t('settings.knowledgeType.' + c.key) }}
                  </el-checkbox>
                </el-checkbox-group>
                <div class="share-tip">{{ $t('settings.knowledgeTip') }}</div>
              </el-form-item>
              <el-button type="primary" @click="saveDaily">{{ $t('settings.saveDaily') }}</el-button>
            </el-form>
          </div>
        </template>

        <!-- 成员管理(嵌入 Member 页面组件) -->
        <template v-if="active === 'member'">
          <MemberView />
        </template>

        <!-- 存储管理(嵌入 Storage 页面组件) -->
        <template v-if="active === 'storage'">
          <StorageView />
        </template>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { profileApi, familyApi, fileApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import Breadcrumb from '@/components/Breadcrumb.vue'
import { applyLocale } from '@/i18n'
import { applyTheme, initTheme, THEMES } from '@/theme'
import MemberView from '@/views/Member.vue'
import StorageView from '@/views/storage/Storage.vue'

const { locale, t } = useI18n()
const userStore = useUserStore()

// 当前选中设置大类
const active = ref('profile')

// 每日内容偏好:开启开关 + 知识分类,存 localStorage(纯前端展示偏好,不落库)
const KNOWLEDGE_TYPES = [
  { key: 'history' }, { key: 'science' }, { key: 'literature' }, { key: 'life' },
]
const daily = reactive(JSON.parse(localStorage.getItem('ihomy-daily') || '{}'))
daily.imageOn ??= true
daily.knowledgeOn ??= true
daily.types ??= ['history', 'life']
const saveDaily = () => {
  localStorage.setItem('ihomy-daily', JSON.stringify(daily))
  ElMessage.success(t('settings.dailySaved'))
}
const onChangeLang = (v) => applyLocale(v)

// 主题:明暗切换 / 主题色选择 / 日出日落自动切换,applyTheme 已含持久化
const theme = ref(initTheme())
const onChangeTheme = (dark) => { theme.value = applyTheme({ ...theme.value, dark }) }
const changeThemeColor = (key) => { theme.value = applyTheme({ ...theme.value, theme: key }) }
const onToggleAutoMode = (autoMode) => { theme.value = applyTheme({ ...theme.value, autoMode }) }

const profile = reactive({ nickname: '', avatar: '', birthday: null, gender: 0 })
const labelForm = reactive({ label: '', color: '#409EFF' })
const presets = ['爸爸', '妈妈']
const family = reactive({ name: '', description: '', coverImage: '', coverText: '', coverSubtitle: '', isPublic: 1, musicUrl: '', musicTitle: '' })
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
    // 身份标签独立接口拉取(未设置时 data 为 null)
    const l = await profileApi.label()
    if (l) Object.assign(labelForm, { label: l.label || '', color: l.color || '#409EFF' })
  } catch (e) {
    // 忽略
  }
  try {
    const f = await familyApi.get()
    Object.assign(family, {
      name: f.name || '', description: f.description || '', coverImage: f.coverImage || '',
      coverText: f.coverText || '', coverSubtitle: f.coverSubtitle || '', isPublic: f.isPublic ?? 1,
      musicUrl: f.musicUrl || '', musicTitle: f.musicTitle || '',
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
    const updated = await profileApi.update({
      nickname: profile.nickname, avatar: profile.avatar,
      birthday: profile.birthday || null, gender: profile.gender,
    })
    // 同步到全局 userStore,顶栏头像立即刷新
    if (updated) {
      userStore.userInfo = { ...userStore.userInfo, nickname: updated.nickname, avatar: updated.avatar, birthday: updated.birthday, gender: updated.gender }
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

const saveFamily = async () => {
  familySaving.value = true
  try {
    await familyApi.update({ ...family })
    ElMessage.success(t('settings.familySaved'))
  } finally {
    familySaving.value = false
  }
}

const removeMusic = async () => {
  family.musicUrl = ''
  family.musicTitle = ''
  await saveFamily()
}

const copyShare = async () => {
  try {
    await navigator.clipboard.writeText(shareUrl.value)
    ElMessage.success(t('settings.linkCopied'))
  } catch {
    ElMessage.error(t('settings.copyFailed'))
  }
}

const createNewFamily = async () => {
  try {
    const { value } = await ElMessageBox.prompt('请输入新家庭名称', '创建新家庭', {
      confirmButtonText: '创建',
      cancelButtonText: '取消',
      inputPattern: /.+/,
      inputErrorMessage: '家庭名称不能为空',
    })
    await familyApi.create({ name: value })
    ElMessage.success('家庭创建成功,已切换到新家庭')
    location.reload()
  } catch (e) {}
}

// 上传头像/封面/音乐:拿到文件 URL 回填表单,由保存动作落库
const uploadAvatar = async (options) => {
  try {
    const data = await fileApi.upload(options.file)
    profile.avatar = data.url
    ElMessage.success(t('settings.avatarUploaded'))
  } catch {
    ElMessage.error(t('settings.uploadFailed'))
  }
}

const uploadCover = async (options) => {
  try {
    const data = await fileApi.upload(options.file)
    family.coverImage = data.url
    ElMessage.success(t('settings.coverUploaded'))
  } catch {
    ElMessage.error(t('settings.uploadFailed'))
  }
}

const uploadMusic = async (options) => {
  try {
    const data = await fileApi.upload(options.file)
    family.musicUrl = data.url
    family.musicTitle = options.file.name.replace(/\.[^.]+$/, '')
    ElMessage.success(t('music.uploaded'))
  } catch {
    ElMessage.error(t('settings.uploadFailed'))
  }
}

onMounted(load)
</script>

<style scoped>
.settings-layout { display: flex; gap: 16px; align-items: flex-start; }
.settings-side { width: 180px; flex-shrink: 0; }
.settings-menu { border-right: none; border-radius: 10px; overflow: hidden; }
.menu-icon { margin-right: 8px; }
.settings-body { flex: 1; min-width: 0; }
.settings-card { margin-bottom: 16px; }
.settings-card h2 { color: var(--color-primary); margin-bottom: 16px; font-size: 17px; }
.label-row { display: flex; gap: 8px; align-items: center; }
.form-tip { color: var(--color-text-2); font-size: 12px; margin-top: 6px; }
.share-row { display: flex; align-items: center; gap: 8px; width: 100%; }
.share-tip { color: var(--color-text-secondary); font-size: 12px; margin-top: 4px; }
.upload-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.avatar-preview { width: 80px; height: 80px; border-radius: 50%; object-fit: cover; border: 1px solid var(--color-border); cursor: pointer; }
.uploader-btn { width: 80px; height: 80px; border-radius: 50%; border: 1px dashed var(--color-border); display: flex; align-items: center; justify-content: center; color: var(--color-text-secondary); font-size: 12px; text-align: center; cursor: pointer; background: var(--color-bg); }
.cover-preview { max-width: 180px; max-height: 90px; object-fit: cover; border-radius: 6px; border: 1px solid var(--color-border); }
.theme-row { display: flex; flex-direction: column; gap: 10px; }
.theme-swatches { display: flex; gap: 10px; }
.theme-dot { width: 22px; height: 22px; border-radius: 50%; cursor: pointer; border: 2px solid transparent; transition: transform 0.15s; }
.theme-dot:hover { transform: scale(1.15); }
.theme-dot.active { border-color: var(--color-text); }
.music-name { color: var(--color-text-2); font-size: 13px; }
.music-audio { width: 100%; height: 36px; }
.music-actions { display: flex; gap: 8px; }
@media (max-width: 900px) {
  .settings-layout { flex-direction: column; }
  .settings-side { width: 100%; }
  .settings-menu { display: flex; }
  .settings-menu .el-menu-item { flex: 1; justify-content: center; }
}
</style>