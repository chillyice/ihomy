<template>
  <!-- 家庭 AI 配置:模型池 + 功能绑定(家长可编辑) -->
  <div class="card settings-card" v-loading="aiLoading">
    <div class="section-label">{{ $t('settings.ai.title') }}</div>
    <div class="share-tip">{{ $t('settings.ai.hint') }}</div>

    <div class="ai-sub-label">{{ $t('settings.ai.models') }}</div>
    <div class="ai-toolbar">
      <el-button type="primary" plain @click="openAiModel()">{{ $t('settings.ai.addModel') }}</el-button>
    </div>
    <el-table :data="aiModels" stripe>
      <el-table-column prop="name" :label="$t('settings.ai.modelName')" min-width="140" show-overflow-tooltip />
      <el-table-column :label="$t('settings.ai.modelType')" width="170">
        <template #default="{ row }">
          <el-tag size="small">{{ $t('settings.ai.type.' + row.type) }}</el-tag>
          <el-tag v-if="row.provider === 'BAIDU'" size="small" class="ai-builtin">{{ $t('settings.ai.provider.BAIDU') }}</el-tag>
          <el-tag v-else-if="row.builtin" size="small" type="info" class="ai-builtin">{{ $t('settings.ai.builtin') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="model" :label="$t('settings.ai.model')" min-width="170" show-overflow-tooltip />
      <el-table-column prop="baseUrl" :label="$t('settings.ai.baseUrl')" min-width="190" show-overflow-tooltip />
      <el-table-column :label="$t('common.actions')" width="140">
        <template #default="{ row }">
          <template v-if="row.builtin">—</template>
          <template v-else>
            <el-tooltip :content="$t('common.edit')" placement="top" :show-after="300">
              <el-button size="small" text @click="openAiModel(row)"
                ><el-icon><Edit /></el-icon
              ></el-button>
            </el-tooltip>
            <el-tooltip :content="$t('common.delete')" placement="top" :show-after="300">
              <el-button size="small" text type="danger" @click="removeAiModel(row)"
                ><el-icon><Delete /></el-icon
              ></el-button>
            </el-tooltip>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <div class="ai-sub-label">{{ $t('settings.ai.features') }}</div>
    <div v-for="f in aiFeatures" :key="f.featureCode" class="ai-feature-row">
      <span class="ai-feature-name">{{ $t('settings.ai.feature.' + f.featureCode) }}</span>
      <el-select
        v-model="f.modelId"
        clearable
        filterable
        :placeholder="$t('settings.ai.noModel')"
        class="ai-feature-select"
        @change="bindAiFeature(f)"
      >
        <el-option v-for="m in modelsByTypes(f.modelTypes)" :key="m.id" :label="m.name + ' (' + m.model + ')'" :value="m.id" />
      </el-select>
      <el-select
        v-model="f.fallbackModelId"
        clearable
        filterable
        :placeholder="$t('settings.ai.noFallback')"
        class="ai-feature-select"
        @change="bindAiFeature(f)"
      >
        <el-option
          v-for="m in modelsByTypesExcluding(f.modelTypes, f.modelId)"
          :key="m.id"
          :label="m.name + ' (' + m.model + ')'"
          :value="m.id"
        />
      </el-select>
      <span
        class="ai-feature-state"
        :class="{ ok: f.available }"
        :title="f.available ? $t('settings.ai.available') : $t('settings.ai.unconfigured')"
      >
        <el-icon><CircleCheck v-if="f.available" /><CircleClose v-else /></el-icon>
      </span>
    </div>
  </div>

  <!-- 模型编辑对话框 -->
  <el-dialog v-model="aiModelDialog" append-to-body :title="aiModelForm.id ? $t('common.edit') : $t('settings.ai.addModel')" width="480px">
    <el-form :model="aiModelForm" label-width="110px">
      <el-form-item :label="$t('settings.ai.modelName')" required>
        <el-input v-model="aiModelForm.name" :placeholder="$t('settings.ai.modelNamePh')" />
      </el-form-item>
      <el-form-item :label="$t('settings.ai.modelType')" required>
        <el-select v-model="aiModelForm.type" style="width: 100%">
          <el-option :label="$t('settings.ai.type.LLM')" value="LLM" />
          <el-option :label="$t('settings.ai.type.IMAGE')" value="IMAGE" />
          <el-option :label="$t('settings.ai.type.ASR')" value="ASR" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="aiModelForm.type === 'ASR'" :label="$t('settings.ai.providerLabel')">
        <el-select v-model="aiModelForm.provider" style="width: 100%">
          <el-option :label="$t('settings.ai.provider.OPENAI')" value="OPENAI" />
          <el-option :label="$t('settings.ai.provider.BAIDU')" value="BAIDU" />
        </el-select>
      </el-form-item>
      <el-form-item :label="aiModelForm.provider === 'BAIDU' ? $t('settings.ai.baseUrlBaidu') : $t('settings.ai.baseUrl')">
        <el-input
          v-model="aiModelForm.baseUrl"
          :placeholder="aiModelForm.provider === 'BAIDU' ? $t('settings.ai.baseUrlBaiduPh') : $t('settings.ai.baseUrlPh')"
        />
      </el-form-item>
      <el-form-item :label="$t('settings.ai.apiKey')">
        <el-input
          v-model="aiModelForm.apiKey"
          type="password"
          show-password
          :placeholder="aiModelForm.apiKeySet ? $t('settings.ai.keyKeep') : $t('settings.ai.keyRequired')"
        />
      </el-form-item>
      <el-form-item v-if="aiModelForm.provider === 'BAIDU'" :label="$t('settings.ai.secretKey')">
        <el-input
          v-model="aiModelForm.secretKey"
          type="password"
          show-password
          :placeholder="aiModelForm.secretKeySet ? $t('settings.ai.keyKeep') : $t('settings.ai.secretKeyPh')"
        />
      </el-form-item>
      <el-form-item :label="$t('settings.ai.model')" required>
        <el-input
          v-model="aiModelForm.model"
          :placeholder="aiModelForm.provider === 'BAIDU' ? $t('settings.ai.modelBaiduPh') : $t('settings.ai.modelPh')"
        />
      </el-form-item>
      <el-form-item :label="$t('settings.ai.timeout')">
        <el-input-number
          v-model="aiModelForm.timeoutMs"
          :min="1000"
          :max="600000"
          :step="1000"
          :placeholder="$t('settings.ai.timeoutPh')"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="aiModelDialog = false">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="aiSaving" @click="saveAiModel">{{ $t('common.confirm') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { aiApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheck, CircleClose, Edit, Delete } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const userStore = useUserStore()

// 家庭 AI 配置:模型池 + 功能绑定(家长可编辑;密钥留空保留原值)
const canManageAi = computed(() => userStore.hasPerm('family:manage'))
const aiModels = ref([])
const aiFeatures = ref([])
const aiModelDialog = ref(false)
const aiModelForm = reactive({
  id: null,
  name: '',
  type: 'LLM',
  provider: 'OPENAI',
  baseUrl: '',
  apiKey: '',
  secretKey: '',
  model: '',
  timeoutMs: null,
  apiKeySet: false,
  secretKeySet: false,
})
const aiSaving = ref(false)
const aiLoading = ref(false)

// 切换模型类型:非语音模型强制回 OpenAI 协议(后端同口径),隐藏百度专属字段
watch(
  () => aiModelForm.type,
  (t) => {
    if (t !== 'ASR') aiModelForm.provider = 'OPENAI'
  },
)

const modelsByTypes = (types) => aiModels.value.filter((m) => (types || []).includes(m.type))
const modelsByTypesExcluding = (types, excludeId) => aiModels.value.filter((m) => (types || []).includes(m.type) && m.id !== excludeId)

const loadAiConfig = async () => {
  if (!userStore.isLoggedIn || !canManageAi.value) return
  aiLoading.value = true
  try {
    const [models, features] = await Promise.all([aiApi.models(), aiApi.features()])
    aiModels.value = models || []
    aiFeatures.value = (features || []).map((f) => ({ ...f, modelId: f.modelId ?? null, fallbackModelId: f.fallbackModelId ?? null }))
  } catch (e) {
    // 拦截器已提示(非家长 403 时静默)
  } finally {
    aiLoading.value = false
  }
}

const openAiModel = (row) => {
  Object.assign(
    aiModelForm,
    row
      ? {
          id: row.id,
          name: row.name,
          type: row.type,
          provider: row.provider || 'OPENAI',
          baseUrl: row.baseUrl || '',
          model: row.model || '',
          timeoutMs: row.timeoutMs ?? null,
          apiKey: '',
          secretKey: '',
          apiKeySet: row.apiKeySet,
          secretKeySet: row.secretKeySet,
        }
      : {
          id: null,
          name: '',
          type: 'LLM',
          provider: 'OPENAI',
          baseUrl: '',
          model: '',
          timeoutMs: null,
          apiKey: '',
          secretKey: '',
          apiKeySet: false,
          secretKeySet: false,
        },
  )
  aiModelDialog.value = true
}

const saveAiModel = async () => {
  if (!aiModelForm.name || !aiModelForm.type || !aiModelForm.model) {
    ElMessage.warning(t('settings.ai.requiredTip'))
    return
  }
  aiSaving.value = true
  try {
    const data = {
      name: aiModelForm.name,
      type: aiModelForm.type,
      provider: aiModelForm.provider || 'OPENAI',
      baseUrl: aiModelForm.baseUrl || '',
      model: aiModelForm.model || '',
      timeoutMs: aiModelForm.timeoutMs == null ? '' : String(aiModelForm.timeoutMs),
    }
    // 百度短语音:服务地址留空给默认识别接口
    if (data.provider === 'BAIDU' && !data.baseUrl) data.baseUrl = 'https://vop.baidu.com/server_api'
    // 密钥仅在输入时提交(留空=保留原值)
    if (aiModelForm.apiKey) data.apiKey = aiModelForm.apiKey
    if (aiModelForm.secretKey) data.secretKey = aiModelForm.secretKey
    if (aiModelForm.id) {
      await aiApi.updateModel(aiModelForm.id, data)
    } else {
      await aiApi.addModel(data)
    }
    ElMessage.success(t('settings.ai.saved'))
    aiModelDialog.value = false
    await loadAiConfig()
  } catch (e) {
    // 拦截器已提示
  } finally {
    aiSaving.value = false
  }
}

const removeAiModel = (row) => {
  ElMessageBox.confirm(t('settings.ai.deleteModelConfirm'), t('common.delete'), {
    confirmButtonText: t('common.confirm'),
    cancelButtonText: t('common.cancel'),
    type: 'warning',
    closeOnClickModal: true,
  })
    .then(async () => {
      try {
        await aiApi.deleteModel(row.id)
        ElMessage.success(t('settings.ai.deleteModelDone'))
        await loadAiConfig()
      } catch (e) {
        // 拦截器已提示
      }
    })
    .catch(() => {})
}

// 功能绑定:下拉选择后立即保存;失败回滚刷新
const bindAiFeature = async (f) => {
  const mid = f.modelId == null || f.modelId === '' ? null : f.modelId
  const fid = f.fallbackModelId == null || f.fallbackModelId === '' ? null : f.fallbackModelId
  try {
    await aiApi.bindFeature(f.featureCode, mid, fid)
    ElMessage.success(t('settings.ai.saved'))
    await loadAiConfig()
  } catch (e) {
    // 拦截器已提示
    await loadAiConfig()
  }
}

onMounted(() => {
  loadAiConfig()
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

/* 家庭 AI 配置:模型池 + 功能绑定 */
.ai-sub-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text);
  margin: 14px 0 8px;
}
.ai-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}
.ai-builtin {
  margin-left: 6px;
}
.ai-feature-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
}
.ai-feature-name {
  width: 96px;
  flex-shrink: 0;
  font-size: 13px;
  color: var(--color-text);
}
.ai-feature-select {
  flex: 1;
  min-width: 0;
}
.ai-feature-state {
  display: inline-flex;
  align-items: center;
  color: var(--color-text-secondary);
  white-space: nowrap;
}
.ai-feature-state .el-icon {
  font-size: 16px;
}
.ai-feature-state.ok {
  color: var(--color-primary);
}

/* 表格内编辑/删除图标按钮:与 fp-side-icons 紧凑间距一致(排除「设为默认」等主/次文本按钮) */
:deep(.el-table .el-button.is-text:not(.el-button--primary)) {
  padding: 5px 6px;
}
:deep(.el-table .el-button.is-text:not(.el-button--primary) + .el-button.is-text:not(.el-button--primary)) {
  margin-left: 4px;
}

@media (max-width: 768px) {
  .settings-card .el-input,
  .settings-card .el-select {
    width: 100%;
  }
}
</style>
