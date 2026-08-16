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
          <el-menu-item index="weather">
            <span class="menu-icon">🌤️</span>天气
          </el-menu-item>
          <el-menu-item index="member" v-if="userStore.isOwner">
            <span class="menu-icon">👥</span>{{ $t('settings.cat.member') }}
          </el-menu-item>
          <el-menu-item index="storage">
            <span class="menu-icon">🗄️</span>{{ $t('settings.cat.storage') }}
          </el-menu-item>
          <el-menu-item index="light">
            <span class="menu-icon">🎨</span>个性化设置
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

          <!-- 家人共享歌单:所有家庭成员可添加曲目(上传或外链),右下角播放器全局播放 -->
          <div class="card settings-card">
            <h2>家人共享歌单</h2>
            <el-form label-position="top">
              <el-form-item label="添加曲目">
                <div class="upload-row">
                  <el-upload :show-file-list="false" :http-request="uploadMusic" accept="audio/*">
                    <el-button>上传音乐文件</el-button>
                  </el-upload>
                  <el-input v-model="newTrack.url" placeholder="或粘贴音频链接 https://..." style="flex:1" />
                  <el-input v-model="newTrack.title" placeholder="歌曲名" style="width:160px" />
                  <el-button type="primary" @click="addTrack">添加</el-button>
                </div>
              </el-form-item>
              <el-form-item v-if="playlist.length" label="歌单列表">
                <div class="playlist-mgmt">
                  <div v-for="(t, i) in playlist" :key="t.id || i" class="playlist-mgmt-item">
                    <span class="pl-idx">{{ i + 1 }}</span>
                    <span class="pl-title" :title="t.title">{{ t.title || '未知曲目' }}</span>
                    <span class="pl-url">{{ t.url }}</span>
                    <el-button text type="danger" size="small" @click="removeTrack(t)">删除</el-button>
                  </div>
                </div>
              </el-form-item>
              <el-empty v-else :description="playlistLoading ? '加载中...' : '歌单为空,添加第一首吧'" :image-size="50" />
              <div class="share-tip">保存后全家人右下角播放器可同步播放,支持上传音频文件或外链</div>
            </el-form>
          </div>

          <!-- 创建新家庭:放在家庭设置最下方 -->
          <div class="card settings-card">
            <h2>创建新家庭</h2>
            <p class="share-tip">创建一个新的家庭组,你将成为新家庭的家长(OWNER)。创建后自动切换到新家庭,可在顶栏切换回原家庭。</p>
            <el-button type="success" plain @click="createNewFamily">创建新家庭</el-button>
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
        <!-- 天气设置:地区偏好(避免 IP 定位不准) -->
        <template v-if="active === 'weather'">
          <div class="card settings-card">
            <h2>天气</h2>
            <el-form label-position="top">
              <el-form-item label="地区偏好">
                <div class="weather-loc-row">
                  <el-input v-model="weatherCity" placeholder="城市名(如:济南)" style="width: 160px" />
                  <el-input v-model="weatherLat" placeholder="纬度" style="width: 120px" />
                  <el-input v-model="weatherLng" placeholder="经度" style="width: 120px" />
                  <el-button @click="useIpLocation">使用 IP 定位</el-button>
                </div>
                <div class="share-tip">设置后天气和太阳位置将固定使用此坐标,留空则按 IP 自动定位。城市名用于天气面板显示。经纬度可从地图拾取(如 https://lbs.amap.com/tools/picker)</div>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="savingWeather" @click="saveWeather">保存</el-button>
              </el-form-item>
            </el-form>
          </div>
        </template>

        <template v-if="active === 'member'">
          <MemberView />
        </template>

        <!-- 存储管理(嵌入 Storage 页面组件) -->
        <template v-if="active === 'storage'">
          <StorageView />
        </template>

        <!-- 个性化设置:主题 + 台灯/色温/亮度/夜间超时关灯/光照测试入口 -->
        <template v-if="active === 'light'">
          <div class="card settings-card">
            <h2>个性化设置</h2>
            <el-form label-position="top">
              <el-form-item :label="$t('settings.theme')">
                <div class="theme-row">
                  <el-switch v-model="theme.autoMode" @change="onToggleAutoMode" active-text="日出日落自动切换" />
                  <el-radio-group v-if="!theme.autoMode" :model-value="theme.dark" @change="onChangeTheme">
                    <el-radio :value="false">{{ $t('theme.light') }}</el-radio>
                    <el-radio :value="true">{{ $t('theme.dark') }}</el-radio>
                  </el-radio-group>
                </div>
              </el-form-item>
              <el-divider />
              <el-form-item label="台灯模式">
                <el-radio-group :model-value="lampMode" @change="(v) => lampMode = v">
                  <el-radio value="auto">自动(夜间开灯/日间关灯)</el-radio>
                  <el-radio value="on">常开</el-radio>
                  <el-radio value="off">关闭</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="色温">
                <el-slider v-model.number="lampTemp" :min="0" :max="100" show-input />
                <div class="share-tip">0=暖光(橙黄),100=冷光(蓝白)</div>
              </el-form-item>
              <el-form-item label="亮度">
                <el-slider v-model.number="lampBrightness" :min="0" :max="100" show-input />
              </el-form-item>
              <el-form-item label="夜间超时关灯(分钟)">
                <el-input-number v-model.number="idleMinutes" :min="1" :max="120" :step="1" />
                <div class="share-tip">夜间无操作超过此时长后自动关灯,有操作时立即开灯(仅"自动"模式生效)</div>
                <div v-if="isIdle" class="share-tip" style="color: var(--color-accent)">当前状态:已超时关灯</div>
              </el-form-item>
              <el-form-item>
                <el-button @click="enterLightTest">进入光照测试</el-button>
              </el-form-item>
              <el-divider />
              <el-form-item label="面板布局">
                <el-button type="warning" plain @click="resetPanelLayout">恢复默认面板布局</el-button>
                <div class="share-tip">重置首页所有可拖动面板的位置和大小(家人动态/任务/天气/纪念日/今日)</div>
              </el-form-item>
            </el-form>
          </div>
        </template>
      </div>
    </div>
    <!-- 图片/视频预览 -->
    <!-- 头像裁剪对话框 -->
    <AvatarCropper ref="avatarCropperRef" @cropped="onAvatarCropped" />

    <!-- 新建身份标签对话框 -->
    <el-dialog v-model="showLabelDialog" :title="$t('settings.newLabel')" width="360px" append-to-body>
      <el-input v-model="newLabelName" :placeholder="$t('settings.labelPlaceholder')" @keyup.enter="addCustomLabel" />
      <template #footer>
        <el-button @click="showLabelDialog = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="addCustomLabel">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, inject, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { profileApi, familyApi, fileApi, musicApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import Breadcrumb from '@/components/Breadcrumb.vue'
import AvatarCropper from '@/components/AvatarCropper.vue'
import { applyLocale } from '@/i18n'
import { applyTheme, initTheme } from '@/theme'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import MemberView from '@/views/Member.vue'
import StorageView from '@/views/storage/Storage.vue'

const { locale, t } = useI18n()
const userStore = useUserStore()
const route = useRoute()
const router = useRouter()

// 光照设置:从全局 useSunLight 实例注入(与 SunLightLayer/AppSidebar 共享)
const sunLight = inject(SUN_LIGHT_KEY)
const { lampMode, lampTemp, lampBrightness, idleMinutes, isIdle } = sunLight || {}

// 当前选中设置大类;支持 ?tab= 跳转(从导航栏头像下拉"个人资料"进入时切到 profile)
const active = ref(route.query.tab || 'profile')
watch(() => route.query.tab, (tab) => {
  if (tab) active.value = tab
})

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
const onToggleAutoMode = (autoMode) => { theme.value = applyTheme({ ...theme.value, autoMode }) }

// 进入光照测试:跳转首页并启动测试模式
const enterLightTest = () => {
  if (sunLight) sunLight.startLightTest()
  router.push('/')
}

const profile = reactive({ nickname: '', avatar: '', birthday: null, gender: 0 })
const labelForm = reactive({ label: '', color: '#409EFF' })
const presets = ['爸爸', '妈妈']
const showLabelDialog = ref(false)
const newLabelName = ref('')

// 新建自定义身份标签:加入预设列表并选中
const addCustomLabel = () => {
  const name = newLabelName.value.trim()
  if (!name) return ElMessage.warning(t('settings.labelPlaceholder'))
  if (!presets.includes(name)) presets.push(name)
  labelForm.label = name
  newLabelName.value = ''
  showLabelDialog.value = false
}
const family = reactive({ name: '', description: '', coverImage: '', coverText: '', coverSubtitle: '', isPublic: 1, musicUrl: '', musicTitle: '' })
const shareToken = ref('')
const profileSaving = ref(false)
const familySaving = ref(false)
// 天气地区偏好(空=IP 自动定位)
const weatherCity = ref('')
const weatherLat = ref('')
const weatherLng = ref('')
const savingWeather = ref(false)
const useIpLocation = () => { weatherCity.value = ''; weatherLat.value = ''; weatherLng.value = '' }
const saveWeather = async () => {
  savingWeather.value = true
  try {
    await familyApi.update({
      weatherCity: weatherCity.value || null,
      weatherLat: weatherLat.value === '' ? null : Number(weatherLat.value),
      weatherLng: weatherLng.value === '' ? null : Number(weatherLng.value),
    })
    ElMessage.success('天气地区偏好已保存')
  } catch (e) {
    // 拦截器已提示
  } finally {
    savingWeather.value = false
  }
}

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
    weatherCity.value = f.weatherCity || ''
    weatherLat.value = f.weatherLat ?? ''
    weatherLng.value = f.weatherLng ?? ''
    shareToken.value = f.shareToken || ''
  } catch (e) {
    // 忽略
  }
  loadPlaylist()
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

