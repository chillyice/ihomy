<!-- 智能家居中控:按房间分组展示家里设备(后端每分钟同步 Home Assistant 得来),常用设备可直接控制,点卡片看历史曲线 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('iot.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <el-input v-model="keyword" size="small" clearable style="width: 220px" :placeholder="$t('iot.search')">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>
      <div class="tb-right">
        <el-button size="small" :loading="loading" @click="load">{{ $t('iot.refresh') }}</el-button>
        <el-button v-if="canManage" size="small" @click="openManage">{{ $t('iot.manageBtn') }}</el-button>
        <el-button v-if="canManage" size="small" type="primary" @click="openConfig">{{ $t('iot.setup') }}</el-button>
      </div>
    </PageToolbar>

    <div v-loading="loading">
      <!-- 还没接入 / 没有可展示的设备 -->
      <div v-if="!groups.length && !keyword" class="card iot-empty">
        <div class="iot-empty-title">{{ devices.length ? $t('iot.empty') : $t('iot.notConfigured') }}</div>
        <div class="iot-empty-hint">
          {{ devices.length ? $t('iot.emptyHint') : (canManage ? $t('iot.notConfiguredHint') : $t('iot.noPermissionHint')) }}
        </div>
        <el-button v-if="canManage" type="primary" @click="openConfig">{{ $t('iot.setup') }}</el-button>
      </div>
      <el-empty v-else-if="!groups.length" :description="$t('iot.search')" :image-size="80" />

      <template v-else>
        <div v-if="canManage && cfg.lastError" class="card iot-banner">
          {{ $t('iot.config.lastError') }}:{{ cfg.lastError }}
        </div>

        <section v-for="g in groups" :key="g.room || '_'">
          <div class="section-label">
            {{ g.room || $t('iot.noRoom') }}
            <span class="iot-count">{{ g.items.length }}</span>
          </div>
          <div class="iot-grid">
            <div v-for="d in g.items" :key="d.id" class="iot-card card" v-a11y-click @click="openHistory(d)">
              <div class="iot-card-top">
                <span class="iot-name">{{ d.name }}</span>
                <el-tag size="small" effect="plain">{{ $t('iot.domain.' + d.domain) }}</el-tag>
              </div>

              <!-- 可调数值 -->
              <div v-if="isEditable(d)" class="iot-reading">
                <el-input-number
                  :model-value="numOf(d.state)"
                  size="small"
                  :controls="false"
                  :disabled="busyId === d.id"
                  style="width: 96px"
                  @click.stop
                  @change="(v) => v != null && control(d, 'set_value', { value: v })"
                />
                <span class="iot-unit">{{ d.unit }}</span>
              </div>

              <!-- 开关灯类 -->
              <div v-else-if="isSwitchable(d)" class="iot-reading">
                <span class="iot-value" :class="{ on: d.state === 'on' }">{{ stateText(d) }}</span>
                <el-switch
                  class="iot-switch"
                  :model-value="d.state === 'on'"
                  :loading="busyId === d.id"
                  @click.stop
                  @change="(v) => control(d, v ? 'turn_on' : 'turn_off')"
                />
              </div>

              <!-- 窗帘 -->
              <div v-else-if="d.domain === 'cover'" class="iot-reading iot-reading-col">
                <span class="iot-value">{{ stateText(d) }}</span>
                <div class="iot-actions">
                  <el-button size="small" :loading="busyId === d.id" @click.stop="control(d, 'open_cover')">{{ $t('iot.action.open') }}</el-button>
                  <el-button size="small" :loading="busyId === d.id" @click.stop="control(d, 'close_cover')">{{ $t('iot.action.close') }}</el-button>
                  <el-button v-if="d.state === 'opening' || d.state === 'closing'" size="small" @click.stop="control(d, 'stop_cover')">{{ $t('iot.action.stop') }}</el-button>
                </div>
              </div>

              <!-- 门锁 -->
              <div v-else-if="d.domain === 'lock'" class="iot-reading iot-reading-col">
                <span class="iot-value" :class="{ on: d.state === 'unlocked' }">{{ stateText(d) }}</span>
                <div class="iot-actions">
                  <el-button size="small" :loading="busyId === d.id" @click.stop="control(d, d.state === 'locked' ? 'unlock' : 'lock')">
                    {{ d.state === 'locked' ? $t('iot.action.unlock') : $t('iot.action.lock') }}
                  </el-button>
                </div>
              </div>

              <!-- 播放器 -->
              <div v-else-if="d.domain === 'media_player'" class="iot-reading iot-reading-col">
                <span class="iot-value" :class="{ on: d.state === 'playing' }">{{ stateText(d) }}</span>
                <div class="iot-actions">
                  <el-button size="small" :loading="busyId === d.id" @click.stop="control(d, d.state === 'playing' ? 'media_pause' : 'media_play')">
                    {{ d.state === 'playing' ? $t('iot.action.pause') : $t('iot.action.play') }}
                  </el-button>
                </div>
              </div>

              <!-- 传感器 / 空调等只读 -->
              <div v-else class="iot-reading">
                <span class="iot-value" :class="{ on: d.domain === 'binary_sensor' && d.state === 'on' }">{{ stateText(d) }}</span>
                <span v-if="d.unit" class="iot-unit">{{ d.unit }}</span>
              </div>

              <div class="iot-meta">{{ d.lastSeenAt ? formatDateTime(d.lastSeenAt) : '' }}</div>
            </div>
          </div>
        </section>
      </template>
    </div>

    <!-- 历史曲线 -->
    <el-dialog v-model="hist.visible" append-to-body :title="hist.device ? hist.device.name : ''" width="640px">
      <div class="hist-toolbar">
        <el-radio-group v-model="hist.hours" size="small" @change="loadHistory">
          <el-radio-button :value="24">{{ $t('iot.hours24') }}</el-radio-button>
          <el-radio-button :value="168">{{ $t('iot.hours168') }}</el-radio-button>
        </el-radio-group>
        <span v-if="hist.device" class="hist-now">{{ stateText(hist.device) }}{{ hist.device.unit ? ' ' + hist.device.unit : '' }}</span>
      </div>
      <div v-loading="hist.loading" class="hist-box">
        <template v-if="shape">
          <div class="hist-axis"><span>{{ shape.hi }}</span><span>{{ shape.lo }}</span></div>
          <svg class="hist-svg" :viewBox="'0 0 ' + shape.w + ' ' + shape.h" preserveAspectRatio="none" aria-hidden="true">
            <polyline :points="shape.line" fill="none" stroke="currentColor" stroke-width="2" vector-effect="non-scaling-stroke" stroke-linejoin="round" />
          </svg>
          <div class="hist-axis"><span>{{ shape.from }}</span><span>{{ shape.to }}</span></div>
        </template>
        <el-empty v-else-if="!hist.loading" :description="$t('iot.historyEmpty')" :image-size="80" />
      </div>
    </el-dialog>

    <!-- 管理设备:改名 / 分组 / 是否展示 -->
    <el-dialog v-model="manage.visible" append-to-body :title="$t('iot.manage.title')" width="680px">
      <div class="iot-hint">{{ $t('iot.manage.hint') }}</div>
      <el-table :data="devices" size="small">
        <el-table-column :label="$t('iot.manage.name')" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.name" size="small" @change="saveDevice(row)" />
          </template>
        </el-table-column>
        <el-table-column :label="$t('iot.manage.room')" min-width="130">
          <template #default="{ row }">
            <el-input v-model="row.room" size="small" clearable :placeholder="$t('iot.manage.roomPlaceholder')" @change="saveDevice(row)" />
          </template>
        </el-table-column>
        <el-table-column :label="$t('iot.manage.show')" width="90">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled" @change="(v) => saveDevice(row, { enabled: v })" />
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 接入配置 -->
    <el-dialog v-model="cfgDlg.visible" append-to-body :title="$t('iot.config.title')" width="520px">
      <el-form label-position="top">
        <el-form-item :label="$t('iot.config.baseUrl')">
          <el-input v-model="cfgForm.baseUrl" clearable :placeholder="$t('iot.config.baseUrlPlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('iot.config.token')">
          <el-input v-model="cfgForm.token" type="password" show-password clearable :placeholder="$t('iot.config.tokenPlaceholder')" />
          <div v-if="cfg.hasToken" class="iot-hint">{{ $t('iot.config.tokenHint') }}</div>
        </el-form-item>
        <el-form-item>
          <el-switch v-model="cfgForm.enabled" :active-text="$t('iot.config.enabled')" />
        </el-form-item>
      </el-form>
      <div v-if="cfg.configured" class="iot-cfg-meta">
        <span>{{ $t('iot.config.lastSync') }}:{{ cfg.lastSyncAt ? formatDateTime(cfg.lastSyncAt) : $t('iot.config.never') }}</span>
        <span>{{ $t('iot.config.deviceCount', { n: cfg.deviceCount }) }}</span>
      </div>
      <div v-if="cfg.lastError" class="iot-banner">{{ $t('iot.config.lastError') }}:{{ cfg.lastError }}</div>
      <template #footer>
        <div class="iot-dlg-foot">
          <el-button v-if="cfg.configured" type="danger" text @click="onRemoveCfg">{{ $t('iot.config.remove') }}</el-button>
          <div class="iot-dlg-acts">
            <el-button @click="cfgDlg.visible = false">{{ $t('common.cancel') }}</el-button>
            <el-button :loading="testing" @click="onTest">{{ $t('iot.config.test') }}</el-button>
            <el-button type="primary" :loading="saving" @click="onSaveCfg">{{ $t('iot.config.save') }}</el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
// 智能家居中控:读数/开关来自家里的 Home Assistant(后端每分钟同步),控制由 HA 执行后回写
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { iotApi } from '@/api'
import { useUserStore } from '@/stores/user'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'

const { t } = useI18n()
const userStore = useUserStore()

// 接入配置是家长权限,成员只看设备
const canManage = computed(() => userStore.hasPerm('storage:manage'))

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const busyId = ref(null)
const keyword = ref('')
const devices = ref([])
const cfg = reactive({ configured: false, baseUrl: '', hasToken: false, enabled: true, lastSyncAt: null, lastError: null, deviceCount: 0 })
const cfgForm = reactive({ baseUrl: '', token: '', enabled: true })
const cfgDlg = reactive({ visible: false })
const manage = reactive({ visible: false })
const hist = reactive({ visible: false, device: null, hours: 24, points: [], loading: false })

const SWITCHABLE = new Set(['switch', 'light', 'fan', 'humidifier', 'input_boolean'])
const isSwitchable = (d) => SWITCHABLE.has(d.domain)
const isEditable = (d) => (d.domain === 'number' || d.domain === 'input_number') && numOf(d.state) != null

// 译码:命中 i18n 才回译文,否则回空串让调用方兜底(vue-i18n 缺键时返回的就是键本身)
const tr = (ns, v) => {
  if (v == null || v === '') return ''
  const s = t(ns + '.' + v)
  return s === ns + '.' + v ? '' : s
}
const stateText = (d) => {
  const order = d.domain === 'climate' ? ['iot.climate', 'iot.state'] : ['iot.state', 'iot.climate']
  for (const ns of order) {
    const s = tr(ns, d.state)
    if (s) return s
  }
  return String(d.state ?? '')
}

// 曲线取值:开关/门窗/播放状态映射成 0/1,能解析成数的直接用,解析不了的丢掉
const numOf = (v) => {
  if (v == null || v === '') return null
  if (v === 'on' || v === 'open' || v === 'unlocked' || v === 'playing') return 1
  if (v === 'off' || v === 'closed' || v === 'locked' || v === 'paused' || v === 'idle') return 0
  const n = parseFloat(v)
  return Number.isNaN(n) ? null : n
}
const fmtNum = (n) => (Number.isInteger(n) ? String(n) : n.toFixed(1))
const formatDateTime = (v) => (v ? new Date(v).toLocaleString() : '')

const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return devices.value
  return devices.value.filter((d) => (d.name || '').toLowerCase().includes(k) || (d.room || '').toLowerCase().includes(k))
})

