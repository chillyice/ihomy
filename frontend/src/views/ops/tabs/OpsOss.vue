<template>
  <div class="filter-row">
    <el-button type="primary" :loading="ossChecking" @click="checkOss">
      {{ ossChecking ? $t('ops.oss.checking') : $t('ops.oss.checkNow') }}
    </el-button>
    <el-button @click="openOssAdd">{{ $t('ops.oss.add') }}</el-button>
    <span class="traffic-hint">
      <template v-if="ossLastChecked">{{ $t('ops.oss.lastChecked') }}: {{ fmtTime(ossLastChecked) }}</template>
      <el-tag v-if="ossUpdatable" type="danger" size="small" style="margin-left: 8px">{{
        $t('ops.oss.updatable', { n: ossUpdatable })
      }}</el-tag>
      <el-tag v-else-if="ossLoaded && !ossChecking" type="success" size="small" style="margin-left: 8px">{{
        $t('ops.oss.allUpToDate')
      }}</el-tag>
    </span>
  </div>
  <div class="filter-row" style="margin-top: -8px">
    <el-input v-model="ossFilter.keyword" :placeholder="$t('ops.oss.searchPlaceholder')" clearable size="small" style="width: 200px" />
    <el-select v-model="ossFilter.componentType" :placeholder="$t('ops.oss.componentType')" clearable size="small" style="width: 120px">
      <el-option value="NPM" :label="$t('ops.oss.type_npm')" />
      <el-option value="MAVEN" :label="$t('ops.oss.type_maven')" />
      <el-option value="SERVICE" :label="$t('ops.oss.type_service')" />
    </el-select>
    <el-select v-model="ossFilter.updateType" :placeholder="$t('ops.oss.updateType')" clearable size="small" style="width: 120px">
      <el-option value="MAJOR" :label="$t('ops.oss.update_major')" />
      <el-option value="MINOR" :label="$t('ops.oss.update_minor')" />
      <el-option value="PATCH" :label="$t('ops.oss.update_patch')" />
    </el-select>
    <el-select v-model="ossFilter.status" :placeholder="$t('ops.oss.status')" clearable size="small" style="width: 110px">
      <el-option value="ACTIVE" :label="$t('ops.oss.statusActive')" />
      <el-option value="IGNORED" :label="$t('ops.oss.statusIgnored')" />
    </el-select>
    <el-select v-model="ossFilter.integrationStatus" :placeholder="$t('ops.oss.integration')" clearable size="small" style="width: 120px">
      <el-option value="FULL" :label="$t('ops.oss.integ_full')" />
      <el-option value="PARTIAL" :label="$t('ops.oss.integ_partial')" />
      <el-option value="PLANNED" :label="$t('ops.oss.integ_planned')" />
    </el-select>
  </div>
  <el-table v-loading="ossLoading" :data="filteredOssRows" border stripe size="small">
    <el-table-column :label="$t('ops.oss.name')" min-width="180">
      <template #default="{ row }">
        <div>{{ row.name }}</div>
        <div class="oss-purpose">{{ row.purpose }}</div>
      </template>
    </el-table-column>
    <el-table-column :label="$t('ops.oss.componentType')" width="90">
      <template #default="{ row }">
        <el-tag size="small" :type="ossTypeTag(row.componentType)">{{
          $t('ops.oss.type_' + String(row.componentType).toLowerCase())
        }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column :label="$t('ops.oss.currentVersion')" width="140">
      <template #default="{ row }">
        <span class="mono">{{ row.currentVersion || '—' }}</span>
        <el-tooltip v-if="row.probeType" :content="ossProbeTip(row)" placement="top">
          <span class="oss-probe-tag">{{ $t('ops.oss.probed') }}</span>
        </el-tooltip>
      </template>
    </el-table-column>
    <el-table-column :label="$t('ops.oss.latestVersion')" width="110">
      <template #default="{ row }"
        ><span class="mono">{{ row.latestVersion || '—' }}</span></template
      >
    </el-table-column>
    <el-table-column :label="$t('ops.oss.updateType')" width="90">
      <template #default="{ row }">
        <el-tag v-if="isOssUpdatable(row)" size="small" :type="ossUpdateTag(row.updateType)">{{
          $t('ops.oss.update_' + String(row.updateType).toLowerCase())
        }}</el-tag>
        <span v-else class="oss-none">—</span>
      </template>
    </el-table-column>
    <el-table-column :label="$t('ops.oss.integration')" width="100">
      <template #default="{ row }">{{ $t('ops.oss.integ_' + String(row.integrationStatus).toLowerCase()) }}</template>
    </el-table-column>
    <el-table-column prop="license" :label="$t('ops.oss.license')" width="120" show-overflow-tooltip />
    <el-table-column :label="$t('ops.oss.managedBy')" width="90">
      <template #default="{ row }">
        <el-tag v-if="row.managedBy === 'RENOVATE'" size="small" type="info">{{ $t('ops.oss.managedRenovate') }}</el-tag>
        <span v-else class="oss-none">—</span>
      </template>
    </el-table-column>
    <el-table-column :label="$t('ops.oss.vuln')" width="80">
      <template #default="{ row }">
        <el-tag
          v-if="row.vulnCount"
          size="small"
          :type="row.vulnSeverity === 'CRITICAL' || row.vulnSeverity === 'HIGH' ? 'danger' : 'warning'"
          >{{ row.vulnCount }}</el-tag
        >
        <span v-else class="oss-none">—</span>
      </template>
    </el-table-column>
    <el-table-column :label="$t('ops.oss.actions')" width="300">
      <template #default="{ row }">
        <el-button v-if="row.probeType" link type="primary" size="small" :loading="probingOssId === row.id" @click="probeOss(row)">{{
          $t('ops.oss.probe')
        }}</el-button>
        <el-button v-if="isOssUpdatable(row)" link type="primary" size="small" @click="openOssPlan(row)">{{
          $t('ops.oss.genPlan')
        }}</el-button>
        <el-button v-if="isOssUpdatable(row)" link type="success" size="small" @click="confirmOss(row)">{{
          $t('ops.oss.confirm')
        }}</el-button>
        <el-button v-if="isOssUpdatable(row)" link type="warning" size="small" @click="ignoreOss(row, true)">{{
          $t('ops.oss.ignore')
        }}</el-button>
        <el-button v-else-if="row.status === 'IGNORED'" link size="small" @click="ignoreOss(row, false)">{{
          $t('ops.oss.unignore')
        }}</el-button>
        <el-button link size="small" @click="openOssEdit(row)">{{ $t('common.edit') }}</el-button>
      </template>
    </el-table-column>
  </el-table>

  <el-dialog v-model="ossPlanVisible" :title="$t('ops.oss.plan')" width="640px">
    <div v-if="ossPlan">
      <div class="oss-plan-head">
        <b>{{ ossPlan.name }}</b>
        <span class="mono">{{ ossPlan.currentVersion }} → {{ ossPlan.latestVersion }}</span>
      </div>
      <ol class="oss-plan-steps">
        <li v-for="(s, i) in ossPlan.steps" :key="i">{{ s }}</li>
      </ol>

      <div v-if="ossPlanComponent && isOssUpdatable(ossPlanComponent)" class="oss-assess">
        <div class="oss-assess-bar">
          <el-button size="small" type="primary" :loading="ossAssessing" @click="assessOss(ossPlanComponent)">
            {{ ossAssessing ? $t('ops.oss.assessing') : $t('ops.oss.aiAssess') }}
          </el-button>
          <el-tag v-if="ossAssess" size="small" :type="ossAssessRiskTag(ossAssess.riskLevel)"
            >{{ $t('ops.oss.risk') }}: {{ ossAssess.riskLevel }}</el-tag
          >
          <el-tag v-if="ossAssess" size="small" :type="ossAssess.feasible ? 'success' : 'danger'">
            {{ ossAssess.feasible ? $t('ops.oss.feasibleYes') : $t('ops.oss.feasibleNo') }}
          </el-tag>
        </div>
        <div v-if="ossAssess" class="oss-assess-body">
          <p class="oss-assess-summary">{{ ossAssess.summary }}</p>
          <template v-if="ossAssess.breakingChanges && ossAssess.breakingChanges.length">
            <div class="oss-assess-sub">{{ $t('ops.oss.breakingChanges') }}</div>
            <ul>
              <li v-for="(b, i) in ossAssess.breakingChanges" :key="i">{{ b }}</li>
            </ul>
          </template>
          <template v-if="ossAssess.migrationSteps && ossAssess.migrationSteps.length">
            <div class="oss-assess-sub">{{ $t('ops.oss.migrationSteps') }}</div>
            <ol>
              <li v-for="(m, i) in ossAssess.migrationSteps" :key="i">{{ m }}</li>
            </ol>
          </template>
          <p class="oss-assess-note">{{ ossAssess.disclaimer }}</p>
        </div>
      </div>

      <div style="margin-top: 14px">
        <el-button
          v-if="ossPlanComponent && ossPlanComponent.managedBy === 'RENOVATE' && isOssUpdatable(ossPlanComponent)"
          size="small"
          type="success"
          :disabled="!ossAssess || !ossAssess.feasible"
          @click="requestOssUpgrade(ossPlanComponent)"
          >{{ $t('ops.oss.genPr') }}</el-button
        >
        <el-button size="small" @click="copyOssPlan">{{ ossCopied ? $t('ops.oss.copied') : $t('ops.oss.copy') }}</el-button>
        <el-button v-if="ossPlan.repoUrl" size="small" @click="openRepo(ossPlan.repoUrl)">{{ $t('ops.oss.viewChange') }}</el-button>
      </div>
    </div>
  </el-dialog>

  <el-dialog v-model="ossEditVisible" :title="ossEditForm.id ? $t('ops.oss.edit') : $t('ops.oss.add')" width="520px">
    <el-form :model="ossEditForm" label-width="100px">
      <el-form-item :label="$t('ops.oss.name')"><el-input v-model="ossEditForm.name" /></el-form-item>
      <el-form-item :label="$t('ops.oss.componentType')">
        <el-select v-model="ossEditForm.componentType" style="width: 100%">
          <el-option value="NPM" :label="$t('ops.oss.type_npm')" />
          <el-option value="MAVEN" :label="$t('ops.oss.type_maven')" />
          <el-option value="SERVICE" :label="$t('ops.oss.type_service')" />
        </el-select>
      </el-form-item>
      <el-form-item label="Package / Ref">
        <el-input v-model="ossEditForm.packageRef" placeholder="NPM: element-plus · MAVEN: group:artifact · SERVICE: owner/repo" />
      </el-form-item>
      <el-form-item :label="$t('ops.oss.currentVersion')"><el-input v-model="ossEditForm.currentVersion" /></el-form-item>
      <el-form-item :label="$t('ops.oss.license')"><el-input v-model="ossEditForm.license" /></el-form-item>
      <el-form-item label="Repo"><el-input v-model="ossEditForm.repoUrl" /></el-form-item>
      <el-form-item :label="$t('ops.oss.purpose')"><el-input v-model="ossEditForm.purpose" /></el-form-item>
      <el-form-item :label="$t('ops.oss.integration')">
        <el-select v-model="ossEditForm.integrationStatus" style="width: 100%">
          <el-option value="FULL" :label="$t('ops.oss.integ_full')" />
          <el-option value="PARTIAL" :label="$t('ops.oss.integ_partial')" />
          <el-option value="PLANNED" :label="$t('ops.oss.integ_planned')" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('ops.oss.managedBy')">
        <el-select v-model="ossEditForm.managedBy" style="width: 100%">
          <el-option value="RENOVATE" :label="$t('ops.oss.managedRenovate')" />
          <el-option value="INTERNAL" :label="$t('ops.oss.managedInternal')" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="ossEditForm.componentType === 'SERVICE'" :label="$t('ops.oss.deployType')">
        <el-select v-model="ossEditForm.deployType" style="width: 100%" clearable>
          <el-option value="CONTAINER" :label="$t('ops.oss.deployContainer')" />
          <el-option value="SYSTEMD" :label="$t('ops.oss.deploySystemd')" />
          <el-option value="OTHER" :label="$t('ops.oss.deployOther')" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="ossEditForm.componentType === 'SERVICE'" :label="$t('ops.oss.probeMode')">
        <el-select v-model="ossEditForm.probeType" style="width: 100%" clearable :placeholder="$t('ops.oss.probeNone')">
          <el-option value="NEXTCLOUD_STATUS" :label="$t('ops.oss.probe_nextcloud')" />
          <el-option value="JELLYFIN_INFO" :label="$t('ops.oss.probe_jellyfin')" />
          <el-option value="HA_CONFIG" :label="$t('ops.oss.probe_ha')" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="ossEditForm.componentType === 'SERVICE' && ossEditForm.probeType" :label="$t('ops.oss.probeUrl')">
        <el-input v-model="ossEditForm.probeUrl" :placeholder="$t('ops.oss.probeUrlHint')" />
      </el-form-item>
      <el-form-item v-if="ossEditForm.probeType === 'HA_CONFIG'" :label="$t('ops.oss.probeToken')">
        <el-input
          v-model="ossEditForm.probeToken"
          type="password"
          show-password
          :placeholder="ossEditForm.hasProbeToken ? $t('ops.oss.probeTokenKeep') : $t('ops.oss.probeTokenHint')"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="ossEditVisible = false">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" @click="saveOss">{{ $t('common.save') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { opsApi } from '@/api'
import { ElMessage } from 'element-plus'
import { formatDateTime } from '@/utils/datetime'

const { t } = useI18n()

// ---------- 开源组件台账(版本检测 + 升级提示) ----------
const ossLoading = ref(false)
const ossChecking = ref(false)
const ossRows = ref([])
const ossLoaded = ref(false)
const ossLastChecked = ref(null)
const ossUpdatable = ref(0)

const ossPlanVisible = ref(false)
const ossPlan = ref(null)
const ossCopied = ref(false)

const ossEditVisible = ref(false)
const ossEditForm = reactive({
  id: null,
  name: '',
  componentType: 'NPM',
  packageRef: '',
  currentVersion: '',
  license: '',
  repoUrl: '',
  purpose: '',
  integrationStatus: 'FULL',
  managedBy: '',
  deployType: '',
  probeType: '',
  probeUrl: '',
  probeToken: '',
  hasProbeToken: false,
})
const ossFilter = reactive({ keyword: '', componentType: '', updateType: '', status: '', integrationStatus: '' })
const ossPlanComponent = ref(null)
const ossAssess = ref(null)
const ossAssessing = ref(false)
const probingOssId = ref(null)

const loadOss = async () => {
  ossLoading.value = true
  try {
    const [rows, sum] = await Promise.all([opsApi.ossList(), opsApi.ossSummary()])
    ossRows.value = rows || []
    ossUpdatable.value = sum?.updatable ?? 0
    ossLastChecked.value = sum?.lastCheckedAt ?? null
    ossLoaded.value = true
  } catch (e) {
    ossRows.value = []
  } finally {
    ossLoading.value = false
  }
}

const checkOss = async () => {
  ossChecking.value = true
  try {
    ossRows.value = (await opsApi.ossCheck()) || []
    const sum = await opsApi.ossSummary()
    ossUpdatable.value = sum?.updatable ?? 0
    ossLastChecked.value = sum?.lastCheckedAt ?? null
    ossLoaded.value = true
  } finally {
    ossChecking.value = false
  }
}

const isOssUpdatable = (row) => row.status === 'ACTIVE' && row.updateType && row.updateType !== 'NONE'
const ossTypeTag = (t) => (t === 'MAVEN' ? 'success' : t === 'SERVICE' ? 'warning' : 'primary')
const ossUpdateTag = (ut) => (ut === 'MAJOR' ? 'danger' : ut === 'MINOR' ? 'warning' : 'info')

const filteredOssRows = computed(() => {
  const kw = (ossFilter.keyword || '').trim().toLowerCase()
  return ossRows.value.filter((r) => {
    if (ossFilter.componentType && r.componentType !== ossFilter.componentType) return false
    if (ossFilter.updateType && r.updateType !== ossFilter.updateType) return false
    if (ossFilter.status && r.status !== ossFilter.status) return false
    if (ossFilter.integrationStatus && r.integrationStatus !== ossFilter.integrationStatus) return false
    if (kw) {
      const hay = [r.name, r.packageRef, r.purpose, r.license].filter(Boolean).join(' ').toLowerCase()
      if (!hay.includes(kw)) return false
    }
    return true
  })
})

const openOssPlan = async (row) => {
  try {
    ossPlan.value = await opsApi.ossUpgradePlan(row.id)
    ossPlanComponent.value = row
    ossAssess.value = null
    ossCopied.value = false
    ossPlanVisible.value = true
  } catch (e) {
    /* 错误由 request.js 统一 toast */
  }
}

const ossAssessRiskTag = (level) => (level === 'HIGH' ? 'danger' : level === 'MEDIUM' ? 'warning' : 'success')

// 当前版本自动探测:探到就回写当前版本,没探到后端会把原因写进 probeMessage,原样提示给运维
const probeOss = async (row) => {
  probingOssId.value = row.id
  try {
    const updated = await opsApi.ossProbe(row.id)
    const msg = updated?.probeMessage
    if (msg && msg.startsWith('探测失败')) ElMessage.warning(msg)
    else ElMessage.success(msg || t('common.success'))
    await loadOss()
  } catch (e) {
    /* 错误由 request.js 统一 toast */
  } finally {
    probingOssId.value = null
  }
}

const ossProbeTip = (row) => [row.probedAt ? fmtTime(row.probedAt) : '', row.probeMessage].filter(Boolean).join(' · ')

const assessOss = async (row) => {
  ossAssessing.value = true
  try {
    ossAssess.value = await opsApi.ossAssess(row.id)
  } catch (e) {
    /* 错误由 request.js 统一 toast */
  } finally {
    ossAssessing.value = false
  }
}

const requestOssUpgrade = async (row) => {
  await opsApi.ossUpgrade(row.id)
  ElMessage.success(t('ops.oss.genPrDone'))
  ossPlanVisible.value = false
  await loadOss()
}

const copyOssPlan = async () => {
  if (!ossPlan.value) return
  const text = ossPlan.value.steps.join('\n')
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
    } else {
      const ta = document.createElement('textarea')
      ta.value = text
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    }
    ossCopied.value = true
  } catch (e) {
    ElMessage.error(t('common.failed'))
  }
}