// 家人共享歌单管理
const playlist = ref([])
const playlistLoading = ref(false)
const newTrack = reactive({ url: '', title: '' })
const loadPlaylist = async () => {
  if (!userStore.isLoggedIn) return
  playlistLoading.value = true
  try {
    playlist.value = await musicApi.list()
  } catch (e) {
    playlist.value = []
  } finally {
    playlistLoading.value = false
  }
}
const addTrack = async () => {
  if (!newTrack.url) return ElMessage.warning('请填写音频链接或上传文件')
  try {
    await musicApi.add({ url: newTrack.url, title: newTrack.title || null })
    newTrack.url = ''
    newTrack.title = ''
    ElMessage.success('已添加到歌单')
    await loadPlaylist()
  } catch (e) {
    // 拦截器已提示
  }
}
const removeTrack = async (t) => {
  try {
    await musicApi.remove(t.id)
    ElMessage.success('已删除')
    await loadPlaylist()
  } catch (e) {
    // 拦截器已提示
  }
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

// 恢复默认面板布局:清除 localStorage 中所有面板持久化记录,刷新页面生效
const resetPanelLayout = () => {
  ElMessageBox.confirm('确定恢复首页所有面板的默认位置和大小?当前自定义布局将被清除。', '恢复默认布局', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    const keys = Object.keys(localStorage).filter(k => k.startsWith('ihomy:panel:'))
    keys.forEach(k => localStorage.removeItem(k))
    localStorage.removeItem('ihomy:vinyl:pos')
    ElMessage.success('面板布局已重置,即将刷新...')
    setTimeout(() => location.reload(), 800)
  }).catch(() => {})
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
const uploadAvatar = onAvatarCropped  // 兼容旧引用

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
    const title = options.file.name.replace(/\.[^.]+$/, '')
    await musicApi.add({ url: data.url, title })
    ElMessage.success('已添加到歌单')
    await loadPlaylist()
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
.weather-loc-row { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.upload-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.avatar-preview { width: 80px; height: 80px; border-radius: 50%; object-fit: cover; border: 1px solid var(--color-border); cursor: pointer; }
.uploader-btn { width: 80px; height: 80px; border-radius: 50%; border: 1px dashed var(--color-border); display: flex; align-items: center; justify-content: center; color: var(--color-text-secondary); font-size: 12px; text-align: center; cursor: pointer; background: var(--color-bg); }
.cover-preview { max-width: 180px; max-height: 90px; object-fit: cover; border-radius: 6px; border: 1px solid var(--color-border); }
.theme-row { display: flex; flex-direction: column; gap: 10px; }
.music-name { color: var(--color-text-2); font-size: 13px; }
.music-audio { width: 100%; height: 36px; }
.music-actions { display: flex; gap: 8px; }
.playlist-mgmt { display: flex; flex-direction: column; gap: 6px; width: 100%; }
.playlist-mgmt-item { display: flex; align-items: center; gap: 10px; padding: 8px 12px; background: var(--color-card-2); border-radius: 8px; font-size: 13px; }
.playlist-mgmt-item .pl-idx { width: 20px; text-align: center; opacity: 0.5; }
.playlist-mgmt-item .pl-title { flex: 1; font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.playlist-mgmt-item .pl-url { font-size: 11px; color: var(--color-text-secondary); max-width: 200px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; opacity: 0.6; }
@media (max-width: 900px) {
  .settings-layout { flex-direction: column; }
  .settings-side { width: 100%; }
  .settings-menu { display: flex; }
  .settings-menu .el-menu-item { flex: 1; justify-content: center; }
}
</style>