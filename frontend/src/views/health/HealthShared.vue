<!-- 健康报表分享快照(公开只读页 /health/shared/:token):
     免登录,只展示分享者生成那一刻冻结的报告;不拉任何健康业务接口,也拿不到报告外的数据。
     链接 7 天后由服务端删除,过期/被删都按「已失效」处理,页面本身可整页截图。 -->
<template>
  <div class="page shared-page">
    <div v-if="loading" class="card state-card">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>{{ $t('health.shared.loading') }}</span>
    </div>

    <div v-else-if="error" class="card state-card">
      <div class="state-title">{{ $t('health.shared.expired') }}</div>
      <div class="state-hint">{{ $t('health.shared.expiredHint') }}</div>
      <router-link to="/home">
        <el-button type="primary">{{ $t('health.shared.backHome') }}</el-button>
      </router-link>
    </div>

    <template v-else-if="data">
      <div class="card shared-head">
        <div class="shared-title">{{ $t('health.shared.title', { name: ownerName }) }}</div>
        <div class="shared-meta">
          {{ $t('health.shared.by', { name: ownerName }) }} ·
          {{ $t('health.shared.expire', { date: expireText }) }}
        </div>
        <div class="shared-hint">{{ $t('health.shared.readOnly') }}</div>
      </div>
      <HealthReport :report="data.report" read-only />
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Loading } from '@element-plus/icons-vue'
import { publicApi } from '@/api'
import HealthReport from './HealthReport.vue'

const route = useRoute()
const data = ref(null)
const loading = ref(true)
const error = ref(false)

const ownerName = computed(() => data.value?.share?.ownerName || '—')
const expireText = computed(() => String(data.value?.share?.expireAt || '').slice(0, 10) || '—')

onMounted(async () => {
  try {
    data.value = await publicApi.healthShared(route.params.token)
    if (!data.value?.report) error.value = true
  } catch (e) {
    error.value = true
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.shared-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.state-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 40px 20px;
  text-align: center;
}
.state-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
}
.state-hint {
  max-width: 420px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.shared-head {
  padding: 16px 18px;
}
.shared-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--color-text-primary);
}
.shared-meta {
  margin-top: 6px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.shared-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
</style>
