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
        <el-tooltip :content="$t('tools.mindmap.style')" placement="bottom">
          <el-button :icon="Brush" circle size="small" :disabled="!inited || !activeNodes.length" @click="toggleStylePanel" />
        </el-tooltip>
        <el-tooltip :content="$t('tools.mindmap.search')" placement="bottom">
          <el-button :icon="SearchIcon" circle size="small" :disabled="!inited" @click="openSearch" />
        </el-tooltip>
        <el-tooltip :content="$t('tools.mindmap.demonstrate')" placement="bottom">
          <el-button :icon="CaretRight" circle size="small" :disabled="!inited" @click="enterDemonstrate" />
        </el-tooltip>
        <el-tooltip :content="$t('tools.mindmap.history')" placement="bottom">
          <el-button :icon="Clock" circle size="small" :disabled="!inited" @click="openHistory" />
        </el-tooltip>
      </div>
      <div class="mm-right">
        <el-select v-model="layout" size="small" class="mm-select" @change="onLayoutChange">
          <el-option v-for="l in layoutOptions" :key="l.value" :label="l.label" :value="l.value" />
        </el-select>
        <el-select v-model="themeTemplate" size="small" class="mm-select" @change="onThemeChange">
          <el-option v-for="th in themeOptions" :key="th.value" :label="th.label" :value="th.value" />
        </el-select>
        <el-dropdown trigger="click" @command="onImport">
          <el-button size="small">
            {{ $t('tools.mindmap.import') }}
            <el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="xmind">{{ $t('tools.mindmap.importXmind') }}</el-dropdown-item>
              <el-dropdown-item command="json">{{ $t('tools.mindmap.importJson') }}</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
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
              <el-dropdown-item command="pdf" divided>{{ $t('tools.mindmap.exportPdf') }}</el-dropdown-item>
              <el-dropdown-item command="xmind">{{ $t('tools.mindmap.exportXmind') }}</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <span class="mm-save-state" :class="saveState">{{ saveStateText }}</span>
        <el-button type="primary" size="small" :loading="saving" @click="saveNow">{{ $t('tools.mindmap.save') }}</el-button>
      </div>
    </div>
    <div ref="elRef" class="mm-canvas" @contextmenu.prevent>
      <!-- 搜索替换栏 -->
      <div v-if="search.visible" class="mm-search card">
        <el-input
          ref="searchInputRef"
          v-model="search.text"
          size="small"
          class="mm-search-input"
          :placeholder="$t('tools.mindmap.searchPlaceholder')"
          clearable
          @keyup.enter="doSearch"
        />
        <span class="mm-search-count">{{ search.info.total ? (search.info.currentIndex + 1) + '/' + search.info.total : '0/0' }}</span>
        <el-button :icon="ArrowUp" size="small" circle :title="$t('tools.mindmap.prev')" @click="searchPrev" />
        <el-button :icon="ArrowDown" size="small" circle :title="$t('tools.mindmap.next')" @click="searchNext" />
        <el-input
          v-model="search.replaceText"
          size="small"
          class="mm-search-input"
          :placeholder="$t('tools.mindmap.replacePlaceholder')"
          @keyup.enter="doReplace"
        />
        <el-button size="small" @click="doReplace">{{ $t('tools.mindmap.replace') }}</el-button>
        <el-button size="small" @click="doReplaceAll">{{ $t('tools.mindmap.replaceAll') }}</el-button>
        <el-button :icon="Close" size="small" circle text @click="closeSearch" />
      </div>
      <!-- 节点样式面板 -->
      <div v-if="stylePanel.visible" class="mm-style-panel card">
        <div class="mm-sp-header">
          <span>{{ $t('tools.mindmap.styleTitle') }}</span>
          <el-button :icon="Close" size="small" circle text @click="stylePanel.visible = false" />
        </div>
        <template v-if="stylePanel.node">
          <div class="mm-sp-section">{{ $t('tools.mindmap.textSection') }}</div>
          <div class="mm-sp-row">
            <span class="mm-sp-label">{{ $t('tools.mindmap.textColor') }}</span>
            <el-color-picker size="small" :model-value="styleForm.color" @change="v => applyStyle('color', v)" />
            <span class="mm-sp-label">{{ $t('tools.mindmap.fontSize') }}</span>
            <el-input-number size="small" :min="10" :max="100" :model-value="styleForm.fontSize" @change="v => applyStyle('fontSize', v)" />
          </div>
          <div class="mm-sp-row">
            <el-checkbox size="small" :model-value="styleForm.bold" @change="v => applyStyle('fontWeight', v ? 'bold' : 'normal')">{{ $t('tools.mindmap.bold') }}</el-checkbox>
            <el-checkbox size="small" :model-value="styleForm.italic" @change="v => applyStyle('fontStyle', v ? 'italic' : 'normal')">{{ $t('tools.mindmap.italic') }}</el-checkbox>
            <el-checkbox size="small" :model-value="styleForm.underline" @change="v => applyStyle('textDecoration', v ? 'underline' : 'none')">{{ $t('tools.mindmap.underline') }}</el-checkbox>
          </div>
          <div class="mm-sp-section">{{ $t('tools.mindmap.nodeSection') }}</div>
          <div class="mm-sp-row">
            <span class="mm-sp-label">{{ $t('tools.mindmap.fillColor') }}</span>
            <el-color-picker size="small" :model-value="styleForm.fillColor" @change="v => applyStyle('fillColor', v)" />
            <span class="mm-sp-label">{{ $t('tools.mindmap.borderColor') }}</span>
            <el-color-picker size="small" :model-value="styleForm.borderColor" @change="v => applyStyle('borderColor', v)" />
          </div>
          <div class="mm-sp-row">
            <span class="mm-sp-label">{{ $t('tools.mindmap.borderWidth') }}</span>
            <el-input-number size="small" :min="0" :max="20" :model-value="styleForm.borderWidth" @change="v => applyStyle('borderWidth', v)" />
            <span class="mm-sp-label">{{ $t('tools.mindmap.borderRadius') }}</span>
            <el-input-number size="small" :min="0" :max="50" :model-value="styleForm.borderRadius" @change="v => applyStyle('borderRadius', v)" />
          </div>
          <div class="mm-sp-row">
            <span class="mm-sp-label">{{ $t('tools.mindmap.borderDasharray') }}</span>
            <el-select size="small" class="mm-sp-dash" :model-value="styleForm.borderDasharray" @change="v => applyStyle('borderDasharray', v)">
              <el-option v-for="d in dashOptions" :key="d" :label="d === 'none' ? $t('tools.mindmap.solidLine') : d" :value="d" />
            </el-select>
          </div>
          <div class="mm-sp-section">{{ $t('tools.mindmap.lineSection') }}</div>
          <div class="mm-sp-row">
            <span class="mm-sp-label">{{ $t('tools.mindmap.lineColor') }}</span>
            <el-color-picker size="small" :model-value="styleForm.lineColor" @change="v => applyStyle('lineColor', v)" />
          </div>
        </template>
      </div>
    </div>

    <!-- 右键菜单 -->
    <div v-if="ctxMenu.visible" ref="ctxMenuRef" class="mm-ctx-menu card" :style="ctxMenuPos" @contextmenu.prevent>
      <div v-for="(item, i) in ctxItems" :key="i">
        <div v-if="item.divider" class="mm-ctx-divider"></div>
        <div
          v-else-if="!item.children"
          class="mm-ctx-item"
          :class="{ disabled: item.disabled }"
          @click="!item.disabled && runCtx(item.action)"
        >
          {{ item.label }}
        </div>
      </div>
      <!-- 图标子面板 -->
      <div class="mm-ctx-item" :class="{ disabled: ctxDisabled() }" @click="ctxIconOpen = !ctxIconOpen">
        {{ $t('tools.mindmap.icon') }}
        <el-icon class="mm-ctx-arrow" :class="{ open: ctxIconOpen }"><ArrowDown /></el-icon>
      </div>
      <div v-if="ctxIconOpen" class="mm-icon-groups">
        <div v-for="g in iconGroups" :key="g.type" class="mm-icon-group">
          <div class="mm-icon-group-name">{{ g.name }}</div>
          <div class="mm-icon-list">
            <span
              v-for="ic in g.list"
              :key="ic.name"
              class="mm-icon-item"
              :class="{ active: isIconActive(g.type + '_' + ic.name) }"
              :title="g.name + ' ' + ic.name"
              @click="toggleIcon(g.type, g.type + '_' + ic.name)"
              v-html="ic.icon"
            ></span>
          </div>
        </div>
      </div>
    </div>

    <!-- 备注气泡 -->
    <div
      v-if="noteBubble.visible"
      class="mm-note-bubble card"
      :style="noteBubblePos"
      @mousedown.stop
      @contextmenu.prevent
    >
      <div class="mm-note-bubble-text">{{ noteBubble.text }}</div>
    </div>

    <!-- 富文本格式工具栏(节点文字编辑时,选中即现;mousedown 阻止夺焦保住 quill 选区) -->
    <div
      v-if="rtBar.visible"
      class="mm-rt-bar card"
      :style="rtBarPos"
      @mousedown.prevent
      @contextmenu.prevent
    >
      <span class="mm-rt-btn" :class="{ active: rtBar.formats.bold }" @click="rtToggle('bold')"><b>B</b></span>
      <span class="mm-rt-btn" :class="{ active: rtBar.formats.italic }" @click="rtToggle('italic')"><i>I</i></span>
      <span class="mm-rt-btn" :class="{ active: rtBar.formats.underline }" @click="rtToggle('underline')"><u>U</u></span>
      <span class="mm-rt-btn" :class="{ active: rtBar.formats.strike }" @click="rtToggle('strike')"><s>S</s></span>
      <span class="mm-rt-divider"></span>
      <el-color-picker size="small" :model-value="rtBar.formats.color || null" @change="rtSetColor" />
      <span class="mm-rt-btn mm-rt-bg" :class="{ active: rtBar.formats.background }" @click="rtToggle('background')" title="背景色">A</span>
      <span class="mm-rt-divider"></span>
      <span class="mm-rt-btn" :title="$t('tools.mindmap.clearFormat')" @click="rtClear">{{ $t('tools.mindmap.clearFormatShort') }}</span>
    </div>

    <!-- 历史版本抽屉 -->
    <el-drawer v-model="history.visible" :title="$t('tools.mindmap.history')" size="360px" append-to-body>
      <div class="mm-hist-toolbar">
        <el-button size="small" type="primary" :icon="Camera" :loading="history.creating" @click="createSnapshot">
          {{ $t('tools.mindmap.snapshotNow') }}
        </el-button>
      </div>
      <div v-loading="history.loading">
        <div v-if="history.list.length" class="mm-hist-list">
          <div v-for="s in history.list" :key="s.id" class="mm-hist-item">
            <div class="mm-hist-info">
              <div class="mm-hist-title">{{ s.title }}</div>
              <div class="mm-hist-meta">
                <span>{{ s.source === 'MANUAL' ? $t('tools.mindmap.snapManual') : $t('tools.mindmap.snapAuto') }}</span>
                <span> · {{ s.creatorName || '-' }} · {{ formatSnapTime(s.createdAt) }}</span>
              </div>
            </div>
            <div class="mm-hist-ops">
              <el-button size="small" type="primary" text @click="restoreSnapshot(s)">{{ $t('tools.mindmap.rollback') }}</el-button>
              <el-tooltip :content="$t('common.delete')" placement="top" :show-after="300">
                <el-button size="small" type="danger" text @click="deleteSnapshot(s)"><el-icon><Delete /></el-icon></el-button>
              </el-tooltip>
            </div>
          </div>
        </div>
        <el-empty v-else :description="$t('tools.mindmap.snapEmpty')" :image-size="48" />
      </div>
    </el-drawer>

    <input ref="fileRef" type="file" :accept="fileAccept" style="display: none" @change="onFileChange" />

    <!-- 备注编辑 -->
    <el-dialog v-model="noteDialog.visible" :title="$t('tools.mindmap.noteTitle')" width="480px" append-to-body>
      <el-input v-model="noteDialog.text" type="textarea" :rows="6" :placeholder="$t('tools.mindmap.notePlaceholder')" maxlength="2000" />
      <template #footer>
        <el-button @click="noteDialog.visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveNote">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 超链接编辑 -->
    <el-dialog v-model="linkDialog.visible" :title="$t('tools.mindmap.linkTitle')" width="480px" append-to-body>
      <el-form label-position="top">
        <el-form-item :label="$t('tools.mindmap.linkUrl')">
          <el-input v-model="linkDialog.url" :placeholder="$t('tools.mindmap.linkUrl')" />
        </el-form-item>
        <el-form-item :label="$t('tools.mindmap.linkText')">
          <el-input v-model="linkDialog.text" :placeholder="$t('tools.mindmap.linkText')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="linkDialog.visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveLink">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 标签编辑 -->
    <el-dialog v-model="tagsDialog.visible" :title="$t('tools.mindmap.tagsTitle')" width="480px" append-to-body>
      <div class="mm-tags-list">
        <el-tag v-for="(tag, i) in tagsDialog.list" :key="tag + i" closable @close="tagsDialog.list.splice(i, 1)">{{ tag }}</el-tag>
        <span v-if="!tagsDialog.list.length" class="mm-tags-empty">{{ $t('tools.mindmap.tagsEmpty') }}</span>
      </div>
      <el-input
        v-model="tagsDialog.input"
        :placeholder="$t('tools.mindmap.tagsPlaceholder')"
        @keyup.enter="addTag"
      >
        <template #append>
          <el-button @click="addTag">{{ $t('tools.mindmap.addTag') }}</el-button>
        </template>
      </el-input>
      <template #footer>
        <el-button @click="tagsDialog.visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="saveTags">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft, ArrowDown, ArrowUp, EditPen, RefreshLeft, RefreshRight,
  CirclePlus, Plus, Delete, Aim, Brush, Close, Search as SearchIcon, CaretRight, Clock, Camera,
} from '@element-plus/icons-vue'
import MindMap from 'simple-mind-map'
import Drag from 'simple-mind-map/src/plugins/Drag.js'
import Export from 'simple-mind-map/src/plugins/Export.js'
import MiniMap from 'simple-mind-map/src/plugins/MiniMap.js'
import KeyboardNavigation from 'simple-mind-map/src/plugins/KeyboardNavigation.js'
import Select from 'simple-mind-map/src/plugins/Select.js'
import TouchEvent from 'simple-mind-map/src/plugins/TouchEvent.js'
import Search from 'simple-mind-map/src/plugins/Search.js'
import AssociativeLine from 'simple-mind-map/src/plugins/AssociativeLine.js'
import OuterFrame from 'simple-mind-map/src/plugins/OuterFrame.js'
import Demonstrate from 'simple-mind-map/src/plugins/Demonstrate.js'
import RichText from 'simple-mind-map/src/plugins/RichText.js'
import { nodeIconList } from 'simple-mind-map/src/svg/icons.js'
import { defineMindmapThemes } from './mindmapThemes'
import { mindmapApi } from '@/api'

