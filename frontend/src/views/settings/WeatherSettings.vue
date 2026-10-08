<template>
  <!-- 天气设置:天气地区 + 预警推送 + 和风天气 API 凭证(家长可管理) -->
  <div class="card settings-card">
    <div class="section-label">{{ $t('settings.weather.regionTitle') }}</div>
    <el-form label-position="top">
      <el-form-item :label="$t('settings.weatherRegion')">
        <div class="weather-loc-row">
          <el-select
            v-model="weatherLocationId"
            filterable
            remote
            clearable
            :placeholder="$t('settings.weatherSearchPh')"
            :remote-method="searchLocations"
            :loading="locLoading"
            style="width: 240px"
            @change="onLocationChange"
          >
            <el-option v-for="loc in locationOptions" :key="loc.id" :label="loc.name + ' · ' + loc.adm1" :value="loc.id" />
          </el-select>
          <el-button v-if="weatherCity" :loading="savingWeather" @click="clearLocation">{{ $t('settings.weather.useIp') }}</el-button>
        </div>
        <div class="share-tip">
          {{ $t('settings.weather.currentRegion') }}: <strong>{{ weatherCity || $t('settings.weather.ipAuto') }}</strong> ·
          {{ $t('settings.weather.regionHint') }}
        </div>
      </el-form-item>
      <el-form-item :label="$t('settings.alertPushTitle')">
        <div class="setting-row">
          <el-switch v-model="alertPushEnabled" @change="onAlertPushChange" />
          <span class="setting-label">{{ $t('settings.alertPushLabel') }}</span>
        </div>
        <div class="share-tip">{{ $t('settings.alertPushHint') }}</div>
      </el-form-item>
    </el-form>
  </div>

  <!-- 和风天气 API 凭证 -->
  <div class="card settings-card" v-loading="weatherCredLoading">
    <div class="section-label">{{ $t('settings.weather.apiTitle') }}</div>
    <div class="share-tip">{{ $t('settings.weather.apiHint') }}</div>
    <div class="ai-toolbar">
      <el-button type="primary" plain @click="openWeatherCred()">{{ $t('settings.weather.addCredential') }}</el-button>
    </div>
    <div class="weather-cred-list">
      <div v-for="row in weatherCreds" :key="row.id" class="weather-cred-item">
        <span class="weather-cred-name">{{ row.name }}</span>
        <el-tag v-if="row.status === 1" size="small" type="success">{{ $t('settings.weather.defaultTag') }}</el-tag>
        <div class="weather-cred-actions">
          <el-button v-if="row.status !== 1" size="small" text type="primary" @click="enableWeatherCred(row)">{{
            $t('settings.weather.setDefault')
          }}</el-button>
          <el-tooltip :content="$t('common.edit')" placement="top" :show-after="300">
            <el-button size="small" text @click="openWeatherCred(row)"
              ><el-icon><Edit /></el-icon
            ></el-button>
          </el-tooltip>
          <el-tooltip :content="$t('common.delete')" placement="top" :show-after="300">
            <el-button size="small" text type="danger" @click="removeWeatherCred(row)"
              ><el-icon><Delete /></el-icon
            ></el-button>
          </el-tooltip>
        </div>
      </div>
    </div>
    <el-empty v-if="!weatherCreds.length && !weatherCredLoading" :description="$t('settings.weather.emptyHint')" :image-size="40" />
  </div>

  <!-- 凭证编辑对话框 -->
  <el-dialog
    v-model="weatherCredDialog"
    append-to-body
    :title="weatherCredForm.id ? $t('common.edit') : $t('settings.weather.addCredential')"
    width="560px"
  >
    <el-form :model="weatherCredForm" label-width="130px">
      <el-form-item :label="$t('settings.weather.name')" required>
        <el-input v-model="weatherCredForm.name" :placeholder="$t('settings.weather.namePh')" />
      </el-form-item>
      <el-form-item :label="$t('settings.weather.env')" required>
        <el-select v-model="weatherCredForm.env" style="width: 100%">
          <el-option label="test" value="test" />
          <el-option label="prod" value="prod" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('settings.weather.providerLabel')" required>
        <el-select v-model="weatherCredForm.provider" style="width: 100%">
          <el-option :label="$t('settings.weather.provider.QWEATHER')" value="QWEATHER" />
          <el-option :label="$t('settings.weather.provider.OPENWEATHER')" value="OPENWEATHER" />
          <el-option :label="$t('settings.weather.provider.AMAP')" value="AMAP" />
        </el-select>
      </el-form-item>
      <template v-if="weatherCredForm.provider === 'QWEATHER'">
        <el-form-item :label="$t('settings.weather.apiHost')" required>
          <el-input v-model="weatherCredForm.apiHost" :placeholder="$t('settings.weather.apiHostPh')" />
        </el-form-item>
        <el-form-item :label="$t('settings.weather.projectId')" required>
          <el-input v-model="weatherCredForm.projectId" :placeholder="$t('settings.weather.projectIdPh')" />
        </el-form-item>
        <el-form-item :label="$t('settings.weather.keyId')" required>
          <el-input v-model="weatherCredForm.keyId" :placeholder="$t('settings.weather.keyIdPh')" />
        </el-form-item>
        <el-form-item :label="$t('settings.weather.privateKey')">
          <el-input
            v-model="weatherCredForm.privateKey"
            type="textarea"
            :rows="4"
            :placeholder="weatherCredForm.privateKeySet ? $t('settings.weather.privateKeyKeep') : $t('settings.weather.privateKeyPh')"
          />
        </el-form-item>
        <el-form-item :label="$t('settings.weather.publicKey')">
          <el-input
            v-model="weatherCredForm.publicKey"
            type="textarea"
            :rows="3"
            :placeholder="weatherCredForm.publicKeySet ? $t('settings.weather.publicKeyKeep') : $t('settings.weather.publicKeyPh')"
          />
        </el-form-item>
      </template>
      <template v-else>
        <el-form-item :label="$t('settings.weather.apiKey')" required>
          <el-input
            v-model="weatherCredForm.apiKey"
            type="password"
            show-password
            :placeholder="weatherCredForm.apiKeySet ? $t('settings.weather.apiKeyKeep') : $t('settings.weather.apiKeyPh')"
          />
        </el-form-item>
      </template>
      <el-form-item :label="$t('settings.weather.remark')">
        <el-input v-model="weatherCredForm.remark" :placeholder="$t('settings.weather.remarkPh')" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="weatherCredDialog = false">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="weatherCredSaving" @click="saveWeatherCred">{{ $t('common.confirm') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, inject, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { familyApi, publicApi, weatherApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Edit, Delete } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'

const { t } = useI18n()
const userStore = useUserStore()

// 光照设置:从全局 useSunLight 实例注入(与 SunLightLayer/AppSidebar 共享)
const sunLight = inject(SUN_LIGHT_KEY)

const canManageWeather = computed(() => userStore.hasPerm('family:manage'))

// 天气地区偏好(空=IP 自动定位)
const weatherLocationId = ref('')
const weatherCity = ref('')
const weatherLat = ref('')
const weatherLng = ref('')
const savingWeather = ref(false)
const locationOptions = ref([])
const locLoading = ref(false)
const alertPushEnabled = ref(true)

const searchLocations = async (query) => {
  if (!query) {
    locationOptions.value = []
    return
  }
  locLoading.value = true
  try {
    locationOptions.value = (await publicApi.searchWeatherLocations(query)) || []
  } catch (e) {
  } finally {
    locLoading.value = false
  }
}
const onLocationChange = (id) => {
  if (!id) {
    clearLocation()
    return
  }
  const loc = locationOptions.value.find((l) => l.id === id)
  if (loc) {
    weatherLocationId.value = id
    persistRegion(loc.name, Number(loc.lat), Number(loc.lng))
  }
}
const clearLocation = () => {
  weatherLocationId.value = ''
  persistRegion(null, null, null)
}
// 选择城市/使用 IP 定位后立即保存,「当前地区」始终反映已持久化的值
const persistRegion = async (city, lat, lng) => {
  savingWeather.value = true
  try {
    await familyApi.update({ weatherCity: city || null, weatherLat: lat, weatherLng: lng })
    weatherCity.value = city || ''
    weatherLat.value = lat == null ? '' : String(lat)
    weatherLng.value = lng == null ? '' : String(lng)
    ElMessage.success(t('settings.weather.regionSaved'))
    // 立即按新地域重新拉取天气(首页天气卡片/侧边栏迷你天气/天气页共享同一 sunLight 实例)
    sunLight?.loadWeather?.()
  } catch (e) {
    // 拦截器已提示
  } finally {
    savingWeather.value = false
  }
}
const loadAlertPush = async () => {
  try {
    const r = await familyApi.getWeatherAlertPush()
    alertPushEnabled.value = r?.enabled !== false
  } catch {}
}
const onAlertPushChange = async (v) => {
  try {
    await familyApi.setWeatherAlertPush(v)
  } catch {
    alertPushEnabled.value = !v
  }
}

// ===== 天气 API 凭证(和风天气,家长可管理) =====
const weatherCreds = ref([])
const weatherCredDialog = ref(false)
const weatherCredSaving = ref(false)
const weatherCredLoading = ref(false)
const weatherCredForm = reactive({
  id: null,
  name: '',
  env: 'prod',
  provider: 'QWEATHER',
  apiHost: '',
  projectId: '',
  keyId: '',
  privateKey: '',
  publicKey: '',
  apiKey: '',
  remark: '',
  privateKeySet: false,
  publicKeySet: false,
  apiKeySet: false,
})

const loadWeatherCreds = async () => {
  if (!userStore.isLoggedIn || !canManageWeather.value) return
  weatherCredLoading.value = true
  try {
    weatherCreds.value = (await weatherApi.credentials()) || []
  } catch (e) {
    // 拦截器已提示(非家长 403 静默)
  } finally {
    weatherCredLoading.value = false
  }
}

const openWeatherCred = (row) => {
  Object.assign(
    weatherCredForm,
    row
      ? {
          id: row.id,
          name: row.name,
          env: row.env,
          provider: row.provider || 'QWEATHER',
          apiHost: row.apiHost || '',
          projectId: row.projectId || '',
          keyId: row.keyId || '',
          privateKey: '',
          publicKey: '',
          apiKey: '',
          remark: row.remark || '',
          privateKeySet: row.privateKeySet,
          publicKeySet: row.publicKeySet,
          apiKeySet: row.apiKeySet,
        }
      : {
          id: null,
          name: '',
          env: 'prod',
          provider: 'QWEATHER',
          apiHost: '',
          projectId: '',
          keyId: '',
          privateKey: '',
          publicKey: '',
          apiKey: '',
          remark: '',
          privateKeySet: false,
          publicKeySet: false,
          apiKeySet: false,
        },
  )
  weatherCredDialog.value = true
}

const saveWeatherCred = async () => {
  const isQ = weatherCredForm.provider === 'QWEATHER'
  if (!weatherCredForm.name || !weatherCredForm.env) {
    ElMessage.warning(t('settings.weather.requiredTip'))
    return
  }
  if (isQ && (!weatherCredForm.apiHost || !weatherCredForm.projectId || !weatherCredForm.keyId)) {
    ElMessage.warning(t('settings.weather.requiredTip'))
    return
  }
  if (!isQ && !weatherCredForm.apiKey && !weatherCredForm.apiKeySet) {
    ElMessage.warning(t('settings.weather.apiKeyRequired'))
    return
  }
  weatherCredSaving.value = true
  try {
    const data = {
      name: weatherCredForm.name,
      env: weatherCredForm.env,
      provider: weatherCredForm.provider,
      remark: weatherCredForm.remark || '',
    }
    if (isQ) {
      data.apiHost = weatherCredForm.apiHost
      data.projectId = weatherCredForm.projectId
      data.keyId = weatherCredForm.keyId
      // 密钥仅在输入时提交(留空=保留原值)
      if (weatherCredForm.privateKey) data.privateKey = weatherCredForm.privateKey
      if (weatherCredForm.publicKey) data.publicKey = weatherCredForm.publicKey
    } else if (weatherCredForm.apiKey) {
      data.apiKey = weatherCredForm.apiKey
    }
    if (weatherCredForm.id) {
      await weatherApi.updateCredential(weatherCredForm.id, data)
    } else {
      await weatherApi.addCredential(data)
    }
    ElMessage.success(t('settings.ai.saved'))
    weatherCredDialog.value = false
    await loadWeatherCreds()
  } catch (e) {
    // 拦截器已提示
  } finally {
    weatherCredSaving.value = false
  }
}

const enableWeatherCred = (row) => {
  ElMessageBox.confirm(t('settings.weather.setDefaultConfirm'), t('settings.weather.setDefault'), {
    confirmButtonText: t('common.confirm'),
    cancelButtonText: t('common.cancel'),
    type: 'warning',
    closeOnClickModal: true,
  })
    .then(async () => {
      try {
        await weatherApi.enableCredential(row.id)
        ElMessage.success(t('settings.ai.saved'))
        await loadWeatherCreds()
      } catch (e) {
        // 拦截器已提示
      }
    })
    .catch(() => {})
}

const removeWeatherCred = (row) => {
  ElMessageBox.confirm(t('settings.weather.deleteConfirm'), t('common.delete'), {
    confirmButtonText: t('common.confirm'),
    cancelButtonText: t('common.cancel'),
    type: 'warning',
    closeOnClickModal: true,
  })
    .then(async () => {
      try {
        await weatherApi.deleteCredential(row.id)
        ElMessage.success(t('settings.weather.deleted'))
        await loadWeatherCreds()
      } catch (e) {
        // 拦截器已提示
      }
    })
    .catch(() => {})
}

onMounted(async () => {
  // 天气地区偏好随家庭设置一并回填
  const f = await familyApi.get().catch(() => null)
  if (f) {
    weatherCity.value = f.weatherCity || ''
    weatherLat.value = f.weatherLat ?? ''
    weatherLng.value = f.weatherLng ?? ''
    weatherLocationId.value = ''
    loadAlertPush()
  }
  loadWeatherCreds()
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
.weather-loc-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
}
.weather-cred-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.weather-cred-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: var(--color-card-2);
  border-radius: 10px;
}
.weather-cred-name {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.weather-cred-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
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
.ai-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
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
