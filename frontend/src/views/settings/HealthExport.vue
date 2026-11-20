<!-- 健康数据导出(设置 → 健康数据 → 导出):把最近 90 天的睡眠、分期与样本导成一份 CSV。
     与「导入」分开一张卡,是为了让设置页的顺序与阅读顺序一致(连接 → 同步 → 导入 → 导出 → 隐私)。
     导出范围与报表页原先一致,都是近 90 天;逐点数据的保留期见导入卡的说明。 -->
<template>
  <div class="card settings-card health-export">
    <div class="section-label">{{ $t('settings.health.exportTitle') }}</div>
    <div class="share-tip">{{ $t('settings.health.exportHint') }}</div>
    <div class="export-row">
      <el-button :loading="exporting" @click="exportCsv">
        <el-icon><Download /></el-icon>
        <span>{{ $t('settings.health.exportBtn') }}</span>
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import { healthApi } from '@/api'
import { saveBlobResponse } from '@/utils/download'

const { t } = useI18n()

const EXPORT_DAYS = 90
const exporting = ref(false)

const shiftDay = (offset) => {
  const d = new Date()
  d.setHours(12, 0, 0, 0)
  d.setDate(d.getDate() + offset)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

const exportCsv = async () => {
  const from = shiftDay(-(EXPORT_DAYS - 1))
  const to = shiftDay(0)
  exporting.value = true
  try {
    const blob = await healthApi.exportCsv({ from, to })
    await saveBlobResponse(blob, `health-${from}_${to}.csv`)
    ElMessage.success(t('settings.health.exportDone', { from, to }))
  } catch (e) {
    ElMessage.error(e?.message || t('common.failed'))
  } finally {
    exporting.value = false
  }
}
</script>

<style scoped>
.health-export {
  margin-top: 16px;
}
.export-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
}
</style>