// 插件注册(模块级一次):拖拽/导出/小地图/键盘导航/多选/触屏/搜索替换/关联线/外框/演示模式/富文本
// 注意:RichText 必须静态注册——动态 import 在 Vite dev 下会生成独立模块副本,导致 usePlugin 注册到
// 另一个 MindMap 类副本上静默失效(quill 仅进入编辑器独立 chunk,不影响首屏)
MindMap.usePlugin(Drag)
MindMap.usePlugin(Export)
MindMap.usePlugin(MiniMap)
MindMap.usePlugin(KeyboardNavigation)
MindMap.usePlugin(Select)
MindMap.usePlugin(TouchEvent)
MindMap.usePlugin(Search)
MindMap.usePlugin(AssociativeLine)
MindMap.usePlugin(OuterFrame)
MindMap.usePlugin(Demonstrate)
MindMap.usePlugin(RichText)
// 主题注册:npm 包只内置 default,其余 7 个主题本地 defineTheme 注册(见 mindmapThemes.js)
defineMindmapThemes(MindMap)

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const id = route.params.id
const elRef = ref(null)
const fileRef = ref(null)
const searchInputRef = ref(null)
const ctxMenuRef = ref(null)
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
const dashOptions = ['none', '5,5', '10,5', '15,5,5,5', '20,10,5,10']

