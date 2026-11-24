<!-- 窗下桌面:爪印与桌边小物。embedded=true 时只出内容块,由父卡(暖居)负责卡面与间距。 -->
<template>
  <div :class="embedded ? 'desk-block' : 'card settings-card'">
    <div class="section-label">{{ $t('windowDesk.title') }}</div>
    <el-form label-position="top" class="settings-form">
      <el-form-item>
        <el-switch v-model="desk.enabled" :aria-label="$t(switchLabel)" :active-text="$t(switchLabel)" />
        <div class="share-tip">{{ $t('windowDesk.pawHint') }}</div>
      </el-form-item>
      <template v-if="desk.enabled && showObjects">
        <div class="share-tip">{{ $t('windowDesk.hint') }}</div>
        <el-form-item :label="$t('windowDesk.bind')">
          <div class="desk-bindings">
            <el-select v-model="kind" :aria-label="$t('windowDesk.chooseObject')">
              <el-option v-for="(_, key) in DESK_DEFAULT_PATHS" :key="key" :value="key" :label="$t('windowDesk.items.' + key)" />
            </el-select>
            <span aria-hidden="true">→</span>
            <el-select
              :model-value="target"
              :aria-label="$t('windowDesk.chooseDestination')"
              :placeholder="$t('windowDesk.chooseDestination')"
              @change="setTarget"
            >
              <el-option v-for="entry in available" :key="entry.path" :value="entry.path" :label="$t(entry.labelKey)" />
            </el-select>
          </div>
        </el-form-item>
        <el-button @click="desk.arrangement++">{{ $t('windowDesk.shuffle') }}</el-button>
      </template>
    </el-form>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useDisplayMode } from '@/composables/useDisplayMode'
import { useAppStore } from '@/stores/app'
import { useWindowDeskStore } from '@/stores/windowDesk'
import { DESK_DEFAULT_PATHS, DESK_DESTINATIONS, deskPathAllowed } from '@/utils/deskObjects'

const props = defineProps({ showObjects: Boolean, embedded: Boolean })
const switchLabel = computed(() => (props.showObjects ? 'windowDesk.enabled' : 'windowDesk.pawsEnabled'))
const desk = useWindowDeskStore()
const app = useAppStore()
const { isChild } = useDisplayMode()
const available = computed(() => DESK_DESTINATIONS.filter(({ path }) => deskPathAllowed(path, app.modules, isChild.value)))
const kind = ref('cup')
const target = computed(() => {
  const paths = available.value.map((entry) => entry.path)
  return [desk.destinations[kind.value], DESK_DEFAULT_PATHS[kind.value]].find((path) => paths.includes(path))
})
const setTarget = (path) => desk.setDestination(kind.value, path === DESK_DEFAULT_PATHS[kind.value] ? null : path)
</script>

<style scoped>
.settings-card {
  margin-bottom: 24px;
  background: var(--color-card);
}
/* 嵌在暖居卡里:标题降一档,不另起卡面(分隔线由父组件的 el-divider 画) */
.desk-block .section-label {
  font-size: 14px;
  margin-bottom: 10px;
}
.desk-bindings {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  color: var(--color-text-secondary);
}
.desk-bindings .el-select {
  width: 9em;
}
</style>
