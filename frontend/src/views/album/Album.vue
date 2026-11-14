<!-- 相册列表页:封面/类型/照片数卡片网格,登录后可新建,家长或创建者可管理 -->
<template>
  <div ref="root" class="page">
    <Breadcrumb :items="[{ label: t('album.title') }]" />

    <PageToolbar>
      <template v-if="!selectMode">
        <div class="tb-left album-tb-left">
          <el-input
            v-model="searchKeyword"
            class="album-search"
            :placeholder="t('album.searchPlaceholder')"
            clearable
            size="small"
            data-search-input
          >
            <template #prefix>
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                <circle cx="11" cy="11" r="8" />
                <path d="M21 21l-4.35-4.35" />
              </svg>
            </template>
          </el-input>
          <el-select v-model="sourceFilter" class="album-filter-source" size="small" :placeholder="t('album.filterSource')">
            <el-option value="" :label="t('album.allSources')" />
            <el-option value="LOCAL" :label="t('album.localUpload')" />
            <el-option v-for="s in sourceOptions" :key="s.value" :value="s.value" :label="s.label" />
          </el-select>
          <el-select v-model="visibilityFilter" class="album-filter-vis" size="small" :placeholder="t('album.filterType')">
            <el-option value="" :label="t('album.allTypes')" />
            <el-option v-for="o in visibilityOptions" :key="o.value" :value="o.value" :label="o.label" />
          </el-select>
        </div>
        <div class="tb-right">
          <template v-if="!isMobile">
            <div v-if="!wideList" class="view-toggle">
              <button class="vt-btn" :class="{ active: viewMode === 'grid' }" :title="t('common.viewGrid')" @click="viewMode = 'grid'">
                <svg viewBox="0 0 16 16" width="14" height="14">
                  <rect x="1.5" y="1.5" width="5" height="5" rx="1" fill="currentColor" />
                  <rect x="9.5" y="1.5" width="5" height="5" rx="1" fill="currentColor" />
                  <rect x="1.5" y="9.5" width="5" height="5" rx="1" fill="currentColor" />
                  <rect x="9.5" y="9.5" width="5" height="5" rx="1" fill="currentColor" />
                </svg>
              </button>
              <button class="vt-btn" :class="{ active: viewMode === 'list' }" :title="t('common.viewList')" @click="viewMode = 'list'">
                <svg viewBox="0 0 16 16" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
                  <path d="M2 4h12M2 8h12M2 12h12" />
                </svg>
              </button>
            </div>
            <el-button v-if="userStore.isLoggedIn" @click="cascadeVisible = true">{{ t('cascade.title') }}</el-button>
            <el-button v-if="userStore.isLoggedIn && topAlbums.length" @click="toggleSelect">{{ t('album.select') }}</el-button>
            <el-button v-if="userStore.isOwner" @click="syncVisible = true">{{ t('album.syncFromDevice') }}</el-button>
            <el-button v-if="backupAvailable" @click="openBackupSettings">{{ t('album.backupToolbar') }}</el-button>
          </template>
          <!-- 手机:其余动作收进「更多」,只留搜索 + 更多 + 新建,工具栏不再换行挤压 -->
          <el-dropdown v-else trigger="click" @command="onMoreCommand">
            <el-button :aria-label="t('album.moreActions')"
              ><el-icon><MoreFilled /></el-icon
            ></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="cascade">{{ t('cascade.title') }}</el-dropdown-item>
                <el-dropdown-item v-if="topAlbums.length" command="select">{{ t('album.select') }}</el-dropdown-item>
                <el-dropdown-item v-if="!wideList" command="toggleView">{{
                  viewMode === 'grid' ? t('common.viewList') : t('common.viewGrid')
                }}</el-dropdown-item>
                <el-dropdown-item v-if="userStore.isOwner" command="sync">{{ t('album.syncFromDevice') }}</el-dropdown-item>
                <el-dropdown-item v-if="backupAvailable" command="backup">{{ t('album.backupToolbar') }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button v-if="userStore.isLoggedIn" type="primary" @click="openEditor()">{{ t('album.newAlbum') }}</el-button>
        </div>
      </template>
      <div v-else class="tb-right">
        <span class="select-count">{{ t('album.selectedAlbums', { n: selectedIds.length }) }}</span>
        <el-button :disabled="batchDeleting" @click="selectAllVisible">{{ t('common.selectAll') }}</el-button>
        <el-button :disabled="batchDeleting" @click="toggleSelect">{{ t('album.cancelSelect') }}</el-button>
        <el-button type="danger" :loading="batchDeleting" :disabled="!selectedIds.length" @click="onBatchDelete">{{
          t('album.deleteSelected')
        }}</el-button>
      </div>
    </PageToolbar>

    <div v-loading="loading">
      <!-- 宽屏双栏(≥900px 且屏幕短边 ≥600dp,判定只在 useDevice):
           左栏紧凑相册行只负责选择,右栏渲详情(受控 id,不走路由);两列各自滚。
           多选是纯选择场景,退回整宽卡片网格(保留 pick-badge 交互)。 -->
      <div v-if="wideList" ref="wideEl" class="wide-cols album-wide" :style="colStyle">
        <div class="wide-col album-pane">
          <div v-if="topAlbums.length" class="album-pane-list">
            <div
              v-a11y-click
              v-for="a in topAlbums"
              :key="a.id"
              class="album-pane-row"
              :class="{ active: a.id === activeAlbumId }"
              @click="selectAlbum(a.id)"
            >
              <div class="pane-cover" :style="a.cover ? { backgroundImage: `url(${a.cover})` } : {}">
                <AlbumDefaultCover v-if="!a.cover" :size="28" />
              </div>
              <div class="pane-info">
                <span class="pane-name">{{ a.name }}</span>
                <span v-if="a.sharedFrom" class="pane-shared">{{ sharedFromText(a.sharedFrom) }}</span>
                <span class="pane-meta">{{ t('album.photoCount', { n: a.totalPhotoCount ?? a.photoCount }) }}</span>
              </div>
            </div>
          </div>
          <el-empty v-else :description="userStore.isGuest ? t('album.noPublicAlbum') : t('album.emptyHint')" :image-size="60" />
        </div>
        <div class="wide-col album-detail-pane">
          <AlbumDetail v-if="activeAlbumId" :album-id="activeAlbumId" inline @open-album="selectAlbum" @close="clearActive">
            <template #actions>
              <template v-if="activeAlbum && canManage(activeAlbum)">
                <el-tooltip :content="t('common.edit')" placement="top" :show-after="300">
                  <el-button size="small" text @click="openEditor(activeAlbum)">
                    <el-icon><Edit /></el-icon>
                  </el-button>
                </el-tooltip>
                <el-tooltip :content="t('common.delete')" placement="top" :show-after="300">
                  <el-button size="small" text type="danger" @click="onDel(activeAlbum)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </el-tooltip>
              </template>
              <el-tooltip :content="t('common.close')" placement="top" :show-after="300">
                <el-button size="small" text :aria-label="t('common.close')" @click="clearActive">
                  <el-icon><Close /></el-icon>
                </el-button>
              </el-tooltip>
            </template>
          </AlbumDetail>
          <div v-else class="card pane-empty">{{ t('album.pickAlbumHint') }}</div>
        </div>
      </div>

      <template v-else>
        <div v-if="topAlbums.length && viewMode === 'list'" class="resource-list">
          <div
            v-a11y-click
            v-for="a in topAlbums"
            :key="a.id"
            class="rl-row card"
            :class="{ selected: selectMode && selectedIds.includes(a.id) }"
            @click="onAlbumClick(a)"
            @pointerdown="pressStart(a)"
            @pointerup="cancelPress"
            @pointercancel="cancelPress"
            @pointerleave="cancelPress"
            @contextmenu.prevent
          >
            <span v-if="selectMode" class="pick-badge" :class="{ on: selectedIds.includes(a.id) }" @click.stop="togglePick(a)">
              <svg viewBox="0 0 16 16" width="11" height="11">
                <path
                  d="M3 8.5 L6.5 12 L13 4.5"
                  fill="none"
                  stroke="#fff"
                  stroke-width="2.4"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            </span>
            <div class="rl-thumb" :style="a.cover ? { backgroundImage: `url(${a.cover})` } : {}">
              <AlbumDefaultCover v-if="!a.cover" :size="28" />
            </div>
            <div class="rl-main">
              <span class="rl-name">{{ a.name }}</span>
              <span class="rl-meta">
                {{ t('album.photoCount', { n: a.totalPhotoCount ?? a.photoCount }) }} · {{ formatDate(a.createdAt) }}
                <template v-if="a.sourceDeviceName"> · {{ a.sourceDeviceName }}</template>
              </span>
            </div>
            <span v-if="!selectMode" class="rl-actions album-vis" :class="albumVis(a)">{{ visibilityLabel(t, albumVis(a)) }}</span>
          </div>
        </div>
        <div v-else-if="topAlbums.length" class="album-grid">
          <div
            v-a11y-click
            v-for="a in topAlbums"
            :key="a.id"
            class="album-card card"
            :class="{ selected: selectMode && selectedIds.includes(a.id) }"
            @click="onAlbumClick(a)"
            @pointerdown="pressStart(a)"
            @pointerup="cancelPress"
            @pointercancel="cancelPress"
            @pointerleave="cancelPress"
            @contextmenu.prevent
          >
            <div class="album-cover-wrap">
              <div v-if="a.cover" class="album-cover" :style="{ backgroundImage: `url(${a.cover})` }"></div>
              <div v-else class="album-cover">
                <AlbumDefaultCover :size="64" />
              </div>
              <span v-if="!selectMode" class="album-vis" :class="albumVis(a)">{{ visibilityLabel(t, albumVis(a)) }}</span>
              <span v-if="a.sharedFrom" class="album-shared">{{ sharedFromText(a.sharedFrom) }}</span>
              <span v-if="a.sourceDeviceName" class="album-source">
                <span class="status-dot" :class="a.syncStatus || 'OFFLINE'"></span>{{ a.sourceDeviceName }}
              </span>
              <span class="album-count">{{ t('album.photoCount', { n: a.totalPhotoCount ?? a.photoCount }) }}</span>
              <span v-if="a.childCount" class="album-subcount">{{ t('album.subAlbumCount', { n: a.childCount }) }}</span>
              <span v-if="selectMode" class="pick-badge" :class="{ on: selectedIds.includes(a.id) }">
                <svg viewBox="0 0 16 16" width="12" height="12">
                  <path
                    d="M3 8.5 L6.5 12 L13 4.5"
                    fill="none"
                    stroke="#fff"
                    stroke-width="2.4"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
              </span>
            </div>
            <div class="album-info">
              <div class="album-name">{{ a.name }}</div>
              <div class="album-meta">{{ formatDate(a.createdAt) }}</div>
            </div>
            <div v-if="!selectMode && canManage(a)" class="album-actions" @click.stop>
              <el-tooltip :content="t('common.edit')" placement="top" :show-after="300">
                <el-button size="small" text @click="openEditor(a)"
                  ><el-icon><Edit /></el-icon
                ></el-button>
              </el-tooltip>
              <el-tooltip :content="t('common.delete')" placement="top" :show-after="300">
                <el-button size="small" text type="danger" @click="onDel(a)"
                  ><el-icon><Delete /></el-icon
                ></el-button>
              </el-tooltip>
            </div>
          </div>
        </div>
        <div v-else-if="loadError" style="padding: 20px 0; text-align: center; color: var(--color-text-secondary)">
          {{ t('common.loadFailed') }} <el-button text size="small" @click="load">{{ t('common.retry') }}</el-button>
        </div>
        <el-empty v-else :description="userStore.isGuest ? t('album.noPublicAlbum') : t('album.emptyHint')" />
      </template>
    </div>

    <el-dialog v-model="editor.visible" append-to-body :title="editor.form.id ? t('album.editTitle') : t('album.newAlbum')" width="420px">
      <el-form :model="editor.form" label-position="top">
        <el-form-item :label="t('album.albumName')">
          <el-input v-model="editor.form.name" :placeholder="t('album.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('album.visibility')">
          <VisibilityPicker v-model="editor.form.visibility" :options="visibilityOptions" />
        </el-form-item>
        <!-- 分享范围:只有「亲友圈」才需要选家庭;面板数据随编辑对象加载 -->
        <div v-if="needsAudience(editor.form.visibility)" class="editor-audience">
          <AudiencePicker v-if="audience.canShare" v-model="audience.selected" :families="audience.families" />
          <p v-else class="editor-audience-tip">{{ t('audience.canShareFalse') }}</p>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="editor.visible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="onSave">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
    <SyncDialog v-model="syncVisible" @synced="load" />
    <!-- 照片瀑布:原独立页已并入相册,由本页按钮唤起(兼容旧链接 /album?cascade=1) -->
    <PhotoCascade v-model:visible="cascadeVisible" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onBeforeUnmount, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { albumApi, audienceApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { useAudience } from '@/composables/useAudience'
import { usePickSelect } from '@/composables/usePickSelect'
import { useViewMode } from '@/composables/useViewMode'
import { useSyncStore } from '@/stores/sync'
import { usePullRefresh } from '@/composables/usePullRefresh'
import { useDevice } from '@/composables/useDevice'
import { useWideCols } from '@/composables/useWideCols'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { Edit, Delete, Close, MoreFilled } from '@element-plus/icons-vue'
import Breadcrumb from '@/components/Breadcrumb.vue'
import VisibilityPicker from '@/components/common/VisibilityPicker.vue'
import AudiencePicker from '@/components/common/AudiencePicker.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import SyncDialog from '@/components/SyncDialog.vue'
import AlbumDefaultCover from '@/components/AlbumDefaultCover.vue'
import PhotoCascade from './PhotoCascade.vue'
import AlbumDetail from './AlbumDetail.vue'
import { formatDate } from '@/utils/datetime'
import { isNativeApp } from '@/utils/platform'
import { VISIBILITY, visibilityOptions as buildVisibilityOptions, visibilityLabel, needsAudience } from '@/utils/visibility'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { isWide: viewWide, isMobile } = useDevice()
const root = ref(null)
const syncVisible = ref(false)
const cascadeVisible = ref(false)
const albums = ref([])
const loading = ref(false)
const loadError = ref(false)
const editor = reactive({ visible: false, form: { id: null, name: '', visibility: VISIBILITY.PUBLIC } })
// 相册可见范围四档(顺序:公开 > 亲友圈 > 家庭 > 私密);访客/分享链接只看公开
const visibilityOptions = computed(() => buildVisibilityOptions(t))

// 手机自动备份入口:原生壳里显示在工具栏,点进设置页;Web 端不出现
const backupAvailable = computed(() => isNativeApp() && userStore.isLoggedIn)
const openBackupSettings = () => router.push('/settings?tab=client')
// 手机工具栏「更多」里的动作
const onMoreCommand = (cmd) => {
  if (cmd === 'cascade') cascadeVisible.value = true
  else if (cmd === 'select') toggleSelect()
  else if (cmd === 'toggleView') viewMode.value = viewMode.value === 'grid' ? 'list' : 'grid'
  else if (cmd === 'sync') syncVisible.value = true
  else if (cmd === 'backup') openBackupSettings()
}

// 相册可见范围:新列优先,老数据按 type 推导,列表角标与筛选同一口径
const albumVis = (a) => a.visibility || (a.type === 'private' ? VISIBILITY.PRIVATE : VISIBILITY.PUBLIC)

// 顶层相册:层级映射的子相册不直接出现在列表页,进入父相册查看;再按关键词/来源/可见范围前端过滤(数据量小)
const searchKeyword = ref('')
const sourceFilter = ref('')
const visibilityFilter = ref('')
const topAlbums = computed(() =>
  albums.value.filter((a) => {
    if (a.parentId) return false
    if (searchKeyword.value && !a.name.toLowerCase().includes(searchKeyword.value.toLowerCase())) return false
    if (visibilityFilter.value && albumVis(a) !== visibilityFilter.value) return false
    if (sourceFilter.value === 'LOCAL' && a.sourceDeviceId) return false
    if (sourceFilter.value && sourceFilter.value !== 'LOCAL' && String(a.sourceDeviceId) !== sourceFilter.value) return false
    return true
  }),
)
// 来源筛选选项:列表数据中出现的映射设备(去重)
const sourceOptions = computed(() => {
  const map = new Map()
  for (const a of albums.value) {
    if (a.sourceDeviceId && a.sourceDeviceName) map.set(String(a.sourceDeviceId), a.sourceDeviceName)
  }
  return [...map.entries()].map(([value, label]) => ({ value, label }))
})

// 管理权限:家长或相册创建者本人;别人家分享来的相册只能看
const canManage = (a) => userStore.isLoggedIn && !a.readOnly && (userStore.isOwner || a.createdBy === userStore.userInfo?.id)

// 被分享进来的相册:标题旁标出来源家庭(圈内称呼优先)
const sharedFromText = (s) => t('audience.sharedFrom', { name: s.alias || s.familyName || '' })

// ---------- 宽屏双栏:左栏选相册,右栏出详情(受控 id,不改路由) ----------
// 多选是纯选择场景,退回整宽卡片网格;列高由 useWideCols 实测后写 --wide-col-max
// 手机长按相册卡进多选(计时/吞 click/选择集在 usePickSelect)
const { selectMode, selectedIds, toggle, enter, exit, selectAll, pressStart, cancelPress, swallowClick, dispose } = usePickSelect({
  enabled: () => isMobile.value,
})
// 视图模式(网格/列表):进页按本机记忆,切换即持久化
const viewMode = useViewMode('album')
const selectAllVisible = () => selectAll(topAlbums.value.filter((a) => !a.readOnly).map((a) => a.id))
const wideList = computed(() => viewWide.value && !selectMode.value)
const { wideEl, colStyle } = useWideCols(wideList)
const activeAlbumId = ref(null)
const activeAlbum = computed(() => topAlbums.value.find((a) => a.id === activeAlbumId.value) || null)

// 返回键先回「全部相册」:第一次选中压一层同 URL 历史,popstate 只清选中、不离开页面
//(Android 壳的返回键在 canGoBack 时走 router.back(),弹出这一层同样只触发 popstate)
let panelLayerPushed = false
const selectAlbum = (id) => {
  if (activeAlbumId.value === id) return
  activeAlbumId.value = id
  if (!panelLayerPushed) {
    history.pushState({ ...(history.state || {}), ihomy: 'album-panel' }, '')
    panelLayerPushed = true
  }
}
const clearActive = () => {
  if (panelLayerPushed) {
    // 交给 popstate 统一清:程序内关闭也把这层退掉,免得下次返回要多按一次
    history.back()
    return
  }
  activeAlbumId.value = null
}
const onPopState = () => {
  panelLayerPushed = false
  activeAlbumId.value = null
}
// 拖窗口回到窄屏:双栏不渲染了,顺手把压着的历史层退掉,窄屏返回行为与旧版一致
watch(viewWide, (w) => {
  if (w) return
  if (panelLayerPushed) history.back()
  else activeAlbumId.value = null
})

const load = async () => {
  loading.value = true
  loadError.value = false
  try {
    albums.value = await albumApi.list()
  } catch (e) {
    loadError.value = true
  } finally {
    loading.value = false
  }
}

// 分享范围面板:可选圈只随家庭变化,首次打开缓存一次;编辑时读当前相册已分享的圈
const { state: audience, load: loadAudience } = useAudience('album')

const openEditor = (a) => {
  if (a) Object.assign(editor.form, { id: a.id, name: a.name, visibility: albumVis(a) })
  else Object.assign(editor.form, { id: null, name: '', visibility: VISIBILITY.PUBLIC })
  editor.visible = true
  // 新建套家庭默认分享家庭;编辑读该相册已有的分享家庭
  loadAudience(a?.id ?? null, 'album', { applyDefaults: !a })
}

const onSave = async () => {
  if (!editor.form.name) return ElMessage.warning(t('album.nameRequired'))
  // type 同步成旧口径:公开相册=public,其余=private(老客户端与分享链接判定不变)
  const payload = {
    name: editor.form.name,
    visibility: editor.form.visibility,
    type: editor.form.visibility === VISIBILITY.PUBLIC ? 'public' : 'private',
  }
  let albumId = editor.form.id
  if (albumId) await albumApi.update(albumId, payload)
  else {
    const created = await albumApi.create(payload)
    albumId = created?.id
  }
  // 亲友圈保存勾选的家庭;其余档位清空受众(取消分享);失败不影响相册本身已保存
  const picked = audience.loaded && audience.canShare ? (needsAudience(editor.form.visibility) ? [...audience.selected] : []) : null
  if (albumId && picked !== null) {
    try {
      const res = await audienceApi.save('album', albumId, picked)
      if (picked.length && res?.targetCount === 0) ElMessage.info(t('audience.noTarget'))
    } catch {
      ElMessage.warning(t('audience.saveFailed'))
    }
  }
  ElMessage.success(t('album.saved'))
  editor.visible = false
  load()
}

// 删除相册:映射相册=解除映射(不碰设备文件),普通相册=连同照片删除
const onDel = async (a) => {
  const msg = a.sourceDeviceId
    ? t('album.unmapConfirm', { name: a.name, device: a.sourceDeviceName })
    : t('album.deleteConfirm', { name: a.name })
  await ElMessageBox.confirm(msg, t('common.tip'), { type: 'warning', closeOnClickModal: true })
  await albumApi.remove(a.id)
  ElMessage.success(t('common.deleted'))
  if (activeAlbumId.value === a.id) clearActive()
  load()
}

// ---------- 多选删除(选择集在上方宽屏双栏段解构) ----------
const batchDeleting = ref(false)
const toggleSelect = () => {
  if (selectMode.value) exit()
  else enter()
}
const togglePick = (a) => {
  if (a.readOnly) return
  toggle(a.id)
}
const onAlbumClick = (a) => {
  // 长按已选中这张:吞掉松手后的 click,避免又跳详情
  if (swallowClick()) return
  if (selectMode.value) togglePick(a)
  else router.push(`/album/${a.id}`)
}
const onBatchDelete = async () => {
  const targets = topAlbums.value.filter((a) => selectedIds.value.includes(a.id))
  const mapped = targets.filter((a) => a.sourceDeviceId)
  const msg = mapped.length
    ? t('album.batchUnmapConfirm', { n: selectedIds.value.length })
    : t('album.batchDeleteConfirm', { n: selectedIds.value.length })
  await ElMessageBox.confirm(msg, t('common.tip'), { type: 'warning', closeOnClickModal: true })
  batchDeleting.value = true
  try {
    for (const id of [...selectedIds.value]) {
      await albumApi.remove(id)
    }
    ElMessage.success(t('common.deleted'))
    exit()
    load()
  } finally {
    batchDeleting.value = false
  }
}

// ---------- 后台同步完成时自动刷新列表 ----------
const syncStore = useSyncStore()
watch(syncStore.doneCount, () => load())

// 下拉刷新:load 内部已把错误落到 loadError,这里转成 reject 让指示器统一提示
usePullRefresh({
  target: root,
  onRefresh: async () => {
    await load()
    if (loadError.value) throw new Error('album load failed')
  },
})

onMounted(() => {
  // 兼容旧链接 /cascade 重定向来的 /album?cascade=1:进页即打开照片瀑布,并清掉参数(刷新不再自动弹出)
  if (route.query.cascade && userStore.isLoggedIn) {
    cascadeVisible.value = true
    router.replace({ path: '/album' })
  }
  window.addEventListener('popstate', onPopState)
  load()
})

onBeforeUnmount(() => {
  dispose()
  window.removeEventListener('popstate', onPopState)
})
</script>

<style scoped>
.select-count {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-right: 8px;
}
.album-card.selected {
  outline: 3px solid var(--color-primary, var(--color-brand));
  outline-offset: -3px;
}
.pick-badge {
  position: absolute;
  top: 10px;
  left: 10px;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.85);
  border: 2px solid rgba(184, 140, 110, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2;
}
.pick-badge.on {
  background: var(--color-brand);
  border-color: var(--color-brand);
}
.album-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}
/* 宽屏双栏:左 280px 相册行(只做选择)+ 右详情;列内滚动由 main.css 的 .wide-col 统一 */
.album-wide {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 16px;
}
.album-pane-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.album-pane-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 9px;
  border-radius: 10px;
  border: 1px solid transparent;
  cursor: pointer;
  transition: background 0.15s;
}
@media (hover: hover) and (pointer: fine) {
  .album-pane-row:hover {
    background: rgba(var(--color-brand-rgb), 0.06);
  }
}
.album-pane-row.active {
  background: rgba(var(--color-brand-rgb), 0.12);
  border-color: rgba(var(--color-brand-rgb), 0.25);
}
.pane-cover {
  width: 48px;
  height: 48px;
  flex: none;
  border-radius: 8px;
  overflow: hidden;
  background-size: cover;
  background-position: center;
  background-color: var(--color-bg-2, #eef2f7);
  display: flex;
  align-items: center;
  justify-content: center;
}
.pane-info {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.pane-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pane-meta {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.pane-shared {
  font-size: 11px;
  color: var(--color-accent);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
/* 别人家分享进来的相册:左下角标来源家庭,与「子相册数」同位置(被分享相册无子相册) */
.album-shared {
  position: absolute;
  bottom: 10px;
  left: 10px;
  font-size: 11px;
  color: #fff;
  padding: 2px 10px;
  border-radius: 10px;
  background: rgba(var(--color-brand-rgb), 0.85);
}
/* 新建/编辑相册弹窗里的分享范围:与表单字段同宽,私密相册不显示 */
.editor-audience {
  margin-top: 2px;
}
.editor-audience-tip {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-secondary);
}
.album-detail-pane {
  min-width: 0;
}
.pane-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 240px;
  color: var(--color-text-secondary);
  font-size: 13px;
}
.album-card {
  overflow: hidden;
  cursor: pointer;
  transition:
    transform 0.15s,
    box-shadow 0.15s;
}
.album-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 24px rgba(31, 58, 95, 0.15);
}
.album-cover-wrap {
  position: relative;
  aspect-ratio: 4 / 3;
}
.album-cover {
  width: 100%;
  height: 100%;
  background-size: cover;
  background-position: center;
}
.album-vis {
  position: absolute;
  top: 10px;
  left: 10px;
  font-size: 11px;
  color: #fff;
  padding: 2px 10px;
  border-radius: 10px;
  background: rgba(46, 116, 181, 0.85);
}
.album-vis.CIRCLE {
  background: rgba(126, 87, 194, 0.88);
}
.album-vis.FAMILY {
  background: rgba(76, 145, 116, 0.88);
}
.album-vis.PRIVATE {
  background: rgba(230, 162, 60, 0.9);
}
.album-source {
  position: absolute;
  top: 10px;
  right: 10px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  color: #fff;
  padding: 2px 10px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
}
.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  display: inline-block;
}
.status-dot.VALID {
  background: #67b26b;
  box-shadow: 0 0 4px rgba(103, 178, 107, 0.9);
}
.status-dot.OFFLINE,
.status-dot.SYNCING {
  background: #9a9a9a;
}
.status-dot.MISSING {
  background: #b96058;
  box-shadow: 0 0 4px rgba(185, 96, 88, 0.9);
}
.album-count {
  position: absolute;
  bottom: 10px;
  right: 10px;
  font-size: 12px;
  color: #fff;
  background: rgba(0, 0, 0, 0.5);
  padding: 2px 10px;
  border-radius: 10px;
}
.album-subcount {
  position: absolute;
  bottom: 10px;
  left: 10px;
  font-size: 11px;
  color: #fff;
  background: rgba(0, 0, 0, 0.5);
  padding: 2px 10px;
  border-radius: 10px;
}
.album-info {
  padding: 14px 16px 6px;
}
.album-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
}
.album-meta {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-top: 4px;
}
.album-actions {
  /* 按钮是 inline-flex,块级容器里会按基线排版(整体偏上 2px);改 flex + 居中,
     padding 上下对称后中线才真正与容器一致 */
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  padding: 6px 8px;
  margin-bottom: 2px;
}
.album-actions :deep(.el-button) {
  padding: 5px 6px;
}

/* 暖居主题:画廊(暖木阴影 + 深木/陶土徽章 + 封面加高) */
html.theme-warm .album-card {
  border-radius: 16px;
}
html.theme-warm .album-card:hover {
  box-shadow: var(--shadow-hover);
}
html.theme-warm .album-cover-wrap {
  aspect-ratio: 1 / 1;
}
html.theme-warm .album-vis {
  background: var(--color-brand);
  color: var(--color-card);
}
html.theme-warm .album-vis.PRIVATE {
  background: var(--color-accent);
  color: #fff7f0;
}
html.theme-warm .album-grid {
  gap: 18px;
}

.album-search {
  width: 200px;
}
.album-filter-source {
  width: 150px;
}
.album-filter-vis {
  width: 120px;
}

@media (max-width: 768px) {
  /* 手机工具栏:搜索独占一行,筛选两个一行,操作收进「更多」+ 新建 */
  .album-tb-left {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px;
    width: 100%;
  }
  .album-tb-left .album-search {
    grid-column: 1 / -1;
    width: 100%;
  }
  .album-tb-left .album-filter-source,
  .album-tb-left .album-filter-vis {
    width: 100%;
  }
  .album-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 10px;
  }
  .album-name {
    font-size: 14px;
  }
}
</style>