const groups = computed(() => {
  const map = new Map()
  for (const d of filtered.value) {
    const key = d.room || ''
    if (!map.has(key)) map.set(key, [])
    map.get(key).push(d)
  }
  return [...map.entries()].map(([room, items]) => ({ room, items }))
})

const shape = computed(() => {
  const pts = hist.points || []
  const valid = []
  pts.forEach((p, i) => {
    const n = numOf(p.v)
    if (n != null) valid.push({ i, n })
  })
  if (!valid.length) return null
  const W = 600
  const H = 200
  const PAD = 24
  const nums = valid.map((o) => o.n)
  const lo = Math.min(...nums)
  const hi = Math.max(...nums)
  let min = lo
  let max = hi
  if (min === max) {
    min -= 1
    max += 1
  }
  const span = Math.max(pts.length - 1, 1)
  const x = (i) => PAD + (i / span) * (W - 2 * PAD)
  const y = (n) => H - PAD - ((n - min) / (max - min)) * (H - 2 * PAD)
  return {
    w: W,
    h: H,
    line: valid.map((o) => `${x(o.i).toFixed(1)},${y(o.n).toFixed(1)}`).join(' '),
    lo: fmtNum(lo),
    hi: fmtNum(hi),
    from: formatDateTime(pts[0].t),
    to: formatDateTime(pts[pts.length - 1].t),
  }
})

