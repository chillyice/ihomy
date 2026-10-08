<template>
  <!-- 每日内容 -->
  <div class="card settings-card">
    <div class="section-label">{{ $t('settings.dailyContent') }}</div>
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
      <div class="form-footer">
        <el-button type="primary" @click="saveDaily">{{ $t('settings.saveDaily') }}</el-button>
      </div>
    </el-form>
  </div>
</template>

<script setup>
import { reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'

const { t } = useI18n()

// 每日内容偏好:开启开关 + 知识分类,存 localStorage(纯前端展示偏好,不落库)
const KNOWLEDGE_TYPES = [{ key: 'history' }, { key: 'science' }, { key: 'literature' }, { key: 'life' }]
const daily = reactive(JSON.parse(localStorage.getItem('ihomy-daily') || '{}'))
daily.imageOn ??= true
daily.knowledgeOn ??= true
daily.types ??= ['history', 'life']
const saveDaily = () => {
  localStorage.setItem('ihomy-daily', JSON.stringify(daily))
  ElMessage.success(t('settings.dailySaved'))
}
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
/* 表单提交按钮:右下角对齐 */
.form-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 4px;
}
@media (max-width: 768px) {
  .settings-card .el-input,
  .settings-card .el-select {
    width: 100%;
  }
}
</style>