const confirmOss = async (row) => {
  await opsApi.ossConfirm(row.id)
  ElMessage.success(t('common.success'))
  await loadOss()
}

const ignoreOss = async (row, ignored) => {
  await opsApi.ossIgnore(row.id, ignored)
  await loadOss()
}

const openOssEdit = (row) => {
  Object.assign(ossEditForm, {
    id: row.id,
    name: row.name,
    componentType: row.componentType,
    packageRef: row.packageRef,
    currentVersion: row.currentVersion,
    license: row.license,
    repoUrl: row.repoUrl,
    purpose: row.purpose,
    integrationStatus: row.integrationStatus,
    managedBy: row.managedBy || '',
    deployType: row.deployType || '',
    probeType: row.probeType || '',
    probeUrl: row.probeUrl || '',
    probeToken: '',
    hasProbeToken: !!row.hasProbeToken,
  })
  ossEditVisible.value = true
}

const openOssAdd = () => {
  Object.assign(ossEditForm, {
    id: null,
    name: '',
    componentType: 'NPM',
    packageRef: '',
    currentVersion: '',
    license: '',
    repoUrl: '',
    purpose: '',
    integrationStatus: 'FULL',
    managedBy: '',
    deployType: '',
    probeType: '',
    probeUrl: '',
    probeToken: '',
    hasProbeToken: false,
  })
  ossEditVisible.value = true
}

