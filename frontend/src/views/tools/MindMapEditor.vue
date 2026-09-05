<template>
  <div class="page mm-page">
    <div class="mm-topbar">
      <el-tooltip :content="$t('tools.mindmap.back')" placement="bottom">
        <el-button :icon="ArrowLeft" circle size="small" @click="goList" />
      </el-tooltip>
      <div class="mm-title" @click="rename">
        <span class="mm-title-text">{{ title }}</span>
        <el-icon class="mm-title-edit"><EditPen /></el-icon>
      </div>
      <div class="mm-tools">
        <el-tooltip :content="$t('tools.mindmap.undo')" placement="bottom">
          <el-button :icon="RefreshLeft" circle size="small" :disabled="!inited" @click="exec('BACK')" />
        </el-tooltip>
        <el-tooltip :content="$t('tools.mindmap.redo')" placement="bottom">
          <el-button :icon="RefreshRight" circle size="small" :disabled="!inited" @click="exec('FORWARD')" />
        </el-tooltip>
        <el-tooltip :content="$t('tools.mindmap.addChild')" placement="bottom">
          <el-button :icon="CirclePlus" circle size="small" :disabled="!inited" @click="exec('INSERT_CHILD_NODE')" />
        </el-tooltip>
        <el-tooltip :content="$t('tools.mindmap.addSibling')" placement="bottom">
          <el-button :icon="Plus" circle size="small" :disabled="!inited" @click="exec('INSERT_NODE')" />
        </el-tooltip>
        <el-tooltip :content="$t('tools.mindmap.removeNode')" placement="bottom">
          <el-button :icon="Delete" circle size="small" :disabled="!inited" @click="exec('REMOVE_NODE')" />
        </el-tooltip>
        <el-tooltip :content="$t('tools.mindmap.center')" placement="bottom">
          <el-button :icon="Aim" circle size="small" :disabled="!inited" @click="exec('CENTER_ROOT')" />
        </el-tooltip>
      </div>
      <div class="mm-right">
        <el-select v-model="layout" size="small" class="mm-select" @change="onLayoutChange">
          <el-option v-for="l in layoutOptions" :key="l.value" :label="l.label" :value="l.value" />
        </el-select>
        <el-select v-model="themeTemplate" size="small" class="mm-select" @change="onThemeChange">
          <el-option v-for="th in themeOptions" :key="th.value" :label="th.label" :value="th.value" />
        </el-select>
        <el-dropdown trigger="click" @command="onExport">
          <el-button size="small">
            {{ $t('tools.mindmap.export') }}
            <el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="png">{{ $t('tools.mindmap.exportPng') }}</el-dropdown-item>
              <el-dropdown-item command="svg">{{ $t('tools.mindmap.exportSvg') }}</el-dropdown-item>
              <el-dropdown-item command="json">{{ $t('tools.mindmap.exportJson') }}</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <span class="mm-save-state" :class="saveState">{{ saveStateText }}</span>
        <el-button type="primary" size="small" :loading="saving" @click="saveNow">{{ $t('tools.mindmap.save') }}</el-button>
      </div>
    </div>
    <div ref="elRef" class="mm-canvas"></div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft, ArrowDown, EditPen, RefreshLeft, RefreshRight,
  CirclePlus, Plus, Delete, Aim,
} from '@element-plus/icons-vue'
import MindMap from 'simple-mind-map'
import Drag from 'simple-mind-map/src/plugins/Drag.js'
import Export from 'simple-mind-map/src/plugins/Export.js'
import MiniMap from 'simple-mind-map/src/plugins/MiniMap.js'
import KeyboardNavigation from 'simple-mind-map/src/plugins/KeyboardNavigation.js'
import Select from 'simple-mind-map/src/plugins/Select.js'
import TouchEvent from 'simple-mind-map/src/plugins/TouchEvent.js'
import { mindmapApi } from '@/api'

// 插件注册(模块级一次):拖拽/导出/小地图/键盘导航/多选/触屏
MindMap.usePlugin(Drag)
MindMap.usePlugin(Export)
MindMap.usePlugin(MiniMap)
MindMap.usePlugin(KeyboardNavigation)
MindMap.usePlugin(Select)
MindMap.usePlugin(TouchEvent)

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const id = route.params.id
const elRef = ref(null)
let mm = null
let saveTimer = null
let initing = false

const inited = ref(false)
const title = ref('')
const layout = ref('logicalStructure')
const themeTemplate = ref('default')
const saveState = ref('saved') // saved | dirty | saving | failed
const saving = ref(false)
const savedAt = ref('')

const layoutOptions = [
  { value: 'logicalStructure', label: t('tools.mindmap.layoutLogical') },
  { value: 'mindMap', label: t('tools.mindmap.layoutMindMap') },
  { value: 'organizationStructure', label: t('tools.mindmap.layoutOrganization') },
  { value: 'catalogOrganization', label: t('tools.mindmap.layoutCatalog') },
  { value: 'fishbone', label: t('tools.mindmap.layoutFishbone') },
]
const themeOptions = [
  { value: 'default', label: t('tools.mindmap.themeDefault') },
  { value: 'classic', label: t('tools.mindmap.themeClassic') },
  { value: 'dark', label: t('tools.mindmap.themeDark') },
  { value: 'blackGold', label: t('tools.mindmap.themeBlackGold') },
  { value: 'avocado', label: t('tools.mindmap.themeAvocado') },
  { value: 'mintGreen', label: t('tools.mindmap.themeMintGreen') },
  { value: 'blueSky', label: t('tools.mindmap.themeBlueSky') },
  { value: 'colored', label: t('tools.mindmap.themeColored') },
]