const saveStateText = computed(() => {
  if (saveState.value === 'dirty') return t('tools.mindmap.dirty')
  if (saveState.value === 'saving') return t('tools.mindmap.saving')
  if (saveState.value === 'failed') return t('tools.mindmap.saveFailed')
  return t('tools.mindmap.saved', { time: savedAt.value })
})

// ---------- 当前激活节点(右键/点击均会激活) ----------
const activeNodes = ref([])
const activeNode = () => activeNodes.value[0] || null

// ---------- 右键菜单 ----------
const ctxMenu = reactive({ visible: false, x: 0, y: 0, isRoot: false })
const ctxIconOpen = ref(false)
const iconGroups = nodeIconList

const ctxItems = computed(() => [
  { label: t('tools.mindmap.addChild'), action: () => exec('INSERT_CHILD_NODE') },
  { label: t('tools.mindmap.addSibling'), action: () => exec('INSERT_NODE'), disabled: ctxMenu.isRoot },
  { divider: true },
  { label: t('tools.mindmap.copy'), action: () => mm.renderer.copy() },
  { label: t('tools.mindmap.cut'), action: () => exec('CUT_NODE') },
  { label: t('tools.mindmap.paste'), action: () => { try { mm.renderer.paste() } catch (e) { /* 剪贴板权限被拒时静默 */ } } },
  { label: t('tools.mindmap.removeNode'), action: () => exec('REMOVE_NODE'), disabled: ctxMenu.isRoot },
  { divider: true },
  { label: t('tools.mindmap.expandAll'), action: () => exec('EXPAND_ALL') },
  { label: t('tools.mindmap.unexpandAll'), action: () => exec('UNEXPAND_ALL') },
  { divider: true },
  { label: t('tools.mindmap.assocLine'), action: () => { try { mm.associativeLine.createLineFromActiveNode() } catch (e) { /* 忽略 */ } }, disabled: ctxMenu.isRoot },
  { label: t('tools.mindmap.outerFrame'), action: () => { try { mm.outerFrame.addOuterFrame() } catch (e) { /* 忽略 */ } }, disabled: ctxMenu.isRoot },
  { label: t('tools.mindmap.removeOuterFrame'), action: () => {
    try {
      if (mm.outerFrame.activeOuterFrame) { mm.outerFrame.removeActiveOuterFrame(); return }
      // 未点击激活外框时,退化清除激活节点(含其子孙)上的外框数据,避免菜单点了没反应
      const node = activeNode()
      if (!node) { ElMessage.warning(t('tools.mindmap.selectFrameFirst')); return }
      const targets = [node, ...(node.children || [])]
      let cleared = 0
      targets.forEach(n => {
        if (n.getData('outerFrame')) {
          mm.execCommand('SET_NODE_DATA', n, { outerFrame: null })
          cleared++
        }
      })
      if (!cleared) ElMessage.warning(t('tools.mindmap.selectFrameFirst'))
    } catch (e) { /* 忽略 */ }
  } },
  { divider: true },
  { label: t('tools.mindmap.note'), action: openNoteDialog },
  { label: t('tools.mindmap.hyperlink'), action: openLinkDialog },
  { label: t('tools.mindmap.tags'), action: openTagsDialog },
  { label: t('tools.mindmap.style'), action: openStylePanelFromMenu },
])