const load = async () => {
  loading.value = true
  try {
    devices.value = (await iotApi.devices()) || []
    if (canManage.value) {
      try {
        Object.assign(cfg, await iotApi.config())
      } catch { /* 配置读不到就按现状渲染,不打断设备列表 */ }
    }
  } finally {
    loading.value = false
  }
}

// 控制:后端回的是控制后的设备行,原地替换卡片状态(失败由请求层统一提示)
const control = async (d, service, data) => {
  busyId.value = d.id
  try {
    const fresh = await iotApi.control({ entityId: d.entityId, service, data })
    const i = devices.value.findIndex((x) => x.id === d.id)
    if (i >= 0) devices.value[i] = fresh
    else devices.value.push(fresh)
  } finally {
    busyId.value = null
  }
}

const saveDevice = async (row, extra) => {
  try {
    Object.assign(row, await iotApi.updateDevice(row.id, { name: row.name, room: row.room, ...extra }))
    ElMessage.success(t('iot.manage.saved'))
  } catch {
    await load()
  }
}

const openManage = () => { manage.visible = true }

const openHistory = (d) => {
  hist.device = d
  hist.hours = 24
  hist.visible = true
  loadHistory()
}

const loadHistory = async () => {
  if (!hist.device) return
  hist.loading = true
  try {
    hist.points = (await iotApi.history(hist.device.id, hist.hours)) || []
  } finally {
    hist.loading = false
  }
}

