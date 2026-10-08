<template>
  <!-- 放映厅:我的播放档案(每位成员自助)+ 引擎配置(家长) -->
  <div v-if="mediaMine.engineReady" class="card settings-card">
    <div class="section-label">{{ $t('settings.mediaMine.title') }}</div>
    <div class="share-tip">{{ $t('settings.mediaMine.hint') }}</div>

    <div class="media-status">
      <el-tag :type="mediaMineTag.type" size="small">{{ mediaMineTag.text }}</el-tag>
      <span v-if="mediaMine.configured" class="media-status-sub">{{ mediaMine.username }}</span>
      <span v-if="mediaMine.message" class="media-status-sub">{{ mediaMine.message }}</span>
    </div>

    <el-form :model="mediaMineForm" label-position="top" class="settings-form">
      <el-form-item :label="$t('settings.mediaMine.username')">
        <el-input v-model="mediaMineForm.username" />
      </el-form-item>
      <el-form-item :label="$t('settings.mediaMine.password')">
        <el-input
          v-model="mediaMineForm.password"
          type="password"
          show-password
          :placeholder="mediaMine.configured ? $t('settings.media.passwordKeep') : $t('settings.mediaMine.passwordHint')"
        />
      </el-form-item>
      <div class="media-actions">
        <el-button type="primary" :loading="mediaMineSaving" @click="saveMediaMine">{{ $t('common.save') }}</el-button>
        <el-button v-if="mediaMine.configured" type="danger" plain @click="removeMediaMine">
          {{ $t('settings.mediaMine.remove') }}
        </el-button>
      </div>
    </el-form>
  </div>
  <div v-else-if="!userStore.hasPerm('storage:manage')" class="card settings-card">
    <div class="share-tip">{{ $t('settings.media.ownerOnly') }}</div>
  </div>

  <div v-if="userStore.hasPerm('storage:manage')" class="card settings-card" v-loading="mediaLoading">
    <div class="section-label">{{ $t('settings.media.title') }}</div>
    <div class="share-tip">{{ $t('settings.media.hint') }}</div>

    <div class="media-status">
      <el-tag :type="mediaStatusTag.type" size="small">{{ mediaStatusTag.text }}</el-tag>
      <span v-if="mediaCfg.lastConnectedAt" class="media-status-sub">
        {{ $t('settings.media.lastConnected') }}{{ formatDateTime(mediaCfg.lastConnectedAt) }}
      </span>
      <span v-if="mediaStatus.connected && mediaStatus.counts" class="media-status-sub">
        {{
          $t('settings.media.counts', {
            m: mediaStatus.counts.movies || 0,
            s: mediaStatus.counts.series || 0,
            e: mediaStatus.counts.episodes || 0,
          })
        }}
      </span>
    </div>
    <div v-if="mediaStatus.connected && mediaStatus.libraries?.length" class="media-libs">
      <el-tag v-for="l in mediaStatus.libraries" :key="l.name" size="small" type="info">{{ l.name }}</el-tag>
    </div>

    <el-form :model="mediaForm" label-position="top" class="settings-form">
      <el-form-item :label="$t('settings.media.serverType')">
        <el-select v-model="mediaForm.serverType" style="width: 100%">
          <el-option :label="$t('dict.media_server_type.JELLYFIN')" value="JELLYFIN" />
          <el-option :label="$t('dict.media_server_type.EMBY')" value="EMBY" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('settings.media.serverUrl')">
        <el-input v-model="mediaForm.serverUrl" placeholder="http://192.168.1.10:8096" />
        <div class="share-tip">{{ $t('settings.media.serverUrlHint') }}</div>
      </el-form-item>
      <el-form-item :label="$t('settings.media.publicUrl')">
        <el-input v-model="mediaForm.publicUrl" placeholder="http://192.168.1.10:8096" />
        <div class="share-tip">{{ $t('settings.media.publicUrlHint') }}</div>
      </el-form-item>
      <el-alert
        v-if="mediaUrlInsecure"
        type="warning"
        :closable="false"
        show-icon
        :title="$t('settings.media.insecureUrl')"
        style="margin-bottom: 18px"
      />
      <el-form-item :label="$t('settings.media.username')">
        <el-input v-model="mediaForm.username" />
      </el-form-item>
      <el-form-item :label="$t('settings.media.password')">
        <el-input
          v-model="mediaForm.password"
          type="password"
          show-password
          :placeholder="mediaCfg.hasPassword ? $t('settings.media.passwordKeep') : ''"
        />
      </el-form-item>
      <el-form-item>
        <div class="setting-row">
          <el-switch v-model="mediaForm.enabled" />
          <span class="setting-label">{{ $t('settings.media.enabled') }}</span>
        </div>
      </el-form-item>
      <div class="media-actions">
        <el-button type="primary" :loading="mediaSaving" @click="saveMediaConfig">{{ $t('common.save') }}</el-button>
        <el-button :loading="mediaTesting" @click="testMediaConfig">{{ $t('settings.media.test') }}</el-button>
        <el-button v-if="mediaCfg.configured" type="danger" plain @click="removeMediaConfig">{{ $t('settings.media.remove') }}</el-button>
      </div>
    </el-form>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { mediaApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { formatDateTime } from '@/utils/datetime'

const { t } = useI18n()
const userStore = useUserStore()

// ===== 放映厅引擎(家庭媒体服务器,家长可管理) =====
const mediaCfg = reactive({
  configured: false,
  serverType: 'JELLYFIN',
  serverUrl: '',
  publicUrl: '',
  username: '',
  hasPassword: false,
  enabled: true,
  lastConnectedAt: null,
})
const mediaStatus = reactive({ configured: false, connected: false, enabled: true, libraries: [], counts: null })
const mediaForm = reactive({ serverType: 'JELLYFIN', serverUrl: '', publicUrl: '', username: '', password: '', enabled: true })
const mediaLoading = ref(false)
const mediaSaving = ref(false)
const mediaTesting = ref(false)

const mediaStatusTag = computed(() => {
  if (!mediaCfg.configured) return { type: 'info', text: t('settings.media.stateUnconfigured') }
  if (mediaCfg.enabled === false) return { type: 'info', text: t('settings.media.stateDisabled') }
  if (mediaStatus.connected) return { type: 'success', text: t('settings.media.stateConnected') }
  return { type: 'warning', text: t('settings.media.stateOffline') }
})

// 站点走 HTTPS 时,http 的媒体地址会被浏览器按混合内容拦掉(直出视频与转码取流都拦),
// 先提示一句,免得去翻播放失败的日志
const mediaUrlInsecure = computed(() => {
  if (window.location.protocol !== 'https:') return false
  return [mediaForm.serverUrl, mediaForm.publicUrl].some((u) => /^http:\/\//i.test((u || '').trim()))
})

// ===== 我的播放档案(成员用自己的媒体服务器账号,各自续看) =====
const mediaMine = reactive({ configured: false, usable: false, engineReady: false, username: '', message: null })
const mediaMineForm = reactive({ username: '', password: '' })
const mediaMineSaving = ref(false)

const mediaMineTag = computed(() => {
  if (!mediaMine.configured) return { type: 'info', text: t('settings.mediaMine.stateOff') }
  if (mediaMine.usable) return { type: 'success', text: t('settings.mediaMine.stateOn') }
  return { type: 'warning', text: t('settings.mediaMine.stateBad') }
})

const saveMediaMine = async () => {
  if (!mediaMineForm.username) return ElMessage.warning(t('settings.media.usernameRequired'))
  mediaMineSaving.value = true
  try {
    const r = await mediaApi.saveMyAccount({ ...mediaMineForm })
    if (r.usable) ElMessage.success(t('settings.mediaMine.savedOk'))
    else ElMessage.warning(r.message || t('settings.mediaMine.savedBad'))
    mediaMineForm.password = ''
    await loadMediaConfig()
  } finally {
    mediaMineSaving.value = false
  }
}

const removeMediaMine = async () => {
  await ElMessageBox.confirm(t('settings.mediaMine.removeConfirm'), t('common.tip'), { type: 'warning', closeOnClickModal: true })
  await mediaApi.removeMyAccount()
  ElMessage.success(t('common.deleted'))
  mediaMineForm.password = ''
  await loadMediaConfig()
}

const loadMediaConfig = async () => {
  mediaLoading.value = true
  try {
    // 我的播放档案:每位成员都读(接口未配置引擎时回 engineReady=false)
    try {
      const mine = await mediaApi.myAccount()
      Object.assign(mediaMine, mine)
      if (!mediaMineForm.username) mediaMineForm.username = mine.username || ''
    } catch {
      /* 拿不到就按未配置渲染,不打断设置页其它区块 */
    }
    if (!userStore.hasPerm('storage:manage')) return
    const cfg = await mediaApi.config()
    Object.assign(mediaCfg, cfg)
    Object.assign(mediaForm, {
      serverType: cfg.serverType || 'JELLYFIN',
      serverUrl: cfg.serverUrl || '',
      publicUrl: cfg.publicUrl || '',
      username: cfg.username || '',
      password: '',
      enabled: cfg.enabled !== false,
    })
    Object.assign(mediaStatus, { serverName: null, version: null, libraries: [], counts: null }, await mediaApi.status())
  } catch {
    // 引擎配置读取失败(权限变更/后端未就绪):保持上一次的展示,不产生未捕获拒绝
  } finally {
    mediaLoading.value = false
  }
}

// 保存:密码留空表示沿用已保存的密码;保存后顺带告知是否已能连上
const saveMediaConfig = async () => {
  if (!mediaForm.serverUrl) return ElMessage.warning(t('settings.media.serverUrlRequired'))
  if (!mediaForm.username) return ElMessage.warning(t('settings.media.usernameRequired'))
  if (!mediaCfg.configured && !mediaForm.password) return ElMessage.warning(t('settings.media.passwordRequired'))
  mediaSaving.value = true
  try {
    const r = await mediaApi.saveConfig({ ...mediaForm })
    ElMessage.success(r.connected ? t('settings.media.savedConnected') : t('settings.media.savedOffline'))
    await loadMediaConfig()
  } finally {
    mediaSaving.value = false
  }
}

// 连通测试:用表单里当前填的值试一次(未保存也能测)
const testMediaConfig = async () => {
  mediaTesting.value = true
  try {
    const r = await mediaApi.test({ ...mediaForm })
    if (r.ok) ElMessage.success(t('settings.media.testOk', { name: r.serverName || '', version: r.version || '' }))
    else ElMessage.error(r.message || t('settings.media.testFail'))
  } finally {
    mediaTesting.value = false
  }
}

const removeMediaConfig = async () => {
  await ElMessageBox.confirm(t('settings.media.removeConfirm'), t('common.tip'), { type: 'warning', closeOnClickModal: true })
  await mediaApi.removeConfig()
  ElMessage.success(t('common.deleted'))
  await loadMediaConfig()
}

// 放映厅引擎配置在挂载时一并拉取(家长才有权限,内部自行判断)
onMounted(() => {
  loadMediaConfig()
})
</script>

<style scoped>
.settings-card {
  margin-bottom: 16px;
  background: var(--color-card);
}
.share-tip {
  color: #776e62;
  font-size: 12px;
  margin-top: 8px;
  line-height: 1.4;
  width: 100%;
}
html.dark .share-tip {
  color: #9a9088;
}
.media-status {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.media-status-sub {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.media-libs {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.media-actions {
  display: flex;
  gap: 10px;
}

/* 个性化设置:控件行 + 标签水平排列,垂直居中 */
.setting-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.setting-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text);
}

/* 个性化设置:表单项间距统一 */
.settings-form .el-form-item {
  margin-bottom: 16px;
  padding: 6px 0;
}
.settings-form .el-form-item .el-switch,
.settings-form .el-form-item .el-input-number,
.settings-form .el-form-item .el-radio-group,
.settings-form .el-form-item .el-slider {
  margin-bottom: 0;
}
.settings-form .el-divider {
  margin: 8px 0 4px;
}

@media (max-width: 768px) {
  .setting-row {
    flex-wrap: wrap;
  }
  .settings-card .el-input,
  .settings-card .el-select {
    width: 100%;
  }
}
</style>