const ctxDisabled = () => !activeNode()

const ctxMenuPos = computed(() => {
  // 视口内 clamp:菜单实测高约 554px(16 项,图标子面板展开更高,CSS max-height 会兜底滚动),
  // 估算 H 必须取大值,否则 clamp 后菜单底部仍会溢出视口(小屏实测 BUG:原 380 导致底部 4 项不可点)
  const W = 220, H = 560
  const x = Math.min(ctxMenu.x, window.innerWidth - W - 8)
  const y = Math.min(ctxMenu.y, window.innerHeight - H - 8)
  return { left: Math.max(8, x) + 'px', top: Math.max(8, y) + 'px' }
})

const onNodeContextmenu = (e, node) => {
  closeFloat()
  ctxMenu.x = e.clientX
  ctxMenu.y = e.clientY
  ctxMenu.isRoot = !!node?.isRoot
  ctxIconOpen.value = false
  ctxMenu.visible = true
}

const runCtx = (action) => {
  ctxMenu.visible = false
  action()
}

// ---------- 图标(库内置 4 组:优先级/进度/表情/标记) ----------
const ctxNodeIconList = () => (activeNode() ? activeNode().getData('icon') || [] : [])
const isIconActive = (iconId) => ctxNodeIconList().includes(iconId)
const toggleIcon = (type, iconId) => {
  const node = activeNode()
  if (!node) return
  const wasSelected = (node.getData('icon') || []).includes(iconId)
  let icons = (node.getData('icon') || []).filter(i => i !== iconId)
  // 同组互斥(如优先级只能有一个)
  icons = icons.filter(i => !i.startsWith(type + '_'))
  if (!wasSelected) icons.push(iconId)
  try { mm.execCommand('SET_NODE_ICON', node, icons) } catch (e) { /* 忽略 */ }
}

// ---------- 节点样式面板 ----------
const stylePanel = reactive({ visible: false })
const styleForm = reactive({
  color: '', fontSize: 16, bold: false, italic: false, underline: false,
  fillColor: '', borderColor: '', borderWidth: 0, borderRadius: 5, borderDasharray: 'none',
  lineColor: '',
})
const dashToArr = (s) => (s === 'none' ? 'none' : String(s).split(',').map(Number))

const loadStyleForm = () => {
  const node = activeNode()
  stylePanel.node = node
  if (!node || !mm) return
  // 节点未自定义时回退主题默认(root/second/node 三档)
  const tc = mm.getThemeConfig()
  const base = node.isRoot ? tc.root : (node.layerIndex === 1 ? tc.second : tc.node)
  const d = node.getData()
  styleForm.color = d.color ?? base.color
  styleForm.fontSize = d.fontSize ?? base.fontSize
  styleForm.bold = (d.fontWeight ?? base.fontWeight) === 'bold'
  styleForm.italic = (d.fontStyle ?? base.fontStyle) === 'italic'
  styleForm.underline = (d.textDecoration ?? base.textDecoration) === 'underline'
  styleForm.fillColor = d.fillColor ?? base.fillColor
  styleForm.borderColor = d.borderColor ?? base.borderColor
  styleForm.borderWidth = d.borderWidth ?? base.borderWidth
  styleForm.borderRadius = d.borderRadius ?? base.borderRadius
  styleForm.borderDasharray = (() => {
    const v = d.borderDasharray ?? base.borderDasharray
    return Array.isArray(v) ? v.join(',') : (v || 'none')
  })()
  styleForm.lineColor = d.lineColor ?? base.lineColor
}

