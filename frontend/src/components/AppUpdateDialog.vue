<template>
  <!-- 应用内更新弹窗(仅原生壳):状态机在 native/appUpdate.js,这里只渲染与转发点击 -->
  <el-dialog
    :model-value="state.visible"
    class="dialog-sm"
    :title="title"
    :show-close="!state.required"
    :close-on-click-modal="false"
    :close-on-press-escape="!state.required"
    append-to-body
    @update:model-value="onVisible"
  >
    <div class="upd-body">
      <p class="upd-tip" :class="{ 'is-required': state.required }">
        {{ state.required ? $t('appUpdate.requiredTip') : $t('appUpdate.available') }}
      </p>

      <div v-if="state.notes" class="upd-notes">
        <div class="upd-notes-label">{{ $t('appUpdate.notesTitle') }}</div>
        <div class="upd-notes-text">{{ state.notes }}</div>
      </div>

      <div v-if="state.phase === 'downloading'" class="upd-progress">
        <el-progress :percentage="state.percent" :stroke-width="8" :show-text="false" />
        <div class="upd-size">
          {{ $t('appUpdate.downloading', { percent: state.percent }) }}
          <span v-if="state.total"
            >· {{ $t('appUpdate.downloadingSize', { done: formatBytes(state.done), total: formatBytes(state.total) }) }}</span
          >
        </div>
        <div class="upd-hint">{{ $t('appUpdate.keepForeground') }}</div>
      </div>

      <div v-else-if="state.phase === 'installer'" class="upd-hint">{{ $t('appUpdate.installerTip') }}</div>
      <div v-else-if="state.phase === 'permission'" class="upd-hint is-warn">{{ $t('appUpdate.permissionTip') }}</div>
      <div v-else-if="state.phase === 'error'" class="upd-hint is-error">{{ state.error }}</div>
    </div>

    <template #footer>
      <div class="upd-actions">
        <el-button v-if="!state.required && state.phase !== 'downloading'" @click="dismissUpdate">
          {{ $t('appUpdate.later') }}
        </el-button>

        <el-button v-if="state.phase === 'available'" type="primary" @click="onPrimary">
          {{ state.downloaded ? $t('appUpdate.installReady') : $t('appUpdate.installNow') }}
        </el-button>

        <el-button v-else-if="state.phase === 'downloading'" type="primary" disabled>
          {{ $t('appUpdate.downloading', { percent: state.percent }) }}
        </el-button>

        <el-button v-else-if="state.phase === 'installer'" type="primary" @click="installNow">
          {{ $t('appUpdate.reopenInstaller') }}
        </el-button>

        <template v-else-if="state.phase === 'permission'">
          <el-button type="primary" @click="openPermissionSettings">{{ $t('appUpdate.goSettings') }}</el-button>
          <el-button link @click="installNow">{{ $t('appUpdate.grantedRetry') }}</el-button>
        </template>

        <el-button v-else-if="state.phase === 'error'" type="primary" @click="startDownload">
          {{ $t('appUpdate.retry') }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatBytes } from '@/utils/appUpdate'
import { dismissUpdate, installNow, openPermissionSettings, startDownload, updateState as state } from '@/native/appUpdate'

const { t } = useI18n()

const title = computed(() =>
  state.required ? t('appUpdate.titleRequired', { version: state.version }) : t('appUpdate.title', { version: state.version }),
)

function onVisible(visible) {
  if (!visible) dismissUpdate()
}

/** 已下好就直接装,否则开始后台下载 */
function onPrimary() {
  if (state.downloaded) installNow()
  else startDownload()
}
</script>

<style scoped>
.upd-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.upd-tip {
  margin: 0;
  font-size: 13.5px;
  line-height: 1.6;
  color: var(--color-text-secondary, #6b6b6b);
}
.upd-tip.is-required {
  color: var(--el-color-warning);
}
.upd-notes {
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--el-fill-color-light);
}
.upd-notes-label {
  font-size: 12px;
  margin-bottom: 4px;
  color: var(--color-text-secondary, #8a8a8a);
}
.upd-notes-text {
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
.upd-progress {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.upd-size {
  font-size: 12.5px;
  color: var(--color-text-secondary, #8a8a8a);
}
.upd-hint {
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--color-text-secondary, #8a8a8a);
}
.upd-hint.is-warn {
  color: var(--el-color-warning);
}
.upd-hint.is-error {
  color: var(--el-color-danger);
}
.upd-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}
</style>
