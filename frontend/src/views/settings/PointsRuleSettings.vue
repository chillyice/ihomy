<template>
  <!-- 积分获取规则:家长配置各功能能否得积分、得多少 -->
  <div class="card settings-card" v-loading="pointsRuleLoading">
    <div class="section-label">{{ $t('settings.pointsRule.title') }}</div>
    <div class="share-tip">{{ $t('settings.pointsRule.hint') }}</div>
    <div v-for="g in pointsRuleGroups" :key="g.code" class="points-rule-group">
      <div class="ai-sub-label">{{ $t('settings.pointsRule.group.' + g.code) }}</div>
      <div v-for="r in pointsRuleGroup(g.code)" :key="r.featureCode" class="ai-feature-row">
        <span class="ai-feature-name">{{ $t('settings.pointsRule.feature.' + r.featureCode) }}</span>
        <el-switch v-model="r.enabled" />
        <el-input-number v-if="r.hasPoints" v-model="r.points" :min="0" :max="9999" :step="1" class="points-rule-input" />
        <span v-else class="points-rule-na">{{ $t('settings.pointsRule.taskPoints') }}</span>
      </div>
    </div>
    <div class="ai-toolbar">
      <el-button type="primary" :loading="pointsRuleSaving" @click="savePointsRule">{{ $t('common.save') }}</el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { pointsApi } from '@/api'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const userStore = useUserStore()

// ===== 积分获取规则(家长配置各功能能否得积分、得多少) =====
const canManagePointsRule = computed(() => userStore.hasPerm('points:manage'))
const pointsRules = ref([])
const pointsRuleLoading = ref(false)
const pointsRuleSaving = ref(false)
const pointsRuleGroups = [{ code: 'content' }, { code: 'task' }, { code: 'game' }, { code: 'garden' }]
const pointsRuleGroup = (code) => pointsRules.value.filter((r) => r.group === code)

const loadPointsRule = async () => {
  if (!userStore.isLoggedIn || !canManagePointsRule.value) return
  pointsRuleLoading.value = true
  try {
    const rules = await pointsApi.rules()
    pointsRules.value = rules || []
  } catch (e) {
    // 拦截器已提示(非家长 403 时静默)
  } finally {
    pointsRuleLoading.value = false
  }
}

const savePointsRule = async () => {
  pointsRuleSaving.value = true
  try {
    const body = pointsRules.value.map((r) => ({
      featureCode: r.featureCode,
      enabled: r.enabled,
      points: r.hasPoints ? r.points : null,
    }))
    await pointsApi.saveRules(body)
    ElMessage.success(t('settings.pointsRule.saved'))
    await loadPointsRule()
  } catch (e) {
    // 拦截器已提示
  } finally {
    pointsRuleSaving.value = false
  }
}

onMounted(() => {
  loadPointsRule()
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

/* 积分获取规则:分组 + 数值输入 */
.points-rule-group {
  margin-bottom: 4px;
}
.points-rule-input {
  width: 120px;
}
.points-rule-na {
  font-size: 13px;
  color: var(--color-text-secondary);
}

@media (max-width: 768px) {
  .settings-card .el-input,
  .settings-card .el-select {
    width: 100%;
  }
}
</style>