const applyStyle = (prop, value) => {
  const node = activeNode()
  if (!node || value === undefined || value === null || value === '') return
  try {
    mm.execCommand('SET_NODE_STYLE', node, prop, prop.endsWith('Dasharray') ? dashToArr(value) : value)
  } catch (e) { /* 忽略 */ }
}

const toggleStylePanel = () => {
  if (!activeNode()) return
  stylePanel.visible = !stylePanel.visible
  if (stylePanel.visible) loadStyleForm()
}

const openStylePanelFromMenu = () => {
  if (!activeNode()) return
  stylePanel.visible = true
  loadStyleForm()
}

// ---------- 搜索替换 ----------
const search = reactive({ visible: false, text: '', replaceText: '', info: { currentIndex: 0, total: 0 } })

const openSearch = () => {
  search.visible = true
  nextTick(() => searchInputRef.value?.focus())
}
const closeSearch = () => {
  if (mm) mm.search.endSearch()
  search.visible = false
}
const doSearch = () => {
  if (!search.text.trim()) return
  try { mm.search.search(search.text.trim()) } catch (e) { /* 忽略 */ }
}
const searchNext = () => { try { mm.search.searchNext() } catch (e) { /* 忽略 */ } }
const searchPrev = () => { try { mm.search.searchPrev() } catch (e) { /* 忽略 */ } }
const doReplace = () => {
  if (!search.text.trim()) return
  try { mm.search.replace(search.replaceText) } catch (e) { /* 忽略 */ }
}
const doReplaceAll = () => {
  if (!search.text.trim()) return
  try { mm.search.replaceAll(search.replaceText) } catch (e) { /* 忽略 */ }
}
const onSearchInfoChange = (info) => { search.info = info }

// ---------- 富文本工具栏(库事件驱动,quill 选区由库维护) ----------
const rtBar = reactive({ visible: false, x: 0, y: 0, formats: {} })

const rtBarPos = computed(() => ({
  left: Math.max(8, Math.min(rtBar.x, window.innerWidth - 330)) + 'px',
  top: Math.max(8, rtBar.y) + 'px',
}))

const onRichTextSelectionChange = (hasRange, rectInfo, formatInfo) => {
  if (hasRange && rectInfo) {
    rtBar.visible = true
    rtBar.x = rectInfo.left
    rtBar.y = rectInfo.bottom + 8
    rtBar.formats = formatInfo || {}
  } else {
    rtBar.visible = false
  }
}

const rtToggle = (key) => {
  // background 需要具体色值,开关用默认高亮黄
  const val = key === 'background'
    ? (rtBar.formats.background ? false : '#ffe58f')
    : !rtBar.formats[key]
  try { mm.richText.formatText({ [key]: val }) } catch (e) { /* 忽略 */ }
}
const rtSetColor = (v) => { if (v) { try { mm.richText.formatText({ color: v }) } catch (e) { /* 忽略 */ } } }
const rtSetBg = (v) => { if (v) { try { mm.richText.formatText({ background: v }) } catch (e) { /* 忽略 */ } } }
const rtClear = () => { try { mm.richText.removeFormat() } catch (e) { /* 忽略 */ } }

// ---------- 备注气泡 / 编辑 ----------
const noteBubble = reactive({ visible: false, x: 0, y: 0, text: '' })
const noteDialog = reactive({ visible: false, text: '' })

const noteBubblePos = computed(() => ({
  left: Math.max(8, Math.min(noteBubble.x, window.innerWidth - 340)) + 'px',
  top: Math.max(8, Math.min(noteBubble.y, window.innerHeight - 160)) + 'px',
}))

const onNodeNoteClick = (node, e) => {
  noteBubble.x = e.clientX + 8
  noteBubble.y = e.clientY + 8
  noteBubble.text = node.getData('note') || ''
  noteBubble.visible = true
}

const openNoteDialog = () => {
  const node = activeNode()
  if (!node) return
  noteDialog.text = node.getData('note') || ''
  noteDialog.visible = true
}
const saveNote = () => {
  const node = activeNode()
  if (!node) return
  const text = noteDialog.text.trim()
  try { mm.execCommand('SET_NODE_NOTE', node, text || null) } catch (e) { /* 忽略 */ }
  noteDialog.visible = false
}

// ---------- 超链接编辑 ----------
const linkDialog = reactive({ visible: false, url: '', text: '' })

const openLinkDialog = () => {
  const node = activeNode()
  if (!node) return
  linkDialog.url = node.getData('hyperlink') || ''
  linkDialog.text = node.getData('hyperlinkTitle') || ''
  linkDialog.visible = true
}
const saveLink = () => {
  const node = activeNode()
  if (!node) return
  const url = linkDialog.url.trim()
  if (url && !/^https?:\/\//i.test(url)) {
    ElMessage.warning(t('tools.mindmap.linkInvalid'))
    return
  }
  try { mm.execCommand('SET_NODE_HYPERLINK', node, url || null, linkDialog.text.trim()) } catch (e) { /* 忽略 */ }
  linkDialog.visible = false
}

// ---------- 标签编辑 ----------
const tagsDialog = reactive({ visible: false, list: [], input: '' })

