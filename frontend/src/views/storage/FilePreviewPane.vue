<!-- 文件预览内容:宽屏右侧预览栏与窄屏 70% 弹窗共用同一份(不复制两套预览)。
     只负责展示,不开请求;关闭由外层决定(弹窗关 dialog / 右栏清选中)。 -->
<template>
  <div class="preview-pane">
    <div class="pp-head">
      <div class="pp-text">
        <span class="pp-title" :title="name">{{ name }}</span>
        <span v-if="meta" class="pp-meta">{{ meta }}</span>
      </div>
      <el-tooltip :content="$t('common.close')" placement="top" :show-after="300">
        <el-button text size="small" :aria-label="$t('common.close')" @click="$emit('close')">
          <el-icon><Close /></el-icon>
        </el-button>
      </el-tooltip>
    </div>
    <div class="pp-body">
      <img v-if="isImage" :src="src" class="pp-img" :alt="name || ''" />
      <video v-else-if="isVideo" :src="src" class="pp-video" controls autoplay />
      <div v-else class="pp-none">{{ $t('storage.noPreview') }}</div>
    </div>
  </div>
</template>

<script setup>
import { Close } from '@element-plus/icons-vue'

defineProps({
  src: { type: String, default: '' },
  name: { type: String, default: '' },
  meta: { type: String, default: '' },
  isImage: { type: Boolean, default: false },
  isVideo: { type: Boolean, default: false },
})
defineEmits(['close'])
</script>

<style scoped>
.preview-pane {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.pp-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 0 0 8px;
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 10px;
}
.pp-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.pp-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pp-meta {
  font-size: 12px;
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pp-body {
  min-height: 160px;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
}
.pp-img,
.pp-video {
  max-width: 100%;
  max-height: 70vh;
  border-radius: 8px;
}
.pp-none {
  padding: 40px 16px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