const saveStateText = computed(() => {
  if (saveState.value === 'dirty') return t('tools.mindmap.dirty')
  if (saveState.value === 'saving') return t('tools.mindmap.saving')
  if (saveState.value === 'failed') return t('tools.mindmap.saveFailed')
  return t('tools.mindmap.saved', { time: savedAt.value })
})

const load = async () => {
  let record
  try {
    record = await mindmapApi.detail(id)
  } catch (e) {
    ElMessage.error(t('tools.mindmap.loadFailed'))
    router.replace({ name: 'MindMapList' })
    return
  }
  title.value = record.title
  let root = { data: { text: record.title, expand: true }, children: [] }
  let theme = null
  if (record.data) {
    try {
      const parsed = JSON.parse(record.data)
      if (parsed && parsed.root) {
        root = parsed.root
        if (parsed.theme && parsed.theme.template && themeOptions.some(o => o.value === parsed.theme.template)) {
          theme = parsed.theme
        }
        if (parsed.layout && layoutOptions.some(o => o.value === parsed.layout)) {
          layout.value = parsed.layout
        }
      }
    } catch (e) { /* 数据损坏时用默认根节点重建 */ }
  }
  await nextTick()
  init(root, theme)
  inited.value = true
}

const init = (root, theme) => {
  mm = new MindMap({
    el: elRef.value,
    data: root,
    layout: layout.value,
    theme: { template: themeTemplate.value, config: theme?.config || {} },
  })
  mm.on('data_change', () => scheduleSave())
  initing = true
  if (theme) {
    themeTemplate.value = theme.template
    mm.setTheme(theme.template)
    if (theme.config) mm.setThemeConfig(theme.config)
  }
  nextTick(() => {
    initing = false
    // 不调用 view.fit():库的渲染是 setTimeout 异步的,fit 在空画布上会把视图平移半个画布,
    // 导致内容双重偏移被裁出视口(画布空白);布局本身以画布中心为原点渲染,无需初始平移
  })
}

const exec = (cmd) => {
  if (!mm) return
  try {
    mm.execCommand(cmd)
  } catch (e) { /* 无选中节点时静默忽略 */ }
}

const scheduleSave = () => {
  if (initing) return
  saveState.value = 'dirty'
  clearTimeout(saveTimer)
  saveTimer = setTimeout(saveNow, 1500)
}

const saveNow = async () => {
  if (!mm || saving.value) return
  clearTimeout(saveTimer)
  saving.value = true
  saveState.value = 'saving'
  try {
    await mindmapApi.update(id, { title: title.value, data: JSON.stringify(mm.getData(true)) })
    savedAt.value = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    saveState.value = 'saved'
  } catch (e) {
    saveState.value = 'failed'
  } finally {
    saving.value = false
  }
}

const rename = async () => {
  const { value } = await ElMessageBox.prompt(t('tools.mindmap.titlePrompt'), t('tools.mindmap.rename'), {
    inputValue: title.value,
    inputValidator: (v) => !!(v && v.trim()) || t('tools.mindmap.titlePrompt'),
  })
  title.value = value.trim()
  saveNow()
}

const onLayoutChange = () => {
  if (!mm) return
  mm.setLayout(layout.value)
  scheduleSave()
}

const onThemeChange = () => {
  if (!mm) return
  mm.setTheme(themeTemplate.value)
  scheduleSave()
}

const onExport = (command) => {
  if (!mm) return
  mm.export(command, true, title.value || 'mindmap')
}

const goList = () => {
  router.push({ name: 'MindMapList' })
}

const onResize = () => mm && mm.resize()
const onBeforeUnload = (e) => {
  if (saveState.value === 'dirty' || saveState.value === 'saving') {
    e.preventDefault()
    e.returnValue = ''
  }
}

onMounted(() => {
  load()
  window.addEventListener('resize', onResize)
  window.addEventListener('beforeunload', onBeforeUnload)
})

onBeforeUnmount(() => {
  clearTimeout(saveTimer)
  window.removeEventListener('resize', onResize)
  window.removeEventListener('beforeunload', onBeforeUnload)
  if (mm) {
    mm.destroy()
    mm = null
  }
})
</script>

<style scoped>
/* 全屏画布型编辑页:参照 Item.vue 的 .fp-page 模式覆写 .page 宽高限制 */
.mm-page { display: flex; flex-direction: column; max-width: none; width: 100%; height: 100vh; height: 100dvh; }
.mm-topbar { display: flex; align-items: center; gap: 10px; padding: 10px 16px; flex-wrap: wrap; }
.mm-title { display: flex; align-items: center; gap: 6px; cursor: pointer; min-width: 0; }
.mm-title-text {
  font-size: 16px;
  font-weight: 600;
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mm-title-edit { color: var(--color-text-secondary, #8a8a8a); }
.mm-tools { display: flex; align-items: center; gap: 8px; margin-left: 8px; }
.mm-right { display: flex; align-items: center; gap: 10px; margin-left: auto; flex-wrap: wrap; }
.mm-select { width: 116px; }
.mm-save-state { font-size: 12px; color: var(--color-text-secondary, #8a8a8a); }
.mm-save-state.failed { color: var(--color-danger, #e05c5c); }
.mm-save-state.dirty { color: var(--color-warning, #d9a24a); }
.mm-canvas {
  position: relative;
  flex: 1;
  overflow: hidden;
  border-radius: 14px;
  border: 1px solid var(--color-border);
  background: var(--color-card);
}
@media (max-width: 768px) {
  .mm-page { height: calc(100dvh - 120px); }
  .mm-title-text { max-width: 140px; }
}
</style>