const openTagsDialog = () => {
  const node = activeNode()
  if (!node) return
  tagsDialog.list = [...(node.getData('tag') || [])]
  tagsDialog.input = ''
  tagsDialog.visible = true
}
const addTag = () => {
  const parts = tagsDialog.input.split(/[,，]/).map(s => s.trim()).filter(Boolean)
  for (const p of parts) {
    if (!tagsDialog.list.includes(p)) tagsDialog.list.push(p)
  }
  tagsDialog.input = ''
}
const saveTags = () => {
  const node = activeNode()
  if (!node) return
  try { mm.execCommand('SET_NODE_TAG', node, tagsDialog.list.length ? tagsDialog.list : null) } catch (e) { /* 忽略 */ }
  tagsDialog.visible = false
}

// ---------- 导入(XMind / JSON) ----------
const fileAccept = ref('')
const importType = ref('xmind')

const onImport = (command) => {
  importType.value = command
  fileAccept.value = command === 'xmind' ? '.xmind' : '.json,application/json'
  nextTick(() => {
    fileRef.value && (fileRef.value.value = '')
    fileRef.value?.click()
  })
}

const applyTree = (tree, full) => {
  if (!tree || !tree.data) throw new Error('invalid')
  // 完整导出 JSON 带 layout/theme,一并还原
  if (full) {
    if (full.layout && layoutOptions.some(o => o.value === full.layout)) {
      layout.value = full.layout
      mm.setLayout(full.layout)
    }
    if (full.theme && full.theme.template && themeOptions.some(o => o.value === full.theme.template)) {
      themeTemplate.value = full.theme.template
      mm.setTheme(full.theme.template)
      if (full.theme.config) mm.setThemeConfig(full.theme.config)
    }
  }
  mm.setData(tree)
  scheduleSave()
}

const onFileChange = async (e) => {
  const file = e.target.files && e.target.files[0]
  e.target.value = ''
  if (!file) return
  try {
    if (importType.value === 'xmind') {
      // jszip+xml-js 体积较大(~150KB),仅在真正导入 .xmind 时动态加载
      const { parseXmindFile } = await import('simple-mind-map/src/parse/xmind.js')
      const tree = await parseXmindFile(file)
      applyTree(tree)
    } else {
      const text = await file.text()
      const parsed = JSON.parse(text)
      if (parsed && parsed.root) {
        applyTree(parsed.root, parsed)
      } else if (parsed && parsed.data) {
        applyTree(parsed)
      } else {
        throw new Error('invalid')
      }
    }
    ElMessage.success(t('tools.mindmap.importSuccess'))
  } catch (err) {
    ElMessage.error(t('tools.mindmap.importFailed'))
  }
}

// ---------- 悬浮层关闭 ----------
const closeFloat = () => {
  ctxMenu.visible = false
  noteBubble.visible = false
}

const onGlobalMousedown = (e) => {
  // 右键菜单/备注气泡:点击外部关闭;样式面板与搜索栏有显式关闭按钮,不自动关
  if (ctxMenu.visible && ctxMenuRef.value && !ctxMenuRef.value.contains(e.target)) {
    ctxMenu.visible = false
  }
  if (noteBubble.visible && !e.target.closest?.('.mm-note-bubble')) {
    noteBubble.visible = false
  }
}

const onKeydown = (e) => {
  if ((e.ctrlKey || e.metaKey) && !e.shiftKey && !e.altKey && String(e.key).toLowerCase() === 'f') {
    e.preventDefault()
    openSearch()
    return
  }
  if (e.key === 'Escape') {
    if (ctxMenu.visible) { ctxMenu.visible = false; return }
    if (noteBubble.visible) { noteBubble.visible = false; return }
    if (search.visible) { closeSearch(); return }
  }
}

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
  remoteUpdatedAt = record.updatedAt || ''
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
  mm.on('node_active', (node, list) => {
    activeNodes.value = list || []
    if (stylePanel.visible) loadStyleForm()
  })
  mm.on('node_contextmenu', onNodeContextmenu)
  mm.on('node_note_click', onNodeNoteClick)
  mm.on('search_info_change', onSearchInfoChange)
  mm.on('rich_text_selection_change', onRichTextSelectionChange)
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

// 生成列表缩略图:全图导出 PNG 后等比压到 320px 宽(失败返回 null,不阻塞保存)
const buildThumb = async () => {
  try {
    const dataUrl = await mm.doExport.png(title.value || 'mindmap', false)
    const img = new Image()
    await new Promise((resolve, reject) => { img.onload = resolve; img.onerror = reject; img.src = dataUrl })
    const scale = Math.min(1, 320 / img.width)
    const canvas = document.createElement('canvas')
    canvas.width = Math.max(1, Math.round(img.width * scale))
    canvas.height = Math.max(1, Math.round(img.height * scale))
    canvas.getContext('2d').drawImage(img, 0, 0, canvas.width, canvas.height)
    return canvas.toDataURL('image/png')
  } catch (e) {
    return null
  }
}

// 递归移除「无文本且无子节点」的空叶子(新增节点后未输入直接确认会残留 <p><br></p> 空节点,
// 长期残留会在画布上显示为小块;只清提交数据,画布保持,刷新后自然消失)。根节点永不删。
const stripEmptyNodes = (root) => {
  const strip = (node) => {
    if (!node || !node.data) return node
    if (Array.isArray(node.children)) node.children = node.children.map(strip).filter(Boolean)
    const text = String(node.data.text || '').replace(/<[^>]+>/g, '').trim()
    if (!text && !(node.children && node.children.length)) return null
    return node
  }
  strip(root)
  return root
}