const saveOss = async () => {
  if (!ossEditForm.name || !ossEditForm.packageRef || !ossEditForm.componentType) {
    ElMessage.warning(t('ops.oss.name') + ' / ' + t('ops.oss.requiredHint'))
    return
  }
  if (ossEditForm.id) {
    await opsApi.ossUpdate(ossEditForm.id, { ...ossEditForm })
  } else {
    await opsApi.ossAdd({ ...ossEditForm })
  }
  ossEditVisible.value = false
  ElMessage.success(t('common.success'))
  await loadOss()
}

const openRepo = (url) => {
  if (url) window.open(url, '_blank')
}
const fmtTime = (d) => {
  if (!d) return ''
  const date = new Date(d)
  return Number.isNaN(date.getTime()) ? String(d) : formatDateTime(date)
}

onMounted(loadOss)
</script>

<style scoped>
.filter-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 16px;
  align-items: center;
}
.mono {
  font-family: Consolas, Monaco, 'Courier New', monospace;
}
.traffic-hint {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.oss-purpose {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-top: 2px;
}
.oss-none {
  color: var(--color-text-secondary);
}
.oss-probe-tag {
  margin-left: 4px;
  font-size: 11px;
  padding: 0 4px;
  border-radius: 4px;
  background: var(--color-card-2);
  color: var(--color-text-secondary);
  cursor: help;
}
.oss-plan-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.oss-plan-steps {
  margin: 0;
  padding-left: 20px;
  line-height: 1.9;
}
.oss-plan-steps li {
  word-break: break-all;
}
.oss-assess {
  margin-top: 14px;
  padding: 12px 14px;
  background: var(--color-card-2);
  border-radius: 10px;
}
.oss-assess-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.oss-assess-body {
  margin-top: 10px;
}
.oss-assess-summary {
  margin: 0 0 8px;
  color: var(--color-text);
  line-height: 1.6;
}
.oss-assess-sub {
  font-size: 13px;
  font-weight: 600;
  margin: 8px 0 4px;
  color: var(--color-text-secondary);
}
.oss-assess-body ul,
.oss-assess-body ol {
  margin: 0;
  padding-left: 20px;
  line-height: 1.8;
}
.oss-assess-note {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--color-text-secondary);
}
</style>