const openConfig = async () => {
  cfgDlg.visible = true
  cfgForm.token = ''
  try {
    const v = await iotApi.config()
    Object.assign(cfg, v)
    cfgForm.baseUrl = v.baseUrl || ''
    cfgForm.enabled = v.enabled !== false
  } catch { /* 读不到就按未接入渲染 */ }
}

const onSaveCfg = async () => {
  saving.value = true
  try {
    Object.assign(cfg, await iotApi.saveConfig({ baseUrl: cfgForm.baseUrl, token: cfgForm.token, enabled: cfgForm.enabled }))
    cfgForm.token = ''
    cfgDlg.visible = false
    ElMessage.success(t('common.saveSuccess'))
    await load()
  } finally {
    saving.value = false
  }
}

// 先测连通再保存:令牌留空表示沿用已保存的那把
const onTest = async () => {
  testing.value = true
  try {
    const r = await iotApi.test({ baseUrl: cfgForm.baseUrl, token: cfgForm.token })
    if (r.ok) {
      ElMessage.success(`${t('iot.config.testOk')}${r.locationName ? ': ' + r.locationName : ''}${r.version ? ' (' + r.version + ')' : ''}`)
    } else {
      ElMessage.error(r.message || t('iot.config.testFail'))
    }
  } finally {
    testing.value = false
  }
}

const onRemoveCfg = async () => {
  await ElMessageBox.confirm(t('iot.config.removeConfirm'), t('iot.config.remove'), { type: 'warning', closeOnClickModal: true })
  await iotApi.removeConfig()
  Object.assign(cfg, { configured: false, baseUrl: '', hasToken: false, lastSyncAt: null, lastError: null, deviceCount: 0 })
  cfgDlg.visible = false
  ElMessage.success(t('common.deleted'))
  await load()
}

onMounted(load)
</script>

<style scoped>
.iot-empty {
  padding: 56px 24px;
  text-align: center;
}
.iot-empty-title {
  font-size: 16px;
  font-weight: 600;
}
.iot-empty-hint {
  margin: 8px auto 18px;
  max-width: 460px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.iot-banner {
  margin-bottom: 12px;
  padding: 10px 14px;
  font-size: 13px;
  color: var(--el-color-warning);
}
.iot-count {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}
.iot-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}
.iot-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 16px;
  cursor: pointer;
  contain: layout style;
  transition: transform .18s ease;
}
.iot-card:hover {
  transform: translateY(-2px);
}
.iot-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.iot-name {
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.iot-reading {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 34px;
}
.iot-reading-col {
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}
.iot-value {
  font-size: 26px;
  font-weight: 600;
  line-height: 1.1;
}
.iot-value.on {
  color: var(--el-color-primary);
}
.iot-unit {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.iot-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.iot-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
.iot-switch {
  margin-left: auto;
}
.iot-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.iot-hint {
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.iot-cfg-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.iot-dlg-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.iot-dlg-acts {
  display: flex;
  gap: 8px;
}
.hist-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}
.hist-now {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.hist-box {
  min-height: 220px;
}
.hist-axis {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.hist-svg {
  display: block;
  width: 100%;
  height: 200px;
  margin: 4px 0;
  color: var(--el-color-primary);
}

@media (max-width: 768px) {
  .iot-grid {
    grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  }
  .iot-value {
    font-size: 22px;
  }
}
</style>