const saveNow = async (force = false) => {
  if (!mm || saving.value) return
  clearTimeout(saveTimer)
  saving.value = true
  saveState.value = 'saving'
  try {
    const thumbUrl = await buildThumb()
    // 乐观锁:带最后一次看到的 updated_at,家人先保存过则后端 409,由用户决定覆盖还是加载远端
    const exportData = mm.getData(true)
    stripEmptyNodes(exportData.root) // getData(true) 返回整包,节点树在 .root
    const saved = await mindmapApi.update(id, {
      title: title.value,
      data: JSON.stringify(exportData),
      thumbUrl,
      ...(force || !remoteUpdatedAt ? {} : { baseUpdatedAt: remoteUpdatedAt }),
    })
    if (saved?.updatedAt) remoteUpdatedAt = saved.updatedAt
    savedAt.value = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    saveState.value = 'saved'
    maybeAutoSnapshot()
  } catch (e) {
    if (e?.code === 409) {
      saveState.value = 'failed'
      try {
        await ElMessageBox.confirm(t('tools.mindmap.conflictTip'), t('tools.mindmap.conflictTitle'), {
          confirmButtonText: t('tools.mindmap.conflictOverwrite'),
          cancelButtonText: t('tools.mindmap.conflictLoadRemote'),
          type: 'warning',
        })
        saving.value = false
        return saveNow(true)
      } catch (choice) {
        // 取消=放弃本地改动,加载家人版本
        try {
          const fresh = await mindmapApi.get(id)
          applyRemoteData(fresh)
          saveState.value = 'saved'
          ElMessage.success(t('tools.mindmap.conflictLoaded'))
        } catch (e2) { /* 拉取失败保持 failed */ }
      }
    } else {
      saveState.value = 'failed'
    }
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

const onExport = async (command) => {
  if (!mm) return
  try {
    // pdf/xmind 导出依赖重(pdf-lib/jszip),插件在首次导出时动态注册
    if (command === 'pdf' || command === 'xmind') {
      const mod = await import(command === 'pdf'
        ? 'simple-mind-map/src/plugins/ExportPDF.js'
        : 'simple-mind-map/src/plugins/ExportXMind.js')
      if (!MindMap.hasPlugin(mod.default)) mm.addPlugin(mod.default)
    }
    mm.export(command, true, title.value || 'mindmap')
  } catch (e) {
    ElMessage.error(t('tools.mindmap.exportFailed'))
  }
}

const enterDemonstrate = () => {
  if (!mm) return
  try { mm.demonstrate.enter() } catch (e) { /* 忽略 */ }
}

// ---------- 历史版本快照 ----------
const history = reactive({ visible: false, loading: false, creating: false, list: [] })

// 自动快照:活跃编辑距上次快照超过间隔时,随保存静默存一份 AUTO 快照(失败不提示)
const AUTO_SNAP_INTERVAL = 5 * 60 * 1000
let lastSnapAt = Date.now()

const maybeAutoSnapshot = () => {
  if (Date.now() - lastSnapAt < AUTO_SNAP_INTERVAL) return
  lastSnapAt = Date.now()
  mindmapApi.snapshotCreate(id, 'AUTO').catch(() => { /* 快照失败不打扰编辑 */ })
}

const openHistory = async () => {
  history.visible = true
  history.loading = true
  try {
    history.list = await mindmapApi.snapshotList(id)
  } finally {
    history.loading = false
  }
}

const createSnapshot = async () => {
  history.creating = true
  try {
    await saveNow()
    await mindmapApi.snapshotCreate(id)
    lastSnapAt = Date.now()
    ElMessage.success(t('tools.mindmap.snapCreated'))
    history.list = await mindmapApi.snapshotList(id)
  } finally {
    history.creating = false
  }
}

const restoreSnapshot = async (s) => {
  await ElMessageBox.confirm(t('tools.mindmap.rollbackConfirm'), { type: 'warning' })
  const restored = await mindmapApi.snapshotRestore(id, s.id)
  lastSnapAt = Date.now()
  title.value = restored.title
  applyRemoteData(restored)
  ElMessage.success(t('tools.mindmap.rollbackDone'))
  history.list = await mindmapApi.snapshotList(id)
}

const deleteSnapshot = async (s) => {
  await mindmapApi.snapshotDelete(id, s.id)
  history.list = history.list.filter(x => x.id !== s.id)
}

const formatSnapTime = (s) => (s ? String(s).slice(0, 16).replace('T', ' ') : '-')

// ---------- 协同(轻量):定期拉详情,远端有更新且本地无未保存修改时自动刷新 ----------
let remoteUpdatedAt = ''
let syncTimer = null

const applyRemoteData = (record) => {
  if (!mm || !record?.data) return
  try {
    const parsed = JSON.parse(record.data)
    if (!parsed?.root) return
    if (record.title) title.value = record.title
    if (parsed.layout && layoutOptions.some(o => o.value === parsed.layout)) {
      layout.value = parsed.layout
      mm.setLayout(parsed.layout)
    }
    if (parsed.theme?.template && themeOptions.some(o => o.value === parsed.theme.template)) {
      themeTemplate.value = parsed.theme.template
      mm.setTheme(parsed.theme.template)
    }
    mm.setData(parsed.root)
    remoteUpdatedAt = record.updatedAt || ''
  } catch (e) { /* 数据损坏忽略 */ }
}

const pollRemote = async () => {
  if (document.hidden) return
  // 本地有未保存修改时不抢刷,等本地保存后的下一次轮询再对齐
  if (saveState.value === 'dirty' || saveState.value === 'saving') return
  try {
    const record = await mindmapApi.detail(id)
    if (record?.updatedAt && record.updatedAt !== remoteUpdatedAt) {
      if (!remoteUpdatedAt) {
        remoteUpdatedAt = record.updatedAt
      } else {
        applyRemoteData(record)
        ElMessage.info(t('tools.mindmap.remoteUpdated'))
      }
    }
  } catch (e) { /* 网络失败静默 */ }
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
  syncTimer = setInterval(pollRemote, 20000)
  window.addEventListener('resize', onResize)
  window.addEventListener('mousedown', onGlobalMousedown, true)
  window.addEventListener('keydown', onKeydown)
  window.addEventListener('beforeunload', onBeforeUnload)
})

onBeforeUnmount(() => {
  clearTimeout(saveTimer)
  clearInterval(syncTimer)
  window.removeEventListener('resize', onResize)
  window.removeEventListener('mousedown', onGlobalMousedown, true)
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('beforeunload', onBeforeUnload)
  if (mm) {
    if (search.visible) { try { mm.search.endSearch() } catch (e) { /* 忽略 */ } }
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

/* 搜索替换栏(悬浮画布顶部) */
.mm-search {
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 30;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  max-width: calc(100% - 24px);
  flex-wrap: wrap;
}
.mm-search-input { width: 170px; }
.mm-search-count { font-size: 12px; color: var(--color-text-secondary, #8a8a8a); min-width: 34px; text-align: center; }

/* 节点样式面板(悬浮画布右侧) */
.mm-style-panel {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 30;
  width: 268px;
  max-height: calc(100% - 24px);
  overflow: auto;
  padding: 12px 14px;
}
.mm-sp-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}
.mm-sp-section {
  font-size: 12px;
  color: var(--color-text-secondary, #8a8a8a);
  margin: 10px 0 6px;
  padding-left: 8px;
  border-left: 3px solid var(--color-primary, var(--color-brand));
  line-height: 1.2;
}
.mm-sp-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 6px;
}
.mm-sp-label { font-size: 12px; color: var(--color-text-secondary, #8a8a8a); }
.mm-sp-dash { width: 120px; }
.mm-sp-row :deep(.el-input-number) { width: 90px; }

/* 右键菜单(fixed 定位到视口) */
.mm-ctx-menu {
  position: fixed;
  z-index: 2600;
  width: 200px;
  padding: 6px;
  max-height: calc(100vh - 16px);
  overflow: auto;
}
.mm-ctx-item {
  padding: 7px 12px;
  font-size: 13px;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: space-between;
  user-select: none;
}
.mm-ctx-item:hover { background: var(--color-card-2, rgba(0, 0, 0, 0.04)); }
.mm-ctx-item.disabled { color: var(--color-text-secondary, #aaa); cursor: not-allowed; }
.mm-ctx-item.disabled:hover { background: transparent; }
.mm-ctx-divider { height: 1px; background: var(--color-border, #e5e5e5); margin: 5px 8px; }
.mm-ctx-arrow { transition: transform 0.15s; font-size: 12px; }
.mm-ctx-arrow.open { transform: rotate(180deg); }

/* 图标子面板 */
.mm-icon-groups { padding: 4px 6px 6px; }
.mm-icon-group { margin-bottom: 8px; }
.mm-icon-group-name { font-size: 11px; color: var(--color-text-secondary, #8a8a8a); margin-bottom: 4px; }
.mm-icon-list { display: flex; flex-wrap: wrap; gap: 4px; }
.mm-icon-item {
  width: 24px;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  cursor: pointer;
  border: 1px solid transparent;
}
.mm-icon-item:hover { background: var(--color-card-2, rgba(0, 0, 0, 0.04)); }
.mm-icon-item.active { border-color: var(--color-primary, var(--color-brand)); background: var(--color-primary-light, rgba(184, 140, 110, 0.1)); }
.mm-icon-item :deep(svg) { width: 18px; height: 18px; }

/* 备注气泡 */
.mm-note-bubble {
  position: fixed;
  z-index: 2600;
  width: 320px;
  padding: 10px 12px;
}
.mm-note-bubble-text {
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow: auto;
}

/* 富文本格式工具栏 */
.mm-rt-bar {
  position: fixed;
  z-index: 2600;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 8px;
}

/* 历史版本抽屉 */
.mm-hist-toolbar { margin-bottom: 12px; }
.mm-hist-list { display: flex; flex-direction: column; gap: 8px; }
.mm-hist-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--color-border, #e5e5e5);
  border-radius: 10px;
}
.mm-hist-title { font-size: 13px; font-weight: 600; margin-bottom: 2px; }
.mm-hist-meta { font-size: 12px; color: var(--color-text-secondary, #8a8a8a); }
.mm-hist-ops { flex-shrink: 0; }
.mm-rt-btn {
  min-width: 26px;
  height: 26px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  user-select: none;
  padding: 0 4px;
}
.mm-rt-btn:hover { background: var(--color-card-2, rgba(0, 0, 0, 0.04)); }
.mm-rt-btn.active { background: var(--color-primary-light, rgba(184, 140, 110, 0.15)); color: var(--color-primary, var(--color-brand)); }
.mm-rt-bg { font-weight: 700; background: rgba(255, 229, 143, 0.4); }
.mm-rt-divider { width: 1px; height: 16px; background: var(--color-border, #e5e5e5); margin: 0 2px; }
.mm-rt-bar :deep(.el-color-picker__trigger) { width: 26px; height: 26px; }

/* 标签编辑 */
.mm-tags-list { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 10px; min-height: 24px; }
.mm-tags-empty { font-size: 12px; color: var(--color-text-secondary, #8a8a8a); }

@media (max-width: 768px) {
  .mm-page { height: calc(100dvh - 120px); }
  .mm-title-text { max-width: 140px; }
  .mm-search-input { width: 120px; }
  .mm-style-panel { width: calc(100% - 24px); }
}
</style>
